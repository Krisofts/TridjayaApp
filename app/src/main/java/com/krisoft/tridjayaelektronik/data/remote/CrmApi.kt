package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateLeadRequest
import com.krisoft.tridjayaelektronik.data.model.LeadDetailData
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.data.model.LeadListData
import com.krisoft.tridjayaelektronik.data.model.LostLeadRequest
import com.krisoft.tridjayaelektronik.data.model.MoveStageRequest
import com.krisoft.tridjayaelektronik.data.model.PipelinesData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CrmApi {

    @GET("api/crm/pipelines")
    suspend fun pipelines(): Response<ApiResponse<PipelinesData>>

    @GET("api/crm/leads")
    suspend fun listLeads(
        @Query("assignedTo") assignedTo: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<LeadListData>>

    @GET("api/crm/leads/{id}")
    suspend fun leadDetail(@Path("id") id: Long): Response<ApiResponse<LeadDetailData>>

    @POST("api/crm/leads")
    suspend fun createLead(@Body body: CreateLeadRequest): Response<ApiResponse<LeadDto>>

    @POST("api/crm/leads/{id}/move-stage")
    suspend fun moveStage(@Path("id") id: Long, @Body body: MoveStageRequest): Response<ApiResponse<LeadDto>>

    @POST("api/crm/leads/{id}/won")
    suspend fun markWon(@Path("id") id: Long): Response<ApiResponse<LeadDto>>

    @POST("api/crm/leads/{id}/lost")
    suspend fun markLost(@Path("id") id: Long, @Body body: LostLeadRequest): Response<ApiResponse<LeadDto>>

    @POST("api/crm/leads/{id}/reopen")
    suspend fun reopenLead(@Path("id") id: Long): Response<ApiResponse<LeadDto>>
}
