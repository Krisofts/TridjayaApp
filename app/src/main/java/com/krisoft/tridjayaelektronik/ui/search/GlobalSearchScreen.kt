package com.krisoft.tridjayaelektronik.ui.search

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.ProductSortOrder
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard

/**
 * Global search (the "Cari" tab root) — Rhythm's UniversalSearchScreen style. A bottom-docked
 * search bar with a back button + a filter button; the filter panel exposes Inventory-style
 * product filters. When idle it shows recent-search history with per-item + clear-all deletion.
 */
@Composable
fun GlobalSearchScreen(
    onProductClick: (kode: String, kodeCabang: String) -> Unit,
    onLeadClick: (Long) -> Unit,
    onBrowseInventory: () -> Unit,
    onClose: () -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        // Result-type chips (Semua / Produk / Prospek) pinned at the top, always visible.
        SearchTypeChips(selected = state.filter, onSelect = viewModel::setFilter)

        // Results / history / prompt fill the space above the bar.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isSearching -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(6) { SkeletonCard() }
                    }
                }
                state.query.trim().length < 2 -> {
                    IdleView(
                        history = state.history,
                        onApply = { viewModel.applyHistory(it) },
                        onRemove = { viewModel.removeHistory(it) },
                        onClearAll = { viewModel.clearHistory() },
                        onBrowseInventory = onBrowseInventory
                    )
                }
                state.hasSearched && state.isEmpty -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        ExpressiveEmptyState(
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = "Tidak ditemukan",
                            subtitle = "Tidak ada yang cocok dengan \"${state.query.trim()}\""
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.showProducts && state.products.isNotEmpty()) {
                            item { ResultSectionHeader(title = "Produk", count = state.products.size) }
                            items(state.products, key = { "${it.kode}|${it.kodeCabang}" }) { p ->
                                Box(modifier = Modifier.animateItem()) {
                                    ProductResultRow(product = p, onClick = {
                                        viewModel.commitToHistory()
                                        onProductClick(p.kode, p.kodeCabang)
                                    })
                                }
                            }
                        }
                        if (state.showLeads && state.leads.isNotEmpty()) {
                            item { ResultSectionHeader(title = "Prospek", count = state.leads.size) }
                            items(state.leads, key = { it.id }) { l ->
                                Box(modifier = Modifier.animateItem()) {
                                    LeadResultRow(lead = l, onClick = {
                                        viewModel.commitToHistory()
                                        onLeadClick(l.id)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter panel appears just above the search bar.
        AnimatedVisibility(visible = showFilters) {
            SearchFilterPanel(state = state, viewModel = viewModel)
        }

        // Rhythm search bar: back + field + filter button, docked above the keyboard.
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onClear = viewModel::clearQuery,
            onBack = onClose,
            onSubmit = { viewModel.commitToHistory() },
            filtersActive = showFilters,
            onToggleFilters = { showFilters = !showFilters },
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    filtersActive: Boolean,
    onToggleFilters: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Back
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            }
        }
        // Field
        Surface(
            modifier = Modifier.weight(1f).height(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Cari produk atau prospek…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() })
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        // Filter toggle
        Surface(shape = CircleShape, color = if (filtersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh) {
            IconButton(onClick = onToggleFilters) {
                Icon(
                    Icons.Rounded.FilterList,
                    contentDescription = "Filter",
                    tint = if (filtersActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Result-type chips (Semua / Produk / Prospek), pinned at the top of the search screen. */
@Composable
private fun SearchTypeChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(SearchFilter.entries.toList()) { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
        }
    }
}

@Composable
private fun SearchFilterPanel(state: GlobalSearchUiState, viewModel: GlobalSearchViewModel) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Product filters (only meaningful for product results) — Inventory-style.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = state.productFilters.readyOnly,
                    onClick = { viewModel.setReadyOnly(!state.productFilters.readyOnly) },
                    label = { Text("Ready") },
                    leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                    colors = chipColors
                )
            }
            items(RegionAlias.allCodes) { code ->
                FilterChip(
                    selected = state.productFilters.region == code,
                    onClick = { viewModel.setRegion(code) },
                    label = { Text(RegionAlias.label(code)) },
                    leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                    colors = chipColors
                )
            }
            item {
                DropdownFilterChip("Kategori", state.productFilters.category, state.categories, Icons.AutoMirrored.Rounded.List, chipColors) { viewModel.setCategory(it) }
            }
            item {
                DropdownFilterChip("Merk", state.productFilters.merk, state.merks, Icons.Rounded.Star, chipColors) { viewModel.setMerk(it) }
            }
            item {
                SortFilterChip(state.productFilters.sortOrder, chipColors) { viewModel.setSortOrder(it) }
            }
            if (state.productFilters.isActive) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.clearProductFilters() },
                        label = { Text("Reset") },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownFilterChip(
    label: String,
    selectedValue: String,
    options: List<String>,
    icon: ImageVector,
    colors: androidx.compose.material3.SelectableChipColors,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedValue.isNotEmpty(),
            onClick = { expanded = true },
            label = { Text(if (selectedValue.isNotEmpty()) selectedValue else label) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50),
            colors = colors
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Semua $label") }, onClick = { onSelect(""); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun SortFilterChip(current: Int, colors: androidx.compose.material3.SelectableChipColors, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        ProductSortOrder.NAME_ASC to "Nama (A-Z)",
        ProductSortOrder.PRICE_ASC to "Termurah",
        ProductSortOrder.PRICE_DESC to "Termahal"
    )
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: "Urutkan"
    Box {
        FilterChip(
            selected = current != ProductSortOrder.NAME_ASC,
            onClick = { expanded = true },
            label = { Text(currentLabel) },
            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50),
            colors = colors
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

/** Idle state: recent-search history (deletable) + a browse shortcut, or a centred prompt. */
@Composable
private fun IdleView(
    history: List<String>,
    onApply: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onBrowseInventory: () -> Unit
) {
    if (history.isEmpty()) {
        SearchPrompt(onBrowseInventory)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pencarian Terakhir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ExpressiveTextButton(onClick = onClearAll) { Text("Hapus Semua") }
            }
        }
        items(history) { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onApply(q) }
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemove(q) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                ExpressiveTextButton(onClick = onBrowseInventory) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Jelajahi semua barang")
                }
            }
        }
    }
}

@Composable
private fun SearchPrompt(onBrowseInventory: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
            Box(modifier = Modifier.padding(20.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Cari apa saja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Ketik nama produk, kode, atau nama prospek", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))
        ExpressiveTextButton(onClick = onBrowseInventory) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Jelajahi semua barang")
        }
    }
}

@Composable
private fun ResultSectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun ProductResultRow(product: ProductAggregate, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "Kode: ${product.kode}  |  ${RegionAlias.label(product.kodeCabang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatRupiah(product.harga),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LeadResultRow(lead: LeadDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = lead.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = lead.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(status = lead.status)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status.lowercase()) {
        "won" -> "Won" to Color(0xFF2E7D32)
        "lost" -> "Lost" to Color(0xFFC62828)
        else -> "Open" to Color(0xFF1565C0)
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}
