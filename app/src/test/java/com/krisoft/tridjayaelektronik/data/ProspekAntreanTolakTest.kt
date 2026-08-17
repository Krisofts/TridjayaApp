package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antrean create prospek: membedakan "belum ada sinyal" dari "server menolak
 * isinya".
 *
 * Sampai 2026-08-15 keduanya diperlakukan sama — barisnya tetap `pendingSync`
 * dan layar tetap berlabel "ANTRE". Untuk kegagalan sementara itu benar; untuk
 * vonis permanen itu berarti sales diberi tahu prospeknya tersimpan padahal
 * server tak pernah menerimanya, jadi prospek itu tak ikut menghitung target
 * harian dan aktivitas raportnya tak pernah otomatis disetujui. Terukur di nginx
 * produksi: 390 penolakan 400 pada `POST /api/prospek-harian` dalam tujuh hari
 * (8–14 Agt 2026), seluruhnya dari app, naik 40/hari → 93/hari.
 */
class ProspekAntreanTolakTest {

    private fun gagal(code: String, message: String, status: Int?) =
        AuthResult.Failure(code, message, status)

    @Test
    fun `400 dari service kita adalah vonis permanen`() {
        assertTrue(
            "nomor yang ditolak server tak akan diterima berapa kali pun diulang",
            vonisPermanenProspek(gagal("validation_error", "Nomor WhatsApp tidak valid", 400))
        )
    }

    @Test
    fun `403 HTML dari Cloudflare BUKAN vonis permanen`() {
        // Badan errornya tak bisa diurai jadi ErrorBody, sehingga `code` jatuh ke
        // "http_403" — penanda bahwa yang menjawab bukan service kita. Menghukum
        // baris antrean karena WAF/rate-limit berarti memvonis "ditolak" seluruh
        // prospek yang baru diketik sales, padahal keadaannya sementara.
        assertFalse(
            vonisPermanenProspek(gagal("http_403", "Terjadi kesalahan (403)", 403))
        )
    }

    @Test
    fun `sesi mati bukan vonis atas isi prospeknya`() {
        assertFalse(
            "401 sah lagi sesudah login ulang — barisnya harus tetap mengantre",
            vonisPermanenProspek(gagal("unauthorized", "Sesi berakhir", 401))
        )
    }

    @Test
    fun `server sekarat dan jaringan putus tetap mengantre`() {
        assertFalse(vonisPermanenProspek(gagal("gateway_error", "Bad gateway", 502)))
        assertFalse(
            "tak pernah sampai server — status null, arah aman ke sementara",
            vonisPermanenProspek(gagal("network_error", "Tidak bisa terhubung", null))
        )
    }

    @Test
    fun `kalimatnya menyebut sebab DAN langkah berikutnya`() {
        val pesan = pesanTolakProspek(gagal("validation_error", "Nomor WhatsApp tidak valid", 400))
        assertTrue("sebabnya harus terbawa apa adanya", pesan.startsWith("Nomor WhatsApp tidak valid."))
        assertTrue("harus menyebut bahwa prospeknya belum masuk", pesan.contains("BELUM masuk ke server"))
        assertTrue("harus menyebut akibatnya ke target", pesan.contains("target"))
        assertTrue("harus menyebut apa yang bisa dilakukan sales", pesan.contains("input ulang"))
    }

    @Test
    fun `pesan server kosong tidak menghasilkan kalimat menggantung`() {
        val pesan = pesanTolakProspek(gagal("validation_error", "   ", 400))
        assertEquals(
            "Ditolak server. Prospek ini BELUM masuk ke server dan tidak terhitung di target " +
                "harianmu — input ulang dengan data yang benar.",
            pesan
        )
    }
}
