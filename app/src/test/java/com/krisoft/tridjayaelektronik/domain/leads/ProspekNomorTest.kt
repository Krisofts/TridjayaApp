package com.krisoft.tridjayaelektronik.domain.leads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan nomor prospek di app HARUS sejajar `is_plausible_whatsapp`
 * (kinerja-service `prospek.rs`, `(7..=15).contains(&digit_count)`).
 *
 * Form dulu tak punya batas ATAS sama sekali. Akibatnya nomor 16+ angka lolos
 * di HP, tersimpan ke Room, ditandai "ANTRE", lalu ditolak server selamanya —
 * 390 kali dalam tujuh hari di produksi, tanpa satu pun tanda bagi penginputnya.
 */
class ProspekNomorTest {

    @Test
    fun `normalisasi sama dengan form web`() {
        assertEquals("081234567890", normalisasiNomorProspek("0812-3456-7890"))
        assertEquals("081234567890", normalisasiNomorProspek("+62 812 3456 7890"))
        assertEquals("081234567890", normalisasiNomorProspek("81234567890"))
        assertEquals("", normalisasiNomorProspek("tidak ada angka"))
    }

    @Test
    fun `nomor Indonesia wajar diterima`() {
        assertNull(masalahNomorProspek("081234567890"))
        // Batas atas persis: 15 angka masih diterima server.
        assertNull(masalahNomorProspek("081234567890123"))
    }

    @Test
    fun `nomor 16 angka ditolak DI FORM, bukan diam-diam di antrean`() {
        val masalah = masalahNomorProspek("0812345678901234")
        assertNotNull("inilah celah yang membuat 390 prospek hilang senyap", masalah)
        assertTrue("harus menyebut berapa angkanya", masalah!!.contains("16 angka"))
        assertTrue("harus menyebut batasnya", masalah.contains("15"))
    }

    @Test
    fun `nomor terlalu pendek atau bukan 08 ditolak`() {
        assertNotNull(masalahNomorProspek("08123"))
        assertNotNull(masalahNomorProspek(""))
        // `+`-internasional dinormalkan jadi angka telanjang tanpa awalan 0 →
        // ditolak form ini. Prospek luar negeri belum jadi jalur yang didukung;
        // yang penting ia ditolak SEKARANG dengan kalimat jelas, bukan nanti.
        assertNotNull(masalahNomorProspek(normalisasiNomorProspek("+1 415 555 0100")))
    }

    @Test
    fun `pesannya menyebut angka batas, bukan kata valid saja`() {
        val pendek = masalahNomorProspek("0812")!!
        assertTrue(pendek.contains("$WA_ANGKA_MIN_LOKAL angka"))
        val panjang = masalahNomorProspek("08123456789012345")!!
        assertTrue(panjang.contains("$WA_ANGKA_MAKS"))
    }
}
