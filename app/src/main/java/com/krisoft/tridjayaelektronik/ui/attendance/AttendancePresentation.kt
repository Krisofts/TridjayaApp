package com.krisoft.tridjayaelektronik.ui.attendance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import com.krisoft.tridjayaelektronik.data.model.OffRequestDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

/** Kategori izin/OFF — samakan dgn web `OFF_KATEGORI_LABEL` (izin|sakit|cuti|off). */
enum class OffKategori(val key: String, val label: String, val color: Color) {
    IZIN("izin", "Izin", Color(0xFF1565C0)),
    SAKIT("sakit", "Sakit", Color(0xFFB5670C)),
    CUTI("cuti", "Cuti", Color(0xFF6941C6)),
    OFF("off", "Off", Color(0xFF667085));

    companion object {
        /** Fallback ke IZIN untuk nilai tak dikenal — samakan dgn web (`... : 'izin'`). */
        fun from(key: String?): OffKategori =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: IZIN
    }
}

/** Status harian rekap — sepadan dgn `RekapStatus` di web `AbsensiPage`. */
enum class RekapStatus(val label: String, val color: Color) {
    HADIR("Hadir", Color(0xFF12B76A)),
    IZIN("Izin", Color(0xFF1565C0)),
    SAKIT("Sakit", Color(0xFFB5670C)),
    CUTI("Cuti", Color(0xFF6941C6)),
    OFF("Off", Color(0xFF667085)),
    BELUM_ABSEN("Belum Absen", Color(0xFFF04438))
}

private fun offToRekap(kategori: String): RekapStatus = when (OffKategori.from(kategori)) {
    OffKategori.IZIN -> RekapStatus.IZIN
    OffKategori.SAKIT -> RekapStatus.SAKIT
    OffKategori.CUTI -> RekapStatus.CUTI
    OffKategori.OFF -> RekapStatus.OFF
}

/** "yyyy-MM-dd" LOKAL hari ini (tz device = tz server Indonesia WIB/WITA). */
fun attendanceToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/**
 * Tanggal 1 bulan berjalan s/d hari ini ("yyyy-MM-dd", lokal). Sengaja komponen
 * tanggal lokal (bukan UTC) supaya cocok dgn `tanggal` record server — sama dgn
 * perbaikan geser-UTC di web `dateRangeKeys`.
 */
fun currentMonthDays(): List<String> {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return (1..today).map { d ->
        fmt.format(Calendar.getInstance().apply { clear(); set(year, month, d) }.time)
    }
}

/**
 * Rekap kehadiran bulan berjalan (tgl 1 s/d hari ini) digabung izin/OFF — mengikuti
 * logika `RekapKehadiranTab` web: tiap hari = HADIR bila ada check-in, else kategori
 * off disetujui, else BELUM_ABSEN. (Seperti web, hari libur tanpa absen/izin ikut
 * terhitung "belum absen" karena tak ada jadwal kerja per-hari.)
 */
data class AttendanceRekap(
    val counts: Map<RekapStatus, Int> = RekapStatus.entries.associateWith { 0 },
    val totalHari: Int = 0
) {
    fun count(status: RekapStatus): Int = counts[status] ?: 0
}

fun buildRekap(
    history: List<AbsensiRecordDto>,
    offRequests: List<OffRequestDto>,
    days: List<String> = currentMonthDays()
): AttendanceRekap {
    val attByDate = history.associateBy { it.tanggal }
    val offByDate = offRequests
        .filter { it.status.equals("approved", ignoreCase = true) }
        .associateBy { it.tanggal }
    val counts = RekapStatus.entries.associateWith { 0 }.toMutableMap()
    for (day in days) {
        val status = when {
            attByDate[day]?.checkInAt != null -> RekapStatus.HADIR
            offByDate[day] != null -> offToRekap(offByDate.getValue(day).kategori)
            else -> RekapStatus.BELUM_ABSEN
        }
        counts[status] = (counts[status] ?: 0) + 1
    }
    return AttendanceRekap(counts, days.size)
}

/**
 * Entri riwayat gabungan: absensi ATAU hari izin/OFF disetujui (yang belum punya
 * record absensi). Membuat riwayat mobile setara "Detail Kehadiran" web yang
 * menampilkan izin/sakit/cuti/off, bukan cuma hari check-in.
 */
sealed interface TimelineEntry {
    val tanggal: String

    data class Attendance(val record: AbsensiRecordDto) : TimelineEntry {
        override val tanggal: String get() = record.tanggal
    }

    data class Off(val off: OffRequestDto) : TimelineEntry {
        override val tanggal: String get() = off.tanggal
    }
}

fun buildTimeline(
    history: List<AbsensiRecordDto>,
    offRequests: List<OffRequestDto>
): List<TimelineEntry> {
    val attDates = history.map { it.tanggal }.toSet()
    val offEntries = offRequests
        .filter { it.status.equals("approved", ignoreCase = true) && it.tanggal !in attDates }
        .map { TimelineEntry.Off(it) }
    return (history.map { TimelineEntry.Attendance(it) } + offEntries)
        .sortedByDescending { it.tanggal }
}
