package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.LeadDao
import com.krisoft.tridjayaelektronik.data.local.LeadEntity
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaEntity
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.CreateLeadRequest
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.data.model.LeadListData
import com.krisoft.tridjayaelektronik.data.model.LostLeadRequest
import com.krisoft.tridjayaelektronik.data.model.MoveStageRequest
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.data.remote.CrmApi
import com.krisoft.tridjayaelektronik.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    val openEstimatedValue: Double = 0.0
)

@Singleton
class CrmRepository @Inject constructor(
    private val api: CrmApi,
    private val leadDao: LeadDao,
    private val syncMetaDao: SyncMetaDao,
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
        // Push any offline-created leads first so they return as authoritative server rows below.
        syncPendingLeads()
        return try {
            val response = api.listLeads(assignedTo = assignedTo, page = 1, limit = SUMMARY_FETCH_LIMIT)
            val data = response.body()?.data
            if (!response.isSuccessful || data == null) return parseError(response)

            // Keep any leads that still couldn't be pushed (offline) — a refresh must never drop them.
            val stillPending = leadDao.pendingLeads()
            leadDao.replaceAll(data.items.map { it.toEntity() } + stillPending)
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
     *  observing this update immediately without waiting for the next 5-hour sync or a manual refresh. */
    fun observeCachedLeads(search: String): Flow<List<LeadDto>> =
        leadDao.observe(search.trim())
            .map { rows -> rows.map { it.toDto() } }
            .flowOn(Dispatchers.Default)

    /** Live CRM summary, recomputed from the cache whenever it changes. */
    fun observeSummary(): Flow<LeadSummary> =
        leadDao.observeAll()
            .map { computeSummary(it) }
            .flowOn(Dispatchers.Default)

    /** Personal CRM summary computed from the cached leads — no extra network round-trip. */
    suspend fun cachedSummary(): LeadSummary = computeSummary(leadDao.all())

    private fun computeSummary(leads: List<LeadEntity>): LeadSummary {
        val currentYearMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
            .format(java.util.Date())

        var openCount = 0
        var wonThisMonth = 0
        var lostThisMonth = 0
        var openValue = 0.0
        leads.forEach { lead ->
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
        return LeadSummary(openCount, wonThisMonth, lostThisMonth, openValue)
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
        estimatedValue = estimatedValue,
        source = source,
        lokasi = lokasi,
        lostReason = lostReason,
        catatan = catatan,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = pendingSync
    )

    private fun LeadEntity.toDto() = LeadDto(
        id = id,
        nama = nama,
        phone = phone,
        pipelineId = pipelineId,
        stageId = stageId,
        status = status,
        assignedTo = assignedTo,
        estimatedValue = estimatedValue,
        source = source,
        lokasi = lokasi,
        lostReason = lostReason,
        catatan = catatan,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = pendingSync
    )

    suspend fun pipelines(): AuthResult<List<PipelineDto>> {
        return try {
            val response = api.pipelines()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data.items)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
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

    suspend fun leadDetail(id: Long): AuthResult<LeadDto> {
        return try {
            val response = api.leadDetail(id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheLead(data.lead)
                AuthResult.Success(data.lead)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Offline-first create: writes the lead to the local cache immediately (marked [pendingSync],
     * with a temporary negative id) so it appears in the list at once, then kicks the background sync
     * queue to push it to the server. The queue survives this call via [appScope]; on success it
     * swaps the temp row for the real server row. Always returns Success — the write is never lost.
     */
    suspend fun createLead(request: CreateLeadRequest): AuthResult<LeadDto> {
        val now = nowTimestamp()
        val local = LeadEntity(
            id = -System.currentTimeMillis(),
            nama = request.nama,
            phone = request.phone,
            pipelineId = request.pipelineId ?: 0,
            stageId = 0,
            status = "open",
            assignedTo = request.assignedTo,
            estimatedValue = request.estimatedValue ?: 0.0,
            source = request.source,
            lokasi = request.lokasi,
            lostReason = null,
            catatan = request.catatan,
            createdAt = now,
            updatedAt = now,
            pendingSync = true
        )
        leadDao.insertAll(listOf(local))
        appScope.launch { syncPendingLeads() }
        return AuthResult.Success(local.toDto())
    }

    /**
     * Pushes every locally-created (pending) lead to the server, oldest-first, one at a time. On a
     * successful push the temp row is replaced by the authoritative server row (id + pendingSync
     * cleared). Failed pushes stay pending and are retried on the next trigger (create / refresh /
     * app open). Mutex-guarded so concurrent triggers don't double-submit.
     */
    suspend fun syncPendingLeads() {
        syncMutex.withLock {
            val pending = leadDao.pendingLeads().sortedBy { it.id * -1 } // oldest create first
            for (entity in pending) {
                val request = CreateLeadRequest(
                    nama = entity.nama,
                    phone = entity.phone,
                    pipelineId = entity.pipelineId.takeIf { it > 0 },
                    assignedTo = entity.assignedTo,
                    estimatedValue = entity.estimatedValue.takeIf { it > 0 },
                    source = entity.source,
                    lokasi = entity.lokasi,
                    catatan = entity.catatan
                )
                val serverLead = runCatching { api.createLead(request) }.getOrNull()
                val data = serverLead?.body()?.data
                if (serverLead?.isSuccessful == true && data != null) {
                    // Replace the optimistic temp row with the real server row.
                    leadDao.deleteById(entity.id)
                    leadDao.insertAll(listOf(data.toEntity()))
                }
                // else: leave it pending; a later trigger retries it.
            }
        }
    }

    private fun nowTimestamp(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    suspend fun moveStage(id: Long, stageId: Long): AuthResult<LeadDto> {
        return try {
            val response = api.moveStage(id, MoveStageRequest(stageId))
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheLead(data)
                AuthResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun markWon(id: Long): AuthResult<LeadDto> {
        return try {
            val response = api.markWon(id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheLead(data)
                AuthResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun markLost(id: Long, reason: String): AuthResult<LeadDto> {
        return try {
            val response = api.markLost(id, LostLeadRequest(reason))
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheLead(data)
                AuthResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun reopenLead(id: Long): AuthResult<LeadDto> {
        return try {
            val response = api.reopenLead(id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheLead(data)
                AuthResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Personal CRM summary computed client-side from this user's own leads (no server-side per-user dashboard). */
    suspend fun mySummary(assignedTo: String): AuthResult<LeadSummary> {
        return try {
            val response = api.listLeads(assignedTo = assignedTo, page = 1, limit = SUMMARY_FETCH_LIMIT)
            val data = response.body()?.data
            if (!response.isSuccessful || data == null) return parseError(response)

            val currentYearMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                .format(java.util.Date())

            var openCount = 0
            var wonThisMonth = 0
            var lostThisMonth = 0
            var openValue = 0.0
            data.items.forEach { lead ->
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
            AuthResult.Success(LeadSummary(openCount, wonThisMonth, lostThisMonth, openValue))
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
