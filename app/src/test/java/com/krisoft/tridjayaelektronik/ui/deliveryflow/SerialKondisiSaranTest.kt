package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keputusan user 2026-08-09: unit ber-kondisi bermasalah **diperingatkan, tidak
 * diblokir** di picker serial SPK. Registry bisa telat diperbarui — unit repair
 * yang sudah selesai diperbaiki masih bertanda repair, dan memblokirnya akan
 * menghentikan penjualan barang yang fisiknya layak tanpa jalan keluar.
 *
 * Yang tetap harus dijaga: unit bermasalah tak boleh MENDESAK unit sehat keluar
 * dari lima saran yang tampil — sales cuma melihat yang tampil.
 */
class SerialKondisiSaranTest {

    private fun row(sn: String, kondisi: String? = null) =
        SerialRegistryRow(serialNumber = sn, kondisi = kondisi)

    @Test
    fun `kondisi null bukan bermasalah`() {
        // Ribuan baris registry hasil impor Excel belum pernah ditetapkan
        // kondisinya. Menandainya bermasalah = peringatan di hampir tiap SPK,
        // dan peringatan yang selalu menyala berhenti dibaca.
        assertFalse(row("A").bermasalah)
        assertFalse(row("A", "layak").bermasalah)
        assertTrue(row("A", "repair").bermasalah)
        assertTrue(row("A", "retur").bermasalah)
        assertTrue(row("A", "tidak_layak").bermasalah)
    }

    @Test
    fun `unit sehat naik ke atas sebelum daftar dipotong lima`() {
        val opsi = listOf(
            row("R1", "repair"), row("R2", "retur"), row("R3", "repair"),
            row("R4", "tidak_layak"), row("R5", "repair"), row("BAIK", "layak"),
        )
        val saran = serialUntukDisarankan(opsi, serialTerpilih = "")
        assertEquals("BAIK", saran.first().serialNumber)
        // Yang bermasalah TIDAK dibuang — cuma turun.
        assertEquals(6, saran.size)
    }

    @Test
    fun `serial yang sedang terpilih tak muncul lagi di saran`() {
        val saran = serialUntukDisarankan(listOf(row("A"), row("B")), serialTerpilih = "A")
        assertEquals(listOf("B"), saran.map { it.serialNumber })
    }

    @Test
    fun `urutan dari server terjaga di dalam kelompok yang sama`() {
        // sortedBy stabil: kelompok sehat tetap urut sesuai kiriman server
        // (kode barang lalu serial), bukan diacak.
        val saran = serialUntukDisarankan(
            listOf(row("A"), row("B"), row("C", "repair"), row("D")),
            serialTerpilih = "",
        )
        assertEquals(listOf("A", "B", "D", "C"), saran.map { it.serialNumber })
    }
}
