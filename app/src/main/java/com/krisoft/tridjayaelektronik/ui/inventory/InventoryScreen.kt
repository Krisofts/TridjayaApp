package com.krisoft.tridjayaelektronik.ui.inventory

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.krisoft.tridjayaelektronik.data.export.InventoryCsvExporter
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.ProductSortOrder
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onProductClick: (kode: String, kodeCabang: String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()
    var showSortSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                if (onBack != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 16.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            text = "Semua Barang",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                InventorySearchBar(
                    query = state.filters.search,
                    onQueryChange = viewModel::onSearchChange,
                    onRefresh = viewModel::refresh,
                    isRefreshing = state.isSyncing,
                    isExporting = isExporting,
                    onExport = {
                        isExporting = true
                        scope.launch {
                            val products = viewModel.exportProducts()
                            exportAndShare(context, products)
                            isExporting = false
                        }
                    }
                )
                FilterChipsRow(
                    filters = state.filters,
                    hasMyRegion = state.myRegion != null,
                    categories = state.categories,
                    merks = state.merks,
                    onToggleReady = viewModel::toggleReadyOnly,
                    onToggleRegion = viewModel::setRegion,
                    onMyBranch = viewModel::setMyBranchOnly,
                    onSelectCategory = viewModel::setCategory,
                    onSelectMerk = viewModel::setMerk,
                    onSortClick = { showSortSheet = true }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.syncError != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.syncError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::refresh) { Text("Coba lagi") }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom clearance so the last row clears the floating nav it scrolls behind.
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
            ) {
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { "${it.kode}|${it.kodeCabang}" }
                ) { index ->
                    val product = pagingItems[index]
                    if (product != null) {
                        val key = "${product.kode}|${product.kodeCabang}"
                        // derivedStateOf so toggling one card's expand state only recomposes that
                        // card — without it, every visible ProductCard re-reads the whole `state`
                        // object on every expand/collapse, causing scroll jank as the list grows.
                        val isExpanded by remember(key) { derivedStateOf { key in state.expanded } }
                        val isLoadingBranches by remember(key) { derivedStateOf { state.loadingBranchFor == key } }
                        val branches by remember(key) { derivedStateOf { state.branchDetails[key] } }
                        ProductCard(
                            product = product,
                            isExpanded = isExpanded,
                            isLoadingBranches = isLoadingBranches,
                            branches = branches,
                            onClick = { onProductClick(product.kode, product.kodeCabang) },
                            onToggleExpand = { viewModel.toggleExpand(product.kode, product.kodeCabang) }
                        )
                    }
                }

                when (val append = pagingItems.loadState.append) {
                    is LoadState.Loading -> {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is LoadState.Error -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gagal memuat data.",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { pagingItems.retry() }) { Text("Coba lagi") }
                            }
                        }
                    }
                    else -> Unit
                }

                // Initial load → shimmering skeleton rows instead of a spinner.
                if (pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0) {
                    items(7) {
                        SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }

                // Refresh failed with nothing cached to show (e.g. first load while offline) →
                // full error state with retry, instead of a blank list.
                if (pagingItems.loadState.refresh is LoadState.Error && pagingItems.itemCount == 0) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveErrorState(
                                message = "Tidak bisa memuat barang. Periksa koneksi lalu coba lagi.",
                                onRetry = { pagingItems.retry() }
                            )
                        }
                    }
                }

                if (pagingItems.loadState.refresh is LoadState.NotLoading && pagingItems.itemCount == 0) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Tidak ada barang",
                                subtitle = "Tidak ada barang yang cocok dengan filter"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            current = state.filters.sortOrder,
            onDismiss = { showSortSheet = false },
            onSelect = {
                viewModel.setSortOrder(it)
                showSortSheet = false
            }
        )
    }
}

private suspend fun exportAndShare(context: Context, products: List<ProductAggregate>) {
    val uri = InventoryCsvExporter.export(context, products)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export ke Excel"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    isExporting: Boolean,
    onExport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SearchBar(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text("Cari nama atau kode barang") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
                                }
                            }
                            if (isRefreshing || isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(horizontal = 12.dp))
                            } else {
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                                    }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Sinkronkan Ulang") },
                                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                            onClick = {
                                                showMenu = false
                                                onRefresh()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Export ke Excel") },
                                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                            onClick = {
                                                showMenu = false
                                                onExport()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            },
            expanded = false,
            onExpandedChange = {}
        ) {}
    }
}

@Composable
private fun FilterChipsRow(
    filters: InventoryFilters,
    hasMyRegion: Boolean,
    categories: List<String>,
    merks: List<String>,
    onToggleReady: () -> Unit,
    onToggleRegion: (String) -> Unit,
    onMyBranch: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectMerk: (String) -> Unit,
    onSortClick: () -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        item {
            FilterChip(
                selected = filters.readyOnly,
                onClick = onToggleReady,
                label = { Text("Ready") },
                leadingIcon = {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
        }
        if (hasMyRegion) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onMyBranch,
                    label = { Text("Cabang Saya") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(50)
                )
            }
        }
        items(RegionAlias.allCodes) { code ->
            FilterChip(
                selected = filters.region == code,
                onClick = { onToggleRegion(code) },
                label = { Text(RegionAlias.label(code)) },
                leadingIcon = {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
        }
        item {
            FilterDropdownChip(
                label = "Kategori",
                selectedValue = filters.category,
                options = categories,
                onSelect = onSelectCategory,
                icon = Icons.AutoMirrored.Rounded.List,
                colors = chipColors
            )
        }
        item {
            FilterDropdownChip(
                label = "Merk",
                selectedValue = filters.merk,
                options = merks,
                onSelect = onSelectMerk,
                icon = Icons.Rounded.Star,
                colors = chipColors
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onSortClick,
                label = { Text("Urutkan") },
                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdownChip(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    icon: ImageVector,
    colors: SelectableChipColors
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
            DropdownMenuItem(
                text = { Text("Semua $label") },
                onClick = { onSelect(""); expanded = false }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        ProductSortOrder.NAME_ASC to "Nama (A-Z)",
        ProductSortOrder.PRICE_ASC to "Harga Termurah",
        ProductSortOrder.PRICE_DESC to "Harga Termahal"
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Urutkan Berdasarkan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            options.forEach { (value, label) ->
                val selected = current == value
                ListItem(
                    headlineContent = {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        RadioButton(selected = selected, onClick = { onSelect(value) })
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(value) }
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ProductCard(
    product: ProductAggregate,
    isExpanded: Boolean,
    isLoadingBranches: Boolean,
    branches: List<BranchStockEntity>?,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit
) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Rhythm-style leading artwork placeholder (products have no photo field yet).
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = product.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Kode: ${product.kode}  |  ${product.kategori}  |  ${product.merk}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = RegionAlias.label(product.kodeCabang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = remember(product.harga) { formatRupiah(product.harga) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${product.totalStok.toInt()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Sembunyikan stok cabang" else "Lihat stok cabang"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    when {
                        isLoadingBranches -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        branches.isNullOrEmpty() -> {
                            Text(text = "Tidak ada rincian stok cabang", style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {
                            branches.forEach { branch ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = DealerAlias.label(branch.kodeDealer), style = MaterialTheme.typography.bodySmall)
                                    Text(text = "${branch.stok.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}
