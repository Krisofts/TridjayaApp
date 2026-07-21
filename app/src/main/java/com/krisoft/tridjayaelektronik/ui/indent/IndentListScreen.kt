package com.krisoft.tridjayaelektronik.ui.indent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

@Composable
fun IndentListScreen(
    onBack: () -> Unit,
    viewModel: IndentListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<IndentDto?>(null) }

    if (showCreate) {
        IndentCreateScreen(
            onBack = { showCreate = false },
            onCreated = {
                showCreate = false
                viewModel.load()
            }
        )
        return
    }

    selected?.let { indent ->
        IndentDetailScreen(indent = indent, onBack = { selected = null })
        return
    }

    var statusFilter by remember { mutableStateOf<String?>(null) }
    val filteredItems = remember(state.items, state.searchQuery, statusFilter) {
        val query = state.searchQuery.trim()
        state.items.filter { indent ->
            val matchesQuery = query.isBlank() ||
                indent.namaBarang.contains(query, ignoreCase = true) ||
                indent.pemesan.contains(query, ignoreCase = true)
            val matchesStatus = statusFilter == null || indent.status.equals(statusFilter, ignoreCase = true)
            matchesQuery && matchesStatus
        }
    }

    TridjayaCollapsibleHeader(
        title = "Indent Pemesanan",
        onBack = onBack,
        actions = {
            ExpressiveFilledIconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.onSearchChange("")
                }
            ) {
                Icon(
                    if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (showSearch) "Tutup pencarian" else "Cari indent"
                )
            }
        }
    ) { contentModifier ->
        Box(modifier = contentModifier) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ExpressiveTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = "Cari nama barang atau pemesan"
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.isLoading -> {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                repeat(6) {
                                    SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                }
                            }
                        }
                        state.errorMessage != null && state.items.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                ExpressiveErrorState(
                                    message = state.errorMessage ?: "Tidak bisa memuat daftar indent.",
                                    onRetry = viewModel::load
                                )
                            }
                        }
                        state.items.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                ExpressiveEmptyState(
                                    icon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
                                    title = "Belum ada indent",
                                    subtitle = "Tekan tombol + untuk mengajukan indent barang baru"
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                            ) {
                                // Chips are the first list item on purpose — they scroll away
                                // with the content instead of staying pinned under the header.
                                item(key = "status_filter") {
                                    StatusFilterChips(
                                        items = state.items,
                                        selected = statusFilter,
                                        onSelect = { statusFilter = if (statusFilter == it) null else it }
                                    )
                                }
                                if (filteredItems.isEmpty()) {
                                    item(key = "no_match") {
                                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                            ExpressiveEmptyState(
                                                icon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
                                                title = "Tidak ditemukan",
                                                subtitle = "Tidak ada indent yang cocok dengan filter/pencarian"
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredItems, key = { it.id }) { indent ->
                                        IndentRow(indent, onClick = { selected = indent })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Ajukan") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

/** Scrollable status filter chips (Semua + one per status, with live counts). */
@Composable
private fun StatusFilterChips(
    items: List<IndentDto>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    val counts = remember(items) { items.groupingBy { it.status.lowercase() }.eachCount() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { selected?.let(onSelect) },
                label = { Text("Semua (${items.size})") },
                shape = RoundedCornerShape(50)
            )
        }
        items(listOf("menunggu", "dipesan", "tiba", "selesai", "batal")) { status ->
            val count = counts[status] ?: 0
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(if (count > 0) "${statusLabel(status)} ($count)" else statusLabel(status)) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor(status), RoundedCornerShape(50))
                    )
                },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/**
 * Order-card row (e-commerce order list style): status pill + date header, product summary,
 * then a divider with branch info and an explicit "Lihat Detail" action.
 */
@Composable
private fun IndentRow(indent: IndentDto, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(indent.status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatIndentDate(indent.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = indent.namaBarang,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${indent.quantity} unit · ${indent.pemesan}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = indent.pemesanCabang?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Lihat Detail",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = statusColor(status)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = statusLabel(status),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

internal fun statusLabel(status: String): String = when (status.lowercase()) {
    "menunggu" -> "Menunggu"
    "dipesan" -> "Dipesan"
    "tiba" -> "Tiba"
    "selesai" -> "Selesai"
    "batal" -> "Batal"
    else -> status
}

internal fun statusColor(status: String): Color = when (status.lowercase()) {
    "menunggu" -> Color(0xFFB5670C)
    "dipesan" -> Color(0xFF0086C9)
    "tiba" -> Color(0xFF465FFF)
    "selesai" -> Color(0xFF12B76A)
    "batal" -> Color(0xFFF04438)
    else -> Color(0xFF667085)
}
