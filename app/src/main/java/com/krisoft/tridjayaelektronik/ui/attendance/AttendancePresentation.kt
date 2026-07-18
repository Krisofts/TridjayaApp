package com.krisoft.tridjayaelektronik.ui.attendance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import java.text.SimpleDateFormat
import java.util.Locale

/** Status review absensi dari backend (`valid` | `pending_review` | `approved` | `rejected`). */
enum class AbsensiStatus(
    val key: String,
    val label: String,
    val color: Color,
    val icon: ImageVector
) {
    VALID("valid", "Tercatat", Color(0xFF12B76A), Icons.Rounded.CheckCircle),
    PENDING_REVIEW("pending_review", "Perlu Review", Color(0xFFB5670C), Icons.Rounded.HourglassTop),
    APPROVED("approved", "Disetujui", Color(0xFF12B76A), Icons.Rounded.Verified),
    REJECTED("rejected", "Ditolak", Color(0xFFF04438), Icons.Rounded.Cancel);

    companion object {
        fun from(key: String?): AbsensiStatus =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: VALID
    }
}

/** Format jarak singkat: "18 m" atau "1,2 km". */
fun formatDistance(meters: Long): String =
    if (meters < 1000) "$meters m"
    else String.format(Locale("in", "ID"), "%.1f km", meters / 1000.0)

private val dayFormatter = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("in", "ID"))
private val shortDayFormatter = SimpleDateFormat("EEE, d MMM", Locale("in", "ID"))
private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dbDateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

/** "yyyy-MM-dd" → "Sabtu, 18 Juli 2026" (fallback: string asli). */
fun formatAttendanceDate(iso: String): String =
    runCatching { dayFormatter.format(isoDateFormatter.parse(iso)!!) }.getOrDefault(iso)

/** "yyyy-MM-dd" → "Sab, 18 Jul". */
fun formatAttendanceDateShort(iso: String): String =
    runCatching { shortDayFormatter.format(isoDateFormatter.parse(iso)!!) }.getOrDefault(iso)

/** "yyyy-MM-dd HH:mm:ss" → "HH:mm" (fallback: potong 11..16, atau string asli). */
fun formatPunchTime(datetime: String?): String {
    if (datetime.isNullOrBlank()) return "-"
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.US).format(dbDateTimeFormatter.parse(datetime)!!)
    }.getOrElse {
        if (datetime.length >= 16) datetime.substring(11, 16) else datetime
    }
}

/** Rekap dihitung dari riwayat (backend tak menyediakan endpoint rekap khusus). */
data class AttendanceRekap(
    val hadir: Int = 0,
    val telat: Int = 0,
    val review: Int = 0,
    val ditolak: Int = 0
)

fun buildRekap(records: List<AbsensiRecordDto>): AttendanceRekap {
    var hadir = 0; var telat = 0; var review = 0; var ditolak = 0
    records.forEach { r ->
        if (r.checkInAt != null) hadir++
        if (r.checkInLate) telat++
        when (AbsensiStatus.from(r.status)) {
            AbsensiStatus.PENDING_REVIEW -> review++
            AbsensiStatus.REJECTED -> ditolak++
            else -> Unit
        }
    }
    return AttendanceRekap(hadir, telat, review, ditolak)
}
