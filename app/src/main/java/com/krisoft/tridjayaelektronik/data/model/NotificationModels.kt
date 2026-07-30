package com.krisoft.tridjayaelektronik.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable

/**
 * Notifikasi in-app (audit-service, gateway `/api/notifications`). Envelope `{message,data}` biasa
 * (pakai [ApiResponse]). Field 1:1 dgn serde backend (`audit-service/src/domain.rs::Notification`,
 * `#[serde(rename_all = "camelCase")]`, `kind` di-rename ke `type`). Kontrak: android-api.md §7.
 */
@Serializable
data class NotificationDto(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val message: String? = null,
    val actionPath: String? = null,
    val entityId: String? = null,
    val isRead: Boolean = false,
    val createdAt: String? = null,
    val readAt: String? = null
)

@Serializable
data class NotificationListData(
    val items: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0
)

@Serializable
data class UnreadCountData(val unreadCount: Int = 0)

@Serializable
data class MarkReadData(val updated: Long = 0)

/**
 * Label relatif Indonesia dari `createdAt`. Bentuknya kini WIB polos tanpa
 * penanda (`"2026-07-30T18:38:01"`); nilai lama ber-`Z` masih ada di notifikasi
 * tersimpan, dan [parseTimestampMillis] yang memilah keduanya. minSdk 24 → `java.time` belum tersedia tanpa desugaring (pola sama
 * `DeliveryFlowScreens` — lihat komentar "butuh API 26"), jadi ambil 19 karakter pertama
 * (`yyyy-MM-ddTHH:mm:ss`, abaikan fraksi detik/offset — presisi detik cukup utk label relatif) lalu
 * parse dengan [SimpleDateFormat].
 */
fun relativeTimeId(iso: String?, nowMillis: Long = System.currentTimeMillis()): String {
    val millis = parseTimestampMillis(iso) ?: return "-"
    val diff = (nowMillis - millis).coerceAtLeast(0)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "Baru saja"
        diff < hour -> "${diff / minute} menit lalu"
        diff < day -> "${diff / hour} jam lalu"
        diff < 7 * day -> "${diff / day} hari lalu"
        else -> SimpleDateFormat("d MMM yyyy", Locale("in", "ID")).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }
}

/**
 * `yyyy-MM-dd'T'HH:mm:ss…` → epoch millis, **selalu ditafsir UTC**.
 *
 * Namanya janji: fungsi ini TIDAK menebak zona. Pemilihan penafsiran ada di
 * [parseTimestampMillis]; `ProspekReminder` juga memanggilnya langsung untuk
 * nilai yang sudah dipastikan ber-zona. Menaruh logika penebak zona DI SINI
 * pernah dicoba dan diam-diam mengubah perilaku pemanggil lain.
 *
 * `internal`, bukan private: dipakai ulang di luar berkas ini. Sengaja
 * SimpleDateFormat — `java.time` butuh API 26 sementara modul ini minSdk 24
 * tanpa `coreLibraryDesugaring` (`NoClassDefFoundError` di lapangan).
 */
internal fun parseIsoUtcMillis(iso: String?): Long? {
    if (iso == null || iso.length < 19) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(iso.trim().substring(0, 19))?.time
    }.getOrNull()
}

/**
 * Kembaran [parseIsoUtcMillis] untuk nilai TANPA penanda zona: `SimpleDateFormat`
 * tanpa `timeZone` di-set, jadi memakai zona device — persis cara nilainya
 * ditulis server (jam dinding WIB).
 */
internal fun parseWallClockMillis(iso: String?): Long? {
    if (iso == null || iso.length < 19) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .parse(iso.trim().substring(0, 19))?.time
    }.getOrNull()
}

/**
 * Router: pilih penafsiran dari BENTUK nilainya, bukan dari nama field.
 *
 * Sejak 2026-07-30 backend mengirim WIB polos tanpa penanda (kontrak
 * `tridjaya_shared::waktu`). Membacanya sebagai UTC memundurkan tiap nilai 7
 * jam: notifikasi yang baru masuk berlabel "7 jam lalu", umur `deliveredAt` di
 * kartu SPK Gantung ikut meleset — tanpa error, tanpa crash. Nilai LAMA ber-`Z`
 * masih tersimpan di notifikasi device, jadi cabang UTC tetap dibutuhkan.
 */
internal fun parseTimestampMillis(iso: String?): Long? {
    val raw = iso?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return if (berpenandaZona(raw)) parseIsoUtcMillis(raw) else parseWallClockMillis(raw)
}

/**
 * `Z` di ujung atau offset `±HH:MM`/`±HHMM` sesudah bagian jam.
 *
 * Catatan jujur soal bentuk beroffset: [parseIsoUtcMillis] memotong string di 19
 * karakter sehingga offsetnya tak pernah benar-benar diterapkan — digitnya
 * dibaca sebagai UTC. Itu perilaku LAMA yang dipertahankan apa adanya (dipakai
 * `ProspekReminder`); backend repo ini memang tak pernah mengirim bentuk itu.
 */
private fun berpenandaZona(raw: String): Boolean =
    raw.endsWith("Z", ignoreCase = true) || Regex("""\d[+-]\d{2}:?\d{2}$""").containsMatchIn(raw)
