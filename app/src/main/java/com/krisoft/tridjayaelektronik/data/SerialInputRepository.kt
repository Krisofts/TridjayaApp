package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.CreateSerialNumbersBody
import com.krisoft.tridjayaelektronik.data.model.MutasiContextDto
import com.krisoft.tridjayaelektronik.data.model.SerialCreateResultDto
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val STOK_FETCH_LIMIT = 5000

/**
 * Input Serial Number (admin-stok) — reuse [DeliveryFlowApi] (endpoint mutasi/stok-cabang/
 * serial-numbers sudah ada di sana utk picker SPK) daripada bikin Retrofit interface baru.
 * Tanpa cache lokal — data harus real-time (stok GS + registry SN berubah cepat).
 */
@Singleton
class SerialInputRepository @Inject constructor(
    private val api: DeliveryFlowApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Dealer akun login (admin-stok terikat satu cabang). */
    suspend fun context(): AuthResult<MutasiContextDto> = try {
        val response = api.mutasiContext()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat konteks cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Semua produk stok cabang (search kosong = tanpa filter server; UI filter client-side). */
    suspend fun stokCabang(kodeDealer: String): AuthResult<List<StokCabangRow>> = try {
        val response = api.stokCabang(search = "", kodeDealer = kodeDealer, limit = STOK_FETCH_LIMIT)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat stok cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Jumlah SN yang sudah tercatat utk satu produk (onlySerial/excludeAssigned=false = SEMUA baris). */
    suspend fun existingSerialCount(kodeDealer: String, kodeBarang: String): AuthResult<Int> = try {
        val response = api.serialNumbers(
            kodeDealer = kodeDealer,
            kodeBarang = kodeBarang,
            onlySerial = false,
            excludeAssigned = false
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items.size)
        else parseError(response, "Gagal memuat serial tercatat")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun createSerialNumbers(
        kodeDealer: String,
        kodeBarang: String,
        namaBarang: String?,
        serialNumbers: List<String>
    ): AuthResult<SerialCreateResultDto> = try {
        val response = api.createSerialNumbers(
            CreateSerialNumbersBody(
                kodeDealer = kodeDealer,
                kodeBarang = kodeBarang,
                namaBarang = namaBarang,
                serialNumbers = serialNumbers
            )
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan serial number")
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
