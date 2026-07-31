package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.BirthdayListData
import retrofit2.Response
import retrofit2.http.GET

/**
 * Ulang tahun karyawan hari ini — login-only, tanpa gate role (semua karyawan
 * boleh ikut mengucapkan). Endpoint sama persis yang dipakai popup web.
 */
interface BirthdayApi {

    @GET("api/birthdays/today")
    suspend fun today(): Response<ApiResponse<BirthdayListData>>
}
