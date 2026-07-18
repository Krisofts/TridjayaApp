package com.krisoft.tridjayaelektronik.ui.leads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.rememberHapticClick

private enum class LeadFilter(val label: String, val icon: ImageVector) {
    ALL("Semua", Icons.Rounded.Groups),
    OPEN("Open", Icons.Rounded.Schedule),
    WON("Deal", Icons.Rounded.EmojiEvents),
    LOST("Gagal", Icons.Rounded.Cancel);

    fun matches(status: String): Boolean = when (this) {
        ALL -> true
        OPEN -> status.equals("open", ignoreCase = true)
        WON -> status.equals("won", ignoreCase = true)
        LOST -> status.equals("lost", ignoreCase = true)
    }
}

private enum class LeadSortOption(val label: String) {
    NEWEST("Terbaru Dibuat"),
    NAME("Nama (A–Z)"),
    VALUE("Nilai Tertinggi"),
    PROBABILITY("Prioritas (Hot Duluan)")
}

/** Cakupan kepemilikan prospek — membedakan yang saya tangani vs yang dilempar antar sales. */
private enum class LeadScope(val label: String) {
    ALL("Semua"),
    MINE("Ditangani Saya"),
    THROWN_TO_ME("Dilempar ke Saya"),
    THROWN_BY_ME("Saya Lempar ke Sales Lain");

    fun matches(lead: LeadDto, myId: String?): Boolean = when (this) {
        ALL -> true
        MINE -> myId != null && (lead.assignedTo == myId || (lead.assignedTo.isNullOrBlank() && lead.pendingSync))
        THROWN_TO_ME -> isThrownToMe(lead, myId)
        THROWN_BY_ME -> isThrownToOther(lead, myId)
    }
}

private fun filterColor(filter: LeadFilter, fallback: Color): Color = when (filter) {
    LeadFilter.OPEN -> Color(0xFF1565C0)
    LeadFilter.WON -> Color(0xFF2E7D32)
    LeadFilter.LOST -> Color(0xFFC62828)
    LeadFilter.ALL -> fallback
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
    var sort by remember { mutableStateOf(LeadSortOption.NEWEST) }
    var scope by remember { mutableStateOf(LeadScope.ALL) }
    var filter by remember { mutableStateOf(LeadFilter.ALL) }
    var showSearch by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    // Auto-focus the field the moment the search bar opens.
    LaunchedEffect(showSearch) { if (showSearch) searchFocus.requestFocus() }

    val visibleLeads = remember(state.items, sort, filter, scope, state.stageProgress, state.myId) {
        val filtered = state.items.filter { filter.matches(it.status) && scope.matches(it, state.myId) }
        when (sort) {
            LeadSortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
            LeadSortOption.NAME -> filtered.sortedBy { it.nama.lowercase() }
            LeadSortOption.VALUE -> filtered.sortedByDescending { it.estimatedValue }
            LeadSortOption.PROBABILITY -> filtered.sortedByDescending {
                leadProbability(it.status, state.stageProgress[it.stageId])
            }
        }
    }
    val counts = remember(state.items, scope, state.myId) {
        val scoped = state.items.filter { scope.matches(it, state.myId) }
        LeadFilter.entries.associateWith { f -> scoped.count { f.matches(it.status) } }
    }

    // Edge-to-edge: content scrolls behind the floating nav, so the last row needs enough bottom
    // clearance to sit above it (nav pill height ≈ 88dp) plus the system nav-bar inset.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomClearance = navBarInset + 104.dp

    val openWhatsAppPromo: (LeadDto) -> Unit = { lead ->
        openWhatsApp(context, lead.phone, buildPromoMessage(lead, state.myName))
    }

    if (showSortSheet) {
        LeadSortFilterSheet(
            sort = sort,
            scope = scope,
            onSelectSort = { sort = it },
            onSelectScope = { scope = it },
            onDismiss = { showSortSheet = false }
        )
    }

    TridjayaCollapsibleHeader(
        title = "Prospek",
        actions = {
            ExpressiveFilledIconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.onSearchChange("")
                }
            ) {
                Icon(
                    if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (showSearch) "Tutup pencarian" else "Cari prospek"
                )
            }
            IconButton(onClick = { showSortSheet = true }) {
                Icon(Icons.Rounded.Tune, contentDescription = "Urutkan & filter")
            }
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
                    ExpressiveTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(searchFocus),
                        placeholder = "Cari nama atau nomor WA…",
                        trailingIcon = if (state.search.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.onSearchChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus teks")
                                }
                            }
                        } else null
                    )
                }

                // Summary that doubles as the status filter — tap a segment to filter the list.
                StatusSummaryFilter(
                    counts = counts,
                    selected = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Chip pengingat saat cakupan bukan "Semua" — sekali tap kembali normal.
                if (scope != LeadScope.ALL) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { scope = LeadScope.ALL },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = scope.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Hapus filter cakupan",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.isLoading -> {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                repeat(6) {
                                    SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                }
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
                        visibleLeads.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                ExpressiveEmptyState(
                                    icon = { Icon(filter.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    title = if (filter == LeadFilter.ALL && scope == LeadScope.ALL) "Belum Ada Prospek"
                                    else "Tidak ada prospek untuk filter ini",
                                    subtitle = if (filter == LeadFilter.ALL && scope == LeadScope.ALL) "Tekan tombol + untuk menambahkan prospek baru"
                                    else "Ganti filter atau tambah prospek baru"
                                )
                            }
                        }
                        else -> {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = listBottomClearance)
                            ) {
                                items(visibleLeads, key = { it.id }) { lead ->
                                    LeadCard(
                                        lead = lead,
                                        stageName = state.stageNames[lead.stageId],
                                        probability = leadProbability(lead.status, state.stageProgress[lead.stageId]),
                                        creatorName = resolveCreatorName(lead, state.myId, state.employeeNames),
                                        handlerName = resolveHandlerName(lead, state.myId, state.employeeNames),
                                        thrownToOther = isThrownToOther(lead, state.myId),
                                        thrownToMe = isThrownToMe(lead, state.myId),
                                        onClick = { onLeadClick(lead.id) },
                                        onWhatsApp = { openWhatsAppPromo(lead) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
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

/** Four tappable segments (count + label); the selected one fills with its status colour. */
@Composable
private fun StatusSummaryFilter(
    counts: Map<LeadFilter, Int>,
    selected: LeadFilter,
    onSelect: (LeadFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LeadFilter.entries.forEach { f ->
                val accent = filterColor(f, MaterialTheme.colorScheme.primary)
                val isSelected = f == selected
                val container by animateColorAsState(
                    targetValue = if (isSelected) accent else Color.Transparent,
                    label = "segment_container"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(container)
                        .clickable(onClick = rememberHapticClick { onSelect(f) })
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${counts[f] ?: 0}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) Color.White else accent
                    )
                    Text(
                        text = f.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White.copy(alpha = 0.9f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Kartu prospek bergaya bersih: avatar kotak lembut, nama + minat + tahap, badge suhu
 * Cold/Warm/Hot (pemetaan probabilitas) atau badge status utk lead selesai, nilai estimasi,
 * lalu baris bawah penginput → penanggung jawab + tanggal + tombol WA promo.
 */
@Composable
private fun LeadCard(
    lead: LeadDto,
    stageName: String?,
    probability: Int,
    creatorName: String,
    handlerName: String,
    thrownToOther: Boolean,
    thrownToMe: Boolean,
    onClick: () -> Unit,
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = leadAccentColor(lead.status)
    val isOpen = lead.status.equals("open", ignoreCase = true)
    val temp = leadTemperature(probability)
    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lead.nama.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = lead.nama,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (lead.pendingSync) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.CloudUpload,
                                contentDescription = "Antre sinkron",
                                tint = Color(0xFFB5670C),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val minat = lead.minatBarang?.trim().orEmpty()
                    Text(
                        text = minat.ifBlank { lead.phone },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (minat.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isOpen && !stageName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tahap: $stageName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isOpen) {
                        TemperatureBadge(temp)
                    } else {
                        Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.13f)) {
                            Text(
                                text = leadStatusLabel(lead.status),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (lead.estimatedValue > 0) {
                        Text(
                            text = formatRupiahShort(lead.estimatedValue),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (creatorName == handlerName) "Oleh $creatorName" else "$creatorName → $handlerName",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                when {
                    thrownToOther -> ThrowBadge("Dilempar", Color(0xFFB5670C))
                    thrownToMe -> ThrowBadge("Limpahan", Color(0xFF0E7490))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatShortDate(lead.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    onClick = onWhatsApp,
                    shape = CircleShape,
                    color = Color(0xFF25D366).copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = "Chat WhatsApp ${lead.nama}",
                            tint = Color(0xFF1B9E4B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Badge suhu Cold/Warm/Hot — ikon + label dengan tint warna suhunya. */
@Composable
private fun TemperatureBadge(temp: LeadTemperature) {
    Surface(shape = RoundedCornerShape(50), color = temp.color.copy(alpha = 0.13f)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = temp.icon,
                contentDescription = null,
                tint = temp.color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = temp.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = temp.color
            )
        }
    }
}

/** Badge kecil penanda prospek limpahan antar sales. */
@Composable
private fun ThrowBadge(label: String, color: Color) {
    Spacer(modifier = Modifier.width(6.dp))
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.13f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Bottom sheet urutkan + cakupan (pengganti dropdown sort lama). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LeadSortFilterSheet(
    sort: LeadSortOption,
    scope: LeadScope,
    onSelectSort: (LeadSortOption) -> Unit,
    onSelectScope: (LeadScope) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Urutkan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            LeadSortOption.entries.forEach { option ->
                val selected = option == sort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = rememberHapticClick { onSelectSort(option) })
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tampilkan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeadScope.entries.forEach { option ->
                    FilterChip(
                        selected = scope == option,
                        onClick = { onSelectScope(option) },
                        label = { Text(option.label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Prospek yang Anda lempar ke sales lain baru terlihat setelah tersinkron dari server.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

/** "yyyy-MM-dd…" → "d/M" (compact list date); falls back to empty string. */
private fun formatShortDate(raw: String): String {
    val datePart = raw.substringBefore('T').substringBefore(' ').trim()
    val parts = datePart.split("-")
    if (parts.size != 3) return ""
    val month = parts[1].toIntOrNull() ?: return ""
    val day = parts[2].toIntOrNull() ?: return ""
    return "$day/$month"
}
