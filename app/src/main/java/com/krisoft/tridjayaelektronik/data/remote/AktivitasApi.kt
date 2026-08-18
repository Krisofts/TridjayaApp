package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.AktivitasDivisionsData
import com.krisoft.tridjayaelektronik.data.model.AktivitasListData
import com.krisoft.tridjayaelektronik.data.model.AktivitasUploadData
import com.krisoft.tridjayaelektronik.data.model.ReviewAktivitasBody
import com.krisoft.tridjayaelektronik.data.model.ReviewAktivitasResult
import com.krisoft.tridjayaelektronik.data.model.SubmitAktivitasBody
import com.krisoft.tridjayaelektronik.data.model.SubmitAktivitasResult
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Aktivitas harian / laporan aktivitas — kinerja-service via gateway.
 * `POST /api/raport-harian` di-gate role `karyawan` (raport.rs `KARYAWAN_ROLES`);
 * `GET` lebih longgar (`LIST_ROLES`) tapi untuk role `karyawan` server memaksa
 * scope ke miliknya sendiri.
 */
interface AktivitasApi {

    /**
     * Master aktivitas per posisi (`app_settings` `jobdesk_divisions`).
     *
     * Rute & kunci settings SENGAJA tetap mengeja "jobdesk": keduanya kontrak
     * yang hidup di gateway dan basis data, bukan istilah yang dipakai di layar.
     */
    @GET("api/jobdesk-divisions")
    suspend fun divisions(): Response<ApiResponse<AktivitasDivisionsData>>

    /**
     * `karyawan_id` (snake_case, sama dengan web) SENGAJA dikirim walau server
     * sudah memaksa scope untuk role `karyawan`: user multi-role yang role
     * PRIMARY-nya bukan `karyawan` lolos pemaksaan itu dan akan menerima baris
     * seluruh karyawan.
     */
    @GET("api/raport-harian")
    suspend fun list(
        @Query("tanggal") tanggal: String,
        @Query("karyawan_id") karyawanId: String? = null,
        @Query("limit") limit: Int = 200,
        /** `pending|approved|rejected|all` — nilai lain dijawab 422 oleh server. */
        @Query("status") status: String? = null,
        /** Cari nama karyawan / aktivitas / cabang / divisi (LIKE di server). */
        @Query("q") q: String? = null,
    ): Response<ApiResponse<AktivitasListData>>

    @POST("api/raport-harian")
    suspend fun submit(@Body body: SubmitAktivitasBody): Response<ApiResponse<SubmitAktivitasResult>>

    /**
     * Putusan PIC — `REVIEW_ROLES` (rust-shared `AKTIVITAS_REVIEW_ROLES`, kunci
     * kemampuan `raport.review`). Server TIDAK menyaring cabang/divisi: siapa
     * pun yang lolos role boleh menilai baris siapa pun.
     */
    @PATCH("api/raport-harian/{id}/review")
    suspend fun review(
        @Path("id") id: String,
        @Body body: ReviewAktivitasBody,
    ): Response<ApiResponse<ReviewAktivitasResult>>

    @Multipart
    @POST("api/raport-harian/upload")
    suspend fun uploadEvidence(@Part file: MultipartBody.Part): Response<ApiResponse<AktivitasUploadData>>
}
