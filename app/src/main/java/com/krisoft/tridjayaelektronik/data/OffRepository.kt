package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.CreateOffRequest
import com.krisoft.tridjayaelektronik.data.model.OffRequestDto
import com.krisoft.tridjayaelektronik.data.remote.OffApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pengajuan izin/OFF — langsung ke backend `kinerja-service` via [OffApi]. Staff hanya
 * melihat pengajuannya sendiri (scope ditegakkan server). Tanpa cache lokal.
 */
@Singleton
class OffRepository @Inject constructor(
    private val api: OffApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Daftar pengajuan milik user login (server otomatis scope ke diri sendiri). */
    suspend fun mine(): AuthResult<List<OffRequestDto>> = try {
        val response = api.list(status = "all", limit = 50)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat pengajuan izin")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Ajukan izin baru. `tanggal` = "yyyy-MM-dd" (null = hari ini di server). */
    suspend fun create(tanggal: String?, alasan: String): AuthResult<OffRequestDto> = try {
        val response = api.create(CreateOffRequest(tanggal = tanggal, alasan = alasan))
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal mengajukan izin")
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
