package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.data.model.PetugasDto
import com.krisoft.tridjayaelektronik.data.model.PetugasGroupDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pemetaan respons `GET /inventory/delivery/petugas` → tampilan. Tiga janji yang
 * gampang dilanggar diam-diam: kelompok kosong tetap dikatakan (bukan
 * disembunyikan), petugas tanpa nomor WA tetap tampil (tapi mati), dan
 * `lintasCabang` dibaca dari FLAG bukan dari nama kuncinya.
 */
class PanduanAlurTest {

    private fun group(
        kunci: String = "pdi",
        label: String = "PDI",
        lintasCabang: Boolean = false,
        petugas: List<PetugasDto> = emptyList(),
    ) = PetugasGroupDto(kunci = kunci, label = label, lintasCabang = lintasCabang, petugas = petugas)

    @Test
    fun `kelompok cabang yang terisi tak butuh keterangan`() {
        assertNull(keteranganDivisi(group(petugas = listOf(PetugasDto("Adji", "6281615420255")))))
    }

    @Test
    fun `kelompok kosong dikatakan jujur bukan disembunyikan`() {
        assertEquals("Belum ada petugas", keteranganDivisi(group()))
    }

    @Test
    fun `lintas cabang dibaca dari flag bukan dari nama kunci`() {
        // Kunci "pdi" + flag true → tetap dianggap lintas cabang. Kalau
        // implementasinya meng-hardcode "delivery-control", test ini merah.
        assertEquals(
            "Melayani semua cabang",
            keteranganDivisi(group(lintasCabang = true, petugas = listOf(PetugasDto("Rian", "628123456789")))),
        )
        // Kunci delivery-control TANPA flag → bukan lintas cabang.
        assertNull(
            keteranganDivisi(
                group(kunci = "delivery-control", lintasCabang = false, petugas = listOf(PetugasDto("Rian", null)))
            )
        )
    }

    @Test
    fun `lintas cabang yang kosong menyebut keduanya`() {
        assertEquals(
            "Melayani semua cabang · Belum ada petugas",
            keteranganDivisi(group(kunci = "delivery-control", lintasCabang = true)),
        )
    }

    @Test
    fun `petugas tanpa nomor WA tetap tampil tapi tak bisa ditekan`() {
        val tanpaNomor = PetugasDto(nama = "Dita Amalia", whatsapp = null)
        assertFalse(dapatDihubungi(tanpaNomor))
        assertEquals("Nomor WhatsApp belum terdaftar", keteranganPetugas(tanpaNomor))

        // String kosong / spasi dari server diperlakukan sama dengan null.
        assertFalse(dapatDihubungi(PetugasDto("Dita", "")))
        assertFalse(dapatDihubungi(PetugasDto("Dita", "   ")))
        assertEquals("Nomor WhatsApp belum terdaftar", keteranganPetugas(PetugasDto("Dita", "   ")))
    }

    @Test
    fun `petugas bernomor bisa ditekan dan nomornya ditampilkan`() {
        val ada = PetugasDto(nama = "Adji Restu Efendi", whatsapp = "6281615420255")
        assertTrue(dapatDihubungi(ada))
        assertEquals("6281615420255", keteranganPetugas(ada))
    }

    /** Gate tombol = cerminan `is_pipeline_actor`: semua role KECUALI ai-engineer murni. */
    @Test
    fun `tombol panduan alur ditutup untuk ai-engineer murni`() {
        assertFalse(panduanAlurVisible(setOf("ai-engineer")))
        assertTrue(panduanAlurVisible(setOf("karyawan")))
        assertTrue(panduanAlurVisible(setOf("pdi")))
        // Peta kemampuan menang atas daftar role cadangan.
        assertFalse(panduanAlurVisible(setOf("pdi"), mapOf("spk.pipeline" to false)))
        assertTrue(panduanAlurVisible(setOf("ai-engineer"), mapOf("spk.pipeline" to true)))
    }
}
