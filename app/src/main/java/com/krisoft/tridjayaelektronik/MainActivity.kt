package com.krisoft.tridjayaelektronik

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.krisoft.tridjayaelektronik.ui.security.SecurityBlockScreen
import com.krisoft.tridjayaelektronik.ui.security.SecurityGuard
import com.krisoft.tridjayaelektronik.ui.security.Threat
import androidx.compose.runtime.produceState
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.krisoft.tridjayaelektronik.data.ThemePreferences
import com.krisoft.tridjayaelektronik.ui.home.HOME_ROUTE_DASHBOARD
import com.krisoft.tridjayaelektronik.ui.home.HomeNavHost
import com.krisoft.tridjayaelektronik.ui.inventory.InventoryNavHost
import com.krisoft.tridjayaelektronik.ui.inventory.SEARCH_ROUTE_ROOT
import com.krisoft.tridjayaelektronik.ui.leads.LEADS_ROUTE_LIST
import com.krisoft.tridjayaelektronik.ui.leads.LeadsNavHost
import com.krisoft.tridjayaelektronik.ui.login.ChangePasswordScreen
import com.krisoft.tridjayaelektronik.ui.login.ForgotPasswordScreen
import com.krisoft.tridjayaelektronik.ui.login.LoginScreen
import com.krisoft.tridjayaelektronik.ui.login.ResetPasswordScreen
import com.krisoft.tridjayaelektronik.ui.navigation.AppDestination
import com.krisoft.tridjayaelektronik.ui.session.SessionViewModel
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import com.krisoft.tridjayaelektronik.ui.settings.SettingsScreen
import com.krisoft.tridjayaelektronik.ui.splash.SplashScreen
import com.krisoft.tridjayaelektronik.ui.update.UpdateDialog
import com.krisoft.tridjayaelektronik.ui.update.UpdateViewModel
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaAppTheme
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaFloatingNav
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaNavItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN = "main"
private const val ROUTE_CHANGE_PW = "change_password" // forced (must_change_password)
private const val ROUTE_FORGOT_PW = "forgot_password"
private const val ROUTE_RESET_PW = "reset_password"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeState by themePreferences.state.collectAsState()
            TridjayaAppTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecurityGate { TridjayaNavHost() }
                }
            }
        }
    }
}

/**
 * Gerbang integritas: bila [SecurityGuard] mendeteksi aplikasi mock location / perangkat berbahaya,
 * seluruh aplikasi diganti dengan [SecurityBlockScreen] sampai ancaman dicopot. Deteksi diulang tiap
 * kali app kembali ke foreground (ON_RESUME) dan lewat tombol "Cek Ulang".
 */
@Composable
private fun SecurityGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var recheckKey by remember { mutableStateOf(0) }

    // Deteksi (scan semua paket + cek root berbasis file) dijalankan di Dispatchers.IO — sebelumnya
    // sinkron di main thread saat start & tiap resume → risiko ANR/jank. produceState menahan hasil
    // lama saat re-cek sehingga tak berkedip; hanya run pertama menampilkan status "memeriksa".
    val threats by produceState<List<Threat>?>(initialValue = null, recheckKey) {
        value = withContext(Dispatchers.IO) { SecurityGuard.detect(context) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recheckKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val t = threats) {
        null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> if (t.isEmpty()) content() else SecurityBlockScreen(threats = t, onRecheck = { recheckKey++ })
    }
}

@Composable
private fun TridjayaNavHost(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val navController: NavHostController = rememberNavController()
    // Cached locally, available instantly — no network wait. SessionViewModel silently
    // validates/refreshes this in the background and the value here updates live if that fails.
    val isLoggedIn by sessionViewModel.sessionState.collectAsState()
    val mustChangePassword by sessionViewModel.mustChangePassword.collectAsState()

    val updateStatus by updateViewModel.status.collectAsState()
    val optionalDismissed by updateViewModel.optionalDismissed.collectAsState()
    val context = LocalContext.current

    // Single reactive source of truth for which gate we belong on: logout / background
    // session-invalidation → Login; server-flagged forced change → Change Password; otherwise Main.
    // Covers changes originating anywhere in the app without per-screen callbacks.
    LaunchedEffect(isLoggedIn, mustChangePassword) {
        val route = navController.currentDestination?.route
        when {
            !isLoggedIn -> {
                // Login + the public forgot/reset screens are all valid while logged out.
                val onAuthGate = route in setOf(ROUTE_SPLASH, ROUTE_LOGIN, ROUTE_FORGOT_PW, ROUTE_RESET_PW)
                if (!onAuthGate) navController.navigate(ROUTE_LOGIN) {
                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                }
            }
            mustChangePassword -> {
                if (route != ROUTE_CHANGE_PW) navController.navigate(ROUTE_CHANGE_PW) {
                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                }
            }
            // Logged in, no forced change: if the forced screen is still up (change just completed),
            // move into the app.
            route == ROUTE_CHANGE_PW -> navController.navigate(ROUTE_MAIN) {
                popUpTo(0) { inclusive = true }; launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_SPLASH,
        // Smooth crossfade between the splash and the first real screen.
        enterTransition = { fadeIn(tween(400)) },
        exitTransition = { fadeOut(tween(400)) }
    ) {
        composable(ROUTE_SPLASH) {
            SplashScreen(
                onFinished = {
                    val dest = when {
                        !isLoggedIn -> ROUTE_LOGIN
                        mustChangePassword -> ROUTE_CHANGE_PW
                        else -> ROUTE_MAIN
                    }
                    navController.navigate(dest) {
                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_LOGIN) {
            LoginScreen(
                // Always go to Main; the gate LaunchedEffect redirects to Change Password if the
                // server flagged must_change_password.
                onLoginSuccess = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onForgotPassword = { navController.navigate(ROUTE_FORGOT_PW) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_CHANGE_PW) {
            ChangePasswordScreen(
                forced = true,
                onDone = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(0) { inclusive = true }; launchSingleTop = true
                    }
                },
                onBack = {}
            )
        }
        composable(ROUTE_FORGOT_PW) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onHaveCode = { navController.navigate(ROUTE_RESET_PW) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_RESET_PW) {
            ResetPasswordScreen(
                onBack = { navController.popBackStack() },
                // Reset done → back to Login to sign in with the new password.
                onDone = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }; launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_MAIN) {
            MainScreen()
        }
    }

    // Update gate — a force update blocks the whole app (over any screen incl. login); an optional
    // update shows a dismissible prompt once. AlertDialog renders in its own window above the NavHost.
    (updateStatus as? UpdateStatus.Available)?.let { available ->
        if (available.force || !optionalDismissed) {
            UpdateDialog(
                available = available,
                onUpdate = { openUpdateUrl(context, available.updateUrl) },
                onDismiss = if (available.force) null else ({ updateViewModel.dismissOptional() })
            )
        }
    }
}

/** Opens the update link (Play Store / APK URL); falls back to this app's Play Store page. */
private fun openUpdateUrl(context: android.content.Context, url: String) {
    val target = url.ifBlank { "https://play.google.com/store/apps/details?id=${context.packageName}" }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
}

@Composable
private fun DestinationContent(
    destination: AppDestination,
    onSettingsClick: () -> Unit,
    onSettingsBack: () -> Unit,
    onCloseSearch: () -> Unit,
    onQuickAccessInventory: () -> Unit,
    onQuickAccessLeads: () -> Unit,
    inventoryOpenListSignal: Int,
    homeNav: NavHostController,
    inventoryNav: NavHostController,
    leadsNav: NavHostController
) {
    when (destination) {
        AppDestination.HOME -> HomeNavHost(
            onSettingsClick = onSettingsClick,
            onQuickAccessInventory = onQuickAccessInventory,
            onQuickAccessLeads = onQuickAccessLeads,
            navController = homeNav
        )
        AppDestination.INVENTORY -> InventoryNavHost(
            navController = inventoryNav,
            onCloseSearch = onCloseSearch,
            openListSignal = inventoryOpenListSignal,
            // Same "leave this tab, land on Home" semantics as closing search — reused here for
            // quick-access entries where there's nothing left in this tab's own back stack to pop.
            onExitToHome = onCloseSearch
        )
        AppDestination.LEADS -> LeadsNavHost(navController = leadsNav)
        AppDestination.SETTINGS -> SettingsScreen(onBack = onSettingsBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val destinations = AppDestination.bottomNavItems
    var selected by remember { mutableStateOf(destinations.first()) }
    // Bumped by Home's "Akses Cepat" Inventory tile — see the LaunchedEffect inside
    // InventoryNavHost for why the actual navigate() call lives there, not here.
    var inventoryOpenListTrigger by remember { mutableStateOf(0) }

    // Hoisted so we can watch each tab's inner route and hide the floating nav on detail screens.
    val homeNav = rememberNavController()
    val inventoryNav = rememberNavController()
    val leadsNav = rememberNavController()
    val homeEntry by homeNav.currentBackStackEntryAsState()
    val inventoryEntry by inventoryNav.currentBackStackEntryAsState()
    val leadsEntry by leadsNav.currentBackStackEntryAsState()

    // Show the bottom nav only on each tab's root list screen — hide it on any pushed detail
    // (product/lead/ranking/add) and on Settings, so those full-screen sub-pages own the frame.
    val showBottomNav = when (selected) {
        AppDestination.HOME -> homeEntry?.destination?.route == HOME_ROUTE_DASHBOARD
        // The Cari tab is a full-screen global search with its own bottom search bar — never
        // show the floating nav over it (or its pushed detail/browse screens).
        AppDestination.INVENTORY -> false
        AppDestination.LEADS -> leadsEntry?.destination?.route == LEADS_ROUTE_LIST
        AppDestination.SETTINGS -> false
    }

    // Tab switching here is driven by `selected` state, not a NavController, so system back has
    // nothing on a back stack to pop when a non-Home tab is at its root — it would fall straight
    // through and exit the app (e.g. pressing back on the Cari/global-search screen). This single
    // handler owns back for the *selected* tab: pop that tab's own nested stack first (detail →
    // list), then fall back to Home, and only exit from Home's root. Reading the observed entry
    // keeps `canPopSelected` fresh across navigations.
    val selectedNav: NavHostController? = when (selected) {
        AppDestination.HOME -> homeNav
        AppDestination.INVENTORY -> inventoryNav
        AppDestination.LEADS -> leadsNav
        AppDestination.SETTINGS -> null
    }
    val selectedEntry = when (selected) {
        AppDestination.HOME -> homeEntry
        AppDestination.INVENTORY -> inventoryEntry
        AppDestination.LEADS -> leadsEntry
        AppDestination.SETTINGS -> null
    }
    val canPopSelected = selectedEntry != null && selectedNav?.previousBackStackEntry != null

    Scaffold(
        // Each destination owns its own header/insets; the floating nav overlays content and
        // consumes its own nav-bar inset, so the Scaffold reserves nothing.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        fun navItem(destination: AppDestination) = TridjayaNavItem(
            icon = destination.icon,
            label = destination.label,
            selected = selected == destination,
            onClick = { selected = destination }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // Every tab is composed once (on first visit) and then kept alive for the rest of
                // the session — only its visibility toggles. This is what stops switching tabs from
                // tearing down and recreating each tab's NavHost/ViewModels (and re-fetching data)
                // every single time.
                val visitedDestinations = remember { mutableStateListOf(selected) }
                LaunchedEffect(selected) {
                    if (selected !in visitedDestinations) visitedDestinations.add(selected)
                }
                // Horizontal shared-axis slide between tabs (Rhythm-style), instead of an instant
                // swap. Kept-alive tabs just animate translationX/alpha — nothing is torn down. A
                // tab left of the selected one sits one screen-width to the left, one to the right
                // sits to the right; switching slides the incoming tab in from its side.
                val selectedOrder = tabOrder(selected)
                Box(modifier = Modifier.fillMaxSize()) {
                    visitedDestinations.forEach { destination ->
                        val isActive = destination == selected
                        val targetOffset = (tabOrder(destination) - selectedOrder)
                            .toFloat().coerceIn(-1f, 1f)
                        // Match Rhythm's horizontal shared-axis timing: 500ms slide, 400ms fade.
                        val slide by animateFloatAsState(
                            targetValue = targetOffset,
                            animationSpec = tween(durationMillis = 500, easing = EaseInOutQuart),
                            label = "tab_slide"
                        )
                        val tabAlpha by animateFloatAsState(
                            targetValue = if (isActive) 1f else 0f,
                            animationSpec = tween(durationMillis = 400),
                            label = "tab_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isActive) 1f else 0f)
                                .graphicsLayer {
                                    translationX = slide * size.width
                                    alpha = tabAlpha
                                }
                                .blockInputWhen(disabled = !isActive)
                        ) {
                            DestinationContent(
                                destination = destination,
                                onSettingsClick = { selected = AppDestination.SETTINGS },
                                onSettingsBack = { selected = AppDestination.HOME },
                                onCloseSearch = { selected = AppDestination.HOME },
                                onQuickAccessInventory = {
                                    selected = AppDestination.INVENTORY
                                    inventoryOpenListTrigger++
                                },
                                onQuickAccessLeads = { selected = AppDestination.LEADS },
                                inventoryOpenListSignal = inventoryOpenListTrigger,
                                homeNav = homeNav,
                                inventoryNav = inventoryNav,
                                leadsNav = leadsNav
                            )
                        }
                    }
                }
            }

            // Rhythm layout: floating pill (Home + Prospek) + separate search FAB, overlaying
            // the content — the content scrolls behind it instead of being pushed above a bar.
            // (Scrollable screens add ~100dp bottom clearance so nothing hides permanently.)
            // Slides away on detail/sub-screens.
            AnimatedVisibility(
                visible = showBottomNav,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                TridjayaFloatingNav(
                    pillItems = destinations
                        .filter { it != AppDestination.INVENTORY }
                        .map { navItem(it) },
                    searchItem = navItem(AppDestination.INVENTORY)
                )
            }
        }
    }

    // Composed after the Scaffold so it registers last and takes priority over the (kept-alive)
    // per-tab NavHosts' own back callbacks — this stops a background tab from stealing a back press
    // and routes it to the selected tab instead. Disabled only on Home's root, where back should
    // exit the app as usual.
    BackHandler(enabled = canPopSelected || selected != AppDestination.HOME) {
        when {
            canPopSelected -> selectedNav?.popBackStack()
            else -> selected = AppDestination.HOME
        }
    }
}

/** Left-to-right screen order used to decide which side a tab slides in from on switch. */
private fun tabOrder(destination: AppDestination): Int = when (destination) {
    AppDestination.HOME -> 0
    AppDestination.LEADS -> 1
    AppDestination.INVENTORY -> 2
    AppDestination.SETTINGS -> 3
}

/** Swallows all pointer input for this subtree so an off-screen (alpha=0) kept-alive tab can't steal touches from the active one. */
private fun Modifier.blockInputWhen(disabled: Boolean): Modifier {
    if (!disabled) return this
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
}
