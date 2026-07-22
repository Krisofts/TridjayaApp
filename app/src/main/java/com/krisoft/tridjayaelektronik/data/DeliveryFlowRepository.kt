package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.AssignBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.DeliverBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryContextDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryCreateResult
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryNoteBody
import com.krisoft.tridjayaelektronik.data.model.PdiBody
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alur pengiriman SPK → antar, langsung ke backend `inventory-service` via [DeliveryFlowApi].
 * Tanpa cache lokal — data harus real-time (antrian per-tahap berpindah cepat, RBAC di server).
 * Setiap aksi tahap mengembalikan [DeliveryJobDto] terbaru dari server.
 */
@Singleton
class DeliveryFlowRepository @Inject constructor(
    private val api: DeliveryFlowApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun list(status: String? = null, view: String? = null): AuthResult<List<DeliveryJobDto>> = try {
        val response = api.list(status = status, view = view, limit = 200)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat daftar pengiriman")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun detail(id: String): AuthResult<DeliveryJobDto> = call("Gagal memuat detail") { api.detail(id) }

    suspend fun context(): AuthResult<DeliveryContextDto> = try {
        val response = api.context()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat konteks cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun create(body: CreateDeliveryBody): AuthResult<DeliveryCreateResult> = try {
        val response = api.create(body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal membuat SPK")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun submitPdi(id: String, body: PdiBody): AuthResult<DeliveryJobDto> =
        call("Gagal simpan PDI") { api.submitPdi(id, body) }

    suspend fun confirmSpk(id: String): AuthResult<DeliveryJobDto> =
        call("Gagal konfirmasi SPK") { api.confirmSpk(id) }

    suspend fun issueDeliveryNote(id: String, body: DeliveryNoteBody): AuthResult<DeliveryJobDto> =
        call("Gagal terbitkan surat jalan") { api.issueDeliveryNote(id, body) }

    suspend fun assign(id: String, body: AssignBody): AuthResult<DeliveryJobDto> =
        call("Gagal assign driver") { api.assign(id, body) }

    suspend fun dispatch(id: String): AuthResult<DeliveryJobDto> =
        call("Gagal berangkat") { api.dispatch(id) }

    suspend fun deliver(id: String, body: DeliverBody): AuthResult<DeliveryJobDto> =
        call("Gagal tandai terkirim") { api.deliver(id, body) }

    suspend fun cancel(id: String, reason: String): AuthResult<DeliveryJobDto> =
        call("Gagal membatalkan") { api.cancel(id, reason) }

    suspend fun checklist(kategori: String): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>> = try {
        val response = api.checklist(kategori)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items.filter { it.aktif })
        else parseError(response, "Gagal memuat checklist PDI")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun categories(): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.DeliveryCategoryDto>> = try {
        val response = api.categories()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat kategori PDI")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun jobAkiForms(id: String): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>> = try {
        val response = api.jobAkiForms(id)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat form pengambilan aki")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun createAkiForm(id: String, body: com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody): AuthResult<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = try {
        val response = api.createAkiForm(id, body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.form)
        else parseError(response, "Gagal simpan form pengambilan aki")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun drivers(): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.DriverDto>> = try {
        val response = api.users("driver")
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat daftar driver")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun discounts(status: String? = "pending"): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>> = try {
        val response = api.discountRequests(status = status)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat pengajuan diskon")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun approveDiscount(id: String, note: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = decision("Gagal menyetujui diskon") {
        api.approveDiscount(id, com.krisoft.tridjayaelektronik.data.model.DecisionBody(note.ifBlank { null }))
    }

    suspend fun rejectDiscount(id: String, note: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = decision("Gagal menolak diskon") {
        api.rejectDiscount(id, com.krisoft.tridjayaelektronik.data.model.DecisionBody(note.ifBlank { null }))
    }

    private inline fun decision(
        fallback: String,
        block: () -> Response<com.krisoft.tridjayaelektronik.data.model.ApiResponse<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>>
    ): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = try {
        val response = block()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, fallback)
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Upload foto (JPEG) → URL relatif untuk dikirim di body tahap (PDI/deliver). */
    suspend fun uploadPhoto(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData("file", filename, bytes.toRequestBody("image/jpeg".toMediaType()))
        val response = api.uploadPhoto(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah foto")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private inline fun call(
        fallback: String,
        block: () -> Response<com.krisoft.tridjayaelektronik.data.model.ApiResponse<DeliveryJobDto>>
    ): AuthResult<DeliveryJobDto> = try {
        val response = block()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, fallback)
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}
