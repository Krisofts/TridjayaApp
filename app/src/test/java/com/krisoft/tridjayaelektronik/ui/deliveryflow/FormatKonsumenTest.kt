package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatKonsumenTest {

    // ── nama ─────────────────────────────────────────────────────────────────

    @Test
    fun `huruf besar semua dan kecil semua sama-sama jadi Title Case`() {
        assertEquals("Krisna Suwandi", rapikanNama("KRISNA SUWANDI"))
        assertEquals("Krisna Suwandi", rapikanNama("krisna suwandi"))
        assertEquals("Krisna Suwandi", rapikanNama("kRiSnA sUwAnDi"))
    }

    @Test
    fun `spasi ganda dan spasi ujung dirapikan`() {
        assertEquals("Krisna Suwandi", rapikanNama("  krisna   suwandi  "))
    }

    /** Tanda hubung memang menyambung dua kata utuh, jadi ia pemisah. */
    @Test
    fun `nama bertanda hubung dikapitalkan di kedua kata`() {
        assertEquals("Abdul-Rahman", rapikanNama("ABDUL-RAHMAN"))
    }

    /**
     * Apostrof BUKAN pemisah kata — nama Indonesia menulisnya di tengah kata.
     * Aturan "kapital setelah setiap bukan-huruf" menghasilkan "Nur'Aini",
     * dan itu salah untuk mayoritas nama yang benar-benar diketik di sini.
     * Konsekuensi yang diterima: "D'souza", bukan "D'Souza".
     */
    @Test
    fun `apostrof di tengah nama tidak membuka kata baru`() {
        assertEquals("Siti Nur'aini", rapikanNama("siti nur'aini"))
        assertEquals("Ma'ruf Amin", rapikanNama("MA'RUF AMIN"))
        assertEquals("D'souza", rapikanNama("d'souza"))
    }

    /** Titik & angka juga bukan pemisah — hanya spasi sesudahnya yang memisah. */
    @Test
    fun `titik dan angka tak membuka kata baru`() {
        assertEquals("Pt. Maju Jaya", rapikanNama("PT. MAJU JAYA"))
        assertEquals("Rumah 2 Lantai", rapikanNama("rumah 2 lantai"))
    }

    @Test
    fun `kosong tetap kosong`() {
        assertEquals("", rapikanNama(""))
        assertEquals("", rapikanNama("   "))
    }

    // ── nomor HP ─────────────────────────────────────────────────────────────

    @Test
    fun `semua bentuk umum jadi format 62`() {
        val harapan = "6285172083358"
        assertEquals(harapan, rapikanNomorHp("085172083358"))
        assertEquals(harapan, rapikanNomorHp("85172083358"))
        assertEquals(harapan, rapikanNomorHp("6285172083358"))
        assertEquals(harapan, rapikanNomorHp("+6285172083358"))
        assertEquals(harapan, rapikanNomorHp("+62 851-7208-3358"))
        assertEquals(harapan, rapikanNomorHp("0851 7208 3358"))
        assertEquals(harapan, rapikanNomorHp("(0851) 7208-3358"))
        assertEquals(harapan, rapikanNomorHp("006285172083358"))
    }

    @Test
    fun `kosong tetap kosong bukan jadi 62`() {
        assertEquals("", rapikanNomorHp(""))
        assertEquals("", rapikanNomorHp("   "))
        assertEquals("", rapikanNomorHp("-"))
    }

    /** "62" telanjang bukan nomor, itu kode negara — jangan diloloskan sebagai
     *  isian yang terisi, kalau tidak validasi "No. HP wajib" tertipu. */
    @Test
    fun `masukan nol semua tidak menghasilkan 62 telanjang`() {
        assertEquals("", rapikanNomorHp("0"))
        assertEquals("", rapikanNomorHp("000"))
    }

    /** Idempoten: menyimpan ulang nomor yang sudah rapi tak boleh mengubahnya
     *  (mis. jadi `6262…`). */
    @Test
    fun `dijalankan dua kali hasilnya sama`() {
        val sekali = rapikanNomorHp("0851 7208 3358")
        assertEquals(sekali, rapikanNomorHp(sekali))
        val nama = rapikanNama("  KRISNA   suwandi ")
        assertEquals(nama, rapikanNama(nama))
    }
}
