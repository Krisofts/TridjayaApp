package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.AkiFormCreateData
import com.krisoft.tridjayaelektronik.data.model.AkiFormsData
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.AssignBody
import com.krisoft.tridjayaelektronik.data.model.BrokerListData
import com.krisoft.tridjayaelektronik.data.model.ChecklistConfigData
import com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody
import com.krisoft.tridjayaelektronik.data.model.DecisionBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryCategoriesData
import com.krisoft.tridjayaelektronik.data.model.DiscountListData
import com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto
import com.krisoft.tridjayaelektronik.data.model.UsersListData
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.DeliverBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryContextDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryCreateResult
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryListData
import com.krisoft.tridjayaelektronik.data.model.DeliveryNoteBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryUploadResponse
import com.krisoft.tridjayaelektronik.data.model.PdiBody
import com.krisoft.tridjayaelektronik.data.model.ReturnAkiBody
import com.krisoft.tridjayaelektronik.data.model.SerialListData
import com.krisoft.tridjayaelektronik.data.model.StokCabangData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Alur pengiriman SPK — inventory-service via gateway `/api/inventory/delivery`. */
interface DeliveryFlowApi {

    @GET("api/inventory/delivery")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("view") view: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<DeliveryListData>>

    @GET("api/inventory/delivery/context")
    suspend fun context(): Response<ApiResponse<DeliveryContextDto>>

    @GET("api/inventory/delivery/{id}")
    suspend fun detail(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery")
    suspend fun create(@Body body: CreateDeliveryBody): Response<ApiResponse<DeliveryCreateResult>>

    @POST("api/inventory/delivery/{id}/pdi")
    suspend fun submitPdi(@Path("id") id: String, @Body body: PdiBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/spk")
    suspend fun confirmSpk(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/delivery-note")
    suspend fun issueDeliveryNote(@Path("id") id: String, @Body body: DeliveryNoteBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/assign")
    suspend fun assign(@Path("id") id: String, @Body body: AssignBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/dispatch")
    suspend fun dispatch(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/deliver")
    suspend fun deliver(@Path("id") id: String, @Body body: DeliverBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/cancel")
    suspend fun cancel(@Path("id") id: String, @Query("reason") reason: String): Response<ApiResponse<DeliveryJobDto>>

    @Multipart
    @POST("api/inventory/delivery/upload-photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<ApiResponse<DeliveryUploadResponse>>

    @GET("api/inventory/delivery/config/checklist")
    suspend fun checklist(
        @Query("kategori") kategori: String,
        @Query("stage") stage: String? = null
    ): Response<ApiResponse<ChecklistConfigData>>

    /** 088: catat driver sudah chat konsumen H-1 (idempoten, fan-out per batch SPK). */
    @POST("api/inventory/delivery/{id}/chat-consumer")
    suspend fun chatConsumer(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    /** Autocomplete barang Input SPK, di-scope satu cabang. */
    @GET("api/inventory/stok-cabang")
    suspend fun stokCabang(
        @Query("search") search: String,
        @Query("kodeDealer") kodeDealer: String,
        @Query("limit") limit: Int = 24
    ): Response<ApiResponse<StokCabangData>>

    /** Autocomplete broker KBK — di-scope query. */
    @GET("api/inventory/delivery/brokers")
    suspend fun brokers(@Query("q") q: String): Response<ApiResponse<BrokerListData>>

    /** Registry serial per cabang+barang (picker No. Rangka Input SPK). */
    @GET("api/inventory/serial-numbers")
    suspend fun serialNumbers(
        @Query("kodeDealer") kodeDealer: String,
        @Query("kodeBarang") kodeBarang: String,
        @Query("onlySerial") onlySerial: Boolean = true,
        @Query("excludeAssigned") excludeAssigned: Boolean = true
    ): Response<ApiResponse<SerialListData>>

    @GET("api/inventory/delivery/config/categories")
    suspend fun categories(): Response<ApiResponse<DeliveryCategoriesData>>

    @GET("api/inventory/delivery/{id}/aki-form")
    suspend fun jobAkiForms(@Path("id") id: String): Response<ApiResponse<AkiFormsData>>

    @POST("api/inventory/delivery/{id}/aki-form")
    suspend fun createAkiForm(@Path("id") id: String, @Body body: CreateAkiFormBody): Response<ApiResponse<AkiFormCreateData>>

    /** Daftar riwayat form aki (admin/manager lintas cabang; PDI cabang sendiri). */
    @GET("api/inventory/delivery/aki-forms")
    suspend fun akiForms(): Response<ApiResponse<AkiFormsData>>

    /** Tandai aki bekas dikembalikan. */
    @POST("api/inventory/delivery/aki-forms/{id}/return")
    suspend fun returnAkiForm(@Path("id") id: String, @Body body: ReturnAkiBody): Response<ApiResponse<AkiFormCreateData>>

    @GET("api/users")
    suspend fun users(@Query("role") role: String): Response<ApiResponse<UsersListData>>

    @GET("api/inventory/discount-requests")
    suspend fun discountRequests(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<ApiResponse<DiscountListData>>

    @POST("api/inventory/discount-requests/{id}/approve")
    suspend fun approveDiscount(@Path("id") id: String, @Body body: DecisionBody): Response<ApiResponse<DiscountRequestDto>>

    @POST("api/inventory/discount-requests/{id}/reject")
    suspend fun rejectDiscount(@Path("id") id: String, @Body body: DecisionBody): Response<ApiResponse<DiscountRequestDto>>
}
