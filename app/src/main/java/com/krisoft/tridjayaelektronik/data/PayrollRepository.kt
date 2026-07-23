package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.PayslipDetailData
import com.krisoft.tridjayaelektronik.data.model.PayslipDto
import com.krisoft.tridjayaelektronik.data.remote.PayrollApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Slip gaji milik sendiri — langsung ke `kinerja-service` via [PayrollApi]. Tanpa cache lokal. */
@Singleton
class PayrollRepository @Inject constructor(
    private val api: PayrollApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun me(): AuthResult<List<PayslipDto>> = try {
        val response = api.me()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat daftar slip gaji")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun detail(id: Long): AuthResult<PayslipDetailData> = try {
        val response = api.detail(id)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat detail slip gaji")
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
