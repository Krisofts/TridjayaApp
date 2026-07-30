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

    /**
     * Bentuk TANPA penanda zona = jam dinding device, bukan UTC.
     *
     * Backend berhenti mengirim `Z` pada 2026-07-30 (semuanya WIB polos). Sebelum
     * perbaikan ini parser selalu menganggap UTC, sehingga notifikasi yang baru
     * masuk berlabel "7 jam lalu" di HP armada — tanpa error, tanpa crash.
     */
    @Test
    fun `tanpa penanda zona dibaca sebagai jam device`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.JULY, 30, 18, 38, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val lokalMillis = cal.timeInMillis

        assertEquals("Baru saja", relativeTimeId("2026-07-30T18:38:00", lokalMillis + 10_000))
        assertEquals("2 jam lalu", relativeTimeId("2026-07-30T18:38:00", lokalMillis + 2 * 3_600_000L))
    }

    /** Nilai LAMA ber-`Z` masih tersimpan di notifikasi device — tetap absolut. */
    @Test
    fun `nilai ber-Z tetap ditafsir UTC`() {
        assertEquals("Baru saja", relativeTimeId("2026-07-23T10:00:00Z", baseMillis))
        assertEquals("1 jam lalu", relativeTimeId("2026-07-23T10:00:00Z", baseMillis + 3_600_000L))
    }

    /**
     * Keterbatasan yang DISENGAJA: bentuk beroffset (`+07:00`) tidak didukung.
     *
     * Parser memotong string di 19 karakter, jadi offsetnya mustahil ikut
     * terbaca. Backend repo ini tak pernah mengirimnya (dulu `Z`, kini polos).
     * Test ini memakukan perilakunya supaya kalau suatu saat bentuk itu muncul,
     * yang gagal adalah test — bukan angka di layar armada.
     */
    @Test
    fun `bentuk beroffset diperlakukan seperti jam device`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.JULY, 23, 10, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        assertEquals("Baru saja", relativeTimeId("2026-07-23T10:00:00+07:00", cal.timeInMillis))
    }
}
