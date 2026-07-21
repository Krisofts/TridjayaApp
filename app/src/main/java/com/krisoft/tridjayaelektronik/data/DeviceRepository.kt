package com.krisoft.tridjayaelektronik.data

import com.google.firebase.messaging.FirebaseMessaging
import com.krisoft.tridjayaelektronik.data.model.RegisterDeviceRequest
import com.krisoft.tridjayaelektronik.data.remote.DeviceApi
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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
        val token = fetchFcmToken() ?: return false
        return try {
            api.register(RegisterDeviceRequest(token = token)).isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    /** Daftarkan token spesifik (dipakai `FcmService.onNewToken`). */
    suspend fun registerToken(token: String): Boolean {
        if (!authRepository.isLoggedIn || token.isBlank()) return false
        return try {
            api.register(RegisterDeviceRequest(token = token)).isSuccessful
        } catch (_: Exception) {
            false
        }
    }

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
