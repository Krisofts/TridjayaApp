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
 * Tiga channel: "approval" (persetujuan absen/izin), "crm" (notifikasi CRM: lead di-assign,
 * pindah stage, won/lost, follow-up terlambat, lead baru), dan "delivery" (alur SPK/pengiriman).
 * Ketiganya dibuat lebih awal via [ensureChannels] supaya notifikasi background (Android 8+) selalu punya channel yang cocok.
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
        // Deep-link halus opsional (mis. "diskon"/"pdi"/"kasir") — buka halaman terkait saat di-tap.
        val route = message.data["route"]
        showNotification(title, body, normalizeChannel(channelId), route)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showNotification(title: String, body: String, channelId: String, route: String? = null) {
        ensureChannels(this)
        // API 33+: tanpa izin POST_NOTIFICATIONS notifikasi tak tampil — jangan crash.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // ID dihitung DULU (dipakai jadi requestCode PendingIntent DAN notify()) — requestCode unik
        // per notifikasi mencegah FLAG_UPDATE_CURRENT menimpa extras (channel) milik notif lain yang
        // masih tampil di tray (dulu requestCode 0 dipakai semua notif → tap notif lama bisa nyasar
        // ke channel notif terbaru).
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pending = channelLaunchPendingIntent(channelId, notifId, route)
        // Query DULU (sebelum posting yang baru) supaya hitungan ringkasan tidak bergantung pada
        // urutan/timing binder call — hitungan grup existing + 1 (yang baru) selalu benar.
        val priorCountInGroup = NotificationManagerCompat.from(this).activeNotifications
            .count { it.notification.group == channelId && it.id != groupSummaryId(channelId) }

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(channelId)
            .build()
        NotificationManagerCompat.from(this).notify(notifId, notif)

        // Ringkasan grup — Android 7+ (API 24) HANYA men-collapse notif ber-`setGroup` sama kalau
        // ada satu notif ringkasan (`setGroupSummary(true)`) menaunginya; tanpa ini tiap notif
        // channel yang sama tetap tampil terpisah di tray walau field `group`-nya sudah sama.
        // Android <7 mengabaikan `setGroup`/`setGroupSummary` — no-op aman, jadi tak perlu dicabang.
        showGroupSummary(channelId, title, priorCountInGroup + 1)
    }

    private fun showGroupSummary(channelId: String, latestTitle: String, totalCount: Int) {
        val label = channelLabel(channelId)
        val summaryText = if (totalCount > 1) "$totalCount notifikasi baru" else latestTitle
        val summaryId = groupSummaryId(channelId)
        val summary = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(label)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(label))
            .setGroup(channelId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(channelLaunchPendingIntent(channelId, summaryId))
            .build()
        NotificationManagerCompat.from(this).notify(summaryId, summary)
    }

    /** ID stabil per channel (bukan timestamp) — ringkasan grup harus selalu menimpa dirinya sendiri,
     *  bukan menumpuk notif ringkasan baru tiap kali. */
    private fun groupSummaryId(channelId: String): Int = channelId.hashCode()

    /** Intent peluncur + PendingIntent dgn `requestCode` unik (dipakai [showNotification] &
     *  [showGroupSummary]) — requestCode sama antar keduanya akan saling timpa extras via
     *  FLAG_UPDATE_CURRENT, jadi tiap pemanggil WAJIB kirim id yang sudah unik miliknya sendiri. */
    private fun channelLaunchPendingIntent(channelId: String, requestCode: Int, route: String? = null): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIF_CHANNEL, channelId)
            if (!route.isNullOrBlank()) putExtra(EXTRA_NOTIF_ROUTE, route)
        }
        return PendingIntent.getActivity(
            this, requestCode, launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun channelLabel(channelId: String): String = when (channelId) {
        CHANNEL_CRM -> "CRM / Prospek"
        CHANNEL_DELIVERY -> "SPK & Pengiriman"
        else -> "Persetujuan Absensi/Izin"
    }

    /** Channel tak dikenal dari server → pakai "approval" supaya notif tetap tampil. */
    private fun normalizeChannel(channelId: String): String = when (channelId) {
        CHANNEL_CRM, CHANNEL_DELIVERY -> channelId
        else -> CHANNEL_APPROVAL
    }

    companion object {
        const val CHANNEL_APPROVAL = "approval"
        const val CHANNEL_CRM = "crm"
        const val CHANNEL_DELIVERY = "delivery"

        /** Extra pada intent peluncur tap-notifikasi — dibaca [com.krisoft.tridjayaelektronik.MainActivity]
         *  untuk deep-link ke layar yang relevan (channel `delivery` → hub SPK, `crm` → tab CRM). */
        const val EXTRA_NOTIF_CHANNEL = "notif_channel"

        /** Extra deep-link HALUS (key hub SPK: diskon/pdi/kasir/note/jadwal/driver/history) —
         *  dibaca MainActivity utk buka LANGSUNG halaman terkait (akses cepat). Foreground-notif
         *  pakai key ini; background (notif dirender OS) pakai [DATA_KEY_ROUTE] dari `data` FCM. */
        const val EXTRA_NOTIF_ROUTE = "notif_route"
        const val DATA_KEY_ROUTE = "route"

        /** Key `data` pada payload FCM (backend kinerja-service `push.rs`). Dipakai HANYA saat
         *  notifikasi dirender OS sendiri (app di background, payload `notification`+`data` →
         *  [onMessageReceived] tak dipanggil) — tap notif meneruskan `data` sbg intent extras dgn
         *  key ini, BUKAN [EXTRA_NOTIF_CHANNEL] (itu cuma dipakai intent yang kita buat sendiri di
         *  [showNotification]/[showGroupSummary]). MainActivity harus cek dua-duanya. */
        const val DATA_KEY_CHANNEL = "channel"

        /**
         * Buat tiga channel notifikasi bila belum ada. Dipanggil di startup app
         * ([TridjayaApplication.onCreate]) — penting supaya notifikasi FCM saat app di
         * **background** (ditampilkan sistem, kode kita tak jalan) sudah punya channel
         * "approval"/"crm"/"delivery" yang cocok di Android 8+; kalau belum ada, notif bisa tak tampil.
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
            if (manager.getNotificationChannel(CHANNEL_DELIVERY) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_DELIVERY, "SPK & Pengiriman", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Notifikasi alur SPK: diskon, PDI, input GS, surat jalan, tugas antar, terkirim"
                    }
                )
            }
        }
    }
}
