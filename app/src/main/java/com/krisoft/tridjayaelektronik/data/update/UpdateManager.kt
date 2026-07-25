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

/** Result of an update check. */
sealed interface UpdateStatus {
    /** App is on the latest (or newer) version, or no APK/version has ever been published. */
    data object UpToDate : UpdateStatus

    /** Couldn't determine (offline / request failed) — treated as no-force. */
    data object Unknown : UpdateStatus

    /** A newer version exists. [force] = superadmin marked it mandatory — the app must update. */
    data class Available(
        val force: Boolean,
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
                latestVersionName = meta.versionName ?: "?",
                releaseNotes = meta.changelog.orEmpty()
            )
        } catch (_: Exception) {
            UpdateStatus.Unknown
        }
    }

    /** Streams the APK to app-private cache storage, reporting progress via [onProgress]
     *  (`null` = indeterminate). Returns a `content://` [Uri] via [FileProvider] ready to hand to
     *  the system installer. */
    suspend fun downloadApk(onProgress: (Float?) -> Unit): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val response = apkApi.download()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(IOException("Unduhan gagal (${response.code()})"))
            }
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            val file = File(dir, "employee-app.apk")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    onProgress(if (total > 0) 0f else null)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null)
                    }
                }
            }
            Result.success(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
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
