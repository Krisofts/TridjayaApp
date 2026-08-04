package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.SpkTodayCounter
import com.krisoft.tridjayaelektronik.data.model.AssignBody
import com.krisoft.tridjayaelektronik.data.model.ConfirmSpkBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryItemBody
import com.krisoft.tridjayaelektronik.data.model.DeliverBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryNoteBody
import com.krisoft.tridjayaelektronik.data.model.PdiBody
import com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.attendance.LocationProvider
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Keadaan foto bukti aki satu form. `Kosong` = `photoUrl` null/blank (form
 *  sebelum fitur foto, atau PDI tak mengunggah); `Gagal` = URL ada tapi
 *  file/jaringannya tidak menjawab — dua hal yang WAJIB terlihat berbeda oleh
 *  approver. */
/** Form mana yang fotonya perlu diambil. Dipisah jadi fungsi murni supaya
 *  invarian intinya bisa dikunci tanpa memalsukan repository: form BER-URL
 *  selalu dapat entri di peta status (jadi kartunya menampilkan Memuat/Ada/
 *  Gagal, tak pernah diam), form tanpa URL sengaja TIDAK dapat entri — itulah
 *  yang dibaca kartu sebagai "tanpa foto bukti". */
internal fun akiFormsNeedingPhoto(
    forms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>
): List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> =
    forms.filter { !it.photoUrl.isNullOrBlank() }

sealed interface AkiPhotoState {
    data object Memuat : AkiPhotoState
    data class Ada(val bitmap: Bitmap) : AkiPhotoState
    data object Gagal : AkiPhotoState
}

data class DeliveryFlowUiState(
    val loading: Boolean = false,
    val items: List<DeliveryJobDto> = emptyList(),
    val detail: DeliveryJobDto? = null,
    /** Karyawan yang sudah menangani unit yang sedang dibuka. Gagal dimuat =
     *  dibiarkan kosong tanpa pesan error: ini informasi pelengkap, tak boleh
     *  menutupi detail SPK yang justru jadi alasan orang membuka layar ini. */
    val kontributor: List<com.krisoft.tridjayaelektronik.data.model.KontributorDto> = emptyList(),
    val error: String? = null,
    val submitting: Boolean = false,
    val actionError: String? = null,
    val actionDone: Boolean = false,
    /** Checklist PDI per-kategori (untuk tahap pending_pdi). */
    val checklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto> = emptyList(),
    /** Daftar driver (untuk tahap pending_scheduling); kosong → form fallback input manual. */
    val drivers: List<com.krisoft.tridjayaelektronik.data.model.DriverDto> = emptyList(),
    /** Pengajuan diskon menunggu approval (layar approval diskon). */
    val discounts: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList(),
    /** Konteks cabang login sales — default selektor Cabang SPK (Input SPK). */
    val deliveryContext: com.krisoft.tridjayaelektronik.data.model.DeliveryContextDto? = null,
    /** Hasil autocomplete stok GS (Input SPK). */
    val stokResults: List<com.krisoft.tridjayaelektronik.data.model.StokCabangRow> = emptyList(),
    /**
     * Cabang ASAL [stokResults] — baris stok tak membawa kodeDealer sendiri,
     * jadi tanpa penanda ini daftar di layar tak bisa dibedakan milik cabang
     * mana. Layar Input SPK hanya menampilkan hasil yang cabangnya sama dengan
     * "Cabang SPK" saat itu (insiden DLV-M84149DA0, 2026-07-29: barang Pagaden
     * ter-submit dengan kode dealer Soklat, unitnya masuk antrian PDI cabang
     * yang tak memegang barangnya).
     */
    val stokDealer: String = "",
    val stokLoading: Boolean = false,
    val stokAttempted: Boolean = false,
    /** Hasil autocomplete broker KBK (Input SPK section 3). */
    val brokerResults: List<com.krisoft.tridjayaelektronik.data.model.BrokerOption> = emptyList(),
    /** Serial per `"$kodeDealer|$kodeBarang"` — picker per-item SPK multi-unit. */
    val serialOptions: Map<String, List<String>> = emptyMap(),
    /** Checklist serah-terima stage=driver (088) — kosong bila kategori tak ber-item / pre-088. */
    val driverChecklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto> = emptyList(),
    /** Gagal memuat checklist driver — FAIL-HARD: submit serah terima diblok sampai
     *  retry sukses (checklist null terkirim = 400 backend tanpa petunjuk). */
    val driverChecklistError: String? = null,
    /** Foto job ter-autentikasi utk ditampilkan di detail (key "pdi"/"delivery"/"cash"). */
    val jobPhotos: Map<String, Bitmap> = emptyMap(),
    /** Gate form aki (tahap pending_pdi, kategori ber-flag `requiresAkiForm`). */
    val requiresAki: Boolean = false,
    val akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
    /** Riwayat diskon baris SPK yang sedang dibuka — HANYA untuk timeline detail
     *  (beda dari [discounts] yang antrian approval). Kosong = tak pernah diajukan
     *  diskon, atau job lama dari worker GS (tak punya kode batch manual). */
    val jobDiscounts: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList(),
    /** Daftar riwayat (menu "Pengambilan Aki", beda dari [akiForms] yang di-scope satu job). */
    val akiList: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
    /** Status foto bukti aki per form id (key = form.id). Sengaja BUKAN
     *  `Map<String, Bitmap>` lagi: peta bitmap tak bisa membedakan "PDI memang
     *  tak memotret" dari "filenya gagal diambil", sehingga approver hanya
     *  melihat kartu kosong dan tak tahu harus menagih siapa. Insiden
     *  2026-07-29: 5 foto raib dari server, gejalanya identik dengan form lama
     *  yang wajar tanpa foto. */
    val akiPhotos: Map<String, AkiPhotoState> = emptyMap(),
    /** Status foto bukti acc diskon per pengajuan (key = `DiscountRequestDto.id`) —
     *  pola sama [akiPhotos]. */
    val diskonBuktiPhotos: Map<String, AkiPhotoState> = emptyMap(),
    /** Preview foto (sudah ber-watermark geotag+jam) — pola sama [AttendanceUiState.selfie]:
     *  bitmap dipegang di state, BUKAN dibaca ulang dari file (hindari cache-basi/race preview). */
    /** Hasil `POST /delivery` terakhir (2026-07-26) — dipakai `CreateSpkScreen` buat
     *  resolve id job PDI Mandiri dan auto-navigate langsung ke form PDI, tanpa
     *  balik dulu ke daftar. */
    val lastCreateResult: com.krisoft.tridjayaelektronik.data.model.DeliveryCreateResult? = null,
    val pdiPhoto: Bitmap? = null,
    val deliverPhoto: Bitmap? = null,
    val cashPhoto: Bitmap? = null,
    /** true setelah user tekan "Pakai Foto Ini" di dialog review pasca-jepret. Foto baru (belum
     *  di-retake) selalu mulai false → memaksa dialog review muncul sebelum foto dianggap final. */
    val pdiPhotoConfirmed: Boolean = false,
    val deliverPhotoConfirmed: Boolean = false,
    val cashPhotoConfirmed: Boolean = false,
    /** GPS untuk watermark foto — pola sama [com.krisoft.tridjayaelektronik.ui.attendance.AttendanceUiState]:
     *  diambil LEBIH AWAL (saat detail job dimuat), bukan baru dicoba saat jepret — kalau baru
     *  dicoba pas jepret, GPS cold-start belum sempat lock (ketemu nyata: PDI selalu "belum terkunci"). */
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsAccuracyM: Float? = null,
    val gpsLocating: Boolean = false,
    val gpsError: String? = null,
    val gpsDenied: Boolean = false,
    /** Alamat terbaca hasil reverse-geocode (kota/kabupaten/jalan) — `null` selama proses / gagal
     *  (offline dsb.); UI+watermark fallback ke koordinat mentah saat itu. */
    val gpsAddress: String? = null,
    /** true selagi [refreshGps] menunggu hasil geocode — terpisah dari [gpsLocating] karena fix GPS
     *  biasanya selesai duluan, lookup alamat masih jalan beberapa saat lagi di background. */
    val gpsAddressLoading: Boolean = false
)

/**
 * Alur pengiriman SPK NYATA — satu VM dipakai layar antrian per-tahap & detail, lewat
 * [DeliveryFlowRepository] (inventory-service). Tanpa cache: tiap load memanggil server; tiap aksi
 * tahap memutakhirkan job lalu memicu kembali ke daftar.
 */
@HiltViewModel
class DeliveryFlowViewModel @Inject constructor(
    private val repository: DeliveryFlowRepository,
    authRepository: AuthRepository,
    private val spkTodayCounter: SpkTodayCounter,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryFlowUiState())
    val state: StateFlow<DeliveryFlowUiState> = _state.asStateFlow()

    val currentUserName: String = authRepository.currentUserName?.trim().orEmpty().ifBlank { "Pengguna" }
    val currentUserId: String = authRepository.currentUserId?.trim().orEmpty()

    // ── Akses viewer (SpkAccessPolicy — mirror gate backend, backend tetap
    // otoritatif). REAKTIF (temuan review): dihitung dari cache saat konstruksi,
    // lalu di-refresh SEKALI dari server — approver page-grant/extra-role yang
    // di-grant SETELAH cache profil terbentuk tak kehilangan tombol.
    var isAdminViewer by androidx.compose.runtime.mutableStateOf(false)
        private set
    var canApproveAki by androidx.compose.runtime.mutableStateOf(false)
        private set
    /** Akses per-tahap (dipakai menyaring aksi di layar detail job). */
    var access by androidx.compose.runtime.mutableStateOf(SpkAccessPolicy.accessOf(null))
        private set

    private fun recomputeAccess(user: com.krisoft.tridjayaelektronik.data.model.UserDto?) {
        val roles = SpkAccessPolicy.rolesOf(user)
        val grants = SpkAccessPolicy.grantPrefixesOf(user)
        isAdminViewer = SpkAccessPolicy.isAdmin(roles)
        canApproveAki = SpkAccessPolicy.canApproveAki(roles, grants)
        access = SpkAccessPolicy.accessOf(user)
    }

    init {
        recomputeAccess(authRepository.cachedUser)
        // Refresh profil dari server SEKALI PER PROSES APP (bukan per layar —
        // VM ini dibuat ulang tiap buka layar delivery; refresh tiap kali =
        // 1 roundtrip ekstra per navigasi, terasa di jaringan lapangan).
        // Cache TokenStore sudah ter-update oleh refresh pertama.
        if (!accessProfileRefreshed) {
            accessProfileRefreshed = true
            viewModelScope.launch {
                // profile() meng-update TokenStore + fallback ke cache saat offline.
                (authRepository.profile() as? AuthResult.Success)?.let { recomputeAccess(it.data) }
            }
        }
    }

    companion object {
        /** Sekali per proses — lihat init. Login ulang me-restart proses (reset otomatis). */
        @Volatile
        private var accessProfileRefreshed = false
    }

    /** Foto serah-terima terkompres siap upload (dipisah dari state). */
    private var deliverPhotoBytes: ByteArray? = null
    private var pdiPhotoBytes: ByteArray? = null
    private var cashPhotoBytes: ByteArray? = null

    private val serialFetched = mutableSetOf<String>()

    fun loadQueue(status: String?, view: String? = null, asDriver: Boolean = false) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.list(status = status, view = view, asDriver = asDriver)) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, items = res.data, error = null) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    /** Geser urutan muatan driver (manifest). Optimistic; gagal → reload + error. */
    fun moveLoad(id: String, up: Boolean) {
        val current = _state.value.items
        val idx = current.indexOfFirst { it.id == id }
        val target = if (up) idx - 1 else idx + 1
        if (idx == -1 || target < 0 || target >= current.size) return
        val swapped = current.toMutableList().apply { val t = this[idx]; this[idx] = this[target]; this[target] = t }
        _state.update { it.copy(items = swapped) }
        viewModelScope.launch {
            when (val res = repository.reorderLoads(swapped.map { it.id })) {
                is AuthResult.Success -> {}
                is AuthResult.Failure -> {
                    _state.update { it.copy(actionError = res.message) }
                    loadQueue(status = null, view = null)
                }
            }
        }
    }

    fun loadDetail(id: String) {
        _state.update {
            it.copy(
                loading = true, error = null, actionDone = false, actionError = null,
                kontributor = emptyList(), driverChecklist = emptyList(),
                driverChecklistError = null, jobPhotos = emptyMap(),
                pdiPhoto = null, deliverPhoto = null, cashPhoto = null,
                pdiPhotoConfirmed = false, deliverPhotoConfirmed = false, cashPhotoConfirmed = false
            )
        }
        deliverPhotoBytes = null
        pdiPhotoBytes = null
        cashPhotoBytes = null
        viewModelScope.launch {
            when (val res = repository.detail(id)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(loading = false, detail = res.data) }
                    loadAuxFor(res.data)
                    loadTimelineExtras(res.data)
                    loadJobPhotos(res.data)
                    loadKontributor(id)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
        // Konteks juga dibutuhkan layar detail (flag driverGateEnabled — gate
        // serah-terima klien mengikuti kill-switch server). Cached, fail-soft.
        loadDeliveryContextForCreate()
        refreshGps()
    }

    /** Muat foto job ter-autentikasi (bukti PDI / serah terima / uang) utk preview
     *  di detail — fail-soft per foto (gagal = tak tampil, tanpa error). */
    /** Fail-soft: gagal = daftar dibiarkan kosong, layar detail tetap utuh. */
    private fun loadKontributor(id: String) {
        viewModelScope.launch {
            when (val res = repository.kontributor(id)) {
                is AuthResult.Success -> _state.update { it.copy(kontributor = res.data) }
                is AuthResult.Failure -> Unit
            }
        }
    }

    private fun loadJobPhotos(job: DeliveryJobDto) {
        val urls = listOfNotNull(
            job.pdiReadyPhotoUrl?.takeIf { it.isNotBlank() }?.let { "pdi" to it },
            job.deliveryPhotoUrl?.takeIf { it.isNotBlank() }?.let { "delivery" to it },
            job.cashPhotoUrl?.takeIf { it.isNotBlank() }?.let { "cash" to it },
        )
        urls.forEach { (key, url) ->
            viewModelScope.launch {
                val bytes = repository.fetchPhoto(url) ?: return@launch
                val bmp = withContext(Dispatchers.Default) {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } ?: return@launch
                _state.update { it.copy(jobPhotos = it.jobPhotos + (key to bmp)) }
            }
        }
    }

    /** Ambil satu titik GPS lebih awal (dipakai watermark foto PDI/serah-terima/uang saat jepret). */
    fun refreshGps() {
        if (_state.value.gpsLocating) return
        _state.update { it.copy(gpsLocating = true, gpsError = null, gpsDenied = false, gpsAddress = null, gpsAddressLoading = false) }
        viewModelScope.launch {
            if (!LocationProvider.hasPermission(appContext)) {
                _state.update { it.copy(gpsLocating = false, gpsDenied = true) }
                return@launch
            }
            val loc = LocationProvider.current(appContext)
            if (loc == null) {
                _state.update { it.copy(gpsLocating = false, gpsError = "Tidak bisa mendapatkan lokasi. Pastikan GPS aktif.") }
            } else {
                _state.update {
                    it.copy(
                        gpsLocating = false, gpsError = null,
                        gpsLat = loc.latitude, gpsLng = loc.longitude,
                        gpsAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                        gpsAddressLoading = true
                    )
                }
                // Alamat terbaca (kota/kabupaten/tempat) dicari terpisah, tak menahan fix GPS —
                // gagal/lambat (offline dsb.) tetap fail-soft, UI+watermark fallback ke koordinat.
                val address = LocationProvider.addressFor(appContext, loc.latitude, loc.longitude)
                _state.update { it.copy(gpsAddress = address, gpsAddressLoading = false) }
            }
        }
    }

    /**
     * Data timeline yang hidup di TABEL SAMPING — approval diskon
     * (`discount_requests`) dan form aki (`delivery_aki_forms`). Dimuat untuk
     * SEMUA status, sengaja TERPISAH dari [loadAuxFor] yang stage-specific:
     * approval diskon terjadi di `pending_discount` dan form aki di
     * `pending_pdi`, tapi keduanya harus tetap kelihatan di timeline setelah
     * tahapnya lewat. Bug 2026-07-27: form aki cuma dimuat saat status
     * `pending_pdi`, jadi SPK sepeda listrik yang tertahan di
     * `pending_discount` tak pernah menampilkan approval aki maupun diskon.
     *
     * Fail-soft penuh: gagal/kosong = step-nya tak muncul, detail tetap kebuka.
     */
    private fun loadTimelineExtras(job: DeliveryJobDto) {
        viewModelScope.launch {
            val forms = (repository.jobAkiForms(job.id) as? AuthResult.Success)?.data.orEmpty()
            _state.update { it.copy(akiForms = if (it.akiForms.isEmpty()) forms else it.akiForms) }
        }
        // Kode batch cuma ada di job input manual sales (`DLV-M{batch}-{baris}u{seq}`);
        // job worker GS lama tak pernah punya discount_request.
        val baris = job.baris ?: return
        val unitSeq = job.unitSeq ?: return
        val suffix = "-${baris}u$unitSeq"
        if (job.inputChannel != "manual" || !job.kodePengiriman.endsWith(suffix)) return
        val batch = job.kodePengiriman.removeSuffix(suffix)
        viewModelScope.launch {
            val history = (repository.discountHistory(batch, baris) as? AuthResult.Success)?.data.orEmpty()
            _state.update { it.copy(jobDiscounts = history) }
        }
    }

    /** Muat data pendukung sesuai tahap: checklist PDI (pending_pdi) atau daftar driver (pending_scheduling). */
    private fun loadAuxFor(job: DeliveryJobDto) {
        when (job.status) {
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PDI,
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PERBAIKAN -> {
                val kategori = job.kategori?.trim().orEmpty()
                if (kategori.isNotEmpty()) viewModelScope.launch {
                    (repository.checklist(kategori) as? AuthResult.Success)?.let { r -> _state.update { it.copy(checklist = r.data) } }
                }
                viewModelScope.launch {
                    val cats = (repository.categories() as? AuthResult.Success)?.data.orEmpty()
                    val need = cats.any { it.requiresAkiForm && it.kategori.equals(job.kategori?.trim(), ignoreCase = true) }
                    val forms = if (need) (repository.jobAkiForms(job.id) as? AuthResult.Success)?.data.orEmpty() else emptyList()
                    _state.update { it.copy(requiresAki = need, akiForms = forms) }
                }
            }
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_SCHEDULING -> viewModelScope.launch {
                (repository.drivers() as? AuthResult.Success)?.let { r -> _state.update { it.copy(drivers = r.data) } }
            }
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.ASSIGNED,
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.IN_TRANSIT ->
                loadDriverChecklist(job)
        }
    }

    /** Checklist serah-terima stage=driver (088) — FAIL-HARD: gagal fetch →
     *  `driverChecklistError` terisi, tombol serah terima diblok sampai retry
     *  sukses. Tanpa ini checklist null terkirim → 400 backend "Checklist serah
     *  terima driver wajib diisi" tanpa petunjuk di UI (temuan audit 2026-07-23). */
    fun loadDriverChecklist(job: DeliveryJobDto) {
        // 088 aktif? (driverTerimaUang selalu terisi pasca-088). Pre-088 JANGAN
        // fetch stage=driver — backend lama abaikan param & balik item PDI.
        val kategori = job.kategori?.trim().orEmpty()
        if (job.driverTerimaUang == null || kategori.isEmpty()) return
        _state.update { it.copy(driverChecklistError = null) }
        viewModelScope.launch {
            when (val r = repository.checklist(kategori, stage = "driver")) {
                is AuthResult.Success ->
                    _state.update { it.copy(driverChecklist = r.data, driverChecklistError = null) }
                is AuthResult.Failure ->
                    _state.update { it.copy(driverChecklistError = r.message) }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    // ── Approval diskon per-baris ────────────────────────────────────────────
    fun loadDiscounts(status: String? = "pending") {
        // Peta foto dikosongkan di sini, bukan cuma ditimpa: item yang sudah
        // diputuskan hilang dari antrian, dan bitmap-nya ikut dibuang.
        _state.update { it.copy(loading = true, error = null, diskonBuktiPhotos = emptyMap()) }
        viewModelScope.launch {
            when (val res = repository.discounts(status)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(loading = false, discounts = res.data.items, error = null) }
                    loadDiscountPhotos(res.data.items)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    /** Muat foto bukti acc diskon per pengajuan — pola sama [loadAkiPhotos].
     *  Tiga keadaan dibedakan (memuat / ada / gagal) supaya approver tahu
     *  bedanya "sales tak melampirkan" dan "filenya hilang dari server". */
    private fun loadDiscountPhotos(items: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>) {
        items.filter { !it.buktiUrl.isNullOrBlank() }.forEach { d ->
            val url = d.buktiUrl.orEmpty()
            _state.update { it.copy(diskonBuktiPhotos = it.diskonBuktiPhotos + (d.id to AkiPhotoState.Memuat)) }
            viewModelScope.launch {
                val bytes = repository.fetchPhoto(url)
                val bmp = bytes?.let {
                    withContext(Dispatchers.Default) {
                        android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }
                _state.update {
                    it.copy(
                        diskonBuktiPhotos = it.diskonBuktiPhotos +
                            (d.id to (bmp?.let(AkiPhotoState::Ada) ?: AkiPhotoState.Gagal))
                    )
                }
            }
        }
    }

    fun approveDiscount(id: String, note: String) = discountAction { repository.approveDiscount(id, note) }
    fun rejectDiscount(id: String, note: String) = discountAction { repository.rejectDiscount(id, note) }

    private fun discountAction(block: suspend () -> AuthResult<*>) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = block()) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadDiscounts("pending") // muat ulang: item yang diputuskan hilang dari antrian
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    // ── Foto (PDI ready / serah terima / terima uang) — watermark geotag+jam, pola SAMA
    // [com.krisoft.tridjayaelektronik.ui.attendance.AttendanceViewModel.onSelfieCaptured]: preview
    // dipegang sebagai Bitmap DI STATE (bukan dibaca ulang dari file lewat Coil/AsyncImage) —
    // menghindari 2 footgun yang sempat ketemu di sini: (a) race kalau UI flip "foto siap" sebelum
    // watermark async selesai, (b) Coil meng-cache bitmap mentah berbasis path file yang isinya
    // berubah-ubah (file capture ditulis ulang tiap retake, key cache Coil tidak tahu itu).
    fun onPdiPhotoCaptured(file: File) = viewModelScope.launch {
        val prepared = watermarked(file, "TRIDJAYA · PDI")
        pdiPhotoBytes = prepared?.first
        _state.update { it.copy(pdiPhoto = prepared?.second, pdiPhotoConfirmed = false) }
    }

    fun hasPdiPhoto(): Boolean = pdiPhotoBytes != null

    /** User menekan "Pakai Foto Ini" di dialog review pasca-jepret. */
    fun confirmPdiPhoto() = _state.update { it.copy(pdiPhotoConfirmed = true) }

    /** User menekan "Ambil Ulang" — buang hasil jepretan, biar tombol kamera bisa dipakai lagi. */
    fun retakePdiPhoto() {
        pdiPhotoBytes = null
        _state.update { it.copy(pdiPhoto = null, pdiPhotoConfirmed = false) }
    }

    fun onDeliverPhotoCaptured(file: File) = viewModelScope.launch {
        val prepared = watermarked(file, "TRIDJAYA · SERAH TERIMA")
        deliverPhotoBytes = prepared?.first
        _state.update { it.copy(deliverPhoto = prepared?.second, deliverPhotoConfirmed = false) }
    }

    fun hasDeliverPhoto(): Boolean = deliverPhotoBytes != null

    fun confirmDeliverPhoto() = _state.update { it.copy(deliverPhotoConfirmed = true) }

    fun retakeDeliverPhoto() {
        deliverPhotoBytes = null
        _state.update { it.copy(deliverPhoto = null, deliverPhotoConfirmed = false) }
    }

    fun onCashPhotoCaptured(file: File) = viewModelScope.launch {
        val prepared = watermarked(file, "TRIDJAYA · TERIMA UANG")
        cashPhotoBytes = prepared?.first
        _state.update { it.copy(cashPhoto = prepared?.second, cashPhotoConfirmed = false) }
    }

    fun hasCashPhoto(): Boolean = cashPhotoBytes != null

    fun confirmCashPhoto() = _state.update { it.copy(cashPhotoConfirmed = true) }

    fun retakeCashPhoto() {
        cashPhotoBytes = null
        _state.update { it.copy(cashPhoto = null, cashPhotoConfirmed = false) }
    }

    /** Foto PO per-barang (2026-07-24, koreksi dari slot global — Pre Order
     *  melekat ke produk, SPK bisa multi-barang tiap satu foto sendiri).
     *  Watermark+upload langsung (bukan slot review terpisah spt PDI/deliver
     *  — tak ada GPS-timing kritis di sini, cukup capture→upload sekali jalan,
     *  pola sama web `uploadDeliveryPhoto` on-file-select). Return `null` kalau
     *  watermark/upload gagal — caller (kartu barang) tampilkan toast error.
     *  Subtitle watermark ikut fallback [watermarked] (nama user saja, kode
     *  SPK belum ada saat ini). */
    suspend fun uploadPoPhoto(file: File): String? {
        val prepared = watermarked(file, "TRIDJAYA · NO PO") ?: return null
        return when (val up = repository.uploadPhoto(prepared.first, "po_${System.currentTimeMillis()}.jpg")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> null
        }
    }

    /** Foto bukti acc diskon (2026-08-01) — pola sama [uploadPoPhoto]:
     *  watermark lalu unggah ke endpoint foto delivery yang sama. Return
     *  `null` kalau gagal. */
    suspend fun uploadBuktiAccPhoto(file: File): String? {
        val prepared = watermarked(file, "TRIDJAYA · ACC DISKON") ?: return null
        return when (val up = repository.uploadPhoto(prepared.first, "acc_diskon_${System.currentTimeMillis()}.jpg")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> null
        }
    }

    /** Foto bukti aki (2026-07-24, wajib) — capture→watermark→upload langsung,
     *  pola sama [uploadPoPhoto]. Return `null` kalau gagal. */
    suspend fun uploadAkiPhoto(file: File): String? {
        val prepared = watermarked(file, "TRIDJAYA · BUKTI AKI") ?: return null
        return when (val up = repository.uploadPhoto(prepared.first, "aki_${System.currentTimeMillis()}.jpg")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> null
        }
    }

    /**
     * GPS best-effort: pakai titik yang SUDAH di-prime oleh [refreshGps] (dipanggil saat detail job
     * dimuat) — bukan menarik lokasi baru di sini. Gagal/izin ditolak → watermark timestamp saja,
     * JANGAN blokir foto. Subtitle = nama · kode SPK job aktif (kalau sudah termuat).
     */
    private suspend fun watermarked(file: File, title: String): Pair<ByteArray, Bitmap>? {
        val s = _state.value
        val kode = s.detail?.kodePengiriman.orEmpty()
        val subtitle = listOf(currentUserName, kode).filter { it.isNotBlank() }.joinToString(" · ")
        return withContext(Dispatchers.Default) {
            PhotoWatermark.prepareWatermarkedJpeg(file, s.gpsLat, s.gpsLng, title, subtitle, s.gpsAccuracyM, s.gpsAddress)
        }
    }

    // ── Aksi tahap ───────────────────────────────────────────────────────────

    // ── Input SPK: cabang + autocomplete stok ────────────────────────────────

    /** Muat konteks cabang login sekali (default selektor Cabang SPK). Fail-soft. */
    fun loadDeliveryContextForCreate() {
        if (_state.value.deliveryContext != null) return
        viewModelScope.launch {
            (repository.context() as? AuthResult.Success)?.let { r ->
                _state.update { it.copy(deliveryContext = r.data) }
            }
        }
    }

    /** Pencarian stok yang sedang berjalan — dibatalkan tiap pencarian baru.
     *  Tanpa ini respons cabang LAMA mendarat setelah user pindah cabang dan
     *  mengisi ulang daftar (lihat [DeliveryFlowUiState.stokDealer]). */
    private var stokJob: Job? = null

    /** Autocomplete barang — dipanggil UI setelah debounce. `query` < 2 char atau
     *  `kodeDealer` kosong → kosongkan hasil tanpa panggil server. */
    fun searchStok(query: String, kodeDealer: String) {
        stokJob?.cancel()
        val term = query.trim()
        val dealer = kodeDealer.trim()
        if (term.length < 2 || dealer.isBlank()) {
            _state.update {
                it.copy(stokResults = emptyList(), stokDealer = dealer, stokLoading = false, stokAttempted = false)
            }
            return
        }
        _state.update { it.copy(stokLoading = true) }
        stokJob = viewModelScope.launch {
            when (val res = repository.stokCabang(term, dealer)) {
                is AuthResult.Success -> _state.update {
                    it.copy(stokLoading = false, stokResults = res.data, stokDealer = dealer, stokAttempted = true)
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(stokLoading = false, stokResults = emptyList(), stokDealer = dealer, stokAttempted = true)
                }
            }
        }
    }

    fun searchBrokers(q: String) {
        val term = q.trim()
        if (term.length < 2) { _state.update { it.copy(brokerResults = emptyList()) }; return }
        viewModelScope.launch {
            (repository.searchBrokers(term) as? AuthResult.Success)?.let { r ->
                _state.update { it.copy(brokerResults = r.data) }
            }
        }
    }

    fun clearBrokerResults() = _state.update { it.copy(brokerResults = emptyList()) }

    /** Fetch serial sekali per `cabang|kode` (cache); fail-soft. */
    fun ensureSerials(kodeDealer: String, kodeBarang: String) {
        if (kodeDealer.isBlank() || kodeBarang.isBlank()) return
        val key = "$kodeDealer|$kodeBarang"
        if (!serialFetched.add(key)) return
        viewModelScope.launch {
            (repository.serialNumbers(kodeDealer, kodeBarang) as? AuthResult.Success)?.let { r ->
                _state.update { it.copy(serialOptions = it.serialOptions + (key to r.data)) }
            }
        }
    }

    /** Reset cache serial (ganti cabang SPK). */
    fun clearSerialCache() {
        serialFetched.clear()
        _state.update { it.copy(serialOptions = emptyMap()) }
    }

    // Foto PO (2026-07-24, per-barang): sudah ter-upload & ter-set ke
    // `item.poPhotoUrl` masing-masing SEBELUM submit (lihat [uploadPoPhoto],
    // dipanggil kartu barang saat capture) — body di sini sudah lengkap.
    fun createSpk(body: CreateDeliveryBody) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null, actionDone = false, lastCreateResult = null) }
        viewModelScope.launch {
            when (val res = repository.create(body)) {
                is AuthResult.Success -> {
                    // Angka informatif kartu "Buat SPK" di layar Activity (lokal per-device).
                    spkTodayCounter.increment(KlasemenStandings.todayIso())
                    _state.update {
                        it.copy(submitting = false, actionDone = true, actionError = null, lastCreateResult = res.data)
                    }
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }


    fun submitPdi(id: String, serial: String, engine: String, checklist: List<PdiChecklistItemBody>, onDone: () -> Unit) = action {
        val photoUrl = pdiPhotoBytes?.let { bytes ->
            when (val up = repository.uploadPhoto(bytes, "pdi_${System.currentTimeMillis()}.jpg")) {
                is AuthResult.Success -> up.data
                is AuthResult.Failure -> return@action up
            }
        }
        val res = repository.submitPdi(id, PdiBody(serialNumber = serial.trim(), engineNumber = engine.trim().ifBlank { null }, readyPhotoUrl = photoUrl, checklist = checklist))
        if (res is AuthResult.Success &&
            res.data.status == com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PERBAIKAN
        ) {
            // Unit DITAHAN (ada jawaban "Tidak" + saklar cabang menyala). Server
            // menjawab 200, tapi meneruskannya sebagai Success membuat wrapper
            // `action` menyetel `actionDone` → layar pop-back ke antrian TANPA
            // SATU KALIMAT PUN; unit tampak "beres" padahal justru berhenti.
            // Dipulangkan sebagai Failure SENGAJA: wrapper menaruh pesannya di
            // `actionError` (satu-satunya kanal pesan layar ini — dan ini memang
            // peringatan) dan petugas TETAP di detail, yang di-reload dulu
            // supaya badge merah "Ditahan — Perbaikan" ikut tampil.
            loadDetail(id)
            return@action AuthResult.Failure(
                "pdi_ditahan",
                "Unit DITAHAN — ada item checklist dijawab \"Tidak\". Perbaiki lalu PDI ulang, atau minta kepala cabang melepaskannya.",
            )
        }
        res.mapOk { onDone() }
    }

    /** Simpan satu form pengambilan aki (gate PDI kategori ber-flag `requiresAkiForm`). */
    fun createAkiForm(id: String, body: com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody, onDone: () -> Unit) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.createAkiForm(id, body)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false, akiForms = it.akiForms + res.data) }
                    onDone()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /** Riwayat form aki (menu "Pengambilan Aki"). */
    fun loadAkiForms() {
        _state.update { it.copy(loading = true, error = null, akiPhotos = emptyMap()) }
        viewModelScope.launch {
            when (val res = repository.akiForms()) {
                is AuthResult.Success -> {
                    _state.update { it.copy(loading = false, akiList = res.data, error = null) }
                    loadAkiPhotos(res.data)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    /** Muat foto bukti aki ter-autentikasi per form — fail-soft per foto (pola
     *  sama [loadJobPhotos]). */
    private fun loadAkiPhotos(forms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>) {
        akiFormsNeedingPhoto(forms).forEach { form ->
            val url = form.photoUrl.orEmpty()
            _state.update { it.copy(akiPhotos = it.akiPhotos + (form.id to AkiPhotoState.Memuat)) }
            viewModelScope.launch {
                // Gagal di tahap MANA pun berakhir sama bagi approver: fotonya
                // tak bisa dilihat. Yang penting ia tahu itu kegagalan, bukan
                // ketiadaan — dulu keduanya sama-sama senyap.
                val bytes = repository.fetchPhoto(url)
                val bmp = bytes?.let {
                    withContext(Dispatchers.Default) {
                        android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }
                _state.update {
                    it.copy(
                        akiPhotos = it.akiPhotos +
                            (form.id to (bmp?.let(AkiPhotoState::Ada) ?: AkiPhotoState.Gagal))
                    )
                }
            }
        }
    }

    fun markAkiReturned(id: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.returnAkiForm(id)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadAkiForms()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /** Setujui form aki — approval TUNGGAL (redesain 2026-07-24), tanpa slot.
     *  Muat ulang daftar setelah sukses. */
    fun approveAki(id: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.approveAkiForm(id)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadAkiForms()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /** Tolak form aki (alasan wajib). Muat ulang daftar setelah sukses. */
    fun rejectAki(id: String, reason: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.rejectAkiForm(id, reason)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadAkiForms()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    fun confirmSpk(
        id: String,
        noTransaksi: String,
        kasirKonfirmasiPembayaran: Boolean? = null,
        kasirDpDiterima: Double? = null,
        onDone: () -> Unit,
    ) = action {
        repository.confirmSpk(
            id,
            ConfirmSpkBody(
                noTransaksi = noTransaksi.trim(),
                kasirKonfirmasiPembayaran = kasirKonfirmasiPembayaran,
                kasirDpDiterima = kasirDpDiterima,
            ),
        ).mapOk { onDone() }
    }

    /**
     * Kasir: konfirmasi uang penjualan sudah diterima (semua jenis pembayaran,
     * bukan cuma COD). Foto bukti dipakai dari slot `deliverPhoto` yang sama —
     * job berstatus `delivered` tak pernah bersamaan dengan job in_transit di
     * layar yang sama, pola persis [selfPickupComplete].
     */
    fun setoranKasir(id: String, nominal: Double, onDone: () -> Unit) = action {
        val bytes = deliverPhotoBytes ?: return@action AuthResult.Failure("validation", "Foto bukti wajib diambil")
        val photoUrl = when (val up = repository.uploadPhoto(bytes, "setoran_${System.currentTimeMillis()}.jpg")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> return@action up
        }
        repository.setoranKasir(
            id,
            com.krisoft.tridjayaelektronik.data.model.SetoranKasirBody(nominalDiterima = nominal, photoUrl = photoUrl),
        ).mapOk { onDone() }
    }

    fun issueDeliveryNote(id: String, sourceBranch: String, onDone: () -> Unit) = action {
        repository.issueDeliveryNote(id, DeliveryNoteBody(sourceBranch = sourceBranch.trim())).mapOk { onDone() }
    }

    fun assign(id: String, driverId: String, driverName: String, scheduledDate: String, customerMapUrl: String?, onDone: () -> Unit) = action {
        repository.assign(id, AssignBody(driverId = driverId.trim(), driverName = driverName.trim().ifBlank { null }, scheduledDate = scheduledDate.trim(), customerMapUrl = customerMapUrl))
            .mapOk { onDone() }
    }

    fun dispatch(id: String, onDone: () -> Unit) = action { repository.dispatch(id).mapOk { onDone() } }

    /** 088: tandai sudah chat konsumen — refresh detail job (consumerChatAt terisi). */
    fun chatConsumer(id: String) = jobUpdate { repository.chatConsumer(id) }

    /**
     * 111: ambil / lepas klaim PDI. Klaim OPSIONAL di server (job tak diklaim
     * tetap boleh di-PDI), jadi kegagalan di sini TIDAK boleh menutup jalan
     * kerja: form PDI tetap seperti sebelum tombol ditekan, dan pesan server
     * ditampilkan apa adanya — pada 409 pesan itulah satu-satunya tempat nama
     * pemegang klaim disebutkan.
     */
    /**
     * Sunting isi SPK (administrator). Sengaja BUKAN [jobUpdate]: balasannya
     * membungkus job di dalam `{job, konsumenDiubah}`, dan memaksanya ke bentuk
     * yang sama akan membuang angka fan-out konsumen yang justru ingin
     * dilaporkan ke penyuntingnya.
     *
     * `onDone` dipanggil HANYA saat sukses — dialog menutup dirinya di situ.
     * Gagal tetap membiarkan dialog terbuka dengan isian utuh, supaya koreksi
     * yang ditolak server (mis. NIK kurang digit) tak perlu diketik ulang.
     */
    fun editJob(id: String, patch: kotlinx.serialization.json.JsonObject, onDone: (Int) -> Unit) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.editJob(id, patch)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false, detail = res.data.job) }
                    onDone(res.data.konsumenDiubah)
                }
                is AuthResult.Failure ->
                    _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    fun claimPdi(id: String) = jobUpdate { repository.claimPdi(id) }

    fun releasePdiClaim(id: String) = jobUpdate { repository.releasePdiClaim(id) }

    /** Aksi yang MEMUTAKHIRKAN job yang sedang dibuka, bukan menyelesaikan
     *  tahapnya — sengaja TIDAK menyetel `actionDone` (layar detail memakai
     *  flag itu untuk menutup dirinya sendiri). */
    private fun jobUpdate(block: suspend () -> AuthResult<DeliveryJobDto>) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = block()) {
                is AuthResult.Success -> _state.update { it.copy(submitting = false, detail = res.data) }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    fun deliver(id: String, rating: Int, comment: String, checklist: List<PdiChecklistItemBody>, onDone: () -> Unit) = action {
        val bytes = deliverPhotoBytes ?: return@action AuthResult.Failure("validation", "Foto serah terima wajib diambil")
        val photoUrl = when (val up = repository.uploadPhoto(bytes, "deliver_${System.currentTimeMillis()}.jpg")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> return@action up
        }
        // Foto uang (088) — hanya di-upload bila diambil; gate wajib ada di UI + backend.
        val cashUrl = cashPhotoBytes?.let { cb ->
            when (val up = repository.uploadPhoto(cb, "cash_${System.currentTimeMillis()}.jpg")) {
                is AuthResult.Success -> up.data
                is AuthResult.Failure -> return@action up
            }
        }
        // GPS best-effort (pola sama absensi): null bila izin ditolak/gagal fix — JANGAN blokir serah terima.
        //
        // PAKAI ULANG fix yang sudah dipanaskan `refreshGps()` saat layar detail
        // dibuka; minta baru HANYA kalau memang belum ada. Dulu selalu meminta
        // ulang di sini, jadi driver menunggu fix kedua tepat saat menekan kirim
        // — keluhan "geotag lama" 2026-07-28. Bonus kebenaran: koordinat yang
        // dikirim kini SAMA dengan yang tercetak di watermark foto (keduanya
        // dari `gpsLat`/`gpsLng`); sebelumnya dua fix berbeda bisa berselisih.
        val warm = _state.value
        val lat = warm.gpsLat
        val lng = warm.gpsLng
        val loc = if (lat != null && lng != null) null else LocationProvider.current(appContext)
        repository.deliver(
            id,
            DeliverBody(
                photoUrl = photoUrl, lat = lat ?: loc?.latitude, lng = lng ?: loc?.longitude, reviewRating = rating,
                reviewComment = comment.trim().ifBlank { null },
                checklist = checklist.ifEmpty { null }, cashPhotoUrl = cashUrl
            )
        ).mapOk { onDone() }
    }

    fun cancel(id: String, reason: String, onDone: () -> Unit) = action {
        repository.cancel(id, reason.trim().ifBlank { "-" }).mapOk { onDone() }
    }

    /** (2026-07-24) DC/admin tandai job `self_pickup` selesai — reuse slot foto
     *  [deliverPhotoBytes] (tidak bentrok: self-pickup-complete di `pending_scheduling`,
     *  `deliver` di `in_transit`, tak pernah sama job di saat sama). */
    fun selfPickupComplete(id: String, rating: Int, comment: String, onDone: () -> Unit) = action {
        val bytes = deliverPhotoBytes ?: return@action AuthResult.Failure("validation", "Foto wajib diambil")
        val photoUrl = when (val up = repository.uploadPhoto(bytes, "selfpickup_${System.currentTimeMillis()}.jpg")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> return@action up
        }
        repository.selfPickupComplete(
            id,
            com.krisoft.tridjayaelektronik.data.model.SelfPickupCompleteBody(
                photoUrl = photoUrl, reviewRating = rating, reviewComment = comment.trim().ifBlank { null }
            )
        ).mapOk { onDone() }
    }

    private inline fun <T> AuthResult<T>.mapOk(onOk: () -> Unit): AuthResult<T> {
        if (this is AuthResult.Success) onOk()
        return this
    }

    private fun action(block: suspend () -> AuthResult<*>) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null, actionDone = false) }
        viewModelScope.launch {
            when (val res = block()) {
                is AuthResult.Success -> _state.update { it.copy(submitting = false, actionDone = true, actionError = null) }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

}
