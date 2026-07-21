package com.krisoft.tridjayaelektronik.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.LeadSummary
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenEntity
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.sales.KlasemenRowCard
import com.krisoft.tridjayaelektronik.ui.sales.KlasemenViewModel
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonBox
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonLine
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    onViewMoreBranches: () -> Unit = {},
    onViewMoreSales: () -> Unit = {},
    onBranchClick: (LeaderboardBranchItemDto) -> Unit = {},
    onSalesClick: (LeaderboardSalesItemDto) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onQuickAccessInventory: () -> Unit = {},
    onQuickAccessLeads: () -> Unit = {},
    onQuickAccessIndent: () -> Unit = {},
    onQuickAccessSales: () -> Unit = {},
    onQuickAccessOpname: () -> Unit = {},
    onQuickAccessDelivery: () -> Unit = {},
    onQuickAccessAbsen: () -> Unit = {},
    /** Buka satu menu alur SPK (dummy) berdasarkan key: input/diskon/kasir/pdi/kontrol/driver. */
    onSpkMenu: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val layout by viewModel.layout.collectAsState()
    var showCustomizeSheet by remember { mutableStateOf(false) }
    // Content scrolls behind the floating nav; clear it (pill ≈ 88dp) plus the system nav-bar inset.
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

    TridjayaCollapsibleHeader(
        title = "Tridjaya.com",
        actions = {
            ExpressiveFilledIconButton(
                onClick = { showCustomizeSheet = true },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Atur tampilan Home")
            }
            Spacer(modifier = Modifier.size(8.dp))
            ExpressiveFilledIconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, contentDescription = "Pengaturan")
            }
        }
    ) { contentModifier ->
        Box(modifier = contentModifier) {
            when {
                state.isLoading -> HomeLoadingSkeleton()
                state.errorMessage != null && state.kpi == null && state.target == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat dashboard.",
                            onRetry = { viewModel.loadDashboard(forceRefresh = true) }
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomClearance),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item { GreetingCard(userName = state.user?.name.orEmpty()) }
                        // Every section renders as a full-width titled card (same style as the rankings),
                        // in the user's chosen order from the Tune sheet.
                        layout.visibleOrdered.forEach { section ->
                            homeSection(
                                section, state, onViewMoreBranches, onViewMoreSales, onBranchClick, onSalesClick,
                                onQuickAccessInventory, onQuickAccessLeads, onQuickAccessIndent, onQuickAccessSales,
                                onQuickAccessOpname, onQuickAccessDelivery, onQuickAccessAbsen, onSpkMenu
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomizeSheet) {
        HomeCustomizeSheet(
            layout = layout,
            onMoveUp = viewModel::moveSectionUp,
            onMoveDown = viewModel::moveSectionDown,
            onToggle = viewModel::setSectionVisible,
            onReset = viewModel::resetLayout,
            onDismiss = { showCustomizeSheet = false }
        )
    }
}

/** Emits one dashboard section: a titled header + its full-width content card. */
private fun LazyListScope.homeSection(
    section: HomeSection,
    state: HomeUiState,
    onViewMoreBranches: () -> Unit,
    onViewMoreSales: () -> Unit,
    onBranchClick: (LeaderboardBranchItemDto) -> Unit,
    onSalesClick: (LeaderboardSalesItemDto) -> Unit,
    onQuickAccessInventory: () -> Unit,
    onQuickAccessLeads: () -> Unit,
    onQuickAccessIndent: () -> Unit,
    onQuickAccessSales: () -> Unit,
    onQuickAccessOpname: () -> Unit,
    onQuickAccessDelivery: () -> Unit,
    onQuickAccessAbsen: () -> Unit,
    onSpkMenu: (String) -> Unit
) {
    when (section) {
        HomeSection.QUICK_ACCESS -> {
            item { SectionHeader(title = "Akses Cepat", icon = Icons.Rounded.Bolt) }
            item {
                val role = state.user?.role
                QuickAccessRow(
                    onInventory = onQuickAccessInventory,
                    onLeads = onQuickAccessLeads,
                    onIndent = onQuickAccessIndent,
                    onSales = onQuickAccessSales,
                    onOpname = onQuickAccessOpname,
                    onDelivery = onQuickAccessDelivery,
                    onAbsen = onQuickAccessAbsen,
                    onSpkMenu = onSpkMenu,
                    showIndent = canAccessIndent(role),
                    showOpname = canAccessOpname(role),
                    // Fitur dummy untuk review desain — belum digating role (aktifkan
                    // canAccessDelivery(role) begitu di-wire ke API delivery-schedules).
                    showDelivery = true
                )
            }
        }
        HomeSection.CRM_SUMMARY -> {
            item { SectionHeader(title = "Ringkasan CRM", icon = Icons.Rounded.Groups) }
            item { CrmCard(summary = state.crmSummary) }
        }
        HomeSection.LEADERBOARD -> {
            item { SectionHeader(title = "Klasemen", icon = Icons.Rounded.EmojiEvents) }
            item { HomeKlasemenCard(onOpenSales = onQuickAccessSales) }
        }
    }
}

/**
 * Widget Klasemen di Home — memakai data & gaya yang sama persis dengan layar Sales
 * ([KlasemenViewModel] + [KlasemenRowCard]): kartu-per-baris, medali 🥇🥈🥉, dan
 * MovementBadge (naik/turun/BARU). Default periode = **kemarin**; metrik otomatis
 * mengikuti entity (Sales → unit, Cabang → omset), sama seperti web /dashboard/klasemen.
 * Top 5 saja; "Lihat semua" membuka layar Sales lengkap.
 */
@Composable
private fun HomeKlasemenCard(onOpenSales: () -> Unit) {
    val vm: KlasemenViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()

    // Default klasemen di Home = hari kemarin (layar Sales tetap default hari ini — VM terpisah per entry).
    LaunchedEffect(Unit) {
        val kemarin = KlasemenStandings.shiftDays(KlasemenStandings.todayIso(), -1)
        if (state.cutoffIso != kemarin) vm.setCutoff(kemarin)
    }

    val isSales = state.entity == KlasemenEntity.SALES
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LeaderboardTab("Sales", isSales, Modifier.weight(1f)) { vm.setEntity(KlasemenEntity.SALES) }
            LeaderboardTab("Cabang", !isSales, Modifier.weight(1f)) { vm.setEntity(KlasemenEntity.CABANG) }
        }

        Text(
            text = if (isSales) "Peringkat sales (unit) · kemarin" else "Peringkat cabang (omset) · kemarin",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        when {
            state.isLoading && state.standings.isEmpty() -> repeat(3) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp)
                )
            }
            state.errorMessage != null && state.standings.isEmpty() ->
                EmptyRankRow(state.errorMessage ?: "Gagal memuat klasemen")
            state.standings.isEmpty() -> EmptyRankRow("Belum ada data klasemen kemarin")
            else -> state.standings.take(5).forEach { row -> KlasemenRowCard(row, state.metric) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lihat semua",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenSales() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun LeaderboardTab(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyRankRow(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
        textAlign = TextAlign.Center
    )
}

/**
 * Role gates for the quick-access menus — mirrors the backend gateway's route guards so the
 * user never sees a menu that would only answer 403:
 * - Indent (`require_indent_submitter` GET): admin, owner, indent-approver, manager, kepala-cabang.
 * - Opname (service `has_admin`/`has_manager`): admin, admin-stok, kepala-cabang, manager, owner.
 * Inventory/Prospek/Sales are open to every logged-in role. A null role (profile not loaded
 * yet) hides the gated tiles — they appear as soon as the cached profile lands.
 */
private val INDENT_MENU_ROLES = setOf("admin", "owner", "indent-approver", "manager", "kepala-cabang")
private val OPNAME_MENU_ROLES = setOf("admin", "admin-stok", "kepala-cabang", "manager", "owner")
// Selaras DELIVERY_ROLES di backend kinerja-service (admin/sales/admin-sales) + owner/manager.
private val DELIVERY_MENU_ROLES = setOf("admin", "sales", "admin-sales", "admin_sales", "owner", "manager", "kepala-cabang")

internal fun canAccessIndent(role: String?): Boolean =
    role?.trim()?.lowercase() in INDENT_MENU_ROLES

internal fun canAccessOpname(role: String?): Boolean =
    role?.trim()?.lowercase() in OPNAME_MENU_ROLES

internal fun canAccessDelivery(role: String?): Boolean =
    role?.trim()?.lowercase() in DELIVERY_MENU_ROLES

/**
 * Shortcut row to the app's most-used destinations. Five tiles no longer fit a fixed-width
 * phone row, so this scrolls horizontally with fixed-width tiles instead of weight-splitting.
 */
@Composable
private fun QuickAccessRow(
    onInventory: () -> Unit,
    onLeads: () -> Unit,
    onIndent: () -> Unit,
    onSales: () -> Unit,
    onOpname: () -> Unit,
    onDelivery: () -> Unit,
    onAbsen: () -> Unit,
    onSpkMenu: (String) -> Unit,
    showIndent: Boolean = true,
    showOpname: Boolean = true,
    showDelivery: Boolean = true
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(224.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            QuickAccessTile(
                icon = Icons.Rounded.Fingerprint,
                label = "Absen",
                tint = Color(0xFF0E9384),
                onClick = onAbsen,
                modifier = Modifier.width(86.dp)
            )
        }
        // Alur pengiriman SPK per-tahap (nyata → inventory-service). RBAC di backend; tampil semua.
        item {
            QuickAccessTile(Icons.Rounded.Description, "Input SPK", Color(0xFF1E63E9), { onSpkMenu("input") }, Modifier.width(86.dp))
        }
        item {
            QuickAccessTile(Icons.Rounded.FactCheck, "PDI", Color(0xFF6941C6), { onSpkMenu("pdi") }, Modifier.width(86.dp))
        }
        item {
            QuickAccessTile(Icons.Rounded.PointOfSale, "Kasir SPK", Color(0xFF0086C9), { onSpkMenu("kasir") }, Modifier.width(86.dp))
        }
        item {
            QuickAccessTile(Icons.Rounded.Receipt, "Surat Jalan", Color(0xFF0E9384), { onSpkMenu("note") }, Modifier.width(86.dp))
        }
        item {
            QuickAccessTile(Icons.Rounded.CalendarToday, "Jadwal", Color(0xFF1565C0), { onSpkMenu("jadwal") }, Modifier.width(86.dp))
        }
        item {
            QuickAccessTile(Icons.Rounded.LocalShipping, "Driver", Color(0xFF6941C6), { onSpkMenu("driver") }, Modifier.width(86.dp))
        }
        item {
            QuickAccessTile(
                icon = Icons.Rounded.Inventory2,
                label = "Inventory",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onInventory,
                modifier = Modifier.width(86.dp)
            )
        }
        item {
            QuickAccessTile(
                icon = Icons.Rounded.Groups,
                label = "Prospek",
                tint = MaterialTheme.colorScheme.tertiary,
                onClick = onLeads,
                modifier = Modifier.width(86.dp)
            )
        }
        if (showIndent) {
            item {
                QuickAccessTile(
                    icon = Icons.Rounded.PlaylistAddCheck,
                    label = "Indent",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onIndent,
                    modifier = Modifier.width(86.dp)
                )
            }
        }
        item {
            QuickAccessTile(
                icon = Icons.Rounded.BarChart,
                label = "Sales",
                tint = Color(0xFF12B76A),
                onClick = onSales,
                modifier = Modifier.width(86.dp)
            )
        }
        if (showOpname) {
            item {
                QuickAccessTile(
                    icon = Icons.Rounded.FactCheck,
                    label = "Opname",
                    tint = Color(0xFF0086C9),
                    onClick = onOpname,
                    modifier = Modifier.width(86.dp)
                )
            }
        }
        if (showDelivery) {
            item {
                QuickAccessTile(
                    icon = Icons.Rounded.LocalShipping,
                    label = "Kirim",
                    tint = Color(0xFF6941C6),
                    onClick = onDelivery,
                    modifier = Modifier.width(86.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Sales KPI — MTD hero (vs last month) + a 2×2 grid of today's metrics, each with growth. */
@Composable
internal fun KpiCard(kpi: ExecutiveKpiDto?) {
    if (kpi == null) {
        PlaceholderCard("Belum ada data KPI")
        return
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Hero: MTD amount in a tinted panel — label up top, growth badge pinned top-right,
            // big value below.
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Amount Bulan Ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GrowthBadge(kpi.mtd.growthPct)
                    }
                    Text(
                        text = formatRupiah(kpi.mtd.current),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Bulan lalu ${formatRupiah(kpi.mtd.lastMonth)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Text(
                text = "Performa Hari Ini",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.AccountBalanceWallet, "Amount", formatRupiah(kpi.revenue.today), kpi.revenue.growthPct)
                MiniMetric(Icons.Rounded.Receipt, "Transaksi", "${kpi.transaction.today.toInt()}", kpi.transaction.growthPct)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.Inventory2, "Unit Terjual", "${kpi.unit.today.toInt()}", kpi.unit.growthPct)
                MiniMetric(Icons.Rounded.Calculate, "Rata²/Transaksi", formatRupiah(kpi.avgTransaction), null)
            }
        }
    }
}

/** Small circular tinted icon badge — the "icon chip" used on KPI/target tile headers. */
@Composable
internal fun IconChip(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(26.dp), shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

/** A small metric tile inside a card: icon chip + label + value + optional growth arrow. */
@Composable
internal fun RowScope.MiniMetric(icon: ImageVector, label: String, value: String, growthPct: Double?) {
    Surface(
        modifier = Modifier.weight(1f).heightIn(min = 84.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                IconChip(icon = icon, tint = MaterialTheme.colorScheme.primary)
            }
            // Full (un-abbreviated) amounts can run long — allow a second line instead of
            // ellipsis-truncating a currency figure, which would hide real digits.
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (growthPct != null) {
                val positive = growthPct >= 0
                Text(
                    text = "${if (positive) "▲" else "▼"} %.1f%%".format(kotlin.math.abs(growthPct)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (positive) Color(0xFF12B76A) else Color(0xFFF04438),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun TargetCard(target: MonthlyTargetDto?) {
    if (target == null) {
        PlaceholderCard("Belum ada data target")
        return
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pencapaian Target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TargetStatusChip(willAchieve = target.projection.willAchieve)
                    }
                    Text(
                        text = "%.1f%%".format(target.achievementPct),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    LinearProgressIndicator(
                        progress = { (target.achievementPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 8.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = formatRupiah(target.actual), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = "dari ${formatRupiah(target.target)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "Seharusnya %.1f%%  •  hari ke-%d dari %d".format(target.expectedPct, target.dayPassed, target.daysInMonth),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.AccountBalanceWallet, "Sisa Target", formatRupiahShort(target.remainingRevenue), null)
                MiniMetric(Icons.Rounded.CalendarToday, "Sisa Hari", "${target.remainingDays} hari", null)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(Icons.Rounded.TrendingUp, "Butuh / Hari", formatRupiahShort(target.neededPerDay), null)
                MiniMetric(Icons.Rounded.Flag, "Target / Hari", formatRupiahShort(target.targetPerDay), null)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Proyeksi Akhir Bulan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatRupiah(target.projection.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Estimasi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "%.1f%%".format(target.projection.achievementPct), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (target.projection.gap > 0) {
                Text(
                    text = "Kurang ${formatRupiahShort(target.projection.gap)} lagi untuk capai target",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB5670C),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun TargetStatusChip(willAchieve: Boolean) {
    val (label, color) = if (willAchieve) "On Track" to Color(0xFF2E7D32) else "Perlu Usaha" to Color(0xFFB5670C)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CrmCard(summary: LeadSummary?) {
    if (summary == null) {
        PlaceholderCard("Belum ada data prospek")
        return
    }
    // Desain tenang & rapi: satu angka utama (nilai pipeline) + baris statistik seragam.
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Nilai Pipeline Aktif",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatRupiah(summary.openEstimatedValue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CrmStat("Hari Ini", summary.todayCount, MaterialTheme.colorScheme.onSurface)
                CrmStatDivider()
                CrmStat("Total", summary.totalCount, MaterialTheme.colorScheme.onSurface)
                CrmStatDivider()
                CrmStat("Deal", summary.wonThisMonth, Color(0xFF2E7D32))
                CrmStatDivider()
                CrmStat("Gagal", summary.lostThisMonth, Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun RowScope.CrmStat(label: String, value: Int, valueColor: Color) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CrmStatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

@Composable
internal fun PlaceholderCard(text: String) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    icon: ImageVector,
    onViewMore: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (onViewMore != null) {
            IconButton(onClick = onViewMore) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Lihat semua $title",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Soft 3D sphere decoration (radial highlight off-center), same clay language as the flyers. */
private fun DrawScope.greetingSphere(cx: Float, cy: Float, r: Float, base: Color, alpha: Float = 1f) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(base, Color.White, 0.55f), base, lerp(base, Color.Black, 0.08f)),
            center = Offset(cx - r * 0.35f, cy - r * 0.4f),
            radius = r * 1.7f
        ),
        radius = r,
        center = Offset(cx, cy),
        alpha = alpha
    )
}

private data class GreetingUi(
    val label: String,
    val quote: String,
    val icon: ImageVector,
    /** Gradien latar kartu greeting. */
    val gradient: List<Color>,
    /** Warna teks di atas gradien. */
    val onColor: Color,
    /** Tint ikon (harus kontras dengan badge putih). */
    val iconTint: Color
)

/** Tema sapaan per waktu — background + ikon berubah pagi/siang/sore/malam. */
private fun timeGreeting(hour: Int): GreetingUi = when (hour) {
    in 5..10 -> GreetingUi(   // Pagi — sunrise hangat
        "Selamat Pagi",
        "Awali harimu dengan senyuman — semoga rezeki & closing mengalir deras hari ini!",
        Icons.Rounded.WbSunny,
        listOf(Color(0xFFFFD07A), Color(0xFFFF9F5A)),
        Color(0xFF3E2A12),
        Color(0xFFF57C00)
    )
    in 11..14 -> GreetingUi(  // Siang — langit cerah
        "Selamat Siang",
        "Tetap semangat & fokus — jangan lupa follow up prospekmu, ya!",
        Icons.Rounded.LightMode,
        listOf(Color(0xFF57ABFF), Color(0xFF2E7CF6)),
        Color.White,
        Color(0xFFFB8C00)
    )
    in 15..18 -> GreetingUi(  // Sore — sunset
        "Selamat Sore",
        "Sedikit lagi! Cek progres targetmu sebelum hari berakhir.",
        Icons.Rounded.WbTwilight,
        listOf(Color(0xFFFF9E6D), Color(0xFFE8577D)),
        Color.White,
        Color(0xFFF4511E)
    )
    else -> GreetingUi(       // Malam
        "Selamat Malam",
        "Kerja kerasmu hari ini luar biasa. Istirahat yang cukup, ya!",
        Icons.Rounded.DarkMode,
        listOf(Color(0xFF3B4A8C), Color(0xFF1E2547)),
        Color.White,
        Color(0xFF3F51B5)
    )
}

/** Tema sapaan musiman (override tema waktu). Tambah case lain untuk perayaan berikutnya
 *  (mis. Desember/Tahun Baru, Ramadan) — tinggal daftarkan bulannya di sini. */
private fun seasonalGreeting(month: Int): GreetingUi? = when (month) {
    Calendar.AUGUST -> GreetingUi(   // Agustus — Kemerdekaan RI
        "Dirgahayu Indonesia",
        "Merdeka! Bawa semangat 45 untuk gebrak target bulan ini.",
        Icons.Rounded.Flag,
        listOf(Color(0xFFF44336), Color(0xFFB71C1C)),
        Color.White,
        Color(0xFFE53935)
    )
    else -> null
}

@Composable
private fun GreetingCard(userName: String) {
    val cal = remember { Calendar.getInstance() }
    val hour = remember { cal.get(Calendar.HOUR_OF_DAY) }
    val month = remember { cal.get(Calendar.MONTH) }
    // Musiman (mis. Agustus) menimpa tema waktu; kalau tidak, pakai tema pagi/siang/sore/malam.
    val greeting = remember(hour, month) { seasonalGreeting(month) ?: timeGreeting(hour) }
    val tanggal = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("in", "ID")).format(cal.time) }

    // ── Animasi berkelanjutan ────────────────────────────────────────────────
    val transition = rememberInfiniteTransition(label = "greeting")
    val iconScale by transition.animateFloat(
        1f, 1.08f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconScale"
    )
    val iconBob by transition.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconBob"
    )
    // Gradien latar "bernapas" — geser titik awal/akhir pelan.
    val gradientPan by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradientPan"
    )
    // Sphere dekoratif mengambang.
    val sphereDrift by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sphereDrift"
    )
    // Kilau (shimmer) menyapu diagonal, jeda di antara sapuan.
    val shimmer by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    // Entrance: muncul dengan fade + naik sedikit.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "enter"
    )

    val shape = RoundedCornerShape(26.dp)
    val base = greeting.gradient.first()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .graphicsLayer { alpha = enter; translationY = (1f - enter) * 24.dp.toPx() }
            .shadow(12.dp, shape, ambientColor = base.copy(alpha = 0.5f), spotColor = base.copy(alpha = 0.5f))
            .clip(shape)
            .drawBehind {
                val w = size.width
                val h = size.height
                // 1) Gradien latar bergerak pelan.
                val pan = (gradientPan - 0.5f) * w * 0.45f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = greeting.gradient,
                        start = Offset(pan, 0f),
                        end = Offset(w + pan, h)
                    )
                )
                // 2) Sheen glossy di atas.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.20f),
                        0.45f to Color.Transparent
                    )
                )
                // 3) Sphere mengambang.
                val d = (sphereDrift - 0.5f) * 14.dp.toPx()
                greetingSphere(w * 0.90f, h * 0.18f + d, 30.dp.toPx(), Color.White, alpha = 0.16f)
                greetingSphere(w * 0.80f, h * 0.92f - d, 18.dp.toPx(), Color.White, alpha = 0.14f)
                greetingSphere(w * 0.97f, h * 0.62f + d * 0.6f, 12.dp.toPx(), Color.White, alpha = 0.12f)
                // 4) Kilau menyapu (band diagonal translusen).
                val band = w * 0.16f
                val sx = -band + shimmer * (w + band * 2)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                        start = Offset(sx - band, 0f),
                        end = Offset(sx + band, h)
                    )
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ikon vektor di badge kaca putih, ditint tema, dengan pulse + bob halus.
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        translationY = iconBob * 5.dp.toPx()
                    }
                    .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
                    .background(Color.White.copy(alpha = 0.94f), CircleShape)
                    .size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = greeting.icon,
                    contentDescription = null,
                    tint = greeting.iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userName.isBlank()) greeting.label else "${greeting.label}, ${userName.substringBefore(' ')}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = greeting.onColor
                )
                Text(
                    text = greeting.quote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = greeting.onColor.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = tanggal,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = greeting.onColor.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun GrowthBadge(pct: Double) {
    val positive = pct >= 0
    val color = if (positive) Color(0xFF12B76A) else Color(0xFFF04438)
    val arrow = if (positive) "▲" else "▼"
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = "$arrow %.1f%%".format(kotlin.math.abs(pct)),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun RankingCard(content: @Composable () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val color = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = CircleShape, modifier = Modifier.size(28.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = "$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun BranchRankingRow(rank: Int, branch: LeaderboardBranchItemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = branch.cabang, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${branch.totalTransaksi} transaksi", style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatRupiah(branch.omset.toDouble()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SalesRankingRow(rank: Int, sales: LeaderboardSalesItemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = sales.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${sales.totalQty} unit · ${sales.totalTransaksi} transaksi", style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatRupiah(sales.revenue.toDouble()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(110.dp), shape = MaterialTheme.shapes.extraLarge)
        repeat(3) {
            SkeletonLine(widthFraction = 0.45f, height = 22.dp)
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(24.dp))
        }
    }
}

internal fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = kotlin.math.abs(rounded).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (rounded < 0) "-Rp $text" else "Rp $text"
}

/** Compact currency for stat cards, e.g. Rp 1,8M / Rp 77,9Jt. */
internal fun formatRupiahShort(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000 -> "%sRp %.1fM".format(sign, abs / 1_000_000_000)
        abs >= 1_000_000 -> "%sRp %.1fJt".format(sign, abs / 1_000_000)
        abs >= 1_000 -> "%sRp %.0fRb".format(sign, abs / 1_000)
        else -> "%sRp %.0f".format(sign, abs)
    }
}
