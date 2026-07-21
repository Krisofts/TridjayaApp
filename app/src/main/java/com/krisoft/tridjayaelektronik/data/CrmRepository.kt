package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.DashboardCacheDao
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheEntity
import com.krisoft.tridjayaelektronik.data.local.LeadDao
import com.krisoft.tridjayaelektronik.data.local.LeadEntity
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaEntity
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.AssigneeDto
import com.krisoft.tridjayaelektronik.data.model.AssigneesData
import com.krisoft.tridjayaelektronik.data.model.CreateProspekRequest
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.data.model.ProspekDraft
import com.krisoft.tridjayaelektronik.data.model.LeadListData
import com.krisoft.tridjayaelektronik.data.model.LostLeadRequest
import com.krisoft.tridjayaelektronik.data.model.MoveStageRequest
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.data.model.PipelinesData
import com.krisoft.tridjayaelektronik.data.remote.CrmApi
import com.krisoft.tridjayaelektronik.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SUMMARY_FETCH_LIMIT = 300
private val LEADS_SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(5)

data class LeadSummary(
    val openCount: Int = 0,
    val wonThisMonth: Int = 0,
    val lostThisMonth: Int = 0,
    val openEstimatedValue: Double = 0.0,
    /** Prospects CREATED today (all statuses) — the field team's daily-input headline number. */
    val todayCount: Int = 0,
    /** Every cached prospect regardless of status. */
    val totalCount: Int = 0
)

private const val KEY_CRM_PIPELINES = "crm_pipelines"
private const val KEY_CRM_ASSIGNEES = "crm_assignees"

@Singleton
class CrmRepository @Inject constructor(
    private val api: CrmApi,
    private val leadDao: LeadDao,
    private val syncMetaDao: SyncMetaDao,
    private val dashboardCacheDao: DashboardCacheDao,
    private val tokenStore: TokenStore,
    @AppScope private val appScope: CoroutineScope
) {

    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Serialises the pending-lead sync queue so overlapping triggers (create, refresh, app-open)
     *  never push the same lead twice. */
    private val syncMutex = Mutex()

    /** Refreshes the local leads cache from the network only if the last sync is older than 5 hours. */
    suspend fun syncLeadsIfStale(assignedTo: String): AuthResult<Unit> {
        val lastSync = syncMetaDao.get(SyncMetaEntity.KEY_LEADS)?.lastSyncMillis ?: 0L
        val isStale = System.currentTimeMillis() - lastSync >= LEADS_SYNC_INTERVAL_MILLIS
        if (!isStale) return AuthResult.Success(Unit)
        return syncLeads(assignedTo)
    }

    /** Forces a network refresh of every one of this user's leads (manual refresh / pull-to-refresh). */
    suspend fun syncLeads(assignedTo: String): AuthResult<Unit> {
        // Push any offline work first so it returns as authoritative server rows below.
        syncPendingLeads()
        syncDirtyStages()
        syncDirtyStatuses()
        return fetchAndCacheLeads(assignedTo)
    }

    /** Network fetch + cache replace only — no pending-queue push (callers handle that), so it's
     *  safe to call from inside [syncPendingLeads]'s mutex without re-entering it. */
    private suspend fun fetchAndCacheLeads(userId: String): AuthResult<Unit> {
        return try {
            val response = api.listLeads(assignedTo = userId, page = 1, limit = SUMMARY_FETCH_LIMIT)
            val data = response.body()?.data
            if (!response.isSuccessful || data == null) return parseError(response)

            // Lengkapi dengan lead yang user ini INPUT tapi dilempar ke sales lain. Untuk role
            // karyawan server sudah mengembalikannya di panggilan pertama (scope OR di crm-service),
            // tapi role manajerial (crm-manager/admin/manager) memakai filter assignedTo murni —
            // panggilan createdBy ini menambalnya. Kegagalannya tidak menggagalkan sync.
            val createdByMe = runCatching {
                api.listLeads(createdBy = userId, page = 1, limit = SUMMARY_FETCH_LIMIT)
                    .takeIf { it.isSuccessful }?.body()?.data?.items
            }.getOrNull().orEmpty()
            val merged = (data.items + createdByMe).distinctBy { it.id }

            // Keep offline work a refresh must never clobber: unpushed creates (temp rows), server
            // rows whose stage moved offline, and unpushed won/lost/reopen outcomes.
            val keepLocal = (leadDao.pendingLeads() + leadDao.dirtyStageLeads() + leadDao.dirtyStatusLeads())
                .distinctBy { it.id }
            val keepIds = keepLocal.map { it.id }.toSet()
            leadDao.replaceAll(merged.filter { it.id !in keepIds }.map { it.toEntity() } + keepLocal)
            syncMetaDao.upsert(SyncMetaEntity(SyncMetaEntity.KEY_LEADS, System.currentTimeMillis()))
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Reads the cached leads list (optionally filtered), no network call. */
    suspend fun cachedLeads(search: String): List<LeadDto> =
        leadDao.search(search.trim()).map { it.toDto() }

    /** Live cached leads — emits on every cache write (create / stage move / won / lost), so screens
     *  observing this update immediately without waiting for the next 5-hour sync or a manual refresh.
     *  distinctUntilChanged: Room re-emits on SETIAP write ke tabel leads walau hasil query tak
     *  berubah — tanpa ini UI recompose percuma. */
    fun observeCachedLeads(search: String): Flow<List<LeadDto>> =
        leadDao.observe(search.trim())
            .map { rows -> rows.map { it.toDto() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** Live CRM summary, recomputed from the cache whenever it changes. */
    fun observeSummary(): Flow<LeadSummary> =
        leadDao.observeAll()
            .map { computeSummary(it) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** Personal CRM summary computed from the cached leads — no extra network round-trip. */
    suspend fun cachedSummary(): LeadSummary = computeSummary(leadDao.all())

    private fun computeSummary(leads: List<LeadEntity>): LeadSummary {
        val currentYearMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
            .format(java.util.Date())
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val myId = tokenStore.userId

        var openCount = 0
        var wonThisMonth = 0
        var lostThisMonth = 0
        var openValue = 0.0
        var todayCount = 0
        leads.forEach { lead ->
            // "Prospek hari ini" & total = semua yang terlihat user (termasuk yang ia lempar —
            // itu tetap hasil input-nya). Angka PIPELINE (open/deal/gagal/nilai) hanya lead yang
            // DIA tangani — cache kini juga berisi lead yang dilempar ke sales lain, dan nilai
            // pipeline orang lain tidak boleh menggelembungkan ringkasan pribadi.
            if (lead.createdAt.take(10) == today) todayCount++
            val mine = lead.assignedTo.isNullOrBlank() || myId == null || lead.assignedTo == myId
            if (!mine) return@forEach
            val updatedYearMonth = lead.updatedAt.take(7)
            when (lead.status) {
                "open" -> {
                    openCount++
                    openValue += lead.estimatedValue
                }
                "won" -> if (updatedYearMonth == currentYearMonth) wonThisMonth++
                "lost" -> if (updatedYearMonth == currentYearMonth) lostThisMonth++
            }
        }
        return LeadSummary(openCount, wonThisMonth, lostThisMonth, openValue, todayCount, leads.size)
    }

    /** Keeps the local cache in sync immediately after a mutation, instead of waiting for the next 5-hour sync. */
    private suspend fun cacheLead(lead: LeadDto) {
        leadDao.insertAll(listOf(lead.toEntity()))
    }

    private fun LeadDto.toEntity() = LeadEntity(
        id = id,
        nama = nama,
        phone = phone,
        pipelineId = pipelineId,
        stageId = stageId,
        status = status,
        assignedTo = assignedTo,
        assignedName = assignedName,
        createdBy = createdBy,
        estimatedValue = estimatedValue,
        source = source,
        lokasi = lokasi,
        lostReason = lostReason,
        catatan = catatan,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = pendingSync,
        minatBarang = minatBarang,
        kategoriProduk = kategoriProduk
    )

    private fun LeadEntity.toDto() = LeadDto(
        id = id,
        nama = nama,
        phone = phone,
        pipelineId = pipelineId,
        stageId = stageId,
        status = status,
        assignedTo = assignedTo,
        assignedName = assignedName,
        createdBy = createdBy,
        estimatedValue = estimatedValue,
        source = source,
        lokasi = lokasi,
        lostReason = lostReason,
        catatan = catatan,
        minatBarang = minatBarang,
        kategoriProduk = kategoriProduk,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = pendingSync
    )

    /** Pipelines with an offline fallback: every successful fetch is cached as JSON in Room, and a
     *  failed fetch (offline) serves the last cached copy so the detail stepper still works. */
    suspend fun pipelines(): AuthResult<List<PipelineDto>> {
        val network = try {
            val response = api.pipelines()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                dashboardCacheDao.upsert(
                    DashboardCacheEntity(
                        key = KEY_CRM_PIPELINES,
                        jsonPayload = errorJson.encodeToString(PipelinesData.serializer(), data),
                        cachedAtMillis = System.currentTimeMillis()
                    )
                )
                return AuthResult.Success(data.items)
            }
            parseError<List<PipelineDto>>(response)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
        // Network failed — serve the cached copy if we ever had one.
        val cached = dashboardCacheDao.get(KEY_CRM_PIPELINES)?.let { entity ->
            runCatching { errorJson.decodeFromString(PipelinesData.serializer(), entity.jsonPayload) }.getOrNull()
        }
        return if (cached != null) AuthResult.Success(cached.items) else network
    }

    /** Assignable employees for the prospect form, with the same cache-fallback pattern as
     *  [pipelines] so assignment still works offline after one successful fetch. */
    suspend fun assignees(): AuthResult<List<AssigneeDto>> {
        val network = try {
            val response = api.assignees()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                dashboardCacheDao.upsert(
                    DashboardCacheEntity(
                        key = KEY_CRM_ASSIGNEES,
                        jsonPayload = errorJson.encodeToString(AssigneesData.serializer(), data),
                        cachedAtMillis = System.currentTimeMillis()
                    )
                )
                return AuthResult.Success(data.items)
            }
            parseError<List<AssigneeDto>>(response)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
        val cached = dashboardCacheDao.get(KEY_CRM_ASSIGNEES)?.let { entity ->
            runCatching { errorJson.decodeFromString(AssigneesData.serializer(), entity.jsonPayload) }.getOrNull()
        }
        return if (cached != null) AuthResult.Success(cached.items) else network
    }

    suspend fun myLeads(assignedTo: String, search: String?, page: Int, limit: Int): AuthResult<LeadListData> {
        return try {
            val response = api.listLeads(
                assignedTo = assignedTo,
                search = search?.ifBlank { null },
                page = page,
                limit = limit
            )
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Lead detail with an offline fallback to the Room cache (the list is already cached, so any
     *  lead opened from it can render offline). Local pending/dirty rows are always served from
     *  cache first — the server copy would show stale data for them. */
    suspend fun leadDetail(id: Long): AuthResult<LeadDto> {
        val cachedFirst = leadDao.byId(id)
        if (cachedFirst != null && (cachedFirst.pendingSync || cachedFirst.stageDirty || cachedFirst.statusDirtyOp != null)) {
            return AuthResult.Success(cachedFirst.toDto())
        }
        val network = try {
            val response = api.leadDetail(id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheLead(data.lead)
                return AuthResult.Success(data.lead)
            }
            parseError<LeadDto>(response)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
        // Network failed — serve the cached row if we have it.
        return if (cachedFirst != null) AuthResult.Success(cachedFirst.toDto()) else network
    }

    /**
     * Offline-first create: writes the prospect to the local cache immediately (marked
     * [LeadEntity.pendingSync], with a temporary negative id) so it appears in the list at once,
     * then kicks the background sync queue to push it to `/api/prospek-harian`. The queue survives
     * this call via [appScope]. Always returns Success — the write is never lost.
     */
    // Sekuens id sementara (negatif) yang dijamin unik meski dua create terjadi di milidetik sama
    // — mencegah tabrakan REPLACE di Room yang bisa membuang lead pertama.
    private val tempIdSeq = java.util.concurrent.atomic.AtomicLong(-System.currentTimeMillis())

    suspend fun createLead(draft: ProspekDraft): AuthResult<LeadDto> {
        val now = nowTimestamp()
        val local = LeadEntity(
            id = tempIdSeq.decrementAndGet(),
            nama = draft.nama,
            phone = draft.phone,
            pipelineId = draft.pipelineId ?: 0,
            stageId = 0,
            status = "open",
            assignedTo = draft.assignedTo,
            estimatedValue = draft.estimatedValue ?: 0.0,
            source = draft.source,
            lokasi = draft.lokasi,
            lostReason = null,
            catatan = draft.catatan,
            createdAt = now,
            updatedAt = now,
            pendingSync = true,
            minatBarang = draft.minatBarang,
            kategoriProduk = draft.kategoriProduk,
            keteranganFincoy = draft.keteranganFincoy
        )
        leadDao.insertAll(listOf(local))
        appScope.launch { syncPendingLeads() }
        return AuthResult.Success(local.toDto())
    }

    /**
     * Pushes every locally-created (pending) prospect to `POST /api/prospek-harian`, oldest-first.
     * That endpoint doesn't return the CRM lead row, so after any successful push the fresh list is
     * re-fetched to swap temp rows for the authoritative server rows. Failed pushes stay pending
     * and are retried on the next trigger (create / refresh / app open). Mutex-guarded so
     * concurrent triggers don't double-submit.
     */
    suspend fun syncPendingLeads() {
        syncMutex.withLock {
            val pending = leadDao.pendingLeads().sortedBy { it.id * -1 } // oldest create first
            var pushedAny = false
            for (entity in pending) {
                val request = CreateProspekRequest(
                    namaProspek = entity.nama,
                    noWhatsapp = entity.phone,
                    minatBarang = entity.minatBarang.orEmpty(),
                    kategoriProduk = entity.kategoriProduk,
                    keteranganProspek = entity.catatan,
                    keteranganFincoy = entity.keteranganFincoy,
                    tanggal = entity.createdAt.take(10),
                    pipelineId = entity.pipelineId.takeIf { it > 0 },
                    source = entity.source,
                    estimatedValue = entity.estimatedValue.takeIf { it > 0 },
                    lokasi = entity.lokasi,
                    assignedTo = entity.assignedTo
                )
                val response = runCatching { api.createProspek(request) }.getOrNull()
                if (response?.isSuccessful == true) {
                    leadDao.deleteById(entity.id)
                    pushedAny = true
                }
                // else: leave it pending; a later trigger retries it.
            }
            // Pull the server rows the pushes just created (fetch only — no queue re-entry).
            // Selalu pakai id user SENDIRI — bukan assignedTo lead yang dipush: prospek yang
            // dilempar ke sales lain akan mengarahkan fetch ke daftar orang itu.
            if (pushedAny) tokenStore.userId?.let { fetchAndCacheLeads(it) }
        }
    }

    private fun nowTimestamp(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    /**
     * Offline-first stage move: the Room row is updated immediately (so the stepper responds
     * instantly, online or offline) and marked [LeadEntity.stageDirty]; a background queue pushes
     * dirty stages to the server and is retried on refresh/app-open. Temp (unpushed) leads only
     * move locally — they have no server id yet.
     */
    suspend fun moveStage(id: Long, stageId: Long): AuthResult<LeadDto> {
        val existing = leadDao.byId(id)
            ?: return AuthResult.Failure("not_found", "Prospek tidak ditemukan di cache")
        // Lead lokal yang belum tersinkron (id negatif) tak punya id server → pindah stage tak bisa
        // ikut disinkron dan akan hilang saat replace. Blokir sampai lead selesai tersinkron.
        if (existing.id <= 0) {
            return AuthResult.Failure("pending_sync", "Prospek belum tersinkron ke server. Coba lagi setelah online.")
        }
        val updated = existing.copy(
            stageId = stageId,
            updatedAt = nowTimestamp(),
            stageDirty = existing.id > 0
        )
        leadDao.insertAll(listOf(updated))
        appScope.launch { syncDirtyStages() }
        return AuthResult.Success(updated.toDto())
    }

    /** Pushes every offline stage move to the server; on success the authoritative server row
     *  replaces the dirty local one. Failures stay dirty and retry on the next trigger. */
    suspend fun syncDirtyStages() {
        syncMutex.withLock {
            for (entity in leadDao.dirtyStageLeads()) {
                val response = runCatching { api.moveStage(entity.id, MoveStageRequest(entity.stageId)) }.getOrNull()
                val data = response?.body()?.data
                if (response?.isSuccessful == true && data != null) {
                    // Preserve any still-unpushed outcome op — the server row doesn't know about it.
                    // Field hydrated (assignedName/minat/kategori/createdBy) dipertahankan bila
                    // endpoint mutasi mengembalikan lead tanpa hidrasi — jangan sampai kartu list
                    // kehilangan nama penanggung jawab/minat setelah push.
                    val server = data.toEntity().copy(
                        statusDirtyOp = entity.statusDirtyOp,
                        status = if (entity.statusDirtyOp != null) entity.status else data.status,
                        lostReason = if (entity.statusDirtyOp != null) entity.lostReason else data.lostReason,
                        assignedName = data.assignedName ?: entity.assignedName,
                        createdBy = data.createdBy ?: entity.createdBy,
                        minatBarang = data.minatBarang ?: entity.minatBarang,
                        kategoriProduk = data.kategoriProduk ?: entity.kategoriProduk
                    )
                    leadDao.insertAll(listOf(server))
                }
                // else: leave it dirty; a later trigger retries it.
            }
        }
    }

    /** Pushes every offline won/lost/reopen outcome to the server, then swaps in the server row.
     *  Failures stay queued and retry on the next trigger. */
    suspend fun syncDirtyStatuses() {
        syncMutex.withLock {
            for (entity in leadDao.dirtyStatusLeads()) {
                val response = runCatching {
                    when (entity.statusDirtyOp) {
                        "won" -> api.markWon(entity.id)
                        "lost" -> api.markLost(entity.id, LostLeadRequest(entity.lostReason.orEmpty()))
                        "reopen" -> api.reopenLead(entity.id)
                        else -> null
                    }
                }.getOrNull()
                val data = response?.body()?.data
                if (response?.isSuccessful == true && data != null) {
                    // Preserve a still-unpushed stage move — the server row still has the old stage.
                    // Plus field hydrated, alasan sama dgn syncDirtyStages di atas.
                    val server = data.toEntity().copy(
                        stageDirty = entity.stageDirty,
                        stageId = if (entity.stageDirty) entity.stageId else data.stageId,
                        assignedName = data.assignedName ?: entity.assignedName,
                        createdBy = data.createdBy ?: entity.createdBy,
                        minatBarang = data.minatBarang ?: entity.minatBarang,
                        kategoriProduk = data.kategoriProduk ?: entity.kategoriProduk
                    )
                    leadDao.insertAll(listOf(server))
                }
                // else: leave it queued; a later trigger retries it.
            }
        }
    }

    suspend fun markWon(id: Long): AuthResult<LeadDto> = applyOutcomeOffline(id, "won")

    suspend fun markLost(id: Long, reason: String): AuthResult<LeadDto> =
        applyOutcomeOffline(id, "lost", reason)

    suspend fun reopenLead(id: Long): AuthResult<LeadDto> = applyOutcomeOffline(id, "reopen")

    /**
     * Offline-first outcome (won/lost/reopen): the Room row flips immediately so the UI responds
     * online or offline, and the op is queued ([LeadEntity.statusDirtyOp]) for the background push.
     * Temp (unpushed) leads only change locally — they have no server id yet.
     */
    private suspend fun applyOutcomeOffline(id: Long, op: String, lostReason: String? = null): AuthResult<LeadDto> {
        val existing = leadDao.byId(id)
            ?: return AuthResult.Failure("not_found", "Prospek tidak ditemukan di cache")
        // Lead lokal belum tersinkron (id negatif) tak bisa dikirim won/lost/reopen ke server dan
        // akan hilang saat replace — blokir sampai tersinkron.
        if (existing.id <= 0) {
            return AuthResult.Failure("pending_sync", "Prospek belum tersinkron ke server. Coba lagi setelah online.")
        }
        val updated = existing.copy(
            status = when (op) {
                "won" -> "won"
                "lost" -> "lost"
                else -> "open"
            },
            lostReason = if (op == "lost") lostReason else null,
            updatedAt = nowTimestamp(),
            statusDirtyOp = op.takeIf { existing.id > 0 }
        )
        leadDao.insertAll(listOf(updated))
        appScope.launch { syncDirtyStatuses() }
        return AuthResult.Success(updated.toDto())
    }

    /** Personal CRM summary computed client-side from this user's own leads (no server-side per-user dashboard). */
    suspend fun mySummary(assignedTo: String): AuthResult<LeadSummary> {
        return try {
            val response = api.listLeads(assignedTo = assignedTo, page = 1, limit = SUMMARY_FETCH_LIMIT)
            val data = response.body()?.data
            if (!response.isSuccessful || data == null) return parseError(response)

            val currentYearMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                .format(java.util.Date())
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())

            var openCount = 0
            var wonThisMonth = 0
            var lostThisMonth = 0
            var openValue = 0.0
            var todayCount = 0
            data.items.forEach { lead ->
                if (lead.createdAt.take(10) == today) todayCount++
                val updatedYearMonth = lead.updatedAt.take(7)
                when (lead.status) {
                    "open" -> {
                        openCount++
                        openValue += lead.estimatedValue
                    }
                    "won" -> if (updatedYearMonth == currentYearMonth) wonThisMonth++
                    "lost" -> if (updatedYearMonth == currentYearMonth) lostThisMonth++
                }
            }
            AuthResult.Success(LeadSummary(openCount, wonThisMonth, lostThisMonth, openValue, todayCount, data.items.size))
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    private fun <T> parseError(response: Response<*>): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "Terjadi kesalahan (${response.code()})"
        )
    }
}
