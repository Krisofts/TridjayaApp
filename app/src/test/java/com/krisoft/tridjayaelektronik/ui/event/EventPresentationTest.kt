package com.krisoft.tridjayaelektronik.ui.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPresentationTest {

    @Test
    fun `form kosong tidak boleh disimpan`() {
        assertFalse(adaIsian(EventLeadForm()))
    }

    @Test
    fun `satu field terisi sudah cukup`() {
        // Tiap field diuji sendiri: aturan server bilang MINIMAL SATU, jadi menguji satu
        // field saja akan meloloskan versi yang diam-diam mewajibkan field tertentu.
        assertTrue(adaIsian(EventLeadForm(namaKonsumen = "Budi")))
        assertTrue(adaIsian(EventLeadForm(noWa = "081234567890")))
        assertTrue(adaIsian(EventLeadForm(minat = "Kulkas 2 pintu")))
        assertTrue(adaIsian(EventLeadForm(alamat = "Jl. Mawar 5")))
        assertTrue(adaIsian(EventLeadForm(fotoKtpUrl = "/uploads/event/abc.jpg")))
    }

    @Test
    fun `spasi saja bukan isian`() {
        // Server men-trim sebelum menilai; tanpa trim di sini tombol Simpan hidup padahal
        // permintaannya pasti ditolak 400.
        assertFalse(adaIsian(EventLeadForm(namaKonsumen = "   ", alamat = "\t\n")))
    }

    @Test
    fun `field kosong jadi null di badan permintaan`() {
        // `null` tidak ikut terkirim (explicitNulls = false). Mengirim "" untuk noWa akan
        // masuk normalisasi WA di server dan ditolak 400, padahal maksudnya "tidak diisi".
        val body = EventLeadForm(noWa = "  0812  ", namaKonsumen = "  ").toRequest()
        assertEquals("0812", body.noWa)
        assertNull(body.namaKonsumen)
        assertNull(body.minat)
        assertNull(body.alamat)
        assertNull(body.fotoKtpUrl)
    }
}
