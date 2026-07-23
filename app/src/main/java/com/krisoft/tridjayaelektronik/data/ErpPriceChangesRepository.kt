package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.ErpPriceChangeResultDto
import com.krisoft.tridjayaelektronik.data.remote.ErpPriceChangesApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Perubahan harga GS — langsung ke `inventory-service` via [ErpPriceChangesApi]. Tanpa cache lokal. */
@Singleton
class ErpPriceChangesRepository @Inject constructor(
    private val api: ErpPriceChangesApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun list(): AuthResult<ErpPriceChangeResultDto> = try {
        val response = api.list()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat perubahan harga")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}
