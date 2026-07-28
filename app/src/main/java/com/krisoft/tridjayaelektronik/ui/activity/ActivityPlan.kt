package com.krisoft.tridjayaelektronik.ui.activity

/**
 * Penyusun tampilan layar Activity — SENGAJA fungsi murni tanpa Android/Compose
 * supaya bisa diuji JUnit biasa (repo ini tak punya mockk / coroutines-test).
 * `ActivityViewModel` hanya melakukan IO lalu menyerahkan hasilnya ke sini.
 */
data class ActivityCard(
    val item: ActivityItem,
    /** `null` = belum/ gagal dimuat — BUKAN nol. */
    val count: Int?,
    val failed: Boolean,
)

data class DailyTask(
    val item: ActivityItem,
    val done: Boolean,
    /** Teks kanan kartu: jam absen, "2 lead hari ini", "SEGERA", "belum". */
    val detail: String,
    /**
     * true = sumbernya GAGAL dimuat (jaringan/API), BUKAN "belum dikerjakan"
     * (I3 audit 2026-07-28). Dikecualikan dari penyebut [dailyProgressLabel] —
     * kegagalan jaringan bukan salah user, jangan menghukum progresnya.
     */
    val loadFailed: Boolean = false,
)

/**
 * Kartu antrian: yang berangka besar di atas, yang nol tetap tampil (redup)
 * supaya "semua beres" terbaca dan menu tak terasa hilang. Satu-satunya yang
 * disembunyikan saat kosong adalah Tugas Antar untuk non-driver (spec §6).
 */
internal fun buildQueueCards(
    items: List<ActivityItem>,
    counts: Map<ActivitySource, Int?>,
    failed: Set<ActivitySource>,
    effectiveRoles: Set<String>,
): List<ActivityCard> = items
    .filter { it.kind == ActivityKind.ANTRIAN }
    .map { item ->
        val gagal = item.source in failed
        ActivityCard(
            item = item,
            count = if (gagal) null else counts[item.source],
            failed = gagal,
        )
    }
    .filter { card ->
        if (card.item.source != ActivitySource.DLV_AS_DRIVER) true
        else card.failed || driverCardVisible(card.count, effectiveRoles)
    }
    .sortedByDescending { it.count ?: -1 }

/**
 * Jam dari timestamp kontrak absensi (`YYYY-MM-DD HH:MM:SS`, bukan ISO "T").
 * Asumsi: backend absensi SELALU mengirim format penuh itu (posisi 11-16 tetap
 * "HH:MM") — JANGAN pakai ulang parser ini untuk timestamp dari sumber lain.
 */
private fun jam(timestamp: String?): String? =
    timestamp?.takeIf { it.length >= 16 }?.substring(11, 16)

internal fun buildDailyTasks(
    items: List<ActivityItem>,
    checkInAt: String?,
    checkOutAt: String?,
    leadsToday: Int,
    /** true = panggilan absensi hari ini gagal (jaringan/API) — [checkInAt]/
     *  [checkOutAt] di atas TIDAK bisa dipercaya (bukan benar-benar "belum"). */
    absensiFailed: Boolean = false,
): List<DailyTask> = items
    .filter { it.kind == ActivityKind.TUGAS_HARIAN }
    // Absen pulang tak relevan sebelum check-in — menampilkannya sejak pagi
    // membuat progres harian selalu terlihat gagal. Gagal-muat dikecualikan
    // dari aturan ini (di bawah): kita tak TAHU status check-in-nya, jadi
    // jangan diam-diam menyembunyikan tugas gara-gara ketidaktahuan itu.
    .filterNot { it.id == "absen_pulang" && checkInAt.isNullOrBlank() && !absensiFailed }
    .map { item ->
        when {
            absensiFailed && (item.id == "absen_masuk" || item.id == "absen_pulang") ->
                // I3 audit 2026-07-28: sebelumnya jatuh ke detail "belum" — tak
                // bisa dibedakan dari benar-benar belum absen, jadi kartu yang
                // bisa ditap MENDORONG USER YANG SUDAH CHECK-IN untuk absen
                // lagi. "gagal muat" jujur soal ketidaktahuan kita.
                DailyTask(item, done = false, detail = "gagal muat", loadFailed = true)
            item.id == "absen_masuk" -> DailyTask(item, !checkInAt.isNullOrBlank(), jam(checkInAt) ?: "belum")
            item.id == "absen_pulang" -> DailyTask(item, !checkOutAt.isNullOrBlank(), jam(checkOutAt) ?: "belum")
            item.id == "prospek" -> DailyTask(
                item,
                leadsToday > 0,
                if (leadsToday > 0) "$leadsToday lead hari ini" else "belum ada",
            )
            else -> DailyTask(item, done = false, detail = if (item.comingSoon) "SEGERA" else "belum")
        }
    }

/**
 * `n/total` — item `comingSoon` (belum bisa dikerjakan) DAN [DailyTask.loadFailed]
 * (bukan salah user, kegagalan jaringan) tak ikut jadi penyebut.
 */
internal fun dailyProgressLabel(tasks: List<DailyTask>): String {
    val nyata = tasks.filterNot { it.item.comingSoon || it.loadFailed }
    return "${nyata.count { it.done }}/${nyata.size}"
}

/**
 * Lead yang dibuat hari ini oleh [userId]. Baris cache lama tanpa `createdBy`
 * ikut dihitung — untuk `karyawan` cache-nya memang sudah di-scope server
 * (`karyawan_scope`), jadi lebih baik menghitung daripada diam-diam nol.
 *
 * [leads] = pasangan (`createdAt`, `createdBy`).
 */
internal fun leadsCreatedTodayBy(
    leads: List<Pair<String, String?>>,
    userId: String?,
    todayIso: String,
): Int = leads.count { (createdAt, createdBy) ->
    createdAt.startsWith(todayIso) && (userId == null || createdBy == null || createdBy == userId)
}
