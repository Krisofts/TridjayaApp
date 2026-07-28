package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertEquals
import org.junit.Test

class BranchRegionsTest {

    @Test
    fun `13 cabang total, dikelompok jadi 2 region`() {
        val groups = BranchRegions.cabangOptionsByRegion()
        assertEquals(2, groups.size)
        assertEquals(13, groups.sumOf { it.cabang.size })
    }

    @Test
    fun `D-06 dan D-07 masuk region Manado, sisanya Jawa`() {
        val groups = BranchRegions.cabangOptionsByRegion()
        val manado = groups.first { it.region == BranchRegions.REGION_MANADO }
        val jawa = groups.first { it.region == BranchRegions.REGION_JAWA }
        assertEquals(setOf("D-06", "D-07"), manado.cabang.map { it.kodeDealer }.toSet())
        assertEquals(11, jawa.cabang.size)
    }

    @Test
    fun `urutan grup Jawa dulu baru Manado`() {
        val groups = BranchRegions.cabangOptionsByRegion()
        assertEquals(BranchRegions.REGION_JAWA, groups[0].region)
        assertEquals(BranchRegions.REGION_MANADO, groups[1].region)
    }

    @Test
    fun `label region persis Jawa dan Manado, bukan Jawa Barat`() {
        assertEquals("Jawa", BranchRegions.regionLabel(BranchRegions.REGION_JAWA))
        assertEquals("Manado", BranchRegions.regionLabel(BranchRegions.REGION_MANADO))
    }

    @Test
    fun `dealerRegion default Jawa utk dealer null atau tak dikenal, case-insensitive utk D-06 D-07`() {
        assertEquals(BranchRegions.REGION_JAWA, BranchRegions.dealerRegion(null))
        assertEquals(BranchRegions.REGION_JAWA, BranchRegions.dealerRegion("D-99"))
        assertEquals(BranchRegions.REGION_MANADO, BranchRegions.dealerRegion("d-06"))
    }

    @Test
    fun `label cabang lengkap sesuai DEALER_LABEL`() {
        assertEquals("Pagaden", BranchRegions.DEALER_LABEL["D-01"])
        assertEquals("Cilacap", BranchRegions.DEALER_LABEL["D-13"])
    }
}
