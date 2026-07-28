package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.JobdeskPositionDto
import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import com.krisoft.tridjayaelektronik.data.model.SubmitRaportBody
import com.krisoft.tridjayaelektronik.data.model.SubmitRaportItem
import com.krisoft.tridjayaelektronik.data.model.SubmitRaportResult
import com.krisoft.tridjayaelektronik.data.remote.RaportApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Laporan aktivitas harian (raport) — langsung ke kinerja-service, tanpa cache
 * lokal: jendela pelaporan & status review dihitung server, dan raport basi
 * lebih berbahaya daripada satu panggilan jaringan.
 */
@Singleton
class RaportRepository @Inject constructor(
    private val api: RaportApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Master jobdesk semua posisi — pemilihan posisi milik user dilakukan UI. */
    suspend fun jobdeskPositions(): AuthResult<List<JobdeskPositionDto>> = try {
        val response = api.divisions()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.divisions)
        else parseError(response, "Gagal memuat master jobdesk")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Raport yang sudah terkirim pada [tanggal] (`YYYY-MM-DD`) milik
     * [karyawanId]. Selain dikirim sebagai filter server, hasilnya disaring
     * ULANG di sini: `list_raport` hanya memaksa scope diri sendiri saat role
     * PRIMARY user = `karyawan`, jadi user multi-role (mis. primary `sales` +
     * extra `karyawan`) bisa menerima baris karyawan lain — dan angka "sudah
     * berapa jobdesk hari ini" di layar Activity akan ikut salah.
     */
    suspend fun raportOfDay(tanggal: String, karyawanId: String?): AuthResult<List<RaportItemDto>> = try {
        val response = api.list(tanggal = tanggal, karyawanId = karyawanId?.takeIf { it.isNotBlank() })
        val data = response.body()?.data
        if (response.isSuccessful && data != null) {
            val items = if (karyawanId.isNullOrBlank()) data.items
            else data.items.filter { it.employeeId.isBlank() || it.employeeId == karyawanId }
            AuthResult.Success(items)
        } else parseError(response, "Gagal memuat laporan hari ini")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Upload bukti (JPEG) → URL relatif untuk dikirim di [submitItem]. */
    suspend fun uploadEvidence(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData(
            "file", filename, bytes.toRequestBody("image/jpeg".toMediaType())
        )
        val response = api.uploadEvidence(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah bukti")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Kirim SATU jobdesk. Server-nya upsert per (karyawan, tanggal,
     * jobdeskIndex) — mengirim ulang jobdesk yang sama menimpa bukti lama dan
     * mengembalikan statusnya ke `pending` (persis perilaku web).
     */
    suspend fun submitItem(
        jobdeskIndex: Int,
        jobdeskText: String,
        mode: String,
        evidenceUrl: String? = null,
        employeeNote: String? = null,
    ): AuthResult<SubmitRaportResult> = try {
        val response = api.submit(
            SubmitRaportBody(
                items = listOf(
                    SubmitRaportItem(
                        jobdeskIndex = jobdeskIndex,
                        jobdeskText = jobdeskText,
                        mode = mode,
                        evidenceUrl = evidenceUrl,
                        employeeNote = employeeNote,
                    )
                )
            )
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan laporan")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Beda dari repository lain di app ini: pesan `errors[0]` dipakai LEBIH
     * DULU. `ApiError::Validation` selalu ber-`message` generik "Input tidak
     * valid" — detail sebenarnya ("Pelaporan jobdesk sedang ditutup. Jam
     * pelaporan aktif: 08:00 - 18:00 WITA", "alasan wajib minimal 10 karakter",
     * dst) hanya ada di `errors`, dan tanpa itu user tak tahu harus berbuat apa.
     */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull()?.takeIf { it.isNotBlank() }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}
