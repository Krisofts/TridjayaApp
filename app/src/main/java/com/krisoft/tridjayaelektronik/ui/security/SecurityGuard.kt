package com.krisoft.tridjayaelektronik.ui.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** Jenis ancaman yang memblokir aplikasi. */
enum class ThreatType { MOCK, ROOT }

/** Satu ancaman terdeteksi (aplikasi mock/berbahaya atau kondisi perangkat). */
data class Threat(
    val packageName: String,
    val label: String,
    val type: ThreatType
)

/**
 * Deteksi integritas perangkat untuk absensi: **aplikasi mock location** (fake GPS) dan kondisi
 * berbahaya (root). Bila ada, aplikasi memblok diri (hanya menampilkan splash pemblokir) sampai
 * pengguna mencopotnya — mencegah pemalsuan titik absen. Pemeriksaan bersifat heuristik best-effort.
 */
object SecurityGuard {

    /** Paket fake-GPS / mock-location yang umum dikenal (dicek walau tak menyatakan izin mock). */
    private val KNOWN_SPOOFERS = setOf(
        "com.lexa.fakegps",
        "com.incorporateapps.fakegps.fre",
        "com.incorporateapps.fakegps",
        "com.blogspot.newapphorizons.fakegps",
        "com.theappninjas.gpsjoystick",
        "com.evezzon.fakegps",
        "com.rosteam.gpsemulator",
        "com.gsmartstudio.fakegps",
        "ru.gavrikov.mocklocations",
        "com.fakegps.mock",
        "com.lkr.fakelocation",
        "com.hola.gpslocation",
        "com.just4funtools.fakegpslocationprofessional",
        "com.byfen.fakegps",
        "com.fakegps.mokelocation"
    )

    /** Kembalikan daftar ancaman; kosong = perangkat bersih. */
    fun detect(context: Context): List<Threat> {
        val pm = context.packageManager
        val self = context.packageName
        val found = LinkedHashMap<String, Threat>()

        // 1) Scan semua app terpasang yang menyatakan izin ACCESS_MOCK_LOCATION (bukan sistem).
        val installed = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            emptyList()
        }
        for (pi in installed) {
            val pkg = pi.packageName ?: continue
            if (pkg == self) continue
            val ai = pi.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val wantsMock = pi.requestedPermissions?.any {
                it == "android.permission.ACCESS_MOCK_LOCATION"
            } == true
            if (wantsMock && !isSystem) {
                found[pkg] = Threat(pkg, labelOf(pm, ai, pkg), ThreatType.MOCK)
            }
        }

        // 2) Paket spoofer terkenal walau tak masuk hasil scan (mis. QUERY_ALL_PACKAGES dibatasi).
        for (pkg in KNOWN_SPOOFERS) {
            if (found.containsKey(pkg)) continue
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            found[pkg] = Threat(pkg, labelOf(pm, ai, pkg), ThreatType.MOCK)
        }

        // 3) Perangkat ter-root (biner su) — sinyal kuat, hindari false-positive test-keys.
        if (isRooted()) {
            found["__root__"] = Threat("__root__", "Perangkat ter-root", ThreatType.ROOT)
        }

        return found.values.toList()
    }

    private fun labelOf(pm: PackageManager, ai: ApplicationInfo, fallback: String): String =
        runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(fallback)

    private fun isRooted(): Boolean {
        val suPaths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/system/xbin/daemonsu"
        )
        return suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
            // Magisk umum
            runCatching { File("/sbin/.magisk").exists() }.getOrDefault(false)
    }

    /** Info tambahan (tak dipakai untuk memblok, hanya konteks debug/log). */
    fun androidInfo(): String = "Android ${Build.VERSION.RELEASE} (${Build.MODEL})"
}
