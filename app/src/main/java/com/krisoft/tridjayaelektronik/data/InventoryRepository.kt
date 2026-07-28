package com.krisoft.tridjayaelektronik.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.krisoft.tridjayaelektronik.data.local.BranchStockDao
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaEntity
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import com.krisoft.tridjayaelektronik.data.model.UpdateIndentRequest
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_PAGE_LIMIT = 1000
private val SYNC_INTERVAL_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(5)

/**
 * Batas waras sinkronisasi stok: 20 halaman × [SYNC_PAGE_LIMIT] = 20.000 baris.
 *
 * Dengan `inStock=true` katalog nyata ±3.6k baris (4 halaman), jadi batas ini murni sabuk
 * pengaman: 28 Jul 2026 SP GS `GetStokCabang` mulai mengembalikan katalog penuh per dealer
 * (66.482 baris = 67 halaman) dan sinkronisasi HP lapangan tak pernah selesai — 834 percobaan
 * berhenti di halaman 4-5, hanya 19 yang tuntas. Kalau server mengabaikan/rollback filter
 * `inStock`, lebih baik berhenti dengan 20.000 baris terpakai + catatan log daripada menarik
 * tanpa akhir.
 */
private const val SYNC_MAX_PAGES = 20

private const val SYNC_LOG_TAG = "InventorySync"

/** Keputusan setelah satu halaman stok berhasil ditarik & ditulis. */
internal enum class SyncStep {
    /** Masih ada halaman berikutnya dan batas belum tersentuh. */
    CONTINUE,

    /** Server bilang habis — data yang terkumpul = snapshot utuh, boleh menggantikan cache. */
    DONE,

    /** Berhenti karena batas waras / respons tak jelas — data parsial, JANGAN buang baris lama. */
    TRUNCATED
}

/**
 * Fungsi murni penentu langkah paging (diuji di `InventorySyncPagingTest`). Dipisah dari [sync]
 * supaya batas & syarat berhenti bisa diuji tanpa Retrofit/Room.
 */
internal fun nextSyncStep(page: Int, hasMore: Boolean, maxPages: Int = SYNC_MAX_PAGES): SyncStep =
    when {
        !hasMore -> SyncStep.DONE
        page >= maxPages -> SyncStep.TRUNCATED
        else -> SyncStep.CONTINUE
    }

/** Hasil [InventoryRepository.findInTransitHint] — barang ketemu di mutasi OUT belum ada padanan IN. */
data class InTransitHint(
    val namaBarang: String,
    val tujuanCabang: String,
    val tanggal: String
)

@Singleton
class InventoryRepository @Inject constructor(
    private val api: InventoryApi,
    private val branchStockDao: BranchStockDao,
    private val syncMetaDao: SyncMetaDao
) {

    fun pagedProducts(
        search: String,
        region: String,
        dealer: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int,
        deadstockOnly: Boolean
    ): Flow<PagingData<ProductAggregate>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                initialLoadSize = 30,
                prefetchDistance = 10
            )
        ) {
            branchStockDao.pagingSource(
                search.trim(), region, dealer, readyOnly, category, merk, sortOrder, deadstockOnly
            )
        }.flow
    }

    suspend fun exportProducts(
        search: String,
        region: String,
        dealer: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int,
        deadstockOnly: Boolean
    ): List<ProductAggregate> =
        branchStockDao.filteredProducts(
            search.trim(), region, dealer, readyOnly, category, merk, sortOrder, deadstockOnly
        )

    /** Simple product search for the global search screen — name/code match, default sort, no filters. */
    suspend fun searchProducts(query: String): List<ProductAggregate> =
        branchStockDao.filteredProducts(
            query.trim(),
            "",
            "",
            false,
            "",
            "",
            com.krisoft.tridjayaelektronik.data.local.ProductSortOrder.NAME_ASC,
            false
        )

    suspend fun categories(): List<String> = branchStockDao.distinctCategories()

    suspend fun merks(): List<String> = branchStockDao.distinctMerks()

    suspend fun branchBreakdown(kode: String, kodeCabang: String): List<BranchStockEntity> =
        branchStockDao.branchesForProduct(kode, kodeCabang)

    suspend fun productDetail(kode: String, kodeCabang: String): ProductAggregate? =
        branchStockDao.productAggregate(kode, kodeCabang)

    /**
     * Barang lagi mutasi antar cabang bikin stok 0 di dua sisi selama jeda GS OUT→IN
     * (lihat delivery-flow-audit.md item #5) — jadi gak nongol di search biasa. Cuma
     * dipanggil saat hasil search kosong (bukan tiap keystroke): cek [limit] transaksi
     * OUT terakhir dari cabang sendiri, buka detail satu-satu sampai ketemu barang yang
     * namanya/kodenya cocok [query]. Fail-soft — gagal jaringan/parse cukup return null,
     * ini cuma hint tambahan di empty-state, bukan jalur kritis.
     */
    suspend fun findInTransitHint(dealerCode: String, query: String, limit: Int = 15): InTransitHint? {
        val needle = query.trim()
        if (needle.isEmpty() || dealerCode.isEmpty()) return null
        return try {
            val fromDate = java.time.LocalDate.now().minusDays(30).toString()
            val listResponse = api.mutasiHistori(dealer = dealerCode, arah = "out", from = fromDate, limit = limit)
            val rows = listResponse.body()?.data?.items.orEmpty()
            for (row in rows) {
                val detailResponse = api.mutasiHistoriDetail(noTransaksi = row.noTransaksi, arah = "out")
                val items = detailResponse.body()?.data?.items.orEmpty()
                val match = items.firstOrNull {
                    it.nama.contains(needle, ignoreCase = true) || it.kodeBarang.equals(needle, ignoreCase = true)
                }
                if (match != null) {
                    return InTransitHint(
                        namaBarang = match.nama,
                        tujuanCabang = row.lawanNama.ifEmpty { row.lawan },
                        tanggal = row.tanggal
                    )
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Refreshes the local cache from the network only if the last sync is older than 6 hours. */
    suspend fun syncIfStale(): AuthResult<Unit> {
        val lastSync = syncMetaDao.get(SyncMetaEntity.KEY_BRANCH_STOCK)?.lastSyncMillis ?: 0L
        val isStale = System.currentTimeMillis() - lastSync >= SYNC_INTERVAL_MILLIS
        if (!isStale) return AuthResult.Success(Unit)
        return sync()
    }

    /**
     * Forces a network refresh regardless of the last sync time (pull-to-refresh).
     *
     * **Tulis per halaman, bukan semua-atau-tidak.** Tiap halaman langsung di-upsert ke Room, jadi
     * sinkronisasi yang putus di halaman ke-4 tetap meninggalkan data yang bisa dipakai (dulu:
     * satu `replaceAll` di akhir → putus di tengah = nol baris tersimpan, layar Inventory kosong
     * terus). Pembersihan baris yang hilang dari server (stok jadi 0 → tak dikirim lagi karena
     * `inStock=true`) baru dilakukan saat snapshot benar-benar utuh, lewat [BranchStockDao.replaceAll]
     * yang `@Transaction` — jadi TIDAK PERNAH ada jendela "DB kosong": pembaca melihat data lama
     * sampai transaksi commit, tak pernah tabel setengah terhapus.
     */
    suspend fun sync(): AuthResult<Unit> {
        return try {
            val rows = mutableListOf<BranchStockEntity>()
            var page = 1
            var step: SyncStep
            while (true) {
                val response = api.stokCabang(page = page, limit = SYNC_PAGE_LIMIT, inStock = true)
                if (!response.isSuccessful) {
                    // Halaman yang sudah masuk Room tetap tinggal; syncMeta sengaja TIDAK diperbarui
                    // supaya percobaan berikutnya menyegarkan lagi (upsert = idempoten).
                    return AuthResult.Failure(
                        "http_${response.code()}",
                        "Gagal mengambil data stok (${response.code()})"
                    )
                }
                val data = response.body()?.data
                if (data == null) {
                    // Page pertama sukses tapi body kosong/null → JANGAN timpa cache dengan list kosong
                    // (bisa mengosongkan inventori selama 5 jam). Page berikutnya null → data parsial.
                    if (page == 1) return AuthResult.Failure("empty_response", "Respons stok kosong dari server")
                    step = SyncStep.TRUNCATED
                    android.util.Log.w(SYNC_LOG_TAG, "Body kosong di halaman $page — snapshot parsial (${rows.size} baris)")
                    break
                }
                val pageRows = data.items.map {
                    BranchStockEntity(
                        kode = it.Kode,
                        kodeDealer = it.kodeDealer,
                        nama = it.Nama,
                        kategori = it.Kategori,
                        merk = it.Merk,
                        harga = it.Harga,
                        stok = it.Stok,
                        kodeCabang = it.kodeCabang,
                        gambar = it.Gambar?.trim()?.takeIf { url -> url.isNotEmpty() },
                        umurHari = it.umurHari,
                        kondisi = it.kondisi
                    )
                }
                branchStockDao.insertAll(pageRows)
                rows += pageRows
                step = nextSyncStep(page, data.hasMore)
                if (step != SyncStep.CONTINUE) break
                page += 1
            }
            if (step == SyncStep.DONE && rows.isNotEmpty()) {
                branchStockDao.replaceAll(rows)
            } else if (rows.isEmpty()) {
                // Snapshot utuh TAPI nol baris (mis. mirror stok server lagi kosong sesaat) —
                // dengan `inStock=true` ini mungkin terjadi tanpa error HTTP. Menukar cache
                // dengan list kosong = inventori HP kosong 5 jam; lebih baik pertahankan yang lama.
                android.util.Log.w(SYNC_LOG_TAG, "Server mengembalikan 0 baris stok — cache lama dipertahankan")
            } else {
                android.util.Log.w(
                    SYNC_LOG_TAG,
                    "Sinkronisasi stok DIPOTONG di halaman $page (batas $SYNC_MAX_PAGES halaman × $SYNC_PAGE_LIMIT baris): " +
                        "${rows.size} baris tersimpan, baris lama TIDAK dibersihkan karena snapshot tak utuh"
                )
            }
            syncMetaDao.upsert(SyncMetaEntity(SyncMetaEntity.KEY_BRANCH_STOCK, System.currentTimeMillis()))
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun listIndent(status: String? = null): AuthResult<IndentListData> {
        return try {
            val response = api.listIndent(status)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, "Gagal memuat daftar indent")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Putusan approver: setujui (`dipesan`) atau tolak (`batal` + alasan). */
    suspend fun updateIndentStatus(
        id: String,
        body: UpdateIndentRequest
    ): AuthResult<IndentDto> {
        return try {
            val response = api.updateIndentStatus(id, body)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, "Gagal memperbarui status indent")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun createIndent(request: CreateIndentRequest): AuthResult<IndentDto> {
        return try {
            val response = api.createIndent(request)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, "Gagal mengajukan indent")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun uploadIndentProof(bytes: ByteArray, filename: String, mimeType: String): AuthResult<String> {
        return try {
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("file", filename, body)
            val response = api.uploadIndentProof(part)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data.url)
            } else {
                parseError(response, "Gagal mengunggah bukti")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Surfaces the backend's own error text instead of a bare HTTP code — validation errors
     * carry the actionable detail in `errors[0]` ("Ukuran file maksimum 5 MB", etc.) with a
     * generic "Input tidak valid" message, so the detail wins when present.
     */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull() ?: parsed?.message
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: "$fallback (${response.code()})"
        )
    }

    private companion object {
        val errorJson = Json { ignoreUnknownKeys = true }
    }
}
