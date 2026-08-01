package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.update.unduhanUtuh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga verifikasi unduhan APK (`UpdateManager.downloadApk`).
 *
 * Lahir dari laporan nyata 2026-08-01: pemakai Android 11 memakai app dengan normal
 * sepanjang pagi, tapi update-nya selalu gagal dipasang. Berkas di server terbukti utuh
 * (unduhan lewat jalur publik: 200, 5.161.638 byte, md5 cocok) dan tanda tangan 2.53
 * identik dengan 2.52 yang terpasang mulus — jadi yang rusak adalah salinan di HP.
 * Sebelum perbaikan ini, `downloadApk` menyerahkan apa pun yang mendarat di disk ke
 * installer tanpa satu pun pemeriksaan.
 */
class UnduhanUtuhTest {

    @Test
    fun `byte tertulis sama dengan content-length = utuh`() {
        assertTrue(unduhanUtuh(copied = 5_161_638, total = 5_161_638))
    }

    @Test
    fun `unduhan terputus ditolak`() {
        // Kasus yang dilaporkan: koneksi putus di tengah, berkas separuh.
        assertFalse(unduhanUtuh(copied = 2_000_000, total = 5_161_638))
        assertFalse(unduhanUtuh(copied = 0, total = 5_161_638))
    }

    @Test
    fun `lebih panjang dari yang dijanjikan juga ditolak`() {
        // Bukan berkas yang dijanjikan server — menyerahkannya ke installer = menebak.
        assertFalse(unduhanUtuh(copied = 5_161_700, total = 5_161_638))
    }

    @Test
    fun `tanpa content-length dianggap utuh, penjaga kedua yang menilai`() {
        // Server tanpa Content-Length (atau respons terkompresi) tak bisa diverifikasi
        // panjangnya. Menolak di sini akan mematikan update pada server yang SAH;
        // `getPackageArchiveInfo` yang memutuskan berkasnya bisa diurai atau tidak.
        assertTrue(unduhanUtuh(copied = 5_161_638, total = -1))
        assertTrue(unduhanUtuh(copied = 0, total = 0))
    }
}
