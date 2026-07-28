package com.krisoft.tridjayaelektronik.ui.deadstock

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.model.DeadstockItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

private fun formatRupiah(value: Long): String {
    val text = kotlin.math.abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

/** `brosurUrl` dari response cuma path server (`/uploads/deadstock/...`) — endpoint privat
 *  butuh auth, filename = segmen terakhir. Lihat brief: TIDAK BISA `<img>`/Coil polos. */
private fun brosurDisplayUrl(raw: String): String {
    val filename = raw.substringAfterLast('/')
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/api/inventory/deadstock/brosur/" + filename
}

/**
 * Deadstock Cabang (karyawan/kepala-cabang/admin-stok) — daftar barang umur >=180 hari cabang
 * sendiri (dealer dipaksa backend), lihat brosur (kalau ada). Tanpa audit & upload brosur
 * (manager/web-only, lihat brief Fase 1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadstockScreen(
    onBack: () -> Unit,
    viewModel: DeadstockViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    if (state.selected != null) {
        DeadstockDetailScreen(
            item = state.selected!!,
            token = viewModel.bearerToken(),
            onBack = { viewModel.selectItem(null) }
        )
        return
    }

    val filtered = remember(state.items, state.search, state.brosurFilter) {
        val query = state.search.trim()
        state.items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.kodeBarang.contains(query, ignoreCase = true) ||
                item.namaBarang.contains(query, ignoreCase = true)
            val matchesBrosur = when (state.brosurFilter) {
                BrosurFilter.SEMUA -> true
                BrosurFilter.SUDAH -> !item.brosurUrl.isNullOrBlank()
                BrosurFilter.BELUM -> item.brosurUrl.isNullOrBlank()
            }
            matchesQuery && matchesBrosur
        }
    }

    TridjayaCollapsibleHeader(title = "Deadstock Cabang", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            ExpressiveTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "Cari kode atau nama barang"
            )
            BrosurFilterChips(selected = state.brosurFilter, onSelect = viewModel::onBrosurFilterChange)

            if (state.cabang.isNotBlank()) {
                Text(
                    text = "Cabang: ${state.cabang} · barang umur ≥180 hari",
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
                    filtered.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Tidak ada deadstock",
                                subtitle = if (state.items.isEmpty()) {
                                    "Belum ada barang umur ≥180 hari di cabang ini."
                                } else {
                                    "Tidak ada barang yang cocok dengan filter/pencarian."
                                }
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
                                items(filtered, key = { it.kodeBarang }) { item ->
                                    DeadstockRow(item, onClick = { viewModel.selectItem(item) })
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
private fun BrosurFilterChips(selected: BrosurFilter, onSelect: (BrosurFilter) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selected == BrosurFilter.SEMUA,
                onClick = { onSelect(BrosurFilter.SEMUA) },
                label = { Text("Semua") },
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = selected == BrosurFilter.SUDAH,
                onClick = { onSelect(BrosurFilter.SUDAH) },
                label = { Text("Sudah Brosur") },
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = selected == BrosurFilter.BELUM,
                onClick = { onSelect(BrosurFilter.BELUM) },
                label = { Text("Belum Brosur") },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

@Composable
private fun DeadstockRow(item: DeadstockItemDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.namaBarang.ifBlank { item.kodeBarang }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${item.kodeBarang} · ${item.kategori}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UmurBadge(umurHari = item.umurHari)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatRupiah(item.hargaJual), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Stok: ${item.stok}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (item.brosurUrl.isNullOrBlank()) "Belum ada brosur" else "Brosur tersedia",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (item.brosurUrl.isNullOrBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun UmurBadge(umurHari: Int) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = "$umurHari hari",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DeadstockDetailScreen(item: DeadstockItemDto, token: String?, onBack: () -> Unit) {
    // State-swap di dalam route ini (bukan nav destination sendiri) — pola SerialInputScreen.
    BackHandler(onBack = onBack)
    TridjayaCollapsibleHeader(title = "Detail Deadstock", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navBottom)
        ) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = item.namaBarang.ifBlank { item.kodeBarang }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "${item.kodeBarang} · ${item.kategori}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    DetailRow("Harga jual", formatRupiah(item.hargaJual))
                    DetailRow("Stok", "${item.stok}")
                    DetailRow("Umur", "${item.umurHari} hari")
                    DetailRow("Cabang", item.cabang.ifBlank { item.kodeDealer })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "Brosur", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (item.brosurUrl.isNullOrBlank()) {
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Belum ada brosur untuk barang ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                AuthedImage(
                    url = brosurDisplayUrl(item.brosurUrl),
                    token = token,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
                item.brosurUploadedBy?.let {
                    Text(
                        text = "Diunggah oleh $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Coil image dengan bearer token — endpoint brosur privat menolak request anonim
 *  (pola `IndentDetailScreen.AuthedImage`). */
@Composable
private fun AuthedImage(url: String, token: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(url, token) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
            .build()
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = "Brosur",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                is AsyncImagePainter.State.Loading -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
