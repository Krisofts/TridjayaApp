package com.krisoft.tridjayaelektronik.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AbsensiRepository
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.RaportRepository
import com.krisoft.tridjayaelektronik.data.SpkTodayCounter
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import com.krisoft.tridjayaelektronik.data.model.ProspekTargetDto
import com.krisoft.tridjayaelektronik.domain.indent.ListIndentUseCase
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.home.effectiveRoles
import com.krisoft.tridjayaelektronik.ui.raport.matchJobdeskPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val cabangName: String = "",
    val tasks: List<DailyTask> = emptyList(),
    val progress: String = "",
    val queueCards: List<ActivityCard> = emptyList(),
    val actions: List<ActivityItem> = emptyList(),
    /** Dipakai subtitle ubin "Buat SPK" — lokal per-device. */
    val spkToday: Int = 0,
    /** Tombol "Panduan Alur" di baris PINTASAN — lihat [panduanAlurVisible]. */
    val panduanVisible: Boolean = false,
)

/**
 * Layar pertama app: mengambil angka untuk kartu yang BOLEH dilihat user saja.
 *
 * Dua sifat penting:
 *  1. **dedup per endpoint** — dua item aki berbagi satu panggilan `akiForms()`;
 *  2. **gagal per-sumber** — satu endpoint mati hanya membuat kartunya sendiri
 *     bertanda "—", kartu lain tetap berangka. Tak ada layar error global:
 *     bagian tugas harian & aksi tak butuh jaringan.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val absensiRepository: AbsensiRepository,
    private val deliveryRepository: DeliveryFlowRepository,
    private val crmRepository: CrmRepository,
    private val raportRepository: RaportRepository,
    private val listIndentUseCase: ListIndentUseCase,
    private val spkTodayCounter: SpkTodayCounter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private var capabilities: Map<String, Boolean>? = null
    private var lastLoadedAtMs: Long = 0L

    /**
     * F2 audit final-fix-3 (2026-07-28): penghitung generasi — `init` memicu DUA
     * `load()` fan-out (refresh(force=true) awal + susulan setelah peta capabilities
     * tiba), tanpa pengait urutan sama sekali. Kalau fan-out pertama kebetulan
     * SELESAI belakangan, penulisan `_uiState.value = ActivityUiState(...)` PENUH
     * (bukan `.copy()`) bisa mundur ke versi `capabilities = null`. Generation
     * counter dipilih ketimbang `Job.cancel()`: `load()` selalu jalan sampai akhir
     * (repository balikin `AuthResult`, tak pernah throw) jadi tak ada risiko
     * `isLoading` nyangkut `true` akibat dibatalkan di tengah — panggilan
     * TERAKHIR (generasi tertinggi) selalu yang menang, apa pun urutan selesainya.
     */
    private var loadGeneration: Long = 0L

    init {
        refresh(force = true)
        // I2 audit 2026-07-28: dulu di-await DI DALAM `load()`, memblokir SELURUH
        // layar (termasuk HARI INI & PINTASAN yang sengaja tak butuh jaringan)
        // sampai timeout OkHttp saat sinyal jelek. Pola sama `HomeViewModel.init`:
        // coroutine TERPISAH — render duluan dgn `capabilities = null` (gate jatuh
        // ke `allowedRoles`, sudah didukung `gateAllows`), lalu muat ulang PENUH
        // begitu peta tiba supaya item yang baru terbuka ikut fan-out (bukan
        // tertinggal tanpa angka).
        viewModelScope.launch {
            authRepository.capabilities()?.let { caps ->
                capabilities = caps
                load()
            }
        }
    }

    /**
     * `force = false` dipakai observer ON_RESUME di `ActivityScreen` (I1 audit
     * 2026-07-28) — tanpa jendela cache ini, bolak-balik cepat antara layar ini
     * dan layar anak (absen/PDI/kasir/dst, ON_RESUME ikut jalan tiap balik ke
     * root) akan menembak ulang seluruh endpoint tiap kali.
     */
    fun refresh(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastLoadedAtMs < CACHE_WINDOW_MS) return
        lastLoadedAtMs = now
        viewModelScope.launch { load() }
    }

    /**
     * `deliveredAt` sudah lewat 24 jam? Timestamp kontrak delivery berformat
     * `YYYY-MM-DD HH:MM:SS` dalam UTC (kadang bersufiks `Z`). Gagal parse =
     * `false` (tak dihitung gantung): lebih baik kartunya kurang satu daripada
     * menuduh kasir lalai gara-gara format tak terbaca.
     */
    private fun isGantung(deliveredAt: String?): Boolean {
        val raw = deliveredAt?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val iso = raw.removeSuffix("Z").replace(' ', 'T')
        val millis = runCatching {
            java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }.getOrNull() ?: return false
        return System.currentTimeMillis() - millis > GANTUNG_THRESHOLD_MS
    }

    private suspend fun load() {
        val myGeneration = ++loadGeneration
        _uiState.value = _uiState.value.copy(isLoading = true)

        val user = authRepository.cachedUser
        val roles = effectiveRoles(user)
        val items = visibleActivityItems(roles, capabilities)
        val todayIso = KlasemenStandings.todayIso()

        val counts = mutableMapOf<ActivitySource, Int?>()
        val failed = mutableSetOf<ActivitySource>()

        val sources = sourcesToFetch(items)
        var checkInAt: String? = null
        var checkOutAt: String? = null
        /** `null` = penyebut jobdesk tak diketahui — lihat `raportJobdeskDetail`. */
        var raportExpected: Int? = null
        /** `null` = target prospek tak diketahui — lihat `prospekTaskDetail`. */
        var prospekTarget: ProspekTargetDto? = null

        coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

            if (ActivitySource.ABSENSI_TODAY in sources) jobs += async {
                when (val r = absensiRepository.today()) {
                    is AuthResult.Success -> {
                        checkInAt = r.data.record?.checkInAt
                        checkOutAt = r.data.record?.checkOutAt
                    }
                    is AuthResult.Failure -> failed += ActivitySource.ABSENSI_TODAY
                }
            }

            // Dua item (milik PDI & milik approver) — SATU panggilan.
            if (ActivitySource.AKI_FORMS_MINE in sources ||
                ActivitySource.AKI_FORMS_APPROVAL in sources
            ) jobs += async {
                when (val r = deliveryRepository.akiForms()) {
                    is AuthResult.Success -> {
                        counts[ActivitySource.AKI_FORMS_MINE] =
                            r.data.count { it.akiBekasStatus == "belum" }
                        counts[ActivitySource.AKI_FORMS_APPROVAL] =
                            r.data.count { it.approvalStatus == "pending" }
                    }
                    is AuthResult.Failure -> {
                        failed += ActivitySource.AKI_FORMS_MINE
                        failed += ActivitySource.AKI_FORMS_APPROVAL
                    }
                }
            }

            fun antrianStatus(source: ActivitySource, status: String) {
                if (source in sources) jobs += async {
                    when (val r = deliveryRepository.list(status = status)) {
                        is AuthResult.Success -> counts[source] = r.data.size
                        is AuthResult.Failure -> failed += source
                    }
                }
            }
            antrianStatus(ActivitySource.DLV_PENDING_PDI, DeliveryStatusKey.PENDING_PDI)
            antrianStatus(ActivitySource.DLV_PENDING_SPK, DeliveryStatusKey.PENDING_SPK)
            antrianStatus(ActivitySource.DLV_PENDING_NOTE, DeliveryStatusKey.PENDING_DELIVERY_NOTE)
            antrianStatus(ActivitySource.DLV_PENDING_SCHEDULING, DeliveryStatusKey.PENDING_SCHEDULING)

            // C2 audit 2026-07-28: role di DELIVERY_READ_ALL_ROLES mendapat SELURUH
            // job perusahaan dari `list_delivery` (cabang `is_manager || is_admin`
            // mengabaikan `asDriver`), bukan job miliknya — kartunya tak pernah
            // tampil (driverCardVisible), jadi jangan tembak endpointnya sama sekali.
            if (ActivitySource.DLV_AS_DRIVER in sources &&
                roles.none { it in DELIVERY_READ_ALL_ROLES }
            ) jobs += async {
                when (val r = deliveryRepository.list(asDriver = true)) {
                    is AuthResult.Success -> counts[ActivitySource.DLV_AS_DRIVER] = r.data.size
                    is AuthResult.Failure -> failed += ActivitySource.DLV_AS_DRIVER
                }
            }

            // SPK Gantung: server memberi seluruh unit terkirim yang belum
            // dikonfirmasi pembayarannya; ambang 24 jam disaring DI SINI supaya
            // kartunya benar-benar berarti "sudah lewat tenggat", bukan sekadar
            // "belum ditutup". Baris tanpa `deliveredAt` tak dihitung — tak bisa
            // dinilai umurnya, dan menebaknya berarti menuduh kasir lalai.
            if (ActivitySource.DLV_PENDING_PAYMENT in sources) jobs += async {
                when (val r = deliveryRepository.list(view = "pending_payment")) {
                    is AuthResult.Success ->
                        counts[ActivitySource.DLV_PENDING_PAYMENT] = r.data.count { isGantung(it.deliveredAt) }
                    is AuthResult.Failure -> failed += ActivitySource.DLV_PENDING_PAYMENT
                }
            }

            if (ActivitySource.DISCOUNT_PENDING in sources) jobs += async {
                when (val r = deliveryRepository.discounts(status = "pending")) {
                    // `.total`, BUKAN `.items.size` — backend membatasi `items`
                    // ke `limit = 20`, badge tak boleh ikut terpotong (I2).
                    is AuthResult.Success -> counts[ActivitySource.DISCOUNT_PENDING] = r.data.total
                    is AuthResult.Failure -> failed += ActivitySource.DISCOUNT_PENDING
                }
            }

            if (ActivitySource.RAPORT_TODAY in sources) {
                jobs += async {
                    when (val r = raportRepository.raportOfDay(todayIso, user?.id)) {
                        is AuthResult.Success -> counts[ActivitySource.RAPORT_TODAY] = r.data.size
                        is AuthResult.Failure -> failed += ActivitySource.RAPORT_TODAY
                    }
                }
                // Penyebut "x/y jobdesk". Panggilan TERPISAH dari yang di atas dan
                // sengaja TIDAK menandai RAPORT_TODAY gagal saat ia sendiri gagal:
                // jumlah yang sudah terkirim tetap benar, cuma penyebutnya yang tak
                // diketahui — dan kartu bertanda "gagal muat" gara-gara master
                // jobdesk tak terambil justru menyembunyikan angka yang valid.
                // `matchJobdeskPosition` dipakai ulang apa adanya (BUKAN matcher
                // baru) supaya penyebut di kartu ini identik dengan daftar jobdesk
                // yang user lihat begitu kartunya dibuka.
                jobs += async {
                    val r = raportRepository.jobdeskPositions()
                    if (r is AuthResult.Success) {
                        raportExpected = matchJobdeskPosition(user?.divisi.orEmpty(), r.data)
                            ?.jobdesks?.size
                    }
                }
            }

            // Kartu "Input Prospek": aktual/target dari SERVER. Ikut fan-out ini
            // (bukan panggilan berurutan) supaya tak menambah waktu buka layar.
            // Gagal = diamkan `null` → kartu jatuh ke hitungan cache lama, TIDAK
            // ditandai "gagal muat": angka cache tetap ada gunanya offline, dan
            // menandainya gagal justru menyembunyikan tugasnya dari progres.
            if (ActivitySource.LEADS_CACHE in sources) jobs += async {
                val r = crmRepository.myProspekTarget(todayIso)
                if (r is AuthResult.Success) prospekTarget = r.data
            }

            if (ActivitySource.INDENT_PENDING in sources) jobs += async {
                when (val r = listIndentUseCase(status = "menunggu")) {
                    is AuthResult.Success -> counts[ActivitySource.INDENT_PENDING] = r.data.count
                    is AuthResult.Failure -> failed += ActivitySource.INDENT_PENDING
                }
            }

            jobs.forEach { it.await() }
        }

        val leadsToday = if (ActivitySource.LEADS_CACHE in sources) {
            // `cachedLeads(search)` — kirim "" untuk seluruh cache. Room, bukan
            // jaringan: seksi tugas harian tetap benar saat offline.
            leadsCreatedTodayBy(
                leads = crmRepository.cachedLeads("").map { it.createdAt to it.createdBy },
                userId = user?.id,
                todayIso = todayIso,
            )
        } else 0

        val tasks = buildDailyTasks(
            items = items,
            checkInAt = checkInAt,
            checkOutAt = checkOutAt,
            leadsToday = leadsToday,
            // I3 audit 2026-07-28: tanpa ini, gagal jaringan tampil identik dgn
            // "belum absen" → user yang sudah check-in didorong absen lagi.
            absensiFailed = ActivitySource.ABSENSI_TODAY in failed,
            raportToday = counts[ActivitySource.RAPORT_TODAY] ?: 0,
            raportFailed = ActivitySource.RAPORT_TODAY in failed,
            raportExpected = raportExpected,
            prospekTarget = prospekTarget,
        )

        // Ada load() yang lebih baru sudah dipanggil sejak kita mulai (atau sedang
        // menulis state akhirnya) — jangan timpa dgn hasil yang sudah basi.
        if (myGeneration != loadGeneration) return

        _uiState.value = ActivityUiState(
            isLoading = false,
            userName = user?.name.orEmpty(),
            cabangName = user?.cabangName.orEmpty(),
            tasks = tasks,
            progress = dailyProgressLabel(tasks),
            queueCards = buildQueueCards(items, counts, failed, roles),
            actions = items.filter { it.kind == ActivityKind.AKSI },
            spkToday = spkTodayCounter.todayCount(todayIso),
            panduanVisible = panduanAlurVisible(roles, capabilities),
        )
    }

    private companion object {
        const val CACHE_WINDOW_MS = 60_000L
        const val GANTUNG_THRESHOLD_MS = 24 * 60 * 60 * 1000L
    }
}
