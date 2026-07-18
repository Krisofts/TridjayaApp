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

    /** Refreshes the local cache from the network only if the last sync is older than 6 hours. */
    suspend fun syncIfStale(): AuthResult<Unit> {
        val lastSync = syncMetaDao.get(SyncMetaEntity.KEY_BRANCH_STOCK)?.lastSyncMillis ?: 0L
        val isStale = System.currentTimeMillis() - lastSync >= SYNC_INTERVAL_MILLIS
        if (!isStale) return AuthResult.Success(Unit)
        return sync()
    }

    /** Forces a network refresh regardless of the last sync time (pull-to-refresh). */
    suspend fun sync(): AuthResult<Unit> {
        return try {
            val rows = mutableListOf<BranchStockEntity>()
            var page = 1
            while (true) {
                val response = api.stokCabang(page = page, limit = SYNC_PAGE_LIMIT)
                if (!response.isSuccessful) {
                    return AuthResult.Failure("http_${response.code()}", "Gagal mengambil data stok (${response.code()})")
                }
                val data = response.body()?.data ?: break
                rows += data.items.map {
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
                if (!data.hasMore) break
                page += 1
            }
            branchStockDao.replaceAll(rows)
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
