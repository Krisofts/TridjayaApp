package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.AktivitasChatTodayDto
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.BuktiChatDto
import com.krisoft.tridjayaelektronik.data.model.BuktiListDto
import com.krisoft.tridjayaelektronik.data.model.KirimBuktiRequest
import com.krisoft.tridjayaelektronik.data.model.ReviewBuktiRequest
import com.krisoft.tridjayaelektronik.data.model.UploadVideoDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Bukti aktivitas chat harian — kinerja-service via gateway `/api/aktivitas-chat`. */
interface AktivitasChatApi {

    @GET("api/aktivitas-chat/today")
    suspend fun today(): Response<ApiResponse<AktivitasChatTodayDto>>

    @POST("api/aktivitas-chat")
    suspend fun kirim(@Body body: KirimBuktiRequest): Response<ApiResponse<BuktiChatDto>>

    @GET("api/aktivitas-chat")
    suspend fun list(
        @Query("tanggal") tanggal: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<ApiResponse<BuktiListDto>>

    @POST("api/aktivitas-chat/{id}/review")
    suspend fun review(
        @Path("id") id: String,
        @Body body: ReviewBuktiRequest,
    ): Response<ApiResponse<BuktiChatDto>>
}

/**
 * ponytail: interface terpisah HANYA supaya unggah video dapat client ber-timeout panjang
 * (lihat [NetworkModule.createAktivitasChatUploadApi]) TANPA ikut melonggarkan timeout
 * endpoint JSON di atas — endpoint JSON justru harus gagal cepat di jaringan cabang.
 */
interface AktivitasChatUploadApi {

    @Multipart
    @POST("api/aktivitas-chat/upload-video")
    suspend fun uploadVideo(@Part file: MultipartBody.Part): Response<ApiResponse<UploadVideoDto>>

    /**
     * Unduh video bukti — endpoint serve butuh header `Authorization`, jadi URL-nya tak bisa
     * diserahkan mentah ke pemutar. Ditaruh di interface INI (bukan [AktivitasChatApi]) karena
     * berkasnya sebesar yang diunggah: lewat client JSON ber-timeout 20 detik, unduhan puluhan
     * MB di jaringan cabang mati di tengah jalan.
     */
    @Streaming
    @GET("api/aktivitas-chat/video/{filename}")
    suspend fun unduhVideo(@Path("filename") filename: String): Response<ResponseBody>
}
