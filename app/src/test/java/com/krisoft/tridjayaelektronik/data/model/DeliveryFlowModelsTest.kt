package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryFlowModelsTest {

    @Test
    fun `category dto parses requiresAkiForm`() {
        val c = Json { ignoreUnknownKeys = true }.decodeFromString(DeliveryCategoryDto.serializer(),
            """{"kategori":"SEPEDA LISTRIK","requiresAkiForm":true,"aktif":true}""")
        assertTrue(c.requiresAkiForm)
    }
}
