package com.krisoft.tridjayaelektronik.ui.sales

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.SparklinePointDto
import com.krisoft.tridjayaelektronik.ui.home.HomeLoadingSkeleton
import com.krisoft.tridjayaelektronik.ui.home.KpiCard
import com.krisoft.tridjayaelektronik.ui.home.PlaceholderCard
import com.krisoft.tridjayaelektronik.ui.home.SectionHeader
import com.krisoft.tridjayaelektronik.ui.home.TargetCard
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Dedicated Sales dashboard — the same KPI/Target/leaderboard data as Home's customizable
 * sections, bundled into one fixed screen plus a 7-day revenue trend chart. Reads the same cached
 * bundle Home does ([com.krisoft.tridjayaelektronik.domain.home.GetSalesDashboardUseCase]), so
 * opening this right after Home never forces a redundant network round-trip.
 */
@Composable
fun SalesScreen(
    onBack: () -> Unit,
    onViewMoreBranches: () -> Unit = {},
    onViewMoreSales: () -> Unit = {},
    onBranchClick: (LeaderboardBranchItemDto) -> Unit = {},
    onSalesClick: (LeaderboardSalesItemDto) -> Unit = {},
    viewModel: SalesViewModel = hiltViewModel(),
    klasemenViewModel: KlasemenViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val klasemenState by klasemenViewModel.uiState.collectAsState()
    var showSearchSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    // Standings dihitung di KlasemenViewModel (Dispatchers.Default), bukan saat komposisi.
    val standings = klasemenState.standings
    // Search filters the visible rows but keeps ranks/medals from the full standings (web parity).
    val filteredStandings = remember(standings, klasemenState.search) {
        val term = klasemenState.search.trim()
        if (term.isEmpty()) standings else standings.filter { it.name.contains(term, ignoreCase = true) }
    }

    TridjayaCollapsibleHeader(
        title = "Sales",
        onBack = onBack,
        actions = {
            ExpressiveFilledIconButton(onClick = { showSearchSheet = true }) {
                Icon(Icons.Rounded.Search, contentDescription = "Cari nama di klasemen")
            }
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveFilledIconButton(onClick = { showFilterSheet = true }) {
                Icon(Icons.Rounded.Tune, contentDescription = "Filter klasemen")
            }
        }
    ) { contentModifier ->
        Box(modifier = contentModifier) {
            when {
                state.isLoading -> HomeLoadingSkeleton()
                state.errorMessage != null && state.kpi == null && state.target == null &&
                    state.topBranches.isEmpty() && state.topSales.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat data sales.",
                            onRetry = { viewModel.load(forceRefresh = true) }
                        )
                    }
                }
                else -> {
                    // Edge-to-edge: the content draws under the gesture nav bar, so the last row
                    // needs the nav inset added to its clearance or it gets clipped behind it.
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Executive sections are role-guarded (403 for regular sales accounts) —
                        // hide them entirely instead of showing empty placeholders, so a
                        // klasemen-only role gets straight to the leaderboards.
                        if (state.kpi != null) {
                            item { SectionHeader(title = "Sales KPI", icon = Icons.Rounded.Insights) }
                            item { KpiCard(kpi = state.kpi) }
                        }
                        if (state.target != null) {
                            item { SectionHeader(title = "Target Bulanan", icon = Icons.Rounded.TrackChanges) }
                            item { TargetCard(target = state.target) }
                        }
                        if (state.sparkline.size >= 2) {
                            item { SectionHeader(title = "Tren Revenue 7 Hari", icon = Icons.Rounded.ShowChart) }
                            item { SparklineCard(points = state.sparkline) }
                        }

                        // Web-parity interactive Klasemen (dashboard/klasemen): replaces the old
                        // static top-5 Ranking Cabang / Klasemen Sales cards.
                        item { SectionHeader(title = "Klasemen", icon = Icons.Rounded.Star) }
                        klasemenSection(
                            state = klasemenState,
                            standings = standings,
                            filtered = filteredStandings,
                            onSearch = klasemenViewModel::setSearch,
                            onRefresh = { klasemenViewModel.load(forceRefresh = true) }
                        )
                    }
                }
            }
        }
    }

    if (showSearchSheet) {
        KlasemenSearchSheet(
            state = klasemenState,
            onSearch = klasemenViewModel::setSearch,
            onDismiss = { showSearchSheet = false }
        )
    }
    if (showFilterSheet) {
        KlasemenFilterSheet(
            state = klasemenState,
            onEntity = klasemenViewModel::setEntity,
            onMetric = klasemenViewModel::setMetric,
            onCutoff = klasemenViewModel::setCutoff,
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun SparklineCard(points: List<SparklinePointDto>) {
    if (points.size < 2) {
        PlaceholderCard("Belum cukup data untuk tren revenue")
        return
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val lineColor = MaterialTheme.colorScheme.primary
            val maxRevenue = remember(points) { points.maxOf { it.revenue }.coerceAtLeast(1.0) }

            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val stepX = size.width / (points.size - 1)
                val topInset = size.height * 0.08f
                val plotHeight = size.height - topInset
                fun yFor(revenue: Double) = topInset + plotHeight - (revenue / maxRevenue).toFloat() * plotHeight

                val linePath = Path()
                val fillPath = Path()
                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = yFor(point.revenue)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()

                drawPath(fillPath, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f))))
                drawPath(
                    linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                points.forEachIndexed { index, point ->
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(index * stepX, yFor(point.revenue)))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                points.forEach { point ->
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
