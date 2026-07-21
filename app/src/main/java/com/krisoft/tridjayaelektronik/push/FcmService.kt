package com.krisoft.tridjayaelektronik.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.krisoft.tridjayaelektronik.R
import com.krisoft.tridjayaelektronik.data.DeviceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Terima push FCM + daftarkan token baru. Saat app di background, sistem menampilkan notifikasi
 * otomatis (payload `notification`) memakai `channel_id` dari payload; saat foreground,
 * [onMessageReceived] dipanggil dan kita tampilkan manual pada channel yang sesuai.
 *
 * Dua channel: "approval" (persetujuan absen/izin) dan "crm" (notifikasi CRM: lead di-assign,
 * pindah stage, won/lost, follow-up terlambat, lead baru). Keduanya dibuat lebih awal via
 * [ensureChannels] supaya notifikasi background (Android 8+) selalu punya channel yang cocok.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { deviceRepository.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val title = notification?.title ?: message.data["title"] ?: "Tridjaya Elektronik"
        val body = notification?.body ?: message.data["body"] ?: ""
        // Channel dari payload (server kirim "crm"/"approval"); fallback ke data lalu default.
        val channelId = notification?.channelId
            ?: message.data["channel"]
            ?: CHANNEL_APPROVAL
        showNotification(title, body, normalizeChannel(channelId))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showNotification(title: String, body: String, channelId: String) {
        ensureChannels(this)
        // API 33+: tanpa izin POST_NOTIFICATIONS notifikasi tak tampil — jangan crash.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }

    /** Channel tak dikenal dari server → pakai "approval" supaya notif tetap tampil. */
    private fun normalizeChannel(channelId: String): String =
        if (channelId == CHANNEL_CRM) CHANNEL_CRM else CHANNEL_APPROVAL

    companion object {
        const val CHANNEL_APPROVAL = "approval"
        const val CHANNEL_CRM = "crm"

        /**
         * Buat kedua channel notifikasi bila belum ada. Dipanggil di startup app
         * ([TridjayaApplication.onCreate]) — penting supaya notifikasi FCM saat app di
         * **background** (ditampilkan sistem, kode kita tak jalan) sudah punya channel
         * "crm"/"approval" yang cocok di Android 8+; kalau belum ada, notif bisa tak tampil.
         */
        fun ensureChannels(context: android.content.Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_APPROVAL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_APPROVAL, "Persetujuan Absensi/Izin", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Notifikasi saat pengajuan izin atau absen disetujui/ditolak"
                    }
                )
            }
            if (manager.getNotificationChannel(CHANNEL_CRM) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_CRM, "CRM / Prospek", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Notifikasi lead ditugaskan, perubahan stage, won/lost, dan follow-up terlambat"
                    }
                )
            }
        }
    }
}
