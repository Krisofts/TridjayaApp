package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengelompokan per-SPK + klasifikasi barang besar/kecil. Semua fungsi murni —
 * inilah lapisan yang menentukan tombol menyebut "N unit" yang benar dan unit
 * mana yang boleh lewat jalur PDI massal.
 */
class SpkBatchTest {

    private fun job(
        kode: String,
        id: String = kode,
        status: String = DeliveryStatusKey.PENDING_PDI,
        hargaOtr: Double? = null,
    ) = DeliveryJobDto(
        id = id,
        kodePengiriman = kode,
        status = status,
        hargaOtr = hargaOtr,
    )

    // ── prefix ───────────────────────────────────────────────────────────────

    @Test
    fun `prefix memotong di hubung terakhir`() {
        assertEquals("DLV-M9933140B", spkBatchPrefix("DLV-M9933140B-1u1"))
        assertEquals("DLV-M9933140B", spkBatchPrefix("DLV-M9933140B-12u3"))
    }

    /**
     * Kode enroll GS lama tak berpola `-{baris}u{seq}`. Memotong di hubung
     * terakhir (bukan regex) menjaga app tetap sepakat dengan `batch_prefix`
     * backend untuk kode-kode itu.
     */
    @Test
    fun `kode tanpa pola baris-unit tetap dipotong di hubung terakhir`() {
        assertEquals("GS-2026", spkBatchPrefix("GS-2026-0007"))
    }

    @Test
    fun `kode tanpa hubung dikembalikan apa adanya`() {
        assertEquals("TANPAHUBUNG", spkBatchPrefix("TANPAHUBUNG"))
    }

    /** Hubung di posisi 0 bukan pemisah batch — `substring(0,0)` = string kosong,
     *  yang akan menggabungkan SEMUA kode aneh ke satu grup hantu. */
    @Test
    fun `hubung di awal tidak menghasilkan prefix kosong`() {
        assertEquals("-X1", spkBatchPrefix("-X1"))
    }

    // ── grouping ─────────────────────────────────────────────────────────────

    @Test
    fun `unit satu SPK jadi satu grup, urutan kemunculan terjaga`() {
        val grup = groupJobsBySpk(
            listOf(
                job("DLV-B-1u1"), job("DLV-A-1u1"), job("DLV-B-2u1"), job("DLV-A-1u2"),
            )
        )
        assertEquals(2, grup.size)
        // B muncul duluan di masukan → tetap duluan, walau A lebih kecil abjadnya.
        assertEquals("DLV-B", grup[0].kode)
        assertEquals(listOf("DLV-B-1u1", "DLV-B-2u1"), grup[0].jobs.map { it.kodePengiriman })
        assertEquals("DLV-A", grup[1].kode)
        assertEquals(2, grup[1].jobs.size)
    }

    @Test
    fun `daftar kosong menghasilkan nol grup`() {
        assertTrue(groupJobsBySpk(emptyList()).isEmpty())
    }

    // ── barang besar / kecil ─────────────────────────────────────────────────

    @Test
    fun `di atas ambang besar, sama dengan ambang kecil`() {
        assertTrue(isBarangBesar(1_500_001.0, 1_500_000.0))
        assertFalse(isBarangBesar(1_500_000.0, 1_500_000.0))
        assertFalse(isBarangBesar(900_000.0, 1_500_000.0))
    }

    /**
     * Fail-closed. Harga tak diketahui TIDAK boleh jatuh ke jalur massal
     * tanpa checklist — cerminan `delivery/barang_besar.rs`.
     */
    @Test
    fun `harga tak diketahui atau nol dinilai besar`() {
        assertTrue(isBarangBesar(null, 1_500_000.0))
        assertTrue(isBarangBesar(0.0, 1_500_000.0))
        assertTrue(isBarangBesar(-5.0, 1_500_000.0))
    }

    /** Server lama tanpa `barangBesarThreshold` → app kembali ke PDI per unit. */
    @Test
    fun `ambang belum terbaca membuat semua barang besar`() {
        assertTrue(isBarangBesar(50_000.0, null))
        assertTrue(isBarangBesar(50_000.0, 0.0))
    }

    // ── kandidat pdi-kecil ───────────────────────────────────────────────────

    @Test
    fun `pdi kecil hanya memungut unit kecil yang masih pending_pdi`() {
        val kandidat = unitPdiKecil(
            listOf(
                job("DLV-A-1u1", id = "kecil", hargaOtr = 800_000.0),
                job("DLV-A-2u1", id = "besar", hargaOtr = 9_000_000.0),
                job("DLV-A-3u1", id = "kecil-tapi-sudah-lewat", hargaOtr = 800_000.0, status = DeliveryStatusKey.PENDING_SPK),
                job("DLV-A-4u1", id = "kecil-tertahan", hargaOtr = 800_000.0, status = DeliveryStatusKey.PENDING_PERBAIKAN),
                job("DLV-A-5u1", id = "tanpa-harga"),
            ),
            threshold = 1_500_000.0,
        )
        assertEquals(listOf("kecil"), kandidat.map { it.id })
    }
}
