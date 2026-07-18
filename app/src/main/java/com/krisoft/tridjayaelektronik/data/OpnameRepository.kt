package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.OpnameCountDao
import com.krisoft.tridjayaelektronik.data.local.OpnameCountEntity
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.BatchOpnameItemsRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameSessionDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.data.model.UpsertOpnameItemRequest
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock opname (hitung fisik) client. Counting is LOCAL-FIRST: every input accumulates into
 * Room ([OpnameCountDao]) so the counter works fast/offline mid-session; the buffer is pushed
 * to inventory-service in one batch only when the session is completed (then cleared).
 */
@Singleton
class OpnameRepository @Inject constructor(
    private val api: InventoryApi,
    private val countDao: OpnameCountDao
) {

    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun context(): AuthResult<OpnameContextDto> =
        call("Gagal memuat konteks opname") { api.opnameContext() }

    suspend fun list(status: String? = null): AuthResult<List<OpnameSessionDto>> =
        when (val result = call("Gagal memuat daftar opname") { api.listOpname(status) }) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            is AuthResult.Failure -> AuthResult.Failure(result.code, result.message)
        }

    suspend fun create(request: CreateOpnameRequest): AuthResult<OpnameDetailDto> =
        call("Gagal membuat sesi opname") { api.createOpname(request) }

    suspend fun detail(id: String): AuthResult<OpnameDetailDto> =
        call("Gagal memuat detail opname") { api.opnameDetail(id) }

    suspend fun stockList(id: String): AuthResult<List<OpnameStockItemDto>> =
        when (val result = call("Gagal memuat daftar barang opname") { api.opnameStock(id) }) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            is AuthResult.Failure -> AuthResult.Failure(result.code, result.message)
        }

    // ---- Local counting buffer ----

    fun observeCounts(sessionId: String): Flow<List<OpnameCountEntity>> = countDao.observe(sessionId)

    /** Re-inputs ACCUMULATE (counting the same SKU from another rack adds up, not overwrites). */
    suspend fun addCount(
        sessionId: String,
        stockItem: OpnameStockItemDto,
        layak: Long,
        tidakLayak: Long,
        keterangan: String?
    ) {
        val existing = countDao.get(sessionId, stockItem.kodeBarang)
        countDao.upsert(
            OpnameCountEntity(
                sessionId = sessionId,
                kodeBarang = existing?.kodeBarang ?: stockItem.kodeBarang,
                namaBarang = stockItem.namaBarang ?: existing?.namaBarang,
                merk = stockItem.merk ?: existing?.merk,
                stokFisikLayak = (existing?.stokFisikLayak ?: 0) + layak,
                stokFisikTidakLayak = (existing?.stokFisikTidakLayak ?: 0) + tidakLayak,
                keterangan = keterangan?.takeIf { it.isNotBlank() } ?: existing?.keterangan,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    /**
     * Finish flow: push the whole local buffer in one batch, then complete the session
     * server-side, then drop the buffer. The buffer survives a failed push untouched so the
     * user can just retry.
     */
    suspend fun finalize(sessionId: String): AuthResult<OpnameDetailDto> {
        val counts = countDao.list(sessionId)
        if (counts.isEmpty()) {
            return AuthResult.Failure("empty", "Belum ada barang yang dihitung")
        }
        val batch = BatchOpnameItemsRequest(
            items = counts.map {
                UpsertOpnameItemRequest(
                    kodeBarang = it.kodeBarang,
                    stokFisikLayak = it.stokFisikLayak,
                    stokFisikTidakLayak = it.stokFisikTidakLayak,
                    keterangan = it.keterangan
                )
            }
        )
        val pushed = call("Gagal mengunggah hitungan") { api.batchUpsertOpnameItems(sessionId, batch) }
        if (pushed is AuthResult.Failure) return pushed
        val completed = call<OpnameDetailDto>("Gagal menyelesaikan sesi") { api.completeOpname(sessionId) }
        if (completed is AuthResult.Success) {
            countDao.clearSession(sessionId)
        }
        return completed
    }

    suspend fun cancel(id: String): AuthResult<OpnameDetailDto> {
        val result = call<OpnameDetailDto>("Gagal membatalkan sesi") { api.cancelOpname(id) }
        if (result is AuthResult.Success) {
            countDao.clearSession(id)
        }
        return result
    }

    private suspend fun <T> call(
        fallback: String,
        block: suspend () -> Response<ApiResponse<T>>
    ): AuthResult<T> {
        return try {
            val response = block()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, fallback)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Same shape as InventoryRepository.parseError: validation detail in errors[0] wins. */
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
}
