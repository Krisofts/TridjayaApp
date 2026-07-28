package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Syarat kirim form SPK. Yang paling gampang lolos diam-diam: link Maps pada
 * metode "Sales Antar Sendiri" — backend fail-soft (job jatuh ke antrian
 * Delivery Control) sehingga sales tak pernah tahu SPK-nya tak jadi miliknya.
 */
class SpkSubmitBlockerTest {

    private fun blocker(
        pelanggan: String = "Budi Santoso",
        telepon: String = "081234567",
        nik: String = "",
        mapUrl: String = "",
        deliveryMethod: String = "driver",
        spkCabang: String = "D-01",
        itemsCount: Int = 1,
        itemsValid: Boolean = true,
        totalUnits: Int = 1,
    ) = spkSubmitBlocker(
        pelanggan, telepon, nik, mapUrl, deliveryMethod, spkCabang,
        itemsCount, itemsValid, totalUnits,
    )

    @Test
    fun `form minimal yang benar lolos`() {
        assertNull(blocker())
        assertNull(blocker(nik = "3212345678901234"))
        assertNull(blocker(deliveryMethod = "self_pickup"))
    }

    @Test
    fun `sales antar sendiri tanpa link Maps ditolak`() {
        assertEquals(
            "Isi Link Lokasi Maps — wajib untuk metode Sales Antar Sendiri.",
            blocker(deliveryMethod = "sales_delivery"),
        )
        assertNull(blocker(deliveryMethod = "sales_delivery", mapUrl = "https://maps.app.goo.gl/x"))
    }

    @Test
    fun `metode lain tidak menuntut link Maps`() {
        assertNull(blocker(deliveryMethod = "driver"))
        assertNull(blocker(deliveryMethod = "self_pickup"))
    }

    @Test
    fun `syarat lama tetap berlaku`() {
        assertNotNull(blocker(pelanggan = "Bu"))
        assertNotNull(blocker(telepon = "0812"))
        assertNotNull(blocker(nik = "321234"))
        assertNotNull(blocker(spkCabang = ""))
        assertNotNull(blocker(itemsCount = 0))
        assertNotNull(blocker(totalUnits = 201))
        assertNotNull(blocker(totalUnits = 0))
        assertNotNull(blocker(itemsValid = false))
    }

    @Test
    fun `pesan pelanggan didahulukan supaya urutan perbaikannya wajar`() {
        assertEquals(
            "Lengkapi nama & No. HP pelanggan.",
            blocker(pelanggan = "", deliveryMethod = "sales_delivery", itemsCount = 0),
        )
    }
}
