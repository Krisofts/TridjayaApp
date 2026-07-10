package com.krisoft.tridjayaelektronik.data.update

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.krisoft.tridjayaelektronik.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of an update check. */
sealed interface UpdateStatus {
    /** App is on the latest (or newer) version. */
    data object UpToDate : UpdateStatus

    /** Couldn't determine (Firebase not configured / offline / fetch failed) — treated as no-force. */
    data object Unknown : UpdateStatus

    /** A newer version exists. [force] = the running version is below the minimum supported and must update. */
    data class Available(
        val force: Boolean,
        val latestVersionName: String,
        val releaseNotes: String,
        val updateUrl: String
    ) : UpdateStatus
}

/**
 * Checks for app updates via Firebase Remote Config, powering the in-app "Cek Pembaruan" button and
 * the startup force-update gate. Remote Config keys (set them in the Firebase console):
 *  - `min_version_code`   (Number)  running versionCode below this ⇒ FORCE update
 *  - `latest_version_code`(Number)  running versionCode below this ⇒ optional update prompt
 *  - `latest_version_name`(String)  shown in the dialog
 *  - `update_url`         (String)  Play Store / APK download link opened by the Update button
 *  - `release_notes`      (String)  what's new
 *
 * Firebase is optional at build time: without a `google-services.json` there is no default
 * [FirebaseApp], so [check] returns [UpdateStatus.Unknown] and nothing is forced.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    suspend fun check(): UpdateStatus {
        // No Firebase configured (no google-services.json) → can't check, never force.
        if (FirebaseApp.getApps(context).isEmpty()) return UpdateStatus.Unknown

        return try {
            val config = Firebase.remoteConfig
            config.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
                }
            ).awaitResult()
            config.setDefaultsAsync(
                mapOf(
                    KEY_MIN to 0L,
                    KEY_LATEST to currentVersionCode.toLong(),
                    KEY_LATEST_NAME to currentVersionName,
                    KEY_URL to "",
                    KEY_NOTES to ""
                )
            ).awaitResult()
            config.fetchAndActivate().awaitResult()

            val current = currentVersionCode.toLong()
            val min = config.getLong(KEY_MIN)
            val latest = config.getLong(KEY_LATEST)
            val name = config.getString(KEY_LATEST_NAME).ifBlank { currentVersionName }
            val url = config.getString(KEY_URL)
            val notes = config.getString(KEY_NOTES)

            when {
                current < min -> UpdateStatus.Available(force = true, name, notes, url)
                current < latest -> UpdateStatus.Available(force = false, name, notes, url)
                else -> UpdateStatus.UpToDate
            }
        } catch (_: Exception) {
            UpdateStatus.Unknown
        }
    }

    private companion object {
        const val KEY_MIN = "min_version_code"
        const val KEY_LATEST = "latest_version_code"
        const val KEY_LATEST_NAME = "latest_version_name"
        const val KEY_URL = "update_url"
        const val KEY_NOTES = "release_notes"
    }
}

/** Awaits a Play-services [Task] without pulling the kotlinx-coroutines-play-services dependency. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) cont.resumeWithException(error) else cont.resume(task.result)
    }
}
