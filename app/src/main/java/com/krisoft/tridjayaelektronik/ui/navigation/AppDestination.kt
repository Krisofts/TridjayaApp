package com.krisoft.tridjayaelektronik.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChecklistRtl
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for every app destination. [bottomNavItems] is what
 * actually shows on the bottom bar. Layar pertama app kini Activity ("hari ini
 * aku harus ngapain?"), dashboard lama pindah ke tab Operasional. SETTINGS &
 * LEADS tetap ada sebagai destination tapi dibuka dari Activity (gear /
 * kartu "Input prospek") daripada menempati slot bottom nav sendiri.
 */
enum class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    ACTIVITY("activity", "Activity", Icons.Rounded.ChecklistRtl),
    SUMMARY("summary", "Operasional", Icons.Rounded.Insights),
    INVENTORY("inventory", "Cari", Icons.Rounded.Search),
    // LEADS & SETTINGS tetap destination yang sah — dibuka dari Activity
    // (kartu "Input prospek" / gear), hanya tak menempati slot bottom nav.
    LEADS("leads", "CRM", Icons.Rounded.Groups),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings);

    companion object {
        // Tab CRM dilepas dari bottom nav: prospek kini jadi tugas harian di
        // Activity (dan tetap dibuka dari sana). Pill Rhythm dirancang 2-3 item.
        val bottomNavItems = listOf(ACTIVITY, SUMMARY, INVENTORY)
    }
}
