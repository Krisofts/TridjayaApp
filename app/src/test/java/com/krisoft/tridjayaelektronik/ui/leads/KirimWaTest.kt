package com.krisoft.tridjayaelektronik.ui.leads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nama paket WhatsApp adalah kontrak stringly-typed ke dunia luar (tak ada
 * pemeriksa kompiler): salah satu huruf saja dan pemilihnya diam-diam menganggap
 * app-nya tak terpasang, lalu jatuh ke browser tanpa satu pun pesan error.
 */
class KirimWaTest {

    @Test
    fun `paket sesuai nama resmi kedua app WhatsApp`() {
        assertEquals("com.whatsapp.w4b", WaApp.BISNIS.paket)
        assertEquals("com.whatsapp", WaApp.BIASA.paket)
    }

    @Test
    fun `keduanya terpasang menghasilkan dua pilihan, Business lebih dulu`() {
        val pilihan = pilihanWa(setOf("com.whatsapp", "com.whatsapp.w4b"))
        assertEquals(listOf(WaApp.BISNIS, WaApp.BIASA), pilihan)
    }

    @Test
    fun `hanya WhatsApp biasa terpasang menghasilkan satu pilihan`() {
        assertEquals(listOf(WaApp.BIASA), pilihanWa(setOf("com.whatsapp")))
    }

    @Test
    fun `hanya WhatsApp Business terpasang menghasilkan satu pilihan`() {
        assertEquals(listOf(WaApp.BISNIS), pilihanWa(setOf("com.whatsapp.w4b")))
    }

    /**
     * Nol pilihan BUKAN kondisi error: pemanggil jatuh ke intent umum (browser /
     * pemilih app bawaan), jadi tombolnya tetap berguna di HP tanpa WhatsApp.
     */
    @Test
    fun `tak satu pun terpasang menghasilkan daftar kosong`() {
        assertTrue(pilihanWa(emptySet()).isEmpty())
        assertTrue(pilihanWa(setOf("com.telegram", "com.whatsapp.w4b.palsu")).isEmpty())
    }

    @Test
    fun `uri chat memakai nomor internasional dan pesan ter-encode`() {
        val uri = waUri("08123456789", "Halo Kak, ada promo?")
        assertTrue(uri.startsWith("https://wa.me/628123456789?text="))
        // Spasi & tanda tanya harus ter-escape, kalau tidak pesannya terpotong di WA.
        assertTrue(uri.contains("Halo%20Kak") || uri.contains("Halo+Kak"))
        assertTrue(!uri.substringAfter("?text=").contains(" "))
    }
}
