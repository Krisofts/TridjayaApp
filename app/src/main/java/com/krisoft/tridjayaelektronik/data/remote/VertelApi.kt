package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.VertelCatatBody
import com.krisoft.tridjayaelektronik.data.model.VertelDaftarDto
import com.krisoft.tridjayaelektronik.data.model.VertelPanggilanDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * VERTEL (`inventory-service/src/vertel.rs`, migrasi 257). Rutenya menumpang
 * wildcard gateway `/api/inventory/delivery/{*rest}` yang sudah ada, jadi tak
 * ada rute gateway baru yang perlu di-deploy untuk layar ini.
 *
 * Gate-nya kemampuan `vertel.manage` = `["cs", "admin", "superadmin"]`; jabatan
 * VERIFICATOR DAN REPORTING sampai ke sana lewat slug `cs` hasil lipatan
 * `divisi_access_slugs` (migrasi 223).
 */
interface VertelApi {

    /**
     * Daftar transaksi yang perlu diverifikasi + ringkasannya.
     *
     * [tanggal] `null` = **KEMARIN menurut WIB**, dihitung SERVER
     * (`kemarin_wib`). Jangan menghitungnya di app: zona waktu perangkat bukan
     * zona kerja, dan pada 00:00–07:00 WIB "kemarin" versi UTC menggeser
     * seluruh daftar kerja satu hari tanpa satu pun galat.
     *
     * Cakupannya SELURUH CABANG untuk pemegang `cs`/admin — verifikator duduk
     * di pusat untuk 13 cabang. Tak ada parameter cabang dari klien.
     *
     * Daftarnya hanya selengkap worker mirror `erp_mirror_sales`; mirror basi =
     * halaman sepi, dan gejalanya BUKAN error melainkan layar kosong.
     */
    @GET("api/inventory/delivery/vertel")
    suspend fun daftar(
        @Query("tanggal") tanggal: String? = null,
    ): Response<ApiResponse<VertelDaftarDto>>

    /**
     * Catat hasil satu panggilan. **Upsert** atas kunci
     * `(noTransaksi, tanggal)` — menelepon ulang memperbarui baris yang sama,
     * bukan menambah baris kedua.
     */
    @POST("api/inventory/delivery/vertel/catat")
    suspend fun catat(@Body body: VertelCatatBody): Response<ApiResponse<VertelPanggilanDto>>
}
