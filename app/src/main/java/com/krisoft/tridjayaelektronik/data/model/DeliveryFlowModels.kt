package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Alur pengiriman NYATA (SPK → antar) — wiring ke backend `inventory-service` modul delivery,
 * gateway `/api/inventory/delivery/...`. Envelope `{message,data}` (pakai [ApiResponse]). Semua DTO
 * camelCase 1:1 dengan serde backend. Pipeline status:
 * `pending_discount?` → `pending_pdi` → `pending_spk` → `pending_delivery_note` →
 * `pending_scheduling` → `assigned` → `in_transit` → `delivered` (+ `cancelled`).
 */
object DeliveryStatusKey {
    const val PENDING_DISCOUNT = "pending_discount"
    const val PENDING_PDI = "pending_pdi"
    const val PENDING_SPK = "pending_spk"
    const val PENDING_DELIVERY_NOTE = "pending_delivery_note"
    const val PENDING_SCHEDULING = "pending_scheduling"
    const val ASSIGNED = "assigned"
    const val IN_TRANSIT = "in_transit"
    const val DELIVERED = "delivered"
    const val CANCELLED = "cancelled"
}

/** Satu job pengiriman (1 unit fisik). Subset field yang dipakai app; semua opsional agar tahan null. */
@Serializable
data class DeliveryJobDto(
    val id: String = "",
    val kodePengiriman: String = "",
    val noTransaksi: String? = null,
    val baris: Int? = null,
    val unitSeq: Int? = null,
    val kodeDealer: String? = null,
    val dealerName: String? = null,
    val kodeCabang: String? = null,
    val tanggalJual: String? = null,
    val kodeBarang: String? = null,
    val namaBarang: String? = null,
    val kategori: String? = null,
    val merk: String? = null,
    val tipe: String? = null,
    val warna: String? = null,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val customerMapUrl: String? = null,
    val customerPhone: String? = null,
    val customerNik: String? = null,
    val fincoy: String? = null,
    val paymentType: String? = null,
    val hargaOtr: Double? = null,
    val diskon: Double? = null,
    val hargaTotal: Double? = null,
    val keterangan: String? = null,
    val salesName: String? = null,
    val status: String = DeliveryStatusKey.PENDING_PDI,
    val inputChannel: String? = null,
    val serialNumber: String? = null,
    val engineNumber: String? = null,
    val pdiReadyPhotoUrl: String? = null,
    val pdiByName: String? = null,
    val pdiAt: String? = null,
    val spkConfirmedBy: String? = null,
    val spkConfirmedAt: String? = null,
    val sourceBranch: String? = null,
    val deliveryNoteNo: String? = null,
    val deliveryNoteBy: String? = null,
    val deliveryNoteAt: String? = null,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val scheduledDate: String? = null,
    val assignedByName: String? = null,
    val assignedAt: String? = null,
    val dispatchedAt: String? = null,
    val deliveryPhotoUrl: String? = null,
    val deliveredAt: String? = null,
    val deliveryLat: Double? = null,
    val deliveryLng: Double? = null,
    val deliveredBy: String? = null,
    val reviewRating: Int? = null,
    val reviewComment: String? = null,
    val reviewAt: String? = null,
    val cancelReason: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** Response `GET /api/inventory/delivery` (di dalam `data`). */
@Serializable
data class DeliveryListData(
    val items: List<DeliveryJobDto> = emptyList(),
    val page: Int? = null,
    val limit: Int? = null
)

/** Response `POST /api/inventory/delivery` (di dalam `data`). */
@Serializable
data class DeliveryCreateResult(
    val created: Int = 0,
    val kodePengiriman: List<String> = emptyList(),
    val discountPending: Boolean = false
)

/** Konteks cabang/dealer aktor untuk form input SPK (`GET /delivery/context`). */
@Serializable
data class DeliveryContextDto(
    val kodeDealer: String? = null,
    val dealerName: String? = null,
    val kodeCabang: String? = null
)

/** Response upload foto (`POST /delivery/upload-photo`). */
@Serializable
data class DeliveryUploadResponse(val url: String = "")

/** Item checklist PDI per-kategori (`GET /delivery/config/checklist?kategori=`). */
@Serializable
data class ChecklistItemDto(
    val id: String = "",
    val kategori: String = "",
    val itemLabel: String = "",
    val urutan: Int = 0,
    val wajib: Boolean = false,
    val aktif: Boolean = true
)

@Serializable
data class ChecklistConfigData(val items: List<ChecklistItemDto> = emptyList())

/** Driver untuk dropdown assign (`GET /api/users?role=driver`). Field 1-kata → aman snake/camel. */
@Serializable
data class DriverDto(
    val id: String? = null,
    val userId: String? = null,
    val name: String = "",
    val nik: String? = null,
    val role: String? = null
) {
    val effectiveId: String get() = (id ?: userId).orEmpty()
}

@Serializable
data class UsersListData(val items: List<DriverDto> = emptyList())

// ── Approval diskon per-baris (SPK) ──────────────────────────────────────────

@Serializable
data class DiscountJobSummaryDto(
    val kodeBarang: String? = null,
    val namaBarang: String? = null,
    val kategori: String? = null,
    val merk: String? = null,
    val tipe: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null
)

/** Pengajuan diskon (`GET /api/inventory/discount-requests`). camelCase 1:1. */
@Serializable
data class DiscountRequestDto(
    val id: String = "",
    val context: String = "",
    val spkBatchKode: String = "",
    val baris: Int? = null,
    val deliveryJobIds: List<String> = emptyList(),
    val jobSummary: DiscountJobSummaryDto? = null,
    val discountType: String = "",
    val value: Double = 0.0,
    val reason: String = "",
    val hargaSebelum: Double? = null,
    val hargaSesudah: Double? = null,
    val status: String = "pending",
    val requestedById: String = "",
    val requestedByName: String? = null,
    val decidedById: String? = null,
    val decidedByName: String? = null,
    val decidedAt: String? = null,
    val decisionNote: String? = null,
    val createdAt: String = ""
)

@Serializable
data class DiscountListData(
    val items: List<DiscountRequestDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20
)

/** Body approve/reject diskon. */
@Serializable
data class DecisionBody(val decisionNote: String? = null)

// ── Request bodies ───────────────────────────────────────────────────────────

@Serializable
data class CreateDeliveryItemBody(
    val kodeBarang: String,
    val namaBarang: String,
    val kategori: String,
    val merk: String,
    val tipe: String,
    val qty: Int = 1,
    val warna: String? = null,
    val serialNumber: String? = null,
    val paymentType: String = "cash",
    val fincoy: String? = null,
    val hargaOtr: Double,
    val diskon: Double? = null,
    val alasanDiskon: String? = null,
    val dpNet: Double? = null,
    val kodeDealer: String? = null,
    val kodeCabang: String? = null
)

@Serializable
data class CreateDeliveryBody(
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String? = null,
    val customerMapUrl: String? = null,
    val customerNik: String? = null,
    val salesNik: String? = null,
    val keterangan: String? = null,
    val tanggalJual: String? = null,
    val items: List<CreateDeliveryItemBody>
)

@Serializable
data class PdiChecklistItemBody(
    val item: String,
    val hasil: String,          // "ok" | "tidak" | "na"
    val catatan: String? = null
)

@Serializable
data class PdiBody(
    val serialNumber: String,
    val engineNumber: String? = null,
    val readyPhotoUrl: String? = null,
    val checklist: List<PdiChecklistItemBody> = emptyList()
)

@Serializable
data class DeliveryNoteBody(
    val sourceBranch: String,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val customerPhone: String? = null,
    val deliveryNoteNo: String? = null
)

@Serializable
data class AssignBody(
    val driverId: String,
    val driverName: String? = null,
    val scheduledDate: String,
    val customerMapUrl: String? = null
)

@Serializable
data class DeliverBody(
    val photoUrl: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val reviewRating: Int,
    val reviewComment: String? = null
)
