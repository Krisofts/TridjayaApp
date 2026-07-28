package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsData
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsRequest
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailListDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriListDto
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameListData
import com.krisoft.tridjayaelektronik.data.model.OpnameStockData
import com.krisoft.tridjayaelektronik.data.model.StokCabangPageDto
import com.krisoft.tridjayaelektronik.data.model.UpdateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.UploadProofResponseDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitListData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface InventoryApi {

    /**
     * [inStock] `true` = hanya baris berstok > 0. WAJIB dikirim eksplisit oleh sinkronisasi:
     * default server pernah berarti "seluruh katalog dealer" (66.482 baris, hanya 5,5% berstok
     * — perubahan perilaku SP GS `GetStokCabang` 28 Jul 2026), dan server lama/rollback bisa
     * mengembalikan default itu lagi. Jangan bergantung pada default sisi server.
     */
    @GET("api/inventory/stok-cabang")
    suspend fun stokCabang(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("refresh") refresh: Boolean? = null,
        @Query("inStock") inStock: Boolean? = null
    ): Response<ApiResponse<StokCabangPageDto>>

    @GET("api/inventory/indent")
    suspend fun listIndent(
        @Query("status") status: String? = null
    ): Response<ApiResponse<IndentListData>>

    @POST("api/inventory/indent")
    suspend fun createIndent(@Body body: CreateIndentRequest): Response<ApiResponse<IndentDto>>

    @PATCH("api/inventory/indent/{id}")
    suspend fun updateIndentStatus(
        @Path("id") id: String,
        @Body body: UpdateIndentRequest
    ): Response<ApiResponse<IndentDto>>

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

    @POST("api/inventory/opname/{id}/units")
    suspend fun createOpnameUnits(
        @Path("id") id: String,
        @Body body: CreateOpnameUnitsRequest
    ): Response<ApiResponse<CreateOpnameUnitsData>>

    @GET("api/inventory/opname/{id}/units")
    suspend fun listOpnameUnits(@Path("id") id: String): Response<ApiResponse<OpnameUnitListData>>

    @DELETE("api/inventory/opname/{id}/units/{unitId}")
    suspend fun deleteOpnameUnit(
        @Path("id") id: String,
        @Path("unitId") unitId: String
    ): Response<ApiResponse<OpnameDetailDto>>

    @POST("api/inventory/opname/{id}/complete")
    suspend fun completeOpname(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>

    @POST("api/inventory/opname/{id}/cancel")
    suspend fun cancelOpname(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>

    // ---- Mutasi histori (arsip GS, read-only) — dipakai buat cek barang "dalam perjalanan"
    // saat search stok kosong (lihat InventoryRepository.findInTransitHint) ----

    @GET("api/inventory/mutasi-histori")
    suspend fun mutasiHistori(
        @Query("dealer") dealer: String? = null,
        @Query("arah") arah: String? = null,
        @Query("from") from: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<MutasiHistoriListDto>>

    @GET("api/inventory/mutasi-histori/detail")
    suspend fun mutasiHistoriDetail(
        @Query("noTransaksi") noTransaksi: String,
        @Query("arah") arah: String
    ): Response<ApiResponse<MutasiHistoriDetailListDto>>
}
