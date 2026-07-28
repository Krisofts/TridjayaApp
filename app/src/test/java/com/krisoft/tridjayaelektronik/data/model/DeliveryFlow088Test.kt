package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryFlow088Test {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun `job dto decode field 088 dan pembiayaan`() {
        val raw = """
            {"id":"J1","kodePengiriman":"DLV-M1-1u1","status":"assigned",
             "driverTerimaUang":true,"driverTerimaNominal":150000.0,
             "consumerChatAt":"2026-07-22T08:00:00","cashPhotoUrl":null,
             "dpNet":500000.0,"pembayaran1":100000.0,"angsuran":250000.0,"tenor":12,
             "komisiSales":50000.0,"orderSource":"kbk","kbkBrokerNama":"Broker Satu",
             "sosmedTiktok":"@x","alasanDiskon":"promo"}
        """.trimIndent()
        val job = json.decodeFromString(DeliveryJobDto.serializer(), raw)
        assertEquals(true, job.driverTerimaUang)
        assertEquals(150000.0, job.driverTerimaNominal)
        assertEquals("2026-07-22T08:00:00", job.consumerChatAt)
        assertEquals(12, job.tenor)
        assertEquals("kbk", job.orderSource)
        assertEquals("Broker Satu", job.kbkBrokerNama)
    }

    @Test
    fun `job dto pre-088 driverTerimaUang null = penanda absen`() {
        val job = json.decodeFromString(DeliveryJobDto.serializer(), """{"id":"J1","kodePengiriman":"K","status":"assigned"}""")
        assertNull(job.driverTerimaUang)
        assertNull(job.consumerChatAt)
    }

    @Test
    fun `deliver body serialize checklist + cashPhotoUrl`() {
        val body = DeliverBody(
            photoUrl = "/uploads/x.jpg", reviewRating = 5,
            checklist = listOf(PdiChecklistItemBody(item = "Unit bersih", hasil = "ok")),
            cashPhotoUrl = "/uploads/cash.jpg"
        )
        val s = json.encodeToString(DeliverBody.serializer(), body)
        assertTrue(s.contains("\"checklist\""))
        assertTrue(s.contains("\"cashPhotoUrl\""))
        assertTrue(s.contains("\"Unit bersih\""))
    }

    @Test
    fun `item body serialize driverTerimaUang + nominal`() {
        val item = CreateDeliveryItemBody(
            kodeBarang = "K", namaBarang = "N", kategori = "C", merk = "M", tipe = "T",
            hargaOtr = 1.0, driverTerimaUang = true, driverTerimaNominal = 150000.0
        )
        val s = json.encodeToString(CreateDeliveryItemBody.serializer(), item)
        assertTrue(s.contains("\"driverTerimaUang\":true"))
        assertTrue(s.contains("\"driverTerimaNominal\""))
    }
}
