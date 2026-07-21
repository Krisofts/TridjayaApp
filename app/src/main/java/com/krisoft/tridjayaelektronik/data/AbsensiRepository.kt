package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.AbsensiPunchRequest
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiTodayDto
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.remote.AbsensiApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Absensi karyawan — check-in/out (selfie + GPS) + riwayat, langsung ke backend
 * `kinerja-service` via [AbsensiApi]. Tidak ada cache lokal (data absen harus real-time,
 * dan geofence/telat dihitung server) — layar menampilkan hasil terakhir dari network.
 */
@Singleton
class AbsensiRepository @Inject constructor(
    private val api: AbsensiApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun today(): AuthResult<AbsensiTodayDto> = try {
        val response = api.today()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat absensi hari ini")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun history(limit: Int = 60): AuthResult<List<AbsensiRecordDto>> = try {
        val response = api.list(page = 1, limit = limit)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat riwayat absensi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Upload selfie (JPEG) → mengembalikan URL relatif untuk dikirim saat check-in/out. */
    suspend fun uploadPhoto(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val body = bytes.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", filename, body)
        val response = api.uploadPhoto(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah foto selfie")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun checkIn(lat: Double, lng: Double, photoUrl: String): AuthResult<AbsensiRecordDto> =
        punch(photoUrl) { api.checkIn(AbsensiPunchRequest(lat, lng, photoUrl)) }

    suspend fun checkOut(lat: Double, lng: Double, photoUrl: String): AuthResult<AbsensiRecordDto> =
        punch(photoUrl) { api.checkOut(AbsensiPunchRequest(lat, lng, photoUrl)) }

    private suspend fun punch(
        photoUrl: String,
        call: suspend () -> Response<com.krisoft.tridjayaelektronik.data.model.ApiResponse<AbsensiRecordDto>>
    ): AuthResult<AbsensiRecordDto> = try {
        if (photoUrl.isBlank()) {
            AuthResult.Failure("validation", "Foto selfie wajib diambil dulu")
        } else {
            val response = call()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) AuthResult.Success(data)
            else parseError(response, "Absen gagal")
        }
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
