package com.krisoft.tridjayaelektronik.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga kelas bug "Key was already used" — force close yang dilempar Compose
 * saat dua item `LazyColumn`/`LazyRow` berbagi kunci.
 *
 * Yang diuji BUKAN bentuk akhirannya, melainkan satu-satunya sifat yang
 * menentukan app tertutup atau tidak: **tidak boleh ada kunci kembar**, apa pun
 * isi datanya.
 */
class KunciDaftarTest {

    private data class Baris(val kode: String, val sn: String)

    @Test
    fun `daftar yang sudah unik tidak diubah sama sekali`() {
        // Penting: item unik WAJIB mempertahankan kunci alaminya. Kalau tidak,
        // menghapus satu baris akan membuat Compose menganggap seluruh baris di
        // bawahnya item baru — state per-baris hilang, animasi meloncat.
        val daftar = listOf(Baris("A", "1"), Baris("B", "2"), Baris("C", "3"))
        assertEquals(
            listOf("A_1", "B_2", "C_3"),
            kunciUnik(daftar) { "${it.kode}_${it.sn}" },
        )
    }

    @Test
    fun `dua unit tanpa serial di satu dokumen mutasi tidak bertabrakan`() {
        // Kasus produksi persis: MutasiHistoriScreen memakai "kodeBarang_sn",
        // dan barang tanpa SN membuat dua kunci "ABC_" yang identik.
        val daftar = listOf(Baris("ABC", ""), Baris("ABC", ""), Baris("ABC", ""))
        val kunci = kunciUnik(daftar) { "${it.kode}_${it.sn}" }
        assertEquals(listOf("ABC_", "ABC_#2", "ABC_#3"), kunci)
        assertEquals(kunci.size, kunci.toSet().size)
    }

    @Test
    fun `kode barang sama dari cabang berbeda tidak bertabrakan`() {
        // Kasus produksi Deadstock: daftar menggabungkan 13 cabang, dikunci
        // hanya dengan kodeBarang.
        val kode = List(13) { "K-100" }
        val kunci = kunciUnik(kode) { it }
        assertEquals(13, kunci.toSet().size)
        assertEquals("K-100", kunci.first())
    }

    @Test
    fun `foto yang sama dipilih dua kali tidak bertabrakan`() {
        val uri = "content://media/picker/0/com.android.providers.media.photopicker/media/1000"
        val kunci = kunciUnik(listOf(uri, uri)) { it }
        assertEquals(2, kunci.toSet().size)
    }

    @Test
    fun `kunci kosong tetap kosong dan kembarannya tetap dibedakan`() {
        // Placeholder untuk kunci kosong justru berbahaya: ia bisa bertabrakan
        // dengan item lain yang kebetulan bernilai placeholder itu.
        val kunci = kunciUnik(listOf("", "", "x")) { it }
        assertEquals(listOf("", "#2", "x"), kunci)
        assertEquals(3, kunci.toSet().size)
    }

    @Test
    fun `akhiran buatan tidak bisa bertabrakan dengan kunci alami yang meniru bentuknya`() {
        // Data nakal: sebuah item yang kunci alaminya SUDAH berbentuk "A#2",
        // bersama dua item "A". Naif-nya, "A" kedua jadi "A#2" dan bentrok.
        val kunci = kunciUnik(listOf("A", "A#2", "A")) { it }
        assertEquals(kunci.size, kunci.toSet().size)
    }

    @Test
    fun `daftar kosong menghasilkan daftar kosong`() {
        assertTrue(kunciUnik(emptyList<String>()) { it }.isEmpty())
    }

    @Test
    fun `urutan dan jumlah kunci selalu sejajar dengan daftarnya`() {
        // Layar memakai `kunci.getOrElse(i)` — kalau panjangnya tak sejajar,
        // penyelarasan kunci↔item bergeser dan Compose me-reuse node yang salah.
        val daftar = List(500) { "kode-${it % 7}" }
        val kunci = kunciUnik(daftar) { it }
        assertEquals(daftar.size, kunci.size)
        assertEquals(daftar.size, kunci.toSet().size)
    }
}
