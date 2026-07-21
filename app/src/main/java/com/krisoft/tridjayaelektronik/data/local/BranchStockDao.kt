package com.krisoft.tridjayaelektronik.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Sort options for [BranchStockDao.pagingSource]'s `sortOrder` parameter. */
object ProductSortOrder {
    const val NAME_ASC = 0
    const val PRICE_ASC = 1
    const val PRICE_DESC = 2
    const val STOCK_DESC = 3
    const val STOCK_ASC = 4
}

/** Ambang deadstock (hari) — produk dengan umur stok tertua >= ini dianggap deadstock. */
const val DEADSTOCK_MIN_DAYS = 180L

@Dao
interface BranchStockDao {

    @Query(
        """
        SELECT kode, kodeCabang, nama, kategori, merk, harga, SUM(stok) AS totalStok, MAX(gambar) AS gambar,
               MAX(umurHari) AS maxUmurHari
        FROM branch_stock
        WHERE (:search = '' OR nama LIKE '%' || :search || '%' OR kode LIKE '%' || :search || '%')
          AND (:region = '' OR kodeCabang = :region)
          AND (:dealer = '' OR kodeDealer = :dealer)
          AND (:category = '' OR kategori LIKE '%' || :category || '%')
          AND (:merk = '' OR merk LIKE '%' || :merk || '%')
        GROUP BY kode, kodeCabang
        HAVING (:readyOnly = 0 OR SUM(stok) > 0)
           AND (:deadstockOnly = 0 OR MAX(umurHari) >= :deadstockMinDays)
        ORDER BY
          CASE WHEN :sortOrder = 0 THEN nama END ASC,
          CASE WHEN :sortOrder = 1 THEN harga END ASC,
          CASE WHEN :sortOrder = 2 THEN harga END DESC,
          CASE WHEN :sortOrder = 3 THEN totalStok END DESC,
          CASE WHEN :sortOrder = 4 THEN totalStok END ASC
        """
    )
    fun pagingSource(
        search: String,
        region: String,
        dealer: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int,
        deadstockOnly: Boolean,
        deadstockMinDays: Long = DEADSTOCK_MIN_DAYS
    ): PagingSource<Int, ProductAggregate>

    @Query(
        """
        SELECT kode, kodeCabang, nama, kategori, merk, harga, SUM(stok) AS totalStok, MAX(gambar) AS gambar,
               MAX(umurHari) AS maxUmurHari
        FROM branch_stock
        WHERE (:search = '' OR nama LIKE '%' || :search || '%' OR kode LIKE '%' || :search || '%')
          AND (:region = '' OR kodeCabang = :region)
          AND (:dealer = '' OR kodeDealer = :dealer)
          AND (:category = '' OR kategori LIKE '%' || :category || '%')
          AND (:merk = '' OR merk LIKE '%' || :merk || '%')
        GROUP BY kode, kodeCabang
        HAVING (:readyOnly = 0 OR SUM(stok) > 0)
           AND (:deadstockOnly = 0 OR MAX(umurHari) >= :deadstockMinDays)
        ORDER BY
          CASE WHEN :sortOrder = 0 THEN nama END ASC,
          CASE WHEN :sortOrder = 1 THEN harga END ASC,
          CASE WHEN :sortOrder = 2 THEN harga END DESC,
          CASE WHEN :sortOrder = 3 THEN totalStok END DESC,
          CASE WHEN :sortOrder = 4 THEN totalStok END ASC
        """
    )
    suspend fun filteredProducts(
        search: String,
        region: String,
        dealer: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int,
        deadstockOnly: Boolean,
        deadstockMinDays: Long = DEADSTOCK_MIN_DAYS
    ): List<ProductAggregate>

    @Query("SELECT DISTINCT kategori FROM branch_stock WHERE kategori != '' ORDER BY kategori ASC")
    suspend fun distinctCategories(): List<String>

    @Query("SELECT DISTINCT merk FROM branch_stock WHERE merk != '' ORDER BY merk ASC")
    suspend fun distinctMerks(): List<String>

    @Query("SELECT * FROM branch_stock WHERE kode = :kode AND kodeCabang = :kodeCabang ORDER BY stok DESC")
    suspend fun branchesForProduct(kode: String, kodeCabang: String): List<BranchStockEntity>

    @Query(
        """
        SELECT kode, kodeCabang, nama, kategori, merk, harga, SUM(stok) AS totalStok, MAX(gambar) AS gambar,
               MAX(umurHari) AS maxUmurHari
        FROM branch_stock
        WHERE kode = :kode AND kodeCabang = :kodeCabang
        GROUP BY kode, kodeCabang
        """
    )
    suspend fun productAggregate(kode: String, kodeCabang: String): ProductAggregate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<BranchStockEntity>)

    @Query("DELETE FROM branch_stock")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(rows: List<BranchStockEntity>) {
        clearAll()
        insertAll(rows)
    }
}
