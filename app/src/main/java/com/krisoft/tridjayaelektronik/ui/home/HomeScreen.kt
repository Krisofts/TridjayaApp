package com.krisoft.tridjayaelektronik.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrackChanges
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.R
import com.krisoft.tridjayaelektronik.data.LeadSummary
import com.krisoft.tridjayaelektronik.data.model.BranchPerformanceItemDto
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.SalesPerformanceItemDto
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
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val layout by viewModel.layout.collectAsState()
    var showCustomizeSheet by remember { mutableStateOf(false) }
    // Content scrolls behind the floating nav; clear it (pill ≈ 88dp) plus the system nav-bar inset.
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

    TridjayaCollapsibleHeader(
        title = "Beranda",
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
                            homeSection(section, state, onViewMoreBranches, onViewMoreSales)
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
    onViewMoreSales: () -> Unit
) {
    when (section) {
        HomeSection.KPI -> {
            item { SectionHeader(title = "Sales KPI", icon = Icons.Rounded.Insights) }
            item { KpiCard(kpi = state.kpi) }
        }
        HomeSection.TARGET -> {
            item { SectionHeader(title = "Target Bulanan", icon = Icons.Rounded.TrackChanges) }
            item { TargetCard(target = state.target) }
        }
        HomeSection.CRM_SUMMARY -> {
            item { SectionHeader(title = "Ringkasan CRM", icon = Icons.Rounded.Groups) }
            item { CrmCard(summary = state.crmSummary) }
        }
        HomeSection.RANKING_CABANG -> if (state.topBranches.isNotEmpty()) {
            item { SectionHeader(title = "Ranking Cabang", icon = Icons.Rounded.Star, onViewMore = onViewMoreBranches) }
            item {
                RankingCard {
                    state.topBranches.forEachIndexed { index, branch -> BranchRankingRow(rank = index + 1, branch = branch) }
                }
            }
        }
        HomeSection.RANKING_SALES -> if (state.topSales.isNotEmpty()) {
            item { SectionHeader(title = "Ranking Sales", icon = Icons.Rounded.Star, onViewMore = onViewMoreSales) }
            item {
                RankingCard {
                    state.topSales.forEachIndexed { index, sales -> SalesRankingRow(rank = index + 1, sales = sales) }
                }
            }
        }
    }
}

/** Sales KPI — MTD hero (vs last month) + a 2×2 grid of today's metrics, each with growth. */
@Composable
private fun KpiCard(kpi: ExecutiveKpiDto?) {
    if (kpi == null) {
        PlaceholderCard("Belum ada data KPI")
        return
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pendapatan Bulan Ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatRupiahShort(kpi.mtd.current),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Bulan lalu ${formatRupiahShort(kpi.mtd.lastMonth)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GrowthBadge(kpi.mtd.growthPct)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            Text(
                text = "Performa Hari Ini",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("Pendapatan", formatRupiahShort(kpi.revenue.today), kpi.revenue.growthPct)
                MiniMetric("Transaksi", "${kpi.transaction.today.toInt()}", kpi.transaction.growthPct)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("Unit Terjual", "${kpi.unit.today.toInt()}", kpi.unit.growthPct)
                MiniMetric("Rata²/Transaksi", formatRupiahShort(kpi.avgTransaction), null)
            }
        }
    }
}

/** A small metric tile inside a card: label + value + optional growth arrow (uniform size). */
@Composable
private fun RowScope.MiniMetric(label: String, value: String, growthPct: Double?) {
    Surface(
        modifier = Modifier.weight(1f).height(80.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (growthPct != null) {
                val positive = growthPct >= 0
                Text(
                    text = "${if (positive) "▲" else "▼"} %.1f%%".format(kotlin.math.abs(growthPct)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (positive) Color(0xFF2E7D32) else Color(0xFFC62828),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TargetCard(target: MonthlyTargetDto?) {
    if (target == null) {
        PlaceholderCard("Belum ada data target")
        return
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Pencapaian Target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "%.1f%%".format(target.achievementPct),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TargetStatusChip(willAchieve = target.projection.willAchieve)
            }
            LinearProgressIndicator(
                progress = { (target.achievementPct / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 2.dp),
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("Sisa Target", formatRupiahShort(target.remainingRevenue), null)
                MiniMetric("Sisa Hari", "${target.remainingDays} hari", null)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("Butuh / Hari", formatRupiahShort(target.neededPerDay), null)
                MiniMetric("Target / Hari", formatRupiahShort(target.targetPerDay), null)
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
private fun TargetStatusChip(willAchieve: Boolean) {
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Open", "${summary.openCount}", Color(0xFF1565C0))
                MiniStat("Menang", "${summary.wonThisMonth}", Color(0xFF2E7D32))
                MiniStat("Hilang", "${summary.lostThisMonth}", Color(0xFFC62828))
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
private fun RowScope.MiniStat(label: String, value: String, color: Color) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaceholderCard(text: String) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(
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
    val mascotWave by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "mascot_wave_rotation"
    )

    ClayCard(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = greeting.emoji,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .graphicsLayer { scaleX = emojiScale; scaleY = emojiScale }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userName.isBlank()) greeting.label else "${greeting.label}, $userName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = quote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            Image(
                painter = painterResource(id = R.drawable.mascot_greeting),
                contentDescription = "Maskot Tridjaya Elektronik menyapa",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = mascotWave }
            )
        }
    }
}

@Composable
private fun GrowthBadge(pct: Double) {
    val positive = pct >= 0
    val color = if (positive) Color(0xFF2E7D32) else Color(0xFFC62828)
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
private fun RankingCard(content: @Composable () -> Unit) {
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
private fun BranchRankingRow(rank: Int, branch: BranchPerformanceItemDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = branch.branch, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${branch.currentUnit} unit", style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatRupiah(branch.currentAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SalesRankingRow(rank: Int, sales: SalesPerformanceItemDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(rank)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = sales.salesPerson, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${sales.currentUnit} / ${sales.targetUnit} unit", style = MaterialTheme.typography.bodySmall)
            }
            Text(text = formatRupiah(sales.currentAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        if (sales.targetUnit > 0) {
            val achievementPct = sales.currentUnit.toDouble() / sales.targetUnit * 100
            LinearProgressIndicator(
                progress = { (achievementPct / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp)
            )
            Text(
                text = "Pencapaian target: %.1f%%".format(achievementPct),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 40.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun HomeLoadingSkeleton() {
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

private fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = kotlin.math.abs(rounded).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (rounded < 0) "-Rp $text" else "Rp $text"
}

/** Compact currency for stat cards, e.g. Rp 1,8M / Rp 77,9Jt. */
private fun formatRupiahShort(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000 -> "%sRp %.1fM".format(sign, abs / 1_000_000_000)
        abs >= 1_000_000 -> "%sRp %.1fJt".format(sign, abs / 1_000_000)
        abs >= 1_000 -> "%sRp %.0fRb".format(sign, abs / 1_000)
        else -> "%sRp %.0f".format(sign, abs)
    }
}
