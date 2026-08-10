package com.krisoft.tridjayaelektronik.ui.serials

import com.krisoft.tridjayaelektronik.data.KONDISI_REPAIR
import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jejak audit satu unit di bagian "SN sudah tercatat".
 *
 * [waktuSingkat] diuji karena `java.time` HARAM di `app/src/main` (minSdk 24,
 * tanpa desugaring) — formatnya ditulis tangan, dan format tanggal yang meleset
 * tidak memunculkan error, hanya jejak audit yang menunjuk waktu yang salah.
 *
 * [jejakUnit] diuji karena ia harus membedakan TIGA hal yang gampang menyatu
 * jadi satu tampilan kosong: belum pernah divonis, sudah divonis tapi
 * penetapnya tak terekam, dan asal-usul baris yang berbeda-beda.
 */
class JejakUnitTest {

    private fun baris(
        kondisi: String? = null,
        kondisiByName: String? = null,
        kondisiAt: String? = null,
        sourceFile: String = "manual-input",
        createdByName: String? = null
    ) = SerialRegistryRow(
        serialNumber = "SN-1",
        kondisi = kondisi,
        kondisiByName = kondisiByName,
        kondisiAt = kondisiAt,
        sourceFile = sourceFile,
        createdByName = createdByName
    )

    @Test
    fun `iso diringkas jadi tanggal dan jam`() {
        assertEquals("10 Agu 17:05", waktuSingkat("2026-08-10T17:05:00"))
    }

    @Test
    fun `nol di depan tanggal dibuang`() {
        assertEquals("3 Jan 08:00", waktuSingkat("2026-01-03T08:00:00"))
    }

    @Test
    fun `desember terpetakan benar`() {
        // Indeks bulan off-by-one adalah kesalahan klasik di peta bulan manual;
        // Desember (12) adalah ujung yang membuktikannya.
        assertEquals("31 Des 23:59", waktuSingkat("2026-12-31T23:59:59"))
    }

    @Test
    fun `format tak dikenal dikembalikan apa adanya`() {
        // Lebih baik menampilkan mentahnya daripada menebak — jejak audit yang
        // salah tanggal lebih buruk daripada jejak yang terlihat mentah.
        assertEquals("bukan-iso", waktuSingkat("bukan-iso"))
        assertEquals("2026-08-10", waktuSingkat("2026-08-10"))
    }

    @Test
    fun `bulan di luar 1-12 tidak dipaksa`() {
        assertEquals("2026-13-01T10:00:00", waktuSingkat("2026-13-01T10:00:00"))
    }

    @Test
    fun `kondisi belum ditetapkan dikatakan apa adanya`() {
        val teks = jejakUnit(baris(kondisi = null))
        assertTrue(teks, teks.contains("belum pernah ditetapkan"))
    }

    @Test
    fun `penetap tak terekam tidak disamarkan jadi kosong`() {
        val teks = jejakUnit(baris(kondisi = KONDISI_REPAIR, kondisiByName = null))
        assertTrue(teks, teks.contains("tak diketahui"))
    }

    @Test
    fun `penetap dan waktu ikut tampil`() {
        val teks = jejakUnit(
            baris(kondisi = KONDISI_REPAIR, kondisiByName = "Kiryanto", kondisiAt = "2026-08-10T17:05:00")
        )
        assertTrue(teks, teks.contains("Kiryanto"))
        assertTrue(teks, teks.contains("10 Agu 17:05"))
    }

    @Test
    fun `asal-usul baris dijelaskan per sentinel`() {
        assertTrue(jejakUnit(baris(sourceFile = "manual-input")).contains("diketik admin-stok"))
        assertTrue(jejakUnit(baris(sourceFile = "manager-generated")).contains("kode GEN-"))
        assertTrue(jejakUnit(baris(sourceFile = "usulan-cabang")).contains("usulan cabang"))
        // Nama berkas impor Excel bebas — apa pun di luar sentinel dibaca impor.
        assertTrue(jejakUnit(baris(sourceFile = "SN_JULI.xlsx")).contains("impor SN_JULI.xlsx"))
        assertTrue(jejakUnit(baris(sourceFile = "")).contains("asal tak diketahui"))
    }

    @Test
    fun `pendaftar ikut disebut bila terekam`() {
        val teks = jejakUnit(baris(sourceFile = "manual-input", createdByName = "Sari"))
        assertTrue(teks, teks.contains("oleh Sari"))
    }
}
