package com.krisoft.tridjayaelektronik.ui.leads

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonBox
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonLine
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.rememberHapticClick
import kotlinx.coroutines.launch

/** Swipeable status tabs — one pager page per filter. */
private enum class LeadFilter(val label: String, val icon: ImageVector) {
    ALL("Semua", Icons.Rounded.Groups),
    OPEN("Open", Icons.Rounded.Schedule),
    WON("Menang", Icons.Rounded.EmojiEvents),
    LOST("Hilang", Icons.Rounded.Cancel);

    fun matches(status: String): Boolean = when (this) {
        ALL -> true
        OPEN -> status.equals("open", ignoreCase = true)
        WON -> status.equals("won", ignoreCase = true)
        LOST -> status.equals("lost", ignoreCase = true)
    }
}

private enum class LeadSortOption(val label: String) {
    NAME("Nama (A–Z)"),
    NEWEST("Terbaru"),
    VALUE("Nilai Tertinggi")
}

/** Status → semantic accent colour, used consistently across avatar, badge and stats. */
private fun statusColor(status: String): Color = when (status.lowercase()) {
    "won" -> Color(0xFF2E7D32)
    "lost" -> Color(0xFFC62828)
    else -> Color(0xFF1565C0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadsListScreen(
    onAddClick: () -> Unit,
    onLeadClick: (Long) -> Unit,
    viewModel: LeadsListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var sort by remember { mutableStateOf(LeadSortOption.NAME) }
    var showSearch by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Auto-focus the field the moment the search bar opens.
    LaunchedEffect(showSearch) { if (showSearch) searchFocus.requestFocus() }

    val tabs = LeadFilter.entries
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val selectedTab = pagerState.currentPage

    val sortedItems = remember(state.items, sort) {
        when (sort) {
            LeadSortOption.NAME -> state.items.sortedBy { it.nama.lowercase() }
            LeadSortOption.NEWEST -> state.items.sortedByDescending { it.createdAt }
            LeadSortOption.VALUE -> state.items.sortedByDescending { it.estimatedValue }
        }
    }

    // Edge-to-edge: content scrolls behind the floating nav, so the last row needs enough bottom
    // clearance to sit above it (nav pill height ≈ 88dp) plus the system nav-bar inset.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomClearance = navBarInset + 104.dp

    val openWhatsApp: (LeadDto) -> Unit = { lead ->
        val text = "Halo ${lead.nama}, "
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://wa.me/${lead.phone}?text=${android.net.Uri.encode(text)}".toUri())
            )
        }
    }

    TridjayaCollapsibleHeader(
        title = "Prospek",
        actions = {
            IconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.onSearchChange("")
                }
            ) {
                Icon(
                    if (showSearch) Icons.Rounded.Clear else Icons.Rounded.Search,
                    contentDescription = if (showSearch) "Tutup pencarian" else "Cari prospek",
                    tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            LeadSortMenu(current = sort, onSelect = { sort = it })
            if (state.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Sinkronkan ulang")
                }
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
                    LeadSearchBar(
                        query = state.search,
                        onQueryChange = viewModel::onSearchChange,
                        onClose = {
                            showSearch = false
                            viewModel.onSearchChange("")
                        },
                        focusRequester = searchFocus
                    )
                }
                LibraryTabRow(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onTabClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.isLoading -> {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                repeat(6) { LeadCardSkeleton() }
                            }
                        }
                        state.errorMessage != null && state.items.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                ExpressiveErrorState(
                                    message = state.errorMessage ?: "Tidak bisa memuat prospek.",
                                    onRetry = viewModel::refresh
                                )
                            }
                        }
                        else -> {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(0.dp),
                                pageSpacing = 0.dp
                            ) { page ->
                                val filter = tabs[page]
                                val pageLeads = remember(sortedItems, filter) {
                                    sortedItems.filter { filter.matches(it.status) }
                                }
                                LeadPage(
                                    filter = filter,
                                    leads = pageLeads,
                                    stageNames = state.stageNames,
                                    bottomClearance = listBottomClearance,
                                    onLeadClick = onLeadClick,
                                    onWhatsApp = openWhatsApp
                                )
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Tambah") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 100.dp)
            )
        }
    }
}

/** One swipe page: a scrollable list of professional lead cards for the tab's status. */
@Composable
private fun LeadPage(
    filter: LeadFilter,
    leads: List<LeadDto>,
    stageNames: Map<Long, String>,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onLeadClick: (Long) -> Unit,
    onWhatsApp: (LeadDto) -> Unit
) {
    if (leads.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ExpressiveEmptyState(
                icon = { Icon(filter.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = if (filter == LeadFilter.ALL) "Belum Ada Prospek" else "Tidak ada prospek \"${filter.label}\"",
                subtitle = if (filter == LeadFilter.ALL) "Tekan tombol + untuk menambahkan prospek baru" else "Ganti filter atau tambah prospek baru"
            )
        }
    } else {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomClearance)
        ) {
            items(leads, key = { it.id }) { lead ->
                LeadCard(
                    lead = lead,
                    stageName = stageNames[lead.stageId],
                    onClick = { onLeadClick(lead.id) },
                    onWhatsApp = { onWhatsApp(lead) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

/** Professional CRM lead card: status avatar, name + badge, phone, source, estimated value, quick WA. */
@Composable
private fun LeadCard(
    lead: LeadDto,
    stageName: String?,
    onClick: () -> Unit,
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = statusColor(lead.status)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = lead.nama.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lead.nama,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (lead.pendingSync) {
                        PendingBadge()
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    StatusBadge(status = lead.status)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lead.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!stageName.isNullOrBlank() || lead.estimatedValue > 0 || !lead.source.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!stageName.isNullOrBlank()) {
                            StagePill(stageName)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (!lead.source.isNullOrBlank()) {
                            SourcePill(lead.source)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (lead.estimatedValue > 0) {
                            Text(
                                text = formatRupiahShort(lead.estimatedValue),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                onClick = onWhatsApp,
                shape = CircleShape,
                color = Color(0xFF25D366).copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = "Chat WhatsApp ${lead.nama}",
                        tint = Color(0xFF1B9E4B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** Marks a lead that was created offline and is still queued to sync to the server. */
@Composable
private fun PendingBadge() {
    val amber = Color(0xFFB5670C)
    Surface(color = amber.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = amber, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Antre",
                color = amber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Current pipeline stage of the lead — a primary-tinted pill with a flag icon. */
@Composable
private fun StagePill(stage: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourcePill(source: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = source,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LeadCardSkeleton() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(modifier = Modifier.size(48.dp), shape = CircleShape)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonLine(widthFraction = 0.55f, height = 15.dp)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonLine(widthFraction = 0.35f, height = 12.dp)
            }
        }
    }
}

/** Sort menu in the header actions. */
@Composable
private fun LeadSortMenu(current: LeadSortOption, onSelect: (LeadSortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.SwapVert, contentDescription = "Urutkan")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp)
        ) {
            LeadSortOption.entries.forEach { option ->
                val selected = option == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Custom, professional search bar revealed by the header search icon — pill field, live filter. */
@Composable
private fun LeadSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Cari nama atau nomor WA…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus teks", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Tutup pencarian", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Rhythm-style swipeable tab row: pills inside one rounded Surface, selected fills with primary. */
@Composable
private fun LibraryTabRow(
    tabs: List<LeadFilter>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(tabs) { index, tab ->
                LibraryTab(
                    selected = index == selectedIndex,
                    label = tab.label,
                    icon = tab.icon,
                    onClick = { onTabClick(index) }
                )
            }
        }
    }
}

@Composable
private fun LibraryTab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        label = "tab_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "tab_content"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_scale"
    )
    Surface(
        modifier = modifier
            .padding(all = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        onClick = rememberHapticClick(onClick),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status.lowercase()) {
        "won" -> "Menang" to Color(0xFF2E7D32)
        "lost" -> "Hilang" to Color(0xFFC62828)
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

private fun formatRupiahShort(value: Double): String {
    return when {
        value >= 1_000_000_000 -> "Rp %.1fM".format(value / 1_000_000_000)
        value >= 1_000_000 -> "Rp %.1fJt".format(value / 1_000_000)
        value >= 1_000 -> "Rp %.0fRb".format(value / 1_000)
        else -> "Rp ${value.toInt()}"
    }
}
