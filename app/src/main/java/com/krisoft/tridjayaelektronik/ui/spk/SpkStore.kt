package com.krisoft.tridjayaelektronik.ui.spk

import com.krisoft.tridjayaelektronik.data.model.DeliveryDto
import com.krisoft.tridjayaelektronik.ui.delivery.DeliveryStatus
import com.krisoft.tridjayaelektronik.ui.delivery.DeliveryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Penyimpan state alur SPK **in-memory, dibagikan** ke semua layar tahap (hub, approval diskon,
 * antri kasir, antri PDI, kontrol pengiriman, driver) sebagai [Singleton] Hilt — agar aksi di satu
 * layar (mis. approve diskon) langsung terlihat di antrian berikutnya. Murni dummy: tak ada
 * jaringan/persistensi, seluruh isi kembali ke [SpkDummyData] saat proses aplikasi dimatikan.
 */
@Singleton
class SpkStore @Inject constructor(
    private val deliveryStore: DeliveryStore
) {

    private val _orders = MutableStateFlow(SpkDummyData.seed())
    val orders: StateFlow<List<SpkOrder>> = _orders.asStateFlow()

    fun byId(id: String): SpkOrder? = _orders.value.firstOrNull { it.id == id }

    fun byStage(stage: SpkStage): List<SpkOrder> = _orders.value.filter { it.stage == stage }

    fun countByStage(stage: SpkStage): Int = _orders.value.count { it.stage == stage }

    /** Buat SPK baru → masuk tahap Approval Diskon bila ada diskon, kalau tidak langsung Antri Kasir. */
    fun createSpk(
        pelanggan: String,
        telepon: String,
        sales: String,
        cabang: String,
        unit: String,
        qty: Int,
        otr: Double,
        diskon: Double,
        paymentStatus: String,
        alamat: String,
        catatan: String
    ): String {
        val list = _orders.value
        val seq = list.size + 1
        val id = "SPK-${seq.toString().padStart(3, '0')}"
        val nomor = "SPK/20260721/${seq.toString().padStart(3, '0')}"
        val order = SpkOrder(
            id = id,
            nomor = nomor,
            pelanggan = pelanggan.trim(),
            telepon = telepon.trim().ifBlank { null },
            sales = sales.trim().ifBlank { "Sales" },
            cabang = cabang.trim().ifBlank { "Cabang" },
            unit = unit.trim(),
            qty = qty.coerceAtLeast(1),
            otr = otr,
            diskon = diskon.coerceAtLeast(0.0),
            paymentStatus = paymentStatus.ifBlank { "cash" },
            stage = if (diskon > 0.0) SpkStage.MENUNGGU_DISKON else SpkStage.ANTRI_KASIR,
            alamat = alamat.trim(),
            catatan = catatan.trim().ifBlank { null },
            dibuat = "2026-07-21 09:00"
        )
        _orders.update { listOf(order) + it }
        return id
    }

    /** Komite: setujui diskon → lanjut ke Antri Kasir. */
    fun approveDiskon(id: String) = update(id) { it.copy(stage = SpkStage.ANTRI_KASIR) }

    /** Komite: tolak diskon → tahap Ditolak (terminal). */
    fun rejectDiskon(id: String, alasan: String) = update(id) {
        it.copy(stage = SpkStage.DITOLAK, catatan = alasan.ifBlank { it.catatan })
    }

    /** Kasir: SPK sudah diinput/diproses → lanjut ke Antri PDI. */
    fun kasirProses(id: String) = update(id) { it.copy(stage = SpkStage.ANTRI_PDI) }

    fun togglePdi(id: String, itemKey: String) = update(id) {
        val checked = it.pdiChecked
        it.copy(pdiChecked = if (itemKey in checked) checked - itemKey else checked + itemKey)
    }

    fun isPdiComplete(order: SpkOrder): Boolean = PDI_ITEMS.all { it.key in order.pdiChecked }

    /** PDI selesai (semua tercentang) → lanjut ke Kontrol Pengiriman. */
    fun selesaikanPdi(id: String) = update(id) {
        if (isPdiComplete(it)) it.copy(stage = SpkStage.KONTROL_KIRIM) else it
    }

    /**
     * Kontrol pengiriman (delivery controller): tugaskan driver + jadwal → **handoff** ke pipeline
     * pengiriman. Membuat [DeliveryDto] baru di [DeliveryStore] (status Dikirim, siap diantar driver)
     * dan menandai order SPK sebagai Diserahkan. Setelah ini, sisa alur (Dikirim → Terkirim)
     * ditangani layar "Kirim" (ui/delivery).
     */
    fun serahkanKePengiriman(id: String, driver: String, jadwal: String) {
        val order = byId(id) ?: return
        deliveryStore.addDelivery(
            DeliveryDto(
                id = "DLV-${order.id}",
                customerName = order.pelanggan,
                itemName = order.unit,
                quantity = order.qty,
                paymentStatus = order.paymentStatus,
                address = order.alamat,
                salesName = order.sales,
                senderBranch = order.cabang,
                status = DeliveryStatus.DIKIRIM.key,
                customerPhone = order.telepon,
                scheduledDate = jadwal.ifBlank { order.jadwalKirim },
                note = "Driver: $driver · dari ${order.nomor}",
                estimatedValue = order.totalOtr,
                spkNumber = order.nomor
            )
        )
        update(id) { it.copy(stage = SpkStage.DISERAHKAN, driver = driver, jadwalKirim = jadwal.ifBlank { it.jadwalKirim }) }
    }

    private inline fun update(id: String, transform: (SpkOrder) -> SpkOrder) {
        _orders.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }
}
