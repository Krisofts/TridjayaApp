package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App dulu membuang body 401 mentah lalu pindah ke layar Login tanpa keterangan.
 * Karena sesi device-scoped, itu terjadi tiap kali rekan login pakai akun yang
 * sama — korban menyimpulkan "aplikasi error", login lagi, dan diam-diam
 * menendang keluar rekannya.
 */
class AlasanSesiBerakhirTest {

    @Test
    fun `pesan server selalu menang`() {
        val dariServer = "Sesi di perangkat ini sudah diakhiri. Bisa karena akun Anda dipakai login di perangkat lain."
        assertEquals(dariServer, AlasanSesiBerakhir.dari("session_revoked", dariServer))
    }

    @Test
    fun `pesan server kosong atau spasi jatuh ke teks bawaan`() {
        assertTrue(AlasanSesiBerakhir.dari("session_revoked", "   ").isNotBlank())
        assertTrue(AlasanSesiBerakhir.dari("session_revoked", null).isNotBlank())
    }

    /**
     * Server SENGAJA tak mengklaim satu sebab: keempat sebab pencabutan menulis
     * baris DB identik. Teks cadangan kita harus menyebut semuanya — menghilangkan
     * "login di perangkat lain" berarti membuang sebab yang paling sering.
     */
    @Test
    fun `cadangan session_revoked menyebut SEMUA kemungkinan`() {
        val teks = AlasanSesiBerakhir.dari("session_revoked", null)
        assertTrue("perangkat lain harus disebut: $teks", teks.contains("perangkat lain"))
        assertTrue("admin harus disebut: $teks", teks.contains("admin"))
        assertTrue("Perangkat & Sesi harus disebut: $teks", teks.contains("Perangkat & Sesi"))
    }

    @Test
    fun `session_superseded punya kalimat sendiri yang lebih pasti`() {
        val superseded = AlasanSesiBerakhir.dari("session_superseded", null)
        val revoked = AlasanSesiBerakhir.dari("session_revoked", null)
        assertNotEquals(superseded, revoked)
        assertTrue(superseded.contains("perangkat lain"))
    }

    /**
     * Kode asing TIDAK boleh menghasilkan layar kosong. Server lama menjawab
     * `unauthorized`; APK yang beredar harus tetap memberi keterangan.
     */
    @Test
    fun `kode tak dikenal tetap menghasilkan kalimat, bukan kosong`() {
        for (kode in listOf("unauthorized", "kode_masa_depan", "", null)) {
            val teks = AlasanSesiBerakhir.dari(kode, null)
            assertTrue("kode=$kode menghasilkan kosong", teks.isNotBlank())
        }
    }

    /**
     * Kegagalan jaringan BUKAN sesi berakhir. Menuduhnya begitu membuat orang
     * mengira akunnya dipakai orang lain lalu buru-buru ganti password.
     */
    @Test
    fun `gagal jaringan berbeda dari sesi berakhir`() {
        val jaringan = AlasanSesiBerakhir.gagalJaringan()
        assertNotEquals(jaringan, AlasanSesiBerakhir.dari("session_revoked", null))
        assertNotEquals(jaringan, AlasanSesiBerakhir.dari("session_expired", null))
        assertTrue(jaringan.contains("koneksi", ignoreCase = true))
    }

    /**
     * REGRESI (dilaporkan user 2026-08-07): layar Login menampilkan "Sesi tidak
     * valid" pada orang yang BELUM login sama sekali.
     *
     * Sebabnya bukan di pemetaan ini melainkan di pemanggilnya — app boot di
     * layar Login, sebuah request terlindungi menembak tanpa token, kena 401
     * `unauthorized`, dan pesan servernya dicatat sebagai "alasan keluar".
     * Test ini mengunci fakta yang membuat kekeliruan itu mungkin: kode
     * `unauthorized` MEMANG menghasilkan kalimat (bukan kosong), jadi pemanggil
     * WAJIB memutuskan sendiri apakah ada sesi yang benar-benar berakhir
     * sebelum mencatat apa pun.
     *
     * Penjaganya ada di `NetworkModule`: `adaSesiSebelumnya` di
     * `TokenRefreshAuthenticator.authenticate()`, dan cek `current.isNullOrBlank()`
     * di `TokenRefresher.refresh()`.
     */
    @Test
    fun `unauthorized tetap punya kalimat - pemanggil yang wajib menyaring`() {
        val teks = AlasanSesiBerakhir.dari("unauthorized", "Sesi tidak valid")
        assertEquals("Sesi tidak valid", teks)
        assertTrue(AlasanSesiBerakhir.dari("unauthorized", null).isNotBlank())
    }
}
