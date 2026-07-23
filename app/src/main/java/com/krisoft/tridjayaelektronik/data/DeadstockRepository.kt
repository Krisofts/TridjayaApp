package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.DeadstockListDto
import com.krisoft.tridjayaelektronik.data.remote.DeadstockApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Deadstock cabang — langsung ke `inventory-service` via [DeadstockApi]. Tanpa cache lokal. */
@Singleton
class DeadstockRepository @Inject constructor(
    private val api: DeadstockApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun list(): AuthResult<DeadstockListDto> = try {
        val response = api.list()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat deadstock cabang")
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
