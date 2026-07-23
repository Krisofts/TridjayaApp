package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.model.AssignBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryItemBody
import com.krisoft.tridjayaelektronik.data.model.DeliverBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryNoteBody
import com.krisoft.tridjayaelektronik.data.model.PdiBody
import com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody
import com.krisoft.tridjayaelektronik.ui.attendance.LocationProvider
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DeliveryFlowUiState(
    val loading: Boolean = false,
    val items: List<DeliveryJobDto> = emptyList(),
    val detail: DeliveryJobDto? = null,
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
    /** Daftar riwayat (menu "Pengambilan Aki", beda dari [akiForms] yang di-scope satu job). */
    val akiList: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
    /** Preview foto (sudah ber-watermark geotag+jam) — pola sama [AttendanceUiState.selfie]:
     *  bitmap dipegang di state, BUKAN dibaca ulang dari file (hindari cache-basi/race preview). */
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
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryFlowUiState())
    val state: StateFlow<DeliveryFlowUiState> = _state.asStateFlow()

    val currentUserName: String = authRepository.currentUserName?.trim().orEmpty().ifBlank { "Pengguna" }
    val currentUserId: String = authRepository.currentUserId?.trim().orEmpty()

    // ── Akses viewer (SpkAccessPolicy — mirror gate backend, backend tetap
    // otoritatif). Dipakai gating aksi layar detail + tombol approval aki.
    private val viewerRoles = SpkAccessPolicy.rolesOf(authRepository.cachedUser)
    private val viewerGrants = SpkAccessPolicy.grantPrefixesOf(authRepository.cachedUser)
    val isAdminViewer: Boolean = SpkAccessPolicy.isAdmin(viewerRoles)
    val canApproveAki: Boolean = SpkAccessPolicy.canApproveAki(viewerRoles, viewerGrants)
    /** Admin/manager wajib pilih slot eksplisit saat approve aki (backend 400 tanpa slot). */
    val akiNeedsSlot: Boolean = SpkAccessPolicy.akiNeedsSlot(viewerRoles)
    /** Akses per-tahap (dipakai menyaring aksi di layar detail job). */
    val access: SpkHubAccess = SpkAccessPolicy.accessOf(authRepository.cachedUser)

    /** Foto serah-terima terkompres siap upload (dipisah dari state). */
    private var deliverPhotoBytes: ByteArray? = null
    private var pdiPhotoBytes: ByteArray? = null
    private var cashPhotoBytes: ByteArray? = null

    private val serialFetched = mutableSetOf<String>()

    fun loadQueue(status: String?, view: String? = null) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.list(status = status, view = view)) {
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
                loading = true, error = null, actionDone = false, actionError = null, driverChecklist = emptyList(),
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
                    loadJobPhotos(res.data)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
        refreshGps()
    }

    /** Muat foto job ter-autentikasi (bukti PDI / serah terima / uang) utk preview
     *  di detail — fail-soft per foto (gagal = tak tampil, tanpa error). */
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

    /** Muat data pendukung sesuai tahap: checklist PDI (pending_pdi) atau daftar driver (pending_scheduling). */
    private fun loadAuxFor(job: DeliveryJobDto) {
        when (job.status) {
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PDI -> {
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
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.discounts(status)) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, discounts = res.data, error = null) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
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

    /** Autocomplete barang — dipanggil UI setelah debounce. `query` < 2 char atau
     *  `kodeDealer` kosong → kosongkan hasil tanpa panggil server. */
    fun searchStok(query: String, kodeDealer: String) {
        val term = query.trim()
        if (term.length < 2 || kodeDealer.isBlank()) {
            _state.update { it.copy(stokResults = emptyList(), stokLoading = false, stokAttempted = false) }
            return
        }
        _state.update { it.copy(stokLoading = true) }
        viewModelScope.launch {
            when (val res = repository.stokCabang(term, kodeDealer)) {
                is AuthResult.Success -> _state.update { it.copy(stokLoading = false, stokResults = res.data, stokAttempted = true) }
                is AuthResult.Failure -> _state.update { it.copy(stokLoading = false, stokResults = emptyList(), stokAttempted = true) }
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

    fun createSpk(body: CreateDeliveryBody, onDone: () -> Unit) = action {
        repository.create(body).mapOk { onDone() }
    }

    fun submitPdi(id: String, serial: String, engine: String, checklist: List<PdiChecklistItemBody>, onDone: () -> Unit) = action {
        val photoUrl = pdiPhotoBytes?.let { bytes ->
            when (val up = repository.uploadPhoto(bytes, "pdi_${System.currentTimeMillis()}.jpg")) {
                is AuthResult.Success -> up.data
                is AuthResult.Failure -> return@action up
            }
        }
        repository.submitPdi(id, PdiBody(serialNumber = serial.trim(), engineNumber = engine.trim().ifBlank { null }, readyPhotoUrl = photoUrl, checklist = checklist))
            .mapOk { onDone() }
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
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.akiForms()) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, akiList = res.data, error = null) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
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

    /** Setujui form aki (slot di-derive backend dari role approver; admin/manager
     *  kirim `slot` eksplisit). Muat ulang daftar setelah sukses. */
    fun approveAki(id: String, slot: String? = null) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.approveAkiForm(id, slot)) {
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

    fun confirmSpk(id: String, onDone: () -> Unit) = action { repository.confirmSpk(id).mapOk { onDone() } }

    fun issueDeliveryNote(id: String, sourceBranch: String, onDone: () -> Unit) = action {
        repository.issueDeliveryNote(id, DeliveryNoteBody(sourceBranch = sourceBranch.trim())).mapOk { onDone() }
    }

    fun assign(id: String, driverId: String, driverName: String, scheduledDate: String, customerMapUrl: String?, onDone: () -> Unit) = action {
        repository.assign(id, AssignBody(driverId = driverId.trim(), driverName = driverName.trim().ifBlank { null }, scheduledDate = scheduledDate.trim(), customerMapUrl = customerMapUrl))
            .mapOk { onDone() }
    }

    fun dispatch(id: String, onDone: () -> Unit) = action { repository.dispatch(id).mapOk { onDone() } }

    /** 088: tandai sudah chat konsumen — refresh detail job (consumerChatAt terisi). */
    fun chatConsumer(id: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.chatConsumer(id)) {
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
        val loc = LocationProvider.current(appContext)
        repository.deliver(
            id,
            DeliverBody(
                photoUrl = photoUrl, lat = loc?.latitude, lng = loc?.longitude, reviewRating = rating,
                reviewComment = comment.trim().ifBlank { null },
                checklist = checklist.ifEmpty { null }, cashPhotoUrl = cashUrl
            )
        ).mapOk { onDone() }
    }

    fun cancel(id: String, reason: String, onDone: () -> Unit) = action {
        repository.cancel(id, reason.trim().ifBlank { "-" }).mapOk { onDone() }
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
