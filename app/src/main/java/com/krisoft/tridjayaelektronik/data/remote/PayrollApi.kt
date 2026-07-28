package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.PayslipDetailData
import com.krisoft.tridjayaelektronik.data.model.PayslipListData
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Slip gaji milik sendiri — kinerja-service via gateway `/api/payroll`. Endpoint manager
 *  (`/payroll/categories`, `/payroll/periods*`, dst) sengaja tidak diekspos di sini —
 *  "Kelola Gaji" desktop-admin, di luar scope layar mobile ini. */
interface PayrollApi {

    @GET("api/payroll/me")
    suspend fun me(): Response<ApiResponse<PayslipListData>>

    @GET("api/payroll/payslips/{id}")
    suspend fun detail(@Path("id") id: Long): Response<ApiResponse<PayslipDetailData>>
}
