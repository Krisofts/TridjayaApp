package com.krisoft.tridjayaelektronik.ui.leads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AssigneeDto
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddLeadScreen(
    onBack: () -> Unit,
    onLeadCreated: () -> Unit,
    viewModel: AddLeadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showAssigneeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdLeadId) {
        if (state.createdLeadId != null) onLeadCreated()
    }

    if (showAssigneeSheet) {
        AssigneePickerSheet(
            assignees = state.assignees,
            isLoading = state.isLoadingAssignees,
            selected = state.selectedAssignee,
            onSelect = { assignee ->
                viewModel.onAssigneeSelected(assignee)
                showAssigneeSheet = false
            },
            onDismiss = { showAssigneeSheet = false }
        )
    }

    TridjayaCollapsibleHeader(title = "Tambah Prospek", onBack = onBack) { contentModifier ->
        Column(
            modifier = contentModifier
                // imePadding shrinks the scroll viewport by the keyboard height, so a focused field
                // (e.g. Catatan at the bottom) auto-scrolls above the keyboard instead of hiding under it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard(
                icon = Icons.Rounded.Person,
                title = "Kontak",
                subtitle = "Identitas & nomor WhatsApp prospek"
            ) {
                ExpressiveTextField(
                    value = state.nama,
                    onValueChange = viewModel::onNamaChange,
                    label = "Nama *",
                    placeholder = "Nama lengkap prospek",
                    modifier = Modifier.fillMaxWidth()
                )
                ExpressiveTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = "Nomor WhatsApp *",
                    placeholder = "08xxxxxxxxxx",
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionCard(
                icon = Icons.Rounded.ShoppingBag,
                title = "Kebutuhan",
                subtitle = "Barang yang diminati prospek"
            ) {
                Column {
                    ExpressiveTextField(
                        value = state.minatBarang,
                        onValueChange = viewModel::onMinatBarangChange,
                        label = "Minat Barang *",
                        placeholder = "Ketik nama barang…",
                        supportingText = "Pisahkan dengan koma untuk lebih dari satu barang.",
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Dropdown saran produk dari cache inventory — tap untuk mengisi otomatis.
                    AnimatedVisibility(visible = state.minatSuggestions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) {
                            Column {
                                state.minatSuggestions.forEachIndexed { index, suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.onMinatSuggestionPicked(suggestion) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (index != state.minatSuggestions.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                FieldLabel("Kategori Produk *")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KATEGORI_PRODUK_OPTIONS.forEach { kategori ->
                        FilterChip(
                            selected = state.kategoriProduk == kategori,
                            onClick = { viewModel.onKategoriProdukSelected(kategori) },
                            label = { Text(kategori) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            SectionCard(
                icon = Icons.Rounded.TrendingUp,
                title = "Peluang",
                subtitle = "Estimasi nilai & jalur penjualan"
            ) {
                ExpressiveTextField(
                    value = state.estimatedValue,
                    onValueChange = viewModel::onEstimatedValueChange,
                    label = "Estimasi Nilai (Rp)",
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    supportingText = previewRupiah(state.estimatedValue),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.isLoadingPipelines) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else if (state.pipelines.isNotEmpty()) {
                    FieldLabel("Pipeline *")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.pipelines.forEach { pipeline: PipelineDto ->
                            FilterChip(
                                selected = state.selectedPipelineId == pipeline.id,
                                onClick = { viewModel.onPipelineSelected(pipeline.id) },
                                label = { Text(pipeline.nama) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
                OptionDropdownField(
                    label = "Fincoy / Leasing",
                    selected = state.keteranganFincoy,
                    options = FINCOY_OPTIONS,
                    onSelect = viewModel::onFincoySelected
                )
            }

            SectionCard(
                icon = Icons.Rounded.Phone,
                title = "Penugasan",
                subtitle = "Siapa yang menangani prospek ini"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                        .clickable { showAssigneeSheet = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ditugaskan ke",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.selectedAssignee?.name ?: "Saya sendiri",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        state.selectedAssignee?.let { assignee ->
                            val sub = listOfNotNull(
                                assignee.divisi?.takeIf { it.isNotBlank() },
                                assignee.cabang?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (state.isLoadingAssignees) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Semua karyawan aktif dari seluruh cabang dapat dipilih.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(
                icon = Icons.AutoMirrored.Rounded.Notes,
                title = "Detail",
                subtitle = "Sumber, alamat & catatan tambahan"
            ) {
                OptionDropdownField(
                    label = "Sumber Lead",
                    selected = state.sumber,
                    options = SUMBER_LEAD_OPTIONS,
                    onSelect = viewModel::onSumberSelected
                )
                ExpressiveTextField(
                    value = state.lokasi,
                    onValueChange = viewModel::onLokasiChange,
                    label = "Alamat",
                    placeholder = "Jalan, RT/RW, kelurahan, kecamatan",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                ExpressiveTextField(
                    value = state.catatan,
                    onValueChange = viewModel::onCatatanChange,
                    label = "Keterangan",
                    placeholder = "Minta brosur, mau datang sore, dsb.",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.errorMessage != null) {
                ExpressiveFormError(message = state.errorMessage ?: "")
            }

            ExpressiveFilledButton(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan Prospek", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Label kecil di atas grup chip/dropdown. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Searchable employee picker for the assignment field — "Saya sendiri" first, then every active
 *  employee (name + divisi · cabang) filtered live by the query. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssigneePickerSheet(
    assignees: List<AssigneeDto>,
    isLoading: Boolean,
    selected: AssigneeDto?,
    onSelect: (AssigneeDto?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(assignees, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) assignees
        else assignees.filter {
            "${it.name} ${it.divisi.orEmpty()} ${it.cabang.orEmpty()}".lowercase().contains(q)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                text = "Ditugaskan ke",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Cari nama, divisi, atau cabang…",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    AssigneeRow(
                        title = "Saya sendiri",
                        subtitle = "Prospek dikerjakan oleh saya",
                        isSelected = selected == null,
                        onClick = { onSelect(null) }
                    )
                }
                items(filtered, key = { it.id }) { assignee ->
                    AssigneeRow(
                        title = assignee.name,
                        subtitle = listOfNotNull(
                            assignee.divisi?.takeIf { it.isNotBlank() },
                            assignee.cabang?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").ifBlank { "Data divisi/cabang belum tersedia" },
                        isSelected = selected?.id == assignee.id,
                        onClick = { onSelect(assignee) }
                    )
                }
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        }
                    }
                } else if (filtered.isEmpty() && query.isNotBlank()) {
                    item {
                        Text(
                            text = "Karyawan tidak ditemukan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssigneeRow(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Compact single-choice dropdown: a field-like row that opens a menu of fixed options. */
@Composable
private fun OptionDropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected.ifBlank { "Pilih $label" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("— Kosongkan —") },
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
}

/** Kartu seksi bergaya baru: header ikon berlatar tint + judul & subjudul, lalu isi form. */
@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

/** Live "Rp 5.000.000" preview under the amount field; null (no supporting text) when empty. */
private fun previewRupiah(digits: String): String? {
    if (digits.isBlank()) return null
    val n = digits.toLongOrNull() ?: return null
    if (n <= 0) return null
    val grouped = n.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $grouped"
}
