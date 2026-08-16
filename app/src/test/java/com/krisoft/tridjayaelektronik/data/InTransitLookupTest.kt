package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tiga keadaan petunjuk in-transit — dan yang dijaga di sini adalah bahwa
 * ketiganya tetap TIGA.
 *
 * Kelas bug yang mendasarinya: memetakan "permintaannya gagal" dan "server
 * bilang tak ada" ke `null` yang sama. Itu yang menyembunyikan matinya
 * `mutasi-histori` selama tiga minggu — 10.230 permintaan, NOL yang pernah
 * dijawab 200, 78 akun, tanpa satu pun gejala. Sebabnya berganti-ganti (403
 * kemarin, 429 hari ini dari rate limit 20/menit di gateway); gejalanya tidak.
 */
class InTransitLookupTest {

    private val hint = InTransitHint(
        namaBarang = "AC SHARP 1/2PK",
        tujuanCabang = "Pagaden",
        tanggal = "2026-08-10"
    )

    @Test
    fun `gagal TIDAK sama dengan tak ada — itu inti perbaikannya`() {
        val gagal: InTransitLookup = InTransitLookup.Gagal(429)
        val takAda: InTransitLookup = InTransitLookup.TakAda
        assertNotEquals(gagal, takAda)
        // Kalau seseorang kelak menyederhanakannya kembali jadi `null?`,
        // pemeriksaan ini yang pertama kehilangan artinya.
        assertTrue(gagal is InTransitLookup.Gagal)
        assertTrue(takAda === InTransitLookup.TakAda)
    }

    @Test
    fun `kode status ikut terbawa supaya 429 bisa dibedakan dari 403`() {
        assertEquals(429, (InTransitLookup.Gagal(429) as InTransitLookup.Gagal).kode)
        assertEquals(403, (InTransitLookup.Gagal(403) as InTransitLookup.Gagal).kode)
        // Jaringan putus tak punya kode HTTP — null, bukan 0.
        assertEquals(null, (InTransitLookup.Gagal(null) as InTransitLookup.Gagal).kode)
    }

    @Test
    fun `ada membawa petunjuknya utuh`() {
        val ada = InTransitLookup.Ada(hint)
        assertEquals("AC SHARP 1/2PK", ada.hint.namaBarang)
        assertEquals("Pagaden", ada.hint.tujuanCabang)
        assertEquals("2026-08-10", ada.hint.tanggal)
    }

    /**
     * Aturan memo di `InventoryViewModel`: hanya jawaban SERVER yang boleh
     * di-memo. Test ini menuliskan aturannya sebagai predikat supaya ia tak
     * cuma hidup di komentar.
     */
    private fun bolehDiMemo(hasil: InTransitLookup): Boolean = when (hasil) {
        is InTransitLookup.Ada -> true
        InTransitLookup.TakAda -> true
        is InTransitLookup.Gagal -> false
    }

    @Test
    fun `hanya jawaban server yang boleh di-memo`() {
        assertTrue(bolehDiMemo(InTransitLookup.Ada(hint)))
        assertTrue(bolehDiMemo(InTransitLookup.TakAda))
        // Satu 429 tak boleh mengunci kata kunci itu jadi "sudah diperiksa"
        // selamanya — itu mengubah kegagalan sementara jadi permanen.
        assertEquals(false, bolehDiMemo(InTransitLookup.Gagal(429)))
        assertEquals(false, bolehDiMemo(InTransitLookup.Gagal(null)))
    }
}
