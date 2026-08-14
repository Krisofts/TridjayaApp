package com.krisoft.tridjayaelektronik.data

import android.content.ContentResolver
import android.net.Uri
import com.krisoft.tridjayaelektronik.data.model.AktivitasChatTodayDto
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.BuktiChatDto
import com.krisoft.tridjayaelektronik.data.model.BuktiListDto
import com.krisoft.tridjayaelektronik.data.model.KirimBuktiRequest
import com.krisoft.tridjayaelektronik.data.model.ReviewBuktiRequest
import com.krisoft.tridjayaelektronik.data.model.UploadVideoDto
import com.krisoft.tridjayaelektronik.data.remote.AktivitasChatApi
import com.krisoft.tridjayaelektronik.data.remote.AktivitasChatUploadApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bukti aktivitas chat harian (jumlah chat + video layar) — syarat absen pulang.
 * Tanpa cache lokal: status bukti ditentukan server (wajib/tidak, sudah direview atau belum),
 * layar selalu menampilkan hasil terakhir dari jaringan. Pola sama [AbsensiRepository].
 */
@Singleton
class AktivitasChatRepository @Inject constructor(
    private val api: AktivitasChatApi,
    private val uploadApi: AktivitasChatUploadApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun today(): AuthResult<AktivitasChatTodayDto> = try {
        val response = api.today()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat status aktivitas chat hari ini")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Unggah video bukti → mengembalikan URL relatif untuk dipakai [kirim].
     *
     * [ukuranBytes] hanya untuk header `Content-Length` (ambil dari pemeriksaan ukuran yang
     * sudah dilakukan layar sebelum memanggil ini); 0 = biarkan OkHttp mengirim chunked.
     */
    suspend fun uploadVideo(
        resolver: ContentResolver,
        uri: Uri,
        namaFile: String,
        mimeType: String = "video/mp4",
        ukuranBytes: Long = 0,
    ): AuthResult<UploadVideoDto> = try {
        val body = UriRequestBody(resolver, uri, mimeType.toMediaTypeOrNull(), ukuranBytes)
        // Nama part WAJIB "file" — itu yang dibaca server.
        val part = MultipartBody.Part.createFormData("file", namaFile, body)
        val response = uploadApi.uploadVideo(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data)
        else parseError(response, "Gagal mengunggah video bukti")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun kirim(jumlahChat: Int, videoUrl: String): AuthResult<BuktiChatDto> = try {
        if (videoUrl.isBlank()) {
            AuthResult.Failure("validation", "Video bukti wajib diunggah dulu")
        } else {
            val response = api.kirim(KirimBuktiRequest(jumlahChat, videoUrl))
            val data = response.body()?.data
            if (response.isSuccessful && data != null) AuthResult.Success(data)
            else parseError(response, "Gagal mengirim bukti aktivitas chat")
        }
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun list(
        tanggal: String? = null,
        status: String? = null,
        page: Int = 1,
        limit: Int = 50,
    ): AuthResult<BuktiListDto> = try {
        val response = api.list(tanggal, status, page, limit)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat daftar bukti aktivitas chat")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Unduh video bukti ke [dir] (cache) lalu kembalikan berkasnya — endpoint serve butuh
     * header `Authorization`, jadi URL polos tak bisa diberikan ke pemutar video. `null` =
     * gagal; pemanggil menampilkan pesan, tidak crash.
     *
     * [videoUrl] = URL logis `/uploads/aktivitas-chat/{berkas}` dari field bukti; hanya nama
     * berkasnya yang dipakai (pola [DeliveryFlowRepository.fetchPhoto]).
     */
    suspend fun unduhVideo(videoUrl: String, dir: File): File? = withContext(Dispatchers.IO) {
        try {
            val namaBerkas = videoUrl.trim().substringAfterLast('/')
            if (namaBerkas.isBlank()) return@withContext null
            val response = uploadApi.unduhVideo(namaBerkas)
            val body = response.body()
            if (!response.isSuccessful || body == null) return@withContext null
            dir.mkdirs()
            val file = File(dir, namaBerkas)
            // ponytail: sapu video lain SEBELUM mengunduh — kepala cabang memeriksa
            // belasan bukti sehari dan tiap berkasnya s/d 20MB, jadi tanpa ini cache
            // app membengkak ratusan MB dalam sepekan. Menghapus SETELAH memutar tidak
            // bisa: berkasnya masih dibaca aplikasi pemutar bawaan HP.
            dir.listFiles()?.forEach { lama -> if (lama.name != namaBerkas) lama.delete() }
            body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file
        } catch (e: Exception) {
            null
        }
    }

    /** Keputusan kepala cabang: [status] `approved` atau `rejected` (tolak wajib [alasan]). */
    suspend fun review(id: String, status: String, alasan: String? = null): AuthResult<BuktiChatDto> = try {
        val response = api.review(id, ReviewBuktiRequest(status, alasan))
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan hasil pemeriksaan")
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
