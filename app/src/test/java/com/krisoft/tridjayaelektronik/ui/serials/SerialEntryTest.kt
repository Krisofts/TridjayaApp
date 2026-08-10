package com.krisoft.tridjayaelektronik.ui.serials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pemasukan SN per unit (scan/ketik) di menu Input Serial Number.
 *
 * Diuji karena inilah jalur yang dijalankan sekali per unit fisik — puluhan kali
 * per produk — dan ketiga penolakannya kalau salah tidak memunculkan error,
 * hanya hasil kerja yang meleset: unit terhitung dua kali, atau unit yang sudah
 * terdata didaftarkan ulang.
 */
class SerialEntryTest {

    private fun terima(hasil: HasilTambahSerial): String {
        assertTrue("harusnya diterima, tapi: $hasil", hasil is HasilTambahSerial.Diterima)
        return (hasil as HasilTambahSerial.Diterima).serial
    }

    private fun tolak(hasil: HasilTambahSerial): String {
        assertTrue("harusnya ditolak, tapi: $hasil", hasil is HasilTambahSerial.Ditolak)
        return (hasil as HasilTambahSerial.Ditolak).alasan
    }

    @Test
    fun `serial baru diterima dan dinormalkan`() {
        val hasil = tambahSerial("  aqa-kr9vqcl001 ", emptyList(), emptySet())
        assertEquals("AQA-KR9VQCL001", terima(hasil))
    }

    @Test
    fun `serial kosong ditolak`() {
        assertTrue(tolak(tambahSerial("   ", emptyList(), emptySet())).contains("kosong"))
    }

    @Test
    fun `serial lebih dari 64 karakter ditolak`() {
        val panjang = "A".repeat(65)
        assertTrue(tolak(tambahSerial(panjang, emptyList(), emptySet())).contains("64"))
    }

    @Test
    fun `tepat 64 karakter masih diterima`() {
        // Batasnya `> 64`, bukan `>= 64` — cerminan `normalize_serial` Rust.
        val pas = "A".repeat(64)
        assertEquals(pas, terima(tambahSerial(pas, emptyList(), emptySet())))
    }

    @Test
    fun `duplikat dalam daftar yang sama ditolak`() {
        val alasan = tolak(tambahSerial("SN-1", listOf("SN-1"), emptySet()))
        assertTrue(alasan, alasan.contains("daftar ini"))
    }

    @Test
    fun `duplikat terdeteksi walau beda huruf besar-kecil`() {
        // Unit yang sama discan dua kali (barcode kecil, gudang gelap) harus
        // ketahuan SEKARANG, bukan lewat daftar `skipped` sesudah simpan.
        val alasan = tolak(tambahSerial(" sn-1 ", listOf("SN-1"), emptySet()))
        assertTrue(alasan, alasan.contains("daftar ini"))
    }

    @Test
    fun `serial yang sudah terdaftar ditolak dengan alasan berbeda`() {
        val alasan = tolak(tambahSerial("SN-9", emptyList(), setOf("SN-9")))
        assertTrue(alasan, alasan.contains("sudah terdaftar"))
    }

    @Test
    fun `sudah terdaftar diperiksa setelah normalisasi`() {
        val alasan = tolak(tambahSerial("sn-9", emptyList(), setOf("SN-9")))
        assertTrue(alasan, alasan.contains("sudah terdaftar"))
    }

    @Test
    fun `daftar ini diperiksa sebelum registry`() {
        // Kalau urutannya terbalik, unit yang barusan discan dua kali dilaporkan
        // "sudah terdaftar" — petugas mengira registry-nya yang bermasalah,
        // bukan tangannya yang men-scan dua kali.
        val alasan = tolak(tambahSerial("SN-1", listOf("SN-1"), setOf("SN-1")))
        assertTrue(alasan, alasan.contains("daftar ini"))
    }

    @Test
    fun `serial berbeda tetap diterima walau daftar sudah berisi`() {
        val hasil = tambahSerial("SN-2", listOf("SN-1"), setOf("SN-9"))
        assertEquals("SN-2", terima(hasil))
    }
}
