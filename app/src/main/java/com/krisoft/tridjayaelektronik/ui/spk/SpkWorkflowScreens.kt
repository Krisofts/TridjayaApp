package com.krisoft.tridjayaelektronik.ui.spk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/** Mode aksi pada detail order — menentukan judul + tombol yang muncul. */
object SpkMode {
    const val DISKON = "diskon"
    const val KASIR = "kasir"
    const val PDI = "pdi"
    const val KONTROL = "kontrol"
}

// ── Komponen bersama ─────────────────────────────────────────────────────────

@Composable
private fun StageBadge(stage: SpkStage) {
    Surface(color = stage.color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            stage.short,
            color = stage.color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Kartu ringkas untuk daftar antrian — bisa diklik untuk membuka detail. */
@Composable
private fun SpkOrderCard(order: SpkOrder, onClick: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth()
    val mod = if (onClick != null) base.clickable(onClick = onClick) else base
    ClayCard(modifier = mod) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(order.nomor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    StageBadge(order.stage)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(order.pelanggan, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${order.unit} · ${order.qty} unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${formatRupiahSpk(order.totalOtr)} · ${paymentLabelSpk(order.paymentStatus)}" + if (order.butuhDiskon) " · diskon ${formatRupiahSpk(order.diskon)}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Scaffold daftar satu-tahap: header + LazyColumn kartu klik-untuk-detail (atau empty state). */
@Composable
private fun StageListScaffold(
    title: String,
    onBack: () -> Unit,
    orders: List<SpkOrder>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    onOpen: ((String) -> Unit)? = null
) {
    TridjayaCollapsibleHeader(title = title, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (orders.isEmpty()) {
            Box(modifier = contentModifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    icon = { Icon(emptyIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                    title = emptyTitle,
                    subtitle = emptySubtitle
                )
            }
        } else {
            LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    SpkOrderCard(order, onClick = onOpen?.let { open -> { open(order.id) } })
                }
            }
        }
    }
}

@Composable
private fun ReasonDialog(title: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            ExpressiveTextField(value = reason, onValueChange = { reason = it }, label = "Alasan", singleLine = false, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ── 1. Input SPK — daftar + form full-screen bottom sheet ────────────────────

@Composable
fun SpkListScreen(onBack: () -> Unit, viewModel: SpkViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsState()
    var showForm by remember { mutableStateOf(false) }

    TridjayaCollapsibleHeader(title = "Input SPK", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ExpressiveFilledButton(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buat SPK")
                }
            }
            if (orders.isEmpty()) {
                item {
                    ExpressiveEmptyState(
                        icon = { Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                        title = "Belum ada SPK",
                        subtitle = "Tekan Buat SPK untuk menambah.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(orders, key = { it.id }) { order -> SpkOrderCard(order) }
            }
        }
    }

    if (showForm) {
        SpkFormSheet(
            defaultSales = viewModel.currentSales,
            defaultCabang = viewModel.currentCabang,
            onDismiss = { showForm = false },
            onSubmit = { p, t, u, q, otr, disk, pay, alamat, cat ->
                viewModel.createSpk(p, t, u, q, otr, disk, pay, alamat, cat)
                showForm = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpkFormSheet(
    defaultSales: String,
    defaultCabang: String,
    onDismiss: () -> Unit,
    onSubmit: (pelanggan: String, telepon: String, unit: String, qty: Int, otr: Double, diskon: Double, paymentStatus: String, alamat: String, catatan: String) -> Unit
) {
    var pelanggan by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var otr by remember { mutableStateOf("") }
    var diskon by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("cash") }
    var alamat by remember { mutableStateOf("") }
    var catatan by remember { mutableStateOf("") }

    val otrValue = otr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    val canSubmit = pelanggan.trim().length >= 3 && unit.trim().length >= 2 && otrValue > 0
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header full-screen dengan tombol tutup.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Buat SPK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$defaultSales · $defaultCabang", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Tutup") }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                ExpressiveTextField(pelanggan, { pelanggan = it }, label = "Nama pelanggan", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(telepon, { telepon = it }, label = "No. HP (opsional)", keyboardType = KeyboardType.Phone, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(unit, { unit = it }, label = "Unit / motor", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveTextField(qty, { qty = it.filter { c -> c.isDigit() } }, label = "Qty", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    ExpressiveTextField(otr, { otr = it.filter { c -> c.isDigit() } }, label = "OTR / unit", keyboardType = KeyboardType.Number, modifier = Modifier.weight(2f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(diskon, { diskon = it.filter { c -> c.isDigit() } }, label = "Diskon diminta (0 = tanpa)", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Text("Status pembayaran", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SPK_PAYMENTS.forEach { (key, label) ->
                        val selected = payment == key
                        Surface(
                            onClick = { payment = key },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(alamat, { alamat = it }, label = "Alamat kirim", singleLine = false, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(catatan, { catatan = it }, label = "Catatan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(20.dp))
                ExpressiveFilledButton(
                    onClick = {
                        onSubmit(
                            pelanggan, telepon, unit,
                            qty.toIntOrNull() ?: 1,
                            otrValue,
                            diskon.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0,
                            payment, alamat, catatan
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan SPK")
                }
                if (!canSubmit) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Isi minimal nama, unit, dan OTR.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ── Daftar antrian per tahap (kartu → detail) ────────────────────────────────

@Composable
fun DiscountApprovalScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: SpkViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsState()
    StageListScaffold(
        title = "Approval Diskon", onBack = onBack,
        orders = orders.filter { it.stage == SpkStage.MENUNGGU_DISKON },
        emptyIcon = Icons.Rounded.Discount,
        emptyTitle = "Tidak ada permintaan diskon",
        emptySubtitle = "Semua permintaan diskon sudah diproses.",
        onOpen = onOpen
    )
}

@Composable
fun KasirQueueScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: SpkViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsState()
    StageListScaffold(
        title = "Antri Kasir", onBack = onBack,
        orders = orders.filter { it.stage == SpkStage.ANTRI_KASIR },
        emptyIcon = Icons.Rounded.PointOfSale,
        emptyTitle = "Antrian kasir kosong",
        emptySubtitle = "Belum ada SPK yang menunggu input kasir.",
        onOpen = onOpen
    )
}

@Composable
fun PdiQueueScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: SpkViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsState()
    StageListScaffold(
        title = "Antri PDI", onBack = onBack,
        orders = orders.filter { it.stage == SpkStage.ANTRI_PDI },
        emptyIcon = Icons.Rounded.FactCheck,
        emptyTitle = "Antrian PDI kosong",
        emptySubtitle = "Belum ada unit yang menunggu inspeksi.",
        onOpen = onOpen
    )
}

@Composable
fun DeliveryControlScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: SpkViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsState()
    StageListScaffold(
        title = "Kontrol Pengiriman", onBack = onBack,
        orders = orders.filter { it.stage == SpkStage.KONTROL_KIRIM },
        emptyIcon = Icons.Rounded.LocalShipping,
        emptyTitle = "Tidak ada yang perlu dikirim",
        emptySubtitle = "Belum ada unit siap kirim (lolos PDI).",
        onOpen = onOpen
    )
}

// ── Detail order (aksi per mode) ─────────────────────────────────────────────

private fun spkModeTitle(mode: String): String = when (mode) {
    SpkMode.DISKON -> "Approval Diskon"
    SpkMode.KASIR -> "Input Kasir SPK"
    SpkMode.PDI -> "Checklist PDI"
    SpkMode.KONTROL -> "Pengerjaan Kiriman"
    else -> "Detail SPK"
}

@Composable
fun SpkOrderDetailScreen(
    mode: String,
    id: String,
    onBack: () -> Unit,
    viewModel: SpkViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsState()
    val order = orders.firstOrNull { it.id == id }

    TridjayaCollapsibleHeader(title = spkModeTitle(mode), onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (order == null) {
            Box(modifier = contentModifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    icon = { Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                    title = "Data tidak ditemukan",
                    subtitle = "Order mungkin sudah diproses ke tahap berikutnya."
                )
            }
        } else {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom)
            ) {
                // Kartu detail order.
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(order.nomor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            StageBadge(order.stage)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(order.pelanggan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${order.unit} · ${order.qty} unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoLine("OTR", formatRupiahSpk(order.totalOtr))
                        InfoLine("Pembayaran", paymentLabelSpk(order.paymentStatus))
                        if (order.butuhDiskon) {
                            InfoLine("Diskon diminta", "- ${formatRupiahSpk(order.diskon)}")
                            InfoLine("Setelah diskon", formatRupiahSpk(order.totalOtr - order.diskon))
                        }
                        order.telepon?.let { InfoLine("No. HP", it) }
                        InfoLine("Alamat", order.alamat)
                        InfoLine("Sales / Cabang", "${order.sales} · ${order.cabang}")
                        order.driver?.let { InfoLine("Driver", it) }
                        order.jadwalKirim?.let { InfoLine("Jadwal kirim", it) }
                        order.catatan?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Catatan: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                // Bagian aksi sesuai mode.
                when (mode) {
                    SpkMode.DISKON -> DiskonAction(order, viewModel, onBack)
                    SpkMode.KASIR -> KasirAction(order, viewModel, onBack)
                    SpkMode.PDI -> PdiAction(order, viewModel, onBack)
                    SpkMode.KONTROL -> KontrolAction(order, viewModel, onBack)
                }
            }
        }
    }
}

@Composable
private fun DiskonAction(order: SpkOrder, viewModel: SpkViewModel, onBack: () -> Unit) {
    var showReject by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpressiveOutlinedButton(onClick = { showReject = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tolak")
        }
        ExpressiveFilledButton(onClick = { viewModel.approveDiskon(order.id); onBack() }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Setujui")
        }
    }
    if (showReject) {
        ReasonDialog(
            title = "Tolak diskon?", confirmLabel = "Tolak",
            onDismiss = { showReject = false },
            onConfirm = { alasan -> viewModel.rejectDiskon(order.id, alasan); showReject = false; onBack() }
        )
    }
}

@Composable
private fun KasirAction(order: SpkOrder, viewModel: SpkViewModel, onBack: () -> Unit) {
    ExpressiveFilledButton(onClick = { viewModel.kasirProses(order.id); onBack() }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Proses & Input SPK")
    }
}

@Composable
private fun PdiAction(order: SpkOrder, viewModel: SpkViewModel, onBack: () -> Unit) {
    Text("Checklist PDI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    PDI_ITEMS.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.togglePdi(order.id, item.key) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.key in order.pdiChecked, onCheckedChange = { viewModel.togglePdi(order.id, item.key) })
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    val complete = viewModel.isPdiComplete(order)
    ExpressiveFilledButton(onClick = { viewModel.selesaikanPdi(order.id); onBack() }, enabled = complete, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (complete) "Selesaikan PDI" else "Lengkapi checklist dulu")
    }
}

@Composable
private fun KontrolAction(order: SpkOrder, viewModel: SpkViewModel, onBack: () -> Unit) {
    var driver by remember { mutableStateOf<String?>(null) }
    var jadwal by remember { mutableStateOf("2026-07-22") }

    Text("Assign driver", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SPK_DRIVERS.chunked(2).forEach { rowDrivers ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowDrivers.forEach { name ->
                    val selected = driver == name
                    Surface(
                        onClick = { driver = name },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    ExpressiveTextField(jadwal, { jadwal = it }, label = "Jadwal kirim (yyyy-mm-dd)", modifier = Modifier.fillMaxWidth())
    Spacer(modifier = Modifier.height(12.dp))
    ExpressiveFilledButton(
        onClick = { driver?.let { viewModel.serahkanKePengiriman(order.id, it, jadwal); onBack() } },
        enabled = driver != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (driver == null) "Pilih driver dulu" else "Serahkan ke Pengiriman")
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "Setelah diserahkan, order masuk ke pipeline pengiriman (menu Kirim) untuk diantar.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

