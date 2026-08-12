package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penolakan server dibagi dua: PERMANEN (barisnya dibuang dari antrean lokal)
 * dan SEMENTARA (barisnya bertahan, dikirim ulang otomatis).
 *
 * Pembedaan ini lahir bersama jendela waktu opname (migrasi 196). Sebelumnya
 * SETIAP penolakan menghapus barisnya, dan itu benar selama satu-satunya
 * penolakan yang mungkin memang permanen. Begitu server bisa menjawab "sesi
 * belum dibuka", aturan lama membuang hasil scan petugas yang cuma kepagian —
 * tanpa error, tanpa tanda di layar, dan baru ketahuan saat hitungan akhir
 * kurang.
 */
class OpnameTolakSementaraTest {

    @Test
    fun `belum mulai adalah satu-satunya penolakan sementara`() {
        assertFalse(
            "kepagian bukan kesalahan — barisnya harus bertahan",
            tolakPermanen(TOLAK_JENDELA_BELUM_MULAI),
        )
        assertTrue(tolakPermanen(TOLAK_JENDELA_SUDAH_TUTUP))
        assertTrue(tolakPermanen(TOLAK_SESI_TAK_DRAFT))
        assertTrue(tolakPermanen("duplikat_dalam_sesi"))
        assertTrue(tolakPermanen("foto_wajib_untuk_manual"))
        assertTrue(tolakPermanen("kondisi_tidak_dikenal"))
    }

    @Test
    fun `kode tak dikenal diperlakukan sementara, bukan dibuang`() {
        // Server versi lebih baru boleh menambah kode. Membuang baris atas kode
        // yang belum dikenal = kehilangan data karena APK-nya tertinggal versi,
        // dan itu kelas kesalahan yang paling mahal di sini. Menyimpannya paling
        // jauh membuat satu baris tertahan di antrean sampai app diperbarui.
        assertFalse(tolakPermanen("kode_dari_masa_depan"))
    }

    @Test
    fun `kode gerbang punya kalimat Indonesia, bukan slug mentah`() {
        assertEquals(
            "sesi opname belum dibuka — tersimpan, dikirim otomatis nanti",
            alasanTolakLabel(TOLAK_JENDELA_BELUM_MULAI),
        )
        assertEquals("jendela opname sudah tutup", alasanTolakLabel(TOLAK_JENDELA_SUDAH_TUTUP))
        assertEquals("sesi opname sudah ditutup", alasanTolakLabel(TOLAK_SESI_TAK_DRAFT))
    }

    @Test
    fun `kode benar-benar asing ditampilkan apa adanya`() {
        // Lebih jujur daripada kalimat karangan yang salah.
        assertEquals("entah_apa", alasanTolakLabel("entah_apa"))
    }

    @Test
    fun `kode cerminan konstanta Rust ditulis literal`() {
        // Kontrak stringly-typed lintas repo tanpa pemeriksa kompiler.
        assertEquals("sesi_tak_draft", TOLAK_SESI_TAK_DRAFT)
        assertEquals("jendela_belum_mulai", TOLAK_JENDELA_BELUM_MULAI)
        assertEquals("jendela_sudah_tutup", TOLAK_JENDELA_SUDAH_TUTUP)
        // Migrasi 212.
        assertEquals("izin_tetapkan_sn", TOLAK_IZIN_TETAPKAN_SN)
        assertEquals("izin_verifikasi_sn", TOLAK_IZIN_VERIFIKASI_SN)
    }

    @Test
    fun `dua kode izin penunjukan permanen dan menyebut jalan keluarnya`() {
        assertTrue(tolakPermanen(TOLAK_IZIN_TETAPKAN_SN))
        assertTrue(tolakPermanen(TOLAK_IZIN_VERIFIKASI_SN))
        // Petugas yang cuma diberi tahu "ditolak" akan mencoba lagi seharian.
        assertTrue(alasanTolakLabel(TOLAK_IZIN_TETAPKAN_SN).contains("hubungi admin stok"))
        assertTrue(alasanTolakLabel(TOLAK_IZIN_VERIFIKASI_SN).contains("hubungi admin stok"))
        // Dua izin BEDA — kalimatnya tak boleh tertukar.
        assertTrue(alasanTolakLabel(TOLAK_IZIN_TETAPKAN_SN).contains("mengetik SN manual"))
        assertTrue(alasanTolakLabel(TOLAK_IZIN_VERIFIKASI_SN).contains("men-scan SN"))
    }

    // ---- Sifat kegagalan HTTP ---------------------------------------------

    @Test
    fun `403 permanen, 5xx dan tanpa status sementara`() {
        // Inti perbaikan 2026-08-12: sebelum ini semuanya diperlakukan sama
        // ("tersimpan offline"), termasuk 403.
        assertEquals(SifatGagal.PERMANEN, sifatGagal(403))
        assertEquals(SifatGagal.PERMANEN, sifatGagal(400))
        assertEquals(SifatGagal.PERMANEN, sifatGagal(404))
        assertEquals(SifatGagal.SESI, sifatGagal(401))
        assertEquals(SifatGagal.SEMENTARA, sifatGagal(500))
        assertEquals(SifatGagal.SEMENTARA, sifatGagal(502))
        assertEquals(SifatGagal.SEMENTARA, sifatGagal(503))
        assertEquals(SifatGagal.SEMENTARA, sifatGagal(429))
        // Tak pernah sampai server (IOException) — mode pesawat.
        assertEquals(SifatGagal.SEMENTARA, sifatGagal(null))
    }

    @Test
    fun `status tak dikenal diperlakukan sementara`() {
        // Daftar-putih, arah aman: APK yang tertinggal versi tak boleh membuang
        // hasil kerja petugas karena jawabannya belum dikenal. Pola sama
        // `tolakPermanen`.
        assertEquals(SifatGagal.SEMENTARA, sifatGagal(418))
        assertFalse(bolehBuangAntrean(gagal(418, "forbidden")))
    }

    /** Kegagalan ber-badan JSON aplikasi kita (punya `code` sungguhan). */
    private fun gagal(status: Int?, code: String) =
        AuthResult.Failure(code, "pesan", status)

    /**
     * Kegagalan yang badannya BUKAN JSON kita — `parseError` menamainya
     * `http_<status>`. Inilah bentuk jawaban Cloudflare/WAF/proxy.
     */
    private fun gagalNonAplikasi(status: Int) =
        AuthResult.Failure("http_$status", "Forbidden", status)

    @Test
    fun `hanya penolakan atas ISI permintaan yang boleh membuang antrean`() {
        assertTrue(bolehBuangAntrean(gagal(403, "forbidden")))
        assertTrue(bolehBuangAntrean(gagal(409, "conflict")))
        assertTrue(bolehBuangAntrean(gagal(422, "validation_error")))
        // 404 bisa berarti rute gateway belum ter-deploy (insiden APK 2.67),
        // 401 cuma berarti sesinya mati, dan 400 di endpoint ini juga dipakai
        // untuk "Akun belum terikat cabang" yang JUSTRU berubah jawabannya
        // setelah admin membetulkan data akun — ketiganya BUKAN vonis atas
        // datanya, jadi barisnya bertahan.
        assertFalse(bolehBuangAntrean(gagal(400, "validation_error")))
        assertFalse(bolehBuangAntrean(gagal(404, "not_found")))
        assertFalse(bolehBuangAntrean(gagal(401, "unauthorized")))
        assertFalse(bolehBuangAntrean(gagal(500, "internal")))
        assertFalse(bolehBuangAntrean(gagal(null, "io")))
    }

    @Test
    fun `403 dari WAF atau proxy TIDAK boleh membuang hasil kerja sehari`() {
        // Skenario nyata: 40 unit tertahan di antrean karena gudang tanpa
        // sinyal; begitu online, satu POST berisi 40 baris ditembakkan dan
        // Cloudflare (tridjaya.com memang di belakangnya) menjawab 403 HTML
        // karena WAF/rate-limit — tanpa pernah menyentuh origin. Kalau status
        // saja yang dilihat, keempat puluh baris itu dihapus dari Room dan
        // layarnya menuduh petugas "belum ditunjuk".
        assertFalse(bolehBuangAntrean(gagalNonAplikasi(403)))
        assertFalse(bolehBuangAntrean(gagalNonAplikasi(409)))
        assertFalse(bolehBuangAntrean(gagalNonAplikasi(422)))
        // Pelaporannya TETAP permanen — petugas harus melihat sebabnya,
        // bukan "tersimpan offline". Yang berubah cuma nasib barisnya.
        assertEquals(SifatGagal.PERMANEN, sifatGagal(403))
    }

    @Test
    fun `pesan 403 menyebut admin stok, pesan 401 menyebut masuk lagi`() {
        // Badan error server untuk 403 cuma berbunyi "Akses ditolak" — tak ada
        // yang bisa ditindaklanjuti dari kalimat itu sendirian.
        val tolak = pesanGagalKirim(AuthResult.Failure("forbidden", "Akses ditolak", 403))
        assertTrue(tolak, tolak.contains("Akses ditolak"))
        assertTrue(tolak, tolak.contains("admin stok"))

        val sesi = pesanGagalKirim(AuthResult.Failure("unauthorized", "Sesi tidak valid", 401))
        assertTrue(sesi, sesi.contains("masuk lagi"))

        // Status lain: pesan server apa adanya, tanpa karangan.
        assertEquals(
            "Periode opname tidak valid",
            pesanGagalKirim(AuthResult.Failure("validation_error", "Periode opname tidak valid", 400))
        )
    }
}
