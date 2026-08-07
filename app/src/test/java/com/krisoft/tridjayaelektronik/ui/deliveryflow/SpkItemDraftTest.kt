package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class SpkItemDraftTest {
    private fun draft() = newSpkItemDraft(
        StokCabangRow(kode = "TE-1", nama = "AC AQUA 1PK \r\n", kategori = "AC", merk = "AQUA", tipe = "1PK", harga = 2500000.0, stok = 3)
    )

    @Test
    fun `newSpkItemDraft prefill dari picker + trim nama`() {
        val d = draft()
        assertEquals("TE-1", d.kodeBarang)
        assertEquals("AC AQUA 1PK", d.namaBarang)
        assertEquals("2500000", d.hargaOtr)
        assertEquals(3, d.stokTersedia)
        assertEquals("1", d.qty)
        assertEquals("cash", d.paymentType)
    }

    @Test
    fun `issues kosong utk draft default valid`() {
        assertTrue(draft().issues().isEmpty())
    }

    @Test
    fun `harga nol = issue`() {
        assertTrue(draft().copy(hargaOtr = "").issues().any { it.contains("Harga") })
    }

    @Test
    fun `diskon tanpa alasan = issue, dengan alasan bersih`() {
        val d = draft().copy(diskon = "50000")
        assertTrue(d.issues().any { it.contains("Alasan") })
        assertTrue(d.copy(alasanDiskon = "promo").issues().isEmpty())
    }

    /** Menyebut pemberi acc = mengaku diskonnya sudah disetujui DI LUAR
     *  sistem; approver tak punya cara memeriksa klaim itu selain fotonya. */
    @Test
    fun `acc diskon di luar sistem wajib berfoto bukti`() {
        val berdiskon = draft().copy(diskon = "50000", alasanDiskon = "promo")
        val pesan = "Foto bukti acc wajib kalau diskon sudah di-acc di luar sistem"

        assertTrue(berdiskon.copy(accDiskon = "Feby").issues().contains(pesan))
        assertTrue(
            berdiskon.copy(accDiskon = "Feby", buktiDiskonUrl = "/uploads/delivery/a.jpg").issues().isEmpty()
        )
        // Spasi saja bukan "terisi" — `toItemBody` juga memangkasnya jadi null.
        assertTrue(berdiskon.copy(accDiskon = "   ").issues().isEmpty())
        // Diskon biasa (menunggu approval DI DALAM sistem) tak butuh foto.
        assertTrue(berdiskon.issues().isEmpty())
        // Tanpa diskon tak ada pengajuan sama sekali → tak ada yang ditagih.
        assertTrue(draft().copy(accDiskon = "Feby").issues().isEmpty())
    }

    @Test
    fun `credit wajib fincoy, lainnya wajib teks`() {
        val c = draft().copy(paymentType = "credit")
        assertTrue(c.issues().any { it.contains("leasing", ignoreCase = true) || it.contains("Fincoy") })
        assertTrue(c.copy(fincoy = "Adira Finance").issues().isEmpty())
        val lain = c.copy(fincoy = FINCOY_LAINNYA)
        assertTrue(lain.issues().isNotEmpty())
        assertTrue(lain.copy(fincoyLain = "BCA Finance").issues().isEmpty())
    }

    @Test
    fun `kbk wajib broker`() {
        val k = draft().copy(orderSource = "kbk")
        assertTrue(k.issues().any { it.contains("Broker") })
        assertTrue(k.copy(kbkBrokerKode = "BR1", kbkBrokerNama = "B Satu").issues().isEmpty())
    }

    /** Sejak COD Full/DP (2026-07-25) yang wajib dipilih adalah METODE-nya;
     *  nominal tagihan dihitung server, dan DP wajib diisi + harus < total. */
    @Test
    fun `COD wajib pilih metode, DP wajib nominal di bawah total`() {
        val t = draft().copy(driverTerimaUang = true)
        assertTrue(t.issues().any { it.contains("Metode COD") })
        assertTrue(t.copy(codPaymentMode = "full").issues().isEmpty())
        assertTrue(t.copy(codPaymentMode = "dp").issues().any { it.contains("Jumlah DP") })
        assertTrue(t.copy(codPaymentMode = "dp", codDpAmount = "150000").issues().isEmpty())
    }

    /**
     * Guard per-baris = cerminan server pasca-2026-08-07: hanya `qty < 1` plus
     * stok. Batas ATAS 10 unit sengaja TIDAK ada di sini — ia batas se-SPK,
     * ditegakkan `spkSubmitBlocker` dengan teks server ("Maksimal 10 unit per
     * SPK"). Dua pesan untuk satu aturan membuat sales memperbaiki hal yang
     * salah.
     */
    @Test
    fun `qty di bawah 1 atau melebihi stok = issue, batas atas milik SPK bukan baris`() {
        assertTrue(draft().copy(qty = "4").issues().any { it.contains("Qty") })   // stok 3
        assertTrue(draft().copy(qty = "0").issues().any { it.contains("Qty") })
        assertTrue(draft().copy(qty = "3").issues().isEmpty())
        val tanpaStok = draft().copy(stokTersedia = null)
        assertTrue(tanpaStok.copy(qty = "11").issues().isEmpty())
        assertTrue(tanpaStok.copy(qty = "0").issues().any { it.contains("Qty") })
    }

    @Test
    fun `toItemBody conditional kredit dan kbk`() {
        val cash = draft().copy(diskon = "50000", alasanDiskon = "promo")
            .toItemBody("D-01", "1-01")
        assertEquals("D-01", cash.kodeDealer)
        assertEquals(50000.0, cash.diskon)
        assertNull(cash.fincoy); assertNull(cash.dpNet); assertNull(cash.orderSource)
        val kbkCredit = draft().copy(
            paymentType = "credit", fincoy = FINCOY_LAINNYA, fincoyLain = "BCA F",
            dpNet = "500000", tenor = "12", orderSource = "kbk",
            kbkBrokerKode = "BR1", kbkBrokerNama = "B", komisiKbk = "25000"
        ).toItemBody("D-01", "1-01")
        assertEquals("BCA F", kbkCredit.fincoy)
        assertEquals(500000.0, kbkCredit.dpNet)
        assertEquals(12, kbkCredit.tenor)
        assertEquals("kbk", kbkCredit.orderSource)
        assertEquals(25000.0, kbkCredit.komisiKbk)
    }

    @Test
    fun `summaryLine ringkas`() {
        val s = draft().copy(qty = "2").summaryLine()
        assertTrue(s.contains("AC AQUA 1PK")); assertTrue(s.contains("2"))
    }
}
