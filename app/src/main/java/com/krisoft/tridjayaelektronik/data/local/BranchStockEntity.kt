package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Entity
import androidx.room.Index

/** One (product, branch) stock row, mirroring `GET /api/inventory/stok-cabang`. */
@Entity(
    tableName = "branch_stock",
    primaryKeys = ["kode", "kodeDealer"],
    indices = [
        // Speeds up the inventory list's region/category/brand filter chips and the
        // kode+kodeCabang lookups used for product detail / branch breakdown / grouping.
        Index(value = ["kodeCabang"]),
        Index(value = ["kategori"]),
        Index(value = ["merk"]),
        Index(value = ["kode", "kodeCabang"]),
        Index(value = ["nama"])
    ]
)
data class BranchStockEntity(
    val kode: String,
    val kodeDealer: String,
    val nama: String,
    val kategori: String,
    val merk: String,
    val harga: Double,
    val stok: Double,
    val kodeCabang: String
)
