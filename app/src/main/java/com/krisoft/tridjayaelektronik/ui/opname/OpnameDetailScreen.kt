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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.export.OpnamePdfExporter
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.ui.deliveryflow.BarcodeScanButton
import com.krisoft.tridjayaelektronik.data.model.OpnameItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
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
 * the physical-count discipline and the backend's own coverage endpoint) and per-UNIT: satu
 * baris per serial number, bukan angka jumlah. Tiap scan disimpan ke Room lalu LANGSUNG
 * dikirim (duplikat cuma bisa divonis server); tanpa sinyal ia diantre dan bisa dikirim ulang.
 * Selisih unit + nilainya baru terungkap dari server setelah sesi ditutup.
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
    var scanSheetOpen by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) } // "complete" | "cancel"
    var isExportingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    val detail = state.detail
    // Jumlah unit per SKU: dipakai penanda "sudah dihitung" di hasil pencarian.
    val unitsByCode = remember(state.units) {
        state.units.groupBy { it.kodeBarang.uppercase() }
    }
    val pendingCount = remember(state.units) { state.units.count { it.syncedAtMillis == null } }
    val searchResults = remember(state.stock, search, unitsByCode) {
        val term = search.trim()
        if (term.isBlank()) emptyList()
        else state.stock.asSequence()
            .filter {
                it.kodeBarang.contains(term, ignoreCase = true) ||
                    (it.namaBarang ?: "").contains(term, ignoreCase = true)
            }
            // Already-counted items sink to the bottom so the next uncounted item is always
            // the first thing under the search box.
            .sortedBy { unitsByCode.containsKey(it.kodeBarang.uppercase()) }
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
                                OpnamePdfExporter.export(context, current, state.units)
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
                val totalUnit = state.units.size
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
                                    val jenisTerhitung = unitsByCode.size
                                    val sisaJenis = (totalJenis - jenisTerhitung).coerceAtLeast(0)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        OpnameStat(
                                            "Sisa Jenis",
                                            if (totalJenis > 0) "$sisaJenis" else "-",
                                            highlight = totalJenis > 0 && sisaJenis > 0
                                        )
                                        OpnameStat(
                                            "Dihitung",
                                            if (totalJenis > 0) "$jenisTerhitung/$totalJenis" else "$jenisTerhitung"
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
                                    text = "Scan serial tiap unit. Tersimpan di HP lalu langsung dikirim; tanpa sinyal akan diantre.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (pendingCount > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$pendingCount unit menunggu terkirim",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(onClick = { viewModel.retryPending() }) {
                                            Text("Kirim ulang")
                                        }
                                    }
                                }
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
                                unitCount = unitsByCode[stockItem.kodeBarang.uppercase()]?.size ?: 0,
                                onClick = {
                                    viewModel.selectItem(stockItem)
                                    scanSheetOpen = true
                                }
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
                            text = if (completed) "Hasil Opname (${detail.items.size})" else "Unit Terscan (${state.units.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                    if (!completed) {
                        // Draft: blind count — hanya serial yang benar-benar discan petugas.
                        if (state.units.isEmpty()) {
                            item(key = "items_empty") {
                                Text(
                                    text = "Belum ada unit yang discan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(state.units, key = { "unit_${it.serialNumber}" }) { unit ->
                                ScannedUnitRow(
                                    unit = unit,
                                    onDelete = if (state.canManage) {
                                        { viewModel.deleteUnit(unit) }
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
                                    enabled = !state.isMutatingStatus && state.units.isNotEmpty(),
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

    val selected = state.selectedItem
    if (scanSheetOpen && selected != null) {
        ScanUnitSheet(
            item = selected,
            units = unitsByCode[selected.kodeBarang.uppercase()].orEmpty(),
            isSaving = state.isSaving,
            saveError = state.saveError,
            scanMessage = state.scanMessage,
            onDismiss = {
                scanSheetOpen = false
                viewModel.selectItem(null)
                search = ""
            },
            onScan = { serial, tidakLayak -> viewModel.scan(serial, tidakLayak) },
            onDelete = { unit -> viewModel.deleteUnit(unit) }
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
                        "Sisa antrean (${state.units.count { it.syncedAtMillis == null }} unit) akan dikirim dulu, lalu sesi ditutup. Setelah selesai, hitungan tidak bisa diubah lagi dan selisih vs stok sistem dihitung."
                    } else {
                        "Sesi yang dibatalkan tidak bisa dilanjutkan lagi. Unit yang tersimpan di HP juga akan dihapus."
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
    unitCount: Int,
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
            if (unitCount > 0) {
                Surface(color = Color(0xFF12B76A).copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "✓ $unitCount",
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

/**
 * Baris unit terscan: serial + kondisi + penanda temuan/antrean. Sengaja tanpa stok
 * sistem — penghitung tak boleh melihat angka pembanding (blind count).
 */
@Composable
private fun ScannedUnitRow(unit: OpnameUnitEntity, onDelete: (() -> Unit)?) {
    ClayCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unit.serialNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(unit.kodeBarang)
                        append(" \u00b7 ")
                        append(if (unit.kondisi == "layak") "layak" else "tidak layak")
                        unit.temuan?.let {
                            append(" \u00b7 ")
                            append(temuanLabel(it))
                        }
                        if (unit.syncedAtMillis == null) append(" \u00b7 menunggu kirim")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unit.temuan != null || unit.syncedAtMillis == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Hapus unit ${unit.serialNumber}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
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

/**
 * Panel scan: satu barang, banyak unit. Tombol scan memakai Google code scanner yang
 * sudah dipakai PDI/SPK (tanpa izin kamera); ketik manual tetap tersedia untuk barcode
 * yang rusak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanUnitSheet(
    item: OpnameStockItemDto,
    units: List<OpnameUnitEntity>,
    isSaving: Boolean,
    saveError: String?,
    scanMessage: String?,
    onDismiss: () -> Unit,
    onScan: (serial: String, tidakLayak: Boolean) -> Unit,
    onDelete: (OpnameUnitEntity) -> Unit
) {
    var manual by remember { mutableStateOf("") }
    var tidakLayak by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Ketik manual dipakai justru saat barcode rusak — tanpa ini keyboard
                // menutupi kolom serial dan tombolnya.
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = item.namaBarang ?: item.kodeBarang,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = listOfNotNull(item.kodeBarang, item.merk?.takeIf { it.isNotBlank() })
                    .joinToString(" \u00b7 "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    placeholder = "Ketik serial number...",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BarcodeScanButton(contentDescription = "Scan serial unit") { hasil ->
                    onScan(hasil, tidakLayak)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = tidakLayak, onCheckedChange = { tidakLayak = it })
                Text(
                    text = "Unit berikutnya TIDAK layak",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            ExpressiveFilledButton(
                onClick = {
                    onScan(manual, tidakLayak)
                    manual = ""
                },
                enabled = !isSaving && manual.isNotBlank(),
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
                    Text("Tambah Unit")
                }
            }

            if (saveError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ExpressiveInlineError(message = saveError)
            } else if (scanMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = scanMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${units.size} unit terscan untuk barang ini",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            units.take(30).forEach { unit ->
                ScannedUnitRow(unit = unit, onDelete = { onDelete(unit) })
            }
            if (units.size > 30) {
                Text(
                    text = "...dan ${units.size - 30} unit lainnya",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

