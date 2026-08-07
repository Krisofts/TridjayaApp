package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cerminan guard server `create_delivery` (2026-08-07): satu serial number
 * mengidentifikasi SATU unit fisik, jadi tak bisa mewakili `qty` unit.
 *
 * Sebelum perbaikan, nilai itu disalin apa adanya ke seluruh unit baris
 * (`for unit_seq in 1..=line.qty`), sehingga qty=3 melahirkan tiga job ber-SN
 * identik. Akibat hilirnya senyap: `mark_sold` menyaring
 * `delivery_job_id IS NULL`, jadi hanya unit PERTAMA yang tertaut registry —
 * dua unit sisanya terkirim tanpa jejak di `stock_serial_numbers`.
 */
class SpkSerialQtyTest {
    private fun draft(qty: String, serial: String) = newSpkItemDraft(
        StokCabangRow(
            kode = "TE-1", nama = "AC AQUA 1PK", kategori = "AC",
            merk = "AQUA", tipe = "1PK", harga = 2_500_000.0, stok = 10
        )
    ).copy(qty = qty, serialNumber = serial, hargaOtr = "2500000")

    private val pesan = "Serial hanya untuk qty 1"

    @Test
    fun `qty lebih dari satu dengan serial ditolak`() {
        val issues = draft("3", "SN-KEMBAR-1").issues()
        assertTrue("harus menolak: $issues", issues.any { it.contains(pesan) })
    }

    @Test
    fun `qty satu dengan serial diterima`() {
        val issues = draft("1", "SN-TUNGGAL-1").issues()
        assertFalse("tak boleh menolak: $issues", issues.any { it.contains(pesan) })
    }

    /**
     * Syaratnya menempel pada "serial diisi DAN qty>1", bukan pada qty>1 saja.
     * SPK qty banyak tanpa serial adalah alur normal — mewajibkan sesuatu
     * secara global adalah pola yang sudah mematikan klien di lapangan
     * (insiden foto aki 2026-07-25).
     */
    @Test
    fun `qty lebih dari satu tanpa serial tetap diterima`() {
        val issues = draft("3", "").issues()
        assertFalse("tak boleh menolak: $issues", issues.any { it.contains(pesan) })
    }

    @Test
    fun `serial berisi spasi saja tidak dianggap terisi`() {
        val issues = draft("5", "   ").issues()
        assertFalse("tak boleh menolak: $issues", issues.any { it.contains(pesan) })
    }
}
