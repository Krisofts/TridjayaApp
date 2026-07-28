package com.krisoft.tridjayaelektronik.ui.mutasi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailRowDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriRowDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.text.SimpleDateFormat
import java.util.Locale

private val MasukColor = androidx.compose.ui.graphics.Color(0xFF12B76A)
private val KeluarColor = androidx.compose.ui.graphics.Color(0xFF1E63E9)

/** `tanggal` format ERP mentah `"YYYY-MM-DD HH:MM:SS"` (bukan ISO ber-`T`) — parse manual,
 *  fallback ke string mentah kalau formatnya di luar dugaan. */
private fun formatMutasiTanggal(raw: String): String {
    if (raw.isBlank()) return "-"
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("in", "ID"))
        val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("in", "ID"))
        formatter.format(parser.parse(raw)!!)
    }.getOrDefault(raw)
}

/**
 * Riwayat Mutasi (admin/admin-stok) — arsip baca-saja `GET /inventory/mutasi-histori`.
 * HISTORI-ONLY: web sendiri sedang menyembunyikan alur Ajukan/Keluar/Masuk di balik flag
 * `HISTORI_ONLY=true` (disengaja, lihat brief) — mobile ikut TANPA create/receive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MutasiHistoriScreen(
    onBack: () -> Unit,
    viewModel: MutasiHistoriViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    if (state.selected != null) {
        MutasiHistoriDetailScreen(
            row = state.selected!!,
            loading = state.detailLoading,
            items = state.detailItems,
            error = state.detailError,
            onBack = { viewModel.selectRow(null) }
        )
        return
    }

    val filtered = remember(state.items, state.arahFilter, state.cabangFilter) {
        state.items.filter { row ->
            val matchesArah = when (state.arahFilter) {
                ArahFilter.SEMUA -> true
                ArahFilter.MASUK -> row.arah.equals("IN", ignoreCase = true)
                ArahFilter.KELUAR -> row.arah.equals("OUT", ignoreCase = true)
            }
            val matchesCabang = state.cabangFilter == null ||
                row.cabang.equals(state.cabangFilter, ignoreCase = true)
            matchesArah && matchesCabang
        }
    }

    TridjayaCollapsibleHeader(title = "Riwayat Mutasi", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            ArahFilterChips(selected = state.arahFilter, onSelect = viewModel::onArahFilterChange)
            CabangFilterChips(selected = state.cabangFilter, onSelect = viewModel::onCabangFilterChange)

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
                    filtered.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Tidak ada riwayat",
                                subtitle = "Tidak ada mutasi yang cocok dengan filter."
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
                                items(filtered, key = { "${it.arah}_${it.noTransaksi}" }) { row ->
                                    MutasiRow(row, onClick = { viewModel.selectRow(row) })
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
private fun ArahFilterChips(selected: ArahFilter, onSelect: (ArahFilter) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    ) {
        item {
            FilterChip(selected == ArahFilter.SEMUA, { onSelect(ArahFilter.SEMUA) }, { Text("Semua") }, shape = RoundedCornerShape(50))
        }
        item {
            FilterChip(selected == ArahFilter.MASUK, { onSelect(ArahFilter.MASUK) }, { Text("Masuk") }, shape = RoundedCornerShape(50))
        }
        item {
            FilterChip(selected == ArahFilter.KELUAR, { onSelect(ArahFilter.KELUAR) }, { Text("Keluar") }, shape = RoundedCornerShape(50))
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
            FilterChip(selected == null, { onSelect(null) }, { Text("Semua Cabang") }, shape = RoundedCornerShape(50))
        }
        items(DealerAlias.allCodes) { code ->
            FilterChip(selected == code, { onSelect(code) }, { Text(DealerAlias.label(code)) }, shape = RoundedCornerShape(50))
        }
    }
}

@Composable
private fun MutasiRow(row: MutasiHistoriRowDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.noTransaksi, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatMutasiTanggal(row.tanggal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArahBadge(arah = row.arah)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (row.arah.equals("OUT", ignoreCase = true)) {
                    "${row.cabangNama} → ${row.lawanNama}"
                } else {
                    "${row.lawanNama} → ${row.cabangNama}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${row.jumlahItem ?: "-"} item · ${row.totalQty ?: "-"} qty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (row.usernya.isNotBlank()) {
                    Text(
                        text = row.usernya,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ArahBadge(arah: String) {
    val isMasuk = arah.equals("IN", ignoreCase = true)
    val color = if (isMasuk) MasukColor else KeluarColor
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = if (isMasuk) "Masuk" else "Keluar",
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MutasiHistoriDetailScreen(
    row: MutasiHistoriRowDto,
    loading: Boolean,
    items: List<MutasiHistoriDetailRowDto>,
    error: String?,
    onBack: () -> Unit
) {
    // State-swap di dalam route ini (bukan nav destination sendiri) — pola SerialInputScreen.
    BackHandler(onBack = onBack)
    TridjayaCollapsibleHeader(title = "Detail Mutasi", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            ClayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = row.noTransaksi, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ArahBadge(arah = row.arah)
                    }
                    Text(text = formatMutasiTanggal(row.tanggal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (row.arah.equals("OUT", ignoreCase = true)) {
                            "${row.cabangNama} → ${row.lawanNama}"
                        } else {
                            "${row.lawanNama} → ${row.cabangNama}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading && items.isEmpty() -> {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            repeat(4) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                        }
                    }
                    error != null && items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Tidak ada barang tercatat untuk transaksi ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + navBottom),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items, key = { "${it.kodeBarang}_${it.sn}" }) { detail ->
                                DetailBarangRow(detail)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBarangRow(detail: MutasiHistoriDetailRowDto) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = detail.nama.ifBlank { detail.kodeBarang }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = detail.kodeBarang, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Qty: ${detail.jumlah ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                if (detail.sn.isNotBlank()) {
                    Text(text = "SN: ${detail.sn}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
