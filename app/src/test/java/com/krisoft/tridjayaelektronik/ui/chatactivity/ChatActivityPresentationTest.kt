package com.krisoft.tridjayaelektronik.ui.chatactivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate kirim bukti chat ditegakkan di klien SEBELUM unggahan dimulai — video bisa 50 MB,
 * dan menolaknya baru setelah terkirim membuang kuota karyawan di lapangan.
 */
class ChatActivityPresentationTest {

    @Test
    fun `menolak kirim tanpa video`() {
        val gate = bolehKirim(adaVideo = false, jumlahChat = 250, minimal = 200, ukuranBytes = 0)
        assertFalse(gate.ok)
    }

    @Test
    fun `menolak kirim di bawah ambang`() {
        assertFalse(bolehKirim(true, 199, 200, 1_000).ok)
        assertTrue(bolehKirim(true, 200, 200, 1_000).ok)
    }

    @Test
    fun `menolak video lebih dari 50MB sebelum menyentuh jaringan`() {
        val gate = bolehKirim(true, 250, 200, MAX_VIDEO_BYTES + 1)
        assertFalse(gate.ok)
        assertTrue(gate.alasan!!.contains("720p"))
    }

    @Test
    fun `sisa chat tidak pernah negatif`() {
        assertEquals(50, sisaChat(150, 200))
        assertEquals(0, sisaChat(250, 200))
    }

    @Test
    fun `label status berbahasa Indonesia`() {
        assertEquals("Menunggu diperiksa", statusLabel("pending_review"))
        assertEquals("Disetujui", statusLabel("approved"))
        assertEquals("Ditolak", statusLabel("rejected"))
        assertEquals("Lolos otomatis", statusLabel("auto_approved"))
        assertEquals("Belum dikirim", statusLabel("apa pun"))
    }

    @Test
    fun `ukuran tak terbaca bukan alasan menolak`() {
        // Sebagian ContentProvider mengembalikan kolom SIZE null → ViewModel mengirim 0.
        assertTrue(bolehKirim(true, 250, 200, 0).ok)
        assertTrue(bolehKirim(true, 250, 200, -1).ok)
    }

    @Test
    fun `ukuran berkas terbaca manusia`() {
        // 0 TIDAK boleh tampil "0 MB" — itu terbaca sebagai berkas rusak, padahal artinya
        // provider-nya saja yang tak mengisi kolom SIZE.
        assertEquals("ukuran tak terbaca", formatUkuranBerkas(0))
        assertEquals("512 KB", formatUkuranBerkas(512 * 1024))
        assertTrue(formatUkuranBerkas(8L * 1024 * 1024).endsWith("MB"))
    }

    @Test
    fun `tolak wajib alasan`() {
        assertFalse(bolehReview(status = "rejected", alasan = "").ok)
        assertFalse(bolehReview(status = "rejected", alasan = "   ").ok)
        assertTrue(bolehReview(status = "rejected", alasan = "video tidak sesuai").ok)
        assertTrue(bolehReview(status = "approved", alasan = "").ok)
    }
}
