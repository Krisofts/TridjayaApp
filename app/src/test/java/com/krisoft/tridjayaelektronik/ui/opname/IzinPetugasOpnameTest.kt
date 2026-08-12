package com.krisoft.tridjayaelektronik.ui.opname

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penunjukan petugas opname (migrasi 212) dilihat dari sisi app.
 *
 * Dua hal yang dijaga di sini, dua-duanya pernah mahal di repo ini:
 * (a) `null` dari server BUKAN `false` — server lama tak mengirim flagnya sama
 *     sekali, dan memperlakukannya "tidak boleh" mencabut fungsi yang sudah
 *     jalan dari semua orang begitu APK baru beredar;
 * (b) jalur yang tertutup WAJIB punya kalimat sebab — tombol yang lenyap tanpa
 *     penjelasan terbaca sebagai aplikasi rusak, dan orangnya melapor ke tempat
 *     yang salah alih-alih ke admin-stok yang bisa menolongnya.
 */
class IzinPetugasOpnameTest {

    @Test
    fun `server lama tanpa flag jatuh balik ke aturan lama`() {
        // Pra-212: yang boleh menghitung boleh scan MAUPUN ketik manual.
        assertTrue(izinPenunjukan(null, bolehHitung = true))
        assertFalse(izinPenunjukan(null, bolehHitung = false))
    }

    @Test
    fun `false eksplisit adalah vonis server dan menang atas aturan lama`() {
        // Petugas yang ditunjuk untuk salah satu izin saja: `canHitung` tetap
        // true (dia boleh mencatat), tapi satu jalurnya ditutup.
        assertFalse(izinPenunjukan(false, bolehHitung = true))
        assertTrue(izinPenunjukan(true, bolehHitung = true))
    }

    @Test
    fun `kalimat sebab menyebut admin stok dan membedakan dua izin`() {
        assertTrue(ALASAN_TAK_BOLEH_SCAN.contains("admin stok"))
        assertTrue(ALASAN_TAK_BOLEH_MANUAL.contains("admin stok"))
        assertTrue(ALASAN_TAK_BOLEH_SCAN.contains("men-scan"))
        assertTrue(ALASAN_TAK_BOLEH_MANUAL.contains("mengetik SN manual"))
    }

    @Test
    fun `unit yang mengantre menyebutkan APA yang sedang ditunggu`() {
        // Alasan server memuat jam jendela opname — satu-satunya tempat angka itu
        // muncul. Membuangnya (perilaku lama) menyisakan kalimat generik dan
        // petugas cuma melihat barisnya diam tanpa tahu sebabnya.
        assertEquals(
            "SN-1 tersimpan di HP, belum terkirim — sesi opname belum dibuka",
            pesanAntre("SN-1", "sesi opname belum dibuka")
        )
        // Sinyal hilang biasa: kalimat lamanya tetap dipakai.
        assertEquals(
            "SN-1 tersimpan offline, menunggu jaringan",
            pesanAntre("SN-1", null)
        )
        assertEquals(
            "SN-1 tersimpan offline, menunggu jaringan",
            pesanAntre("SN-1", "   ")
        )
    }
}
