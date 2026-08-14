package com.krisoft.tridjayaelektronik.ui.raport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrak bukti Input Aktivitas — stringly-typed lintas TIGA tempat tanpa satu
 * pun pemeriksa kompiler: app ini, `KaryawanRaportPage.tsx` (web), dan
 * modul `raport` di `kinerja-service` (server).
 *
 * Nilainya ditulis sebagai LITERAL di sini, bukan dirujuk dari konstantanya —
 * test yang membandingkan konstanta dengan dirinya sendiri selalu hijau (pola
 * `OpnameKondisiTest`).
 */
class RaportBuktiPlanTest {

    // ── Bentuk evidenceUrl ───────────────────────────────────────────────────

    @Test
    fun `satu gambar dikirim sebagai string polos, bukan array`() {
        // Ini yang menjaga bukti tetap bisa dibuka pemiliknya sendiri: guard
        // server mencari `WHERE bukti_url = '/uploads/raport/a.jpg'` PERSIS.
        val hasil = buildEvidenceUrl("image", listOf("/uploads/raport/a.jpg"))
        assertEquals("/uploads/raport/a.jpg", hasil)
        assertFalse("satu gambar tak boleh dibungkus array", hasil!!.startsWith("["))
    }

    @Test
    fun `dua gambar dikirim sebagai JSON array seperti yang ditulis web`() {
        assertEquals(
            "[\"/uploads/raport/a.jpg\",\"/uploads/raport/b.jpg\"]",
            buildEvidenceUrl("image", listOf("/uploads/raport/a.jpg", "/uploads/raport/b.jpg")),
        )
    }

    @Test
    fun `video tidak pernah dibungkus array`() {
        assertEquals(
            "/uploads/raport/x.mp4",
            buildEvidenceUrl("video", listOf("/uploads/raport/x.mp4")),
        )
    }

    @Test
    fun `mode none tidak pernah membawa evidenceUrl`() {
        // Server menolak `none` + evidenceUrl (raport/service.rs:248).
        assertNull(buildEvidenceUrl("none", listOf("/uploads/raport/a.jpg")))
    }

    @Test
    fun `daftar kosong menghasilkan null, bukan array kosong`() {
        assertNull(buildEvidenceUrl("image", emptyList()))
        assertNull(buildEvidenceUrl("image", listOf("   ")))
        assertNull(buildEvidenceUrl("video", emptyList()))
    }

    @Test
    fun `yang ditulis app bisa dibaca ulang oleh parser app sendiri`() {
        // Round-trip lewat fungsi produksi, bukan parser JSON pinjaman — kalau
        // formatnya menyimpang, di sinilah ketahuan.
        val tiga = listOf("/uploads/raport/a.jpg", "/uploads/raport/b.jpg", "/uploads/raport/c.jpg")
        assertEquals(tiga, parseEvidenceUrls(buildEvidenceUrl("image", tiga)))

        val satu = listOf("/uploads/raport/a.jpg")
        assertEquals(satu, parseEvidenceUrls(buildEvidenceUrl("image", satu)))
    }

    // ── Bukti lama tidak boleh hilang ────────────────────────────────────────

    @Test
    fun `bukti lama satu URL tetap terbaca setelah ditambah satu gambar`() {
        // Server upsert dan MENIMPA bukti_url seluruhnya — mengirim hanya berkas
        // baru berarti menghapus bukti lama tanpa satu pun error.
        val hasil = gabungBukti(
            lama = parseEvidenceUrls("/uploads/raport/lama.jpg"),
            baru = listOf("/uploads/raport/baru.jpg"),
        )
        assertEquals(listOf("/uploads/raport/lama.jpg", "/uploads/raport/baru.jpg"), hasil)
    }

    @Test
    fun `gabung bukti membuang duplikat dan memotong di batas enam`() {
        val lama = (1..5).map { "/uploads/raport/l$it.jpg" }
        val baru = listOf("/uploads/raport/l1.jpg", "/uploads/raport/b1.jpg", "/uploads/raport/b2.jpg")
        val hasil = gabungBukti(lama, baru)

        assertEquals(6, hasil.size)
        // Yang BARU yang dibuang saat penuh, bukan yang sudah tersimpan.
        assertEquals(lama, hasil.take(5))
        assertEquals("/uploads/raport/b1.jpg", hasil[5])
    }

    // ── Gerbang sebelum unggah ───────────────────────────────────────────────

    @Test
    fun `batas enam gambar sama dengan web`() {
        assertEquals(6, MAX_GAMBAR)
        val gate = gateKirimBukti(jumlahGambar = 7, adaVideo = false, ukuranVideoBytes = 0L)
        assertFalse(gate.ok)
        assertTrue("pesannya harus menyebut angka batasnya", gate.alasan!!.contains("6"))
    }

    @Test
    fun `foto dan video tidak boleh bercampur dalam satu jobdesk`() {
        // Server hanya punya SATU `mode` per baris.
        val gate = gateKirimBukti(jumlahGambar = 2, adaVideo = true, ukuranVideoBytes = 1L)
        assertFalse(gate.ok)
        assertTrue(gate.alasan!!.contains("ATAU"))
    }

    @Test
    fun `tanpa berkas apa pun tidak boleh kirim`() {
        assertFalse(gateKirimBukti(jumlahGambar = 0, adaVideo = false, ukuranVideoBytes = 0L).ok)
    }

    @Test
    fun `video di atas 30MB ditolak sebelum menyentuh jaringan`() {
        // Angkanya ditulis literal sebagai penjaga terhadap MAX_EVIDENCE_BYTES
        // di kinerja-service/src/raport.rs:14.
        assertEquals(30L * 1024 * 1024, MAX_VIDEO_BUKTI_BYTES)

        val gate = gateKirimBukti(0, adaVideo = true, ukuranVideoBytes = MAX_VIDEO_BUKTI_BYTES + 1)
        assertFalse(gate.ok)
        assertTrue(gate.alasan!!.contains("30 MB"))

        assertTrue(gateKirimBukti(0, adaVideo = true, ukuranVideoBytes = MAX_VIDEO_BUKTI_BYTES).ok)
    }

    @Test
    fun `ukuran video nol berarti tak terbaca dan tetap boleh dikirim`() {
        // Kolom SIZE null itu normal untuk sebagian penyedia; menolaknya berarti
        // memblokir video yang sebenarnya sah.
        assertTrue(gateKirimBukti(0, adaVideo = true, ukuranVideoBytes = 0L).ok)
    }

    @Test
    fun `batas ukuran gambar sama dengan web`() {
        assertEquals(25L * 1024 * 1024, MAX_GAMBAR_INPUT_BYTES)
    }

    // ── Ekstensi & MIME video ────────────────────────────────────────────────

    @Test
    fun `ekstensi video hanya mp4 webm mov`() {
        assertNull(ekstensiVideo("rekaman.mkv", null))
        assertNull(ekstensiVideo("rekaman.3gp", null))
        assertNull(ekstensiVideo("rekaman.avi", "video/x-msvideo"))
        assertNull(ekstensiVideo(null, null))
        assertNull(ekstensiVideo("", null))
    }

    @Test
    fun `ekstensi diambil dari nama berkas dulu, mime jadi cadangan`() {
        // Penyedia galeri kadang menjawab video/mp4 untuk berkas .mov, dan
        // server memvalidasi ekstensi x mime x magic bytes SERENTAK.
        assertEquals("mov", ekstensiVideo("rekaman.MOV", "video/mp4"))
        assertEquals("mov", ekstensiVideo(null, "video/quicktime"))
        assertEquals("webm", ekstensiVideo("bukti", "video/webm"))
        assertEquals("mp4", ekstensiVideo("bukti", "video/mp4;codecs=avc1"))
    }

    @Test
    fun `mime dan ekstensi selalu pasangan yang divalidasi server`() {
        assertEquals("video/mp4", mimeVideo("mp4"))
        assertEquals("video/webm", mimeVideo("webm"))
        assertEquals("video/quicktime", mimeVideo("mov"))
    }

    // ── Penamaan & watermark ─────────────────────────────────────────────────

    @Test
    fun `gambar hasil watermark selalu jpg apa pun sumbernya`() {
        // prepareWatermarkedJpeg selalu meng-encode JPEG, termasuk untuk PNG/WEBP
        // dari galeri — ekstensi yang meleset ditolak server.
        assertTrue(namaBerkasGambar(2, 1L).endsWith(".jpg"))
        assertEquals("raport_1700000000000_0.jpg", namaBerkasGambar(0, 1_700_000_000_000L))
    }

    @Test
    fun `nama berkas video memakai ekstensi aslinya, bukan jpg`() {
        assertEquals("raport_1700000000000.mov", namaBerkasVideo("mov", 1_700_000_000_000L))
        assertEquals("raport_1.webm", namaBerkasVideo("webm", 1L))
    }

    @Test
    fun `nama berkas gambar berbeda per urutan agar tidak saling menimpa`() {
        val nama = (0 until 6).map { namaBerkasGambar(it, 1L) }
        assertEquals(6, nama.distinct().size)
    }

    @Test
    fun `judul watermark galeri berbeda dari kamera`() {
        assertEquals("TRIDJAYA · AKTIVITAS", watermarkTitleBukti(dariGaleri = false))
        assertEquals("TRIDJAYA · AKTIVITAS (GALERI)", watermarkTitleBukti(dariGaleri = true))
        assertTrue(watermarkTitleBukti(dariGaleri = true).contains("GALERI"))
    }

    // ── Pesan ke user ────────────────────────────────────────────────────────

    @Test
    fun `pesan gagal dekode galeri menyebut HEIC, bukan menyuruh jepret ulang`() {
        val galeri = pesanGagalDekode(dariGaleri = true)
        assertTrue(galeri.contains("HEIC"))
        assertFalse("saran 'jepret ulang' tak nyambung untuk berkas galeri", galeri.contains("jepret"))

        assertTrue(pesanGagalDekode(dariGaleri = false).contains("jepret"))
    }

    @Test
    fun `ukuran berkas nol dilaporkan tak diketahui, bukan nol byte`() {
        assertEquals("ukuran tak diketahui", formatUkuranBerkas(0L))
        assertEquals("ukuran tak diketahui", formatUkuranBerkas(-1L))
        assertTrue(formatUkuranBerkas(30L * 1024 * 1024).contains("MB"))
        assertTrue(formatUkuranBerkas(500L * 1024).contains("KB"))
    }
}
