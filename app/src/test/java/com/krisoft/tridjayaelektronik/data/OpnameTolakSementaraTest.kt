package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penolakan server dibagi dua: PERMANEN (barisnya dibuang dari antrean lokal)
 * dan SEMENTARA (barisnya bertahan, dikirim ulang otomatis).
 *
 * Pembedaan ini lahir bersama jendela waktu opname (migrasi 196). Sebelumnya
 * SETIAP penolakan menghapus barisnya, dan itu benar selama satu-satunya
 * penolakan yang mungkin memang permanen. Begitu server bisa menjawab "sesi
 * belum dibuka", aturan lama membuang hasil scan petugas yang cuma kepagian —
 * tanpa error, tanpa tanda di layar, dan baru ketahuan saat hitungan akhir
 * kurang.
 */
class OpnameTolakSementaraTest {

    @Test
    fun `belum mulai adalah satu-satunya penolakan sementara`() {
        assertFalse(
            "kepagian bukan kesalahan — barisnya harus bertahan",
            tolakPermanen(TOLAK_JENDELA_BELUM_MULAI),
        )
        assertTrue(tolakPermanen(TOLAK_JENDELA_SUDAH_TUTUP))
        assertTrue(tolakPermanen(TOLAK_SESI_TAK_DRAFT))
        assertTrue(tolakPermanen("duplikat_dalam_sesi"))
        assertTrue(tolakPermanen("foto_wajib_untuk_manual"))
        assertTrue(tolakPermanen("kondisi_tidak_dikenal"))
    }

    @Test
    fun `kode tak dikenal diperlakukan sementara, bukan dibuang`() {
        // Server versi lebih baru boleh menambah kode. Membuang baris atas kode
        // yang belum dikenal = kehilangan data karena APK-nya tertinggal versi,
        // dan itu kelas kesalahan yang paling mahal di sini. Menyimpannya paling
        // jauh membuat satu baris tertahan di antrean sampai app diperbarui.
        assertFalse(tolakPermanen("kode_dari_masa_depan"))
    }

    @Test
    fun `kode gerbang punya kalimat Indonesia, bukan slug mentah`() {
        assertEquals(
            "sesi opname belum dibuka — tersimpan, dikirim otomatis nanti",
            alasanTolakLabel(TOLAK_JENDELA_BELUM_MULAI),
        )
        assertEquals("jendela opname sudah tutup", alasanTolakLabel(TOLAK_JENDELA_SUDAH_TUTUP))
        assertEquals("sesi opname sudah ditutup", alasanTolakLabel(TOLAK_SESI_TAK_DRAFT))
    }

    @Test
    fun `kode benar-benar asing ditampilkan apa adanya`() {
        // Lebih jujur daripada kalimat karangan yang salah.
        assertEquals("entah_apa", alasanTolakLabel("entah_apa"))
    }

    @Test
    fun `kode cerminan konstanta Rust ditulis literal`() {
        // Kontrak stringly-typed lintas repo tanpa pemeriksa kompiler.
        assertEquals("sesi_tak_draft", TOLAK_SESI_TAK_DRAFT)
        assertEquals("jendela_belum_mulai", TOLAK_JENDELA_BELUM_MULAI)
        assertEquals("jendela_sudah_tutup", TOLAK_JENDELA_SUDAH_TUTUP)
    }
}
