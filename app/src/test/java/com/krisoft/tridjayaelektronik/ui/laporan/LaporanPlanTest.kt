package com.krisoft.tridjayaelektronik.ui.laporan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan laporan verifikator.
 *
 * Yang dijaga di sini terutama SATU hal: laporan ini beredar sebagai berkas
 * terpisah, jadi setiap pemotongan data WAJIB terbaca di dalamnya. Laporan yang
 * diam-diam terpotong membuat pembacanya menyimpulkan periode itu memang sepi —
 * kegagalan yang tak pernah menghasilkan galat.
 */
class LaporanPlanTest {

    // ── tanggal VERTEL (satu permintaan per hari) ────────────────────────────

    @Test
    fun `satu hari menghasilkan satu tanggal`() {
        assertEquals(listOf("2026-08-23"), tanggalVertel("2026-08-23", "2026-08-23"))
    }

    /** Terbaru dulu: kalau batasnya memotong, yang tersisa hari yang paling
     *  mungkin masih dikerjakan. */
    @Test
    fun `urutannya terbaru lebih dulu`() {
        assertEquals(
            listOf("2026-08-23", "2026-08-22", "2026-08-21"),
            tanggalVertel("2026-08-21", "2026-08-23"),
        )
    }

    /** Preset "Bulanan" (30 hari) WAJIB muat utuh — batas yang memotong preset
     *  yang ditawarkan sendiri adalah jebakan, bukan pengaman. */
    @Test
    fun `rentang 30 hari muat utuh tanpa terpotong`() {
        val hari = tanggalVertel("2026-07-25", "2026-08-23")
        assertEquals(30, hari.size)
        assertEquals("2026-07-25", hari.last())
        assertFalse(vertelTerpotong("2026-07-25", "2026-08-23"))
    }

    @Test
    fun `rentang lebih panjang dari batas dipotong dan ditandai`() {
        val hari = tanggalVertel("2026-01-01", "2026-08-23")
        assertEquals(MAKS_HARI_VERTEL, hari.size)
        assertTrue(vertelTerpotong("2026-01-01", "2026-08-23"))
    }

    /**
     * Preset "Semua" tak punya titik awal. Ia diperlakukan sebagai N hari
     * terakhir — BUKAN "tarik semuanya", yang tak akan pernah selesai karena
     * tiap hari adalah satu permintaan HTTP.
     */
    @Test
    fun `tanpa batas awal dibatasi ke N hari terakhir`() {
        val hari = tanggalVertel(null, "2026-08-23")
        assertEquals(MAKS_HARI_VERTEL, hari.size)
        assertTrue(vertelTerpotong(null, "2026-08-23"))
    }

    // ── penyaringan tanggal di klien ─────────────────────────────────────────

    @Test
    fun `stempel di dalam rentang lolos`() {
        assertTrue(dalamRentang("2026-08-22", "2026-08-21", "2026-08-23"))
        assertTrue(dalamRentang("2026-08-21", "2026-08-21", "2026-08-23"))
        assertTrue(dalamRentang("2026-08-23", "2026-08-21", "2026-08-23"))
    }

    @Test
    fun `stempel di luar rentang dibuang`() {
        assertFalse(dalamRentang("2026-08-20", "2026-08-21", "2026-08-23"))
        assertFalse(dalamRentang("2026-08-24", "2026-08-21", "2026-08-23"))
    }

    /** Stempel server berbentuk ISO; hanya sepuluh karakter pertama dipakai. */
    @Test
    fun `stempel berjam tetap dibandingkan per hari`() {
        assertTrue(dalamRentang("2026-08-23T17:46:00", "2026-08-23", "2026-08-23"))
        assertFalse(dalamRentang("2026-08-24T00:01:00", "2026-08-23", "2026-08-23"))
    }

    /**
     * Baris tanpa stempel DIPERTAHANKAN. Membuangnya berarti menghilangkan
     * pekerjaan sungguhan gara-gara satu kolom yang tak terisi.
     */
    @Test
    fun `stempel kosong tidak membuang barisnya`() {
        assertTrue(dalamRentang(null, "2026-08-21", "2026-08-23"))
        assertTrue(dalamRentang("", "2026-08-21", "2026-08-23"))
        assertTrue(dalamRentang("bukan tanggal", "2026-08-21", "2026-08-23"))
    }

    @Test
    fun `batas terbuka hanya menyaring sisi yang ada`() {
        assertTrue(dalamRentang("2020-01-01", null, "2026-08-23"))
        assertTrue(dalamRentang("2030-01-01", "2026-08-21", null))
        assertTrue(dalamRentang("2020-01-01", null, null))
    }

    // ── batas server pemasangan AC ───────────────────────────────────────────

    @Test
    fun `jumlah menyentuh batas server ditandai mungkin terpotong`() {
        assertTrue(acMungkinTerpotong(AC_BATAS_SERVER))
        assertTrue(acMungkinTerpotong(AC_BATAS_SERVER + 5))
        assertFalse(acMungkinTerpotong(AC_BATAS_SERVER - 1))
        assertFalse(acMungkinTerpotong(0))
    }

    // ── kalimat cakupan: pemotongan harus terbaca ────────────────────────────

    @Test
    fun `cakupan menyebut rentang dan jumlah baris`() {
        val t = kalimatCakupan(SumberLaporan.VERTEL, "2026-08-21", "2026-08-23", 12, terpotong = false)
        assertTrue(t.contains("2026-08-21 s/d 2026-08-23"))
        assertTrue(t.contains("12 baris"))
        assertFalse(t.contains("PERHATIAN"))
    }

    @Test
    fun `satu hari ditulis sebagai tanggal tunggal`() {
        val t = kalimatCakupan(SumberLaporan.HOME_SERVICE, "2026-08-23", "2026-08-23", 3, terpotong = false)
        assertTrue(t.contains("tanggal 2026-08-23"))
    }

    /**
     * Sebab pemotongan harus BENAR per sumber. Kalimat generik tak bisa
     * ditindaklanjuti; kalimat yang salah sebab lebih buruk lagi — ia mengirim
     * orang memeriksa hal yang keliru.
     */
    @Test
    fun `sebab pemotongan berbeda per sumber`() {
        val v = kalimatCakupan(SumberLaporan.VERTEL, null, "2026-08-23", 1, terpotong = true)
        val a = kalimatCakupan(SumberLaporan.PEMASANGAN_AC, null, "2026-08-23", 1, terpotong = true)
        val h = kalimatCakupan(SumberLaporan.HOME_SERVICE, null, "2026-08-23", 1, terpotong = true)

        assertTrue(v.contains("satu hari per permintaan"))
        assertTrue(a.contains("$AC_BATAS_SERVER pengajuan terbaru"))
        assertTrue(h.contains("$MAKS_HALAMAN_HS halaman"))
        // Ketiganya wajib menegaskan bahwa yang hilang bukan berarti kosong.
        listOf(v, a, h).forEach { assertTrue(it.contains("BUKAN berarti kosong")) }
    }

    // ── nama berkas ──────────────────────────────────────────────────────────

    @Test
    fun `nama berkas membawa rentangnya dan tanpa spasi`() {
        assertEquals("Laporan_Verifikator_2026-08-23", namaBerkasLaporan("2026-08-23", "2026-08-23"))
        assertEquals("Laporan_Verifikator_2026-08-21_sd_2026-08-23", namaBerkasLaporan("2026-08-21", "2026-08-23"))
        assertEquals("Laporan_Verifikator_semua", namaBerkasLaporan(null, null))
        assertFalse(namaBerkasLaporan("2026-08-21", "2026-08-23").contains(" "))
    }

    // ── lingkup: PDI sengaja tidak ada ───────────────────────────────────────

    /**
     * Dikunci sebagai TEST, bukan cuma komentar: penerus yang melihat empat
     * laporan disebut di permintaan asli akan menganggap absennya PDI sebagai
     * kelalaian. Server membatasi bacaan akun verifikator ke SPK buatannya
     * sendiri, jadi sheet PDI akan selalu kosong dan kosongnya tak bisa
     * dibedakan dari "memang tidak ada".
     */
    @Test
    fun `lingkup laporan tepat tiga sumber dan tak memuat PDI`() {
        assertEquals(3, SumberLaporan.entries.size)
        assertEquals(
            listOf("VERTEL", "Home Service", "Pemasangan AC"),
            SumberLaporan.entries.map { it.judulSheet },
        )
        assertFalse(SumberLaporan.entries.any { it.judulSheet.contains("PDI", ignoreCase = true) })
    }

    // ── kemajuan ─────────────────────────────────────────────────────────────

    @Test
    fun `persen kemajuan aman saat total nol`() {
        assertEquals(0f, KemajuanLaporan(0, 0, "").persen, 0.0001f)
        assertEquals(0.5f, KemajuanLaporan(1, 2, "").persen, 0.0001f)
    }
}
