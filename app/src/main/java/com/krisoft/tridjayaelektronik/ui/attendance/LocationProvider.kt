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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.Locale

/**
 * Ambil satu titik GPS pakai [LocationManager] framework (tanpa play-services — hemat dependency;
 * cukup untuk kebutuhan absen geofence). Mengembalikan `null` bila izin belum diberi, tak ada
 * provider aktif, atau gagal fix. Caller sudah harus memastikan izin lokasi diberikan.
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
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return lastKnown(lm)
        }

        return suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    lm.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc ?: lastKnown(lm))
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
                    // Kalau fix lambat, tetap ada last-known sebagai cadangan tidak diblok selamanya.
                    lastKnown(lm)?.let { if (cont.isActive) { lm.removeUpdates(listener); cont.resume(it) } }
                }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? = try {
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (_: SecurityException) {
        null
    }

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
