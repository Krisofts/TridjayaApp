package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Riwayat perubahan harga ERP GS (`GET /inventory/erp-price-changes`) — inventory-service
 * (`erp_price_changes.rs::ErpPriceChangeItem`, camelCase). Kontrak belum ditulis di
 * android-api.md (flagged di brief, lihat taskD1-report.md). `detectedAt`/`snapshotAt` ISO-8601
 * UTC ber-suffix `Z` — tampilkan via [relativeTimeId] (parse UTC, JANGAN ditafsirkan lokal;
 * insiden web: tampil mundur 7 jam sebelum suffix `Z` ditambahkan).
 */
@Serializable
data class ErpPriceChangeItemDto(
    val kodeBarang: String = "",
    val kodeCabang: String = "",
    /** Label region siap-tampil ("Jawa Barat" / "Manado"). */
    val cabang: String = "",
    /** Bisa kosong kalau barang sudah dihapus di ERP — tampilkan "(dihapus)". */
    val namaBarang: String = "",
    val kategori: String = "",
    val hargaLama: Long = 0,
    val hargaBaru: Long = 0,
    val selisih: Long = 0,
    val isNaik: Boolean = false,
    val detectedAt: String? = null
)

@Serializable
data class ErpPriceChangeResultDto(
    val count: Int = 0,
    val totalNaik: Int = 0,
    val totalTurun: Int = 0,
    val snapshotAt: String? = null,
    val lastDetectedAt: String? = null,
    val items: List<ErpPriceChangeItemDto> = emptyList()
)
