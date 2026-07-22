package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
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
import kotlinx.coroutines.delay

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
                        InfoLine("Kategori", job.kategori)
                        InfoLine("Diskon", job.diskon?.takeIf { it > 0 }?.let { rupiah(it) })
                        InfoLine("DP Net", job.dpNet?.let { rupiah(it) })
                        InfoLine("Pembayaran 1", job.pembayaran1?.let { rupiah(it) })
                        InfoLine("Angsuran", job.angsuran?.let { rupiah(it) })
                        InfoLine("Tenor", job.tenor?.let { "$it bln" })
                        InfoLine("Sumber Order", when {
                            job.orderSource == "kbk" -> "KBK · ${job.kbkBrokerNama ?: job.kbkBrokerKode ?: "-"}"
                            job.orderSource != null -> "Sales"
                            else -> null
                        })
                        InfoLine("Komisi Sales", job.komisiSales?.let { rupiah(it) })
                        InfoLine("Komisi KBK", job.komisiKbk?.let { rupiah(it) })
                        InfoLine("No. HP KBK", job.noHpKbk)
                        InfoLine("Sosmed", listOfNotNull(
                            job.sosmedTiktok?.let { "TikTok $it" },
                            job.sosmedFacebook?.let { "FB $it" },
                            job.sosmedInstagram?.let { "IG $it" },
                        ).joinToString(" · ").ifBlank { null })
                        InfoLine("Terima Uang Driver", job.driverTerimaUang?.takeIf { it }?.let {
                            job.driverTerimaNominal?.let { n -> rupiah(n) } ?: "Ya"
                        })
                        InfoLine("Chat Konsumen", job.consumerChatAt)
                        job.reviewRating?.let { InfoLine("Rating", "★".repeat(it)) }
                        job.customerMapUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Buka Lokasi Maps") }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                state.actionError?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (job.driverTerimaUang != null &&
                    (job.status == DeliveryStatusKey.ASSIGNED || job.status == DeliveryStatusKey.IN_TRANSIT)
                ) {
                    ChatConsumerCard(job, viewModel, state.submitting)
                    Spacer(Modifier.height(14.dp))
                }
                when (job.status) {
                    DeliveryStatusKey.PENDING_PDI -> PdiAction(job.id, viewModel, state.submitting, state.checklist)
                    DeliveryStatusKey.PENDING_SPK -> SimpleAction("Konfirmasi SPK (Kasir)", state.submitting) { viewModel.confirmSpk(job.id) {} }
                    DeliveryStatusKey.PENDING_DELIVERY_NOTE -> DeliveryNoteAction(job, viewModel, state.submitting)
                    DeliveryStatusKey.PENDING_SCHEDULING -> AssignAction(job, viewModel, state.submitting, state.drivers)
                    DeliveryStatusKey.ASSIGNED -> SimpleAction("Berangkat (Dispatch)", state.submitting) { viewModel.dispatch(job.id) {} }
                    DeliveryStatusKey.IN_TRANSIT -> DeliverAction(job, viewModel, state.submitting, state.driverChecklist)
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
private fun AssignAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean, drivers: List<com.krisoft.tridjayaelektronik.data.model.DriverDto>) {
    val id = job.id
    var driverId by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    // minSdk 24 tanpa coreLibraryDesugaring (dicek app/build.gradle.kts) — java.time.LocalDate
    // butuh API 26, jadi pakai SimpleDateFormat.
    var date by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }
    var mapUrl by remember { mutableStateOf(job.customerMapUrl.orEmpty()) }

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
    if (job.customerMapUrl.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        ExpressiveTextField(mapUrl, { mapUrl = it }, label = "Link Google Maps konsumen (wajib)", modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(
        onClick = { vm.assign(id, driverId, driverName, date, mapUrl.trim().ifBlank { null }) {} },
        enabled = !submitting && driverId.trim().isNotEmpty() && date.trim().isNotEmpty() && mapUrl.trim().isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Assign Driver")
    }
}

@Composable
private fun DeliverAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean, driverChecklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>) {
    val id = job.id
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/deliver_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    var hasPhoto by remember { mutableStateOf(false) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) { vm.onDeliverPhotoCaptured(file); hasPhoto = true } }
    // 088: foto bukti terima uang (wajib bila job.driverTerimaUang == true)
    val needCash = job.driverTerimaUang == true
    val cashFile = remember { File(context.cacheDir, "delivery/cash_$id.jpg").apply { parentFile?.mkdirs() } }
    val cashUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cashFile) }
    var hasCashPhoto by remember { mutableStateOf(false) }
    val cashCam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) { vm.onCashPhotoCaptured(cashFile); hasCashPhoto = true } }
    // 088: checklist serah-terima stage=driver (fail-open bila kosong)
    val hasil = remember(driverChecklist) { mutableStateMapOf<String, String>().apply { driverChecklist.forEach { put(it.id, "ok") } } }
    val catatan = remember(driverChecklist) { mutableStateMapOf<String, String>() }

    Text("Serah Terima", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    PhotoBox(if (hasPhoto) file else null, "Foto serah terima (wajib)") { cam.launch(uri) }
    if (needCash) {
        Spacer(Modifier.height(10.dp))
        PhotoBox(if (hasCashPhoto) cashFile else null, "Foto serah terima uang (wajib${job.driverTerimaNominal?.let { " · ${rupiah(it)}" } ?: ""})") { cashCam.launch(cashUri) }
    }
    if (driverChecklist.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Checklist Serah Terima", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        driverChecklist.sortedBy { it.urutan }.forEach { item ->
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
    // 088: hint gate chat H-1 (backend otoritatif; klien cuma peringatan dini)
    if (job.driverTerimaUang != null && job.consumerChatAt == null) {
        Spacer(Modifier.height(8.dp))
        Text("Belum chat konsumen — serah terima akan ditolak backend (wajib chat ≥1 jam sebelumnya).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(14.dp))
    val missingCatatan = driverChecklist.any { hasil[it.id] == "tidak" && catatan[it.id].orEmpty().isBlank() }
    val canDeliver = hasPhoto && (!needCash || hasCashPhoto) && !missingCatatan
    ExpressiveFilledButton(
        onClick = {
            val bodies = driverChecklist.map { com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody(item = it.itemLabel, hasil = hasil[it.id] ?: "ok", catatan = catatan[it.id]?.trim()?.ifBlank { null }) }
            vm.deliver(id, rating, comment, bodies) {}
        },
        enabled = !submitting && canDeliver, modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(when {
            !hasPhoto -> "Ambil foto dulu"
            needCash && !hasCashPhoto -> "Ambil foto uang dulu"
            missingCatatan -> "Isi catatan item 'Tidak'"
            else -> "Tandai Terkirim"
        })
    }
}

/** 088: chat konsumen H-1 — wajib ≥1 jam sebelum serah terima (gate backend). */
@Composable
private fun ChatConsumerCard(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Chat Konsumen (H-1)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (job.consumerChatAt != null) {
                Text("Sudah chat: ${job.consumerChatAt}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF12B76A), fontWeight = FontWeight.SemiBold)
            } else {
                Text("Wajib chat konsumen minimal 1 jam sebelum serah terima.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val phone = job.customerPhone?.filter { it.isDigit() }.orEmpty()
                    .let { if (it.startsWith("0")) "62" + it.drop(1) else it }
                ExpressiveOutlinedButton(
                    onClick = { if (phone.isNotBlank()) runCatching { uriHandler.openUri("https://wa.me/$phone") } },
                    enabled = phone.isNotBlank(), modifier = Modifier.weight(1f)
                ) { Text("Chat WA") }
                if (job.consumerChatAt == null) {
                    ExpressiveFilledButton(onClick = { vm.chatConsumer(job.id) }, enabled = !submitting, modifier = Modifier.weight(1f)) {
                        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Tandai Sudah Chat")
                    }
                }
            }
        }
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
    LaunchedEffect(Unit) { viewModel.loadDeliveryContextForCreate() }

    // Header — Pelanggan
    var pelanggan by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var mapUrl by remember { mutableStateOf("") }
    var nik by remember { mutableStateOf("") }
    var sosTiktok by remember { mutableStateOf("") }
    var sosFb by remember { mutableStateOf("") }
    var sosIg by remember { mutableStateOf("") }
    var keterangan by remember { mutableStateOf("") }
    // Barang multi-unit
    var spkCabang by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<SpkItemDraft>()) }
    var barangSearch by remember { mutableStateOf("") }
    var brokerSearch by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var sec1 by remember { mutableStateOf(true) }
    var sec2 by remember { mutableStateOf(true) }
    var gantiCabangTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.deliveryContext) {
        if (spkCabang.isBlank()) state.deliveryContext?.kodeDealer?.let { spkCabang = it }
    }
    LaunchedEffect(barangSearch, spkCabang) { delay(300); viewModel.searchStok(barangSearch, spkCabang) }
    LaunchedEffect(brokerSearch) { delay(300); viewModel.searchBrokers(brokerSearch) }

    fun applyCabangChange(next: String) {
        spkCabang = next; items = emptyList(); barangSearch = ""
        viewModel.searchStok("", next); viewModel.clearSerialCache()
    }

    val totalUnits = items.sumOf { it.qtyInt ?: 0 }
    val itemsValid = items.isNotEmpty() && items.all { it.issues().isEmpty() }
    val canSubmit = pelanggan.trim().length >= 3 && telepon.trim().length >= 6 &&
        spkCabang.isNotBlank() && itemsValid && totalUnits in 1..200

    TridjayaCollapsibleHeader(title = "Input SPK", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            contentModifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpkSection("1. Pelanggan", sec1, { sec1 = !sec1 }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveTextField(pelanggan, { pelanggan = it }, label = "Nama pelanggan *", modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(telepon, { telepon = it }, label = "No. HP *", keyboardType = KeyboardType.Phone, modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(alamat, { alamat = it }, label = "Alamat", singleLine = false, modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(mapUrl, { mapUrl = it }, label = "Link Lokasi Maps", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(nik, { nik = it }, label = "NIK", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(sosTiktok, { sosTiktok = it }, label = "TikTok", modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(sosFb, { sosFb = it }, label = "Facebook", modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(sosIg, { sosIg = it }, label = "Instagram", modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(keterangan, { keterangan = it }, label = "Keterangan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
                }
            }

            SpkSection("2. Barang (${items.size} barang · $totalUnits unit)", sec2, { sec2 = !sec2 }) {
                CabangSelector(
                    selected = spkCabang,
                    onSelect = { next ->
                        if (next.isBlank() || next == spkCabang) return@CabangSelector
                        if (items.isNotEmpty()) gantiCabangTarget = next else applyCabangChange(next)
                    }
                )
                Spacer(Modifier.height(10.dp))
                if (spkCabang.isNotBlank()) {
                    ExpressiveTextField(barangSearch, { barangSearch = it }, label = "Cari & tambah barang (min. 2 karakter)", modifier = Modifier.fillMaxWidth())
                    when {
                        state.stokLoading -> Text("Mencari…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        state.stokAttempted && state.stokResults.isEmpty() -> Text("Tidak ditemukan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (state.stokResults.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.stokResults.forEach { row ->
                                Surface(
                                    onClick = {
                                        // Prepend + collapse kartu lain (baru = fokus)
                                        items = listOf(newSpkItemDraft(row)) + items.map { it.copy(expanded = false) }
                                        barangSearch = ""
                                        viewModel.ensureSerials(spkCabang, row.kode.trim())
                                    },
                                    shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(row.nama.trim(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${row.kode} · ${row.kategori} · ${row.merk}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("Pilih Cabang SPK dulu untuk mencari stok.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEachIndexed { idx, item ->
                            val key = "$spkCabang|${item.kodeBarang}"
                            val usedElsewhere = items.filterIndexed { i, o -> i != idx && o.serialNumber.isNotBlank() }.map { it.serialNumber }
                            SpkItemCard(
                                index = idx,
                                item = item,
                                issues = if (attemptedSubmit) item.issues() else emptyList(),
                                serialOptions = (state.serialOptions[key] ?: emptyList()).filter { it !in usedElsewhere },
                                brokerResults = state.brokerResults,
                                brokerSearch = brokerSearch,
                                onBrokerSearch = { brokerSearch = it },
                                onUpdate = { updated -> items = items.mapIndexed { i, o -> if (i == idx) updated else o } },
                                onRemove = { items = items.filterIndexed { i, _ -> i != idx } },
                                onSerialFocus = { viewModel.ensureSerials(spkCabang, item.kodeBarang) },
                            )
                        }
                    }
                }
            }

            state.actionError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            if (attemptedSubmit && !canSubmit) {
                Text(
                    when {
                        pelanggan.trim().length < 3 || telepon.trim().length < 6 -> "Lengkapi nama & No. HP pelanggan."
                        items.isEmpty() -> "Tambah minimal 1 barang dari pencarian stok."
                        totalUnits > 200 -> "Total unit maksimal 200."
                        else -> "Ada barang belum lengkap — cek tanda merah di kartu."
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
                )
            }
            ExpressiveFilledButton(
                onClick = {
                    attemptedSubmit = true
                    if (!canSubmit) return@ExpressiveFilledButton
                    val body = CreateDeliveryBody(
                        customerName = pelanggan.trim(), customerPhone = telepon.trim(),
                        customerAddress = alamat.trim().ifBlank { null },
                        customerMapUrl = mapUrl.trim().ifBlank { null },
                        customerNik = nik.trim().ifBlank { null },
                        salesNik = null,
                        sosmedTiktok = sosTiktok.trim().ifBlank { null },
                        sosmedFacebook = sosFb.trim().ifBlank { null },
                        sosmedInstagram = sosIg.trim().ifBlank { null },
                        keterangan = keterangan.trim().ifBlank { null },
                        items = items.map { it.toItemBody(spkCabang, BranchRegions.dealerRegion(spkCabang)) }
                    )
                    viewModel.createSpk(body) {}
                },
                enabled = !state.submitting, modifier = Modifier.fillMaxWidth()
            ) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (totalUnits > 0) "Catat Penjualan ($totalUnits unit)" else "Catat Penjualan")
            }
            Text("Tiap unit fisik jadi baris antrian PDI terpisah.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    gantiCabangTarget?.let { next ->
        AlertDialog(
            onDismissRequest = { gantiCabangTarget = null },
            title = { Text("Ganti cabang?", fontWeight = FontWeight.Bold) },
            text = { Text("Ganti cabang akan mengosongkan semua barang terpilih. Lanjutkan?") },
            confirmButton = { TextButton(onClick = { applyCabangChange(next); gantiCabangTarget = null }) { Text("Ya") } },
            dismissButton = { TextButton(onClick = { gantiCabangTarget = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

/** Kartu section collapsible untuk Input SPK — header tap buka/tutup isi. */
@Composable
private fun SpkSection(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Selektor Cabang SPK — wajib, tanpa opsi kosong. Pola visual mirror
 *  `OptionDropdownField` (`ui/leads/AddLeadScreen.kt`), grouped per region. */
@Composable
private fun CabangSelector(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = BranchRegions.DEALER_LABEL[selected] ?: ""
    Column {
        Text("Cabang SPK *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLabel.ifBlank { "Pilih cabang…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentLabel.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BranchRegions.cabangOptionsByRegion().forEach { group ->
                    Text(
                        group.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    group.cabang.forEach { c ->
                        DropdownMenuItem(text = { Text(c.label) }, onClick = { onSelect(c.kodeDealer); expanded = false })
                    }
                }
            }
        }
    }
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
