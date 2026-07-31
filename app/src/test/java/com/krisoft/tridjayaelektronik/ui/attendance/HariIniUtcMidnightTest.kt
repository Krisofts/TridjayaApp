package com.krisoft.tridjayaelektronik.ui.attendance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Nilai awal `DatePickerState` di form Ajukan Izin.
 *
 * Bug yang dijaga: `System.currentTimeMillis()` mentah dinormalkan Material3 ke
 * tanggal UTC-nya, jadi antara 00:00–06:59 WIB form terbuka — dan mengirim —
 * tanggal KEMARIN. Sejak server menolak tanggal mundur (2026-07-31) itu berarti
 * 400 tepat pada kasus yang aturannya justru ingin biarkan: sakit mendadak pagi
 * hari. Jam-jam di bawah dipilih mengelilingi batas itu.
 */
class HariIniUtcMidnightTest {

    private val wib = TimeZone.getTimeZone("Asia/Jakarta")

    /** Sama seperti `OffFormSheet`: format hasilnya dengan formatter ber-zona UTC. */
    private fun tanggalTerkirim(jam: Int, menit: Int): String {
        val cal = Calendar.getInstance(wib).apply {
            clear()
            set(2026, Calendar.JULY, 31, jam, menit, 0)
        }
        val millis = hariIniSebagaiUtcMidnight(cal.timeInMillis, wib)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
    }

    @Test
    fun `dini hari WIB tetap tanggal hari ini`() {
        assertEquals("2026-07-31", tanggalTerkirim(0, 5))
        assertEquals("2026-07-31", tanggalTerkirim(6, 30))
    }

    @Test
    fun `siang dan malam WIB juga tanggal hari ini`() {
        assertEquals("2026-07-31", tanggalTerkirim(7, 0))
        assertEquals("2026-07-31", tanggalTerkirim(12, 0))
        assertEquals("2026-07-31", tanggalTerkirim(23, 59))
    }

    /** WITA (UTC+8) — cabang Manado; batas geser tapi hasilnya tetap hari itu. */
    @Test
    fun `zona WITA ikut benar`() {
        val wita = TimeZone.getTimeZone("Asia/Makassar")
        val cal = Calendar.getInstance(wita).apply {
            clear()
            set(2026, Calendar.JULY, 31, 1, 0, 0)
        }
        val millis = hariIniSebagaiUtcMidnight(cal.timeInMillis, wita)
        val hasil = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
        assertEquals("2026-07-31", hasil)
    }
}
