package com.krisoft.tridjayaelektronik.ui.attendance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate tombol Absen Masuk (geofence) — cerminan `pastikan_di_area_toko` di
 * kinerja-service.
 *
 * Kelas kesalahan yang dijaga di sini bukan "teks kurang enak": sampai
 * 2026-08-15 layar menulis "absen perlu review" untuk orang di luar area dan
 * membiarkan tombolnya hidup, padahal server MENOLAK dan tak ada baris yang
 * lahir. Nginx produksi 4–15 Agustus 2026 mencatat 314 check-in dijawab 400,
 * seluruhnya penolakan geofence itu.
 *
 * Dua arah sama-sama diuji, karena masing-masing merugikan orang yang berbeda:
 * mengunci terlalu longgar mengembalikan janji palsu, mengunci terlalu ketat
 * MENGHENTIKAN orang yang sebenarnya berhak absen (mis. sedang bertugas di
 * cabang lain, yang server memang izinkan).
 */
class GateAbsenMasukTest {

    @Test
    fun `di dalam area lolos tanpa kalimat apa pun`() {
        val gate = gateAbsenMasuk(
            inArea = true,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = 40
        )
        assertTrue(gate.boleh)
        assertNull("yang sudah di area tak perlu diberi tahu apa-apa", gate.alasan)
    }

    @Test
    fun `lokasi belum terbaca fail-open`() {
        val gate = gateAbsenMasuk(
            inArea = null,
            daftarCabangLengkap = true,
            namaCabangTerdekat = null,
            jarakM = null
        )
        assertTrue("tak tahu posisi bukan berarti di luar area", gate.boleh)
        assertNull(gate.alasan)
    }

    /**
     * Server menilai terhadap SELURUH cabang. Kalau app cuma memegang satu titik
     * (server lama yang hanya mengirim `geofence` tunggal), "di luar" bisa berarti
     * "sedang di cabang sebelah" — dan server akan menerimanya. Mengunci di sini
     * berarti app menghentikan orang yang berhak.
     */
    @Test
    fun `di luar area tapi daftar cabang belum lengkap tidak mengunci`() {
        val gate = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = false,
            namaCabangTerdekat = "Pagaden",
            jarakM = 3_200
        )
        assertTrue("daftar sepotong tak boleh mengunci tombol", gate.boleh)
        assertNotNull("tapi orangnya tetap harus diberi tahu", gate.alasan)
        assertTrue(
            "harus menyebut kemungkinan bertugas di cabang lain: ${gate.alasan}",
            gate.alasan!!.contains("cabang lain")
        )
    }

    @Test
    fun `di luar area dengan daftar lengkap mengunci dan menyebut jarak serta cabang`() {
        val gate = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = 3_200
        )
        assertFalse(gate.boleh)
        val alasan = gate.alasan!!
        assertTrue("APA yang ditolak: $alasan", alasan.contains("Absen masuk hanya bisa"))
        // Koma, bukan titik: `formatDistance` memakai Locale("in","ID").
        assertTrue("KENAPA — jaraknya: $alasan", alasan.contains("3,2 km"))
        assertTrue("KENAPA — dari mana: $alasan", alasan.contains("Pagaden"))
        assertTrue("LANGKAH berikutnya: $alasan", alasan.contains("perbarui lokasi"))
    }

    /**
     * "Minta admin membetulkan titik cabang" hanya masuk akal dari dekat. Kalau
     * disodorkan pada jarak kilometer, seluruh cabang belajar menyalahkan setelan
     * untuk lokasi yang memang salah — dan admin dikirimi laporan palsu.
     */
    @Test
    fun `saran memperbaiki titik cabang hanya muncul dari dekat`() {
        val dekat = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = AMBANG_DUGAAN_TITIK_SALAH_M
        )
        assertFalse(dekat.boleh)
        assertTrue(
            "tepat di ambang masih menyarankan: ${dekat.alasan}",
            dekat.alasan!!.contains("admin")
        )

        val jauh = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = AMBANG_DUGAAN_TITIK_SALAH_M + 1
        )
        assertFalse(jauh.boleh)
        assertFalse(
            "lewat ambang tak boleh menyarankan menyalahkan admin: ${jauh.alasan}",
            jauh.alasan!!.contains("admin")
        )
    }

    /**
     * Nama cabang / jarak bisa saja tak ada (cabang tanpa nama di config, jarak
     * gagal dihitung). Kalimatnya tetap harus utuh dan tetap mengunci — bukan
     * "Kamu null dari null".
     */
    @Test
    fun `tanpa nama cabang dan tanpa jarak kalimatnya tetap utuh`() {
        val gate = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "   ",
            jarakM = null
        )
        assertFalse(gate.boleh)
        val alasan = gate.alasan!!
        assertFalse("tak boleh bocor null: $alasan", alasan.contains("null"))
        assertTrue(alasan.contains("di luar area toko"))
        assertTrue(alasan.contains("perbarui lokasi"))
    }
}
