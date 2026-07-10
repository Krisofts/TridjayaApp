package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.BranchPerformanceData
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.SalesPerformanceData
import com.krisoft.tridjayaelektronik.data.model.StokCabangPageDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface InventoryApi {

    @GET("api/inventory/stok-cabang")
    suspend fun stokCabang(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("refresh") refresh: Boolean? = null
    ): Response<ApiResponse<StokCabangPageDto>>

    @GET("api/inventory/executive/kpi")
    suspend fun executiveKpi(): Response<ApiResponse<ExecutiveKpiDto>>

    @GET("api/inventory/executive/monthly-target")
    suspend fun monthlyTarget(): Response<ApiResponse<MonthlyTargetDto>>

    @GET("api/inventory/branch-performance")
    suspend fun branchPerformance(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("compareStartDate") compareStartDate: String,
        @Query("compareEndDate") compareEndDate: String
    ): Response<ApiResponse<BranchPerformanceData>>

    @GET("api/inventory/sales-performance")
    suspend fun salesPerformance(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("compareStartDate") compareStartDate: String,
        @Query("compareEndDate") compareEndDate: String
    ): Response<ApiResponse<SalesPerformanceData>>
}
