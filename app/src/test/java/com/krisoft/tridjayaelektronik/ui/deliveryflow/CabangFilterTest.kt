package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saringan cabang antrian PDI.
 *
 * Yang dijaga di sini BUKAN tampilan chip melainkan dua penjagaan yang mencegah
 * saringan ini berubah jadi penyembunyi pekerjaan — kelas kekeliruan yang sudah
 * membuat saringan PERIODE dilarang di antrian yang sama.
 */
class CabangFilterTest {

    private fun grup(kode: String, vararg dealer: String) = SpkBatchGroup(
        kode = kode,
        jobs = dealer.mapIndexed { i, d ->
            DeliveryJobDto(id = "$kode-$i", kodePengiriman = "$kode-1u${i + 1}", kodeDealer = d)
        },
    )

    // ── pencocokan kode cabang ───────────────────────────────────────────────

    @Test
    fun `kode sama dianggap cabang saya`() {
        assertTrue(cabangSaya("PGD", "PGD"))
    }

    /**
     * Kode dealer datang dari ERP dan pernah membawa spasi/ejaan campuran.
     * Perbandingan mentah tak melempar galat — ia cuma memindahkan SELURUH
     * antrian ke "Cabang lain", yaitu kegagalan yang terlihat seperti fitur yang
     * bekerja.
     */
    @Test
    fun `spasi dan besar-kecil huruf tidak menggagalkan pencocokan`() {
        assertTrue(cabangSaya(" PGD ", "pgd"))
        assertTrue(cabangSaya("Pgd", "PGD"))
    }

    @Test
    fun `kosong bukan cabang siapa pun`() {
        assertFalse(cabangSaya(null, "PGD"))
        assertFalse(cabangSaya("PGD", null))
        assertFalse(cabangSaya("  ", "PGD"))
        assertFalse(cabangSaya("PGD", "  "))
    }

    /** SPK campur cabang: dipegang selama SALAH SATU unitnya ada di cabang saya. */
    @Test
    fun `grup diklaim milik saya bila salah satu unitnya cabang saya`() {
        assertTrue(grupMilikSaya(grup("A", "SKL", "PGD"), "PGD"))
        assertFalse(grupMilikSaya(grup("B", "SKL", "SBG"), "PGD"))
    }

    // ── penyaringan ──────────────────────────────────────────────────────────

    private val bercampur = listOf(
        grup("A", "PGD"),
        grup("B", "SKL"),
        grup("C", "PGD"),
        grup("D", "SBG"),
    )

    @Test
    fun `daftar bercampur menampilkan chip dan menghitung kedua ember`() {
        val h = saringPerCabang(bercampur, "PGD", CabangSaring.SEMUA)
        assertTrue(h.tampilkanChip)
        assertEquals(2, h.jumlahTokoSaya)
        assertEquals(2, h.jumlahCabangLain)
        assertEquals(4, h.terlihat.size)
    }

    @Test
    fun `toko saya menyisakan grup cabang saya saja`() {
        val h = saringPerCabang(bercampur, "PGD", CabangSaring.TOKO_SAYA)
        assertEquals(listOf("A", "C"), h.terlihat.map { it.kode })
    }

    @Test
    fun `cabang lain menyisakan sisanya`() {
        val h = saringPerCabang(bercampur, "PGD", CabangSaring.CABANG_LAIN)
        assertEquals(listOf("B", "D"), h.terlihat.map { it.kode })
    }

    /** Urutan server tak boleh diacak — antrian punya makna urutan. */
    @Test
    fun `urutan asli dipertahankan`() {
        val h = saringPerCabang(bercampur, "PGD", CabangSaring.SEMUA)
        assertEquals(listOf("A", "B", "C", "D"), h.terlihat.map { it.kode })
    }

    /** Kedua ember MEMBELAH HABIS: tak ada grup yang hilang atau terhitung dua kali. */
    @Test
    fun `kedua ember membelah habis daftar`() {
        val h = saringPerCabang(bercampur, "PGD", CabangSaring.SEMUA)
        assertEquals(bercampur.size, h.jumlahTokoSaya + h.jumlahCabangLain)
    }

    // ── penjagaan 1: chip cuma saat benar-benar bercampur ────────────────────

    /**
     * Petugas PDI cabang biasa memang cuma menerima antrian cabangnya sendiri
     * (server yang men-scope). Chip di sana nol guna dan cuma menambah baris.
     */
    @Test
    fun `semua milik saya - chip tak ditawarkan`() {
        val h = saringPerCabang(listOf(grup("A", "PGD"), grup("B", "PGD")), "PGD", CabangSaring.SEMUA)
        assertFalse(h.tampilkanChip)
        assertEquals(2, h.terlihat.size)
    }

    @Test
    fun `semua cabang lain - chip juga tak ditawarkan`() {
        val h = saringPerCabang(listOf(grup("A", "SKL"), grup("B", "SBG")), "PGD", CabangSaring.SEMUA)
        assertFalse(h.tampilkanChip)
        assertEquals(2, h.terlihat.size)
    }

    @Test
    fun `daftar kosong tak menawarkan chip`() {
        val h = saringPerCabang(emptyList(), "PGD", CabangSaring.SEMUA)
        assertFalse(h.tampilkanChip)
        assertTrue(h.terlihat.isEmpty())
    }

    /**
     * Konteks cabang fail-soft: `null` berarti TAK TAHU, dan menebaknya akan
     * menyembunyikan pekerjaan sungguhan. Seluruh daftar tampil, chip tak ada.
     */
    @Test
    fun `cabang saya tak diketahui - seluruh daftar tampil tanpa chip`() {
        listOf(null, "", "   ").forEach { saya ->
            val h = saringPerCabang(bercampur, saya, CabangSaring.TOKO_SAYA)
            assertFalse("kodeDealerSaya='$saya' seharusnya tak menawarkan chip", h.tampilkanChip)
            assertEquals("kodeDealerSaya='$saya' seharusnya menampilkan semua", 4, h.terlihat.size)
        }
    }

    // ── penjagaan 2: tak ada kebuntuan ───────────────────────────────────────

    /**
     * INI yang paling penting. Skenarionya nyata: petugas memilih "Cabang lain",
     * menarik-refresh, dan ternyata semua unit kini milik cabangnya. Kalau
     * saringannya tetap berlaku sementara chip-nya lenyap, layarnya KOSONG tanpa
     * satu pun jalan kembali — dan itu terbaca sebagai data yang hilang, bukan
     * sebagai saringan.
     */
    @Test
    fun `saringan diabaikan saat chip tak ditampilkan`() {
        val semuaMilikSaya = listOf(grup("A", "PGD"), grup("B", "PGD"))
        CabangSaring.entries.forEach { s ->
            val h = saringPerCabang(semuaMilikSaya, "PGD", s)
            assertFalse(h.tampilkanChip)
            assertEquals("saring=$s meninggalkan layar kosong tanpa jalan kembali", 2, h.terlihat.size)
        }
    }

    /**
     * Selama chip TAMPIL, tak satu pun pilihan boleh menghasilkan daftar kosong —
     * kedua ember dijamin berisi oleh syarat `bercampur` itu sendiri.
     */
    @Test
    fun `selama chip tampil tak ada pilihan yang mengosongkan layar`() {
        CabangSaring.entries.forEach { s ->
            val h = saringPerCabang(bercampur, "PGD", s)
            assertTrue(h.tampilkanChip)
            assertTrue("saring=$s mengosongkan layar", h.terlihat.isNotEmpty())
        }
    }

    // ── label ────────────────────────────────────────────────────────────────

    /**
     * Angka WAJIB ada: yang tersaring harus tetap terbaca sebagai tumpukan yang
     * ada, bukan pekerjaan yang lenyap.
     */
    @Test
    fun `label chip membawa angkanya`() {
        val h = saringPerCabang(bercampur, "PGD", CabangSaring.TOKO_SAYA)
        assertEquals("Semua (4)", labelChipCabang(CabangSaring.SEMUA, h))
        assertEquals("Toko saya (2)", labelChipCabang(CabangSaring.TOKO_SAYA, h))
        assertEquals("Cabang lain (2)", labelChipCabang(CabangSaring.CABANG_LAIN, h))
    }

    /** Angka chip TIDAK ikut menyusut saat sedang menyaring — ia jumlah ember,
     *  bukan jumlah yang kebetulan tampil. */
    @Test
    fun `angka chip tetap sama apa pun pilihannya`() {
        val a = saringPerCabang(bercampur, "PGD", CabangSaring.SEMUA)
        val b = saringPerCabang(bercampur, "PGD", CabangSaring.CABANG_LAIN)
        assertEquals(labelChipCabang(CabangSaring.TOKO_SAYA, a), labelChipCabang(CabangSaring.TOKO_SAYA, b))
    }
}
