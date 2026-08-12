package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Progressive disclosure kartu barang SPK (2026-08-12).
 *
 * Kartu barang kini menyembunyikan blok Kredit/COD/Diskon/KBK sampai pemicunya
 * diketuk. Yang diuji di sini BUKAN tampilannya (itu Compose), melainkan satu
 * invarian yang membuat penyembunyian itu aman:
 *
 * > **Tak boleh ada field tersembunyi yang masih bisa melahirkan pesan di
 * > [SpkItemDraft.issues].**
 *
 * Kalau invarian itu pecah, gejalanya bukan error melainkan tombol "Catat
 * Penjualan" yang tak mempan sambil menunjuk tanda merah yang tak ada di layar
 * — persis kelas kegagalan yang paling lama dicari orang.
 */
class SpkProgressiveDisclosureTest {

    private fun draft() = newSpkItemDraft(
        StokCabangRow(
            kode = "TE-1", nama = "AC AQUA 1PK", kategori = "AC", merk = "AQUA",
            tipe = "1PK", harga = 2_500_000.0, stok = 3,
        )
    )

    // ── Blok diskon: kapan ia terlihat ───────────────────────────────────────

    @Test
    fun `blok diskon tertutup di barang baru`() {
        assertFalse(draft().blokDiskonTerlihat)
    }

    @Test
    fun `pemicu diskon membuka blok walau belum ada yang diketik`() {
        // Kalau syaratnya cuma "nominal terisi", blok yang baru dibuka langsung
        // menutup dirinya lagi sebelum sempat diisi.
        assertTrue(draft().copy(diskonDibuka = true).blokDiskonTerlihat)
    }

    @Test
    fun `isian diskon memaksa blok terlihat walau pemicunya mati`() {
        val d = draft()
        assertTrue(d.copy(diskon = "50000").blokDiskonTerlihat)
        assertTrue(d.copy(alasanDiskon = "promo").blokDiskonTerlihat)
        assertTrue(d.copy(accDiskon = "Feby").blokDiskonTerlihat)
        assertTrue(d.copy(buktiDiskonUrl = "/uploads/delivery/a.jpg").blokDiskonTerlihat)
    }

    /**
     * Keadaan paling berbahaya: diskon terisi tanpa alasan (= ada issue) TAPI
     * pemicunya mati. Bloknya wajib tetap terlihat, kalau tidak pesan "Alasan
     * diskon wajib diisi" menunjuk field yang tak dirender.
     */
    @Test
    fun `diskon berisu tanpa pemicu tetap terlihat`() {
        val d = draft().copy(diskon = "50000", diskonDibuka = false)
        assertTrue(d.issues().any { it.contains("Alasan") })
        assertTrue(d.blokDiskonTerlihat)
    }

    @Test
    fun `tanpaDiskon mengosongkan seluruh isian dan menutup blok`() {
        val terisi = draft().copy(
            diskonDibuka = true, diskon = "50000", alasanDiskon = "promo",
            accDiskon = "Feby", buktiDiskonUrl = "/uploads/delivery/a.jpg",
        )
        val bersih = terisi.tanpaDiskon()
        assertEquals("", bersih.diskon)
        assertEquals("", bersih.alasanDiskon)
        assertEquals("", bersih.accDiskon)
        assertEquals("", bersih.buktiDiskonUrl)
        assertFalse(bersih.diskonDibuka)
        assertFalse(bersih.blokDiskonTerlihat)
        assertTrue(bersih.issues().isEmpty())
    }

    // ── Invarian: mematikan pemicu tak meninggalkan issue tersembunyi ─────────

    /**
     * Tiap cabang di bawah adalah SALINAN PERSIS `copy(...)` yang dijalankan
     * chip pemicu di `SpkItemCard`. Kalau salah satu chip nanti berhenti
     * mengosongkan sesuatu, test ini yang gagal — bukan sales di lapangan.
     */
    @Test
    fun `mematikan pemicu kredit tak menyisakan issue fincoy`() {
        val kredit = draft().copy(paymentType = "credit", fincoy = FINCOY_LAINNYA, preOrderId = "PO-9")
        assertTrue(kredit.issues().isNotEmpty())
        val cash = kredit.copy(paymentType = "cash", fincoy = "", fincoyLain = "", preOrderId = "", poPhotoUrl = "")
        assertTrue(cash.issues().isEmpty())
    }

    @Test
    fun `mematikan pemicu COD tak menyisakan issue metode COD`() {
        val cod = draft().copy(driverTerimaUang = true)
        assertTrue(cod.issues().any { it.contains("Metode COD") })
        val mati = cod.copy(driverTerimaUang = false, codPaymentMode = "", codDpAmount = "")
        assertTrue(mati.issues().isEmpty())
    }

    @Test
    fun `mematikan pemicu KBK tak menyisakan issue broker`() {
        val kbk = draft().copy(orderSource = "kbk", komisiKbk = "25000", noHpKbk = "0812")
        assertTrue(kbk.issues().any { it.contains("Broker") })
        val mati = kbk.copy(
            orderSource = "sales", kbkBrokerKode = "", kbkBrokerNama = "",
            komisiKbk = "", noHpKbk = "",
        )
        assertTrue(mati.issues().isEmpty())
        // Komisi ikut dibuang supaya nilai lama tak muncul lagi tanpa diminta
        // kalau pemicunya dinyalakan lagi nanti.
        assertEquals("", mati.komisiKbk)
        assertEquals("", mati.noHpKbk)
    }

    /**
     * Yang TIDAK boleh ikut disembunyikan: harga, qty, dan serial. Ketiganya
     * selalu dirender, jadi issue-nya selalu punya field yang bisa ditunjuk.
     */
    @Test
    fun `issue harga qty serial tak bergantung pemicu apa pun`() {
        val d = draft()
        assertTrue(d.copy(hargaOtr = "").issues().any { it.contains("Harga") })
        assertTrue(d.copy(qty = "0").issues().any { it.contains("Qty") })
        assertTrue(d.copy(qty = "2", serialNumber = "SN1").issues().any { it.contains("Serial") })
    }

    // ── Ringkasan kartu tertutup ─────────────────────────────────────────────

    /**
     * Kartu tertutup adalah SATU-SATUNYA tempat pemicu yang aktif masih
     * terlihat — kalau ia diam, sales harus membuka sepuluh kartu satu per satu
     * untuk tahu barang mana yang berdiskon atau COD.
     */
    @Test
    fun `summaryLine menyebut pemicu yang aktif`() {
        val polos = draft().summaryLine()
        assertTrue(polos.contains("Cash"))
        assertFalse(polos.contains("Diskon"))
        assertFalse(polos.contains("COD"))
        assertFalse(polos.contains("KBK"))

        val ramai = draft().copy(
            diskon = "50000", alasanDiskon = "promo",
            driverTerimaUang = true, codPaymentMode = "full",
            orderSource = "kbk", kbkBrokerKode = "BR1", kbkBrokerNama = "B Satu",
            pdiRequired = false,
        ).summaryLine()
        assertTrue(ramai.contains("Diskon"))
        assertTrue(ramai.contains("COD"))
        assertTrue(ramai.contains("KBK"))
        // "PDI mandiri", BUKAN "Tanpa PDI" — tak ada lagi rute melewati PDI.
        assertTrue(ramai.contains("PDI mandiri"))
        assertFalse(ramai.contains("Tanpa PDI"))
    }

    /** Diskon nol (kolomnya dibuka tapi tak diisi) bukan "barang berdiskon". */
    @Test
    fun `blok diskon terbuka tanpa nominal tak muncul di ringkasan`() {
        assertFalse(draft().copy(diskonDibuka = true).summaryLine().contains("Diskon"))
        assertFalse(draft().copy(diskon = "0").summaryLine().contains("Diskon"))
    }

    // ── Seksi mana yang dibuka saat submit ditolak ───────────────────────────

    private fun diPelanggan(
        pelanggan: String = "Budi Santoso",
        telepon: String = "081234567",
        nik: String = "",
        mapUrl: String = "",
        deliveryMethod: String = "driver",
    ) = spkBlockerDiPelanggan(pelanggan, telepon, nik, mapUrl, deliveryMethod)

    @Test
    fun `blocker pelanggan dikenali`() {
        assertTrue(diPelanggan(pelanggan = ""))
        assertTrue(diPelanggan(telepon = "0812"))
        assertTrue(diPelanggan(nik = "321234"))
        assertTrue(diPelanggan(deliveryMethod = "sales_delivery"))
    }

    @Test
    fun `data pelanggan lengkap = penyebabnya di kartu barang`() {
        assertFalse(diPelanggan())
        assertFalse(diPelanggan(nik = "3212345678901234"))
        assertFalse(diPelanggan(deliveryMethod = "sales_delivery", mapUrl = "https://maps.app.goo.gl/x"))
        assertFalse(diPelanggan(deliveryMethod = "self_pickup"))
    }

    /**
     * Ia menilai HANYA sisi pelanggan: SPK tanpa barang pun tak boleh membuat
     * kartu "1. Pelanggan" yang terbuka, karena yang harus diperbaiki ada di
     * kartu "2. Barang".
     */
    @Test
    fun `masalah di sisi barang tak diklaim sebagai masalah pelanggan`() {
        assertFalse(diPelanggan())
        assertEquals(
            "Tambah minimal 1 barang dari pencarian stok.",
            spkSubmitBlocker(
                pelanggan = "Budi Santoso", telepon = "081234567", nik = "", mapUrl = "",
                deliveryMethod = "driver", spkCabang = "D-01",
                itemsCount = 0, itemsValid = true, totalUnits = 0,
            ),
        )
    }
}
