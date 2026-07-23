package com.krisoft.tridjayaelektronik.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationModelsTest {

    // 2026-07-23T10:00:00Z in epoch millis (computed via a fixed UTC calendar, not device TZ).
    private val baseMillis = run {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(2026, java.util.Calendar.JULY, 23, 10, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    private val iso = "2026-07-23T10:00:00Z"

    @Test
    fun `baru saja untuk kurang dari 1 menit`() {
        assertEquals("Baru saja", relativeTimeId(iso, baseMillis + 30_000))
    }

    @Test
    fun `menit lalu`() {
        assertEquals("5 menit lalu", relativeTimeId(iso, baseMillis + 5 * 60_000))
    }

    @Test
    fun `jam lalu`() {
        assertEquals("3 jam lalu", relativeTimeId(iso, baseMillis + 3 * 3_600_000L))
    }

    @Test
    fun `hari lalu`() {
        assertEquals("2 hari lalu", relativeTimeId(iso, baseMillis + 2 * 86_400_000L))
    }

    @Test
    fun `lebih dari seminggu jadi tanggal absolut (bukan label relatif)`() {
        // Bandingkan ke pemanggilan SimpleDateFormat yang identik dengan produksi, alih-alih
        // hardcode string nama bulan Indonesia (rentan beda output antar data locale JVM) — yang
        // dites di sini adalah alur kode (parse createdAt lalu format absolut), bukan i18n Java.
        val expected = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("in", "ID")).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(baseMillis))
        val result = relativeTimeId(iso, baseMillis + 8 * 86_400_000L)
        assertEquals(expected, result)
        assert(!result.contains("lalu")) { "harus tanggal absolut, bukan label relatif: $result" }
    }

    @Test
    fun `mengabaikan fraksi detik dan offset Z`() {
        assertEquals("Baru saja", relativeTimeId("2026-07-23T10:00:00.123456Z", baseMillis))
    }

    @Test
    fun `null atau string terlalu pendek balik dash`() {
        assertEquals("-", relativeTimeId(null))
        assertEquals("-", relativeTimeId("2026-07-23"))
    }
}
