package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Insiden DLV-M84149DA0 (2026-07-29): SPK Soklat berisi barang yang cuma
 * berstok di Pagaden — daftar hasil pencarian cabang sebelumnya masih di layar
 * saat cabang sudah pindah, dan cabang barang baru dilekatkan saat SUBMIT dari
 * `spkCabang`. Unitnya masuk antrian PDI cabang yang tak memegang barangnya.
 */
class StokRowsForCabangTest {
    private val rows = listOf(StokCabangRow(kode = "TE-874", nama = "SPEAKER POLYTRON PAS-PRO15F3"))

    @Test
    fun `hasil cabang lain disembunyikan`() {
        assertTrue(stokRowsForCabang(rows, stokDealer = "D-01", spkCabang = "D-03").isEmpty())
    }

    @Test
    fun `hasil cabang sama tampil`() {
        assertEquals(rows, stokRowsForCabang(rows, stokDealer = "D-01", spkCabang = "D-01"))
    }

    @Test
    fun `cocok tanpa peduli besar-kecil huruf dan spasi`() {
        assertEquals(rows, stokRowsForCabang(rows, stokDealer = " d-01 ", spkCabang = "D-01"))
    }

    @Test
    fun `belum pilih cabang berarti tak ada yang bisa ditap`() {
        assertTrue(stokRowsForCabang(rows, stokDealer = "", spkCabang = "").isEmpty())
    }
}
