package com.krisoft.tridjayaelektronik.ui.home

import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

const val HOME_ROUTE_DASHBOARD = "home_dashboard"
private const val ROUTE_RANKING = "home_ranking/{kind}"

@Composable
fun HomeNavHost(
    onSettingsClick: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE_DASHBOARD,
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
        composable(HOME_ROUTE_DASHBOARD) { entry ->
            val viewModel: HomeViewModel = hiltViewModel(entry)
            HomeScreen(
                viewModel = viewModel,
                onViewMoreBranches = {
                    navController.navigate("home_ranking/${RankingKind.BRANCH.name}") { launchSingleTop = true }
                },
                onViewMoreSales = {
                    navController.navigate("home_ranking/${RankingKind.SALES.name}") { launchSingleTop = true }
                },
                onSettingsClick = onSettingsClick
            )
        }
        composable(
            route = ROUTE_RANKING,
            arguments = listOf(navArgument("kind") { type = NavType.StringType })
        ) {
            RankingListScreen(onBack = { navController.popBackStack() })
        }
    }
}
