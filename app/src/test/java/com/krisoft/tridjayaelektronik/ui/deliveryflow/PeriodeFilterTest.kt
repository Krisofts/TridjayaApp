package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tanggal harapan di bawah ini SENGAJA ditulis sebagai literal, bukan dihitung
 * ulang lewat `shiftDays`/`Calendar`. Test yang memanggil rumus yang sama dengan
 * kode yang diujinya cuma membuktikan rumus itu konsisten dengan dirinya
 * sendiri — off-by-one -7 vs -6 akan lolos mulus.
 */
class PeriodeFilterTest {

    @Test
    fun `HARI_INI dari dan sampai sama-sama hari ini`() {
        val r = rentangPeriode(PeriodeSpk.HARI_INI, "2026-08-07")
        assertEquals("2026-08-07", r.dari)
        assertEquals("2026-08-07", r.sampai)
    }

    @Test
    fun `KEMARIN dari dan sampai sama-sama H-1`() {
        val r = rentangPeriode(PeriodeSpk.KEMARIN, "2026-08-07")
        assertEquals("2026-08-06", r.dari)
        assertEquals("2026-08-06", r.sampai)
    }

    @Test
    fun `MINGGUAN persis 7 hari termasuk hari ini`() {
        // 01..07 Agustus = 7 hari. Kalau ini jadi 2026-07-31, jendelanya 8 hari.
        val r = rentangPeriode(PeriodeSpk.MINGGUAN, "2026-08-07")
        assertEquals("2026-08-01", r.dari)
        assertEquals("2026-08-07", r.sampai)
    }

    @Test
    fun `BULANAN persis 30 hari termasuk hari ini`() {
        // 09..31 Juli = 23 hari, + 01..07 Agustus = 7 hari → 30.
        val r = rentangPeriode(PeriodeSpk.BULANAN, "2026-08-07")
        assertEquals("2026-07-09", r.dari)
        assertEquals("2026-08-07", r.sampai)
    }

    @Test
    fun `SEMUA tanpa batas tanggal di kedua sisi`() {
        val r = rentangPeriode(PeriodeSpk.SEMUA, "2026-08-07")
        assertNull(r.dari)
        assertNull(r.sampai)
    }

    @Test
    fun `KEMARIN menyeberang batas bulan - 2026 bukan kabisat`() {
        val r = rentangPeriode(PeriodeSpk.KEMARIN, "2026-03-01")
        assertEquals("2026-02-28", r.dari)
        assertEquals("2026-02-28", r.sampai)
    }

    @Test
    fun `KEMARIN menyeberang batas bulan - 2024 kabisat`() {
        val r = rentangPeriode(PeriodeSpk.KEMARIN, "2024-03-01")
        assertEquals("2024-02-29", r.dari)
        assertEquals("2024-02-29", r.sampai)
    }

    @Test
    fun `MINGGUAN menyeberang batas tahun`() {
        val r = rentangPeriode(PeriodeSpk.MINGGUAN, "2026-01-01")
        assertEquals("2025-12-26", r.dari)
        assertEquals("2026-01-01", r.sampai)
    }

    @Test
    fun `BULANAN menyeberang batas tahun`() {
        // 03..31 Desember = 29 hari, + 01 Januari = 30.
        val r = rentangPeriode(PeriodeSpk.BULANAN, "2026-01-01")
        assertEquals("2025-12-03", r.dari)
        assertEquals("2026-01-01", r.sampai)
    }
}
