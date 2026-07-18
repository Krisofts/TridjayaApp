package com.krisoft.tridjayaelektronik.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Flag
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonBox
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonLine
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.util.Calendar

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
                                onQuickAccessOpname, onQuickAccessDelivery, onQuickAccessAbsen
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
    onQuickAccessAbsen: () -> Unit
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
        HomeSection.RANKING_CABANG -> if (state.topBranches.isNotEmpty()) {
            item { SectionHeader(title = "Ranking Cabang", icon = Icons.Rounded.Star, onViewMore = onViewMoreBranches) }
            item {
                RankingCard {
                    state.topBranches.forEachIndexed { index, branch ->
                        BranchRankingRow(rank = index + 1, branch = branch, onClick = { onBranchClick(branch) })
                    }
                }
            }
        }
        HomeSection.RANKING_SALES -> if (state.topSales.isNotEmpty()) {
            item { SectionHeader(title = "Klasemen Sales", icon = Icons.Rounded.Star, onViewMore = onViewMoreSales) }
            item {
                RankingCard {
                    state.topSales.forEachIndexed { index, sales ->
                        SalesRankingRow(rank = index + 1, sales = sales, onClick = { onSalesClick(sales) })
                    }
                }
            }
        }
    }
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
    showIndent: Boolean = true,
    showOpname: Boolean = true,
    showDelivery: Boolean = true
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Headline: today's prospect input next to the all-time total.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrmHeroTile(
                    label = "Prospek Hari Ini",
                    value = "${summary.todayCount}",
                    icon = Icons.Rounded.CalendarToday,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                CrmHeroTile(
                    label = "Total Prospek",
                    value = "${summary.totalCount}",
                    icon = Icons.Rounded.Groups,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Open", "${summary.openCount}", Color(0xFF1565C0))
                MiniStat("Deal", "${summary.wonThisMonth}", Color(0xFF2E7D32))
                MiniStat("Gagal", "${summary.lostThisMonth}", Color(0xFFC62828))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Nilai Pipeline", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatRupiah(summary.openEstimatedValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CrmHeroTile(
    label: String,
    value: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(16.dp), color = container, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.18f)) {
                Box(modifier = Modifier.padding(7.dp)) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = content
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String, color: Color) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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

// Hour buckets/emoji ported 1:1 from Rhythm's ModernWelcomeSection.
private data class GreetingContent(
    val emoji: String,
    val decorativeEmoji: String,
    val label: String,
    val quotes: List<String>
)

@Composable
private fun GreetingCard(userName: String) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 0..4 -> GreetingContent("🌙", "⭐", "Selamat Malam", listOf("Istirahat cukup, closing lanjut besok pagi.", "Waktu tenang untuk susun strategi besok."))
            in 5..11 -> GreetingContent("☀️", "🌻", "Selamat Pagi", listOf("Awali hari dengan semangat, semoga banyak closing!", "Pagi cerah untuk follow up prospek baru."))
            in 12..16 -> GreetingContent("🌤️", "⚡", "Selamat Siang", listOf("Jangan lupa follow up prospek hari ini.", "Semoga siang ini penuh peluang baru."))
            in 17..20 -> GreetingContent("🌅", "✨", "Selamat Sore", listOf("Saatnya review progress target harian.", "Sore produktif, terus semangat!"))
            else -> GreetingContent("🌙", "🌟", "Selamat Malam", listOf("Tutup hari dengan evaluasi progress.", "Terima kasih atas kerja kerasmu hari ini."))
        }
    }
    val quote = remember(greeting) { greeting.quotes.random() }

    val infiniteTransition = rememberInfiniteTransition(label = "greeting_emoji_pulse")
    val emojiScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(2000), repeatMode = RepeatMode.Reverse),
        label = "greeting_emoji_scale"
    )
    // Flyer-style claymorphism treatment: puffy tinted shadow, top-light sheen, floating 3D
    // spheres — theme-driven (unlike the flyer's fixed palette) so it adapts to preset/dark mode.
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val container = MaterialTheme.colorScheme.primaryContainer
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .shadow(12.dp, shape, ambientColor = primary.copy(alpha = 0.55f), spotColor = primary.copy(alpha = 0.55f))
            .clip(shape)
            .background(container)
            .background(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.30f), 0.45f to Color.Transparent)
            )
            .drawBehind {
                greetingSphere(size.width * 0.90f, size.height * 0.18f, 30.dp.toPx(), primary, alpha = 0.45f)
                greetingSphere(size.width * 0.78f, size.height * 0.88f, 18.dp.toPx(), tertiary, alpha = 0.40f)
                greetingSphere(size.width * 0.97f, size.height * 0.70f, 12.dp.toPx(), primary, alpha = 0.35f)
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji floating in its own little clay ball.
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .graphicsLayer { scaleX = emojiScale; scaleY = emojiScale }
                    .shadow(8.dp, CircleShape, ambientColor = primary.copy(alpha = 0.5f), spotColor = primary.copy(alpha = 0.5f))
                    .background(Color.White.copy(alpha = 0.85f), CircleShape)
                    .background(
                        Brush.verticalGradient(0f to Color.White, 0.6f to Color.Transparent),
                        CircleShape
                    )
                    .size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = greeting.emoji, style = MaterialTheme.typography.headlineMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userName.isBlank()) greeting.label else "${greeting.label}, $userName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = quote,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 5.dp)
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
