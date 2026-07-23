package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.content.Context
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
    /** Gate form aki (tahap pending_pdi, kategori ber-flag `requiresAkiForm`). */
    val requiresAki: Boolean = false,
    val akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
    /** Daftar riwayat (menu "Pengambilan Aki", beda dari [akiForms] yang di-scope satu job). */
    val akiList: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList()
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
        _state.update { it.copy(loading = true, error = null, actionDone = false, actionError = null, driverChecklist = emptyList()) }
        deliverPhotoBytes = null
        pdiPhotoBytes = null
        cashPhotoBytes = null
        viewModelScope.launch {
            when (val res = repository.detail(id)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(loading = false, detail = res.data) }
                    loadAuxFor(res.data)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
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
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.IN_TRANSIT -> {
                // 088 aktif? (driverTerimaUang selalu terisi pasca-088). Pre-088 JANGAN
                // fetch stage=driver — backend lama abaikan param & balik item PDI.
                val kategori = job.kategori?.trim().orEmpty()
                if (job.driverTerimaUang != null && kategori.isNotEmpty()) viewModelScope.launch {
                    (repository.checklist(kategori, stage = "driver") as? AuthResult.Success)?.let { r ->
                        _state.update { it.copy(driverChecklist = r.data) }
                    }
                }
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

    // ── Foto (PDI ready / serah terima / terima uang) — watermark geotag+jam sama pola absensi ──
    fun onPdiPhotoCaptured(file: File) = viewModelScope.launch {
        pdiPhotoBytes = watermarked(file, "TRIDJAYA · PDI")
        _state.update { it.copy() } // trigger recomposition of "photo taken" flag if diperlukan
    }

    fun hasPdiPhoto(): Boolean = pdiPhotoBytes != null

    fun onDeliverPhotoCaptured(file: File) = viewModelScope.launch {
        deliverPhotoBytes = watermarked(file, "TRIDJAYA · SERAH TERIMA")
        _state.update { it.copy() }
    }

    fun hasDeliverPhoto(): Boolean = deliverPhotoBytes != null

    fun onCashPhotoCaptured(file: File) = viewModelScope.launch {
        cashPhotoBytes = watermarked(file, "TRIDJAYA · TERIMA UANG")
        _state.update { it.copy() }
    }

    fun hasCashPhoto(): Boolean = cashPhotoBytes != null

    /**
     * GPS best-effort (pola sama [DeliveryFlowViewModel.deliver]): gagal/izin ditolak → watermark
     * timestamp saja, JANGAN blokir foto. Subtitle = nama · kode SPK job aktif (kalau sudah termuat).
     */
    private suspend fun watermarked(file: File, title: String): ByteArray? {
        val loc = runCatching { LocationProvider.current(appContext) }.getOrNull()
        val kode = _state.value.detail?.kodePengiriman.orEmpty()
        val subtitle = listOf(currentUserName, kode).filter { it.isNotBlank() }.joinToString(" · ")
        return withContext(Dispatchers.Default) {
            PhotoWatermark.prepareWatermarkedJpeg(file, loc?.latitude, loc?.longitude, title, subtitle)?.first
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
