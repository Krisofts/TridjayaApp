package com.krisoft.tridjayaelektronik.ui.opname

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Daftar barang sesi opname yang kosong di layar punya TIGA sebab, dan
 * tindak lanjutnya berbeda: tunggu / coba lagi / minta sesinya dibatalkan.
 *
 * Yang dijaga di sini adalah kesalahan yang benar-benar terjadi: `isLoading`
 * dimatikan begitu detail sesi tiba, sedangkan daftar barang baru diminta
 * sesudahnya — jadi tanpa keadaan "sedang dimuat" ada satu round-trip penuh di
 * mana sesi SEHAT dituduh "tidak punya daftar barang sama sekali", lengkap
 * dengan saran membatalkannya.
 */
class OpnameDaftarBarangKosongTest {

    @Test
    fun `permintaan yang masih terbang tidak dituduh kosong`() {
        assertEquals(
            SebabDaftarBarangKosong.SEDANG_DIMUAT,
            sebabDaftarBarangKosong(stockLoading = true, stockError = null)
        )
    }

    @Test
    fun `coba lagi selagi error lama masih tersimpan tetap terbaca sedang dimuat`() {
        // Tombol "Coba lagi" memanggil `load(paksaStock = true)`, yang menyalakan
        // `stockLoading` TANPA membersihkan `stockError` — error baru ditimpa
        // setelah jawabannya tiba. Yang benar dilaporkan saat itu adalah
        // permintaan yang sedang jalan.
        assertEquals(
            SebabDaftarBarangKosong.SEDANG_DIMUAT,
            sebabDaftarBarangKosong(stockLoading = true, stockError = "upstream tidak merespons")
        )
    }

    @Test
    fun `gagal dimuat dibedakan dari memang kosong`() {
        assertEquals(
            SebabDaftarBarangKosong.GAGAL_DIMUAT,
            sebabDaftarBarangKosong(stockLoading = false, stockError = "upstream tidak merespons")
        )
        assertEquals(
            SebabDaftarBarangKosong.MEMANG_KOSONG,
            sebabDaftarBarangKosong(stockLoading = false, stockError = null)
        )
    }

    @Test
    fun `sebab dari server ikut terbaca petugas`() {
        val dariServer = "Stok cabang D-12 terbaca kosong (nol baris)"
        val pesan = pesanDaftarBarangGagal(dariServer)
        assertTrue(pesan, pesan.contains(dariServer))
        assertTrue(
            "tetap membantah kesimpulan sesi kosong: $pesan",
            pesan.contains("bukan berarti tidak ada barang")
        )
    }

    @Test
    fun `teks exception jaringan tidak ditampilkan mentah`() {
        // `OpnameRepository.call` memakai `e.message` apa adanya; OkHttp menulisnya
        // dalam bahasa Inggris teknis dan di layar gudang itu terbaca APP RUSAK.
        val pesan = pesanDaftarBarangGagal(
            "Unable to resolve host tridjaya.com: No address associated with hostname"
        )
        assertFalse(pesan, pesan.contains("Unable to resolve host"))
        assertTrue(pesan, pesan.contains("sinyal"))
    }

    @Test
    fun `kalimat gateway dan alamat internal tidak bocor ke layar gudang`() {
        // Bukan OkHttp: ini kalimat GATEWAY saat inventory-service mati —
        // `upstream tidak merespons: {reqwest::Error}`, yang Display-nya memuat
        // host dan port internal. Ia berbahasa Indonesia di depan, jadi penyaring
        // lama (daftar penanda OkHttp) meloloskannya sebagai "sebab dari server".
        val pesan = pesanDaftarBarangGagal(
            "upstream tidak merespons: error sending request for url " +
                "(http://127.0.0.1:8085/inventory/opname/OPN-2026-0007/stock)"
        )
        assertFalse(pesan, pesan.contains("http://"))
        assertFalse(pesan, pesan.contains("error sending request"))
        assertFalse(pesan, pesan.contains("8085"))
        assertTrue(pesan, pesan.contains("bukan berarti tidak ada barang"))
    }

    @Test
    fun `tanpa sebab tetap ada kalimat dan langkah berikutnya`() {
        for (sebab in listOf(null, "", "   ")) {
            val pesan = pesanDaftarBarangGagal(sebab)
            assertTrue(pesan, pesan.contains("bukan berarti tidak ada barang"))
            assertTrue(pesan, pesan.contains("Coba lagi"))
        }
    }
}
