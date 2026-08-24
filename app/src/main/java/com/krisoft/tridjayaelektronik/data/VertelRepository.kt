package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.VertelCatatBody
import com.krisoft.tridjayaelektronik.data.model.VertelDaftarDto
import com.krisoft.tridjayaelektronik.data.model.VertelPanggilanDto
import com.krisoft.tridjayaelektronik.data.remote.VertelApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifikasi telepon konsumen (VERTEL).
 *
 * **Tanpa cache lokal.** Isinya daftar kerja harian yang dikerjakan bersama
 * (verifikator + cadangan admin), dan barisnya berubah begitu salah satu dari
 * mereka mencatat hasil. Daftar basi di HP berarti satu konsumen ditelepon dua
 * kali — persis yang keputusan "satu baris per TRANSAKSI" di server berusaha
 * cegah.
 */
@Singleton
class VertelRepository @Inject constructor(
    private val api: VertelApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** [tanggal] `null` = kemarin menurut WIB, ditentukan SERVER. */
    suspend fun daftar(tanggal: String? = null): AuthResult<VertelDaftarDto> = try {
        val response = api.daftar(tanggal)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat daftar verifikasi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun catat(body: VertelCatatBody): AuthResult<VertelPanggilanDto> = try {
        val response = api.catat(body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan hasil verifikasi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * `errors[0]` diutamakan, dan di modul ini itu BUKAN kemewahan:
     * `validasi_catat` mengirim seluruh sebabnya lewat `errors` ("Komplain
     * hanya bisa dicatat pada panggilan yang terhubung", "Isi catatan
     * komplainnya…") sementara `message` cuma kalimat generik. Tanpa ini
     * verifikator menekan simpan lalu melihat pesan yang tak menyebut apa yang
     * salah.
     */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull()?.takeIf { it.isNotBlank() }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: parsed?.message ?: "$fallback (${response.code()})",
        )
    }
}
