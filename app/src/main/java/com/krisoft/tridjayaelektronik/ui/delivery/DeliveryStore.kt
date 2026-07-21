package com.krisoft.tridjayaelektronik.ui.delivery

import com.krisoft.tridjayaelektronik.data.model.DeliveryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State pipeline pengiriman **in-memory, dibagikan** ([Singleton] Hilt) — dipakai oleh
 * [DeliveryViewModel] (layar "Kirim") sekaligus tujuan handoff dari alur SPK
 * ([com.krisoft.tridjayaelektronik.ui.spk.SpkStore.serahkanKePengiriman]): begitu delivery
 * controller menugaskan pengiriman, order SPK ditambahkan ke sini via [addDelivery] dan tampil di
 * pipeline. Murni dummy — kembali ke [DeliveryDummyData] saat proses mati.
 */
@Singleton
class DeliveryStore @Inject constructor() {

    private val _items = MutableStateFlow(DeliveryDummyData.all())
    val items: StateFlow<List<DeliveryDto>> = _items.asStateFlow()

    fun byId(id: String): DeliveryDto? = _items.value.firstOrNull { it.id == id }

    /** Tambah delivery baru di paling atas (mis. hasil handoff dari alur SPK). */
    fun addDelivery(dto: DeliveryDto) = _items.update { listOf(dto) + it }

    /** Input SPK oleh sales — membuat order pengiriman baru di awal alur (status SPK). */
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
        val seq = _items.value.size + 1
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
        _items.update { listOf(record) + it }
        return record.id
    }

    fun isPdiComplete(delivery: DeliveryDto): Boolean = PDI_ITEMS.all { it.key in delivery.pdiChecked }

    fun togglePdiCheck(id: String, itemKey: String) = updateItem(id) { current ->
        val checked = current.pdiChecked
        current.copy(pdiChecked = if (itemKey in checked) checked - itemKey else checked + itemKey)
    }

    /** Majukan satu tahap (SPK → Disiapkan → PDI → Dikirim → Terkirim). Dari PDI harus lengkap dulu. */
    fun advanceStatus(id: String) = updateItem(id) { current ->
        val status = DeliveryStatus.from(current.status)
        if (status == DeliveryStatus.PDI && !isPdiComplete(current)) return@updateItem current
        val next = status.next() ?: return@updateItem current
        current.copy(status = next.key)
    }

    fun markFailed(id: String, reason: String) = updateItem(id) {
        it.copy(status = DeliveryStatus.GAGAL.key, note = reason.ifBlank { it.note })
    }

    fun reschedule(id: String) = updateItem(id) { it.copy(status = DeliveryStatus.SPK.key) }

    private inline fun updateItem(id: String, transform: (DeliveryDto) -> DeliveryDto) {
        _items.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }
}
