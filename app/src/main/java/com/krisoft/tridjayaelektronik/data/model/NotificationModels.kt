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
 * Label relatif Indonesia dari `createdAt` (chrono `DateTime<Utc>` RFC3339, mis.
 * `"2026-07-23T10:15:30.123Z"`). minSdk 24 → `java.time` belum tersedia tanpa desugaring (pola sama
 * `DeliveryFlowScreens` — lihat komentar "butuh API 26"), jadi ambil 19 karakter pertama
 * (`yyyy-MM-ddTHH:mm:ss`, abaikan fraksi detik/offset — presisi detik cukup utk label relatif) lalu
 * parse dengan [SimpleDateFormat].
 */
fun relativeTimeId(iso: String?, nowMillis: Long = System.currentTimeMillis()): String {
    val millis = parseIsoUtcMillis(iso) ?: return "-"
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

private fun parseIsoUtcMillis(iso: String?): Long? {
    if (iso == null || iso.length < 19) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(iso.substring(0, 19))?.time
    }.getOrNull()
}
