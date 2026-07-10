package com.krisoft.tridjayaelektronik.data.local

/**
 * One product row aggregated across every branch within the same region
 * (grouped by `kode` + `kodeCabang` — the same `kode` can be a different
 * physical product in a different region, so region must be part of identity).
 */
data class ProductAggregate(
    val kode: String,
    val kodeCabang: String,
    val nama: String,
    val kategori: String,
    val merk: String,
    val harga: Double,
    val totalStok: Double
)
