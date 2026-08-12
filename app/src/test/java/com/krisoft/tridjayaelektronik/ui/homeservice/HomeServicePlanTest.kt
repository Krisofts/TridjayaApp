package com.krisoft.tridjayaelektronik.ui.homeservice

import com.krisoft.tridjayaelektronik.data.HS_JENIS_TARIK_UNIT
import com.krisoft.tridjayaelektronik.data.model.HsSparepartDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bagian murni layar Komplain (Home Service). */
class HomeServicePlanTest {

    private val base = "https://tridjaya.com/"

    private fun tiket(
        id: String = "1",
        status: String = HS_BARU,
        prioritas: String = "normal",
        sla: Boolean = false,
        umur: Int? = null,
    ) = HsTicketDto(id = id, status = status, prioritas = prioritas, melewatiSla = sla, umurJam = umur)

    // ── Penyaringan antrian ──────────────────────────────────────────────────

    @Test
    fun `antrian triase hanya tiket yang menunggu keputusan CS`() {
        // `ditugaskan`/`dikerjakan` sengaja TIDAK ikut: pekerjaannya sudah pindah
        // ke teknisi, dan menghitungnya membuat badge CS tak pernah nol.
        val semua = listOf(
            tiket("a", HS_BARU),
            tiket("b", HS_MENUNGGU_TINDAK_LANJUT),
            tiket("c", HS_DITUGASKAN),
            tiket("d", HS_DIKERJAKAN),
            tiket("e", HS_SELESAI),
        )
        assertEquals(listOf("a", "b"), saringStatus(semua, HS_STATUS_TRIASE).map { it.id })
    }

    @Test
    fun `tiap mode punya himpunan status sendiri`() {
        assertEquals(HS_STATUS_TRIASE, HsMode.TRIASE.statusAktif)
        assertEquals(HS_STATUS_TUGAS_TEKNISI, HsMode.TEKNISI.statusAktif)
        assertEquals(HS_STATUS_TARIK_AKTIF, HsMode.TARIK.statusAktif)
        assertEquals(setOf(HS_TARIK_DITUGASKAN), HsMode.DRIVER.statusAktif)
    }

    @Test
    fun `daftar tugas driver wajib menyertakan jenis tarik unit`() {
        // `mine=true` memilih KOLOM yang disaring berdasarkan `jenis`; tanpa
        // jenis=tarik_unit server menyaring assigned_teknisi_id dan daftar driver
        // selalu kosong TANPA error.
        assertEquals(HS_JENIS_TARIK_UNIT, HsMode.DRIVER.jenis)
        assertEquals(true, HsMode.DRIVER.mine)
        assertEquals(HS_JENIS_TARIK_UNIT, jenisUntukTugasDriver())
    }

    @Test
    fun `urutan antrian mendahulukan yang perlu perhatian lalu yang tertua`() {
        val hasil = urutkanAntrian(
            listOf(
                tiket("biasa-muda", umur = 1),
                tiket("biasa-tua", umur = 20),
                tiket("mendesak", prioritas = "mendesak", umur = 2),
            )
        )
        assertEquals(listOf("mendesak", "biasa-tua", "biasa-muda"), hasil.map { it.id })
    }

    @Test
    fun `umur tak diketahui jatuh ke bawah, bukan dianggap nol`() {
        val hasil = urutkanAntrian(listOf(tiket("null-umur", umur = null), tiket("umur-0", umur = 0)))
        assertEquals(listOf("umur-0", "null-umur"), hasil.map { it.id })
    }

    @Test
    fun `melewati SLA ikut ditandai perlu perhatian`() {
        assertTrue(perluPerhatian(tiket(sla = true)))
        assertTrue(perluPerhatian(tiket(prioritas = "MENDESAK")))
        assertFalse(perluPerhatian(tiket()))
    }

    // ── Gate aksi ────────────────────────────────────────────────────────────

    @Test
    fun `mulai kunjungan hanya dari status ditugaskan`() {
        assertTrue(bolehMulai(HS_DITUGASKAN).ok)
        assertFalse(bolehMulai(HS_DIKERJAKAN).ok)
        assertFalse(bolehMulai(HS_BARU).ok)
    }

    @Test
    fun `hasil selesai wajib berfoto`() {
        val tanpaFoto = bolehTutupKunjungan("selesai", emptyList(), false, emptyList(), null, null, null)
        assertFalse(tanpaFoto.ok)
        assertTrue(
            bolehTutupKunjungan("selesai", listOf("/uploads/home-service/a.jpg"), false, emptyList(), null, null, null).ok
        )
        // Kunjungan ulang & eskalasi TIDAK menuntut foto (server pun tidak).
        assertTrue(bolehTutupKunjungan("kunjungan_ulang", emptyList(), false, emptyList(), null, null, null).ok)
    }

    @Test
    fun `rating di luar 1 sampai 5 ditolak`() {
        val foto = listOf("/uploads/home-service/a.jpg")
        assertFalse(bolehTutupKunjungan("selesai", foto, false, emptyList(), null, null, 0).ok)
        assertFalse(bolehTutupKunjungan("selesai", foto, false, emptyList(), null, null, 6).ok)
        assertTrue(bolehTutupKunjungan("selesai", foto, false, emptyList(), null, null, 5).ok)
    }

    @Test
    fun `sparepart berbiaya menuntut bukti bayar dan nominal`() {
        val foto = listOf("/uploads/home-service/a.jpg")
        val part = listOf(HsSparepartDto(nama = "Dinamo", qty = 1, harga = 250_000.0))
        assertFalse(bolehTutupKunjungan("selesai", foto, true, part, null, null, null).ok)
        assertFalse(bolehTutupKunjungan("selesai", foto, true, part, 250_000.0, null, null).ok)
        assertTrue(bolehTutupKunjungan("selesai", foto, true, part, 250_000.0, "/uploads/home-service/b.jpg", null).ok)
        // Sparepart gratis tak menuntut bukti bayar apa pun.
        val gratis = listOf(HsSparepartDto(nama = "Baut", qty = 2, harga = 0.0))
        assertTrue(bolehTutupKunjungan("selesai", foto, true, gratis, null, null, null).ok)
    }

    @Test
    fun `sparepart kosong atau tak wajar ditolak`() {
        val foto = listOf("/uploads/home-service/a.jpg")
        assertFalse(bolehTutupKunjungan("selesai", foto, true, emptyList(), null, null, null).ok)
        assertFalse(
            bolehTutupKunjungan("selesai", foto, true, listOf(HsSparepartDto(nama = " ", qty = 1)), null, null, null).ok
        )
        assertFalse(
            bolehTutupKunjungan("selesai", foto, true, listOf(HsSparepartDto(nama = "X", qty = 0)), null, null, null).ok
        )
    }

    @Test
    fun `biaya dihitung qty kali harga`() {
        assertEquals(
            300_000.0,
            hitungBiaya(
                listOf(
                    HsSparepartDto(nama = "A", qty = 2, harga = 100_000.0),
                    HsSparepartDto(nama = "B", qty = 1, harga = 100_000.0),
                )
            ),
            0.001,
        )
    }

    @Test
    fun `aksi beralasan wajib diisi`() {
        assertFalse(bolehAlasan(null).ok)
        assertFalse(bolehAlasan("   ").ok)
        assertTrue(bolehAlasan("Unit rusak berat").ok)
    }

    @Test
    fun `tiket butuh transaksi, foto kwitansi, dan keluhan`() {
        assertFalse(bolehBuatTiket(null, "/uploads/x.jpg", "rusak").ok)
        assertFalse(bolehBuatTiket("TR-1", null, "rusak").ok)
        assertFalse(bolehBuatTiket("TR-1", "/uploads/x.jpg", "  ").ok)
        assertTrue(bolehBuatTiket("TR-1", "/uploads/x.jpg", "rusak").ok)
    }

    // ── Format kirim ─────────────────────────────────────────────────────────

    @Test
    fun `jadwal hanya menerima YYYY-MM-DD`() {
        // Server menolak ISO8601 ber-Z/offset dengan 400 — disaring di klien
        // supaya dialognya tak tertutup lalu gagal diam-diam.
        assertEquals("2026-08-12", jadwalUntukServer("2026-08-12"))
        assertEquals("2026-08-12", jadwalUntukServer(" 2026-08-12 "))
        assertNull(jadwalUntukServer("2026-08-12T10:00:00Z"))
        assertNull(jadwalUntukServer("12/08/2026"))
        assertNull(jadwalUntukServer(""))
        assertNull(jadwalUntukServer(null))
    }

    @Test
    fun `foto dipetakan ke endpoint terautentikasi`() {
        // Upload komplain TIDAK disajikan sebagai berkas statis publik; memuat
        // "/uploads/..." apa adanya = gambar yang selalu gagal.
        assertEquals(
            "https://tridjaya.com/api/home-service/photo/abc.jpg",
            fotoHsUrl("/uploads/home-service/abc.jpg", base),
        )
        assertEquals("https://cdn.example.com/x.jpg", fotoHsUrl("https://cdn.example.com/x.jpg", base))
        assertNull(fotoHsUrl(null, base))
        assertNull(fotoHsUrl("  ", base))
    }

    @Test
    fun `status final tak menyisakan aksi`() {
        assertTrue(HS_SELESAI in HS_STATUS_FINAL)
        assertTrue(HS_ESKALASI in HS_STATUS_FINAL)
        assertTrue(HS_DIBATALKAN in HS_STATUS_FINAL)
        assertFalse(HS_MENUNGGU_TINDAK_LANJUT in HS_STATUS_FINAL)
    }

    @Test
    fun `label status memakai istilah yang dipahami lapangan`() {
        assertEquals("Perlu tindak lanjut", labelStatusHs(HS_MENUNGGU_TINDAK_LANJUT))
        assertEquals("Driver ditugaskan", labelStatusHs(HS_TARIK_DITUGASKAN))
        // Status tak dikenal (server lebih baru) tampil apa adanya, bukan kosong.
        assertEquals("status_baru_dari_server", labelStatusHs("status_baru_dari_server"))
    }
}
