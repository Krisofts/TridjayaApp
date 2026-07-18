package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateOffRequest
import com.krisoft.tridjayaelektronik.data.model.OffListDto
import com.krisoft.tridjayaelektronik.data.model.OffRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Pengajuan izin/OFF karyawan — kinerja-service via gateway `/api/off-requests`. */
interface OffApi {

    @GET("api/off-requests")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<OffListDto>>

    @POST("api/off-requests")
    suspend fun create(@Body body: CreateOffRequest): Response<ApiResponse<OffRequestDto>>
}
