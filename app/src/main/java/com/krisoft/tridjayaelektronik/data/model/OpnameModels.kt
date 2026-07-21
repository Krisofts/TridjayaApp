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

@Serializable
data class UpsertOpnameItemRequest(
    val kodeBarang: String,
    val stokFisikLayak: Long,
    val stokFisikTidakLayak: Long,
    val keterangan: String? = null
)

/** Batch push of the locally-buffered counts — one transaction server-side. */
@Serializable
data class BatchOpnameItemsRequest(
    val items: List<UpsertOpnameItemRequest>
)
