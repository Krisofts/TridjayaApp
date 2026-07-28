package com.krisoft.tridjayaelektronik.ui.attendance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.util.Locale

/** Umur maksimum fix ter-cache yang masih boleh dipakai langsung tanpa menunggu
 *  fix baru. Geofence absensi berdiameter puluhan meter — orang tak berpindah
 *  gedung dalam 2 menit, jadi fix sesegar ini menjawab pertanyaan yang sama. */
internal const val FIX_CACHE_MAX_AGE_MS = 2 * 60 * 1000L

/** Akurasi terburuk (meter) yang masih diterima untuk jalan cepat. Di atas ini
 *  fix-nya terlalu kabur untuk memutuskan di dalam/di luar radius, jadi lebih
 *  baik menunggu yang segar. */
internal const val FIX_CACHE_MAX_ACCURACY_M = 100f

/** Batas tunggu fix segar sebelum menyerah ke cache. Tanpa ini, permintaan GPS
 *  dingin di dalam gedung bisa menggantung sampai timeout internal platform
 *  (puluhan detik) dan UI ikut membeku. */
internal const val FIX_TIMEOUT_MS = 8_000L

/**
 * Fix ter-cache layak dipakai langsung? Fungsi murni supaya bisa diuji tanpa
 * Android (`Location` itu kelas framework; modul ini hanya punya JUnit4).
 *
 * [accuracyMeters] diisi [Float.MAX_VALUE] bila perangkat tak melaporkan
 * akurasi — "tak diketahui" diperlakukan sebagai tak layak, bukan dianggap
 * bagus. Umur negatif (jam perangkat mundur) juga ditolak.
 */
internal fun cachedFixAcceptable(ageMillis: Long, accuracyMeters: Float): Boolean =
    ageMillis in 0..FIX_CACHE_MAX_AGE_MS &&
        accuracyMeters > 0f &&
        accuracyMeters <= FIX_CACHE_MAX_ACCURACY_M

/**
 * Ambil satu titik GPS pakai [LocationManager] framework (tanpa play-services — hemat dependency;
 * cukup untuk kebutuhan absen geofence). Mengembalikan `null` bila izin belum diberi, tak ada
 * provider aktif, atau gagal fix. Caller sudah harus memastikan izin lokasi diberikan.
 *
 * **Urutan usaha (cepat dulu, akurat belakangan)** — dulu kebalikannya, dan itu
 * sebabnya pembacaan lokasi terasa lama (laporan lapangan 2026-07-28):
 *  1. fix ter-cache yang masih segar → dipakai langsung, nol tunggu;
 *  2. fix segar dari provider TERCEPAT yang aktif (`FUSED` di API 31+, lalu
 *     `NETWORK`, baru `GPS`) dengan batas waktu [FIX_TIMEOUT_MS];
 *  3. lewat batas → cache apa adanya, walau agak basi.
 *
 * Versi lama selalu meminta fix SEGAR dari `GPS_PROVIDER` lebih dulu bila GPS
 * aktif, dan `NETWORK` cuma dipakai kalau GPS mati. Fix GPS dingin di dalam
 * gudang/toko butuh 30-60 detik, kadang tak pernah datang. Ironisnya cabang
 * API < 30 sudah resume dengan `lastKnown` seketika — jadi perangkat LAMA cepat
 * dan perangkat baru lambat. Sekarang kedua cabang berperilaku sama.
 */
object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // 1. Jalan cepat: fix yang sudah ada dan masih segar.
        val cached = lastKnown(lm)
        if (cached != null && cachedFixAcceptable(ageMillisOf(cached), accuracyOf(cached))) {
            return cached
        }

        // 2. Fix segar dari provider tercepat yang aktif, dibatasi waktu.
        val provider = fastestEnabledProvider(lm) ?: return cached
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) { requestFix(context, lm, provider) }

        // 3. Menyerah ke cache (mungkin basi) daripada mengembalikan null.
        return fresh ?: lastKnown(lm) ?: cached
    }

    /** Provider aktif yang paling cepat memberi fix. `FUSED_PROVIDER` (API 31+)
     *  adalah fused milik FRAMEWORK — bukan play-services, jadi tak menambah
     *  dependency apa pun. */
    private fun fastestEnabledProvider(lm: LocationManager): String? =
        candidateProviders().firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    private fun candidateProviders(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(
        context: Context,
        lm: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                lm.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                    @Deprecated("Deprecated in API 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Fix tersimpan TERBARU lintas provider (bukan GPS-dulu-baru-NETWORK):
     *  yang paling baru itulah yang paling mungkin masih relevan. */
    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        candidateProviders()
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }

    /** Umur fix dari jam monotonic perangkat — kebal terhadap perubahan jam
     *  dinding (ganti timezone / sinkronisasi NTP) yang bisa membuat fix segar
     *  terlihat berumur jam-jaman. */
    private fun ageMillisOf(loc: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000

    private fun accuracyOf(loc: Location): Float =
        if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE

    /**
     * Reverse-geocode satu titik jadi alamat terbaca (jalan/kelurahan/kota-kabupaten/provinsi) —
     * `Geocoder` bawaan Android, tanpa dependency baru (play-services-location dsb). `null` bila
     * backend geocoder tak ada di device itu atau gagal (offline dsb.) — caller fallback ke koordinat
     * mentah, sama filosofi best-effort seperti [current].
     */
    @Suppress("DEPRECATION")
    suspend fun addressFor(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale("in", "ID")).getFromLocation(lat, lng, 1)?.firstOrNull()?.let { addr ->
                // Disusun manual field-per-field (bukan getAddressLine) supaya kalau satu bagian null
                // (umum di area yang kurang ter-mapping) sisanya tetap tampil, bukan alamat kosong.
                listOfNotNull(
                    addr.thoroughfare ?: addr.featureName,
                    addr.subLocality,
                    (addr.locality ?: addr.subAdminArea)?.takeIf { it.isNotBlank() },
                    addr.subAdminArea?.takeIf { it.isNotBlank() && it != addr.locality },
                    addr.adminArea
                ).distinct().joinToString(", ").ifBlank { null }
            }
        }.getOrNull()
    }
}
