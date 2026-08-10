package com.krisoft.tridjayaelektronik.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Layar Cari Semua HANYA membaca cache Room, jadi "hasil kosong" bisa berarti tiga hal yang
 * berbeda bagi user. Sampai 2026-08-10 ketiganya dijawab satu kalimat — "Tidak ditemukan" —
 * dan itu menyembunyikan bug nyata: role `karyawan` tak pernah melewati layar yang mengisi
 * `branch_stock` (jelajah barang / detail produk), jadi SETIAP pencarian barangnya dijawab
 * "tidak ada yang cocok" walau tabelnya memang kosong sejak install.
 */
class GlobalSearchEmptyVerdictTest {

    @Test
    fun `sync stok masih jalan - tampilkan pemuatan, bukan vonis kosong`() {
        assertEquals(
            SearchEmptyVerdict.MEMUAT,
            searchEmptyVerdict(isSyncingProducts = true, productSyncError = null, showProducts = true)
        )
    }

    @Test
    fun `pemuatan menang atas kegagalan lama - percobaan baru sedang jalan`() {
        assertEquals(
            SearchEmptyVerdict.MEMUAT,
            searchEmptyVerdict(isSyncingProducts = true, productSyncError = "http_503", showProducts = true)
        )
    }

    @Test
    fun `sync gagal - tawarkan coba lagi, jangan bilang tidak ditemukan`() {
        assertEquals(
            SearchEmptyVerdict.GAGAL_SYNC,
            searchEmptyVerdict(isSyncingProducts = false, productSyncError = "http_500", showProducts = true)
        )
    }

    @Test
    fun `filter Prospek saja - kegagalan sync stok tak boleh disalahkan`() {
        // Pencarian yang sengaja tak menyertakan produk kosong karena prospeknya tak cocok;
        // menyodorkan "data barang belum tersedia" di situ cuma menyesatkan.
        assertEquals(
            SearchEmptyVerdict.TIDAK_DITEMUKAN,
            searchEmptyVerdict(isSyncingProducts = false, productSyncError = "http_500", showProducts = false)
        )
    }

    @Test
    fun `cache sehat dan sync sukses - benar-benar tidak ditemukan`() {
        assertEquals(
            SearchEmptyVerdict.TIDAK_DITEMUKAN,
            searchEmptyVerdict(isSyncingProducts = false, productSyncError = null, showProducts = true)
        )
    }

    @Test
    fun `showProducts mengikuti filter jenis hasil`() {
        assertEquals(true, GlobalSearchUiState(filter = SearchFilter.ALL).showProducts)
        assertEquals(true, GlobalSearchUiState(filter = SearchFilter.PRODUCTS).showProducts)
        assertEquals(false, GlobalSearchUiState(filter = SearchFilter.LEADS).showProducts)
    }
}
