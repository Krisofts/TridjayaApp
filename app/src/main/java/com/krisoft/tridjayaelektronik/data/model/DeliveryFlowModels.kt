package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
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
    val customerPhone: String? = null,
    /** Link Google Maps konsumen (086) — prasyarat backend sebelum assign driver. */
    val customerMapUrl: String? = null,
    val customerNik: String? = null,
    /** Pre Order ID (2026-07-24, opsional, per-barang). */
    val preOrderId: String? = null,
    /** URL foto PO (2026-07-24, opsional, per-barang). */
    val poPhotoUrl: String? = null,
    /** Metode pengiriman (2026-07-24): null/'driver' (default) | 'self_pickup' | 'sales_delivery'. */
    val deliveryMethod: String? = null,
    /** PDI wajib/tidak (2026-07-24, per-barang, independen dari deliveryMethod).
     *  `null` = backend lama tanpa kolom ini (perlakukan sbg true). */
    val pdiRequired: Boolean? = null,
    val fincoy: String? = null,
    val paymentType: String? = null,
    val hargaOtr: Double? = null,
    val diskon: Double? = null,
    val hargaTotal: Double? = null,
    // Pembiayaan per-unit (068)
    val dpNet: Double? = null,
    val pembayaran1: Double? = null,
    val angsuran: Double? = null,
    val tenor: Int? = null,
    val biayaAdm: Double? = null,
    val angsuranPertama: Double? = null,
    // Komisi + sumber order (068/080)
    val komisiSales: Double? = null,
    val komisiKbk: Double? = null,
    val noHpKbk: String? = null,
    val orderSource: String? = null,
    val kbkBrokerKode: String? = null,
    val kbkBrokerNama: String? = null,
    val keterangan: String? = null,
    val salesName: String? = null,
    /** User id sales pembuat SPK (2026-07-24) — opsi "Sales antar sendiri" di assign driver. */
    val salesUserId: String? = null,
    // Sosmed konsumen (068, denormalisasi per baris)
    val sosmedTiktok: String? = null,
    val sosmedFacebook: String? = null,
    val sosmedInstagram: String? = null,
    val status: String = DeliveryStatusKey.PENDING_PDI,
    val inputChannel: String? = null,
    val serialNumber: String? = null,
    val engineNumber: String? = null,
    val pdiReadyPhotoUrl: String? = null,
    val pdiByName: String? = null,
    val pdiAt: String? = null,
    /** Klaim PDI (111, 2026-07-29) — petugas yang menekan "Ambil PDI"; server
     *  mengosongkannya lagi saat PDI selesai / TTL habis. `null` bisa berarti
     *  DUA hal: belum ada yang mengklaim, ATAU server lama yang belum kenal
     *  fitur ini. Pembedanya `DeliveryContextDto.pdiClaimTtlHours` — lihat
     *  `pdiClaimView` (DeliveryFlowScreens.kt). */
    val pdiClaimedBy: String? = null,
    val pdiClaimedByName: String? = null,
    val pdiClaimedAt: String? = null,
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
    val updatedAt: String? = null,
    // 088 — driver checklist/chat/terima uang. `driverTerimaUang != null` = penanda
    // backend 088 aktif (kolom NOT NULL → selalu terisi pasca-088).
    val consumerChatAt: String? = null,
    val driverTerimaUang: Boolean? = null,
    val driverTerimaNominal: Double? = null,
    val cashPhotoUrl: String? = null,
    /** COD Full Payment/DP (2026-07-25, migrasi 103) — "full" | "dp" | null. */
    val codPaymentMode: String? = null,
    /** Rencana DP sales saat SPK dibuat — beda dari `kasirDpDiterima` (aktual). */
    val codDpAmount: Double? = null,
    /** Cabang LOGIN sales pembuat SPK (2026-07-25, migrasi 104) — independen
     *  dari `kodeDealer` (cabang stok fisik unit). */
    val salesDealerCode: String? = null,
    val salesDealerName: String? = null,
    /** Nominal DP AKTUAL diterima kasir (2026-07-25, migrasi 105) — beda dari
     *  `codDpAmount` (rencana sales). */
    val kasirDpDiterima: Double? = null,
    /** Kasir sudah konfirmasi cek pembayaran (2026-07-25, migrasi 105). */
    val kasirKonfirmasiPembayaran: Boolean = false,
    // Setoran driver→kasir (2026-07-25, migrasi 105) — non-blocking, kasir
    // konfirmasi terima balik uang COD dari driver setelah delivered.
    val setoranKasirNominal: Double? = null,
    val setoranKasirPhotoUrl: String? = null,
    val setoranKasirByNama: String? = null,
    val setoranKasirAt: String? = null,
    /** Timeline siap-render dari SERVER (2026-07-27) — hanya diisi endpoint
     *  DETAIL, tidak di list. Satu sumber untuk app & web (termasuk approval
     *  diskon + form aki dari tabel samping); kosong = server lama, app menyusun
     *  timeline-nya sendiri. */
    val timeline: List<TimelineStepDto> = emptyList()
)

/** Satu tahap timeline SPK (`delivery/timeline.rs`). `tone`:
 *  done|active|pending|rejected|cancelled. */
@Serializable
data class TimelineStepDto(
    val key: String = "",
    val label: String = "",
    val timestamp: String? = null,
    val detail: String? = null,
    val tone: String = "pending"
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
    /** Sejajar `kodePengiriman` (2026-07-26) — dipakai auto-navigate PDI Mandiri,
     *  langsung tanpa reverse-lookup via search antrian (yang rapuh). */
    val ids: List<String> = emptyList(),
    val discountPending: Boolean = false
)

/**
 * Konteks cabang/dealer aktor untuk form input SPK (`GET /delivery/context`).
 * Backend (`delivery_context` di `delivery.rs`) balas key `kodeDealer`/`dealerName`/`cabangName`/`name`
 * — TIDAK ADA `kodeCabang` (nama field lama sebelumnya salah tebak, selalu null tak terpakai).
 */
@Serializable
data class DeliveryContextDto(
    val kodeDealer: String? = null,
    val dealerName: String? = null,
    val cabangName: String? = null,
    /** Kill-switch gate serah-terima driver 088 di server (H-1/checklist/foto uang).
     *  null = backend lama tanpa field → perlakukan seperti OFF (gate klien jadi
     *  warning saja): server yang gate-nya ON PASTI sudah membawa field ini. */
    val driverGateEnabled: Boolean? = null,
    /** Jeda minimum chat konsumen → serah terima (MENIT; 0 = chat wajib ditandai
     *  tapi tanpa tunggu). null = backend lama → fallback 60. */
    val chatMinMinutes: Int? = null,
    /** Umur maksimum klaim PDI (JAM, 111). Kehadiran field ini = satu-satunya
     *  penanda server sudah kenal klaim PDI; `null` (server lama ATAU konteks
     *  gagal dimuat) → app TIDAK menawarkan "Ambil PDI" sama sekali dan alur
     *  PDI persis seperti sebelumnya. */
    val pdiClaimTtlHours: Int? = null
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

/** Driver untuk dropdown assign (`GET /api/users?role=driver`). Field 1-kata → aman snake/camel;
 *  `cabang_name` snake_case (UserPublic auth-service TANPA rename_all camelCase). */
@Serializable
data class DriverDto(
    val id: String? = null,
    val userId: String? = null,
    val name: String = "",
    val nik: String? = null,
    val role: String? = null,
    /** Nama cabang penuh (mis. "Tridjaya Elektronik Manado Bahu") — dipakai filter
     *  driver se-region di AssignAction (paritas web `driversForRegion` 2026-07-21). */
    @SerialName("cabang_name") val cabangName: String = ""
) {
    val effectiveId: String get() = (id ?: userId).orEmpty()
}

@Serializable
data class UsersListData(val items: List<DriverDto> = emptyList())

/**
 * Baris stok GS (`GET /inventory/stok-cabang`) — dipakai autocomplete barang Input SPK.
 * JSON key PascalCase (asal GS), BEDA dari konvensi camelCase DTO lain di file ini —
 * kotlinx-serialization TIDAK case-insensitive, `@SerialName` eksplisit wajib per field.
 */
@Serializable
data class StokCabangRow(
    @SerialName("Kode") val kode: String = "",
    @SerialName("Nama") val nama: String = "",
    @SerialName("Kategori") val kategori: String = "",
    @SerialName("Merk") val merk: String = "",
    @SerialName("Tipe") val tipe: String = "",
    @SerialName("Harga") val harga: Double? = null,
    @SerialName("Stok") val stok: Int? = null
)

/** Response `GET /api/inventory/stok-cabang` (di dalam `data`). */
@Serializable
data class StokCabangData(val items: List<StokCabangRow> = emptyList())

/** Broker KBK (`GET /inventory/delivery/brokers?q=`). camelCase 1:1. */
@Serializable
data class BrokerOption(val kode: String = "", val nama: String = "")

@Serializable
data class BrokerListData(val items: List<BrokerOption> = emptyList())

/** Baris registry serial (`GET /inventory/serial-numbers`). Hanya serialNumber dipakai. */
@Serializable
data class SerialRegistryRow(val serialNumber: String = "")

@Serializable
data class SerialListData(val items: List<SerialRegistryRow> = emptyList())

/**
 * Konteks mutasi (`GET /inventory/mutasi/context`) — dipakai layar Input Serial Number
 * admin-stok utk resolve dealer sendiri sebelum POST manual. Respons penuh juga bawa
 * `canRequest`/`isManager`/`dealers` (form mutasi create/receive) — diabaikan di sini,
 * hanya field dealer sendiri yang relevan utk layar SN.
 */
@Serializable
data class MutasiContextDto(
    val sourceDealerCode: String? = null,
    val sourceDealerName: String? = null
)

/**
 * Body `POST /inventory/serial-numbers/requests` — cabang MENGUSULKAN SN, tidak
 * mendaftarkannya. Registry tetap ditulis admin-stok saat menyetujui; kalau app
 * menulis registry langsung, temuan `tidak_terdaftar` pada opname berikutnya
 * hilang dan sinyalnya ikut hilang.
 */
@Serializable
data class CreateSerialRequestBody(
    val kodeDealer: String,
    val kodeBarang: String,
    val namaBarang: String? = null,
    val serialNumber: String,
    val fotoSnUrl: String,
    val fotoBarangUrl: String,
    val opnameSessionId: String? = null,
    val catatan: String? = null
)

/** Satu usulan pendaftaran SN. Field keputusan (`decided*`) kosong selama `pending`. */
@Serializable
data class SerialRequestDto(
    val id: String = "",
    val kodeDealer: String = "",
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val serialNumber: String = "",
    val status: String = "pending",
    val catatan: String? = null,
    val alasanTolak: String? = null,
    val requestedByName: String? = null,
    val requestedAt: String? = null,
    val decidedByName: String? = null,
    val decidedAt: String? = null
)

/** Body `POST /inventory/serial-numbers` — input manual admin-stok (dipaksa dealer sendiri di backend). */
@Serializable
data class CreateSerialNumbersBody(
    val kodeDealer: String,
    val kodeBarang: String,
    val namaBarang: String? = null,
    val serialNumbers: List<String>
)

/**
 * Satu baris arsip mutasi (`GET /inventory/mutasi-histori`) — inventory-service
 * (`repository.rs::get_mutasi_histori`, MSSQL raw `tHeaderMutasiPart{IN,OUT}` digabung,
 * map generik bukan struct tetap — field di bawah adalah kolom yang benar-benar
 * di-`SELECT`/di-`insert` server, lihat source). Endpoint HISTORI-ONLY (arsip baca-saja,
 * bukan alur create/receive yang masih di balik flag `HISTORI_ONLY` di web) — tanpa gate
 * role server-side, RBAC halaman direplikasi di client (lihat `canAccessMutasiHistori`).
 */
@Serializable
data class MutasiHistoriRowDto(
    /** "IN" (barang masuk) | "OUT" (barang keluar). */
    val arah: String = "",
    val noTransaksi: String = "",
    /** Format ERP mentah `"YYYY-MM-DD HH:MM:SS"` — BUKAN ISO dgn `T`, parse manual. */
    val tanggal: String = "",
    val cabang: String = "",
    val cabangNama: String = "",
    val lawan: String = "",
    val lawanNama: String = "",
    val usernya: String = "",
    val totalQty: Int? = null,
    val jumlahItem: Int? = null
)

@Serializable
data class MutasiHistoriListDto(
    val count: Int = 0,
    val items: List<MutasiHistoriRowDto> = emptyList()
)

/** Satu baris detail barang 1 transaksi mutasi (`GET /inventory/mutasi-histori/detail`). */
@Serializable
data class MutasiHistoriDetailRowDto(
    val kodeBarang: String = "",
    val nama: String = "",
    val jumlah: Int? = null,
    /** Serial number — bisa string kosong (tak semua barang mutasi ber-SN tercatat ERP). */
    val sn: String = ""
)

@Serializable
data class MutasiHistoriDetailListDto(
    val count: Int = 0,
    val items: List<MutasiHistoriDetailRowDto> = emptyList()
)

@Serializable
data class SkippedSerialDto(val serialNumber: String = "", val reason: String = "")

@Serializable
data class SerialCreateResultDto(val inserted: Int = 0, val skipped: List<SkippedSerialDto> = emptyList())

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

@OptIn(ExperimentalSerializationApi::class)
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
    /** Pre Order ID (2026-07-24, per-barang — melekat ke produk). */
    val preOrderId: String? = null,
    /** URL foto PO hasil upload (per-barang). */
    val poPhotoUrl: String? = null,
    /** PDI wajib/tidak (2026-07-24, per-barang, independen dari deliveryMethod
     *  SPK). `null`/absen = default true. `false` = skip PDI beneran, sales
     *  bertanggung jawab cek barang sendiri (biasanya barang kecil). */
    val pdiRequired: Boolean? = null,
    // ponytail: paksa selalu ter-serialize — Retrofit Json (encodeDefaults=false) buang field
    // yang = default, tapi backend butuh paymentType eksplisit walau nilainya "cash".
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val paymentType: String = "cash",
    val fincoy: String? = null,
    val hargaOtr: Double,
    val diskon: Double? = null,
    val alasanDiskon: String? = null,
    val dpNet: Double? = null,
    val pembayaran1: Double? = null,
    val angsuran: Double? = null,
    val tenor: Int? = null,
    val komisiSales: Double? = null,
    val komisiKbk: Double? = null,
    val noHpKbk: String? = null,
    val orderSource: String? = null,
    val kbkBrokerKode: String? = null,
    val kbkBrokerNama: String? = null,
    /** 088: driver terima uang dari konsumen (gate foto uang saat deliver). */
    val driverTerimaUang: Boolean? = null,
    /** 2026-07-25: driverTerimaNominal DIHITUNG backend dari codPaymentMode/
     *  codDpAmount + hargaOtr, bukan lagi input manual — field ini tak dikirim lagi. */
    val codPaymentMode: String? = null,
    val codDpAmount: Double? = null,
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
    /** Metode pengiriman (2026-07-24, opsional): kosong = 'driver' (default) |
     *  'self_pickup' | 'sales_delivery'. Body-level, denormalisasi backend ke semua barang. */
    val deliveryMethod: String? = null,
    val sosmedTiktok: String? = null,
    val sosmedFacebook: String? = null,
    val sosmedInstagram: String? = null,
    val keterangan: String? = null,
    val tanggalJual: String? = null,
    val items: List<CreateDeliveryItemBody>
)

/** Body `POST /delivery/{id}/self-pickup-complete` (2026-07-24) — konsumen ambil unit
 *  sendiri di cabang, DC/admin tandai selesai. Foto+rating wajib, sama standar [DeliverBody]. */
@Serializable
data class SelfPickupCompleteBody(
    val photoUrl: String,
    val reviewRating: Int,
    val reviewComment: String? = null
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

/** Body wajib `POST .../spk` sejak 2026-07-25 (migrasi 105) — sebelumnya
 *  endpoint ini tanpa body sama sekali, sekarang backend WAJIB `Content-Type:
 *  application/json` + `noTransaksi` non-kosong (axum `Json` extractor 415
 *  kalau content-type absen/salah — root cause "gagal konfirmasi SPK 415"). */
@Serializable
data class ConfirmSpkBody(
    val noTransaksi: String,
    val kasirDpDiterima: Double? = null,
    val kasirKonfirmasiPembayaran: Boolean? = null,
)

/**
 * Kasir konfirmasi pembayaran unit yang sudah sampai konsumen. Berlaku untuk
 * SEMUA jenis pembayaran (bukan cuma COD) sejak 2026-07-28 — nominal + foto
 * bukti sama-sama wajib, yang beda cuma sumber uangnya.
 */
@Serializable
data class SetoranKasirBody(
    val nominalDiterima: Double,
    val photoUrl: String
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
    val reviewComment: String? = null,
    /** 088: checklist serah-terima driver (kategori ber-item stage=driver). */
    val checklist: List<PdiChecklistItemBody>? = null,
    /** 088: foto bukti terima uang (wajib bila job.driverTerimaUang). */
    val cashPhotoUrl: String? = null
)

/** Body reorder muatan driver (`POST /delivery/driver/reorder`) — array posisi = urutan muat. */
@Serializable
data class ReorderBody(val orderedIds: List<String>)

@Serializable
data class ReorderResult(val count: Int = 0)

// ── Form pengambilan aki (PDI gate, migrasi 082) ─────────────────────────────

/** Kategori PDI (`GET /delivery/config/categories`) — `requiresAkiForm` = gate hard-block submit PDI. */
@Serializable
data class DeliveryCategoryDto(
    val id: String = "",
    val kategori: String = "",
    val requiresAkiForm: Boolean = false,
    val aktif: Boolean = true
)

@Serializable
data class DeliveryCategoriesData(val items: List<DeliveryCategoryDto> = emptyList())

/** Form pengambilan aki (`aki.rs` — subset field yang dipakai app). */
@Serializable
data class AkiFormDto(
    val id: String = "",
    val deliveryJobId: String = "",
    val tanggal: String = "",
    val pengambilNama: String = "",
    val tujuan: String = "",
    val tujuanLainnya: String? = null,
    val merkTipe: String = "",
    val jumlahPcs: Int = 0,
    /** Foto bukti aki (2026-07-24) — wajib diisi PDI saat submit. `null` = form lama sebelum fitur ini. */
    val photoUrl: String? = null,
    val akiBekasStatus: String = "belum",
    /** `rejected` bila ditolak, `approved` bila approver PUSAT (`aki_approver`)
     *  sudah menyetujui (redesain 2026-07-24, dulu 3 slot kepala-cabang+admin-
     *  penjualan+kasir/aki-approver); selain itu `pending`. PDI di-gate backend
     *  sampai `approved`. */
    val approvalStatus: String = "pending",
    val rejectedByNama: String? = null,
    val rejectedReason: String? = null,
    val rejectedAt: String? = null,
    /** Satu-satunya slot yang masih ditulis backend (approver pusat). */
    val akiApproverApprovedNama: String? = null,
    val akiApproverApprovedAt: String? = null,
    /** Dipakai timeline detail SPK (2026-07-27): kapan & siapa yang mengisi form,
     *  plus penentu form MANA yang berlaku kalau ada pengajuan ulang. */
    val createdAt: String = "",
    val createdByNama: String = ""
)

@Serializable
data class AkiFormsData(val items: List<AkiFormDto> = emptyList())

/** Wrapper create (`POST /delivery/{id}/aki-form` → `data.form`, BUKAN objek langsung). */
@Serializable
data class AkiFormCreateData(val form: AkiFormDto = AkiFormDto())

/** Body create (`aki.rs:107-133`, camelCase; tujuan+merkTipe+jumlahPcs wajib; pengambil = actor). */
@Serializable
data class CreateAkiFormBody(
    val tujuan: String,
    val merkTipe: String,
    val jumlahPcs: Int,
    val tujuanLainnya: String? = null,
    val kapasitas: String? = null,
    val jumlahKeterangan: String? = null,
    val keterangan: String? = null,
    val ambilCharger: Boolean = false,
    val ambilKacaSpion: Boolean = false,
    /** Wajib (2026-07-24) — URL hasil upload lewat endpoint upload-photo generic. */
    val photoUrl: String
)

/** Body tandai aki bekas dikembalikan (`POST /aki-forms/{id}/return`); kosong = default backend. */
@Serializable
data class ReturnAkiBody(val jumlah: Int? = null, val keterangan: String? = null)

/** Body tolak form aki (`POST /aki-forms/{id}/reject`). `reason` wajib (backend 400 kalau kosong). */
@Serializable
data class RejectAkiBody(val reason: String)

/** Preferensi WA alur SPK (`GET/PUT /inventory/discount-requests/wa-pref`). `spkWaOptout=true`
 *  → user matikan WA (dapat notif app push saja, anti-double). Default false = WA tetap ON. */
@Serializable
data class WaPrefDto(val spkWaOptout: Boolean = false)

// ---------------------------------------------------------------------------
// Direktori petugas + panduan alur (`GET /inventory/delivery/petugas`, WP7).
// Muatan server SENGAJA minimal (nama + WA saja, tanpa NIK/email/jabatan) —
// jangan menambah field di sini tanpa backend yang benar-benar mengirimnya.
// ---------------------------------------------------------------------------

/** Satu orang di direktori. `whatsapp` null = tetap ditampilkan, tapi tak bisa dihubungi. */
@Serializable
data class PetugasDto(val nama: String = "", val whatsapp: String? = null)

/**
 * Satu kelompok tugas. Server SELALU mengirim enam kelompok berurutan
 * (sales, pdi, kasir, delivery-control, driver, kepala-cabang) termasuk yang
 * kosong — klien tak perlu bercabang untuk kelompok yang tak ada.
 *
 * [lintasCabang] true = orangnya melayani semua cabang (saat ini
 * delivery-control). Tampilkan keterangannya dari flag ini, JANGAN hardcode
 * [kunci] — kelompok lintas-cabang bisa bertambah di server.
 */
@Serializable
data class PetugasGroupDto(
    val kunci: String = "",
    val label: String = "",
    val lintasCabang: Boolean = false,
    val petugas: List<PetugasDto> = emptyList(),
)

/** Satu tahap alur. Kalimatnya datang dari server supaya koreksi teks tak menuntut rilis APK. */
@Serializable
data class TahapAlurDto(
    val status: String = "",
    val aktor: String = "",
    val keterangan: String = "",
)

@Serializable
data class PetugasDirektoriDto(
    val kodeDealer: String? = null,
    val dealerName: String? = null,
    val divisi: List<PetugasGroupDto> = emptyList(),
    val tahapan: List<TahapAlurDto> = emptyList(),
)
