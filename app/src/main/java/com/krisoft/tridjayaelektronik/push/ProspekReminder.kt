package com.krisoft.tridjayaelektronik.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
 * sudah lewat [STALE_THRESHOLD_MILLIS]. Data dibaca dari cache Room `leads`, yang SEHARUSNYA
 * berisi cuma `assignedTo=me` + `createdBy=me` (lihat `CrmRepository.fetchAndCacheLeads`) —
 * TAPI cache itu bisa memuat baris akun SEBELUMNYA di HP bersama (lihat [milikSaya]), jadi
 * penyaringan kepemilikan tetap WAJIB dilakukan di sini, bukan diasumsikan dari cara cache diisi.
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

/**
 * Cache lebih tua dari ini = jangan mengingatkan apa pun. Membuka app TIDAK
 * menyinkron prospek — `CrmRepository.syncLeadsIfStale` cuma terpanggil dari layar
 * Home-Summary (yang hanya didarati manager/owner) atau layar Leads, sedangkan sales
 * mendarat di tab Activity. Tanpa batas ini, orang yang berhenti memakai CRM
 * seminggu tetap dibacakan nama-nama basi tiap pagi, termasuk prospek yang sudah ia
 * tutup lewat web, tanpa koreksi apa pun. Diam lebih baik daripada salah.
 */
private val MAX_CACHE_AGE_MILLIS: Long = TimeUnit.DAYS.toMillis(3)

/** Judul notifikasi — dipakai juga oleh worker, jadi satu tempat. */
internal const val REMINDER_TITLE = "Prospek belum di-update"

/** Sebanyak ini nama disebut; sisanya diringkas jadi angka. */
private const val MAX_NAMES = 3

/**
 * Baris milik pemanggil saja. Cache `leads` BISA berisi baris akun sebelumnya di HP
 * bersama: `AuthRepository.logout()` memang memanggil `clearAllTables()`, tapi jalur
 * pencabutan token di `NetworkModule` (refresh hilang/ditolak) cuma memanggil
 * `tokenStore.clear()` dan meninggalkan Room utuh — dan itu jalur berakhirnya sesi
 * yang NORMAL di app ini, karena sesi per-device: login di HP lain mencabut refresh
 * token yang ini. Tanpa saringan ini, penjaga `lastSync` lolos dan nama konsumen
 * akun sebelumnya terbaca di lock screen pemakai baru.
 *
 * Prospek yang dibuat OFFLINE (`pendingSync`) tetap ikut diingatkan lewat cabang
 * `assignedTo == myId` biasa: `CreateLeadUseCase.kt:61` mengisi `assignedTo` draft
 * dengan id PEMBUAT sendiri kalau form tak memilih assignee lain — itu kasus normal
 * "buat untuk diri sendiri" — dan `CrmRepository.createLead` (baris 361) menyalin
 * `draft.assignedTo` itu apa adanya ke baris lokal. Tak ada biaya yang ditanggung di
 * sini; benar bahwa `createLead` tak menulis `createdBy`, tapi itu tak masalah karena
 * `assignedTo`-nya sudah terisi.
 *
 * Baris yang benar-benar TANPA pemilik di kedua kolom (`assignedTo` maupun
 * `createdBy` null) dibuang sebagai default DEFENSIF, bukan tradeoff yang pernah
 * disetujui siapa pun: baris tak-teratribusi tak boleh dibacakan ke siapa pun yang
 * kebetulan sedang login. Kasus ini tak tercapai dari alur pembuatan mana pun yang
 * ada sekarang — pagar untuk masa depan/data cacat, bukan celah yang sedang dipakai.
 */
internal fun milikSaya(leads: List<LeadEntity>, myId: String): List<LeadEntity> =
    leads.filter { it.assignedTo == myId || it.createdBy == myId }

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
 * `updatedAt` → epoch millis, ditafsirkan MENURUT BENTUKNYA karena di app ini ada DUA
 * penulis dengan dua konvensi berbeda:
 *
 * 1. **Ada penanda zona** (`...Z` atau `+07:00`) → memang UTC/ber-offset → [parseIsoUtcMillis].
 * 2. **Tanpa penanda zona** (`"2026-07-30 13:41:08"`) → **jam dinding LOKAL**, bukan UTC.
 *    Dua-duanya penulis yang bentuknya ini menulis jam lokal:
 *    - crm-service `domain.rs::now_string()` memakai `chrono::Local::now()` — BUKAN
 *      `Utc::now()` seperti service lain — dan VPS-nya ber-TZ Asia/Jakarta, jadi
 *      `crm_leads.updated_at` berisi WIB lalu dikirim apa adanya oleh `fmt_dt`.
 *    - `CrmRepository.nowTimestamp()` di app ini: `SimpleDateFormat` TANPA `timeZone`
 *      di-set = jam dinding device.
 *
 * **Kenapa ini penting dan bukan sekadar rapi-rapi:** sampai 2026-07-30 fungsi ini
 * membaca bentuk (2) sebagai UTC. Di WIB (UTC+7) itu menggeser nilainya 7 jam ke DEPAN,
 * sehingga `now - updated` jadi NEGATIF dan `staleProspek` tak pernah menganggapnya
 * mandek — pengingatnya bisu total untuk baris yang disentuh dalam 7 jam terakhir, dan
 * untuk baris `keepLocal` yang belum tersinkron: bisu selamanya. Terbukti di HP produksi:
 * `raw='2026-07-30 13:41:08'` pada pukul 14:50 WIB menghasilkan `umurMs=-21027204`.
 * Catatan lama di sini yang menyebut arahnya "tak berbahaya, membetulkan diri sendiri"
 * SALAH — under-report dan negatif bukan hal yang sama.
 *
 * Konsekuensi yang diterima: kalau TZ server dan TZ device berbeda, tafsir bentuk (2)
 * ikut melenceng sebesar selisihnya. Seluruh armada dan VPS ber-WIB, dan hasil terburuknya
 * (umur bergeser beberapa jam) jauh lebih ringan daripada umur negatif = fitur mati.
 * JANGAN "membetulkan" ini dengan mengubah `nowTimestamp()` atau `now_string()` — keduanya
 * kode bersama; nilai LAMA di DB tetap WIB, jadi mengubah penulisnya justru membuat data
 * campur dua konvensi.
 */
private fun updatedAtMillis(updatedAt: String): Long? {
    val raw = updatedAt.trim().takeIf { it.isNotEmpty() } ?: return null
    val iso = raw.replace(' ', 'T')
    return if (berzona(raw)) parseIsoUtcMillis(iso) else parseLocalWallClockMillis(iso)
}

/** `Z` di ujung atau offset `±HH:MM`/`±HHMM` setelah bagian jam = penanda zona eksplisit. */
private fun berzona(raw: String): Boolean =
    raw.endsWith("Z", ignoreCase = true) || Regex("""\d[+-]\d{2}:?\d{2}$""").containsMatchIn(raw)

/**
 * Kembaran [parseIsoUtcMillis] untuk bentuk tanpa penanda zona: `SimpleDateFormat` TANPA
 * `timeZone` di-set, jadi ia memakai zona device — persis kebalikan cara nilainya ditulis.
 * Sengaja `SimpleDateFormat`, bukan `java.time`: modul ini `minSdk 24` tanpa
 * `coreLibraryDesugaring`, dan `java.time` di sana melempar `NoClassDefFoundError`.
 */
private fun parseLocalWallClockMillis(iso: String): Long? {
    if (iso.length < 19) return null
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .parse(iso.substring(0, 19))?.time
    }.getOrNull()
}

private fun ageLabel(updatedAt: String, nowMillis: Long): String {
    val updated = updatedAtMillis(updatedAt) ?: return "lama"
    return "${(nowMillis - updated) / TimeUnit.DAYS.toMillis(1)} hari"
}

/** Jam dinding device saat pengingat dikirim. */
private const val REMINDER_HOUR = 9

/**
 * Nama unik pekerjaan periodik — dipakai `enqueueUniquePeriodicWork`.
 *
 * [ExistingPeriodicWorkPolicy.KEEP] menolak mengganti permintaan yang sudah terlanjur
 * dienqueue dengan nama ini, jadi target jam 09:00 ([REMINDER_HOUR]) BEKU di `WorkSpec`
 * sejak enqueue pertama: mengubah `REMINDER_HOUR` di APK berikutnya TIDAK berefek sama
 * sekali di device yang sudah pernah menjadwalkannya — perlu menaikkan nama unik ini
 * (mis. `prospek_stale_reminder_v2`) atau `UPDATE` sengaja sekali-jalan. Beda dengan
 * [STALE_THRESHOLD_MILLIS], yang dibaca ULANG di dalam worker tiap kali jalan sehingga
 * BISA diubah cukup dengan merilis APK baru.
 */
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
 * (jangan bebani server). Cache yang lebih tua dari [MAX_CACHE_AGE_MILLIS] tidak dipakai
 * sama sekali (lihat guard di [doWork]) — jadi ini BUKAN "notifikasi berdasarkan data basi
 * selamanya", cuma diam sampai sinkron berikutnya.
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
        if (!deps.tokenStore().isLoggedIn) return@withContext bisu("belum login")
        // Cache bisa berisi baris akun sebelumnya di HP bersama (lihat milikSaya) — tanpa id,
        // tak ada yang bisa disaring dengan aman, jadi diam saja.
        val myId = deps.tokenStore().userId ?: return@withContext bisu("userId kosong")
        // SATU `now` dipakai untuk staleProspek MAUPUN postReminder di bawah: ageLabel/
        // reminderBody menghitung umur relatif terhadap `now` yang sama persis dengan yang
        // dipakai staleProspek memutuskan "mandek" — kalau dua nilai `now` berbeda dipakai,
        // fallback "lama" di ageLabel dan label "0 hari" jadi bisa benar-benar muncul.
        val now = System.currentTimeMillis()
        // Cache belum pernah disinkron sama sekali (tabelnya kosong/masih sisa akun
        // sebelumnya) ATAU sudah lebih tua dari MAX_CACHE_AGE_MILLIS (orang berhenti memakai
        // CRM berhari-hari, lihat doc konstantanya) — dua alasan beda, sama-sama berarti diam
        // lebih baik daripada mengingatkan berdasarkan data yang basi/bukan miliknya.
        val lastSync = deps.syncMetaDao().get(SyncMetaEntity.KEY_LEADS)?.lastSyncMillis ?: 0L
        if (lastSync == 0L) return@withContext bisu("cache belum pernah disinkron")
        if (now - lastSync >= MAX_CACHE_AGE_MILLIS) {
            return@withContext bisu("cache basi ${(now - lastSync) / 3_600_000} jam")
        }

        val semua = deps.leadDao().all()
        val milik = milikSaya(semua, myId)
        val stale = staleProspek(milik, now)
        // Nol prospek mandek → tidak posting apa pun (bukan notifikasi "0 prospek").
        if (stale.isEmpty()) {
            return@withContext bisu("nol mandek (cache=${semua.size} milik=${milik.size})")
        }
        postReminder(applicationContext, stale, now)
        Log.i(TAG, "terkirim: ${stale.size} prospek mandek (cache=${semua.size} milik=${milik.size})")
        Result.success()
    }

    /**
     * Satu-satunya jalan keluar "tak jadi mengirim", DENGAN alasannya di log.
     *
     * Tanpa ini fitur ini tak bisa diamati sama sekali: tiga penjaga senyap + fail-soft
     * membuat "tak terjadi apa-apa" terlihat IDENTIK dengan "semuanya jalan, memang tak ada
     * yang perlu dilaporkan" — dan keduanya sama-sama `Result.success()` di logcat. Persoalan
     * nyata saat verifikasi di HP 2026-07-30: worker SUCCESS berulang kali tanpa notifikasi
     * dan tak ada cara menebak penjaga mana yang berbunyi. Pola sama `notify_user_on` di
     * kinerja-service, yang menulis INFO saat user tak punya device token justru karena push
     * yang tak pernah terkirim terlihat sama dengan yang sukses.
     *
     * Sengaja HANYA jumlah, bukan nama konsumen — logcat terbaca app lain di device lama.
     */
    private fun bisu(alasan: String): Result {
        Log.i(TAG, "tak mengirim: $alasan")
        return Result.success()
    }

    private companion object {
        const val TAG = "ProspekReminder"
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
