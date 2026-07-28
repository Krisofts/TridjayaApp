package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [normalizeSerial] harus berperilaku PERSIS seperti `normalize_serial` di
 * `inventory-service/src/opname.rs`:
 *
 * ```rust
 * let sn = raw.trim().to_uppercase();
 * if sn.is_empty() || sn.chars().count() > 64 { None } else { Some(sn) }
 * ```
 *
 * Kalau kedua aturan bergeser sedikit saja, dua sisi memvonis duplikat secara berbeda:
 * app menolak serial yang server terima (petugas macet di gudang) atau meloloskan serial
 * yang server anggap sama (dua baris fisik untuk satu unit).
 */
class OpnameSerialNormalizeTest {

    @Test
    fun `spasi pinggir dibuang dan huruf dibesarkan`() {
        assertEquals("SN-A1B2", normalizeSerial("  sn-a1b2 "))
    }

    @Test
    fun `huruf kecil dan besar menghasilkan kunci yang sama`() {
        assertEquals(normalizeSerial("abc123"), normalizeSerial("ABC123"))
        assertEquals(normalizeSerial("AbC123"), normalizeSerial("aBc123"))
    }

    @Test
    fun `kosong dan hanya spasi ditolak`() {
        assertNull(normalizeSerial(""))
        assertNull(normalizeSerial("   "))
        // Rust `trim()` membuang seluruh whitespace Unicode, termasuk tab & newline hasil
        // scanner yang menambahkan Enter di akhir.
        assertNull(normalizeSerial("\t\n "))
    }

    @Test
    fun `scanner yang menambahkan enter tetap menghasilkan serial bersih`() {
        assertEquals("SN-007", normalizeSerial("sn-007\r\n"))
    }

    @Test
    fun `tepat 64 karakter diterima dan 65 ditolak`() {
        assertEquals(64, normalizeSerial("A".repeat(64))?.length)
        assertNull(normalizeSerial("A".repeat(65)))
    }

    @Test
    fun `panjang dihitung per karakter seperti chars count di rust bukan unit utf16`() {
        // Emoji = 1 char di Rust tapi 2 unit UTF-16 di Kotlin. Memakai `length` akan
        // menolak 33 emoji yang di server masih sah — beda vonis di dua sisi.
        val serial = "🔋".repeat(40) // 40 karakter, 80 unit UTF-16
        assertEquals(80, serial.length)
        assertEquals(serial.uppercase(), normalizeSerial(serial))
    }

    @Test
    fun `pembesaran huruf tidak mengikuti locale perangkat`() {
        // `uppercase()` memakai Locale.ROOT. Kalau ini jatuh ke locale default, HP
        // berbahasa Turki mengubah "i" jadi "İ" dan serialnya tak akan cocok di server.
        assertEquals("SIM-01", normalizeSerial("sim-01"))
    }

    @Test
    fun `alasan tolak server diterjemahkan untuk petugas`() {
        assertEquals("serial ini sudah discan di sesi ini", alasanTolakLabel("duplikat_dalam_sesi"))
        // Alasan yang belum dikenal tampil apa adanya, bukan hilang jadi pesan kosong.
        assertEquals("kode barang wajib diisi", alasanTolakLabel("kode barang wajib diisi"))
    }
}
