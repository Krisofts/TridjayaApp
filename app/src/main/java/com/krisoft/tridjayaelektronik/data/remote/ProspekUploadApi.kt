package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ProspekUploadData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Unggah bukti PROSPEK — dipisah dari [CrmApi] dengan alasan yang sama seperti
 * [RaportUploadApi] dipisah dari [RaportApi]: client bersama ber-timeout 20
 * detik, dan bukti sampai 8 MB (`MAX_BUKTI_PROSPEK_BYTES`) tak selesai dalam
 * 20 detik di jaringan cabang. Gagalnya muncul sebagai "tidak bisa terhubung
 * ke server", yang membuat orang menyalahkan sinyal padahal batas waktunya
 * milik kita sendiri.
 */
interface ProspekUploadApi {

    /**
     * Nama part WAJIB `file` — itu yang dibaca `upload_bukti_prospek`
     * (`kinerja-service/src/prospek.rs`); part bernama lain DILEWATI diam-diam
     * dan permintaannya berakhir "File bukti wajib diunggah".
     *
     * Balasannya `{ "url": "/uploads/prospek/<nama>" }`. Kirim `url` itu APA
     * ADANYA sebagai `buktiUrl` saat membuat prospek — server menolak path
     * yang tak berbentuk demikian, dan sejak 2026-08-18 juga memeriksa
     * berkasnya benar-benar ada.
     */
    @Multipart
    @POST("api/prospek-harian/bukti")
    suspend fun uploadBukti(@Part file: MultipartBody.Part): Response<ApiResponse<ProspekUploadData>>
}
