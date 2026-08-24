package com.krisoft.tridjayaelektronik.ui.vertel

import com.krisoft.tridjayaelektronik.data.model.VertelBarisDto
import com.krisoft.tridjayaelektronik.data.model.VertelHasil
import com.krisoft.tridjayaelektronik.data.model.VertelKanal
import com.krisoft.tridjayaelektronik.data.model.VertelRingkasanDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan layar VERTEL.
 *
 * Yang dijaga di sini KESEPAKATAN DENGAN SERVER: dua aturan komplain menyalin
 * `validasi_catat` (`inventory-service/src/vertel.rs`), dan pemisahan
 * `tel:`/`wa.me` menyalin keputusan bahwa `waNumber` hanya soal KELAYAKAN
 * WHATSAPP — bukan soal apakah nomornya bisa ditelepon. Tak ada pemeriksa
 * kompiler untuk satu pun dari itu.
 */
class VertelPlanTest {

    private fun baris(hp: String? = null, wa: String? = null) =
        VertelBarisDto(noTransaksi = "TRX-1", tanggal = "2026-08-23", customerHp = hp, waNumber = wa)

    // ── gerbang simpan: cerminan validasi_catat ──────────────────────────────

    @Test
    fun `kanal dan hasil wajib dipilih`() {
        assertFalse(vertelCatatGate(null, VertelHasil.TERHUBUNG, false, "").bolehSimpan)
        assertFalse(vertelCatatGate(VertelKanal.WA, null, false, "").bolehSimpan)
        assertFalse(vertelCatatGate("", "", false, "").bolehSimpan)
    }

    @Test
    fun `panggilan biasa tanpa komplain boleh disimpan tanpa catatan`() {
        val g = vertelCatatGate(VertelKanal.TELEPON, VertelHasil.TIDAK_DIANGKAT, false, "")
        assertTrue(g.bolehSimpan)
        assertNull(g.alasan)
    }

    /**
     * Kontradiksi yang server tolak: tak ada yang bicara, jadi tak ada yang
     * komplain. Kalau lolos di klien, verifikator menekan Simpan lalu menerima
     * 400 untuk isian yang sudah bisa dinilai salah sebelum dikirim.
     */
    @Test
    fun `komplain pada panggilan yang tak terhubung ditolak`() {
        listOf(VertelHasil.TIDAK_DIANGKAT, VertelHasil.NOMOR_SALAH, VertelHasil.JADWAL_ULANG).forEach { h ->
            val g = vertelCatatGate(VertelKanal.TELEPON, h, adaKomplain = true, catatan = "ada keluhan")
            assertFalse("hasil=$h seharusnya menolak komplain", g.bolehSimpan)
        }
    }

    @Test
    fun `komplain wajib bercatatan`() {
        val g = vertelCatatGate(VertelKanal.TELEPON, VertelHasil.TERHUBUNG, adaKomplain = true, catatan = "   ")
        assertFalse(g.bolehSimpan)
        assertEquals("Isi catatan komplainnya supaya bisa ditindaklanjuti.", g.alasan)
    }

    @Test
    fun `komplain terhubung dan bercatatan boleh disimpan`() {
        val g = vertelCatatGate(VertelKanal.TELEPON, VertelHasil.TERHUBUNG, adaKomplain = true, catatan = "unit bunyi")
        assertTrue(g.bolehSimpan)
    }

    /**
     * Urutan pesan mengikuti server: kontradiksi hasil disebut LEBIH DULU
     * daripada catatan kosong. Kalau dibalik, verifikator mengisi catatan lalu
     * baru diberi tahu bahwa hasilnya yang salah.
     */
    @Test
    fun `hasil yang bertentangan disebut lebih dulu daripada catatan kosong`() {
        val g = vertelCatatGate(VertelKanal.WA, VertelHasil.TIDAK_DIANGKAT, adaKomplain = true, catatan = "")
        assertEquals("Komplain hanya bisa dicatat pada panggilan yang terhubung.", g.alasan)
    }

    // ── sisa pekerjaan (lencana) ─────────────────────────────────────────────

    @Test
    fun `sisa adalah total dikurangi yang sudah dicatat`() {
        assertEquals(7, sisaVertel(VertelRingkasanDto(total = 10, sudahDitelepon = 3)))
    }

    /**
     * `tanpaNomor` TIDAK ikut dikurangi: ia tidak saling lepas dari
     * `sudahDitelepon` (baris tanpa nomor tetap bisa sudah dicatat
     * `nomor_salah`), jadi menguranginya dua kali bisa menghasilkan angka
     * negatif — di sini 10 - 9 - 8 = -7.
     */
    @Test
    fun `tanpa nomor tidak ikut dikurangi sehingga sisa tak pernah negatif`() {
        val r = VertelRingkasanDto(total = 10, sudahDitelepon = 9, tanpaNomor = 8)
        assertEquals(1, sisaVertel(r))
    }

    @Test
    fun `sisa nol saat semua sudah dicatat`() {
        assertEquals(0, sisaVertel(VertelRingkasanDto(total = 5, sudahDitelepon = 5)))
    }

    /** Data server yang tak konsisten tak boleh jadi lencana negatif. */
    @Test
    fun `sisa dijaga tak negatif`() {
        assertEquals(0, sisaVertel(VertelRingkasanDto(total = 2, sudahDitelepon = 5)))
    }

    // ── tautan ───────────────────────────────────────────────────────────────

    /**
     * INTI pemisahan tel/WA: nomor rumah/kantor tak lolos syarat WhatsApp
     * sehingga `waNumber` null, TAPI ia tetap wajib ditelepon. Memakai
     * `waNumber` sebagai syarat tombol telepon akan menyembunyikan tombol dari
     * baris yang justru jadi pekerjaan verifikator.
     */
    @Test
    fun `nomor tanpa waNumber tetap bisa ditelepon`() {
        val b = baris(hp = "0260-411234", wa = null)
        assertEquals("tel:0260411234", telUri(b))
        assertNull(waUri(b))
    }

    @Test
    fun `pemisah dibuang tapi plus dipertahankan`() {
        assertEquals("tel:+628123456789", telUri(baris(hp = "+62 812-3456 (789)")))
    }

    @Test
    fun `nomor terlalu pendek tidak menghasilkan tautan`() {
        assertNull(telUri(baris(hp = "123")))
        assertNull(telUri(baris(hp = "-")))
        assertNull(telUri(baris(hp = null)))
    }

    /** Kelayakan WA milik server; app cuma memakai apa yang dikirimkan. */
    @Test
    fun `wa memakai waNumber dari server apa adanya`() {
        assertEquals("https://wa.me/628123456789", waUri(baris(wa = "628123456789")))
        assertNull(waUri(baris(wa = "  ")))
        assertNull(waUri(baris(wa = null)))
    }

    @Test
    fun `kanal default mengikuti ketersediaan WA`() {
        assertEquals(VertelKanal.WA, kanalDefault(baris(hp = "08123456789", wa = "628123456789")))
        assertEquals(VertelKanal.TELEPON, kanalDefault(baris(hp = "0260411234", wa = null)))
    }

    // ── label ────────────────────────────────────────────────────────────────

    @Test
    fun `label menerjemahkan slug server dan menolak yang asing`() {
        assertEquals("Terhubung", labelHasil(VertelHasil.TERHUBUNG))
        assertEquals("Nomor salah", labelHasil(VertelHasil.NOMOR_SALAH))
        assertNull(labelHasil("entah"))
        assertEquals("WhatsApp", labelKanal(VertelKanal.WA))
        assertNull(labelKanal(null))
    }

    /** Daftar pilihan WAJIB sama persis dengan enum server, tak lebih tak kurang. */
    @Test
    fun `pilihan hasil dan kanal mencerminkan enum server`() {
        assertEquals(
            listOf("terhubung", "tidak_diangkat", "nomor_salah", "jadwal_ulang"),
            HASIL_PILIHAN.map { it.first },
        )
        assertEquals(listOf("telepon", "wa"), KANAL_PILIHAN.map { it.first })
    }

    @Test
    fun `sudah dicatat dibaca dari ada tidaknya panggilan`() {
        assertFalse(sudahDicatat(baris()))
    }
}
