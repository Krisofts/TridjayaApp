package com.krisoft.tridjayaelektronik.ui.serials

import com.krisoft.tridjayaelektronik.data.model.SerialCoverageRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vonis kelengkapan SN — cerminan `kelengkapan()` web `AdminStokSerialInputPage.tsx`.
 *
 * Diuji karena kesalahannya TIDAK memunculkan error: memvonis `BELUM` atas
 * produk yang cakupannya sekadar tak terbaca akan menyuruh admin-stok
 * mendaftarkan ulang SN yang sudah ada (registry menolaknya sebagai duplikat,
 * jadi gejalanya cuma pekerjaan terbuang), sedangkan memvonis `LENGKAP` atas
 * produk yang belum bernomor membuat unitnya tak pernah bisa diverifikasi saat
 * opname — dan itu baru ketahuan waktu hitungan akhir kurang.
 */
class SerialCoverageTest {

    private fun peta(vararg rows: SerialCoverageRowDto) = rows.associateBy { it.kodeBarang }

    private fun vonis(
        kode: String = "BRG-1",
        stok: Int = 5,
        coverage: Map<String, SerialCoverageRowDto> = emptyMap(),
        truncated: Boolean = false,
        gagal: Boolean = false
    ) = kelengkapanSerial(kode, stok, coverage, truncated, gagal)

    @Test
    fun `serial menutupi stok berarti lengkap`() {
        val coverage = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 5, serial = 5))
        assertEquals(Kelengkapan.LENGKAP, vonis(coverage = coverage))
    }

    @Test
    fun `serial melebihi stok tetap lengkap`() {
        // Stok GS bisa turun (terjual) setelah SN didaftarkan — kelebihan baris
        // registry bukan alasan menyuruh admin mendaftarkan lagi.
        val coverage = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 7, serial = 7))
        assertEquals(Kelengkapan.LENGKAP, vonis(stok = 5, coverage = coverage))
    }

    @Test
    fun `serial kurang dari stok berarti belum`() {
        val coverage = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 3, serial = 3))
        assertEquals(Kelengkapan.BELUM, vonis(coverage = coverage))
    }

    @Test
    fun `tag leasing tidak dihitung sebagai unit bernomor`() {
        // `total` 5 tapi cuma 2 yang benar-benar serial unit — sisanya tag
        // leasing. Memakai `total` akan menyatakan produk ini lengkap padahal
        // 3 unitnya belum bernomor.
        val coverage = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 5, serial = 2, nonSerial = 3))
        assertEquals(Kelengkapan.BELUM, vonis(coverage = coverage))
    }

    @Test
    fun `produk absen dari peta utuh berarti belum`() {
        assertEquals(Kelengkapan.BELUM, vonis(coverage = emptyMap(), truncated = false))
    }

    @Test
    fun `produk absen dari peta terpotong tidak divonis nol`() {
        assertEquals(Kelengkapan.TAK_DIKETAHUI, vonis(coverage = emptyMap(), truncated = true))
    }

    @Test
    fun `cakupan gagal dimuat membuat semua produk tak diketahui`() {
        val coverage = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 5, serial = 5))
        assertEquals(Kelengkapan.TAK_DIKETAHUI, vonis(coverage = coverage, gagal = true))
    }

    @Test
    fun `produk yang ada di peta terpotong tetap divonis normal`() {
        // `truncated` cuma melarang kesimpulan atas produk yang ABSEN; yang
        // barisnya ikut terkirim tetap punya angka yang sah.
        val coverage = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 5, serial = 5))
        assertEquals(Kelengkapan.LENGKAP, vonis(coverage = coverage, truncated = true))
    }

    @Test
    fun `filter semua meloloskan status apa pun`() {
        Kelengkapan.entries.forEach { status ->
            assertTrue(status.name, lolosFilterKelengkapan(status, FilterKelengkapan.SEMUA))
        }
    }

    @Test
    fun `tak diketahui gugur dari filter belum maupun lengkap`() {
        assertFalse(lolosFilterKelengkapan(Kelengkapan.TAK_DIKETAHUI, FilterKelengkapan.BELUM))
        assertFalse(lolosFilterKelengkapan(Kelengkapan.TAK_DIKETAHUI, FilterKelengkapan.LENGKAP))
    }

    @Test
    fun `filter memilih status yang sama persis`() {
        assertTrue(lolosFilterKelengkapan(Kelengkapan.BELUM, FilterKelengkapan.BELUM))
        assertFalse(lolosFilterKelengkapan(Kelengkapan.LENGKAP, FilterKelengkapan.BELUM))
        assertTrue(lolosFilterKelengkapan(Kelengkapan.LENGKAP, FilterKelengkapan.LENGKAP))
        assertFalse(lolosFilterKelengkapan(Kelengkapan.BELUM, FilterKelengkapan.LENGKAP))
    }

    @Test
    fun `simpan menaikkan cakupan produk yang bersangkutan`() {
        val awal = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 2, serial = 2, nonSerial = 0))
        val sesudah = coverageDitambah(awal, "BRG-1", 3)
        assertEquals(5, sesudah.getValue("BRG-1").serial)
        assertEquals(5, sesudah.getValue("BRG-1").total)
        // Produk yang barusan dilengkapi harus KELUAR dari filter "belum lengkap",
        // kalau tidak simpannya terbaca gagal.
        assertEquals(Kelengkapan.LENGKAP, kelengkapanSerial("BRG-1", 5, sesudah, false, false))
    }

    @Test
    fun `produk yang belum pernah punya baris cakupan tetap terhitung setelah simpan`() {
        val sesudah = coverageDitambah(emptyMap(), "BRG-2", 4)
        assertEquals(4, sesudah.getValue("BRG-2").serial)
        assertEquals(0, sesudah.getValue("BRG-2").nonSerial)
    }

    @Test
    fun `tag leasing tidak ikut hilang saat cakupan dinaikkan`() {
        val awal = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 5, serial = 2, nonSerial = 3))
        val sesudah = coverageDitambah(awal, "BRG-1", 3)
        assertEquals(3, sesudah.getValue("BRG-1").nonSerial)
        assertEquals(8, sesudah.getValue("BRG-1").total)
    }

    @Test
    fun `nol sisipan tidak mengubah peta`() {
        // `inserted` bisa 0 kalau seluruh baris duplikat — menulis ulang barisnya
        // tak salah hitung, tapi menyalin peta tiap kali gagal itu sia-sia.
        val awal = peta(SerialCoverageRowDto(kodeBarang = "BRG-1", total = 2, serial = 2))
        assertEquals(awal, coverageDitambah(awal, "BRG-1", 0))
    }
}
