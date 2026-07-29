package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.CreateSerialNumbersBody
import com.krisoft.tridjayaelektronik.data.model.CreateSerialRequestBody
import com.krisoft.tridjayaelektronik.data.model.MutasiContextDto
import com.krisoft.tridjayaelektronik.data.model.SerialCreateResultDto
import com.krisoft.tridjayaelektronik.data.model.SerialRequestDto
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    /**
     * Produk BERSTOK di cabang (search kosong = tanpa filter teks server; UI
     * filter client-side). Filter `Stok > 0` datang dari default `inStock` di
     * [DeliveryFlowApi.stokCabang] — tanpa itu katalog GS penuh (~5.500 baris
     * per cabang) MELEBIHI [STOK_FETCH_LIMIT] dan daftar produknya terpotong
     * senyap. SN memang cuma diinput untuk unit yang fisiknya ada di gudang.
     */
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

    /** Foto bukti usulan (JPEG sudah dikompres pemanggil) → URL relatif. */
    suspend fun uploadPhoto(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData("file", filename, bytes.toRequestBody("image/jpeg".toMediaType()))
        val response = api.uploadSerialPhoto(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah foto")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Usulkan pendaftaran SN dari cabang. Serial dinormalkan di sini dengan
     * [normalizeSerial] yang SAMA dengan scan opname — aturan berbeda sedikit
     * saja berarti usulan lolos untuk serial yang server tolak, atau usulan
     * ganda untuk serial yang server anggap sama.
     */
    suspend fun proposeSerial(
        kodeDealer: String,
        kodeBarang: String,
        namaBarang: String?,
        serialNumberRaw: String,
        fotoSnUrl: String,
        fotoBarangUrl: String,
        opnameSessionId: String?,
        catatan: String?
    ): AuthResult<SerialRequestDto> {
        val serial = normalizeSerial(serialNumberRaw)
            ?: return AuthResult.Failure(
                "validation",
                "Serial kosong atau lebih dari $SERIAL_MAX_LENGTH karakter"
            )
        return try {
            val response = api.createSerialRequest(
                CreateSerialRequestBody(
                    kodeDealer = kodeDealer,
                    kodeBarang = kodeBarang,
                    namaBarang = namaBarang,
                    serialNumber = serial,
                    fotoSnUrl = fotoSnUrl,
                    fotoBarangUrl = fotoBarangUrl,
                    opnameSessionId = opnameSessionId,
                    catatan = catatan
                )
            )
            val data = response.body()?.data
            if (response.isSuccessful && data != null) AuthResult.Success(data)
            else parseError(response, "Gagal mengirim usulan SN")
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
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
