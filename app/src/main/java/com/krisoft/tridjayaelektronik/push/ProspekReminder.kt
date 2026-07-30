package com.krisoft.tridjayaelektronik.push

import com.krisoft.tridjayaelektronik.data.local.LeadEntity
import com.krisoft.tridjayaelektronik.data.model.parseIsoUtcMillis
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
