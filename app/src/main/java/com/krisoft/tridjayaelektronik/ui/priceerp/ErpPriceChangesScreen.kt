package com.krisoft.tridjayaelektronik.ui.priceerp

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PriceChange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.ErpPriceChangeItemDto
import com.krisoft.tridjayaelektronik.data.model.relativeTimeId
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

private val NaikColor = Color(0xFFF04438)
private val TurunColor = Color(0xFF12B76A)

private fun formatRupiahLong(value: Long): String {
    val text = kotlin.math.abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

/** Perubahan Harga GS — baca saja: filter cabang (Jawa Barat/Manado) + search kode/nama. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErpPriceChangesScreen(
    onBack: () -> Unit,
    viewModel: ErpPriceChangesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    val filtered = remember(state.items, state.search, state.cabang) {
        val query = state.search.trim()
        state.items.filter { item ->
            val matchesCabang = state.cabang == null || item.kodeCabang.equals(state.cabang, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                item.kodeBarang.contains(query, ignoreCase = true) ||
                item.namaBarang.contains(query, ignoreCase = true)
            matchesCabang && matchesQuery
        }
    }

    TridjayaCollapsibleHeader(title = "Perubahan Harga GS", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            ExpressiveTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                placeholder = "Cari kode atau nama barang"
            )
            CabangFilterChips(selected = state.cabang, onSelect = viewModel::onCabangChange)

            if (!state.snapshotAt.isNullOrBlank()) {
                Text(
                    text = "Data harga per ${relativeTimeId(state.snapshotAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.items.isEmpty() -> {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            repeat(6) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                        }
                    }
                    state.error != null && state.items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = viewModel::load)
                        }
                    }
                    // count==0 & snapshotAt==null = belum ada baseline sama sekali (state kosong
                    // khusus), bukan error — beda dari "tidak ada perubahan setelah filter" di bawah.
                    state.count == 0 && state.snapshotAt == null -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.PriceChange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Menunggu sync pertama",
                                subtitle = "Belum ada baseline harga tercatat. Data akan tampil setelah sinkronisasi pertama berjalan."
                            )
                        }
                    }
                    filtered.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.PriceChange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Tidak ada perubahan",
                                subtitle = "Tidak ada perubahan harga yang cocok dengan filter/pencarian."
                            )
                        }
                    }
                    else -> {
                        val pullState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = state.loading,
                            onRefresh = viewModel::load,
                            state = pullState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {
                                PullToRefreshDefaults.Indicator(
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    isRefreshing = state.loading,
                                    state = pullState,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + navBottom),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { "${it.kodeBarang}_${it.kodeCabang}_${it.detectedAt}" }) { item ->
                                    PriceChangeRow(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabangFilterChips(selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Semua Cabang") },
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = selected == "1-01",
                onClick = { onSelect("1-01") },
                label = { Text("Jawa Barat") },
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = selected == "5-01",
                onClick = { onSelect("5-01") },
                label = { Text("Manado") },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

@Composable
private fun PriceChangeRow(item: ErpPriceChangeItemDto) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (item.namaBarang.isBlank()) {
                        Text(
                            text = "(dihapus)",
                            style = MaterialTheme.typography.titleSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(text = item.namaBarang, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${item.kodeBarang} · ${item.kategori} · ${item.cabang}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NaikTurunBadge(isNaik = item.isNaik)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatRupiahLong(item.hargaLama),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRupiahLong(item.hargaBaru),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isNaik) NaikColor else TurunColor
                )
            }
            item.detectedAt?.let {
                Text(
                    text = relativeTimeId(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun NaikTurunBadge(isNaik: Boolean) {
    val color = if (isNaik) NaikColor else TurunColor
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = if (isNaik) "▲ Naik" else "▼ Turun",
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
