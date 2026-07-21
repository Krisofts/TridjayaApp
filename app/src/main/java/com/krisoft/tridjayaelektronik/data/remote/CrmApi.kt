package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.AssigneesData
import com.krisoft.tridjayaelektronik.data.model.CreateProspekData
import com.krisoft.tridjayaelektronik.data.model.CreateProspekRequest
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
        @Query("createdBy") createdBy: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<LeadListData>>

    @GET("api/crm/leads/{id}")
    suspend fun leadDetail(@Path("id") id: Long): Response<ApiResponse<LeadDetailData>>

    // Create goes through /api/prospek-harian (kinerja-service) — the same endpoint as the web's
    // Submit Prospek form — so mobile-created prospects count toward the daily target/raport and
    // trigger the assignment notification, instead of silently bypassing them via /api/crm/leads.
    @POST("api/prospek-harian")
    suspend fun createProspek(@Body body: CreateProspekRequest): Response<ApiResponse<CreateProspekData>>

    /** Active employees (all branches, sales & non-sales) selectable as the prospect's assignee. */
    @GET("api/prospek-harian/assignees")
    suspend fun assignees(): Response<ApiResponse<AssigneesData>>

    @POST("api/crm/leads/{id}/move-stage")
    suspend fun moveStage(@Path("id") id: Long, @Body body: MoveStageRequest): Response<ApiResponse<LeadDto>>

    @POST("api/crm/leads/{id}/won")
    suspend fun markWon(@Path("id") id: Long): Response<ApiResponse<LeadDto>>

    @POST("api/crm/leads/{id}/lost")
    suspend fun markLost(@Path("id") id: Long, @Body body: LostLeadRequest): Response<ApiResponse<LeadDto>>

    @POST("api/crm/leads/{id}/reopen")
    suspend fun reopenLead(@Path("id") id: Long): Response<ApiResponse<LeadDto>>
}
