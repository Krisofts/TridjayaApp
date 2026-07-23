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
import com.krisoft.tridjayaelektronik.ui.indent.IndentListScreen
import com.krisoft.tridjayaelektronik.ui.opname.OpnameListScreen
import com.krisoft.tridjayaelektronik.ui.sales.SalesScreen
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import com.krisoft.tridjayaelektronik.ui.deliveryflow.AkiListScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.CreateSpkScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.DiscountApprovalScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.DeliveryJobDetailScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.DeliveryQueueScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.SpkHubScreen
import com.krisoft.tridjayaelektronik.ui.notifications.NotificationCenterScreen
import com.krisoft.tridjayaelektronik.ui.payroll.PayrollScreen
import com.krisoft.tridjayaelektronik.ui.priceerp.ErpPriceChangesScreen
import com.krisoft.tridjayaelektronik.ui.serials.SerialInputScreen

const val HOME_ROUTE_DASHBOARD = "home_dashboard"
private const val ROUTE_NOTIFICATIONS = "home_notifications"
private const val ROUTE_RANKING = "home_ranking/{kind}"
private const val ROUTE_TRANSACTIONS = "home_ranking_transactions/{kind}/{code}?name={name}"
private const val ROUTE_INDENT = "home_indent"
private const val ROUTE_SALES = "home_sales"
private const val ROUTE_OPNAME = "home_opname"
private const val ROUTE_ABSEN = "home_absen"
private const val ROUTE_GAJI = "home_gaji"
private const val ROUTE_HARGA_GS = "home_harga_gs"
private const val ROUTE_SERIAL_INPUT = "home_serial_input"
private const val ROUTE_DLV_CREATE = "home_dlv_create"
private const val ROUTE_DLV_DISKON = "home_dlv_diskon"
private const val ROUTE_DLV_PDI = "home_dlv_pdi"
private const val ROUTE_DLV_AKI = "home_dlv_aki"
private const val ROUTE_DLV_KASIR = "home_dlv_kasir"
private const val ROUTE_DLV_NOTE = "home_dlv_note"
private const val ROUTE_DLV_SCHEDULE = "home_dlv_schedule"
private const val ROUTE_DLV_DRIVER = "home_dlv_driver"
private const val ROUTE_DLV_DETAIL = "home_dlv_detail/{id}"
const val ROUTE_DLV_HISTORY = "home_dlv_history"
const val ROUTE_SPK_HUB = "home_spk_hub"

private fun dlvDetailRoute(id: String) = "home_dlv_detail/${Uri.encode(id)}"

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
                onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true } },
                onQuickAccessInventory = onQuickAccessInventory,
                onQuickAccessLeads = onQuickAccessLeads,
                onQuickAccessIndent = { navController.navigate(ROUTE_INDENT) { launchSingleTop = true } },
                onQuickAccessSales = { navController.navigate(ROUTE_SALES) { launchSingleTop = true } },
                onQuickAccessOpname = { navController.navigate(ROUTE_OPNAME) { launchSingleTop = true } },
                onQuickAccessAbsen = { navController.navigate(ROUTE_ABSEN) { launchSingleTop = true } },
                onQuickAccessGaji = { navController.navigate(ROUTE_GAJI) { launchSingleTop = true } },
                onQuickAccessHargaGs = { navController.navigate(ROUTE_HARGA_GS) { launchSingleTop = true } },
                onQuickAccessSerialInput = { navController.navigate(ROUTE_SERIAL_INPUT) { launchSingleTop = true } },
                onSpkMenu = { key ->
                    val route = when (key) {
                        "hub" -> ROUTE_SPK_HUB
                        "input" -> ROUTE_DLV_CREATE
                        "diskon" -> ROUTE_DLV_DISKON
                        "pdi" -> ROUTE_DLV_PDI
                        "aki" -> ROUTE_DLV_AKI
                        "kasir" -> ROUTE_DLV_KASIR
                        "note" -> ROUTE_DLV_NOTE
                        "jadwal" -> ROUTE_DLV_SCHEDULE
                        "driver" -> ROUTE_DLV_DRIVER
                        "history" -> ROUTE_DLV_HISTORY
                        else -> ROUTE_DLV_CREATE
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
        composable(ROUTE_NOTIFICATIONS) {
            NotificationCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_INDENT) {
            IndentListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_OPNAME) {
            OpnameListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_ABSEN) {
            AttendanceScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_GAJI) {
            PayrollScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_HARGA_GS) {
            ErpPriceChangesScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SERIAL_INPUT) {
            SerialInputScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SPK_HUB) {
            SpkHubScreen(onBack = { navController.popBackStack() }, onNavigate = { key ->
                val route = when (key) {
                    "input" -> ROUTE_DLV_CREATE
                    "diskon" -> ROUTE_DLV_DISKON
                    "pdi" -> ROUTE_DLV_PDI
                    "aki" -> ROUTE_DLV_AKI
                    "kasir" -> ROUTE_DLV_KASIR
                    "note" -> ROUTE_DLV_NOTE
                    "jadwal" -> ROUTE_DLV_SCHEDULE
                    "driver" -> ROUTE_DLV_DRIVER
                    "history" -> ROUTE_DLV_HISTORY
                    else -> ROUTE_DLV_CREATE
                }
                navController.navigate(route) { launchSingleTop = true }
            })
        }
        composable(ROUTE_DLV_CREATE) { CreateSpkScreen(onBack = { navController.popBackStack() }) }
        composable(ROUTE_DLV_DISKON) { DiscountApprovalScreen(onBack = { navController.popBackStack() }) }
        composable(ROUTE_DLV_PDI) {
            DeliveryQueueScreen("Antri PDI", DeliveryStatusKey.PENDING_PDI, onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_AKI) { AkiListScreen(onBack = { navController.popBackStack() }) }
        composable(ROUTE_DLV_KASIR) {
            DeliveryQueueScreen("Antri Kasir", DeliveryStatusKey.PENDING_SPK, onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_NOTE) {
            DeliveryQueueScreen("Surat Jalan", DeliveryStatusKey.PENDING_DELIVERY_NOTE, onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_SCHEDULE) {
            DeliveryQueueScreen("Penjadwalan", DeliveryStatusKey.PENDING_SCHEDULING, onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_DRIVER) {
            // Driver: backend meng-scope antrian (assigned + in_transit) berdasarkan role, tanpa filter status.
            DeliveryQueueScreen("Tugas Antar", status = null, reorderable = true, onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_HISTORY) {
            DeliveryQueueScreen("Riwayat SPK", status = null, view = "history", onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(
            route = ROUTE_DLV_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            DeliveryJobDetailScreen(
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
