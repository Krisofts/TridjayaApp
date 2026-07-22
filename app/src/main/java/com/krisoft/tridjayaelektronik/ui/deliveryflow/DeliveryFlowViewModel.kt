package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.math.max

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
    val discounts: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList()
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

    fun loadQueue(status: String?, view: String? = null) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.list(status = status, view = view)) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, items = res.data, error = null) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadDetail(id: String) {
        _state.update { it.copy(loading = true, error = null, actionDone = false, actionError = null) }
        deliverPhotoBytes = null
        pdiPhotoBytes = null
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
            }
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_SCHEDULING -> viewModelScope.launch {
                (repository.drivers() as? AuthResult.Success)?.let { r -> _state.update { it.copy(drivers = r.data) } }
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

    // ── Foto (PDI ready / serah terima) ──────────────────────────────────────
    fun onPdiPhotoCaptured(file: File) = viewModelScope.launch {
        pdiPhotoBytes = withContext(Dispatchers.Default) { compress(file) }
        _state.update { it.copy() } // trigger recomposition of "photo taken" flag if diperlukan
    }

    fun hasPdiPhoto(): Boolean = pdiPhotoBytes != null

    fun onDeliverPhotoCaptured(file: File) = viewModelScope.launch {
        deliverPhotoBytes = withContext(Dispatchers.Default) { compress(file) }
        _state.update { it.copy() }
    }

    fun hasDeliverPhoto(): Boolean = deliverPhotoBytes != null

    // ── Aksi tahap ───────────────────────────────────────────────────────────

    fun createSpk(
        customerName: String, customerPhone: String, address: String, mapUrl: String, item: CreateDeliveryItemBody,
        keterangan: String, onDone: () -> Unit
    ) = action {
        repository.create(
            CreateDeliveryBody(
                customerName = customerName.trim(),
                customerPhone = customerPhone.trim(),
                customerAddress = address.trim().ifBlank { null },
                customerMapUrl = mapUrl.trim().ifBlank { null },
                keterangan = keterangan.trim().ifBlank { null },
                items = listOf(item)
            )
        ).mapOk { onDone() }
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

    fun confirmSpk(id: String, onDone: () -> Unit) = action { repository.confirmSpk(id).mapOk { onDone() } }

    fun issueDeliveryNote(id: String, sourceBranch: String, onDone: () -> Unit) = action {
        repository.issueDeliveryNote(id, DeliveryNoteBody(sourceBranch = sourceBranch.trim())).mapOk { onDone() }
    }

    fun assign(id: String, driverId: String, driverName: String, scheduledDate: String, customerMapUrl: String?, onDone: () -> Unit) = action {
        repository.assign(id, AssignBody(driverId = driverId.trim(), driverName = driverName.trim().ifBlank { null }, scheduledDate = scheduledDate.trim(), customerMapUrl = customerMapUrl))
            .mapOk { onDone() }
    }

    fun dispatch(id: String, onDone: () -> Unit) = action { repository.dispatch(id).mapOk { onDone() } }

    fun deliver(id: String, rating: Int, comment: String, onDone: () -> Unit) = action {
        val bytes = deliverPhotoBytes ?: return@action AuthResult.Failure("validation", "Foto serah terima wajib diambil")
        val photoUrl = when (val up = repository.uploadPhoto(bytes, "deliver_${System.currentTimeMillis()}.jpg")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> return@action up
        }
        repository.deliver(id, DeliverBody(photoUrl = photoUrl, reviewRating = rating, reviewComment = comment.trim().ifBlank { null }))
            .mapOk { onDone() }
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

    /** Downscale ≤1600px + JPEG ≤2MB (tanpa watermark). */
    private fun compress(file: File): ByteArray? {
        val raw = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (raw.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val maxSide = max(bmp.width, bmp.height)
        if (maxSide > 1600) {
            val scale = 1600f / maxSide
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        }
        var q = 85
        var out = ByteArrayOutputStream().apply { bmp.compress(Bitmap.CompressFormat.JPEG, q, this) }.toByteArray()
        while (out.size > 2 * 1024 * 1024 && q > 40) {
            q -= 15
            out = ByteArrayOutputStream().apply { bmp.compress(Bitmap.CompressFormat.JPEG, q, this) }.toByteArray()
        }
        return out
    }
}
