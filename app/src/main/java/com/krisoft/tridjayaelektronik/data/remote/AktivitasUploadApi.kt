package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.AktivitasUploadData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Unggah bukti raport — SENGAJA dipisah dari [AktivitasApi].
 *
 * [AktivitasApi] memakai client bersama yang write/read timeout-nya 20 detik.
 * Bukti video boleh sampai 30 MB (`MAX_EVIDENCE_BYTES` di
 * `kinerja-service/src/raport.rs`), dan di jaringan cabang unggahan sebesar itu
 * TIDAK akan selesai dalam 20 detik — gagalnya muncul sebagai "tidak bisa
 * terhubung ke server", yang membuat orang menyalahkan sinyal atau server
 * padahal batas waktunya milik kita sendiri.
 *
 * Pola client-unggah-terpisah, yang lahir dari alasan yang sama untuk
 * video bukti chat.
 */
interface AktivitasUploadApi {

    /**
     * Nama part WAJIB `file` — itu yang dibaca `upload_evidence`
     * (`raport/handlers.rs`). Server memvalidasi ekstensi × content-type ×
     * magic bytes SERENTAK, jadi nama berkas dan MIME yang dikirim harus
     * sepasang (lihat `ekstensiVideo`/`mimeVideo` di `AktivitasBuktiPlan.kt`).
     */
    @Multipart
    @POST("api/raport-harian/upload")
    suspend fun uploadEvidence(@Part file: MultipartBody.Part): Response<ApiResponse<AktivitasUploadData>>
}
