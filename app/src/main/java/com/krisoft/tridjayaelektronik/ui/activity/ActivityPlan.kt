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
): List<DailyTask> = items
    .filter { it.kind == ActivityKind.TUGAS_HARIAN }
    // Absen pulang tak relevan sebelum check-in — menampilkannya sejak pagi
    // membuat progres harian selalu terlihat gagal.
    .filterNot { it.id == "absen_pulang" && checkInAt.isNullOrBlank() }
    .map { item ->
        when (item.id) {
            "absen_masuk" -> DailyTask(item, !checkInAt.isNullOrBlank(), jam(checkInAt) ?: "belum")
            "absen_pulang" -> DailyTask(item, !checkOutAt.isNullOrBlank(), jam(checkOutAt) ?: "belum")
            "prospek" -> DailyTask(
                item,
                leadsToday > 0,
                if (leadsToday > 0) "$leadsToday lead hari ini" else "belum ada",
            )
            else -> DailyTask(item, done = false, detail = if (item.comingSoon) "SEGERA" else "belum")
        }
    }

/** `n/total` — item `comingSoon` tak ikut jadi penyebut (belum bisa dikerjakan). */
internal fun dailyProgressLabel(tasks: List<DailyTask>): String {
    val nyata = tasks.filterNot { it.item.comingSoon }
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

/** Nilai counter SPK berikutnya; ganti hari = mulai dari 1 lagi. */
internal fun spkCounterAfterIncrement(
    storedDate: String?,
    storedCount: Int,
    todayIso: String,
): Pair<String, Int> =
    if (storedDate == todayIso) todayIso to (storedCount + 1) else todayIso to 1
