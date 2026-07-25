package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ApkMetaDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming

/** Distribusi APK karyawan (user-service via gateway `/api/users/app-apk`) — dipakai splash-check
 *  versi + unduh pembaruan in-app (lihat [com.krisoft.tridjayaelektronik.data.update.UpdateManager]). */
interface ApkApi {

    @GET("api/users/app-apk/meta")
    suspend fun meta(): Response<ApiResponse<ApkMetaDto?>>

    @Streaming
    @GET("api/users/app-apk/download")
    suspend fun download(): Response<ResponseBody>
}
