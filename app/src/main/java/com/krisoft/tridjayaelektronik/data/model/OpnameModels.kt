package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Stock opname (hitung fisik stok) — mirrors inventory-service's opname module. The detail
 * endpoint serde-flattens the session into the same JSON object as `items`, so
 * [OpnameDetailDto] repeats the session fields instead of nesting an [OpnameSessionDto].
 */
@Serializable
data class OpnameSessionDto(
    val id: String = "",
    val kodeOpname: String = "",
    val dealerCode: String = "",
    val dealerName: String = "",
    val cabangId: String? = null,
    val cabangName: String? = null,
    val periodeDate: String = "",
    val jenis: String = "bulanan",
    val status: String = "draft",
    val createdByUserId: String = "",
    val createdByName: String? = null,
    val completedByUserId: String? = null,
    val completedByName: String? = null,
    val completedAt: String? = null,
    val catatan: String? = null,
    val totalItems: Long = 0,
    val totalSelisihItems: Long = 0,
    val totalStokFisik: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class OpnameItemDto(
    val id: String = "",
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val merk: String? = null,
    val kategori: String? = null,
    val stokSistem: Long = 0,
    val stokFisikLayak: Long = 0,
    val stokFisikTidakLayak: Long = 0,
    val selisih: Long = 0,
    val stokSistemAkhir: Long? = null,
    val terjual: Long? = null,
    val masuk: Long? = null,
    val harga: Double? = null,
    val keterangan: String? = null,
    val countedByUserId: String = "",
    val countedByName: String? = null,
    val countedAt: String = ""
)

@Serializable
data class OpnameDetailDto(
    val id: String = "",
    val kodeOpname: String = "",
    val dealerCode: String = "",
    val dealerName: String = "",
    val cabangId: String? = null,
    val cabangName: String? = null,
    val periodeDate: String = "",
    val jenis: String = "bulanan",
    val status: String = "draft",
    val createdByUserId: String = "",
    val createdByName: String? = null,
    val completedByUserId: String? = null,
    val completedByName: String? = null,
    val completedAt: String? = null,
    val catatan: String? = null,
    val totalItems: Long = 0,
    val totalSelisihItems: Long = 0,
    val totalStokFisik: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val items: List<OpnameItemDto> = emptyList()
)

@Serializable
data class OpnameListData(
    val items: List<OpnameSessionDto> = emptyList()
)

@Serializable
data class OpnameDealerDto(
    val code: String = "",
    val name: String = ""
)

/** `GET /api/inventory/opname/context` — actor capabilities + dealer dropdown options. */
@Serializable
data class OpnameContextDto(
    val canCreate: Boolean = false,
    val isManager: Boolean = false,
    val role: String = "",
    val dealers: List<OpnameDealerDto> = emptyList()
)

/** One row of the session's frozen coverage list (identity only — no stock values). */
@Serializable
data class OpnameStockItemDto(
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val merk: String? = null,
    val kategori: String? = null
)

@Serializable
data class OpnameStockData(
    val items: List<OpnameStockItemDto> = emptyList()
)

@Serializable
data class CreateOpnameRequest(
    val dealerCode: String,
    val periodeDate: String,
    val jenis: String? = null,
    val catatan: String? = null
)

/** Satu unit fisik terhitung — satu baris per serial number. */
@Serializable
data class OpnameUnitDto(
    val id: String = "",
    val kodeBarang: String = "",
    val serialNumber: String = "",
    val kondisi: String = "layak",
    /** Vonis admin-stok di registry untuk serial ini (migrasi 194). `null` =
     *  belum terdaftar ATAU terdaftar tapi kondisinya belum pernah ditetapkan —
     *  dua-duanya berarti "tak ada pembanding", bukan "layak". */
    val kondisiRegistry: String? = null,
    /** Dihitung SERVER: `true` hanya bila registry punya vonis DAN berbeda dari
     *  temuan lapangan. Jangan dihitung ulang di klien — perlakuan `null` yang
     *  berbeda membuat web dan app melaporkan angka berbeda untuk sesi sama. */
    val kondisiSelisih: Boolean = false,
    val temuan: String? = null,
    val keterangan: String? = null,
    /** `scan` | `manual` — ketik manual wajib 2 foto + validasi admin-stok. */
    val inputMethod: String = "scan",
    /** Hanya unit manual: `pending` | `approved` | `rejected`; scan = null. */
    val validationStatus: String? = null,
    val rejectReason: String? = null,
    val countedByUserId: String = "",
    val countedByName: String? = null,
    val countedAt: String = ""
)

@Serializable
data class OpnameUnitInput(
    val kodeBarang: String,
    val serialNumber: String,
    val kondisi: String = "layak",
    val keterangan: String? = null,
    /** Absen = server memakai default `scan` — kompatibel dgn backend lama. */
    val inputMethod: String? = null,
    val fotoSnUrl: String? = null,
    val fotoBarangUrl: String? = null
)

@Serializable
data class CreateOpnameUnitsRequest(
    val items: List<OpnameUnitInput>
)

@Serializable
data class OpnameUnitAccepted(
    val serialNumber: String = "",
    val temuan: String? = null,
    /** `pending` bila unitnya manual — bahan pesan "menunggu validasi". */
    val validationStatus: String? = null
)

@Serializable
data class OpnameUnitRejected(
    val serialNumber: String = "",
    val reason: String = ""
)

/**
 * Hasil per-baris: satu serial bermasalah tidak menggagalkan sisanya, jadi
 * petugas tak perlu mengulang seluruh batch scan di lapangan.
 */
@Serializable
data class CreateOpnameUnitsData(
    val accepted: List<OpnameUnitAccepted> = emptyList(),
    val rejected: List<OpnameUnitRejected> = emptyList(),
    val session: OpnameDetailDto = OpnameDetailDto()
)

@Serializable
data class OpnameUnitListData(
    val items: List<OpnameUnitDto> = emptyList()
)

/**
 * Satu baris antrian validasi admin-stok — unit ketik-manual PLUS konteks
 * sesinya (antriannya lintas sesi, jadi pemutus harus tahu cabang & kode
 * opname-nya). Backend serde-flatten `OpnameUnit` ke objek yang sama, makanya
 * field unit diulang di sini alih-alih dibungkus [OpnameUnitDto].
 *
 * `fotoSnUrl`/`fotoBarangUrl` nullable walau kontraknya menjanjikan terisi:
 * baris lama (sebelum foto diwajibkan) bisa kosong, dan "memang tak ada foto"
 * WAJIB terlihat beda dari "foto gagal dimuat".
 */
@Serializable
data class ManualUnitDto(
    val id: String = "",
    val kodeBarang: String = "",
    val serialNumber: String = "",
    val kondisi: String = "layak",
    val temuan: String? = null,
    val keterangan: String? = null,
    val inputMethod: String = "manual",
    val validationStatus: String? = null,
    val fotoSnUrl: String? = null,
    val fotoBarangUrl: String? = null,
    val validatedByName: String? = null,
    val rejectReason: String? = null,
    val countedByUserId: String = "",
    val countedByName: String? = null,
    val countedAt: String = "",
    val sessionId: String = "",
    val kodeOpname: String = "",
    val dealerCode: String = "",
    val dealerName: String = "",
    val sessionStatus: String = ""
)

/** Body tolak — server 400 kalau `alasan` kosong; klien menolak lebih dulu. */
@Serializable
data class RejectUnitBody(
    val alasan: String
)

@Serializable
data class ManualUnitListData(
    val count: Int = 0,
    val status: String = "",
    val items: List<ManualUnitDto> = emptyList()
)

/** `DELETE /api/inventory/opname/{id}` — sesi sudah lenyap, cuma id yg dikonfirmasi. */
@Serializable
data class OpnameDeleteData(
    val id: String = ""
)
