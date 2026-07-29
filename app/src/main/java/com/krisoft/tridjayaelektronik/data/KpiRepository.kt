package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.KpiDetailData
import com.krisoft.tridjayaelektronik.data.model.KpiKaryawanRowDto
import com.krisoft.tridjayaelektronik.data.remote.KpiApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** KPI — langsung ke `kinerja-service` via [KpiApi]. Tanpa cache lokal (angka
 *  ini bergerak tiap hari; menampilkan salinan basi lebih menyesatkan daripada
 *  layar gagal yang bisa di-retry). */
@Singleton
class KpiRepository @Inject constructor(
    private val api: KpiApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun me(periode: String?): AuthResult<KpiDetailData> = try {
        val response = api.me(periode)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat KPI")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun karyawan(periode: String?): AuthResult<List<KpiKaryawanRowDto>> = try {
        val response = api.karyawan(periode)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat daftar KPI karyawan")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun karyawanDetail(id: String, periode: String?): AuthResult<KpiDetailData> = try {
        val response = api.karyawanDetail(id, periode)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat KPI karyawan")
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
