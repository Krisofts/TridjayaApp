package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Logika paging sinkronisasi stok cabang ([nextSyncStep]) — batas waras + syarat berhenti.
 * Murni JVM: tak menyentuh Retrofit/Room, jadi yang diuji memang keputusannya, bukan jaringan.
 */
class InventorySyncPagingTest {

    /** Meniru loop `InventoryRepository.sync()` di atas server palsu berisi [totalPages] halaman. */
    private fun simulate(totalPages: Int, maxPages: Int, rowsPerPage: Int = 1000): Pair<SyncStep, Int> {
        var page = 1
        var rows = 0
        var step: SyncStep
        while (true) {
            rows += rowsPerPage
            step = nextSyncStep(page, hasMore = page < totalPages, maxPages = maxPages)
            if (step != SyncStep.CONTINUE) break
            page += 1
        }
        return step to rows
    }

    @Test
    fun `halaman terakhir menutup sinkronisasi sebagai utuh`() {
        assertEquals(SyncStep.DONE, nextSyncStep(page = 4, hasMore = false, maxPages = 20))
    }

    @Test
    fun `masih ada halaman dan jauh dari batas maka lanjut`() {
        assertEquals(SyncStep.CONTINUE, nextSyncStep(page = 3, hasMore = true, maxPages = 20))
    }

    @Test
    fun `tepat di batas dengan sisa halaman dianggap terpotong`() {
        assertEquals(SyncStep.TRUNCATED, nextSyncStep(page = 20, hasMore = true, maxPages = 20))
    }

    @Test
    fun `halaman terakhir tepat di batas tetap utuh bukan terpotong`() {
        // hasMore=false menang atas batas — 20 halaman pas bukan snapshot parsial,
        // jadi baris lama tetap boleh dibersihkan.
        assertEquals(SyncStep.DONE, nextSyncStep(page = 20, hasMore = false, maxPages = 20))
    }

    @Test
    fun `katalog wajar 4 halaman selesai utuh`() {
        // Bentuk nyata setelah `inStock=true`: 3.639 baris berstok = 4 halaman.
        val (step, rows) = simulate(totalPages = 4, maxPages = 20)
        assertEquals(SyncStep.DONE, step)
        assertEquals(4000, rows)
    }

    @Test
    fun `katalog membengkak 67 halaman berhenti di batas bukan tanpa akhir`() {
        // Regresi SP GS 28 Jul 2026: 66.482 baris. Tanpa batas, sinkronisasi tak pernah selesai.
        val (step, rows) = simulate(totalPages = 67, maxPages = 20)
        assertEquals(SyncStep.TRUNCATED, step)
        assertEquals(20_000, rows)
    }

    @Test
    fun `satu halaman tanpa lanjutan langsung utuh`() {
        val (step, rows) = simulate(totalPages = 1, maxPages = 20)
        assertEquals(SyncStep.DONE, step)
        assertEquals(1000, rows)
    }
}
