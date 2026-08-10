package com.krisoft.tridjayaelektronik.ui.serials

import com.krisoft.tridjayaelektronik.data.KATEGORI_JARANG_BER_SN
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saringan kategori di daftar produk menu Input Serial Number.
 *
 * Diuji karena kesalahannya MENYEMBUNYIKAN pekerjaan: produk yang tersaring
 * keliru tak muncul di daftar sama sekali, tak ada error, dan petugas
 * menyimpulkan barangnya sudah tak perlu didata. Kelas kegagalan yang sama
 * dengan menu yang hilang — tak terlihat justru karena berhasil bersembunyi.
 */
class FilterKategoriTest {

    private fun row(kode: String, kategori: String) =
        StokCabangRow(kode = kode, nama = kode, kategori = kategori, stok = 1)

    @Test
    fun `tanpa sembunyian semua lolos`() {
        assertTrue(lolosFilterKategori(row("A", "BAN"), emptySet()))
        assertTrue(lolosFilterKategori(row("B", "KULKAS"), emptySet()))
    }

    @Test
    fun `kategori yang dicentang tersaring`() {
        val sembunyi = setOf("BAN")
        assertFalse(lolosFilterKategori(row("A", "BAN"), sembunyi))
        assertTrue(lolosFilterKategori(row("B", "KULKAS"), sembunyi))
    }

    @Test
    fun `BANTAL TIDAK ikut tersaring saat BAN disembunyikan`() {
        // Jebakan nyata di data ERP mereka: `BANTAL` mengandung "BAN".
        // Pencocokan substring akan menyembunyikan bantal begitu petugas
        // menyembunyikan ban — 4 produk lenyap tanpa satu pun tanda.
        val sembunyi = setOf("BAN")
        assertTrue(lolosFilterKategori(row("A", "BANTAL"), sembunyi))
    }

    @Test
    fun `spasi pinggir tidak membuat kategori lolos diam-diam`() {
        assertFalse(lolosFilterKategori(row("A", "  BAN  "), setOf("BAN")))
    }

    @Test
    fun `kategori kosong bisa disembunyikan lewat label khususnya`() {
        // Barang tanpa kategori tetap harus bisa disingkirkan; tanpa label
        // eksplisit ia jadi baris yang tak bisa diketuk dengan yakin.
        assertFalse(lolosFilterKategori(row("A", ""), setOf(KATEGORI_TANPA_NAMA)))
        assertTrue(lolosFilterKategori(row("A", ""), setOf("BAN")))
    }

    @Test
    fun `daftar kategori urut abjad berikut jumlah produknya`() {
        val items = listOf(
            row("1", "KULKAS"), row("2", "BAN"), row("3", "KULKAS"), row("4", "AKSESORIS")
        )
        assertEquals(
            listOf("AKSESORIS" to 1, "BAN" to 1, "KULKAS" to 2),
            kategoriTersedia(items)
        )
    }

    @Test
    fun `barang tanpa kategori dikelompokkan di bawah label khusus`() {
        val items = listOf(row("1", ""), row("2", "   "), row("3", "BAN"))
        val hasil = kategoriTersedia(items)
        assertEquals(2, hasil.size)
        assertTrue(hasil.toString(), hasil.contains(KATEGORI_TANPA_NAMA to 2))
    }

    @Test
    fun `saran memakai nama persis dari data ERP`() {
        // Ejaan `SPERPART GODA` memang begitu di ERP mereka — kalau "dibetulkan"
        // jadi SPAREPART, sarannya berhenti mencocokkan apa pun dan tombolnya
        // jadi tak berefek tanpa satu pun error.
        assertTrue(KATEGORI_JARANG_BER_SN.contains("SPERPART GODA"))
        assertTrue(KATEGORI_JARANG_BER_SN.contains("SPAREPART SELIS"))
        assertTrue(KATEGORI_JARANG_BER_SN.contains("BAN"))
        // BANTAL bukan anggota — pastikan tak ada yang menambahkannya lewat pola.
        assertFalse(KATEGORI_JARANG_BER_SN.contains("BANTAL"))
    }
}
