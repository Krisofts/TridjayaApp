package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cerminan `frontend/src/utils/__tests__/spkEdit.spec.ts` — aturan patch harus
 *  sama di kedua klien, kalau tidak koreksi dari HP dan dari web berbeda hasil. */
class SpkEditFieldsTest {

    private val job = DeliveryJobDto(
        id = "j1",
        kodePengiriman = "DLV-M5081A724-1u1",
        status = DeliveryStatusKey.PENDING_PDI,
        kodeBarang = "TE-9093",
        namaBarang = "SEPEDA LISTRIK SAIGE LATTE PRO RED",
        kategori = "SEPEDA LISTRIK",
        merk = "SAIGE",
        tipe = "LATTE PRO RED",
        warna = "grey",
        customerName = "EUIS SETIANAH",
        customerAddress = "kp. gardu",
        paymentType = "cash",
        hargaOtr = 4_199_000.0,
        diskon = 200_000.0,
    )

    @Test
    fun `angka bulat dicetak tanpa desimal supaya tak terbaca sebagai perubahan`() {
        val form = spkEditFormFromJob(job)
        assertEquals("4199000", form["hargaOtr"])
        assertNull(buildSpkEditPatch(form, job, "tak menyentuh apa pun"))
    }

    @Test
    fun `hanya field yang berubah yang dikirim`() {
        val form = spkEditFormFromJob(job) + mapOf(
            "kodeBarang" to "TE-9091",
            "tipe" to "LATTE PRO GREY",
        )
        val patch = buildSpkEditPatch(form, job, "salah varian warna")!!
        assertEquals(setOf("kodeBarang", "tipe", "alasan"), patch.keys)
        assertEquals("TE-9091", patch["kodeBarang"]!!.jsonPrimitive.content)
        assertEquals("salah varian warna", patch["alasan"]!!.jsonPrimitive.content)
    }

    @Test
    fun `spasi di ujung bukan perubahan`() {
        val form = spkEditFormFromJob(job) + ("merk" to "  SAIGE  ")
        assertNull(buildSpkEditPatch(form, job, "apa saja"))
    }

    @Test
    fun `angka dikirim sebagai angka bukan teks`() {
        val form = spkEditFormFromJob(job) + ("hargaOtr" to "4500000")
        val patch = buildSpkEditPatch(form, job, "harga salah ketik")!!
        assertNull(patch["hargaOtr"]!!.jsonPrimitive.isString.takeIf { it })
        assertEquals(4_500_000.0, patch["hargaOtr"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test
    fun `angka tak terbaca diabaikan, tak dikirim sebagai teks`() {
        val form = spkEditFormFromJob(job) + ("hargaOtr" to "empat juta")
        assertNull(buildSpkEditPatch(form, job, "apa saja"))
    }

    @Test
    fun `teks dikosongkan berarti perintah kosongkan kolom`() {
        val form = spkEditFormFromJob(job) + ("warna" to "")
        val patch = buildSpkEditPatch(form, job, "warna salah isi")!!
        assertEquals(setOf("warna", "alasan"), patch.keys)
        assertEquals("", patch["warna"]!!.jsonPrimitive.content)
    }

    @Test
    fun `diskon dan hargaTotal bukan field yang bisa disunting`() {
        val keys = SPK_EDIT_FIELDS.map { it.key }
        assertFalse("diskon" in keys)
        assertFalse("hargaTotal" in keys)
    }

    @Test
    fun `boleh disunting hanya sebelum PDI dan sebelum tercatat di GS`() {
        assertTrue(spkBolehDisunting(job))
        assertTrue(spkBolehDisunting(job.copy(status = DeliveryStatusKey.PENDING_DISCOUNT)))
        assertFalse(spkBolehDisunting(job.copy(noTransaksi = "INV-1")))
        for (s in listOf(
            DeliveryStatusKey.PENDING_SPK,
            DeliveryStatusKey.PENDING_DELIVERY_NOTE,
            DeliveryStatusKey.PENDING_SCHEDULING,
            DeliveryStatusKey.ASSIGNED,
            DeliveryStatusKey.IN_TRANSIT,
            DeliveryStatusKey.DELIVERED,
            DeliveryStatusKey.CANCELLED,
        )) {
            assertFalse(s, spkBolehDisunting(job.copy(status = s)))
        }
    }

    @Test
    fun `ambang alasan sama dengan server`() {
        assertFalse(spkEditAlasanValid("oops"))
        assertFalse(spkEditAlasanValid("     "))
        assertTrue(spkEditAlasanValid("salah varian"))
    }
}
