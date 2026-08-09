package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KONDISI_PILIHAN] harus PERSIS sama dengan `opname::KONDISI_VALID` di
 * `inventory-service/src/opname.rs` (migrasi 194):
 *
 * ```rust
 * pub const KONDISI_VALID: [&str; 4] =
 *     [KONDISI_LAYAK, KONDISI_TIDAK_LAYAK, KONDISI_REPAIR, KONDISI_RETUR];
 * ```
 *
 * Kontrak ini stringly-typed tanpa pemeriksa kompiler lintas repo, dan sejak
 * 2026-08-09 server MENOLAK nilai asing per baris (`kondisi_tidak_dikenal`) —
 * dulu dipaksa jadi `layak` diam-diam. Artinya nilai yang meleset tak lagi
 * "tersimpan salah", melainkan unitnya TIDAK TERHITUNG sama sekali, dan petugas
 * cuma melihat satu baris ditolak tanpa tahu sebabnya.
 *
 * Nilainya ditulis sebagai literal di sini, BUKAN dirujuk dari konstantanya:
 * test yang membandingkan konstanta dengan dirinya sendiri selalu hijau.
 */
class OpnameKondisiTest {

    @Test
    fun `empat nilai kondisi persis sama dengan katalog Rust`() {
        assertEquals(listOf("layak", "tidak_layak", "repair", "retur"), KONDISI_PILIHAN)
    }

    @Test
    fun `konstanta kondisi memakai ejaan yang sama dengan kolom MySQL`() {
        // Kolom `stock_opname_units.kondisi` & `stock_serial_numbers.kondisi`
        // VARCHAR(16) — nilai yang lebih panjang terpotong senyap oleh MySQL
        // non-strict, jadi ejaannya sekaligus batas panjangnya.
        assertEquals("layak", KONDISI_LAYAK)
        assertEquals("tidak_layak", KONDISI_TIDAK_LAYAK)
        assertEquals("repair", KONDISI_REPAIR)
        assertEquals("retur", KONDISI_RETUR)
        assertTrue(KONDISI_PILIHAN.all { it.length <= 16 })
    }

    @Test
    fun `setiap nilai punya label Indonesia`() {
        // Pemilih di sheet scan merender langsung dari KONDISI_PILIHAN, jadi
        // nilai tanpa label akan tampil sebagai slug mentah ("tidak_layak").
        val label = KONDISI_PILIHAN.map { kondisiLabel(it) }
        assertEquals(listOf("Layak", "Tidak layak", "Repair", "Retur"), label)
    }

    @Test
    fun `nilai asing ditampilkan apa adanya alih-alih jadi Layak`() {
        // Server versi lebih baru boleh menambah nilai; menampilkannya apa
        // adanya jauh lebih jujur daripada memetakannya ke "Layak" dan membuat
        // petugas mengira unit rusak sudah tercatat layak.
        assertEquals("kondisi_baru_dari_server", kondisiLabel("kondisi_baru_dari_server"))
    }
}
