package com.krisoft.tridjayaelektronik.ui.serials

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Input Serial Number (admin-stok) — pilih produk stok cabang sendiri, lalu masukkan serial
 * number satu per baris. Mismatch jumlah baris vs sisa kebutuhan hanya warning (backend
 * tetap terima berapa pun baris) — sama pola web `AdminStokSerialInputPage.tsx`.
 */
@Composable
fun SerialInputScreen(
    onBack: () -> Unit,
    viewModel: SerialInputViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    if (state.selected != null) {
        SerialInputFormScreen(
            state = state,
            onTextChange = viewModel::onTextChange,
            onSave = viewModel::save,
            onBack = viewModel::clearSelection
        )
        return
    }

    val filtered = state.items.filter { row ->
        val query = state.search.trim()
        query.isBlank() || row.kode.contains(query, ignoreCase = true) || row.nama.contains(query, ignoreCase = true)
    }

    TridjayaCollapsibleHeader(title = "Input Serial Number", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            Text(
                text = "Pilih produk, lalu masukkan serial number satu per baris.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )
            ExpressiveTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "Cari kode atau nama produk"
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loadingContext || state.itemsLoading -> {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            repeat(6) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                        }
                    }
                    state.contextError != null && state.items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveErrorState(message = state.contextError ?: "Gagal memuat", onRetry = viewModel::load)
                        }
                    }
                    filtered.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.Numbers, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Produk tidak ditemukan",
                                subtitle = "Tidak ada produk stok cabang yang cocok dengan pencarian."
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + navBottom),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.kode }) { row ->
                                ProductRow(row, onClick = { viewModel.selectProduct(row) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(row: StokCabangRow, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = row.nama.ifBlank { row.kode }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "${row.kode} · Stok: ${row.stok ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SerialInputFormScreen(
    state: SerialInputUiState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    // State-swap di dalam route ini (bukan nav destination sendiri) — pola PayrollScreen/IndentDetailScreen.
    BackHandler(onBack = onBack)
    val product = state.selected ?: return
    val stok = product.stok ?: 0
    val remaining = (stok - state.existingCount).coerceAtLeast(0)
    val lines = state.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val countMismatch = lines.isNotEmpty() && lines.size != remaining

    TridjayaCollapsibleHeader(title = "Input Serial Number", onBack = onBack) { contentModifier ->
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
                    Text(text = product.nama.ifBlank { product.kode }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = product.kode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Stok: $stok · SN tercatat: ${if (state.existingLoading) "…" else state.existingCount} · " +
                            "Butuh lagi: $remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveTextField(
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                placeholder = "Satu serial number per baris...",
                singleLine = false
            )

            Text(
                text = "${lines.size} baris terisi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (countMismatch) {
                Text(
                    text = "⚠️ Jumlah baris (${lines.size}) tak sama dengan sisa kebutuhan ($remaining) — " +
                        "stok GS mungkin sudah berubah. Tetap bisa disimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            state.formError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            state.result?.let { result ->
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "${result.inserted} serial number berhasil disimpan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (result.skipped.isNotEmpty()) {
                        Text(
                            text = "${result.skipped.size} dilewati: " +
                                result.skipped.joinToString(", ") { "${it.serialNumber} (${it.reason.ifBlank { "dilewati" }})" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ExpressiveFilledButton(
                onClick = onSave,
                enabled = !state.saving && lines.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Simpan ${lines.size} Serial Number")
                }
            }
        }
    }
}
