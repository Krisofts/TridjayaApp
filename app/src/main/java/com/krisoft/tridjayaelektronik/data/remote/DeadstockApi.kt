package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.DeadstockListDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * Deadstock cabang — inventory-service via gateway `/api/inventory/deadstock`.
 * `cabang` query SENGAJA tidak diekspos: role cabang (satu-satunya audiens mobile
 * Fase 1, lihat brief) dipaksa dealer sendiri di backend, param itu diabaikan untuk
 * role ini. Brosur upload + audit manager = web-only, tidak ada di sini.
 */
interface DeadstockApi {

    @GET("api/inventory/deadstock")
    suspend fun list(): Response<ApiResponse<DeadstockListDto>>
}
