package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.BatchOpnameItemsRequest
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameListData
import com.krisoft.tridjayaelektronik.data.model.OpnameStockData
import com.krisoft.tridjayaelektronik.data.model.StokCabangPageDto
import com.krisoft.tridjayaelektronik.data.model.UploadProofResponseDto
import com.krisoft.tridjayaelektronik.data.model.UpsertOpnameItemRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface InventoryApi {

    @GET("api/inventory/stok-cabang")
    suspend fun stokCabang(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("refresh") refresh: Boolean? = null
    ): Response<ApiResponse<StokCabangPageDto>>

    @GET("api/inventory/indent")
    suspend fun listIndent(
        @Query("status") status: String? = null
    ): Response<ApiResponse<IndentListData>>

    @POST("api/inventory/indent")
    suspend fun createIndent(@Body body: CreateIndentRequest): Response<ApiResponse<IndentDto>>

    @Multipart
    @POST("api/inventory/indent/upload-proof")
    suspend fun uploadIndentProof(@Part file: MultipartBody.Part): Response<ApiResponse<UploadProofResponseDto>>

    // ---- Stock opname (hitung fisik) — inventory-service opname module ----

    @GET("api/inventory/opname/context")
    suspend fun opnameContext(): Response<ApiResponse<OpnameContextDto>>

    @GET("api/inventory/opname")
    suspend fun listOpname(@Query("status") status: String? = null): Response<ApiResponse<OpnameListData>>

    @POST("api/inventory/opname")
    suspend fun createOpname(@Body body: CreateOpnameRequest): Response<ApiResponse<OpnameDetailDto>>

    @GET("api/inventory/opname/{id}")
    suspend fun opnameDetail(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>

    @GET("api/inventory/opname/{id}/stock")
    suspend fun opnameStock(@Path("id") id: String): Response<ApiResponse<OpnameStockData>>

    @POST("api/inventory/opname/{id}/items")
    suspend fun upsertOpnameItem(
        @Path("id") id: String,
        @Body body: UpsertOpnameItemRequest
    ): Response<ApiResponse<OpnameDetailDto>>

    @POST("api/inventory/opname/{id}/items/batch")
    suspend fun batchUpsertOpnameItems(
        @Path("id") id: String,
        @Body body: BatchOpnameItemsRequest
    ): Response<ApiResponse<OpnameDetailDto>>

    @POST("api/inventory/opname/{id}/complete")
    suspend fun completeOpname(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>

    @POST("api/inventory/opname/{id}/cancel")
    suspend fun cancelOpname(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>
}
