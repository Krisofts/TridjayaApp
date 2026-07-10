package com.krisoft.tridjayaelektronik.ui.leads

import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

const val LEADS_ROUTE_LIST = "leads_list"
private const val ROUTE_ADD = "leads_add"
private const val ROUTE_DETAIL = "leads_detail/{leadId}"

@Composable
fun LeadsNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = LEADS_ROUTE_LIST,
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
        composable(LEADS_ROUTE_LIST) { entry ->
            val listViewModel: LeadsListViewModel = hiltViewModel(entry)
            LeadsListScreen(
                viewModel = listViewModel,
                onAddClick = { navController.navigate(ROUTE_ADD) { launchSingleTop = true } },
                onLeadClick = { id -> navController.navigate("leads_detail/$id") { launchSingleTop = true } }
            )
        }
        composable(ROUTE_ADD) {
            val listEntry = remember { navController.getBackStackEntry(LEADS_ROUTE_LIST) }
            val listViewModel: LeadsListViewModel = hiltViewModel(listEntry)
            AddLeadScreen(
                onBack = { navController.popBackStack() },
                onLeadCreated = {
                    listViewModel.refresh()
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(navArgument("leadId") { type = NavType.LongType })
        ) {
            LeadDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
