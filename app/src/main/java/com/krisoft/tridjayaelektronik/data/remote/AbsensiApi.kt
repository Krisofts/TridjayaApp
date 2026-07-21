package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.AbsensiListDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiPunchRequest
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiTodayDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiUploadPhotoDto
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/** Absensi karyawan (check-in/out + selfie + geofence) — kinerja-service via gateway `/api/absensi`. */
interface AbsensiApi {

    @GET("api/absensi/today")
    suspend fun today(): Response<ApiResponse<AbsensiTodayDto>>

    @GET("api/absensi")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 60,
        @Query("tanggalFrom") tanggalFrom: String? = null,
        @Query("tanggalTo") tanggalTo: String? = null
    ): Response<ApiResponse<AbsensiListDto>>

    @POST("api/absensi/check-in")
    suspend fun checkIn(@Body body: AbsensiPunchRequest): Response<ApiResponse<AbsensiRecordDto>>

    @POST("api/absensi/check-out")
    suspend fun checkOut(@Body body: AbsensiPunchRequest): Response<ApiResponse<AbsensiRecordDto>>

    @Multipart
    @POST("api/absensi/upload-photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<ApiResponse<AbsensiUploadPhotoDto>>
}
