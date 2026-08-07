package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Angka yang dibaca approver sebelum memutuskan. Bug yang dikunci di sini:
 * kartu HP dulu menjumlahkan `value` mentah, sehingga SPK dengan baris qty 2
 * menampilkan SEPARUH potongan dan berselisih dengan papan web untuk SPK yang
 * SAMA.
 */
class DiskonPotonganTest {

    private fun req(
        id: String = "d1",
        baris: Int? = 1,
        unitIds: List<String> = listOf("j1"),
        value: Double = 500_000.0,
        discountType: String = "amount",
        sebelum: Double? = 13_000_000.0,
        sesudah: Double? = 12_500_000.0,
        createdAt: String = "2026-08-07T01:00:00Z",
        pengaju: String? = "Administrator",
        accOleh: String? = null,
    ) = DiscountRequestDto(
        id = id, spkBatchKode = "DLV-M11112222", baris = baris, deliveryJobIds = unitIds,
        discountType = discountType, value = value, hargaSebelum = sebelum, hargaSesudah = sesudah,
        createdAt = createdAt, requestedByName = pengaju, accOleh = accOleh,
    )

    @Test
    fun `baris qty 2 dipotong dua kali - inilah bug angkanya`() {
        val d = req(unitIds = listOf("j1", "j2"))
        assertEquals(2, unitTerdampak(d))
        // `value` mentah = 500 rb; potongan sebenarnya 2 unit x 500 rb.
        assertEquals(1_000_000.0, potonganPengajuan(d), 0.01)
    }

    @Test
    fun `diskon persen dihitung dari selisih harga, bukan angka persennya`() {
        val d = req(discountType = "percent", value = 5.0, sebelum = 10_000_000.0, sesudah = 9_500_000.0)
        assertEquals(500_000.0, potonganPengajuan(d), 0.01)
    }

    @Test
    fun `pengajuan warisan tanpa baris dihitung satu unit, bukan seluruh SPK`() {
        // deliveryJobIds = SELURUH unit SPK saat baris null (batch_job_ids).
        val d = req(baris = null, unitIds = listOf("j1", "j2", "j3"))
        assertEquals(1, unitTerdampak(d))
        assertEquals(500_000.0, potonganPengajuan(d), 0.01)
    }

    @Test
    fun `harga null tidak ditebak dari value`() {
        assertEquals(0.0, potonganPengajuan(req(sebelum = null)), 0.01)
        assertEquals(0.0, potonganPengajuan(req(sesudah = null)), 0.01)
    }

    @Test
    fun `total SPK menjumlah seluruh baris`() {
        val a = req(id = "a", baris = 1, unitIds = listOf("j1", "j2"))
        val b = req(id = "b", baris = 2, unitIds = listOf("j3"), sebelum = 4_000_000.0, sesudah = 3_800_000.0)
        assertEquals(1_200_000.0, totalPotonganSpk(listOf(a, b)), 0.01)
        assertEquals(0.0, totalPotonganSpk(emptyList()), 0.01)
    }

    @Test
    fun `kartu diurut menurut baris, bukan urutan pengajuan dari server`() {
        val b3 = req(id = "c", baris = 3, createdAt = "2026-08-07T03:00:00Z")
        val b1 = req(id = "a", baris = 1, createdAt = "2026-08-07T01:00:00Z")
        val warisan = req(id = "w", baris = null)
        // Server mengirim created_at DESC.
        val hasil = urutPengajuanSpk(listOf(b3, warisan, b1))
        assertEquals(listOf("a", "c", "w"), hasil.map { it.id })
    }

    // ── Kartu ringkas (2026-08-07) ───────────────────────────────────────────

    @Test
    fun `ringkasHarga menggantikan dua baris harga dengan satu`() {
        val d = req(unitIds = listOf("j1", "j2"), sebelum = 7_000_000.0, sesudah = 6_700_000.0)
        assertEquals("2 unit · 7.000.000 → 6.700.000", ringkasHarga(d))
    }

    @Test
    fun `harga tak lengkap tidak ditebak - cuma jumlah unitnya`() {
        assertEquals("1 unit", ringkasHarga(req(sebelum = null)))
        assertEquals("1 unit", ringkasHarga(req(sesudah = null)))
    }

    // "Acc oleh" ikut baris harga, bukan baris `InfoLine` sendiri: sebagai
    // InfoLine ia merentang selebar kartu sehingga keluar dari indentasi
    // barangnya dan terbaca seperti keterangan milik SPK.
    @Test
    fun `acc menempel di baris harga hanya saat diminta`() {
        val d = req(sebelum = 3_500_000.0, sesudah = 3_350_000.0, accOleh = "Pak Kiryanto")
        assertEquals("3.500.000 → 3.350.000", ringkasHarga(d))
        assertEquals(
            "3.500.000 → 3.350.000 · acc Pak Kiryanto",
            ringkasHarga(d, sertakanAcc = true),
        )
    }

    @Test
    fun `acc kosong tidak meninggalkan pemisah menggantung`() {
        assertEquals(
            "3.500.000 → 3.350.000",
            ringkasHarga(req(sebelum = 3_500_000.0, sesudah = 3_350_000.0, accOleh = "  "), sertakanAcc = true),
        )
        assertEquals(
            "3.500.000 → 3.350.000",
            ringkasHarga(req(sebelum = 3_500_000.0, sesudah = 3_350_000.0, accOleh = null), sertakanAcc = true),
        )
    }

    // Harga tak lengkap TAPI acc ada: acc tetap ikut, tak boleh hilang hanya
    // karena angkanya tak lengkap.
    @Test
    fun `acc tetap ikut walau harga tak lengkap`() {
        assertEquals(
            "acc Bu Dinda",
            ringkasHarga(req(sebelum = null, accOleh = "Bu Dinda"), sertakanAcc = true),
        )
    }

    @Test
    fun `ribuan sama persis dengan rupiah tanpa prefiks`() {
        assertEquals("0", ribuan(null))
        assertEquals("999", ribuan(999.0))
        assertEquals("7.000.000", ribuan(7_000_000.0))
    }

    @Test
    fun `pengaju sama di semua barang naik ke header`() {
        val p = listOf(req(id = "a", baris = 1), req(id = "b", baris = 2))
        assertEquals("Administrator", nilaiSeragam(p) { it.requestedByName })
    }

    @Test
    fun `pengaju berbeda tetap di barisnya masing-masing`() {
        val p = listOf(req(id = "a", baris = 1), req(id = "b", baris = 2, pengaju = "UJI Sales"))
        assertEquals(null, nilaiSeragam(p) { it.requestedByName })
    }

    @Test
    fun `satu nilai kosong membatalkan keseragaman - jangan mengklaim se-SPK`() {
        // Kalau 1 dari 3 barang tak punya accOleh, menaikkannya ke header
        // membuat approver mengira SELURUH SPK sudah di-acc orang itu.
        val p = listOf(
            req(id = "a", baris = 1, accOleh = "Pak Budi"),
            req(id = "b", baris = 2, accOleh = "Pak Budi"),
            req(id = "c", baris = 3, accOleh = null),
        )
        assertEquals(null, nilaiSeragam(p) { it.accOleh?.trim()?.ifBlank { null } })
        // Blank dianggap kosong oleh pemanggil (fungsi ini cuma membandingkan).
        val q = listOf(req(id = "a", accOleh = "  "), req(id = "b", accOleh = "  "))
        assertEquals(null, nilaiSeragam(q) { it.accOleh?.trim()?.ifBlank { null } })
    }

    @Test
    fun `daftar kosong atau satu barang`() {
        assertEquals(null, nilaiSeragam(emptyList()) { it.requestedByName })
        assertEquals("Administrator", nilaiSeragam(listOf(req())) { it.requestedByName })
    }
}
