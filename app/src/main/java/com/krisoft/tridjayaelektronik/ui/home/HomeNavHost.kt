package com.krisoft.tridjayaelektronik.ui.home

import android.net.Uri
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
import com.krisoft.tridjayaelektronik.ui.attendance.AttendanceScreen
import com.krisoft.tridjayaelektronik.ui.delivery.DeliveryListScreen
import com.krisoft.tridjayaelektronik.ui.indent.IndentListScreen
import com.krisoft.tridjayaelektronik.ui.opname.OpnameListScreen
import com.krisoft.tridjayaelektronik.ui.sales.SalesScreen
import com.krisoft.tridjayaelektronik.ui.spk.DeliveryControlScreen
import com.krisoft.tridjayaelektronik.ui.spk.DiscountApprovalScreen
import com.krisoft.tridjayaelektronik.ui.spk.KasirQueueScreen
import com.krisoft.tridjayaelektronik.ui.spk.PdiQueueScreen
import com.krisoft.tridjayaelektronik.ui.spk.SpkListScreen
import com.krisoft.tridjayaelektronik.ui.spk.SpkOrderDetailScreen

const val HOME_ROUTE_DASHBOARD = "home_dashboard"
private const val ROUTE_RANKING = "home_ranking/{kind}"
private const val ROUTE_TRANSACTIONS = "home_ranking_transactions/{kind}/{code}?name={name}"
private const val ROUTE_INDENT = "home_indent"
private const val ROUTE_SALES = "home_sales"
private const val ROUTE_OPNAME = "home_opname"
private const val ROUTE_DELIVERY = "home_delivery"
private const val ROUTE_ABSEN = "home_absen"
private const val ROUTE_SPK_LIST = "home_spk_list"
private const val ROUTE_SPK_DISKON = "home_spk_diskon"
private const val ROUTE_SPK_KASIR = "home_spk_kasir"
private const val ROUTE_SPK_PDI = "home_spk_pdi"
private const val ROUTE_SPK_KONTROL = "home_spk_kontrol"
private const val ROUTE_SPK_DETAIL = "home_spk_detail/{mode}/{id}"

private fun spkDetailRoute(mode: String, id: String) = "home_spk_detail/$mode/${Uri.encode(id)}"

private fun branchTransactionsRoute(kodeDealer: String, branchName: String) =
    "home_ranking_transactions/${RankingKind.BRANCH.name}/${Uri.encode(kodeDealer)}?name=${Uri.encode(branchName)}"

private fun salesTransactionsRoute(kodePegawai: String, salesName: String) =
    "home_ranking_transactions/${RankingKind.SALES.name}/${Uri.encode(kodePegawai)}?name=${Uri.encode(salesName)}"

@Composable
fun HomeNavHost(
    onSettingsClick: () -> Unit = {},
    onQuickAccessInventory: () -> Unit = {},
    onQuickAccessLeads: () -> Unit = {},
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
                onBranchClick = { branch ->
                    navController.navigate(branchTransactionsRoute(branch.kodeDealer, branch.cabang)) { launchSingleTop = true }
                },
                onSalesClick = { sales ->
                    navController.navigate(salesTransactionsRoute(sales.sourceCode, sales.name)) { launchSingleTop = true }
                },
                onSettingsClick = onSettingsClick,
                onQuickAccessInventory = onQuickAccessInventory,
                onQuickAccessLeads = onQuickAccessLeads,
                onQuickAccessIndent = { navController.navigate(ROUTE_INDENT) { launchSingleTop = true } },
                onQuickAccessSales = { navController.navigate(ROUTE_SALES) { launchSingleTop = true } },
                onQuickAccessOpname = { navController.navigate(ROUTE_OPNAME) { launchSingleTop = true } },
                onQuickAccessDelivery = { navController.navigate(ROUTE_DELIVERY) { launchSingleTop = true } },
                onQuickAccessAbsen = { navController.navigate(ROUTE_ABSEN) { launchSingleTop = true } },
                onSpkMenu = { key ->
                    val route = when (key) {
                        "input" -> ROUTE_SPK_LIST
                        "diskon" -> ROUTE_SPK_DISKON
                        "kasir" -> ROUTE_SPK_KASIR
                        "pdi" -> ROUTE_SPK_PDI
                        "kontrol" -> ROUTE_SPK_KONTROL
                        else -> ROUTE_SPK_LIST
                    }
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(
            route = ROUTE_RANKING,
            arguments = listOf(navArgument("kind") { type = NavType.StringType })
        ) {
            RankingListScreen(
                onBack = { navController.popBackStack() },
                onBranchClick = { branch ->
                    navController.navigate(branchTransactionsRoute(branch.kodeDealer, branch.cabang)) { launchSingleTop = true }
                },
                onSalesClick = { sales ->
                    navController.navigate(salesTransactionsRoute(sales.sourceCode, sales.name)) { launchSingleTop = true }
                }
            )
        }
        composable(
            route = ROUTE_TRANSACTIONS,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("code") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            TransactionListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_INDENT) {
            IndentListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_OPNAME) {
            OpnameListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_DELIVERY) {
            DeliveryListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_ABSEN) {
            AttendanceScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SPK_LIST) { SpkListScreen(onBack = { navController.popBackStack() }) }
        composable(ROUTE_SPK_DISKON) {
            DiscountApprovalScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(spkDetailRoute("diskon", id)) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_SPK_KASIR) {
            KasirQueueScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(spkDetailRoute("kasir", id)) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_SPK_PDI) {
            PdiQueueScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(spkDetailRoute("pdi", id)) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_SPK_KONTROL) {
            DeliveryControlScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(spkDetailRoute("kontrol", id)) { launchSingleTop = true } }
            )
        }
        composable(
            route = ROUTE_SPK_DETAIL,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) { entry ->
            SpkOrderDetailScreen(
                mode = entry.arguments?.getString("mode").orEmpty(),
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(ROUTE_SALES) {
            SalesScreen(
                onBack = { navController.popBackStack() },
                onViewMoreBranches = {
                    navController.navigate("home_ranking/${RankingKind.BRANCH.name}") { launchSingleTop = true }
                },
                onViewMoreSales = {
                    navController.navigate("home_ranking/${RankingKind.SALES.name}") { launchSingleTop = true }
                },
                onBranchClick = { branch ->
                    navController.navigate(branchTransactionsRoute(branch.kodeDealer, branch.cabang)) { launchSingleTop = true }
                },
                onSalesClick = { sales ->
                    navController.navigate(salesTransactionsRoute(sales.sourceCode, sales.name)) { launchSingleTop = true }
                }
            )
        }
    }
}
