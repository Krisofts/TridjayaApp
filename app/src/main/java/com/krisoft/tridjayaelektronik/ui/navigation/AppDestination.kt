package com.krisoft.tridjayaelektronik.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for every app destination. [bottomNavItems] is what
 * actually shows on the bottom bar — matches Rhythm's 3-tab pattern (Home,
 * CRM, Cari). SETTINGS still exists as a destination but is reached via a
 * gear icon on Home rather than occupying a 4th bottom-nav slot.
 */
enum class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Rounded.Home),
    LEADS("leads", "CRM", Icons.Rounded.Groups),
    INVENTORY("inventory", "Cari", Icons.Rounded.Search),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings);

    companion object {
        val bottomNavItems = listOf(HOME, LEADS, INVENTORY)
    }
}
