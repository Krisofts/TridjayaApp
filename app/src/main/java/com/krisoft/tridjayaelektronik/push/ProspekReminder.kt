package com.krisoft.tridjayaelektronik.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.R
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.local.LeadDao
import com.krisoft.tridjayaelektronik.data.local.LeadEntity
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaEntity
import com.krisoft.tridjayaelektronik.data.model.parseIsoUtcMillis
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Pengingat prospek mandek — SELURUHNYA di device, nol request jaringan.
 *
 * "Mandek" = prospek yang masih `open` (belum divonis deal/tidak-deal) dan `updatedAt`-nya
 * sudah lewat [STALE_THRESHOLD_MILLIS]. Data dibaca dari cache Room `leads`, yang isinya
 * gabungan `assignedTo=me` + `createdBy=me` (lihat `CrmRepository.fetchAndCacheLeads`) —
 * persis cakupan yang diminta, jadi TIDAK ada penyaringan kepemilikan di sini.
 *
 * Spec: docs/superpowers/specs/2026-07-30-prospek-stale-reminder-ondevice-design.md (repo Tridjaya)
 */

/**
 * Ambang "mandek" — SATU-SATUNYA tempat angka ini hidup. Teks notifikasi menurunkan
 * jumlah harinya dari sini, jangan mengetik angkanya sebagai literal di kalimat.
 *
 * CATATAN yang disampaikan ke user saat desain: dengan target input 20 prospek/hari,
 * ambang 24 jam membuat hampir semua prospek baru terhitung mandek besok paginya.
 * Kalau notifikasinya mulai diabaikan, naikkan angka DI SINI lalu bump APK.
 */
internal val STALE_THRESHOLD_MILLIS: Long = TimeUnit.DAYS.toMillis(1)

/** Judul notifikasi — dipakai juga oleh worker, jadi satu tempat. */
internal const val REMINDER_TITLE = "Prospek belum di-update"

/** Sebanyak ini nama disebut; sisanya diringkas jadi angka. */
private const val MAX_NAMES = 3

/**
 * Prospek `open` yang umur `updatedAt`-nya sudah >= [STALE_THRESHOLD_MILLIS], TERURUT
 * terlama dulu. Baris ber-`updatedAt` tak terbaca dilewati (bukan dianggap paling tua).
 */
internal fun staleProspek(leads: List<LeadEntity>, nowMillis: Long): List<LeadEntity> =
    leads.asSequence()
        .filter { it.status == "open" }
        .mapNotNull { lead ->
            val updated = updatedAtMillis(lead.updatedAt) ?: return@mapNotNull null
            if (nowMillis - updated >= STALE_THRESHOLD_MILLIS) lead to updated else null
        }
        .sortedBy { it.second }
        .map { it.first }
        .toList()

/** Badan notifikasi. Menganggap [stale] SUDAH terurut terlama dulu (hasil [staleProspek]). */
internal fun reminderBody(stale: List<LeadEntity>, nowMillis: Long): String {
    if (stale.isEmpty()) return ""
    val hariAmbang = STALE_THRESHOLD_MILLIS / TimeUnit.DAYS.toMillis(1)
    val baris = stale.take(MAX_NAMES).joinToString("\n") { lead ->
        val nama = lead.nama.trim().ifEmpty { "(tanpa nama)" }
        "• $nama — ${ageLabel(lead.updatedAt, nowMillis)}"
    }
    val sisa = stale.size - MAX_NAMES
    val ekor = if (sisa > 0) "\ndan $sisa lainnya" else ""
    return "${stale.size} prospek belum di-update ≥$hariAmbang hari:\n$baris$ekor"
}

/**
 * `updatedAt` → epoch millis. Backend mengirim RFC3339 (`...T...Z`), tapi baris cache lama
 * bisa ber-separator spasi — dinormalkan dulu, pola sama `ui/activity/ActivityPlan.kt`.
 */
private fun updatedAtMillis(updatedAt: String): Long? =
    parseIsoUtcMillis(updatedAt.trim().takeIf { it.isNotEmpty() }?.replace(' ', 'T'))

private fun ageLabel(updatedAt: String, nowMillis: Long): String {
    val updated = updatedAtMillis(updatedAt) ?: return "lama"
    return "${(nowMillis - updated) / TimeUnit.DAYS.toMillis(1)} hari"
}

/** Jam dinding device saat pengingat dikirim. */
private const val REMINDER_HOUR = 9

/** Nama unik pekerjaan periodik — dipakai `enqueueUniquePeriodicWork`. */
private const val WORK_NAME = "prospek_stale_reminder"

/**
 * ID notifikasi STABIL (bukan timestamp): kiriman hari ini menimpa kiriman kemarin, jadi
 * tray tak menumpuk pengingat lama yang isinya sudah salah. Dipakai juga sebagai
 * `requestCode` PendingIntent — angkanya sengaja jauh dari ID milik [FcmService]
 * (timestamp) dan dari ringkasan grupnya (`"crm".hashCode()` = 98782).
 */
private const val NOTIF_ID = 910_001

/**
 * Jadwalkan pengingat harian. Idempoten: [ExistingPeriodicWorkPolicy.KEEP] menjaga jadwal
 * yang sudah berjalan, supaya membuka app tidak menggeser jam bunyinya tiap kali.
 *
 * Dipanggil dari `TridjayaApplication.onCreate` — TANPA hook login/logout: worker sendiri
 * berhenti kalau sesi kosong, jadi tak ada tempat kedua yang bisa lupa diperbarui.
 */
fun scheduleProspekReminder(context: Context) {
    val work = WorkManager.getInstance(context)
    work.enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<ProspekStaleWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextRun(System.currentTimeMillis()), TimeUnit.MILLISECONDS)
            .build()
    )
    // ponytail: pemicu langsung khusus build debug — tanpa ini satu-satunya cara melihat
    // notifikasinya adalah menunggu jam 09:00. Nama-uniknya sendiri (`WORK_NAME + "_debug"`)
    // + REPLACE supaya tiap restart proses menimpa permintaan lama, bukan menumpuknya.
    // Hapus kalau debug jadi terlalu berisik.
    if (BuildConfig.DEBUG) {
        work.enqueueUniqueWork(
            WORK_NAME + "_debug",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ProspekStaleWorker>().build()
        )
    }
}

/**
 * Jeda millis dari [nowMillis] ke pukul [hour]:00 waktu device berikutnya. Selalu > 0:
 * kalau jamnya hari ini sudah lewat (atau tepat sekarang), lompat ke besok.
 *
 * `Calendar`, bukan `java.time` — modul ini minSdk 24 tanpa `coreLibraryDesugaring`.
 */
internal fun millisUntilNextRun(nowMillis: Long, hour: Int = REMINDER_HOUR): Long {
    val target = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_MONTH, 1)
    }
    return target.timeInMillis - nowMillis
}

/**
 * Pembaca cache + pemosting notifikasi. NOL request jaringan — permintaan eksplisit user
 * (jangan bebani server). Konsekuensi yang diterima sadar: kalau app tak dibuka berhari-hari,
 * cache basi dan notifikasinya bisa menyebut prospek yang sudah digerakkan lewat web.
 *
 * Dependensi diambil lewat [EntryPointAccessors], bukan `@HiltWorker` — lihat catatan di
 * `app/build.gradle.kts`.
 */
class ProspekStaleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun leadDao(): LeadDao
        fun syncMetaDao(): SyncMetaDao
        fun tokenStore(): TokenStore
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        // Belum login: tak ada prospek milik siapa pun untuk diingatkan.
        if (!deps.tokenStore().isLoggedIn) return@withContext Result.success()
        // Cache belum pernah disinkron sama sekali → tabelnya kosong atau masih sisa akun
        // sebelumnya. Diam lebih baik daripada mengingatkan berdasarkan data yang bukan miliknya.
        val lastSync = deps.syncMetaDao().get(SyncMetaEntity.KEY_LEADS)?.lastSyncMillis ?: 0L
        if (lastSync == 0L) return@withContext Result.success()

        val now = System.currentTimeMillis()
        val stale = staleProspek(deps.leadDao().all(), now)
        // Nol prospek mandek → tidak posting apa pun (bukan notifikasi "0 prospek").
        if (stale.isNotEmpty()) postReminder(applicationContext, stale, now)
        Result.success()
    }
}

private fun postReminder(context: Context, stale: List<LeadEntity>, nowMillis: Long) {
    // Channel "crm" milik FcmService — bukan channel baru. Dipastikan ada dulu: Android 8+
    // membuang notifikasi yang channel-nya belum terdaftar.
    FcmService.ensureChannels(context)
    // API 33+: tanpa POST_NOTIFICATIONS, notify() tidak menampilkan apa pun dan kegagalannya
    // tak terlihat sama sekali. Pola persis FcmService.showNotification.
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        // MainActivity SUDAH membaca extra ini dan membuka tab CRM — tak ada perubahan di sana.
        putExtra(FcmService.EXTRA_NOTIF_CHANNEL, FcmService.CHANNEL_CRM)
    }
    val pending = PendingIntent.getActivity(
        context, NOTIF_ID, launch ?: Intent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notif = NotificationCompat.Builder(context, FcmService.CHANNEL_CRM)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(REMINDER_TITLE)
        .setContentText("${stale.size} prospek perlu ditindaklanjuti")
        .setStyle(NotificationCompat.BigTextStyle().bigText(reminderBody(stale, nowMillis)))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pending)
        // SENGAJA tidak ikut grup "crm": ringkasannya dimiliki & dihitung ulang oleh
        // FcmService dari notifikasi yang SUDAH ada di tray saat itu — kalau pengingat ini
        // ikut nimbrung, dia jadi anak yang lahir setelah hitungan itu dibuat: ringkasan
        // under-report, judul/isi pengingat ini sendiri tersembunyi sampai user expand
        // grupnya, dan push CRM berikutnya dari server malah ikut menghitungnya sebagai
        // anak lama. Berdiri di luar grup, teks pengingat ini selalu langsung terbaca.
        // Channel tetap "crm".
        .build()
    NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
}
