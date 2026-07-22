package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryFlowModelsTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun `create body serializes customerMapUrl and item fincoy`() {
        val body = CreateDeliveryBody(
            customerName = "Budi", customerPhone = "0812345678",
            customerMapUrl = "https://maps.app.goo.gl/xyz",
            items = listOf(CreateDeliveryItemBody(
                kodeBarang = "K1", namaBarang = "Sepeda", kategori = "SEPEDA LISTRIK",
                merk = "UWINFLY", tipe = "T3", hargaOtr = 5000000.0,
                paymentType = "credit", fincoy = "FIF"
            ))
        )
        val s = json.encodeToString(body)
        assertTrue(s.contains("\"customerMapUrl\":\"https://maps.app.goo.gl/xyz\""))
        assertTrue(s.contains("\"fincoy\":\"FIF\""))
    }
}
