package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.KpiDetailData
import com.krisoft.tridjayaelektronik.data.model.KpiListData
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * KPI — kinerja-service via gateway `/api/kpi`. `periode` opsional: kosong =
 * bulan berjalan (server yang menentukan, bukan jam HP).
 *
 * Endpoint tulis (`PUT /kpi/actuals`, `/kpi/assignments`) sengaja tidak
 * diekspos — penilaian HR dikerjakan di web.
 */
interface KpiApi {

    @GET("api/kpi/me")
    suspend fun me(@Query("periode") periode: String?): Response<ApiResponse<KpiDetailData>>

    @GET("api/kpi/karyawan")
    suspend fun karyawan(@Query("periode") periode: String?): Response<ApiResponse<KpiListData>>

    @GET("api/kpi/karyawan/{id}")
    suspend fun karyawanDetail(
        @Path("id") id: String,
        @Query("periode") periode: String?
    ): Response<ApiResponse<KpiDetailData>>
}
