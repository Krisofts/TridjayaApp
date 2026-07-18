package com.krisoft.tridjayaelektronik.ui.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenEntity
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenMetric
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.domain.sales.StandingRow
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard

private val MEDALS = listOf("🥇", "🥈", "🥉") // 🥇🥈🥉

private fun formatRupiah(value: Double): String {
    val text = value.toLong().toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

private fun formatValue(value: Double, metric: KlasemenMetric): String =
    if (metric == KlasemenMetric.OMSET) formatRupiah(value)
    else "${Math.round(value).toString().reversed().chunked(3).joinToString(".").reversed()} unit"

/**
 * Web-parity Klasemen (tridjaya.com/dashboard/klasemen): league-style month-to-date standings
 * per sales OR per cabang, with rank movement vs the previous day, entity/metric toggles,
 * day cutoff, and name search. Added as LazyColumn items so long standings stay lazy.
 */
fun LazyListScope.klasemenSection(
    state: KlasemenUiState,
    standings: List<StandingRow>,
    filtered: List<StandingRow>,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit
) {
    item(key = "klasemen_controls") {
        KlasemenControlsCard(state, onSearch, onRefresh)
    }

    when {
        state.isLoading -> {
            items(count = 5) {
                SkeletonCard(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        state.errorMessage != null && state.rows.isEmpty() -> {
            item(key = "klasemen_error") {
                ExpressiveInlineError(
                    message = state.errorMessage,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        standings.isEmpty() -> {
            item(key = "klasemen_empty") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = "Belum ada penjualan",
                        subtitle = "Belum ada penjualan ${if (state.entity == KlasemenEntity.SALES) "sales" else "cabang"} bulan ini"
                    )
                }
            }
        }
        filtered.isEmpty() -> {
            item(key = "klasemen_no_match") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = "Tidak ditemukan",
                        subtitle = "Tak ada nama yang cocok dengan \"${state.search}\""
                    )
                }
            }
        }
        else -> {
            items(items = filtered, key = { "klasemen_${it.name}" }) { row ->
                KlasemenRowCard(row = row, metric = state.metric)
            }
        }
    }
}

/**
 * Slim header card: dynamic title + active-filter summary + refresh. The actual controls
 * (entity/metric/cutoff, search) live in the app-bar-triggered bottom sheets.
 */
@Composable
private fun KlasemenControlsCard(
    state: KlasemenUiState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val today = KlasemenStandings.todayIso()
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Klasemen ${if (state.entity == KlasemenEntity.SALES) "Sales" else "Cabang"} — ${KlasemenStandings.monthLabel(state.cutoffIso)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (state.metric == KlasemenMetric.OMSET) "Omset" else "Unit"} s/d ${dayLabel(state.cutoffIso, today)} · panah = pergerakan vs hari sebelumnya",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Muat ulang klasemen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (state.search.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = "Cari: \"${state.search}\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = { onSearch("") }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Hapus pencarian",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** App-bar-triggered filter sheet: entity, metric, and day-cutoff controls (applied live). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KlasemenFilterSheet(
    state: KlasemenUiState,
    onEntity: (KlasemenEntity) -> Unit,
    onMetric: (KlasemenMetric) -> Unit,
    onCutoff: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val today = KlasemenStandings.todayIso()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Filter Klasemen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tampilan",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KlasemenChip(
                    label = "Sales",
                    selected = state.entity == KlasemenEntity.SALES,
                    onClick = { onEntity(KlasemenEntity.SALES) }
                )
                KlasemenChip(
                    label = "Cabang",
                    selected = state.entity == KlasemenEntity.CABANG,
                    onClick = { onEntity(KlasemenEntity.CABANG) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Metrik",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            MiniSegmented(
                options = listOf("Omset", "Unit"),
                selectedIndex = if (state.metric == KlasemenMetric.OMSET) 0 else 1,
                onSelect = { onMetric(if (it == 0) KlasemenMetric.OMSET else KlasemenMetric.UNIT) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Peringkat s/d",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "Hari Ini" to today,
                    "Kemarin" to KlasemenStandings.shiftDays(today, -1),
                    "2 Hari Lalu" to KlasemenStandings.shiftDays(today, -2)
                ).forEach { (label, iso) ->
                    KlasemenChip(
                        label = label,
                        selected = state.cutoffIso == iso,
                        onClick = { onCutoff(iso) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            ExpressiveFilledButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Selesai")
            }
        }
    }
}

/** App-bar-triggered search sheet: autofocused name search, applied live to the standings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KlasemenSearchSheet(
    state: KlasemenUiState,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .imePadding()
        ) {
            Text(
                text = "Cari ${if (state.entity == KlasemenEntity.SALES) "Sales" else "Cabang"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExpressiveTextField(
                value = state.search,
                onValueChange = onSearch,
                placeholder = "Cari nama ${if (state.entity == KlasemenEntity.SALES) "sales" else "cabang"}...",
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                trailingIcon = if (state.search.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
                        }
                    }
                } else null
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveFilledButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Selesai")
            }
        }
    }
}

/** Pill filter chip used across the klasemen filter sheet (entity + day cutoff). */
@Composable
private fun KlasemenChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Compact two-option segmented toggle, visually equivalent to the web's ctl-seg control. */
@Composable
private fun MiniSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(3.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KlasemenRowCard(row: StandingRow, metric: KlasemenMetric) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        containerColor = if (row.rank <= 3) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (row.rank <= 3) {
                    Text(text = MEDALS[row.rank - 1], style = MaterialTheme.typography.titleMedium)
                } else {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${row.rank}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                MovementBadge(delta = row.delta)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatValue(row.value, metric),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Rank movement badge: BARU / Tetap / green climb / red drop — the web's MovementBadge. */
@Composable
private fun MovementBadge(delta: Int?) {
    when {
        delta == null -> {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "BARU",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        delta == 0 -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "Tetap",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            val naik = delta > 0
            val color = if (naik) Color(0xFF10B981) else Color(0xFFEF4444)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (naik) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "${kotlin.math.abs(delta)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

private fun dayLabel(iso: String, today: String): String = when (iso) {
    today -> "Hari Ini"
    KlasemenStandings.shiftDays(today, -1) -> "Kemarin"
    else -> iso
}
