package com.krisoft.tridjayaelektronik.ui.delivery

import androidx.lifecycle.ViewModel
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.model.DeliveryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DeliveryUiState(
    val items: List<DeliveryDto> = emptyList(),
    val search: String = "",
    /** Filter status aktif (key), null = semua. */
    val statusFilter: String? = null
) {
    /** Daftar setelah filter status + pencarian nama/barang/cabang. */
    val visible: List<DeliveryDto>
        get() {
            val term = search.trim().lowercase()
            return items.filter { d ->
                (statusFilter == null || d.status.equals(statusFilter, ignoreCase = true)) &&
                    (term.isEmpty() ||
                        d.customerName.lowercase().contains(term) ||
                        d.itemName.lowercase().contains(term) ||
                        d.senderBranch.lowercase().contains(term))
            }
        }
}

/**
 * Delivery workflow — sepenuhnya berbasis data dummy in-memory ([DeliveryDummyData]) untuk
 * mendemokan alur di HP sebelum API `/api/sales/delivery-schedules` di-wire. Perubahan status
 * (Lanjutkan / Tandai Gagal / Jadwalkan Ulang) hanya menulis ke state — hilang saat proses mati.
 */
@HiltViewModel
class DeliveryViewModel @Inject constructor(
    authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeliveryUiState(items = DeliveryDummyData.all()))
    val uiState: StateFlow<DeliveryUiState> = _uiState.asStateFlow()

    /** Nama sales default untuk SPK baru = user login. */
    val currentSalesName: String = authRepository.currentUserName?.trim().orEmpty().ifBlank { "Sales" }

    /** Cabang pengirim default = cabang user login. */
    val currentBranch: String = authRepository.currentCabangName?.trim().orEmpty()

    fun byId(id: String): DeliveryDto? = _uiState.value.items.firstOrNull { it.id == id }

    fun onSearchChange(value: String) = _uiState.update { it.copy(search = value) }

    fun setStatusFilter(status: String?) = _uiState.update {
        it.copy(statusFilter = if (it.statusFilter == status) null else status)
    }

    /**
     * Input SPK oleh sales — membuat order pengiriman baru di awal alur (status SPK). Dummy: id &
     * nomor SPK di-generate lokal, ditaruh paling atas daftar. Mengembalikan id agar layar bisa
     * langsung membuka detailnya.
     */
    fun createSpk(
        customerName: String,
        customerPhone: String,
        itemName: String,
        quantity: Int,
        paymentStatus: String,
        address: String,
        senderBranch: String,
        salesName: String,
        estimatedValue: Double,
        note: String
    ): String {
        val now = System.currentTimeMillis()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        val stamp = java.text.SimpleDateFormat("yyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date(now))
        val seq = _uiState.value.items.size + 1
        val record = DeliveryDto(
            id = "DLV-$stamp",
            spkNumber = "SPK/${today.replace("-", "")}/${seq.toString().padStart(3, '0')}",
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim().ifBlank { null },
            itemName = itemName.trim(),
            quantity = quantity.coerceAtLeast(1),
            paymentStatus = paymentStatus,
            address = address.trim(),
            senderBranch = senderBranch.trim(),
            salesName = salesName.trim().ifBlank { "Sales" },
            estimatedValue = estimatedValue,
            note = note.trim().ifBlank { null },
            scheduledDate = today,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(now)),
            status = DeliveryStatus.SPK.key
        )
        _uiState.update { it.copy(items = listOf(record) + it.items) }
        return record.id
    }

    /** PDI lengkap bila semua poin [PDI_ITEMS] tercentang — syarat lanjut ke Dikirim. */
    fun isPdiComplete(delivery: DeliveryDto): Boolean =
        PDI_ITEMS.all { it.key in delivery.pdiChecked }

    /** Toggle satu poin checklist PDI. */
    fun togglePdiCheck(id: String, itemKey: String) = updateItem(id) { current ->
        val checked = current.pdiChecked
        current.copy(pdiChecked = if (itemKey in checked) checked - itemKey else checked + itemKey)
    }

    /**
     * Majukan satu tahap (SPK → Disiapkan → PDI → Dikirim → Terkirim). Dari PDI hanya boleh maju
     * bila checklist lengkap — kalau belum, diabaikan (UI menonaktifkan tombolnya).
     */
    fun advanceStatus(id: String) = updateItem(id) { current ->
        val status = DeliveryStatus.from(current.status)
        if (status == DeliveryStatus.PDI && !isPdiComplete(current)) return@updateItem current
        val next = status.next() ?: return@updateItem current
        current.copy(status = next.key)
    }

    /** Tandai gagal dengan alasan opsional. */
    fun markFailed(id: String, reason: String) = updateItem(id) {
        it.copy(status = DeliveryStatus.GAGAL.key, note = reason.ifBlank { it.note })
    }

    /** Jadwalkan ulang pengiriman yang gagal — kembali ke tahap awal (SPK). */
    fun reschedule(id: String) = updateItem(id) {
        it.copy(status = DeliveryStatus.SPK.key)
    }

    private inline fun updateItem(id: String, transform: (DeliveryDto) -> DeliveryDto) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) transform(it) else it })
        }
    }
}
