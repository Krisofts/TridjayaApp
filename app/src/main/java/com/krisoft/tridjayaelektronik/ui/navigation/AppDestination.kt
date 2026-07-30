package com.krisoft.tridjayaelektronik.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChecklistRtl
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for every app destination. [bottomNavItems] is what
 * actually shows on the bottom bar. Layar pertama app kini Activity ("hari ini
 * aku harus ngapain?"), dashboard lama pindah ke tab Operasional. INVENTORY
 * tetap ada sebagai destination tapi dibuka dari Activity (ubin "Cari Barang")
 * atau dari grid Akses Cepat di Operasional, daripada menempati slot bottom
 * nav sendiri. Prospek/CRM BUKAN destination lagi (dulu LEADS) — sejak
 * 2026-07-30 ia route biasa di dalam `ActivityNavHost` (`ROUTE_LEADS_LIST`),
 * persis sibling home_* lain, supaya mewarisi header+back+hilangnya pill yang
 * sama alih-alih jadi tab sejajar tanpa keduanya.
 */
enum class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    ACTIVITY("activity", "Activity", Icons.Rounded.ChecklistRtl),
    SUMMARY("summary", "Operasional", Icons.Rounded.Insights),
    INVENTORY("inventory", "Cari", Icons.Rounded.Search),
    SETTINGS("settings", "Pengaturan", Icons.Rounded.Settings);

    companion object {
        // Pill Rhythm dirancang 2-3 item.
        //
        // INVENTORY menyusul dilepas 2026-07-29 (tombol Cari/search FAB dihapus
        // atas permintaan user). Destination-nya SENGAJA tetap ada — ia host
        // `InventoryNavHost` (jelajah barang, detail produk, flyer) — dan
        // dijangkau lewat callback pindah-tab `onQuickAccessInventory`:
        // ubin "Cari Barang" di Activity + tile "Inventory" di grid Akses Cepat
        // Operasional. Menghapus baris ini dari enum akan mematikan seluruh
        // menu Inventory, bukan cuma tombolnya.
        // SETTINGS masuk pill 2026-07-29 (permintaan user: lebih mudah
        // dijangkau). Sebelumnya hanya lewat ikon gear di pojok header Activity
        // & Operasional — ikon itu DIHAPUS bersamaan supaya tak ada dua pintu ke
        // layar yang sama.
        val bottomNavItems = listOf(ACTIVITY, SUMMARY, SETTINGS)
    }
}
