package com.krisoft.tridjayaelektronik.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Helper murni layar KPI. [shiftPeriode] ditulis manual karena `java.time`
 * HARAM di jalur yang jalan di API 24/25 (minSdk 24, tanpa desugaring) — unit
 * test JVM tidak bisa menangkap `NoClassDefFoundError` itu, jadi yang dijaga di
 * sini adalah aritmetikanya, bukan ketiadaan `java.time`.
 */
class KpiModelsTest {

    @Test
    fun `geser bulan menyeberang tahun`() {
        assertEquals("2026-06", shiftPeriode("2026-07", -1))
        assertEquals("2026-08", shiftPeriode("2026-07", 1))
        assertEquals("2025-12", shiftPeriode("2026-01", -1))
        assertEquals("2027-01", shiftPeriode("2026-12", 1))
        assertEquals("2025-07", shiftPeriode("2026-07", -12))
    }

    @Test
    fun `periode tak berbentuk dikembalikan apa adanya`() {
        assertEquals("", shiftPeriode("", -1))
        assertEquals("2026", shiftPeriode("2026", 1))
        assertEquals("2026-13", shiftPeriode("2026-13", 1))
    }

    @Test
    fun `kekurangan menuju target`() {
        val item = KpiItemDto(target = 50.0, actual = 32.0)
        assertEquals(18.0, kpiKekurangan(item)!!, 1e-9)
        // Sudah tercapai / lewat → tak ada yang perlu dikejar.
        assertNull(kpiKekurangan(item.copy(actual = 50.0)))
        assertNull(kpiKekurangan(item.copy(actual = 61.0)))
        // Belum dinilai = 0, bukan "tak ada target" — justru paling perlu dikejar.
        assertEquals(50.0, kpiKekurangan(item.copy(actual = null))!!, 1e-9)
        // Target 0 tak bisa dikejar (pembagi achievement juga 0 di backend).
        assertNull(kpiKekurangan(item.copy(target = 0.0)))
    }

    @Test
    fun `angka bulat tampil tanpa desimal, sisanya koma`() {
        assertEquals("50", formatKpiNumber(50.0))
        assertEquals("0", formatKpiNumber(0.0))
        assertEquals("82,35", formatKpiNumber(82.345))
        // Nol di belakang dibuang: 82,30 → 82,3.
        assertEquals("82,3", formatKpiNumber(82.3))
        assertEquals("-5", formatKpiNumber(-5.0))
        assertEquals("-2,5", formatKpiNumber(-2.5))
    }
}
