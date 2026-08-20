package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gerbang kirim "Konfirmasi Pembayaran Diterima".
 *
 * Yang dijaga di sini adalah KESEPAKATAN DENGAN SERVER, bukan tampilan tombol:
 * `record_kasir_setoran` (inventory-service `delivery.rs`) menolak
 * `nominal_diterima <= 0.0`, dan klien pernah memakai `>= 0` sehingga tombolnya
 * aktif di Rp 0. Penyimpangan itu tak menghasilkan error klien apa pun — foto
 * ter-upload lebih dulu, lalu server menjawab 400 dan pekerjaan kasir tak pernah
 * selesai. Tak ada pemeriksa kompiler lintas repo untuk kontrak ini; test inilah
 * penggantinya.
 */
class SetoranKasirGateTest {

    // ── nominal: cerminan `nominal_diterima <= 0.0` di server ────────────────

    @Test
    fun `nol ditolak walau foto sudah ada - server menolaknya 400`() {
        val gate = setoranKasirGate("0", adaFoto = true)
        assertFalse(gate.bolehKirim)
        assertEquals("Isi nominal yang diterima", gate.label)
    }

    @Test
    fun `nol berangka banyak juga ditolak`() {
        assertFalse(setoranKasirGate("000", adaFoto = true).bolehKirim)
    }

    @Test
    fun `kosong ditolak - kolomnya wajib`() {
        assertFalse(setoranKasirGate("", adaFoto = true).bolehKirim)
    }

    @Test
    fun `nominal wajar diterima`() {
        val gate = setoranKasirGate("5750000", adaFoto = true)
        assertTrue(gate.bolehKirim)
        assertEquals("Konfirmasi Pembayaran", gate.label)
    }

    @Test
    fun `satu rupiah diterima - ambangnya lebih besar dari nol, bukan angka lain`() {
        assertTrue(setoranKasirGate("1", adaFoto = true).bolehKirim)
    }

    // ── foto ─────────────────────────────────────────────────────────────────

    @Test
    fun `tanpa foto ditolak walau nominal sudah benar`() {
        val gate = setoranKasirGate("5750000", adaFoto = false)
        assertFalse(gate.bolehKirim)
        assertEquals("Ambil foto bukti dulu", gate.label)
    }

    /**
     * Urutan pesan, bukan selera: memotret jauh lebih lama daripada mengetik
     * angka, jadi menagihnya belakangan membuat kasir mengetik nominal lalu baru
     * diberi tahu harus keluar memotret.
     */
    @Test
    fun `keduanya kurang - foto ditagih lebih dulu`() {
        assertEquals("Ambil foto bukti dulu", setoranKasirGate("", adaFoto = false).label)
    }

    // ── masukan yang tak wajar tak boleh lolos jadi kiriman ───────────────────

    @Test
    fun `bukan angka ditolak - bukan dianggap nol lalu dikirim`() {
        assertFalse(setoranKasirGate("abc", adaFoto = true).bolehKirim)
    }

    /**
     * `MoneyTextField` menyaring ke digit saja, jadi ini pertahanan berlapis:
     * `toDoubleOrNull` menerima "Infinity" dan "-1", dan keduanya tak boleh
     * pernah sampai ke `nominalDiterima`.
     */
    @Test
    fun `negatif dan tak hingga ditolak`() {
        assertFalse(setoranKasirGate("-1", adaFoto = true).bolehKirim)
        assertFalse(setoranKasirGate("Infinity", adaFoto = true).bolehKirim)
        assertFalse(setoranKasirGate("NaN", adaFoto = true).bolehKirim)
    }
}
