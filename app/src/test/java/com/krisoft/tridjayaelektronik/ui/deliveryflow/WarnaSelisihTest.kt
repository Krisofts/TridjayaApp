package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.WarnaSelisihDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarnaSelisihTest {

    @Test
    fun `server yang tak mengirim field tidak menampilkan apa pun`() {
        // Server lama tak mengenal field ini sama sekali, dan server baru
        // menghilangkannya saat warnanya cocok. Keduanya harus DIAM.
        assertNull(pesanWarnaSelisih(null))
    }

    @Test
    fun `bertentangan jadi peringatan dan menyebut unit mana yang akan keluar`() {
        val t = pesanWarnaSelisih(
            WarnaSelisihDto(jenis = "bertentangan", sku = "hitam", diketik = "merah"),
        )!!
        assertEquals(NadaSelisih.PERINGATAN, t.nada)
        assertTrue(t.judul.contains("hitam"))
        assertTrue(t.judul.contains("merah"))
        // Inti pesannya: yang menentukan barang keluar adalah KODE BARANG,
        // bukan kolom warna. Tanpa kalimat ini orang membetulkan kolom yang
        // salah lalu merasa beres, dan barang yang salah tetap keluar.
        assertTrue(t.penjelasan.contains("kode barangnya yang harus diganti"))
    }

    @Test
    fun `kolom kosong BUKAN peringatan`() {
        // 93 SPK di produksi ada di keadaan ini vs 25 yang benar-benar
        // bertentangan. Menyamakan nadanya menenggelamkan yang 25.
        val t = pesanWarnaSelisih(WarnaSelisihDto(jenis = "kolom_kosong", sku = "hijau"))!!
        assertEquals(NadaSelisih.INFO, t.nada)
        assertTrue(t.judul.contains("hijau"))
    }

    @Test
    fun `jenis yang tak dikenal DIAM, bukan setengah jadi`() {
        // APK yang tertinggal versi tak boleh menampilkan peringatan aneh di
        // layar orang yang sedang bekerja hanya karena server menambah jenis
        // baru. Ini alasan `jenis` dibiarkan String, bukan enum.
        assertNull(pesanWarnaSelisih(WarnaSelisihDto(jenis = "jenis_masa_depan", sku = "hitam")))
    }

    @Test
    fun `sku kosong DIAM`() {
        assertNull(pesanWarnaSelisih(WarnaSelisihDto(jenis = "bertentangan", sku = "")))
        assertNull(pesanWarnaSelisih(WarnaSelisihDto(jenis = "bertentangan", sku = null)))
    }

    @Test
    fun `bertentangan tanpa diketik tetap aman dirender`() {
        val t = pesanWarnaSelisih(WarnaSelisihDto(jenis = "bertentangan", sku = "hitam"))!!
        assertEquals(NadaSelisih.PERINGATAN, t.nada)
        assertTrue(t.judul.contains("hitam"))
    }
}
