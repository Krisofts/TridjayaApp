package com.krisoft.tridjayaelektronik.ui.settings

import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Cek Pembaruan" di Settings pernah berbohong: cabang `else` menyapu
 * [UpdateStatus.Unknown] (pemeriksaan GAGAL) jadi pesan yang sama dengan
 * [UpdateStatus.UpToDate]. User di v2.25 (versionCode 36) selalu melihat
 * "Aplikasi sudah versi terbaru" padahal server sudah 39 — pembaruan ada,
 * yang gagal permintaannya.
 */
class UpdateCheckMessageTest {

    @Test
    fun `gagal memeriksa TIDAK boleh dilaporkan sebagai sudah terbaru`() {
        val gagal = updateCheckMessage(UpdateStatus.Unknown)
        val terbaru = updateCheckMessage(UpdateStatus.UpToDate)
        assertNotEquals(
            "Unknown berarti pemeriksaan gagal, bukan sudah paling baru",
            terbaru,
            gagal,
        )
        assertEquals("Aplikasi sudah versi terbaru", terbaru)
    }

    @Test
    fun `pesan gagal mengarahkan user memeriksa koneksi`() {
        val pesan = updateCheckMessage(UpdateStatus.Unknown).orEmpty()
        assertEquals(true, pesan.contains("Gagal", ignoreCase = true))
        assertEquals(true, pesan.contains("koneksi", ignoreCase = true))
    }

    @Test
    fun `pembaruan tersedia tidak memunculkan toast — dialognya yang tampil`() {
        val status = UpdateStatus.Available(
            force = false,
            latestVersionCode = 39,
            latestVersionName = "2.28",
            releaseNotes = "catatan",
        )
        assertNull(updateCheckMessage(status))
    }

    @Test
    fun `pembaruan wajib juga tanpa toast`() {
        val status = UpdateStatus.Available(
            force = true,
            latestVersionCode = 39,
            latestVersionName = "2.28",
            releaseNotes = "",
        )
        assertNull(updateCheckMessage(status))
    }
}
