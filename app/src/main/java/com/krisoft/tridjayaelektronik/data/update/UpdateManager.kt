package com.krisoft.tridjayaelektronik.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.remote.ApkApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apakah unduhan sampai tuntas. MURNI supaya bisa diuji tanpa Android.
 *
 * `total <= 0` = server tak memberi `Content-Length` (atau terkompresi) → panjangnya TAK BISA
 * diverifikasi, jadi dianggap utuh dan penjaga kedua (`getPackageArchiveInfo`) yang menilai.
 * Menolak di sini justru akan mematikan update pada server yang sah.
 *
 * `copied > total` juga dianggap TIDAK utuh: kalau itu terjadi, yang kita tulis bukan berkas
 * yang dijanjikan server, dan menyerahkannya ke installer sama saja menebak.
 */
internal fun unduhanUtuh(copied: Long, total: Long): Boolean =
    if (total <= 0L) true else copied == total

/** Result of an update check. */
sealed interface UpdateStatus {
    /** App is on the latest (or newer) version, or no APK/version has ever been published. */
    data object UpToDate : UpdateStatus

    /** Couldn't determine (offline / request failed) — treated as no-force. */
    data object Unknown : UpdateStatus

    /** A newer version exists. [force] = superadmin marked it mandatory — the app must update.
     *
     *  [latestVersionCode] adalah angka yang dibandingkan dengan `BuildConfig.VERSION_CODE` di
     *  [UpdateManager.check]. Ia dibawa keluar (bukan cuma dipakai lalu dibuang) karena
     *  penutupan prompt oleh pengguna disimpan PER VERSI — lihat `bolehTampilkanPrompt` di
     *  `ui/update/UpdatePrompt.kt`. [latestVersionName] tidak bisa dipakai untuk itu: ia
     *  `String` bebas dari server, boleh null (`ApkMetaDto.versionName`) dan jatuh ke `"?"`. */
    data class Available(
        val force: Boolean,
        val latestVersionCode: Long,
        val latestVersionName: String,
        val releaseNotes: String
    ) : UpdateStatus
}

/** Progress of an in-app APK download + install prompt. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    /** [progress] 0f..1f, or null when the server didn't report a content length (indeterminate). */
    data class Downloading(val progress: Float?) : UpdateDownloadState

    /** Download finished and the system installer prompt was already fired once for [fileUri] —
     *  kept around so the UI can re-fire it (e.g. user backgrounded the app mid-prompt). */
    data class ReadyToInstall(val fileUri: Uri) : UpdateDownloadState

    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * Checks for app updates via **our own backend** (`GET /api/users/app-apk/meta`, uploaded/tagged
 * from the web admin panel — see `AdminAppApkPage.tsx` / `user-service/src/apk.rs` in the
 * `Tridjaya` repo), not Firebase Remote Config — superadmin marks a release mandatory there and
 * every mobile session picks it up on next launch. Also handles the in-app download + install
 * prompt: the backend has no way to force-install (Android doesn't allow silent install without
 * device-owner/MDM provisioning, which this app doesn't use), so a **mandatory** update
 * auto-starts its download the moment [UpdateStatus.Available.force] is detected and fires the
 * system package-installer intent as soon as the file lands — the single unavoidable step left is
 * the OS's own "Install" confirmation tap (and, the very first time, an "allow installs from this
 * app" settings toggle if not already granted). Optional updates use the same download+install
 * path, just user-triggered instead of automatic.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apkApi: ApkApi
) {
    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    suspend fun check(): UpdateStatus {
        return try {
            val response = apkApi.meta()
            if (!response.isSuccessful) return UpdateStatus.Unknown
            val meta = response.body()?.data ?: return UpdateStatus.UpToDate
            val serverCode = meta.versionCode ?: return UpdateStatus.UpToDate
            if (serverCode <= BuildConfig.VERSION_CODE) return UpdateStatus.UpToDate
            UpdateStatus.Available(
                force = meta.mandatory,
                latestVersionCode = serverCode,
                latestVersionName = meta.versionName ?: "?",
                releaseNotes = meta.changelog.orEmpty()
            )
        } catch (_: Exception) {
            UpdateStatus.Unknown
        }
    }

    /** Streams the APK to app-private storage, reporting progress via [onProgress]
     *  (`null` = indeterminate). Returns a `content://` [Uri] via [FileProvider] ready to hand to
     *  the system installer.
     *
     *  **Berkasnya DIPERIKSA sebelum diserahkan ke installer** (2026-08-01). Sebelumnya tidak:
     *  `contentLength()` cuma dipakai untuk bar progres, jumlah byte yang benar-benar tertulis
     *  tak pernah dibandingkan dengan apa pun, dan berkas apa adanya langsung diberikan ke
     *  installer. Koneksi yang putus di tengah — hal biasa untuk petugas di lapangan —
     *  menghasilkan APK terpotong, dan Android menolaknya dengan "Aplikasi tidak terpasang" /
     *  "masalah saat mengurai paket". Gejalanya menyesatkan: app yang SUDAH terpasang tetap
     *  jalan normal, jadi yang terlihat cuma "update-nya rusak" (laporan nyata dari pemakai
     *  Android 11, 2026-08-01), padahal berkas di server utuh dan tanda tangannya benar.
     *
     *  Dua pemeriksaan, karena masing-masing menangkap kegagalan yang berbeda:
     *  1. [unduhanUtuh] — byte tertulis vs `Content-Length`. Menangkap koneksi putus.
     *  2. `getPackageArchiveInfo` — Android sendiri yang mencoba mengurai berkasnya. Ini yang
     *     menangkap kerusakan TANPA perubahan panjang (proxy menyisipkan halaman galat, berkas
     *     lama tersisa) DAN kasus server tak mengirim `Content-Length` sama sekali.
     *
     *  Berkas gagal DIHAPUS: meninggalkannya berarti percobaan berikutnya bisa memakai sisa
     *  yang rusak, dan itu persis lingkaran "coba lagi, gagal lagi" yang dilaporkan.
     *
     *  Lokasinya pindah `cacheDir` -> `filesDir`: Android membersihkan cache saat penyimpanan
     *  menipis, dan APK yang lenyap ANTARA unduh dan pasang gagal tanpa pesan yang berarti.
     *  `file_paths.xml` ikut diubah (`files-path`), kalau tidak `FileProvider` melempar
     *  IllegalArgumentException. */
    suspend fun downloadApk(onProgress: (Float?) -> Unit): Result<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "update").apply { mkdirs() }
        val file = File(dir, "employee-app.apk")
        try {
            val response = apkApi.download()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(IOException("Unduhan gagal (${response.code()})"))
            }
            val total = body.contentLength()
            var copied = 0L
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    onProgress(if (total > 0) 0f else null)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null)
                    }
                    output.fd.sync()
                }
            }
            if (!unduhanUtuh(copied, total)) {
                file.delete()
                return@withContext Result.failure(
                    IOException("Unduhan terputus (${copied / 1024} dari ${total / 1024} KB). Pastikan sinyal stabil lalu coba lagi.")
                )
            }
            if (context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) == null) {
                file.delete()
                return@withContext Result.failure(
                    IOException("Berkas update rusak dan sudah dihapus. Coba unduh ulang; kalau tetap gagal, minta APK-nya dikirim manual.")
                )
            }
            Result.success(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
            // Sisa unduhan separuh jalan tak boleh ditinggal — percobaan berikutnya
            // menimpanya, tapi `promptInstall` bisa saja dipanggil dengan Uri lama.
            file.delete()
            Result.failure(e)
        }
    }

    /** Fires the system package-installer for a previously-downloaded APK ([downloadApk]'s
     *  result). Requires `REQUEST_INSTALL_PACKAGES` (manifest) — the OS handles the "allow
     *  installs from this app" prompt itself the first time it's needed, we don't request it
     *  explicitly. */
    fun promptInstall(fileUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
