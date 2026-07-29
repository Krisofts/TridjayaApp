package com.krisoft.tridjayaelektronik.ui.opname

import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Usulan pendaftaran SN dari lapangan (registry SN terpusat, 2026-07-29).
 *
 * Dua aturan diuji di sini karena keduanya adalah pagar TERAKHIR sebelum data
 * masuk antrian admin-stok: siapa yang boleh mengusulkan unit mana, dan usulan
 * seperti apa yang boleh dikirim. Server memvalidasi ulang keduanya, tapi
 * kalau klien salah, gejalanya bukan error melainkan antrian persetujuan yang
 * terisi usulan tak bisa diverifikasi.
 */
class SerialProposalTest {

    private fun unit(
        serial: String = "SN-1",
        temuan: String? = TEMUAN_TIDAK_TERDAFTAR,
        syncedAtMillis: Long? = 1_000L
    ) = OpnameUnitEntity(
        sessionId = "sesi-1",
        serialNumber = serial,
        kodeBarang = "BRG-1",
        namaBarang = "Kulkas 2 Pintu",
        kondisi = "layak",
        keterangan = null,
        temuan = temuan,
        updatedAtMillis = 1_000L,
        syncedAtMillis = syncedAtMillis
    )

    @Test
    fun `unit belum terdaftar yang sudah terkirim boleh diusulkan`() {
        assertTrue(bolehUsulkanSn(unit(), canPropose = true))
    }

    @Test
    fun `tanpa kemampuan serial propose tak ada tombol usul`() {
        assertFalse(bolehUsulkanSn(unit(), canPropose = false))
    }

    @Test
    fun `temuan lain bukan urusan pendaftaran`() {
        // Serial yang terdaftar di cabang lain atau sudah terjual TIDAK kurang
        // terdaftar — mengusulkannya cuma menyumbat antrian admin-stok dengan
        // usulan yang pasti ditolak.
        assertFalse(bolehUsulkanSn(unit(temuan = "cabang_lain"), canPropose = true))
        assertFalse(bolehUsulkanSn(unit(temuan = "sudah_terjual"), canPropose = true))
        assertFalse(bolehUsulkanSn(unit(temuan = null), canPropose = true))
    }

    @Test
    fun `unit yang masih mengantre belum punya vonis temuan`() {
        // `temuan` diisi server saat unit diterima. Baris yang belum terkirim
        // belum pernah dinilai, jadi tombolnya harus diam sampai antrean jalan.
        assertFalse(bolehUsulkanSn(unit(syncedAtMillis = null), canPropose = true))
    }

    @Test
    fun `usulan tanpa dua foto ditolak di klien`() {
        val kosong = SerialProposalDraft(kodeBarang = "BRG-1", namaBarang = null, serialNumber = "SN-1")
        assertFalse(kosong.isValid)
        assertFalse(kosong.copy(fotoSnUrl = "/uploads/serial/a.jpg").isValid)
        assertFalse(kosong.copy(fotoBarangUrl = "/uploads/serial/b.jpg").isValid)
        assertTrue(
            kosong.copy(
                fotoSnUrl = "/uploads/serial/a.jpg",
                fotoBarangUrl = "/uploads/serial/b.jpg"
            ).isValid
        )
    }

    @Test
    fun `serial kosong tak bisa diusulkan walau dua foto ada`() {
        val draft = SerialProposalDraft(
            kodeBarang = "BRG-1",
            namaBarang = null,
            serialNumber = "   ".trim(),
            fotoSnUrl = "/uploads/serial/a.jpg",
            fotoBarangUrl = "/uploads/serial/b.jpg"
        )
        assertFalse(draft.isValid)
    }

    @Test
    fun `selagi mengunggah atau mengirim tombol dianggap sibuk`() {
        val siap = SerialProposalDraft(
            kodeBarang = "BRG-1",
            namaBarang = null,
            serialNumber = "SN-1",
            fotoSnUrl = "/uploads/serial/a.jpg",
            fotoBarangUrl = "/uploads/serial/b.jpg"
        )
        assertFalse(siap.busy)
        assertTrue(siap.copy(uploading = true).busy)
        // Tanpa penanda ini tombol "Kirim usulan" bisa ditekan dua kali dan
        // melahirkan dua baris usulan untuk satu unit fisik.
        assertTrue(siap.copy(submitting = true).busy)
    }
}
