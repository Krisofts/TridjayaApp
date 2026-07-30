package com.krisoft.tridjayaelektronik.ui.opname

import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fungsi murni — dites tanpa Compose, pola sama `bolehUsulkanSn`/`temuanLabel`. */
class OpnameStockFilterTest {

    private fun stock(kode: String, nama: String) =
        OpnameStockItemDto(kodeBarang = kode, namaBarang = nama, merk = null, kategori = null)

    private fun unit(kode: String, sn: String) = OpnameUnitEntity(
        sessionId = "s1",
        serialNumber = sn,
        kodeBarang = kode,
        namaBarang = null,
        kondisi = "layak",
        keterangan = null,
        temuan = null,
        updatedAtMillis = 0L,
        syncedAtMillis = 1L,
    )

    private val allStock = listOf(
        stock("P1", "Ban Depan"),
        stock("P2", "Oli Mesin"),
        stock("P3", "Rem Cakram"),
    )

    private fun unitsByCode(vararg counted: String) =
        counted.associateWith { kode -> listOf(unit(kode, "$kode-SN-1")) }

    @Test
    fun `filter BELUM hanya barang tanpa unit`() {
        val result = filterOpnameStock(allStock, unitsByCode("P1"), StockFilter.BELUM, search = "")
        assertEquals(listOf("P2", "P3"), result.map { it.kodeBarang })
    }

    @Test
    fun `filter SUDAH hanya barang ber-unit`() {
        val result = filterOpnameStock(allStock, unitsByCode("P1", "P3"), StockFilter.SUDAH, search = "")
        assertEquals(listOf("P1", "P3"), result.map { it.kodeBarang })
    }

    @Test
    fun `filter SEMUA tidak menyaring status scan`() {
        val result = filterOpnameStock(allStock, unitsByCode("P1"), StockFilter.SEMUA, search = "")
        assertEquals(listOf("P1", "P2", "P3"), result.map { it.kodeBarang })
    }

    @Test
    fun `search menyaring di dalam grup yang sudah difilter`() {
        val result = filterOpnameStock(allStock, unitsByCode("P1"), StockFilter.BELUM, search = "rem")
        assertEquals(listOf("P3"), result.map { it.kodeBarang })
    }

    @Test
    fun `search cocok kode atau nama, tak peduli huruf besar-kecil`() {
        assertEquals(
            listOf("P2"),
            filterOpnameStock(allStock, emptyMap(), StockFilter.SEMUA, search = "P2").map { it.kodeBarang },
        )
        assertEquals(
            listOf("P2"),
            filterOpnameStock(allStock, emptyMap(), StockFilter.SEMUA, search = "OLI").map { it.kodeBarang },
        )
    }
}
