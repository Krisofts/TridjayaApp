package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.AkiFormDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cerminan `frontend/src/utils/akiKelengkapan.test.ts`. Aturan yang sama harus
 * berlaku di web dan HP: dokumen yang ditandatangani konsumen dicetak dari web,
 * yang dibaca petugas di lapangan dari HP.
 */
class AkiKelengkapanTest {

    private fun form(
        merk: String = "YUASA",
        kapasitas: String? = "12V 20AH",
        pcs: Int = 4,
        status: String = "approved",
        charger: Boolean = false,
        spion: Boolean = false,
        keterangan: String? = null,
    ) = AkiFormDto(
        merkTipe = merk,
        kapasitas = kapasitas,
        jumlahPcs = pcs,
        approvalStatus = status,
        ambilCharger = charger,
        ambilKacaSpion = spion,
        jumlahKeterangan = keterangan,
    )

    /** Inti aturannya: barang yang belum/tak pernah disetujui TIDAK boleh
     *  muncul di dokumen penjualan. */
    @Test
    fun `form pending dan rejected diabaikan`() {
        val hasil = kelengkapanDariAkiForms(
            listOf(form(status = "pending"), form(status = "rejected", merk = "GS ASTRA"))
        )
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `baterai merk plus kapasitas sama digabung jadi satu baris`() {
        val hasil = kelengkapanDariAkiForms(listOf(form(pcs = 4), form(pcs = 2)))
        assertEquals(1, hasil.size)
        assertEquals("BATERAI YUASA 12V 20AH", hasil[0].label)
        assertEquals(6, hasil[0].qty)
    }

    @Test
    fun `baterai beda kapasitas tetap dua baris`() {
        val hasil = kelengkapanDariAkiForms(
            listOf(form(kapasitas = "12V 20AH"), form(kapasitas = "48V 12AH"))
        )
        assertEquals(2, hasil.size)
        assertEquals(listOf("BATERAI YUASA 12V 20AH", "BATERAI YUASA 48V 12AH"), hasil.map { it.label })
    }

    /** Keterangan yang berbeda antar-sumber jadi klaim yang tak lagi benar
     *  untuk baris gabungan — harus hilang, bukan diambil salah satu. */
    @Test
    fun `keterangan dibuang saat dua sumbernya berbeda`() {
        val hasil = kelengkapanDariAkiForms(
            listOf(form(keterangan = "1 set = 4 pcs"), form(keterangan = "setengah set"))
        )
        assertEquals(1, hasil.size)
        assertNull(hasil[0].catatan)
    }

    @Test
    fun `keterangan dipertahankan saat sumbernya sama`() {
        val hasil = kelengkapanDariAkiForms(
            listOf(form(keterangan = "1 set = 4 pcs"), form(keterangan = "1 set = 4 pcs"))
        )
        assertEquals("1 set = 4 pcs", hasil[0].catatan)
    }

    /** Satu form = satu pengambilan untuk satu unit, jadi charger/spion
     *  dihitung dari BANYAKNYA form, bukan dari `jumlahPcs`. */
    @Test
    fun `charger dan spion dihitung per form yang menandainya`() {
        val hasil = kelengkapanDariAkiForms(
            listOf(
                form(charger = true, spion = true),
                form(charger = true),
                form(status = "pending", charger = true),
            )
        )
        assertEquals(2, hasil.first { it.label == "CHARGER" }.qty)
        assertEquals(1, hasil.first { it.label == "KACA SPION" }.qty)
    }

    @Test
    fun `merk kosong dilewati`() {
        val hasil = kelengkapanDariAkiForms(listOf(form(merk = "   ")))
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `kapasitas kosong tak meninggalkan spasi ganda di label`() {
        val hasil = kelengkapanDariAkiForms(listOf(form(kapasitas = null)))
        assertEquals("BATERAI YUASA", hasil[0].label)
    }

    @Test
    fun `daftar kosong menghasilkan nol baris`() {
        assertTrue(kelengkapanDariAkiForms(emptyList()).isEmpty())
    }
}
