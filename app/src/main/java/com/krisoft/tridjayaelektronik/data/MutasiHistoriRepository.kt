package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailListDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriListDto
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arsip mutasi (histori-only) — reuse [DeliveryFlowApi] (endpoint mutasi-histori sudah
 * didaftarkan di sana, sama pola [SerialInputRepository]) daripada bikin Retrofit interface baru.
 * `list()` mengambil SEMUA baris sekali (tanpa filter server) — arah/cabang difilter
 * client-side, sama pola layar Harga GS (dataset histori tak besar utk kebutuhan mobile).
 */
@Singleton
class MutasiHistoriRepository @Inject constructor(
    private val api: DeliveryFlowApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun list(): AuthResult<MutasiHistoriListDto> = try {
        val response = api.mutasiHistori()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat histori mutasi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun detail(noTransaksi: String, arah: String): AuthResult<MutasiHistoriDetailListDto> = try {
        val response = api.mutasiHistoriDetail(noTransaksi = noTransaksi, arah = arah)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat detail mutasi")
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
