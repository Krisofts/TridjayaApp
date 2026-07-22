package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryItemBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.io.File

// ── Meta status ──────────────────────────────────────────────────────────────

private fun statusMeta(status: String): Pair<String, Color> = when (status) {
    DeliveryStatusKey.PENDING_DISCOUNT -> "Tunggu Diskon" to Color(0xFFB5670C)
    DeliveryStatusKey.PENDING_PDI -> "Antri PDI" to Color(0xFF6941C6)
    DeliveryStatusKey.PENDING_SPK -> "Antri Kasir" to Color(0xFF0086C9)
    DeliveryStatusKey.PENDING_DELIVERY_NOTE -> "Surat Jalan" to Color(0xFF0E9384)
    DeliveryStatusKey.PENDING_SCHEDULING -> "Penjadwalan" to Color(0xFF0E9384)
    DeliveryStatusKey.ASSIGNED -> "Siap Berangkat" to Color(0xFF1565C0)
    DeliveryStatusKey.IN_TRANSIT -> "Dalam Perjalanan" to Color(0xFF1E63E9)
    DeliveryStatusKey.DELIVERED -> "Terkirim" to Color(0xFF12B76A)
    DeliveryStatusKey.CANCELLED -> "Batal" to Color(0xFFF04438)
    else -> status to Color(0xFF667085)
}

private fun rupiah(v: Double?): String {
    val n = (v ?: 0.0).toLong()
    return "Rp" + n.toString().reversed().chunked(3).joinToString(".").reversed()
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = statusMeta(status)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun JobCard(job: DeliveryJobDto, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth()
    ClayCard(modifier = if (onClick != null) base.clickable(onClick = onClick) else base) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(job.kodePengiriman, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    StatusChip(job.status)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(job.customerName ?: "-", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${job.namaBarang ?: job.kodeBarang ?: "-"}${job.tipe?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Antrian per-tahap ────────────────────────────────────────────────────────

@Composable
fun DeliveryQueueScreen(
    title: String,
    status: String?,
    view: String? = null,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: DeliveryFlowViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(status, view) { viewModel.loadQueue(status, view) }

    TridjayaCollapsibleHeader(title = title, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.loading && state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = { viewModel.loadQueue(status, view) })
                }
            state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = { Icon(Icons.Rounded.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                        title = "Antrian kosong", subtitle = "Belum ada job pada tahap ini."
                    )
                }
            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp).let { PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom) },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.id }) { job -> JobCard(job, onClick = { onOpen(job.id) }) }
            }
        }
    }
}

// ── Detail + aksi per-tahap ──────────────────────────────────────────────────

@Composable
fun DeliveryJobDetailScreen(id: String, onBack: () -> Unit, viewModel: DeliveryFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(id) { viewModel.loadDetail(id) }
    LaunchedEffect(state.actionDone) { if (state.actionDone) onBack() }

    val job = state.detail
    TridjayaCollapsibleHeader(title = "Detail Pengiriman", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.loading && job == null -> Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            job == null -> Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                ExpressiveErrorState(message = state.error ?: "Data tidak ditemukan", onRetry = { viewModel.loadDetail(id) })
            }
            else -> Column(
                contentModifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom)
            ) {
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(job.kodePengiriman, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            StatusChip(job.status)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(job.customerName ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${job.namaBarang ?: job.kodeBarang ?: "-"}${job.tipe?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        InfoLine("Merk / Warna", listOfNotNull(job.merk, job.warna).joinToString(" · ").ifBlank { null })
                        InfoLine("OTR", job.hargaOtr?.let { rupiah(it) })
                        InfoLine("Pembayaran", job.paymentType?.replaceFirstChar { it.uppercase() })
                        InfoLine("No. HP", job.customerPhone)
                        InfoLine("Alamat", job.customerAddress)
                        InfoLine("Serial", job.serialNumber)
                        InfoLine("Surat Jalan", job.deliveryNoteNo)
                        InfoLine("Driver", job.assignedDriverName)
                        InfoLine("Jadwal", job.scheduledDate)
                        InfoLine("Sales", job.salesName)
                        job.reviewRating?.let { InfoLine("Rating", "★".repeat(it)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                state.actionError?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                when (job.status) {
                    DeliveryStatusKey.PENDING_PDI -> PdiAction(job.id, viewModel, state.submitting, state.checklist)
                    DeliveryStatusKey.PENDING_SPK -> SimpleAction("Konfirmasi SPK (Kasir)", state.submitting) { viewModel.confirmSpk(job.id) {} }
                    DeliveryStatusKey.PENDING_DELIVERY_NOTE -> DeliveryNoteAction(job, viewModel, state.submitting)
                    DeliveryStatusKey.PENDING_SCHEDULING -> AssignAction(job.id, viewModel, state.submitting, state.drivers)
                    DeliveryStatusKey.ASSIGNED -> SimpleAction("Berangkat (Dispatch)", state.submitting) { viewModel.dispatch(job.id) {} }
                    DeliveryStatusKey.IN_TRANSIT -> DeliverAction(job.id, viewModel, state.submitting)
                    else -> Text("Tidak ada aksi pada tahap ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SimpleAction(label: String, submitting: Boolean, onClick: () -> Unit) {
    ExpressiveFilledButton(onClick = onClick, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Spacer(Modifier.width(4.dp)); Text(label)
    }
}

@Composable
private fun PdiAction(id: String, vm: DeliveryFlowViewModel, submitting: Boolean, checklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>) {
    var serial by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/pdi_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    var hasPhoto by remember { mutableStateOf(false) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) { vm.onPdiPhotoCaptured(file); hasPhoto = true } }

    // Hasil checklist per item.id: hasil (ok/tidak/na) default "ok" + catatan.
    val hasil = remember(checklist) { mutableStateMapOf<String, String>().apply { checklist.forEach { put(it.id, "ok") } } }
    val catatan = remember(checklist) { mutableStateMapOf<String, String>() }

    Text("PDI / Inspeksi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    ExpressiveTextField(serial, { serial = it }, label = "Nomor serial (wajib)", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    ExpressiveTextField(engine, { engine = it }, label = "Nomor mesin (opsional)", modifier = Modifier.fillMaxWidth())

    if (checklist.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Checklist", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        checklist.sortedBy { it.urutan }.forEach { item ->
            Spacer(Modifier.height(6.dp))
            Text(item.itemLabel + if (item.wajib) " *" else "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ok" to "OK", "tidak" to "Tidak", "na" to "N/A").forEach { (k, l) ->
                    val sel = hasil[item.id] == k
                    Surface(onClick = { hasil[item.id] = k }, shape = RoundedCornerShape(50),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                        Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
            }
            if (hasil[item.id] == "tidak") {
                Spacer(Modifier.height(4.dp))
                ExpressiveTextField(catatan[item.id].orEmpty(), { catatan[item.id] = it }, label = "Catatan (wajib untuk Tidak)", modifier = Modifier.fillMaxWidth())
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    PhotoBox(if (hasPhoto) file else null, "Foto unit siap (opsional)") { cam.launch(uri) }
    Spacer(Modifier.height(14.dp))

    val missingCatatan = checklist.any { hasil[it.id] == "tidak" && catatan[it.id].orEmpty().isBlank() }
    ExpressiveFilledButton(
        onClick = {
            val bodies = checklist.map { com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody(item = it.itemLabel, hasil = hasil[it.id] ?: "ok", catatan = catatan[it.id]?.trim()?.ifBlank { null }) }
            vm.submitPdi(id, serial, engine, bodies) {}
        },
        enabled = !submitting && serial.trim().isNotEmpty() && !missingCatatan, modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(if (missingCatatan) "Isi catatan item 'Tidak'" else "Simpan PDI")
    }
}

@Composable
private fun DeliveryNoteAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var source by remember { mutableStateOf(job.kodeDealer.orEmpty()) }
    Text("Terbitkan Surat Jalan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    ExpressiveTextField(source, { source = it }, label = "Cabang sumber unit (wajib)", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(onClick = { vm.issueDeliveryNote(job.id, source) {} }, enabled = !submitting && source.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Terbitkan Surat Jalan")
    }
}

@Composable
private fun AssignAction(id: String, vm: DeliveryFlowViewModel, submitting: Boolean, drivers: List<com.krisoft.tridjayaelektronik.data.model.DriverDto>) {
    var driverId by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-07-22") }

    Text("Assign Driver + Jadwal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (drivers.isNotEmpty()) {
        Text("Pilih driver", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            drivers.forEach { d ->
                val sel = driverId == d.effectiveId
                Surface(onClick = { driverId = d.effectiveId; driverName = d.name }, shape = RoundedCornerShape(12.dp),
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Text(d.name.ifBlank { d.effectiveId }, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        // Fallback bila daftar driver tak bisa dimuat (role tak berizin / endpoint kosong).
        ExpressiveTextField(driverName, { driverName = it }, label = "Nama driver", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        ExpressiveTextField(driverId, { driverId = it }, label = "ID driver (user id)", modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    ExpressiveTextField(date, { date = it }, label = "Jadwal kirim (yyyy-mm-dd)", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(onClick = { vm.assign(id, driverId, driverName, date) {} }, enabled = !submitting && driverId.trim().isNotEmpty() && date.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Assign Driver")
    }
}

@Composable
private fun DeliverAction(id: String, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/deliver_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    var hasPhoto by remember { mutableStateOf(false) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) { vm.onDeliverPhotoCaptured(file); hasPhoto = true } }

    Text("Serah Terima", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    PhotoBox(if (hasPhoto) file else null, "Foto serah terima (wajib)") { cam.launch(uri) }
    Spacer(Modifier.height(12.dp))
    Text("Rating pengiriman (wajib)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Row {
        (1..5).forEach { i ->
            Icon(
                if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "Rating $i", tint = Color(0xFFF6B10A),
                modifier = Modifier.size(36.dp).clickable { rating = i }.padding(2.dp)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    ExpressiveTextField(comment, { comment = it }, label = "Komentar (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(onClick = { vm.deliver(id, rating, comment) {} }, enabled = !submitting && hasPhoto, modifier = Modifier.fillMaxWidth()) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(if (hasPhoto) "Tandai Terkirim" else "Ambil foto dulu")
    }
}

@Composable
private fun PhotoBox(file: File?, label: String, onCapture: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Surface(onClick = onCapture, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().height(170.dp)) {
        if (file != null && file.exists()) {
            AsyncImage(model = file, contentDescription = "Foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp)); Text("Ketuk untuk ambil foto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Input SPK ────────────────────────────────────────────────────────────────

@Composable
fun CreateSpkScreen(onBack: () -> Unit, viewModel: DeliveryFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.actionDone) { if (state.actionDone) onBack() }

    var pelanggan by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var mapUrl by remember { mutableStateOf("") }
    var kodeBarang by remember { mutableStateOf("") }
    var namaBarang by remember { mutableStateOf("") }
    var kategori by remember { mutableStateOf("") }
    var merk by remember { mutableStateOf("") }
    var tipe by remember { mutableStateOf("") }
    var warna by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var otr by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("cash") }
    var fincoy by remember { mutableStateOf("") }
    var keterangan by remember { mutableStateOf("") }

    val otrValue = otr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    val canSubmit = pelanggan.trim().length >= 3 && telepon.trim().length >= 6 &&
        kodeBarang.trim().isNotEmpty() && namaBarang.trim().isNotEmpty() && kategori.trim().isNotEmpty() &&
        merk.trim().isNotEmpty() && tipe.trim().isNotEmpty() && otrValue > 0 &&
        (payment != "credit" || fincoy.trim().isNotEmpty())

    TridjayaCollapsibleHeader(title = "Input SPK", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom)) {
            SectionLabel("Pelanggan")
            ExpressiveTextField(pelanggan, { pelanggan = it }, label = "Nama pelanggan", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(telepon, { telepon = it }, label = "No. HP", keyboardType = KeyboardType.Phone, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(alamat, { alamat = it }, label = "Alamat", singleLine = false, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(mapUrl, { mapUrl = it }, label = "Link Google Maps konsumen (wajib utk penugasan driver)", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            SectionLabel("Unit")
            ExpressiveTextField(kodeBarang, { kodeBarang = it }, label = "Kode barang", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(namaBarang, { namaBarang = it }, label = "Nama barang", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveTextField(kategori, { kategori = it }, label = "Kategori", modifier = Modifier.weight(1f))
                ExpressiveTextField(merk, { merk = it }, label = "Merk", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveTextField(tipe, { tipe = it }, label = "Tipe", modifier = Modifier.weight(1f))
                ExpressiveTextField(warna, { warna = it }, label = "Warna", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveTextField(qty, { qty = it.filter { c -> c.isDigit() } }, label = "Qty", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                ExpressiveTextField(otr, { otr = it.filter { c -> c.isDigit() } }, label = "OTR / unit", keyboardType = KeyboardType.Number, modifier = Modifier.weight(2f))
            }
            Spacer(Modifier.height(12.dp))
            Text("Pembayaran", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cash" to "Cash", "credit" to "Kredit").forEach { (k, l) ->
                    val sel = payment == k
                    Surface(onClick = { payment = k }, shape = RoundedCornerShape(50),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                        Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                }
            }
            if (payment == "credit") {
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(fincoy, { fincoy = it }, label = "Leasing / Fincoy (wajib utk kredit)", modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(keterangan, { keterangan = it }, label = "Keterangan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
            state.actionError?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(18.dp))
            ExpressiveFilledButton(
                onClick = {
                    viewModel.createSpk(
                        pelanggan, telepon, alamat, mapUrl,
                        CreateDeliveryItemBody(
                            kodeBarang = kodeBarang.trim(), namaBarang = namaBarang.trim(), kategori = kategori.trim(),
                            merk = merk.trim(), tipe = tipe.trim(), warna = warna.trim().ifBlank { null },
                            qty = qty.toIntOrNull() ?: 1, paymentType = payment, fincoy = fincoy.trim().ifBlank { null }, hargaOtr = otrValue
                        ),
                        keterangan
                    ) {}
                },
                enabled = canSubmit && !state.submitting, modifier = Modifier.fillMaxWidth()
            ) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Buat SPK")
            }
            if (!canSubmit) { Spacer(Modifier.height(6.dp)); Text("Lengkapi pelanggan, HP, data unit + OTR (kredit: isi fincoy).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

// ── Approval Diskon per-baris ────────────────────────────────────────────────

@Composable
fun DiscountApprovalScreen(onBack: () -> Unit, viewModel: DeliveryFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadDiscounts("pending") }
    var rejectId by remember { mutableStateOf<String?>(null) }

    TridjayaCollapsibleHeader(title = "Approval Diskon", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.loading && state.discounts.isEmpty() ->
                Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.discounts.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = { viewModel.loadDiscounts("pending") })
                }
            state.discounts.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = { Icon(Icons.Rounded.Discount, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                        title = "Tidak ada pengajuan diskon", subtitle = "Semua pengajuan sudah diputuskan."
                    )
                }
            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.actionError?.let { item { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) } }
                items(state.discounts, key = { it.id }) { d ->
                    DiscountCard(d, state.submitting, onApprove = { viewModel.approveDiscount(d.id, "") }, onReject = { rejectId = d.id })
                }
            }
        }
    }

    rejectId?.let { id ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectId = null },
            title = { Text("Tolak diskon?", fontWeight = FontWeight.Bold) },
            text = { ExpressiveTextField(note, { note = it }, label = "Catatan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { viewModel.rejectDiscount(id, note); rejectId = null }) { Text("Tolak") } },
            dismissButton = { TextButton(onClick = { rejectId = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun DiscountCard(d: com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto, submitting: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.spkBatchKode + (d.baris?.let { " · baris $it" } ?: ""), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Diskon ${rupiah(d.value)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFB5670C))
            }
            Spacer(Modifier.height(6.dp))
            Text(d.jobSummary?.namaBarang ?: d.jobSummary?.kodeBarang ?: "-", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.jobSummary?.customerName ?: "-", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            InfoLine("Harga sebelum", d.hargaSebelum?.let { rupiah(it) })
            InfoLine("Harga sesudah", d.hargaSesudah?.let { rupiah(it) })
            InfoLine("Alasan", d.reason)
            InfoLine("Diajukan", d.requestedByName)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveOutlinedButton(onClick = onReject, enabled = !submitting, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tolak")
                }
                ExpressiveFilledButton(onClick = onApprove, enabled = !submitting, modifier = Modifier.weight(1f)) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                    Text("Setujui")
                }
            }
        }
    }
}
