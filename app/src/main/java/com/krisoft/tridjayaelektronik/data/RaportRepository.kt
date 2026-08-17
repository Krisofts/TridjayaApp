package com.krisoft.tridjayaelektronik.data

import android.content.ContentResolver
import android.net.Uri
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import com.krisoft.tridjayaelektronik.data.model.RaportListData
import com.krisoft.tridjayaelektronik.data.model.ReviewRaportBody
import com.krisoft.tridjayaelektronik.data.model.ReviewRaportResult
import com.krisoft.tridjayaelektronik.data.model.SubmitRaportBody
import com.krisoft.tridjayaelektronik.data.model.SubmitRaportItem
import com.krisoft.tridjayaelektronik.data.model.SubmitRaportResult
import com.krisoft.tridjayaelektronik.data.remote.RaportApi
import com.krisoft.tridjayaelektronik.data.remote.RaportUploadApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
    private val api: RaportApi,
    private val uploadApi: RaportUploadApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Master aktivitas semua posisi — pemilihan posisi milik user dilakukan UI. */
    suspend fun aktivitasPositions(): AuthResult<List<AktivitasPositionDto>> = try {
        val response = api.divisions()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.divisions)
        else parseError(response, "Gagal memuat master aktivitas")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Raport yang sudah terkirim pada [tanggal] (`YYYY-MM-DD`) milik
     * [karyawanId]. Selain dikirim sebagai filter server, hasilnya disaring
     * ULANG di sini: `list_raport` hanya memaksa scope diri sendiri saat role
     * PRIMARY user = `karyawan`, jadi user multi-role (mis. primary `sales` +
     * extra `karyawan`) bisa menerima baris karyawan lain — dan angka "sudah
     * berapa aktivitas hari ini" di layar Activity akan ikut salah.
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

    /**
     * Antrian PIC: SELURUH karyawan pada [tanggal], bukan cuma milik sendiri.
     *
     * Sengaja TIDAK memakai [raportOfDay]: fungsi itu menyaring ulang ke satu
     * `karyawanId`, yang untuk PIC berarti daftar kosong. Yang dikembalikan
     * seluruh `RaportListData` (bukan `items` saja) karena badge antrian harus
     * memakai `total` — `items` dipotong server ke `limit`.
     */
    suspend fun antrianReview(
        tanggal: String,
        status: String = "pending",
        cari: String? = null,
        limit: Int = 200,
    ): AuthResult<RaportListData> = try {
        val response = api.list(
            tanggal = tanggal,
            karyawanId = null,
            limit = limit,
            status = status,
            q = cari?.trim()?.takeIf { it.isNotBlank() },
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat antrian penilaian")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Putusan PIC atas satu baris. [skor] boleh `null` — server mengisi sendiri
     * (`rejected` → 0, selainnya 100). Tolak WAJIB ber-[komentar]: itu satu-
     * satunya cara karyawan tahu apa yang harus diperbaiki.
     */
    suspend fun review(
        id: String,
        status: String,
        skor: Int? = null,
        komentar: String? = null,
    ): AuthResult<ReviewRaportResult> = try {
        val response = api.review(
            id = id,
            body = ReviewRaportBody(
                status = status,
                score = skor,
                comment = komentar?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan penilaian")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Upload bukti GAMBAR → URL relatif untuk dikirim di [submitItem].
     *
     * Selalu `image/jpeg`: keluaran `PhotoWatermark.prepareWatermarkedJpeg`
     * memang selalu JPEG apa pun format sumbernya, termasuk PNG/WEBP yang
     * dipilih dari galeri. Server memvalidasi ekstensi × MIME × magic bytes
     * serentak, jadi [filename] wajib berakhiran `.jpg`.
     */
    suspend fun uploadEvidence(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData(
            "file", filename, bytes.toRequestBody("image/jpeg".toMediaType())
        )
        val response = uploadApi.uploadEvidence(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah bukti")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Upload bukti VIDEO → URL relatif.
     *
     * STREAMING dari [ContentResolver] lewat [UriRequestBody], tidak pernah
     * lewat `ByteArray`: batas server 30 MB sedangkan heap HP lapangan bisa
     * <128 MB, jadi `readBytes()` di sini = `OutOfMemoryError` di HP yang
     * justru paling sering dipakai.
     *
     * [mimeType] dan ekstensi pada [namaFile] WAJIB sepasang — server memeriksa
     * keduanya bersama magic bytes, dan pasangan yang meleset ditolak 400
     * SETELAH seluruh berkas terkirim. Pakai `ekstensiVideo`/`mimeVideo`
     * (`ui/raport/RaportBuktiPlan.kt`), jangan menebak sendiri.
     *
     * [ukuranBytes] hanya untuk header `Content-Length`; `0` = biarkan OkHttp
     * mengirim chunked (kolom `SIZE` tak selalu terbaca dari penyedia galeri).
     */
    suspend fun uploadEvidenceVideo(
        resolver: ContentResolver,
        uri: Uri,
        namaFile: String,
        mimeType: String,
        ukuranBytes: Long = 0L,
    ): AuthResult<String> = try {
        val body = UriRequestBody(resolver, uri, mimeType.toMediaTypeOrNull(), ukuranBytes)
        val part = MultipartBody.Part.createFormData("file", namaFile, body)
        val response = uploadApi.uploadEvidence(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah video bukti")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Kirim SATU aktivitas. Server-nya upsert per (karyawan, tanggal,
     * `jobdeskIndex`) — mengirim ulang aktivitas yang sama menimpa bukti lama
     * dan mengembalikan statusnya ke `pending` (persis perilaku web).
     *
     * Nama field kiriman tetap `jobdeskIndex`/`jobdeskText`: itu nama DI KABEL
     * (repo ini nol `@SerialName`), bukan istilah layar.
     */
    suspend fun submitItem(
        aktivitasIndex: Int,
        aktivitasText: String,
        mode: String,
        evidenceUrl: String? = null,
        employeeNote: String? = null,
    ): AuthResult<SubmitRaportResult> = try {
        val response = api.submit(
            SubmitRaportBody(
                items = listOf(
                    SubmitRaportItem(
                        jobdeskIndex = aktivitasIndex,
                        jobdeskText = aktivitasText,
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
     * valid" — detail sebenarnya ("Laporan aktivitas harian sedang tutup. Jam
     * bukanya 08:00 - 22:00 WIB (waktu Jakarta). …", "alasan wajib minimal 10
     * karakter", dst) hanya ada di `errors`, dan tanpa itu user tak tahu harus
     * berbuat apa.
     *
     * Kutipan di atas disegarkan 2026-08-15: kalimat servernya dulu menulis
     * "WITA" padahal gerbangnya dihitung WIB (`raport/service.rs`
     * `ensure_window_open` → `chrono::Local`, zona VPS `Asia/Jakarta`), jadi 12
     * karyawan cabang Manado membaca jam yang meleset 1 jam. Kalimat barunya
     * sampai ke HP TANPA rilis APK — persis karena `errors[0]` dipakai apa
     * adanya di sini.
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
