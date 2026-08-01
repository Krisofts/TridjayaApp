package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.EventLeadDto
import com.krisoft.tridjayaelektronik.data.model.EventListDto
import com.krisoft.tridjayaelektronik.data.model.EventUploadDto
import com.krisoft.tridjayaelektronik.data.model.SubmitEventLeadRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Event lapangan + prospeknya — kinerja-service via gateway `/api/events`. */
interface EventApi {

    /** Daftar event AKTIF + vonis `bolehIsi`/`bolehKelola` untuk pemanggil. */
    @GET("api/events")
    suspend fun daftar(): Response<ApiResponse<EventListDto>>

    @POST("api/events/{id}/leads")
    suspend fun kirimLead(
        @Path("id") id: String,
        @Body body: SubmitEventLeadRequest,
    ): Response<ApiResponse<EventLeadDto>>

    /** Nama part WAJIB "file" — itu yang dibaca server. */
    @Multipart
    @POST("api/events/upload-ktp")
    suspend fun unggahKtp(@Part file: MultipartBody.Part): Response<ApiResponse<EventUploadDto>>

    /**
     * Serve foto KTP ter-autentikasi (S-02). Foto KTP data pribadi, jadi endpoint-nya
     * bukan berkas statis — `<img src>`/URL polos akan dijawab 401. `filename` diambil
     * dari URL logis `/uploads/event/{berkas}` (pola [DeliveryFlowApi.photo]).
     */
    @GET("api/events/photo/{filename}")
    suspend fun foto(@Path("filename") filename: String): Response<ResponseBody>
}
