package com.krisoft.tridjayaelektronik.ui.opname

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.export.OpnamePdfExporter
import com.krisoft.tridjayaelektronik.data.local.OpnameCountEntity
import com.krisoft.tridjayaelektronik.data.model.OpnameItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatRupiah(value: Double): String {
    val negative = value < 0
    val digits = kotlin.math.abs(value).toLong().toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (negative) "-Rp " else "Rp ") + digits
}

/**
 * One opname session. Counting is BLIND (system stock is never shown while counting — matches
 * the physical-count discipline and the backend's own coverage endpoint) and LOCAL-FIRST:
 * inputs accumulate into Room; "Selesaikan" pushes the whole buffer as one batch, and only a
 * completed session reveals selisih units + value from the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpnameDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: OpnameDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var countingItem by remember { mutableStateOf<OpnameStockItemDto?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) } // "complete" | "cancel"
    var isExportingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    val detail = state.detail
    val localByCode = remember(state.localCounts) {
        state.localCounts.associateBy { it.kodeBarang.uppercase() }
    }
    val searchResults = remember(state.stock, search, localByCode) {
        val term = search.trim()
        if (term.isBlank()) emptyList()
        else state.stock.asSequence()
            .filter {
                it.kodeBarang.contains(term, ignoreCase = true) ||
                    (it.namaBarang ?: "").contains(term, ignoreCase = true)
            }
            // Already-counted items sink to the bottom so the next uncounted item is always
            // the first thing under the search box.
            .sortedBy { localByCode.containsKey(it.kodeBarang.uppercase()) }
            .take(20)
            .toList()
    }

    TridjayaCollapsibleHeader(
        title = "Sesi Opname",
        onBack = onBack,
        actions = {
            ExpressiveFilledIconButton(
                onClick = {
                    val current = state.detail ?: return@ExpressiveFilledIconButton
                    if (isExportingPdf) return@ExpressiveFilledIconButton
                    isExportingPdf = true
                    scope.launch {
                        runCatching {
                            val uri = withContext(Dispatchers.IO) {
                                OpnamePdfExporter.export(context, current, state.localCounts)
                            }
                            sharePdf(context, uri)
                        }
                        isExportingPdf = false
                    }
                }
            ) {
                if (isExportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Print ke PDF")
                }
            }
        }
    ) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.isLoading && detail == null -> {
                Column(modifier = contentModifier.padding(top = 8.dp)) {
                    repeat(5) {
                        SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
            detail == null -> {
                Box(modifier = contentModifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    ExpressiveErrorState(
                        message = state.errorMessage ?: "Tidak bisa memuat sesi opname.",
                        onRetry = { viewModel.load(sessionId) }
                    )
                }
            }
            else -> {
                val completed = detail.status == "completed"
                // remember — jangan jumlahkan ulang ratusan baris setiap ketikan di kolom cari
                // (recomposition scope layar ini ikut ter-trigger oleh perubahan `search`).
                val totalUnit = remember(state.localCounts) {
                    state.localCounts.sumOf { it.stokFisikLayak + it.stokFisikTidakLayak }
                }
                val selisihUnit = remember(detail.items) { detail.items.sumOf { it.selisih } }
                val selisihNilai = remember(detail.items) {
                    detail.items.sumOf { (it.harga ?: 0.0) * it.selisih }
                }

                LazyColumn(
                    modifier = contentModifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom)
                ) {
                    item(key = "header") {
                        ClayCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = detail.kodeOpname,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${detail.dealerName.ifBlank { detail.dealerCode }} · ${formatOpnameDate(detail.periodeDate)} · ${if (detail.jenis == "mingguan") "Mingguan" else "Bulanan"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OpnameStatusBadge(detail.status)
                                }
                                if (!detail.catatan.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = detail.catatan,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                if (completed) {
                                    // Selisih (unit + nilai Rp) only exists once the server has
                                    // reconciled the finished session.
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        OpnameStat("Jenis Barang", "${detail.totalItems}")
                                        OpnameStat("Unit Fisik", "${detail.totalStokFisik}")
                                        OpnameStat(
                                            "Selisih Unit",
                                            if (selisihUnit > 0) "+$selisihUnit" else "$selisihUnit",
                                            highlight = selisihUnit != 0L
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Nilai Selisih",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatRupiah(selisihNilai),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selisihNilai < 0) Color(0xFFF04438) else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    // Live counting progress: total coverage, REMAINING types
                                    // (drops with every input), and inputted units.
                                    val totalJenis = state.stock.size
                                    val sisaJenis = (totalJenis - state.localCounts.size).coerceAtLeast(0)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        OpnameStat(
                                            "Sisa Jenis",
                                            if (totalJenis > 0) "$sisaJenis" else "-",
                                            highlight = totalJenis > 0 && sisaJenis > 0
                                        )
                                        OpnameStat(
                                            "Dihitung",
                                            if (totalJenis > 0) "${state.localCounts.size}/$totalJenis" else "${state.localCounts.size}"
                                        )
                                        OpnameStat("Unit Diinput", "$totalUnit")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Dibuat ${detail.createdByName ?: "-"}" +
                                        (detail.completedByName?.let { " · diselesaikan $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.statusError != null) {
                        item(key = "status_error") {
                            ExpressiveInlineError(
                                message = state.statusError ?: "",
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    if (state.canManage) {
                        item(key = "count_input") {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Hitung Barang",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hitungan disimpan di HP dulu — dikirim ke server saat sesi diselesaikan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                ExpressiveTextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    placeholder = "Cari kode atau nama barang...",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        items(searchResults, key = { "stock_${it.kodeBarang}" }) { stockItem ->
                            StockSearchRow(
                                item = stockItem,
                                counted = localByCode[stockItem.kodeBarang.uppercase()],
                                onClick = { countingItem = stockItem }
                            )
                        }
                        if (search.isNotBlank() && searchResults.isEmpty()) {
                            item(key = "stock_empty") {
                                Text(
                                    text = "Tidak ada barang cocok di daftar opname sesi ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    item(key = "items_header") {
                        Text(
                            text = if (completed) "Hasil Opname (${detail.items.size})" else "Hasil Hitungan (${state.localCounts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                    if (!completed) {
                        // Draft: blind count — only names + the quantities the counter entered.
                        if (state.localCounts.isEmpty()) {
                            item(key = "items_empty") {
                                Text(
                                    text = "Belum ada barang yang dihitung",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(state.localCounts, key = { "local_${it.kodeBarang}" }) { count ->
                                LocalCountRow(
                                    count = count,
                                    onClick = if (state.canManage) {
                                        {
                                            countingItem = OpnameStockItemDto(
                                                kodeBarang = count.kodeBarang,
                                                namaBarang = count.namaBarang,
                                                merk = count.merk
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    } else {
                        items(detail.items, key = { it.id }) { item ->
                            CompletedItemRow(item)
                        }
                    }

                    if (state.canManage) {
                        item(key = "actions") {
                            Column(modifier = Modifier.padding(top = 20.dp)) {
                                ExpressiveFilledButton(
                                    onClick = { confirmAction = "complete" },
                                    enabled = !state.isMutatingStatus && state.localCounts.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (state.isMutatingStatus) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Mengirim hitungan...")
                                    } else {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Selesaikan Sesi")
                                    }
                                }
                                TextButton(
                                    onClick = { confirmAction = "cancel" },
                                    enabled = !state.isMutatingStatus,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Text("Batalkan Sesi", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    countingItem?.let { item ->
        CountInputSheet(
            item = item,
            existing = localByCode[item.kodeBarang.uppercase()],
            isSaving = state.isSaving,
            saveError = state.saveError,
            onDismiss = {
                countingItem = null
                viewModel.clearSaveError()
            },
            onSubmit = { layak, tidakLayak, keterangan ->
                viewModel.saveCount(item, layak, tidakLayak, keterangan) {
                    countingItem = null
                    search = ""
                }
            }
        )
    }

    confirmAction?.let { action ->
        val isComplete = action == "complete"
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (isComplete) "Selesaikan sesi opname?" else "Batalkan sesi opname?") },
            text = {
                Text(
                    if (isComplete) {
                        "Seluruh hitungan di HP (${state.localCounts.size} jenis barang) akan dikirim ke server sekaligus. Setelah selesai, hitungan tidak bisa diubah lagi dan selisih vs stok sistem dihitung."
                    } else {
                        "Sesi yang dibatalkan tidak bisa dilanjutkan lagi. Hitungan lokal di HP juga akan dihapus."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    if (isComplete) viewModel.complete() else viewModel.cancel()
                }) {
                    Text(
                        if (isComplete) "Kirim & Selesaikan" else "Ya, batalkan",
                        color = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Kembali") }
            }
        )
    }
}

private fun sharePdf(context: Context, uri: android.net.Uri) {
    // ClipData is what actually propagates the read grant through the chooser on several OEMs
    // (Vivo included) — with only EXTRA_STREAM some receivers can't read the Uri and report the
    // file as corrupt even though the PDF itself is valid.
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri("laporan_opname", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Buka / print laporan opname").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

@Composable
private fun OpnameStat(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFFB5670C) else MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StockSearchRow(
    item: OpnameStockItemDto,
    counted: OpnameCountEntity?,
    onClick: () -> Unit
) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.namaBarang ?: item.kodeBarang,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(item.kodeBarang, item.merk?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (counted != null) {
                Surface(color = Color(0xFF12B76A).copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "✓ ${counted.stokFisikLayak + counted.stokFisikTidakLayak}",
                        color = Color(0xFF12B76A),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Draft row: name + entered quantities ONLY — no system stock, no selisih (blind count). */
@Composable
private fun LocalCountRow(count: OpnameCountEntity, onClick: (() -> Unit)?) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = count.namaBarang ?: count.kodeBarang,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${count.kodeBarang} · layak ${count.stokFisikLayak} · tidak layak ${count.stokFisikTidakLayak}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!count.keterangan.isNullOrBlank()) {
                    Text(
                        text = count.keterangan,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = "${count.stokFisikLayak + count.stokFisikTidakLayak}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Completed row: full reconciliation — fisik vs sistem, selisih units, and selisih value. */
@Composable
private fun CompletedItemRow(item: OpnameItemDto) {
    val selisihColor = when {
        item.selisih == 0L -> Color(0xFF12B76A)
        item.selisih > 0 -> Color(0xFF0086C9)
        else -> Color(0xFFF04438)
    }
    val nilaiSelisih = (item.harga ?: 0.0) * item.selisih
    ClayCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.namaBarang ?: item.kodeBarang,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.kodeBarang} · fisik ${item.stokFisikLayak + item.stokFisikTidakLayak} · sistem ${item.stokSistem}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Surface(color = selisihColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = if (item.selisih == 0L) "Sesuai" else if (item.selisih > 0) "+${item.selisih}" else "${item.selisih}",
                            color = selisihColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (item.selisih != 0L && item.harga != null) {
                        Text(
                            text = formatRupiah(nilaiSelisih),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = selisihColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            if (!item.keterangan.isNullOrBlank()) {
                Text(
                    text = item.keterangan,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Count input — quantities entered here are ADDED to any earlier count of the same SKU. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountInputSheet(
    item: OpnameStockItemDto,
    existing: OpnameCountEntity?,
    isSaving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSubmit: (layak: Long, tidakLayak: Long, keterangan: String) -> Unit
) {
    var layakText by remember(item.kodeBarang) { mutableStateOf("") }
    var tidakLayakText by remember(item.kodeBarang) { mutableStateOf("") }
    var keterangan by remember(item.kodeBarang) { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .imePadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        Icon(
                            Icons.Rounded.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.namaBarang ?: item.kodeBarang,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.kodeBarang,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (existing != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sudah terhitung ${existing.stokFisikLayak + existing.stokFisikTidakLayak} unit — input di bawah akan DITAMBAHKAN ke jumlah itu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveTextField(
                    value = layakText,
                    onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) layakText = it },
                    label = "Fisik Layak",
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveTextField(
                    value = tidakLayakText,
                    onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) tidakLayakText = it },
                    label = "Tidak Layak",
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveTextField(
                value = keterangan,
                onValueChange = { keterangan = it },
                label = "Keterangan (opsional)",
                modifier = Modifier.fillMaxWidth()
            )

            (inputError ?: saveError)?.let { ExpressiveFormError(message = it) }

            Spacer(modifier = Modifier.height(20.dp))
            ExpressiveFilledButton(
                onClick = {
                    // Blank field = 0 — the counter only fills whichever bucket has units.
                    val layak = layakText.toLongOrNull() ?: 0L
                    val tidakLayak = tidakLayakText.toLongOrNull() ?: 0L
                    if (layak == 0L && tidakLayak == 0L) {
                        inputError = "Isi minimal salah satu jumlah (layak atau tidak layak)"
                    } else {
                        inputError = null
                        onSubmit(layak, tidakLayak, keterangan)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menyimpan...")
                } else {
                    Text(if (existing != null) "Tambahkan Hitungan" else "Simpan Hitungan")
                }
            }
        }
    }
}
