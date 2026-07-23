package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

// Tujuan pengambilan aki — WAJIB salah satu slug enum backend (aki.rs TUJUAN_VALID).
private val AKI_TUJUAN_OPTIONS = listOf(
    "pemasangan_unit_baru" to "Pemasangan unit baru",
    "penggantian_garansi" to "Penggantian garansi",
    "service_repair" to "Service / repair",
    "display" to "Display",
    "lainnya" to "Lainnya…",
)
internal fun akiTujuanLabel(slug: String?): String =
    AKI_TUJUAN_OPTIONS.firstOrNull { it.first == slug }?.second ?: (slug ?: "-")

// Merk aki dari data BATERAI GS (erp_mirror_stok, kategori BATERAI) — merk nyata yang dipakai.
// "Lainnya…" = ketik manual (item aki merk baru yang belum ada di daftar).
private const val AKI_MERK_LAINNYA = "__lainnya__"
private val AKI_MERK_OPTIONS = listOf(
    "GODA", "EXOTIC", "SAIGE", "AVIATOR", "CHILWEE", "SELIS",
    "U-WINFLY", "DUBBS", "PACIFIC", "AIMA", "SOLOS", "QUEEN",
)
// Kapasitas umum dari nama barang BATERAI GS (tegangan×kapasitas).
private val AKI_KAPASITAS_OPTIONS = listOf("36V12AH", "48V12AH", "48V20AH")
// 1 set baterai sepeda listrik = 4 pcs fisik (48V pack = 4× baterai 12V).
private const val AKI_PCS_PER_SET = 4

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
    reorderable: Boolean = false,
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
            else -> Column(modifier = contentModifier.fillMaxSize()) {
                state.actionError?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, job ->
                        if (reorderable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) { JobCard(job, onClick = { onOpen(job.id) }) }
                                Column {
                                    IconButton(onClick = { viewModel.moveLoad(job.id, up = true) }, enabled = index > 0) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Naikkan urutan")
                                    }
                                    IconButton(onClick = { viewModel.moveLoad(job.id, up = false) }, enabled = index < state.items.size - 1) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Turunkan urutan")
                                    }
                                }
                            }
                        } else {
                            JobCard(job, onClick = { onOpen(job.id) })
                        }
                    }
                }
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
                // Foto bukti (PDI siap kirim / serah terima / terima uang) — dimuat
                // ter-autentikasi via VM (kasir/DC/driver bisa verifikasi dari HP).
                if (state.jobPhotos.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    JobPhotosCard(state.jobPhotos)
                }
                Spacer(Modifier.height(14.dp))
                val shareContext = LocalContext.current
                ExpressiveOutlinedButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "Lacak pengiriman Anda: " + com.krisoft.tridjayaelektronik.BuildConfig.API_BASE_URL.trimEnd('/') + "/cek-resi/" + job.id)
                    }
                    shareContext.startActivity(android.content.Intent.createChooser(send, "Bagikan resi"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Bagikan Resi")
                }
                Spacer(Modifier.height(14.dp))
                state.actionError?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                // Aksi per-tahap DIGATE role viewer (SpkAccessPolicy — mirror backend):
                // buka job lewat Riwayat jangan menampilkan tombol yang pasti 403
                // (mis. driver lihat "Assign Driver"). Backend tetap otoritatif.
                val access = viewModel.access
                val isMyDriverJob = viewModel.isAdminViewer ||
                    (access.driver && job.assignedDriverId == viewModel.currentUserId)
                if (job.driverTerimaUang != null && isMyDriverJob &&
                    (job.status == DeliveryStatusKey.ASSIGNED || job.status == DeliveryStatusKey.IN_TRANSIT)
                ) {
                    ChatConsumerCard(job, viewModel, state.submitting)
                    Spacer(Modifier.height(14.dp))
                }
                when {
                    job.status == DeliveryStatusKey.PENDING_PDI && access.pdi ->
                        PdiAction(job.id, viewModel, state.submitting, state.checklist, state.requiresAki, state.akiForms)
                    job.status == DeliveryStatusKey.PENDING_SPK && access.kasir ->
                        SimpleAction("Konfirmasi SPK (Kasir)", state.submitting) { viewModel.confirmSpk(job.id) {} }
                    job.status == DeliveryStatusKey.PENDING_DELIVERY_NOTE && access.note ->
                        DeliveryNoteAction(job, viewModel, state.submitting)
                    job.status == DeliveryStatusKey.PENDING_SCHEDULING && access.jadwal ->
                        AssignAction(job, viewModel, state.submitting, state.drivers)
                    job.status == DeliveryStatusKey.ASSIGNED && isMyDriverJob ->
                        SimpleAction("Berangkat (Dispatch)", state.submitting) { viewModel.dispatch(job.id) {} }
                    job.status == DeliveryStatusKey.IN_TRANSIT && isMyDriverJob ->
                        DeliverAction(job, viewModel, state.submitting, state.driverChecklist, state.driverChecklistError)
                    else -> Text(
                        when (job.status) {
                            DeliveryStatusKey.PENDING_PDI -> "Tahap ini ditangani tim PDI cabang."
                            DeliveryStatusKey.PENDING_SPK -> "Tahap ini ditangani kasir cabang."
                            DeliveryStatusKey.PENDING_DELIVERY_NOTE, DeliveryStatusKey.PENDING_SCHEDULING -> "Tahap ini ditangani Delivery Control."
                            DeliveryStatusKey.ASSIGNED, DeliveryStatusKey.IN_TRANSIT -> "Tahap ini ditangani driver yang ditugaskan."
                            else -> "Tidak ada aksi pada tahap ini."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Batalkan (admin/DC, status non-terminal) — backend `cancel_job`.
                val cancellable = job.status != DeliveryStatusKey.DELIVERED && job.status != DeliveryStatusKey.CANCELLED
                if (cancellable && (viewModel.isAdminViewer || access.note)) {
                    Spacer(Modifier.height(10.dp))
                    CancelJobButton(job.id, viewModel, state.submitting)
                }
            }
        }
    }
}

/** Foto bukti job (dimuat ter-autentikasi via VM) — label per jenis. */
@Composable
private fun JobPhotosCard(photos: Map<String, Bitmap>) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Foto Bukti", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOf(
                "pdi" to "Foto PDI (unit siap kirim)",
                "delivery" to "Foto serah terima",
                "cash" to "Foto terima uang",
            ).forEach { (key, label) ->
                photos[key]?.let { bmp ->
                    Spacer(Modifier.height(10.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

/** Tombol Batalkan + dialog alasan (admin/delivery-control, non-terminal). */
@Composable
private fun CancelJobButton(id: String, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var show by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    OutlinedButton(onClick = { show = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
        Text("Batalkan Pengiriman", color = MaterialTheme.colorScheme.error)
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("Batalkan pengiriman?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Unit keluar dari pipeline (tidak bisa di-undo).", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    ExpressiveTextField(reason, { reason = it }, label = "Alasan", modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { show = false; vm.cancel(id, reason) {} }) { Text("Batalkan", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Kembali") } }
        )
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
private fun PdiAction(
    id: String, vm: DeliveryFlowViewModel, submitting: Boolean,
    checklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>,
    requiresAki: Boolean, akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>
) {
    var serial by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/pdi_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val photoState by vm.state.collectAsState()
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.onPdiPhotoCaptured(file) }

    // Hasil checklist per item.id: hasil (ok/tidak/na) default "ok" + catatan.
    val hasil = remember(checklist) { mutableStateMapOf<String, String>().apply { checklist.forEach { put(it.id, "ok") } } }
    val catatan = remember(checklist) { mutableStateMapOf<String, String>() }

    photoState.pdiPhoto?.takeIf { !photoState.pdiPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakePdiPhoto() }, onConfirm = { vm.confirmPdiPhoto() })
    }

    Text("PDI / Inspeksi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    ExpressiveTextField(
        serial, { serial = it }, label = "Nomor serial (opsional)", modifier = Modifier.fillMaxWidth(),
        trailingIcon = { BarcodeScanButton { serial = it } }
    )
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
    GpsStatusRow(photoState) { vm.refreshGps() }
    Spacer(Modifier.height(8.dp))
    PhotoBox(photoState.pdiPhoto, "Foto unit siap (opsional)") { cam.launch(uri) }

    // Form REJECTED dikecualikan (paritas gate backend pasca-093): semua form
    // rejected = wajib buat form BARU — form create dirender lagi (dulu
    // `akiForms.isEmpty()` → sekali ditolak, PDI tak bisa bikin form baru dari
    // mobile sama sekali, dead-end; temuan review 2026-07-23).
    val activeAkiForms = akiForms.filter { it.approvalStatus != "rejected" }
    val akiPending = requiresAki && activeAkiForms.isEmpty()
    if (requiresAki) {
        Spacer(Modifier.height(14.dp))
        if (akiPending) {
            var tujuan by remember { mutableStateOf("") }
            var tujuanLainnya by remember { mutableStateOf("") }
            // Merk: dropdown merk GS + "Lainnya…" (ketik manual). merkPilih = slug dropdown,
            // merkManual = teks bila pilih Lainnya. merkFinal = yang dikirim.
            var merkPilih by remember { mutableStateOf("") }
            var merkManual by remember { mutableStateOf("") }
            var kapasitas by remember { mutableStateOf("") }
            // Jumlah SET baterai (bukan pcs) — default 1 set, tiap set = 4 pcs (auto keterangan).
            var jumlahSet by remember { mutableStateOf("1") }
            var ambilCharger by remember { mutableStateOf(false) }
            var ambilSpion by remember { mutableStateOf(false) }
            var keteranganAki by remember { mutableStateOf("") }
            val merkFinal = if (merkPilih == AKI_MERK_LAINNYA) merkManual.trim() else merkPilih
            val setN = jumlahSet.toIntOrNull() ?: 0
            val jumlahKet = if (setN > 0) "$setN set = ${setN * AKI_PCS_PER_SET} pcs" else ""

            Text("Form Pengambilan Aki (wajib)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AkiTujuanDropdown(tujuan, { tujuan = it })
            if (tujuan == "lainnya") {
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(tujuanLainnya, { tujuanLainnya = it }, label = "Tujuan lainnya *", modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            AkiOptionDropdown(
                label = "Merk / Tipe *",
                options = AKI_MERK_OPTIONS,
                selected = merkPilih,
                allowLainnya = true,
                lainnyaSlug = AKI_MERK_LAINNYA,
                onSelect = { merkPilih = it },
            )
            if (merkPilih == AKI_MERK_LAINNYA) {
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(merkManual, { merkManual = it }, label = "Merk lainnya *", modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            AkiOptionDropdown(
                label = "Kapasitas (opsional)",
                options = AKI_KAPASITAS_OPTIONS,
                selected = kapasitas,
                allowLainnya = false,
                onSelect = { kapasitas = it },
            )
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(
                jumlahSet, { jumlahSet = it.filter { c -> c.isDigit() } },
                label = "Jumlah (set baterai)", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth()
            )
            if (jumlahKet.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(jumlahKet, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ambilCharger, onCheckedChange = { ambilCharger = it })
                Text("Ambil charger", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(16.dp))
                Checkbox(checked = ambilSpion, onCheckedChange = { ambilSpion = it })
                Text("Ambil kaca spion", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            ExpressiveTextField(keteranganAki, { keteranganAki = it }, label = "Keterangan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ExpressiveOutlinedButton(
                onClick = {
                    vm.createAkiForm(
                        id,
                        com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody(
                            tujuan = tujuan, merkTipe = merkFinal, jumlahPcs = setN,
                            tujuanLainnya = if (tujuan == "lainnya") tujuanLainnya.trim().ifBlank { null } else null,
                            kapasitas = kapasitas.trim().ifBlank { null },
                            jumlahKeterangan = jumlahKet.ifBlank { null },
                            keterangan = keteranganAki.trim().ifBlank { null },
                            ambilCharger = ambilCharger,
                            ambilKacaSpion = ambilSpion,
                        )
                    ) {}
                },
                enabled = !submitting && tujuan.isNotBlank() && (tujuan != "lainnya" || tujuanLainnya.trim().isNotEmpty()) &&
                    merkFinal.isNotEmpty() && setN > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                else Text("Simpan Form Aki")
            }
        } else if (activeAkiForms.all { it.approvalStatus == "approved" }) {
            Text("Form aki disetujui ✓ (${activeAkiForms.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF12B76A))
        } else {
            Text(
                "Form aki menunggu persetujuan (kepala cabang, admin penjualan, kasir) — PDI belum bisa disimpan sampai lengkap.",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFB5670C)
            )
        }
        // Info form yang DITOLAK (beda dari "menunggu" — teks lama menyesatkan).
        akiForms.count { it.approvalStatus == "rejected" }.takeIf { it > 0 }?.let { n ->
            Spacer(Modifier.height(4.dp))
            Text(
                "$n form aki ditolak — lihat alasan di menu Pengambilan Aki.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
            )
        }
    }
    Spacer(Modifier.height(14.dp))

    // Backend meng-gate PDI sampai >=1 form non-rejected & SEMUANYA disetujui
    // lengkap (3 slot) — cek approvalStatus supaya tombol tak "sukses lalu
    // ditolak backend". Form rejected diabaikan (paritas 093).
    val akiApproved = activeAkiForms.isNotEmpty() && activeAkiForms.all { it.approvalStatus == "approved" }
    val missingCatatan = checklist.any { hasil[it.id] == "tidak" && catatan[it.id].orEmpty().isBlank() }
    ExpressiveFilledButton(
        onClick = {
            val bodies = checklist.map { com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody(item = it.itemLabel, hasil = hasil[it.id] ?: "ok", catatan = catatan[it.id]?.trim()?.ifBlank { null }) }
            vm.submitPdi(id, serial, engine, bodies) {}
        },
        enabled = !submitting && !missingCatatan && (!requiresAki || akiApproved),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting && !akiPending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(
            when {
                missingCatatan -> "Isi catatan item 'Tidak'"
                akiPending -> "Isi form aki dulu"
                requiresAki && !akiApproved -> "Tunggu approval form aki"
                else -> "Simpan PDI"
            }
        )
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
    // Filter driver SE-REGION (paritas web `driversForRegion` 2026-07-21): job
    // Jawa hanya driver Jawa, Manado (D-06/D-07) hanya driver Manado. Region
    // driver dibaca dari `cabang_name` /api/users ("...Manado..." → Manado);
    // kosong = tak diketahui → fail-soft ikut tampil (pola web saat store gagal).
    val jobRegion = BranchRegions.dealerRegion(job.kodeDealer)
    val regionDrivers = drivers.filter { d ->
        val r = when {
            d.cabangName.isBlank() -> null
            d.cabangName.contains("manado", ignoreCase = true) -> BranchRegions.REGION_MANADO
            else -> BranchRegions.REGION_JAWA
        }
        r == null || r == jobRegion
    }
    if (regionDrivers.isNotEmpty()) {
        Text(
            "Pilih driver (region ${BranchRegions.regionLabel(jobRegion)})",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            regionDrivers.forEach { d ->
                val sel = driverId == d.effectiveId
                Surface(onClick = { driverId = d.effectiveId; driverName = d.name }, shape = RoundedCornerShape(12.dp),
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Text(d.name.ifBlank { d.effectiveId }, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        // Fallback bila daftar driver tak bisa dimuat (role tak berizin / endpoint
        // kosong) ATAU tak ada driver se-region — input manual = escape hatch
        // (enforce region cuma di klien, backend tak menolak lintas region).
        if (drivers.isNotEmpty()) {
            Text(
                "Tidak ada driver terdaftar di region ${BranchRegions.regionLabel(jobRegion)} — isi manual bila memang perlu lintas region.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(6.dp))
        }
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

/** Parse timestamp backend `YYYY-MM-DDTHH:MM:SS` (naive UTC) → epoch millis.
 *  minSdk 24 tanpa desugaring — SimpleDateFormat, bukan java.time (pola AssignAction). */
private fun parseUtcMillis(ts: String?): Long? = ts?.let {
    runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(it)?.time
    }.getOrNull()
}

@Composable
private fun DeliverAction(
    job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean,
    driverChecklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>,
    checklistError: String?
) {
    val id = job.id
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/deliver_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val photoState by vm.state.collectAsState()
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.onDeliverPhotoCaptured(file) }
    // 088: foto bukti terima uang (wajib bila job.driverTerimaUang == true)
    val needCash = job.driverTerimaUang == true
    val cashFile = remember { File(context.cacheDir, "delivery/cash_$id.jpg").apply { parentFile?.mkdirs() } }
    val cashUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cashFile) }
    val cashCam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.onCashPhotoCaptured(cashFile) }
    // 088: checklist serah-terima stage=driver (fail-open bila kosong)
    val hasil = remember(driverChecklist) { mutableStateMapOf<String, String>().apply { driverChecklist.forEach { put(it.id, "ok") } } }
    val catatan = remember(driverChecklist) { mutableStateMapOf<String, String>() }

    photoState.deliverPhoto?.takeIf { !photoState.deliverPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeDeliverPhoto() }, onConfirm = { vm.confirmDeliverPhoto() })
    }
    photoState.cashPhoto?.takeIf { !photoState.cashPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeCashPhoto() }, onConfirm = { vm.confirmCashPhoto() })
    }

    Text("Serah Terima", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    GpsStatusRow(photoState) { vm.refreshGps() }
    Spacer(Modifier.height(8.dp))
    PhotoBox(photoState.deliverPhoto, "Foto serah terima (wajib)") { cam.launch(uri) }
    if (needCash) {
        Spacer(Modifier.height(10.dp))
        PhotoBox(photoState.cashPhoto, "Foto serah terima uang (wajib${job.driverTerimaNominal?.let { " · ${rupiah(it)}" } ?: ""})") { cashCam.launch(cashUri) }
    }
    // FAIL-HARD checklist (088): gagal fetch → blok submit + retry. Tanpa ini
    // checklist null terkirim → 400 backend tanpa petunjuk (temuan audit).
    if (checklistError != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Gagal memuat checklist serah terima: $checklistError",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(6.dp))
        ExpressiveOutlinedButton(onClick = { vm.loadDriverChecklist(job) }, modifier = Modifier.fillMaxWidth()) {
            Text("Muat Ulang Checklist")
        }
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
    // 088: gate chat H-1 — PARITAS backend `deliver_job` (wajib chat ≥1 jam
    // sebelum serah terima; admin bypass). Gate klien AKTIF hanya bila
    // kill-switch server ON (context.driverGateEnabled — review 2026-07-23:
    // hard-block sepihak saat prod OFF memaksa driver menunggu 60 mnt utk
    // syarat yang server tidak menegakkan). Server OFF / backend lama tanpa
    // field → warning pembiasaan saja, tombol tetap aktif.
    val gate088 = job.driverTerimaUang != null // penanda backend 088 aktif
    val serverGateOn = photoState.deliveryContext?.driverGateEnabled == true
    val chatMillis = parseUtcMillis(job.consumerChatAt)
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(chatMillis) {
        while (true) { nowMillis = System.currentTimeMillis(); delay(30_000) }
    }
    val chatWaitLeftMin: Long? = if (!gate088 || chatMillis == null) null else {
        val elapsedMin = (nowMillis - chatMillis) / 60_000
        // coerceIn: jam device mundur (skew) jangan menampilkan ">60 menit".
        if (elapsedMin >= 60) null else (60 - elapsedMin).coerceIn(1, 60)
    }
    val chatBlocked = serverGateOn && !vm.isAdminViewer && gate088 &&
        (job.consumerChatAt == null || chatWaitLeftMin != null)
    // Teks status chat H-1 — hanya utk non-admin (admin di-bypass server, pesan
    // bergaya blocking di atas tombol aktif = menyesatkan).
    if (!vm.isAdminViewer && gate088 && job.consumerChatAt == null) {
        Spacer(Modifier.height(8.dp))
        if (serverGateOn) {
            Text("Belum chat konsumen — tandai chat dulu (wajib ≥1 jam sebelum serah terima).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        } else {
            Text("Belum chat konsumen — biasakan tandai chat H-1 (aturan wajib segera diberlakukan).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (!vm.isAdminViewer && serverGateOn && chatWaitLeftMin != null) {
        Spacer(Modifier.height(8.dp))
        Text("Chat konsumen tercatat — tunggu ±$chatWaitLeftMin menit lagi (syarat minimal 1 jam).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(14.dp))
    val missingCatatan = driverChecklist.any { hasil[it.id] == "tidak" && catatan[it.id].orEmpty().isBlank() }
    val hasPhoto = photoState.deliverPhoto != null && photoState.deliverPhotoConfirmed
    val hasCashPhoto = photoState.cashPhoto != null && photoState.cashPhotoConfirmed
    // Checklist fail-hard hanya saat gate server ON (server OFF menerima
    // checklist null — jangan kunci seluruh serah terima gara-gara fetch gagal).
    val checklistBlocked = serverGateOn && checklistError != null
    val canDeliver = hasPhoto && (!needCash || hasCashPhoto) && !missingCatatan &&
        !chatBlocked && !checklistBlocked
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
            checklistBlocked -> "Muat ulang checklist dulu"
            chatBlocked && job.consumerChatAt == null -> "Tandai chat konsumen dulu"
            chatBlocked -> "Tunggu ±$chatWaitLeftMin mnt (chat H-1)"
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

/** Status GPS detail (pola sama kartu status di AttendanceScreen) — dipakai di atas [PhotoBox] pada
 *  PDI/serah-terima supaya user tahu lokasi sudah terkunci (+akurasi) SEBELUM jepret, bukan baru
 *  ketauan gagal setelah lihat watermark. */
@Composable
private fun GpsStatusRow(state: DeliveryFlowUiState, onRetry: () -> Unit) {
    val context = LocalContext.current

    // Setelah user diarahkan ke Pengaturan izin & kembali (ON_RESUME), coba lagi otomatis — tanpa
    // ini "Buka Pengaturan" jadi jalan buntu: user balik ke app tapi kartu masih nampilkan status
    // ditolak yang lama sampai keluar-masuk layar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.gpsDenied) onRetry()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val label: String
    val detail: String
    val fg: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when {
        state.gpsDenied -> {
            label = "Izin lokasi ditolak"
            detail = "Aktifkan izin lokasi untuk HP ini di Pengaturan, lalu tekan Perbarui."
            fg = Color(0xFFF04438); icon = Icons.Rounded.LocationOff
        }
        state.gpsLocating -> {
            label = "Mendeteksi lokasi…"
            detail = "Mohon tunggu, GPS sedang mencari sinyal."
            fg = MaterialTheme.colorScheme.onSurfaceVariant; icon = Icons.Rounded.MyLocation
        }
        state.gpsError != null -> {
            label = "Gagal ambil lokasi"
            detail = state.gpsError
            fg = Color(0xFFB5670C); icon = Icons.Rounded.LocationOff
        }
        state.gpsLat != null && state.gpsLng != null -> {
            label = "Lokasi terkunci" + (state.gpsAccuracyM?.let { " · akurasi ±${it.toInt()}m" } ?: "")
            // Alamat terbaca (kota/kabupaten/tempat) diutamakan — angka lat/lng cuma fallback
            // selagi geocode masih jalan atau gagal (offline dsb.), bukan tampilan utama.
            detail = when {
                state.gpsAddress != null -> state.gpsAddress
                state.gpsAddressLoading -> "Mencari nama lokasi…"
                else -> "Lat %.6f, Lng %.6f".format(state.gpsLat, state.gpsLng)
            }
            fg = Color(0xFF12B76A); icon = Icons.Rounded.MyLocation
        }
        else -> {
            label = "Lokasi belum diambil"
            detail = "Foto akan diberi watermark tanpa koordinat."
            fg = MaterialTheme.colorScheme.onSurfaceVariant; icon = Icons.Rounded.LocationOff
        }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = fg.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.gpsLocating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
            else Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!state.gpsLocating) {
                if (state.gpsDenied) {
                    // Sekali user pilih "jangan tanya lagi", sistem tak pernah munculkan dialog izin
                    // lagi — satu-satunya jalan keluar adalah halaman Pengaturan izin app ini.
                    TextButton(onClick = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        )
                    }) { Text("Buka Pengaturan") }
                } else {
                    TextButton(onClick = onRetry) { Text("Perbarui") }
                }
            }
        }
    }
}

@Composable
private fun PhotoBox(bitmap: Bitmap?, label: String, onCapture: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Surface(onClick = onCapture, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().height(170.dp)) {
        if (bitmap != null) {
            // Pola sama AttendanceScreen: render Bitmap hasil watermark LANGSUNG dari state, bukan
            // baca-ulang file lewat Coil — tak ada cache untuk stale, tak ada race timing capture.
            // alignment=BottomCenter (bukan default Center): watermark digambar di bar PALING BAWAH
            // gambar asli (lihat PhotoWatermark.drawWatermark) — foto portrait di-crop ke kotak
            // pendek-lebar ini akan kehilangan tepi atas+bawah kalau alignment default Center dipakai,
            // memotong habis bar watermark. BottomCenter memotong dari ATAS saja, bar selalu utuh.
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Foto",
                contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp)); Text("Ketuk untuk ambil foto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Review pasca-jepret full-screen: kamera sistem (bukan kamera dalam-app) tidak bisa ditempeli
 * overlay saat live — jadi konfirmasi "gambarnya sudah benar" (watermark kebaca dsb.) dilakukan DI
 * SINI, langsung setelah jepret, sebelum foto dianggap final. `ContentScale.Fit` (bukan Crop seperti
 * [PhotoBox]) sengaja dipakai supaya seluruh gambar + bar watermark kelihatan utuh tanpa terpotong.
 */
@Composable
private fun PhotoReviewDialog(bitmap: Bitmap, onRetake: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onRetake, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Cek hasil foto — pastikan watermark jam & lokasi terbaca",
                    color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                Image(
                    bitmap = bitmap.asImageBitmap(), contentDescription = "Pratinjau foto",
                    contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveOutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Ambil Ulang") }
                    ExpressiveFilledButton(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Pakai Foto Ini") }
                }
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
        (nik.isEmpty() || nik.length == 16) &&
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
                    // NIK KTP = 16 digit; backend menolak <16 digit (delivery.rs
                    // "NIK konsumen minimal 16 digit angka") — filter + gate di sini
                    // supaya tak mentok 400 saat submit.
                    ExpressiveTextField(
                        nik, { nik = it.filter(Char::isDigit).take(16) }, label = "NIK",
                        keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth(),
                        isError = nik.isNotEmpty() && nik.length < 16,
                        supportingText = if (nik.isNotEmpty() && nik.length < 16) "NIK harus 16 digit angka (${nik.length}/16)" else null
                    )
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
                                onUpdate = { updated ->
                                    // Maks 1 kartu expand — expand kartu ini = collapse lainnya
                                    // (state pencarian broker dibagi bersama; cegah bocor antar kartu).
                                    val collapseOthers = updated.expanded && !item.expanded
                                    items = items.mapIndexed { i, o ->
                                        if (i == idx) updated else if (collapseOthers) o.copy(expanded = false) else o
                                    }
                                },
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

/** Dropdown tujuan pengambilan aki — slug enum backend (pola CabangSelector/ItemFincoyDropdown). */
@Composable
private fun AkiTujuanDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Tujuan *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                    text = akiTujuanLabel(selected).let { if (selected.isBlank()) "Pilih tujuan…" else it },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AKI_TUJUAN_OPTIONS.forEach { (slug, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(slug); expanded = false })
                }
            }
        }
    }
}

/** Dropdown opsi form aki (merk/kapasitas) — daftar tetap + opsional "Lainnya…" (ketik manual,
 *  di-render terpisah oleh pemanggil). Pola visual sama [AkiTujuanDropdown]. */
@Composable
private fun AkiOptionDropdown(
    label: String,
    options: List<String>,
    selected: String,
    allowLainnya: Boolean,
    onSelect: (String) -> Unit,
    lainnyaSlug: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    val display = when {
        selected.isBlank() -> "Pilih…"
        allowLainnya && selected == lainnyaSlug -> "Lainnya…"
        else -> selected
    }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                    text = display,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
                }
                if (allowLainnya) {
                    DropdownMenuItem(text = { Text("Lainnya…") }, onClick = { onSelect(lainnyaSlug); expanded = false })
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
