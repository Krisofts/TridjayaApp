package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Deadstock cabang (`GET /inventory/deadstock`) — inventory-service
 * (`deadstock/mod.rs::DeadstockItem`, camelCase). Role cabang
 * (karyawan/kepala-cabang/admin-stok) dipaksa dealer sendiri di backend
 * (anti-IDOR) — mobile tidak perlu & tidak bisa kirim `cabang` lain.
 * `dariSnapshot`/audit* HANYA relevan mode manager (union lintas cabang,
 * di luar scope mobile) — selalu `false`/`null` di list role cabang.
 */
@Serializable
data class DeadstockItemDto(
    val kodeBarang: String = "",
    val kodeDealer: String = "",
    val cabang: String = "",
    val namaBarang: String = "",
    val kategori: String = "",
    val hargaJual: Long = 0,
    val stok: Int = 0,
    val umurHari: Int = 0,
    val dariSnapshot: Boolean = false,
    val indikasiTerjual: Boolean = false,
    val brosurUrl: String? = null,
    val brosurUploadedBy: String? = null,
    val brosurUploadedAt: String? = null,
    val auditStatus: String? = null,
    val auditBy: String? = null,
    val auditAt: String? = null,
    val auditNote: String? = null
)

@Serializable
data class DeadstockListDto(
    val count: Int = 0,
    val cabang: String = "",
    val items: List<DeadstockItemDto> = emptyList(),
    val failedDealers: List<String> = emptyList()
)
