package com.krisoft.tridjayaelektronik.ui.activity

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
import com.krisoft.tridjayaelektronik.ui.deadstock.DeadstockScreen
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
import com.krisoft.tridjayaelektronik.ui.kpi.KpiScreen
import com.krisoft.tridjayaelektronik.ui.mutasi.MutasiHistoriScreen
import com.krisoft.tridjayaelektronik.ui.notifications.NotificationCenterScreen
import com.krisoft.tridjayaelektronik.ui.payroll.PayrollScreen
import com.krisoft.tridjayaelektronik.ui.priceerp.ErpPriceChangesScreen
import com.krisoft.tridjayaelektronik.ui.raport.RaportScreen
import com.krisoft.tridjayaelektronik.ui.serials.SerialInputScreen
// Berikut masih tinggal di package ui.home (hanya HomeNavHost yang pindah ke
// ui.activity) — perlu diimpor eksplisit karena tak lagi satu paket.
import com.krisoft.tridjayaelektronik.ui.home.HomeScreen
import com.krisoft.tridjayaelektronik.ui.home.HomeViewModel
import com.krisoft.tridjayaelektronik.ui.home.RankingKind
import com.krisoft.tridjayaelektronik.ui.home.RankingListScreen
import com.krisoft.tridjayaelektronik.ui.home.TransactionListScreen

// Root Activity — layar pertama app (Task B6). Tabel route di bawah dipakai
// DUA tab: lihat dok [ActivityNavHost].
const val ACTIVITY_ROUTE_ROOT = "activity_root"
const val HOME_ROUTE_DASHBOARD = "home_dashboard"
private const val ROUTE_NOTIFICATIONS = "home_notifications"
private const val ROUTE_RANKING = "home_ranking/{kind}"
private const val ROUTE_TRANSACTIONS = "home_ranking_transactions/{kind}/{code}?name={name}"
private const val ROUTE_INDENT = "home_indent"
private const val ROUTE_SALES = "home_sales"
private const val ROUTE_OPNAME = "home_opname"
private const val ROUTE_ABSEN = "home_absen"
private const val ROUTE_RAPORT = "home_raport"
private const val ROUTE_GAJI = "home_gaji"
private const val ROUTE_KPI = "home_kpi"
private const val ROUTE_HARGA_GS = "home_harga_gs"
private const val ROUTE_SERIAL_INPUT = "home_serial_input"
private const val ROUTE_DEADSTOCK = "home_deadstock"
private const val ROUTE_MUTASI_HISTORI = "home_mutasi_histori"
private const val ROUTE_PANDUAN_ALUR = "home_panduan_alur"
// Public (bukan private lagi) — MainActivity deep-link tap-notif buka langsung
// halaman tahap terkait (akses cepat, route dari payload FCM delivery_notif).
const val ROUTE_DLV_CREATE = "home_dlv_create"
const val ROUTE_DLV_DISKON = "home_dlv_diskon"
const val ROUTE_DLV_PDI = "home_dlv_pdi"
const val ROUTE_DLV_AKI = "home_dlv_aki"
const val ROUTE_DLV_KASIR = "home_dlv_kasir"
const val ROUTE_DLV_NOTE = "home_dlv_note"
const val ROUTE_DLV_SCHEDULE = "home_dlv_schedule"
const val ROUTE_DLV_DRIVER = "home_dlv_driver"
private const val ROUTE_DLV_DETAIL = "home_dlv_detail/{id}"
const val ROUTE_DLV_HISTORY = "home_dlv_history"
const val ROUTE_DLV_PENDING_PAYMENT = "home_dlv_pending_payment"
const val ROUTE_SPK_HUB = "home_spk_hub"

private fun dlvDetailRoute(id: String) = "home_dlv_detail/${Uri.encode(id)}"

private fun branchTransactionsRoute(kodeDealer: String, branchName: String) =
    "home_ranking_transactions/${RankingKind.BRANCH.name}/${Uri.encode(kodeDealer)}?name=${Uri.encode(branchName)}"

private fun salesTransactionsRoute(kodePegawai: String, salesName: String) =
    "home_ranking_transactions/${RankingKind.SALES.name}/${Uri.encode(kodePegawai)}?name=${Uri.encode(salesName)}"

/**
 * Kunci tahap delivery → route child — bagian yang SAMA dipakai [routeForNavKey],
 * `onSpkMenu` (tab Operasional), dan `onOpenDelivery` (deep-link notifikasi).
 * Minor 1 audit final-fix-2: dulu tersalin 3× di file ini (drift risk — satu
 * diperbarui, dua lainnya lupa). Tiap pemanggil menambah kunci sendiri
 * (`hub`/`input`/`history`) dan fallback berbeda DI ATAS fungsi ini —
 * perbedaan itu SENGAJA, jangan disamakan.
 */
private fun deliveryStageRoute(key: String): String? = when (key) {
    "diskon" -> ROUTE_DLV_DISKON
    "pdi" -> ROUTE_DLV_PDI
    "aki" -> ROUTE_DLV_AKI
    "kasir" -> ROUTE_DLV_KASIR
    "note" -> ROUTE_DLV_NOTE
    "jadwal" -> ROUTE_DLV_SCHEDULE
    "driver" -> ROUTE_DLV_DRIVER
    else -> null
}

/**
 * Peta `navKey` (kontrak `ActivityRegistry.ACTIVITY_ITEMS`) → route child di
 * tabel ini. Fungsi MURNI (tanpa Compose) supaya bisa diuji JUnit biasa —
 * `navKey` adalah kontrak stringly-typed tanpa pemeriksa kompiler, jadi satu
 * salah ketik di sini berarti kartu yang diam tak melakukan apa-apa. `null`
 * berarti `navKey` tak dikenal (typo) — KECUALI `"crm"`, `"inventory"` dan
 * `"cari_semua"`, yang sengaja tak masuk peta ini karena punya NavHost/tab sendiri
 * dan dibuka lewat callback pindah-tab, bukan navigasi di tabel route ini (lihat
 * pemanggil di [ActivityNavHost]). Diuji di `ActivityNavHostRouteTest`.
 */
internal fun routeForNavKey(navKey: String): String? = when (navKey) {
    "absen" -> ROUTE_ABSEN
    // Bukan item registri (tombol kecil di baris "PINTASAN"), tapi tetap lewat
    // peta ini supaya kontraknya diuji sama seperti navKey lain.
    "panduan_alur" -> ROUTE_PANDUAN_ALUR
    "raport" -> ROUTE_RAPORT
    "indent" -> ROUTE_INDENT
    "spk_input" -> ROUTE_DLV_CREATE
    "spk_history" -> ROUTE_DLV_HISTORY
    "spk_gantung" -> ROUTE_DLV_PENDING_PAYMENT
    else -> deliveryStageRoute(navKey)
}

/**
 * SATU tabel route dipakai DUA tab: tab Activity memulai di
 * [ACTIVITY_ROUTE_ROOT], tab Operasional di [HOME_ROUTE_DASHBOARD]. Masing-masing
 * tab punya `NavHostController` sendiri, jadi keduanya berdiri sendiri.
 *
 * Sengaja tidak memecah jadi dua file: route anak (`home_dlv_*`, `home_opname`,
 * `home_spk_hub`, …) dipakai dari kedua sisi (kartu Activity, grid Akses Cepat
 * di Operasional, dan deep-link push FCM). Memecahnya berarti memindahkan route —
 * hal yang justru dilarang Global Constraints (nama route anak tak boleh berubah).
 */
@Composable
fun ActivityNavHost(
    startDestination: String = ACTIVITY_ROUTE_ROOT,
    onSettingsClick: () -> Unit = {},
    onOpenSummaryTab: () -> Unit = {},
    onQuickAccessInventory: () -> Unit = {},
    /** Ubin "Cari Semua" → SEARCH_ROUTE_ROOT (`GlobalSearchScreen`), bukan daftar barang. */
    onQuickAccessSearch: () -> Unit = {},
    onQuickAccessLeads: () -> Unit = {},
    // Sisa I1: dinaikkan MainScreen tiap tab terpilih berubah JADI Activity (lihat
    // `activityTabSelectedTrigger` di MainActivity.kt) — diteruskan apa adanya ke
    // ActivityScreen, cuma dipakai di root Activity (bukan tab Operasional yang juga
    // memakai NavHost ini dgn startDestination beda).
    activityTabSelectedSignal: Int = 0,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
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
        composable(ACTIVITY_ROUTE_ROOT) {
            ActivityScreen(
                onSettingsClick = onSettingsClick,
                onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true } },
                onOpenAllMenus = onOpenSummaryTab,
                tabSelectedSignal = activityTabSelectedSignal,
                onOpen = { navKey ->
                    // "crm", "inventory" & "cari_semua" sengaja tak masuk `routeForNavKey`:
                    // ketiganya punya NavHost/tab sendiri (LeadsNavHost, InventoryNavHost),
                    // jadi dibuka lewat callback pindah-tab, bukan route di tabel ini.
                    // "inventory" dan "cari_semua" berbagi tab yang sama tapi BERBEDA
                    // tujuan — daftar barang vs pencarian gabungan; jangan disatukan.
                    when (navKey) {
                        "crm" -> onQuickAccessLeads()
                        "inventory" -> onQuickAccessInventory()
                        "cari_semua" -> onQuickAccessSearch()
                        else -> routeForNavKey(navKey)?.let { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                },
            )
        }
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
                onQuickAccessKpi = { navController.navigate(ROUTE_KPI) { launchSingleTop = true } },
                onQuickAccessHargaGs = { navController.navigate(ROUTE_HARGA_GS) { launchSingleTop = true } },
                onQuickAccessSerialInput = { navController.navigate(ROUTE_SERIAL_INPUT) { launchSingleTop = true } },
                onQuickAccessDeadstock = { navController.navigate(ROUTE_DEADSTOCK) { launchSingleTop = true } },
                onQuickAccessMutasiHistori = { navController.navigate(ROUTE_MUTASI_HISTORI) { launchSingleTop = true } },
                onSpkMenu = { key ->
                    val route = when (key) {
                        "hub" -> ROUTE_SPK_HUB
                        "input" -> ROUTE_DLV_CREATE
                        "history" -> ROUTE_DLV_HISTORY
                        else -> deliveryStageRoute(key) ?: ROUTE_DLV_CREATE
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
            NotificationCenterScreen(
                onBack = { navController.popBackStack() },
                // Tap notif delivery → langsung halaman tahap terkait (key sama
                // dgn onSpkMenu HomeScreen + deep-link push FcmService).
                onOpenDelivery = { key ->
                    val route = when (key) {
                        "history" -> ROUTE_DLV_HISTORY
                        else -> deliveryStageRoute(key) ?: ROUTE_SPK_HUB
                    }
                    navController.navigate(route) { launchSingleTop = true }
                },
                onOpenLeads = onQuickAccessLeads
            )
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
        composable(ROUTE_RAPORT) {
            RaportScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_GAJI) {
            PayrollScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_KPI) {
            KpiScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_HARGA_GS) {
            ErpPriceChangesScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SERIAL_INPUT) {
            SerialInputScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_DEADSTOCK) {
            DeadstockScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_MUTASI_HISTORI) {
            MutasiHistoriScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_PANDUAN_ALUR) {
            PanduanAlurScreen(onBack = { navController.popBackStack() })
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
        composable(ROUTE_DLV_CREATE) {
            CreateSpkScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    // PDI Mandiri: ganti layar Input SPK dgn Detail (popUpTo dirinya sendiri)
                    // biar tombol back dari Detail balik ke Hub, bukan ke form SPK yg sudah terkirim.
                    navController.navigate(dlvDetailRoute(id)) {
                        popUpTo(ROUTE_DLV_CREATE) { inclusive = true }
                    }
                },
            )
        }
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
            DeliveryQueueScreen("Tugas Antar", status = null, reorderable = true, asDriver = true, onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_PENDING_PAYMENT) {
            // Server mengirim SEMUA unit terkirim yang belum dikonfirmasi; ambang
            // "gantung 24 jam" dihitung di kartu Activity, bukan di sini — kasir
            // tetap perlu bisa menutup yang baru sebelum jatuh tempo.
            DeliveryQueueScreen("Konfirmasi Pembayaran", status = null, view = "pending_payment", onBack = { navController.popBackStack() },
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
