package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSpkParityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun `item body serialize field baru pakai key camelCase`() {
        val item = CreateDeliveryItemBody(
            kodeBarang = "TE-1", namaBarang = "AC", kategori = "AC", merk = "AQUA", tipe = "X",
            hargaOtr = 1000.0, pembayaran1 = 500.0, angsuran = 250.0, tenor = 12,
            komisiSales = 50.0, komisiKbk = 60.0, noHpKbk = "0812", orderSource = "kbk",
            kbkBrokerKode = "BR1", kbkBrokerNama = "Broker Satu"
        )
        val s = json.encodeToString(CreateDeliveryItemBody.serializer(), item)
        listOf("pembayaran1", "angsuran", "tenor", "komisiSales", "komisiKbk",
               "noHpKbk", "orderSource", "kbkBrokerKode", "kbkBrokerNama").forEach {
            assertTrue("key $it hilang: $s", s.contains("\"$it\""))
        }
    }

    @Test
    fun `header body serialize sosmed + mapUrl camelCase`() {
        val body = CreateDeliveryBody(
            customerName = "Budi", customerPhone = "0812",
            customerMapUrl = "http://maps", sosmedTiktok = "@budi",
            sosmedFacebook = "budi.fb", sosmedInstagram = "budi.ig",
            items = emptyList()
        )
        val s = json.encodeToString(CreateDeliveryBody.serializer(), body)
        listOf("customerMapUrl", "sosmedTiktok", "sosmedFacebook", "sosmedInstagram").forEach {
            assertTrue("key $it hilang: $s", s.contains("\"$it\""))
        }
    }

    @Test
    fun `broker option decode kode+nama`() {
        val data = json.decodeFromString(BrokerListData.serializer(),
            """{"items":[{"kode":"BR1","nama":"Broker Satu"}]}""")
        assertEquals(1, data.items.size)
        assertEquals("BR1", data.items[0].kode)
        assertEquals("Broker Satu", data.items[0].nama)
    }

    @Test
    fun `serial registry decode serialNumber`() {
        val data = json.decodeFromString(SerialListData.serializer(),
            """{"items":[{"serialNumber":"SN123","kodeBarang":"TE-1"}]}""")
        assertEquals(1, data.items.size)
        assertEquals("SN123", data.items[0].serialNumber)
    }
}
