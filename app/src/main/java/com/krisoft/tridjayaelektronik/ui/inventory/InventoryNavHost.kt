package com.krisoft.tridjayaelektronik.ui.inventory

import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.krisoft.tridjayaelektronik.ui.leads.LeadDetailScreen
import com.krisoft.tridjayaelektronik.ui.search.GlobalSearchScreen

// The "Cari" tab now opens a global search, NOT the inventory browse screen. Browse is still
// reachable from search via "Jelajahi semua barang".
const val SEARCH_ROUTE_ROOT = "global_search"
const val INVENTORY_ROUTE_LIST = "inventory_list"
private const val ROUTE_DETAIL = "inventory_detail/{kode}/{kodeCabang}"
private const val ROUTE_LEAD_DETAIL = "search_lead_detail/{leadId}"

@Composable
fun InventoryNavHost(
    navController: NavHostController = rememberNavController(),
    onCloseSearch: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = SEARCH_ROUTE_ROOT,
        // Rhythm's sub-screen transition: fade + short vertical slide.
        enterTransition = {
            fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(350, easing = EaseInOutQuart)
            )
        },
        exitTransition = { fadeOut(tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = {
            fadeOut(tween(300)) + slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(350, easing = EaseInOutQuart)
            )
        }
    ) {
        composable(SEARCH_ROUTE_ROOT) {
            GlobalSearchScreen(
                onProductClick = { kode, kodeCabang ->
                    navController.navigate("inventory_detail/$kode/$kodeCabang") { launchSingleTop = true }
                },
                onLeadClick = { id ->
                    navController.navigate("search_lead_detail/$id") { launchSingleTop = true }
                },
                onBrowseInventory = { navController.navigate(INVENTORY_ROUTE_LIST) { launchSingleTop = true } },
                onClose = onCloseSearch
            )
        }
        composable(INVENTORY_ROUTE_LIST) {
            InventoryScreen(
                onProductClick = { kode, kodeCabang ->
                    navController.navigate("inventory_detail/$kode/$kodeCabang") { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(
                navArgument("kode") { type = NavType.StringType },
                navArgument("kodeCabang") { type = NavType.StringType }
            )
        ) {
            ProductDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_LEAD_DETAIL,
            arguments = listOf(navArgument("leadId") { type = NavType.LongType })
        ) {
            LeadDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
