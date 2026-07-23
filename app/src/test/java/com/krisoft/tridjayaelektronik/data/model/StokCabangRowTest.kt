package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StokCabangRowTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decode row PascalCase dari respons GS nyata`() {
        val raw = """
            {"No":1,"Kode":"TE-9102","Merk":"DUBBS","Nama":"1 SET ACCU DUBSS 6-EVF-45.5 \r\n",
             "Stok":1,"Harga":0,"Gambar":null,"kondisi":"deadstock","Kategori":"BATERAI",
             "umurHari":46223,"kodeCabang":"1-01","kodeDealer":"D-01","Tipe":"1 SET ACCU DUBSS 6-EVF-45.5"}
        """.trimIndent()
        val row = json.decodeFromString(StokCabangRow.serializer(), raw)
        assertEquals("TE-9102", row.kode)
        assertEquals("BATERAI", row.kategori)
        assertEquals("DUBBS", row.merk)
        assertEquals("1 SET ACCU DUBSS 6-EVF-45.5", row.tipe)
        assertEquals(0.0, row.harga)
        assertEquals(1, row.stok)
        // Decode mentah (bawa \r\n) — .trim() jadi tanggung jawab caller (UI), bukan model.
        assertEquals("1 SET ACCU DUBSS 6-EVF-45.5 \r\n", row.nama)
    }

    @Test
    fun `field hilang jatuh ke default kosong tanpa crash`() {
        val row = json.decodeFromString(StokCabangRow.serializer(), """{"Kode":"X"}""")
        assertEquals("X", row.kode)
        assertEquals("", row.nama)
        assertEquals(null, row.harga)
        assertEquals(null, row.stok)
    }

    @Test
    fun `decode wrapper items list dari data StokCabangData`() {
        val raw = """{"items":[{"Kode":"A","Nama":"B","Kategori":"C","Merk":"D","Tipe":"E","Harga":100,"Stok":5}]}"""
        val data = json.decodeFromString(StokCabangData.serializer(), raw)
        assertEquals(1, data.items.size)
        assertEquals("A", data.items[0].kode)
        assertEquals(100.0, data.items[0].harga)
    }

    @Test
    fun `wrapper items kosong bila key absen`() {
        val data = json.decodeFromString(StokCabangData.serializer(), """{}""")
        assertEquals(0, data.items.size)
    }
}
