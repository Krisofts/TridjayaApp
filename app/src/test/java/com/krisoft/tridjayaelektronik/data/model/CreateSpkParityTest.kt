package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSpkParityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /**
     * SALINAN PERSIS config `NetworkModule.json` — `encodeDefaults` memang tak
     * disebut di sana (default-nya `false`, dan itulah jebakannya). Dipakai
     * untuk field yang kegagalannya SENYAP: kalau instance ini membuangnya,
     * app juga membuangnya di jaringan sungguhan tanpa error apa pun.
     */
    private val jsonJaringan = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `item body serialize field baru pakai key camelCase`() {
        val item = CreateDeliveryItemBody(
            kodeBarang = "TE-1", namaBarang = "AC", kategori = "AC", merk = "AQUA", tipe = "X",
            hargaOtr = 1000.0, pembayaran1 = 500.0, angsuran = 250.0, tenor = 12,
            komisiKbk = 60.0, noHpKbk = "0812", orderSource = "kbk",
            kbkBrokerKode = "BR1", kbkBrokerNama = "Broker Satu"
        )
        val s = json.encodeToString(CreateDeliveryItemBody.serializer(), item)
        listOf("pembayaran1", "angsuran", "tenor", "komisiKbk",
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

    /**
     * Lokasi pembayaran (2026-08-12, migrasi 213) — kelas kegagalan yang
     * melahirkan berkas ini: `encodeDefaults = false` di `NetworkModule`
     * membuang field yang nilainya = default TANPA error apa pun. Karena
     * defaultnya `null` dan call site SELALU mengisi "asal"/"tujuan", nilainya
     * selalu berbeda dari default dan karena itu selalu terkirim.
     */
    @Test
    fun `lokasiPembayaran ter-serialize camelCase di TOP-LEVEL body`() {
        listOf("asal", "tujuan").forEach { nilai ->
            val body = CreateDeliveryBody(
                customerName = "Budi", customerPhone = "6285172083358",
                lokasiPembayaran = nilai, items = emptyList(),
            )
            // Dua config: yang umum di berkas ini DAN salinan persis config
            // jaringan. Yang kedua yang sebenarnya menjaga — kalau ia
            // membuang field ini, pilihan sales lenyap di produksi tanpa error.
            listOf("umum" to json, "jaringan" to jsonJaringan).forEach { (nama, j) ->
                val s = j.encodeToString(CreateDeliveryBody.serializer(), body)
                assertTrue("[$nama] key lokasiPembayaran hilang untuk '$nilai': $s", s.contains("\"lokasiPembayaran\""))
                assertTrue("[$nama] nilai '$nilai' tak ikut terkirim: $s", s.contains("\"lokasiPembayaran\":\"$nilai\""))
            }
        }
    }

    /** `null` memang harus ABSEN (server membacanya sbg perilaku lama "tujuan"),
     *  bukan terkirim sebagai `"lokasiPembayaran":null`. */
    @Test
    fun `lokasiPembayaran null tidak dikirim sama sekali`() {
        val body = CreateDeliveryBody(
            customerName = "Budi", customerPhone = "6285172083358", items = emptyList(),
        )
        val s = jsonJaringan.encodeToString(CreateDeliveryBody.serializer(), body)
        assertTrue("null seharusnya absen, bukan terkirim: $s", !s.contains("lokasiPembayaran"))
    }

    /** Field HEADER, bukan per barang — satu SPK dibayar di satu tempat. */
    @Test
    fun `lokasiPembayaran bukan field item body`() {
        val item = CreateDeliveryItemBody(
            kodeBarang = "TE-1", namaBarang = "AC", kategori = "AC", merk = "AQUA", tipe = "X",
            hargaOtr = 1000.0,
        )
        val s = json.encodeToString(CreateDeliveryItemBody.serializer(), item)
        assertTrue("lokasiPembayaran bocor ke items[]: $s", !s.contains("lokasiPembayaran"))
    }

    /** Server lama tak mengenal field ini; ketiganya harus tahan absen. */
    @Test
    fun `job decode tanpa field lokasi bayar tetap sah`() {
        val job = json.decodeFromString(DeliveryJobDto.serializer(), """{"id":"j1","kodeDealer":"D-03"}""")
        assertEquals(null, job.lokasiPembayaran)
        assertEquals(null, job.bayarDealerCode)
        assertEquals(null, job.bayarDealerName)
    }

    @Test
    fun `job decode field lokasi bayar camelCase`() {
        val job = json.decodeFromString(
            DeliveryJobDto.serializer(),
            """{"id":"j1","kodeDealer":"D-03","lokasiPembayaran":"asal",
               "bayarDealerCode":"D-01","bayarDealerName":"Pagaden"}""",
        )
        assertEquals("asal", job.lokasiPembayaran)
        assertEquals("D-01", job.bayarDealerCode)
        assertEquals("Pagaden", job.bayarDealerName)
    }

    @Test
    fun `paymentType tetap ter-serialize saat nilai default cash`() {
        val cash = CreateDeliveryItemBody(
            kodeBarang = "TE-1", namaBarang = "AC", kategori = "AC", merk = "AQUA", tipe = "X",
            hargaOtr = 1000.0
        ) // paymentType defaults to "cash"
        val s = json.encodeToString(CreateDeliveryItemBody.serializer(), cash)
        assertTrue("paymentType hilang saat cash: $s", s.contains("\"paymentType\""))
    }
}
