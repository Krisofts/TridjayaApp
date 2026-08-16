package com.krisoft.tridjayaelektronik.data

import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.krisoft.tridjayaelektronik.data.model.RegisterDeviceRequest
import com.krisoft.tridjayaelektronik.data.remote.DeviceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "DeviceRepository"

/** Tiga percobaan ≈ 15 detik total — cukup melewati pemulihan sinyal saat app
 *  baru dibuka, tanpa menahan coroutine pemanggilnya sampai menit-menitan. */
private const val MAKS_PERCOBAAN = 3
private const val JEDA_ULANG_AWAL_MS = 3_000L

/**
 * Jeda sebelum percobaan berikutnya (0 = sesudah percobaan pertama): 3 dtk,
 * lalu 12 dtk. Fungsi murni supaya jadwalnya bisa diuji tanpa perangkat —
 * pola sama `RaportBuktiPlan`/`OpnameJendela`.
 */
internal fun jedaUlangMs(percobaan: Int): Long {
    var jeda = JEDA_ULANG_AWAL_MS
    repeat(percobaan.coerceIn(0, MAKS_PERCOBAAN)) { jeda *= 4 }
    return jeda
}

/**
 * Daftar FCM device token ke backend agar user login bisa menerima push (approval izin/absen).
 * Idempotent (upsert per token di server). No-op bila belum login atau Firebase tak tersedia.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val api: DeviceApi,
    private val authRepository: AuthRepository
) {
    /** Ambil token FCM terkini lalu daftarkan. Aman dipanggil berkali-kali. */
    suspend fun registerCurrentToken(): Boolean {
        if (!authRepository.isLoggedIn) return false
        val token = fetchFcmToken()
        if (token == null) {
            // Bukan sekadar "lewati": tanpa token FCM perangkat ini TIDAK AKAN
            // menerima satu pun push, dan diamnya adalah satu-satunya gejala.
            Log.w(TAG, "token FCM tak bisa diambil (Firebase belum siap / Play Services bermasalah) — perangkat ini tak akan menerima push")
            return false
        }
        return kirimDenganUlang(token)
    }

    /** Daftarkan token spesifik (dipakai `FcmService.onNewToken`). */
    suspend fun registerToken(token: String): Boolean {
        if (!authRepository.isLoggedIn || token.isBlank()) return false
        return kirimDenganUlang(token)
    }

    /**
     * Pendaftaran token dengan percobaan ulang berjeda.
     *
     * Versi lama sekali-tembak lalu `catch (_: Exception) { false }`, dan KEDUA
     * pemanggilnya (`SessionViewModel`, `AttendanceViewModel`) MEMBUANG nilai
     * baliknya — jadi satu kegagalan jaringan pada detik-detik app dibuka
     * (justru saat sinyal paling sering belum pulih) berarti perangkat itu tak
     * pernah terdaftar SELAMA PROSESNYA HIDUP: pendaftaran hanya terpicu saat
     * `SessionViewModel` dibentuk, dan Android menahan proses berhari-hari.
     *
     * Bukan dugaan — terukur di produksi 16 Agustus 2026: 116 device token,
     * tapi hanya 4 yang diperbarui dalam 24 jam terakhir, sementara 13 akun
     * yang absen ber-selfie (jelas memakai app) tak punya token sama sekali.
     *
     * Dua detail yang menentukan:
     *  - `CancellationException` DILEMPAR ULANG, bukan ditelan. Tanpa itu loop
     *    ini terus berjalan sesudah scope pemanggilnya dibatalkan — cacat yang
     *    tak ada di versi sekali-tembak karena tak ada yang bisa berlanjut.
     *  - `catch (e: Exception)`, BUKAN `runCatching`. `runCatching` menangkap
     *    `Throwable`, dan di app ini `NoClassDefFoundError` (Android 7) sudah
     *    dua kali berubah jadi nol senyap gara-gara itu (lihat CLAUDE.md).
     */
    private suspend fun kirimDenganUlang(token: String): Boolean {
        repeat(MAKS_PERCOBAAN) { percobaan ->
            val sebab: String = try {
                val respons = api.register(requestFor(token))
                if (respons.isSuccessful) {
                    if (percobaan > 0) {
                        Log.i(TAG, "token FCM terdaftar pada percobaan ke-${percobaan + 1}")
                    }
                    return true
                }
                "HTTP ${respons.code()}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.toString()
            }
            Log.w(TAG, "pendaftaran token FCM gagal (${percobaan + 1}/$MAKS_PERCOBAAN): $sebab")
            if (percobaan < MAKS_PERCOBAAN - 1) delay(jedaUlangMs(percobaan))
        }
        Log.w(TAG, "pendaftaran token FCM menyerah sesudah $MAKS_PERCOBAAN percobaan — perangkat ini tak akan menerima push sampai app dibuka ulang")
        return false
    }

    /**
     * Telemetri perangkat ikut menumpang pendaftaran token — request yang SUDAH ada,
     * dipanggil tiap kali app dibuka dan tiap `onNewToken`, jadi tak perlu jalur baru.
     *
     * `SUPPORTED_ABIS[0]` = ABI UTAMA perangkat, bukan daftarnya: yang menentukan
     * apakah sebuah HP bisa menjalankan biner native tertentu. `SUPPORTED_ABIS` ada
     * sejak API 21, aman untuk `minSdk = 24`.
     */
    private fun requestFor(token: String) = RegisterDeviceRequest(
        token = token,
        abi = Build.SUPPORTED_ABIS.firstOrNull(),
        model = Build.MODEL,
        sdkInt = Build.VERSION.SDK_INT
    )

    private suspend fun fetchFcmToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                cont.resume(if (task.isSuccessful) task.result else null)
            }
        } catch (_: Exception) {
            // Firebase belum ter-inisialisasi (google-services.json tak ada) → lewati.
            cont.resume(null)
        }
    }
}
