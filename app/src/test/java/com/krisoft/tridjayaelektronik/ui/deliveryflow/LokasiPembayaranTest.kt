package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan klien lokasi pembayaran SPK (2026-08-12, migrasi 213).
 *
 * Yang diuji di sini adalah NILAI YANG DIKIRIM, bukan tampilannya: kesalahan di
 * lapisan ini memindahkan uang satu SPK ke antrian kasir cabang yang salah
 * tanpa menimbulkan satu pun error.
 */
class LokasiPembayaranTest {

    private fun draft(cod: Boolean = false, mode: String = ""): SpkItemDraft = SpkItemDraft(
        kodeBarang = "TE-1", namaBarang = "AC", kategori = "AC", merk = "AQUA", tipe = "X",
        hargaOtr = "1000000",
        driverTerimaUang = cod, codPaymentMode = mode,
    )

    // ── nilai efektif dari server ────────────────────────────────────────────

    @Test
    fun `null dibaca tujuan - itu perilaku SPK lama`() {
        assertEquals(LOKASI_BAYAR_TUJUAN, lokasiBayarEfektif(null))
    }

    @Test
    fun `nilai asing dibaca tujuan - APK lama tak boleh menolak SPK`() {
        assertEquals(LOKASI_BAYAR_TUJUAN, lokasiBayarEfektif("gudang"))
        assertEquals(LOKASI_BAYAR_TUJUAN, lokasiBayarEfektif(""))
    }

    @Test
    fun `asal dibaca asal - termasuk yang berspasi atau huruf besar`() {
        assertEquals(LOKASI_BAYAR_ASAL, lokasiBayarEfektif("asal"))
        assertEquals(LOKASI_BAYAR_ASAL, lokasiBayarEfektif(" ASAL "))
    }

    // ── kontrol form SPK baru ────────────────────────────────────────────────

    @Test
    fun `cabang asal berbeda dari tujuan - pilihan tampil, default asal`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_ASAL,
            kodeDealerAsal = "D-01", namaDealerAsal = "Pagaden",
            spkCabang = "D-03", semuaCodFull = false,
        )
        assertTrue(k.bolehPilih)
        assertEquals(LOKASI_BAYAR_ASAL, k.nilai)
        assertEquals("Pagaden", k.namaAsal)
        assertEquals("Soklat", k.namaTujuan)
        assertNull(k.catatan)
    }

    @Test
    fun `sales memilih tujuan - nilai itu yang dikirim`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_TUJUAN,
            kodeDealerAsal = "D-01", namaDealerAsal = "Pagaden",
            spkCabang = "D-03", semuaCodFull = false,
        )
        assertTrue(k.bolehPilih)
        assertEquals(LOKASI_BAYAR_TUJUAN, k.nilai)
    }

    @Test
    fun `pilihan user yang tak sah jatuh ke asal, bukan dikirim apa adanya`() {
        val k = lokasiBayarKontrol(
            pilihanUser = "gudang",
            kodeDealerAsal = "D-01", namaDealerAsal = "Pagaden",
            spkCabang = "D-03", semuaCodFull = false,
        )
        assertEquals(LOKASI_BAYAR_ASAL, k.nilai)
    }

    @Test
    fun `cabang asal sama dengan tujuan - tanpa pilihan, kirim asal`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_TUJUAN,
            kodeDealerAsal = "D-01", namaDealerAsal = "Pagaden",
            spkCabang = "D-01", semuaCodFull = false,
        )
        assertFalse(k.bolehPilih)
        assertEquals(LOKASI_BAYAR_ASAL, k.nilai)
        assertEquals("Bayar di: Pagaden", k.catatan)
    }

    @Test
    fun `semua barang COD full - dipaksa tujuan tanpa pilihan`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_ASAL,
            kodeDealerAsal = "D-01", namaDealerAsal = "Pagaden",
            spkCabang = "D-03", semuaCodFull = true,
        )
        assertFalse(k.bolehPilih)
        assertEquals(LOKASI_BAYAR_TUJUAN, k.nilai)
        assertTrue(k.catatan.orEmpty().contains("Soklat"))
    }

    @Test
    fun `cabang asal tak diketahui - tanpa pilihan, kirim tujuan`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_ASAL,
            kodeDealerAsal = null, namaDealerAsal = null,
            spkCabang = "D-03", semuaCodFull = false,
        )
        assertFalse(k.bolehPilih)
        assertEquals(LOKASI_BAYAR_TUJUAN, k.nilai)
    }

    @Test
    fun `cabang asal kosong menang atas semua-COD - keduanya tetap tujuan`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_ASAL,
            kodeDealerAsal = "  ", namaDealerAsal = "  ",
            spkCabang = "D-03", semuaCodFull = true,
        )
        assertFalse(k.bolehPilih)
        assertEquals(LOKASI_BAYAR_TUJUAN, k.nilai)
    }

    @Test
    fun `cabang tujuan belum dipilih - tak merender apa pun`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_ASAL,
            kodeDealerAsal = "D-01", namaDealerAsal = "Pagaden",
            spkCabang = "", semuaCodFull = false,
        )
        assertFalse(k.bolehPilih)
        assertNull(k.catatan)
    }

    @Test
    fun `nama cabang asal jatuh ke kodenya kalau konteks tak bawa nama`() {
        val k = lokasiBayarKontrol(
            pilihanUser = LOKASI_BAYAR_ASAL,
            kodeDealerAsal = "D-01", namaDealerAsal = null,
            spkCabang = "D-03", semuaCodFull = false,
        )
        assertEquals("D-01", k.namaAsal)
    }

    @Test
    fun `nama cabang tujuan dari peta dealer, kode asing jadi kodenya sendiri`() {
        assertEquals("Cikampek", namaCabangTujuan("D-10"))
        assertEquals("Cikampek", namaCabangTujuan("d-10"))
        assertEquals("D-99", namaCabangTujuan("D-99"))
    }

    // ── "semua COD full" ─────────────────────────────────────────────────────

    @Test
    fun `SPK kosong bukan SPK COD`() {
        assertFalse(semuaCodFullPayment(emptyList()))
    }

    @Test
    fun `semua barang COD full`() {
        assertTrue(semuaCodFullPayment(listOf(draft(true, "full"), draft(true, "full"))))
    }

    @Test
    fun `SPK campuran tetap bukan semua-COD - pilihan harus tetap tampil`() {
        assertFalse(semuaCodFullPayment(listOf(draft(true, "full"), draft(false))))
        // COD DP bukan COD full: sebagian uangnya dibayar di kasir.
        assertFalse(semuaCodFullPayment(listOf(draft(true, "full"), draft(true, "dp"))))
    }

    // ── tampilan sisi baca ───────────────────────────────────────────────────

    @Test
    fun `nama cabang bayar hanya dari server`() {
        assertEquals("Pagaden", namaCabangBayar(DeliveryJobDto(bayarDealerName = "Pagaden")))
        assertNull(namaCabangBayar(DeliveryJobDto(bayarDealerName = "  ")))
        // Server lama: tak ada yang dipajang, dan app TIDAK menebak dari kodeDealer.
        assertNull(namaCabangBayar(DeliveryJobDto(kodeDealer = "D-01", dealerName = "Pagaden")))
    }

    @Test
    fun `badge hanya saat bayar di luar cabang stok`() {
        val luar = DeliveryJobDto(kodeDealer = "D-03", bayarDealerCode = "D-01", bayarDealerName = "Pagaden")
        assertEquals("Pagaden", badgeBayarDiLuarCabangStok(luar))

        val sama = DeliveryJobDto(kodeDealer = "D-01", bayarDealerCode = "D-01", bayarDealerName = "Pagaden")
        assertNull(badgeBayarDiLuarCabangStok(sama))

        // Server lama (tanpa kode bayar) -> diam, bukan menebak.
        assertNull(badgeBayarDiLuarCabangStok(DeliveryJobDto(kodeDealer = "D-03", bayarDealerName = "Pagaden")))
    }
}
