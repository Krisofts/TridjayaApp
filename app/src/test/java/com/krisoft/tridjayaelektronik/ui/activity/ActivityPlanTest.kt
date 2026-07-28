package com.krisoft.tridjayaelektronik.ui.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityPlanTest {

    private fun item(id: String) = ACTIVITY_ITEMS.first { it.id == id }

    // ── Kartu antrian ───────────────────────────────────────────────────────

    @Test
    fun `kartu diurut jumlah menurun dan yang nol tetap tampil`() {
        val items = listOf(item("antrian_pdi"), item("aki_saya"))
        val cards = buildQueueCards(
            items = items,
            counts = mapOf(ActivitySource.DLV_PENDING_PDI to 0, ActivitySource.AKI_FORMS_MINE to 3),
            failed = emptySet(),
            effectiveRoles = setOf("pdi"),
        )
        assertEquals(listOf("aki_saya", "antrian_pdi"), cards.map { it.item.id })
        assertEquals(0, cards.last().count)
    }

    @Test
    fun `sumber gagal ditandai dan tak dianggap nol`() {
        val cards = buildQueueCards(
            items = listOf(item("antrian_pdi")),
            counts = emptyMap(),
            failed = setOf(ActivitySource.DLV_PENDING_PDI),
            effectiveRoles = setOf("pdi"),
        )
        assertTrue(cards.single().failed)
        assertEquals(null, cards.single().count)
    }

    @Test
    fun `kartu gagal tidak menjatuhkan kartu lain`() {
        val cards = buildQueueCards(
            items = listOf(item("antrian_pdi"), item("aki_saya")),
            counts = mapOf(ActivitySource.AKI_FORMS_MINE to 2),
            failed = setOf(ActivitySource.DLV_PENDING_PDI),
            effectiveRoles = setOf("pdi"),
        )
        assertEquals(2, cards.size)
        assertEquals(2, cards.first { it.item.id == "aki_saya" }.count)
    }

    @Test
    fun `tugas antar disembunyikan saat kosong untuk non-driver`() {
        val items = listOf(item("tugas_antar"))
        val sales = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 0), emptySet(), setOf("sales"))
        assertTrue(sales.isEmpty())
        val salesPunyaJob = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 1), emptySet(), setOf("sales"))
        assertEquals(1, salesPunyaJob.size)
        val driver = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 0), emptySet(), setOf("driver"))
        assertEquals(1, driver.size)
    }

    // ── Tugas harian ────────────────────────────────────────────────────────

    @Test
    fun `absen pulang baru muncul setelah check-in`() {
        val items = listOf(item("absen_masuk"), item("absen_pulang"))
        val belum = buildDailyTasks(items, checkInAt = null, checkOutAt = null, leadsToday = 0)
        assertEquals(listOf("absen_masuk"), belum.map { it.item.id })

        val sudahMasuk = buildDailyTasks(
            items, checkInAt = "2026-07-28 07:58:00", checkOutAt = null, leadsToday = 0
        )
        assertEquals(listOf("absen_masuk", "absen_pulang"), sudahMasuk.map { it.item.id })
        assertTrue(sudahMasuk.first().done)
        assertFalse(sudahMasuk.last().done)
        assertEquals("07:58", sudahMasuk.first().detail)
    }

    @Test
    fun `prospek selesai bila ada lead hari ini`() {
        val items = listOf(item("prospek"))
        assertFalse(buildDailyTasks(items, null, null, leadsToday = 0).single().done)
        val ada = buildDailyTasks(items, null, null, leadsToday = 2).single()
        assertTrue(ada.done)
        assertEquals("2 lead hari ini", ada.detail)
    }

    @Test
    fun `item coming soon tak dihitung sebagai penyebut progres`() {
        val items = listOf(item("absen_masuk"), item("raport"))
        val tasks = buildDailyTasks(items, checkInAt = "2026-07-28 08:00:00", checkOutAt = null, leadsToday = 0)
        assertEquals("1/1", dailyProgressLabel(tasks))
        assertEquals("SEGERA", tasks.first { it.item.id == "raport" }.detail)
    }

    // ── Lead hari ini ───────────────────────────────────────────────────────

    @Test
    fun `hitung lead hari ini milik user`() {
        val leads = listOf(
            "2026-07-28T09:00:00" to "u1",
            "2026-07-28T10:00:00" to "u2",
            "2026-07-27T09:00:00" to "u1",
            "2026-07-28T11:00:00" to null, // cache lama tanpa createdBy
        )
        assertEquals(2, leadsCreatedTodayBy(leads, userId = "u1", todayIso = "2026-07-28"))
        // Tanpa identitas (profil belum termuat) → hitung semua yang hari ini.
        assertEquals(3, leadsCreatedTodayBy(leads, userId = null, todayIso = "2026-07-28"))
    }

    // ── Counter SPK harian ──────────────────────────────────────────────────

    @Test
    fun `counter spk reset saat ganti hari`() {
        assertEquals("2026-07-28" to 1, spkCounterAfterIncrement("2026-07-27", 5, "2026-07-28"))
        assertEquals("2026-07-28" to 6, spkCounterAfterIncrement("2026-07-28", 5, "2026-07-28"))
        assertEquals("2026-07-28" to 1, spkCounterAfterIncrement(null, 0, "2026-07-28"))
    }
}
