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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material.icons.rounded.Cancel
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
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.krisoft.tridjayaelektronik.data.model.formatWaktuId
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.KontributorDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import com.krisoft.tridjayaelektronik.data.model.parseTimestampMillis
import com.krisoft.tridjayaelektronik.ui.home.formatRupiahShort
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.MoneyTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Meta status ──────────────────────────────────────────────────────────────

private fun statusMeta(status: String): Pair<String, Color> = when (status) {
    DeliveryStatusKey.PENDING_DISCOUNT -> "Tunggu Diskon" to Color(0xFFB5670C)
    DeliveryStatusKey.PENDING_PDI -> "Antri PDI" to Color(0xFF6941C6)
    DeliveryStatusKey.PENDING_PERBAIKAN -> "Ditahan — Perbaikan" to Color(0xFFF04438)
    DeliveryStatusKey.PENDING_SPK -> "Antri Kasir" to Color(0xFF0086C9)
    DeliveryStatusKey.PENDING_DELIVERY_NOTE -> "Surat Jalan" to Color(0xFF0E9384)
    DeliveryStatusKey.PENDING_SCHEDULING -> "Penjadwalan" to Color(0xFF0E9384)
    DeliveryStatusKey.ASSIGNED -> "Siap Berangkat" to Color(0xFF1565C0)
    DeliveryStatusKey.IN_TRANSIT -> "Dalam Perjalanan" to Color(0xFF1E63E9)
    DeliveryStatusKey.DELIVERED -> "Terkirim" to Color(0xFF12B76A)
    DeliveryStatusKey.CANCELLED -> "Batal" to Color(0xFFF04438)
    else -> status to Color(0xFF667085)
}

// ── Klaim PDI (111) ──────────────────────────────────────────────────────────

/** Keadaan klaim PDI dari sudut pandang SATU penonton. */
internal enum class PdiClaimView {
    /** Server belum kenal klaim (atau konteks gagal dimuat) — alur PDI lama persis. */
    TAK_DIDUKUNG,
    BELUM_DIKLAIM,
    MILIK_SAYA,
    MILIK_ORANG_LAIN,
}

/**
 * Aturan tampilan klaim PDI — fungsi MURNI supaya empat keadaannya bisa diuji
 * tanpa Compose/jaringan.
 *
 * `pdiClaimedBy` kosong punya DUA arti dan bedanya penting: server yang sudah
 * kenal fitur ini selalu mengirim `pdiClaimTtlHours` di `/delivery/context`,
 * jadi ketiadaan TTL berarti "jangan tawarkan apa pun" — bukan "belum
 * diklaim". Tanpa pembedaan itu, APK ini akan menawarkan "Ambil PDI" ke server
 * lama yang pasti menjawab 404/405, atau (lebih buruk) ke server yang
 * konteksnya sedang gagal dimuat. Klaim SENGAJA opsional di server, jadi
 * keadaan [TAK_DIDUKUNG] tak pernah boleh memblokir apa pun.
 *
 * @param serverSupportsClaim `pdiClaimTtlHours != null`. Hanya membedakan
 *   [BELUM_DIKLAIM] vs [TAK_DIDUKUNG] — daftar antrian yang cuma menampilkan
 *   label (tak menawarkan tombol ambil) boleh membiarkannya `false`.
 */
internal fun pdiClaimView(
    pdiClaimedBy: String?,
    currentUserId: String,
    serverSupportsClaim: Boolean = false,
): PdiClaimView = when {
    pdiClaimedBy.isNullOrBlank() -> if (serverSupportsClaim) PdiClaimView.BELUM_DIKLAIM else PdiClaimView.TAK_DIDUKUNG
    pdiClaimedBy == currentUserId && currentUserId.isNotBlank() -> PdiClaimView.MILIK_SAYA
    else -> PdiClaimView.MILIK_ORANG_LAIN
}

/** Label klaim (kartu antrian & detail); `null` = tak ada klaim, tak ada label. */
internal fun pdiClaimLabel(view: PdiClaimView, claimedByName: String?): String? = when (view) {
    PdiClaimView.MILIK_SAYA -> "Kamu sedang memproses"
    // Nama BISA kosong (job lama / nama aktor tak terekam) — jangan menampilkan
    // "Diproses oleh " menggantung.
    PdiClaimView.MILIK_ORANG_LAIN -> "Diproses oleh ${claimedByName?.trim()?.ifBlank { null } ?: "petugas lain"}"
    else -> null
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

// ── Antrian per-tahap ────────────────────────────────────────────────────────

@Composable
fun DeliveryQueueScreen(
    title: String,
    status: String?,
    view: String? = null,
    reorderable: Boolean = false,
    /** Sales antar sendiri (2026-07-24): treat aktor sales sbg driver (job self-delivery
     *  miliknya sendiri) — dikirim layar "Tugas Antar", driver asli tak terpengaruh. */
    asDriver: Boolean = false,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: DeliveryFlowViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(status, view) { viewModel.loadQueue(status, view, asDriver) }
    val muatUlang = { viewModel.loadQueue(status, view, asDriver) }

    // ── Aksi level-SPK (2026-08-06) ──────────────────────────────────────────
    // Backend mem-FAN-OUT surat jalan, penugasan driver, konfirmasi kasir,
    // klaim PDI, dan PDI barang kecil ke SELURUH unit satu SPK. Antrian ini
    // dulu murni daftar unit, jadi petugas menekan tombol yang sama N kali
    // untuk pekerjaan yang server sudah selesaikan pada tekanan PERTAMA —
    // panggilan ke-2 dst dijawab 400 "sudah tidak di tahap ini" dan terbaca
    // sebagai kegagalan. Sekarang unit dikelompokkan per SPK dan tombolnya
    // hidup di kepala grup.
    val groups = remember(state.items) { groupJobsBySpk(state.items) }

    val terbitkanLangsung = status == DeliveryStatusKey.PENDING_DELIVERY_NOTE && viewModel.access.note

    var terbitkanGrup by remember { mutableStateOf<SpkBatchGroup?>(null) }
    terbitkanGrup?.let { grup ->
        TerbitkanSuratJalanDialog(
            grup = grup,
            submitting = state.submitting,
            onDismiss = { terbitkanGrup = null },
            onSubmit = { cabang ->
                // Anchor = unit pertama grup; server menyeret sisanya.
                viewModel.issueDeliveryNote(grup.jobs.first().id, cabang) {
                    terbitkanGrup = null
                    muatUlang()
                }
            },
        )
    }


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
                    // SELURUH antrian kini SATU KARTU PER SPK (permintaan user
                    // 2026-08-06): PDI, kasir, surat jalan, penjadwalan, driver,
                    // konfirmasi pembayaran, riwayat. Alasannya sama di semua
                    // tahap - satu SPK adalah satu penjualan, satu konsumen,
                    // satu alamat; memajangnya sebagai N baris membuat petugas
                    // mengira ada N pekerjaan, dan sejak server mem-fan-out-kan
                    // hampir semua endpoint tahap, N-1 di antaranya memang
                    // pekerjaan hantu. Rincian per unit hidup di layar detail,
                    // yang kini memuat seluruh unit SPK.
                    if (reorderable) {
                        // Manifest driver: kartu SPK digeser sebagai SATU BLOK.
                        // Kontrak server tak berubah (tetap daftar id unit) -
                        // lihat `moveLoadSpk`, yang meratakan grup jadi urutan
                        // id. Justru inilah yang menjamin unit satu SPK selalu
                        // berdampingan; penggeseran per unit yang lama tidak.
                        itemsIndexed(groups, key = { _, g -> g.kode }) { index, grup ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SpkRingkasCard(grup, viewModel.currentUserId) { onOpen(grup.jobs.first().id) }
                                }
                                Column {
                                    IconButton(onClick = { viewModel.moveLoadSpk(grup.kode, up = true) }, enabled = index > 0) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Naikkan urutan")
                                    }
                                    IconButton(onClick = { viewModel.moveLoadSpk(grup.kode, up = false) }, enabled = index < groups.size - 1) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Turunkan urutan")
                                    }
                                }
                            }
                        }
                    } else {
                        items(groups, key = { it.kode }) { grup ->
                            SpkRingkasCard(
                                grup = grup,
                                currentUserId = viewModel.currentUserId,
                                // Tombol tahap jadi KAKI kartu, bukan tombol
                                // mengambang di bawahnya. Tahap tanpa aksi
                                // level-SPK tak mengirim slot ini sama sekali.
                                // HANYA surat jalan yang menaruh tombol di
                                // kartu. Antrian PDI mengikuti bentuk antrian
                                // kasir (permintaan user 2026-08-06): kartu
                                // polos, ketuk untuk masuk detail, dan seluruh
                                // tombolnya - Ambil PDI, PDI massal barang
                                // kecil, formulir per unit - hidup di sana.
                                aksi = if (terbitkanLangsung) {
                                    {
                                        ExpressiveFilledButton(
                                            onClick = { terbitkanGrup = grup },
                                            enabled = !state.submitting,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Terbitkan Surat Jalan") }
                                    }
                                } else null,
                            ) { onOpen(grup.jobs.first().id) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SATU kartu untuk satu SPK — dipakai antrian yang pekerjaannya memang per SPK
 * (kasir). Ketuk = buka detail SPK-nya lewat unit pertama; rincian tiap unit
 * dan tombol tahapnya hidup di sana.
 *
 * Kartu ini mewakili satu
 * PENJUALAN. Kasir menyalinnya ke GS sebagai satu transaksi satu nomor, jadi
 * menampilkan N baris untuk satu penjualan membuatnya mengira ada N pekerjaan
 * — persis keluhan yang memicu fan-out di server.
 */
@Composable
private fun SpkRingkasCard(
    grup: SpkBatchGroup,
    currentUserId: String = "",
    /**
     * Tombol tahap, dirender DI DALAM kartu sebagai kaki. Sebelumnya ia
     * mengambang di bawah kartu, sehingga tiap baris antrian terbaca sebagai
     * dua benda — kartu, lalu tombol yatim yang tak jelas milik SPK yang mana.
     */
    aksi: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val anchor = grup.jobs.first()
    val n = grup.jobs.size
    ClayCard(modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth()) {
        // `clickable` DI BARIS ISI, bukan di seluruh kartu: kalau kartunya yang
        // diberi klik, menekan tombol di kaki ikut membuka detail — dua aksi
        // untuk satu ketukan.
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        grup.kode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // Satu chip HANYA kalau seluruh barang memang sestatus.
                    // Kalau tidak, chip anchor itu bohong — dan menggantinya
                    // dengan peringatan "status beda tiap barang" cuma
                    // memindahkan kebohongan jadi teka-teki: orang tetap tak
                    // tahu bedanya apa tanpa membuka SPK-nya. Yang dipajang
                    // sekarang komposisinya sendiri (mis. "Terkirim 2",
                    // "Antri PDI 1"), jadi jawabannya ada di kartu.
                    if (grup.jobs.map { it.status }.distinct().size <= 1) {
                        StatusChip(anchor.status)
                    }
                }
                val perStatus = grup.jobs.groupingBy { it.status }.eachCount()
                if (perStatus.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // `Row` biasa, bukan `FlowRow`: satu SPK praktis tak pernah
                    // punya lebih dari 2-3 status sekaligus, jadi tak perlu
                    // menarik API eksperimental hanya untuk pembungkusan yang
                    // takkan terjadi.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Urut mengikuti kemunculan di daftar, bukan abjad:
                        // daftar sudah diurut server (terbaru dulu).
                        perStatus.forEach { (status, jumlah) ->
                            val (label, warna) = statusMeta(status)
                            Surface(color = warna.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                                Text(
                                    "$label $jumlah", color = warna,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    anchor.customerName ?: "-", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Semua barangnya disebut, bukan cuma yang pertama + "dst":
                // kasir memakai daftar ini untuk mencocokkan dengan penjualan
                // yang sedang dia ketik di GS.
                grup.jobs.forEach { j ->
                    Text(
                        "• ${j.namaBarang ?: j.kodeBarang ?: "-"}${j.tipe?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                        Text(
                            if (n > 1) "$n unit · 1 transaksi GS" else "1 unit",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    // Penanda per-unit yang dulu hidup di kartu per-unit ikut naik ke
                    // kartu SPK — sejak antrian tak lagi memajang kartu per
                    // unit, tanpa ini informasinya HILANG, bukan cuma pindah.
                    // Dinilai atas SELURUH grup (`any`), karena satu barang
                    // ber-COD sudah cukup membuat SPK-nya perlu diperlakukan
                    // sebagai COD.
                    if (grup.jobs.any { it.pdiRequired == false }) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "PDI Mandiri", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
                        )
                    }
                    when {
                        grup.jobs.any { it.deliveryMethod == "self_pickup" } -> {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Diambil Sendiri", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFF0E9384),
                            )
                        }
                        grup.jobs.any { it.deliveryMethod == "sales_delivery" } -> {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Sales Antar", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFF1565C0),
                            )
                        }
                    }
                    if (grup.jobs.any { it.driverTerimaUang == true }) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "COD", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF9E4B00),
                        )
                    }
                }
                // Klaim PDI dinilai se-SPK, sejalan dengan servernya: `claim-pdi`
                // fan-out mengunci SELURUH unit ke satu petugas. "Milik saya"
                // menang atas "milik orang lain" bila SPK terlanjur terbelah
                // (unit yang sudah dipegang orang lain memang DILEWATI server,
                // bukan direbut) — yang perlu diketahui petugas adalah bahwa dia
                // punya pekerjaan di sini, bukan bahwa ada yang tidak.
                val klaimSaya = currentUserId.isNotBlank() && grup.jobs.any { it.pdiClaimedBy == currentUserId }
                val klaimOrangLain = grup.jobs.firstOrNull {
                    !it.pdiClaimedBy.isNullOrBlank() && it.pdiClaimedBy != currentUserId
                }
                when {
                    klaimSaya -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Kamu sedang memproses", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF12B76A),
                        )
                    }
                    klaimOrangLain != null -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Diproses oleh ${klaimOrangLain.pdiClaimedByName?.trim()?.ifBlank { null } ?: "petugas lain"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        aksi?.let {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = it,
            )
        }
      }
    }
}

/**
 * Dialog terbit surat jalan dari daftar antrian — isian sama dengan aksi di
 * layar detail (`DeliveryNoteAction`), cuma dibungkus dialog supaya DC tak
 * perlu keluar-masuk detail satu per satu.
 *
 * Sejak 2026-08-05 endpoint-nya fan-out se-SPK: SATU nomor surat jalan untuk
 * seluruh unit, karena dokumen fisiknya memang satu lembar untuk satu
 * pengiriman.
 */
@Composable
private fun TerbitkanSuratJalanDialog(
    grup: SpkBatchGroup,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val anchor = grup.jobs.first()
    var cabang by remember(grup.kode) { mutableStateOf(anchor.kodeDealer.orEmpty()) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Terbitkan Surat Jalan") },
        text = {
            Column {
                Text("SPK ${grup.kode}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                grup.jobs.forEach { j ->
                    Text(
                        "• ${j.namaBarang ?: j.kodeBarang ?: "-"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (grup.jobs.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Satu nomor surat jalan untuk ${grup.jobs.size} unit — satu pengiriman, satu lembar.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(cabang, { cabang = it }, label = "Cabang sumber unit (wajib)", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(cabang) }, enabled = !submitting && cabang.trim().isNotEmpty()) {
                Text(if (submitting) "Menerbitkan…" else "Terbitkan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Batal") } },
    )
}

/**
 * Daftar barang satu SPK, dirender DI DALAM kartu identitas SPK pada layar
 * detail (bukan di area aksi).
 *
 * SENGAJA TIDAK menampilkan `kodePengiriman` per unit maupun penanda "unit mana
 * yang diketuk dari antrian". Keduanya sempat ada dan dibuang atas masukan user
 * (2026-08-06), dengan alasan yang berlaku seterusnya:
 * - Kode unit dalam satu SPK berawalan SAMA (`DLV-Mxxxxxxxx-`), yang beda cuma
 *   akhiran `-1u1`/`-2u1`. Memajangnya per baris = mengulang kode SPK yang
 *   sudah tertulis di kepala kartu, N kali.
 * - "Lewat unit mana layar ini dibuka" adalah artefak navigasi (detail memuat
 *   satu id), bukan informasi yang dipakai kasir. Yang dia lihat SPK-nya.
 */
@Composable
private fun SpkUnitList(units: List<DeliveryJobDto>) {
    units.forEachIndexed { i, u ->
        Spacer(Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    // Nomor urut, bukan kode unit: menjawab "barang ke berapa"
                    // tanpa mengulang kode SPK yang sudah ada di kepala kartu.
                    Text(
                        "${i + 1}.", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${u.namaBarang ?: u.kodeBarang ?: "-"}${u.tipe?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        // Merk/warna, serial, dan No PO adalah milik UNIT, bukan
                        // milik SPK — dulu dipajang sekali di kepala kartu dari
                        // unit yang kebetulan dibuka, sehingga SPK banyak barang
                        // memperlihatkan serial satu unit seolah berlaku semua.
                        listOfNotNull(
                            listOfNotNull(u.merk, u.warna).joinToString(" · ").ifBlank { null },
                            u.preOrderId?.takeIf { it.isNotBlank() }?.let { "PO $it" },
                        ).joinToString(" · ").ifBlank { null }?.let {
                            Text(
                                it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // SN punya BARIS SENDIRI, bukan digabung ke baris
                        // merk/warna/PO. Alasannya bukan estetika: baris gabungan
                        // itu ber-`maxLines = 2` di kolom sempit, jadi merk/warna
                        // yang panjang MEMOTONG SN-nya lewat ellipsis — nomor
                        // yang justru dicocokkan dengan unit fisik hilang tanpa
                        // jejak. Selalu dirender (nilai atau "—") supaya "unit ini
                        // belum ber-SN" tak lagi terlihat sama dengan "SN-nya
                        // kepotong". SN memang OPSIONAL sejak 2026-07-23 —
                        // kosong bukan kesalahan, karena itu netral, bukan merah.
                        Text(
                            "SN " + (u.serialNumber?.takeIf { it.isNotBlank() } ?: "—"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (u.serialNumber.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (u.driverTerimaUang == true) {
                            Text(
                                "COD ${if (u.codPaymentMode == "dp") "DP" else "Full"} · tagih " +
                                    (u.driverTerimaNominal?.let { rupiah(it) } ?: "-"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFF9E4B00),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        u.hargaOtr?.let {
                            Text(rupiah(it), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        u.diskon?.takeIf { it > 0 }?.let {
                            Text(
                                "−${rupiah(it)}", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFFB5670C),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Baris kelengkapan (baterai/charger/kaca spion) di daftar barang SPK.
 *
 * Dibedakan dari baris unit lewat penanda "Kelengkapan" dan tanpa harga:
 * barang-barang ini tidak punya harga OTR sendiri - nilainya sudah termasuk
 * di unit yang menaunginya. Menampilkan kolom harga kosong akan terbaca
 * sebagai data yang hilang, bukan sebagai barang yang memang tak berharga
 * sendiri.
 */
@Composable
private fun SpkKelengkapanList(items: List<KelengkapanUnit>, nomorMulai: Int) {
    items.forEachIndexed { i, k ->
        Spacer(Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    // Nomor MELANJUTKAN nomor unit (permintaan user 2026-08-06:
                    // "ditampilkan seperti unit"), bukan penanda "+" tersendiri.
                    // Bagi konsumen dan petugas, sepeda listrik + baterai +
                    // charger adalah tiga barang yang diserahkan — pembedaannya
                    // urusan internal server, bukan pemandangan mereka.
                    Text(
                        "${nomorMulai + i}.", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${k.label} x${k.qty}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        // Menempati slot yang SAMA dengan baris merk/warna/PO
                        // pada unit, jadi bentuk barisnya tetap sama persis.
                        Text(
                            listOfNotNull("Kelengkapan unit", k.catatan).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
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
    // Judul mengikuti TAHAP, bukan satu nama untuk semua. Di antrian kasir yang
    // sedang dilihat adalah SPK-nya (satu penjualan, satu transaksi GS, bisa
    // banyak barang) — menyebutnya "Detail Pengiriman" salah alamat: belum ada
    // pengiriman apa pun pada tahap itu, barangnya bahkan belum dijadwalkan.
    // Tahap sesudahnya tetap "Detail Pengiriman" karena di sanalah pengiriman
    // benar-benar jadi pokok bahasannya.
    val judul = if (job?.status == DeliveryStatusKey.PENDING_SPK) "Detail SPK" else "Detail Pengiriman"
    TridjayaCollapsibleHeader(title = judul, onBack = onBack) { contentModifier ->
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
                        // Urutan kartu (permintaan user 2026-08-06): KONSUMEN →
                        // BARANG → TOTAL → PEMBAYARAN → PENGIRIMAN, lalu cabang/
                        // sumber order sebagai ekor. Urutan lamanya campur
                        // (metode pengiriman & PDI nyempil di antara data
                        // konsumen; merk/serial/PO di kepala kartu padahal milik
                        // unit), sehingga SPK banyak barang memperlihatkan serial
                        // SATU unit seolah berlaku untuk semuanya.
                        //
                        // `unitSpk` = seluruh unit SPK bila termuat, kalau tidak
                        // unit yang dibuka saja. Jadi tata letaknya sama persis
                        // untuk SPK satu barang maupun banyak barang.
                        val unitSpk = state.batchUnits.ifEmpty { listOf(job) }

                        // ── 1. KONSUMEN ──────────────────────────────────────
                        Spacer(Modifier.height(6.dp))
                        Text(job.customerName ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        // Urutan: nama → HP → NIK → alamat (permintaan user
                        // 2026-08-06). Alamat sengaja PALING BAWAH di blok ini:
                        // ia satu-satunya nilai yang biasanya membungkus
                        // beberapa baris, jadi menaruhnya di tengah memutus
                        // barisan pendek yang enak dipindai di atasnya.
                        InfoLine("No. HP", job.customerPhone)
                        InfoLine("NIK", job.customerNik)
                        InfoLine("Alamat", job.customerAddress)
                        InfoLine("Sosmed", listOfNotNull(
                            job.sosmedTiktok?.let { "TikTok $it" },
                            job.sosmedFacebook?.let { "FB $it" },
                            job.sosmedInstagram?.let { "IG $it" },
                        ).joinToString(" · ").ifBlank { null })

                        // ── 2. LIST BARANG ───────────────────────────────────
                        // Baterai/charger/kaca spion yang IKUT diserahkan
                        // bersama unitnya, diturunkan dari form aki DISETUJUI
                        // (2026-08-06). Di server ia bukan baris
                        // `delivery_jobs` - sengaja, karena baris job berarti
                        // unit fisik ber-antrian PDI, penugasan driver, dan
                        // hitungan kiriman sendiri. Yang berubah cuma cara
                        // membacanya: di daftar barang ia berdiri sebagai
                        // barisnya sendiri, persis barang lain.
                        val kelengkapan = remember(state.batchAkiForms) {
                            kelengkapanDariAkiForms(state.batchAkiForms)
                        }
                        val totalItem = unitSpk.size + kelengkapan.size
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Barang ($totalItem)",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SpkUnitList(unitSpk)
                        SpkKelengkapanList(kelengkapan, nomorMulai = unitSpk.size + 1)

                        // ── 3. TOTAL UNIT ────────────────────────────────────
                        // Dijumlah dari unit yang termuat, BUKAN dari `job`
                        // sendirian: kolom harga di baris `delivery_jobs` itu
                        // per unit, jadi menampilkan angka unit yang kebetulan
                        // dibuka sebagai "total SPK" akan mengecilkan nilai
                        // penjualan tanpa terlihat salah.
                        val totalOtr = unitSpk.mapNotNull { it.hargaOtr }.sum()
                        val totalDiskon = unitSpk.mapNotNull { it.diskon }.sum()
                        val totalNilai = unitSpk.mapNotNull { it.hargaTotal }.sum()
                        Spacer(Modifier.height(10.dp))
                        Text("Total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        // Angka gabungan DULU (itu yang dicari: "SPK ini
                        // isinya berapa barang"), rinciannya menyusul. Unit
                        // fisik dan kelengkapan sengaja tetap dibedakan: yang
                        // pertama punya antrian PDI, driver, dan hitungan
                        // kiriman di server; yang kedua tidak. Menyatukannya
                        // jadi satu angka "unit" akan berselisih dengan
                        // statistik pengiriman tanpa ada yang tahu sebabnya.
                        InfoLine(
                            "Jumlah Barang",
                            if (kelengkapan.isEmpty()) "${unitSpk.size} unit"
                            else "$totalItem barang (${unitSpk.size} unit + ${kelengkapan.size} kelengkapan)",
                        )
                        InfoLine("Total OTR", totalOtr.takeIf { it > 0 }?.let { rupiah(it) })
                        InfoLine("Total Diskon", totalDiskon.takeIf { it > 0 }?.let { rupiah(it) })
                        // Angka yang dicari orang lebih dulu dari seluruh kartu.
                        InfoLine("Total Nilai", totalNilai.takeIf { it > 0 }?.let { rupiah(it) })

                        // ── 4. PEMBAYARAN ────────────────────────────────────
                        Spacer(Modifier.height(10.dp))
                        Text("Pembayaran", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        // Cash vs Credit mengubah seluruh cara membaca sisa
                        // kartu (ada/tidaknya fincoy, angsuran, tenor), jadi ia
                        // ditebalkan — bukan sekadar salah satu baris.
                        InfoLine("Metode Bayar", job.paymentType?.replaceFirstChar { it.uppercase() })
                        InfoLine("No. Transaksi GS", job.noTransaksi)
                        if (job.paymentType == "credit") {
                            InfoLine("Fincoy", job.fincoy)
                            InfoLine("DP Net", job.dpNet?.let { rupiah(it) })
                            InfoLine("Pembayaran 1", job.pembayaran1?.let { rupiah(it) })
                            InfoLine("Angsuran", job.angsuran?.let { rupiah(it) })
                            InfoLine("Tenor", job.tenor?.let { "$it bln" })
                        }
                        // COD (2026-07-25): uang diambil driver saat kirim — cuma ada
                        // kalau ada driver beneran (bukan diambil sendiri/sales antar sendiri).
                        if (job.driverTerimaUang == true) {
                            InfoLine("Metode COD", if (job.codPaymentMode == "dp") "DP" else "Full Payment")
                            if (job.codPaymentMode == "dp") {
                                InfoLine("DP Rencana (Sales)", job.codDpAmount?.let { rupiah(it) })
                                InfoLine("DP Diterima Kasir", job.kasirDpDiterima?.let { rupiah(it) })
                            }
                            InfoLine("Sisa Diambil Driver", job.driverTerimaNominal?.let { rupiah(it) })
                            InfoLine("Kasir Konfirmasi Bayar", if (job.kasirKonfirmasiPembayaran) "Sudah" else "Belum")
                            InfoLine("Setoran Driver→Kasir", job.setoranKasirNominal?.let { "${rupiah(it)} · ${job.setoranKasirByNama ?: "-"}" })
                        }

                        // ── 5. PENGIRIMAN ────────────────────────────────────
                        Spacer(Modifier.height(10.dp))
                        Text("Pengiriman", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        // Setara "Metode Bayar" di seksi sebelumnya: nilai yang
                        // menentukan sisa barisnya masuk akal atau tidak (job
                        // "diambil sendiri" tak pernah punya driver & jadwal).
                        InfoLine("Metode Pengiriman", when (job.deliveryMethod) {
                            "self_pickup" -> "Diambil Sendiri"
                            "sales_delivery" -> "Sales Antar Sendiri"
                            else -> "Driver"
                        })
                        InfoLine("PDI", if (job.pdiRequired == false) "PDI Mandiri (sales)" else "PDI (tim PDI)")
                        InfoLine("Surat Jalan", job.deliveryNoteNo)
                        InfoLine("Driver", job.assignedDriverName)
                        InfoLine("Jadwal", job.scheduledDate?.let(::formatWaktuId))
                        InfoLine("Chat Konsumen", job.consumerChatAt?.let(::formatWaktuId))
                        job.reviewRating?.let { InfoLine("Rating", "★".repeat(it) + (job.reviewComment?.let { c -> " · $c" } ?: "")) }
                        if (job.status == DeliveryStatusKey.CANCELLED) InfoLine("Alasan Batal", job.cancelReason)
                        job.customerMapUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            Spacer(Modifier.height(4.dp))
                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Buka Lokasi Maps") }
                        }

                        // ── Ekor: cabang & asal order ────────────────────────
                        // Di luar lima seksi yang diminta, tapi TIDAK dibuang:
                        // cabang stok menentukan siapa yang memegang barangnya,
                        // dan komisi KBK ikut dibayarkan dari SPK ini.
                        Spacer(Modifier.height(10.dp))
                        Text("Cabang & Asal Order", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        InfoLine("Cabang Stok", job.dealerName)
                        InfoLine("Cabang Asal Sales", job.salesDealerName)
                        InfoLine("Sales", job.salesName)
                        InfoLine("Sumber", when {
                            job.orderSource == "kbk" -> "KBK · ${job.kbkBrokerNama ?: job.kbkBrokerKode ?: "-"}"
                            job.orderSource != null -> "Sales"
                            else -> null
                        })
                        InfoLine("Komisi KBK", job.komisiKbk?.let { rupiah(it) })
                        InfoLine("No. HP KBK", job.noHpKbk)
                    }
                }
                Spacer(Modifier.height(14.dp))
                SpkTimelineCard(job, state.jobDiscounts, state.akiForms)
                // Foto bukti (PDI siap kirim / serah terima / terima uang) — dimuat
                // ter-autentikasi via VM (kasir/DC/driver bisa verifikasi dari HP).
                if (state.jobPhotos.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    JobPhotosCard(state.jobPhotos)
                }
                if (state.kontributor.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    KontributorCard(state.kontributor)
                }
                // Sunting isi SPK (2026-08-01) — administrator saja, dan hanya
                // selagi unitnya belum di-PDI + belum tercatat di GS. Kartunya
                // TIDAK dirender kalau syaratnya tak terpenuhi (bukan
                // dirender-lalu-dinonaktifkan): tombol mati yang servernya
                // jawab 400 cuma bikin orang menebak.
                if (bolehSuntingSpk(job, viewModel.isAdminViewer, viewModel.currentUserId)) {
                    Spacer(Modifier.height(14.dp))
                    EditSpkAction(job, viewModel, state.submitting)
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
                    (access.driverAction && job.assignedDriverId == viewModel.currentUserId)
                // Self-PDI: sales pemilik SPK boleh PDI unitnya sendiri — "bertanggung
                // jawab penuh". Syaratnya (mirror `submit_pdi` backend 2026-07-27):
                // toggle PDI Mandiri (`pdiRequired=false`) ATAU metode diambil-sendiri/
                // antar-sendiri. `pdiRequired=false` masuk sejak rute skip PDI dibuang —
                // dulu kombinasi itu tak pernah mampir ke `pending_pdi` sama sekali.
                // Backend otoritatif, ini murni gate UI (paritas web `PdiDetailPage`).
                val isSelfPdiJob = (job.pdiRequired == false ||
                    job.deliveryMethod == "self_pickup" || job.deliveryMethod == "sales_delivery") &&
                    !job.salesUserId.isNullOrBlank() && job.salesUserId == viewModel.currentUserId
                if (job.driverTerimaUang != null && isMyDriverJob &&
                    (job.status == DeliveryStatusKey.ASSIGNED || job.status == DeliveryStatusKey.IN_TRANSIT)
                ) {
                    ChatConsumerCard(job, viewModel, state.submitting)
                    Spacer(Modifier.height(14.dp))
                }
                when {
                    // `pending_perbaikan` ikut: unit tertahan HANYA bisa keluar lewat
                    // PDI ulang di sini (jalur a) — tanpa cabang ini form-nya tak pernah
                    // muncul dan unit terkunci dari sisi app, tanpa error apa pun.
                    (job.status == DeliveryStatusKey.PENDING_PDI || job.status == DeliveryStatusKey.PENDING_PERBAIKAN) && (access.pdi || isSelfPdiJob) ->
                        PdiAction(job, state.batchUnits, viewModel, state.submitting, state.checklist, state.requiresAki, state.akiForms)
                    job.status == DeliveryStatusKey.PENDING_SPK && access.kasir ->
                        KasirConfirmSpkAction(job, state.batchUnits, viewModel, state.submitting)
                    job.status == DeliveryStatusKey.PENDING_DELIVERY_NOTE && access.note ->
                        DeliveryNoteAction(job, viewModel, state.submitting)
                    // Diambil sendiri (2026-07-24): konsumen ambil unit di cabang — TIDAK
                    // lewat assign-driver. Sales pemilik SPK yang serah-terima langsung
                    // (foto+rating wajib, 2026-07-26 — konsisten pola PDI mandiri), DC/admin
                    // tetap bisa (ditambah, bukan dicabut).
                    job.status == DeliveryStatusKey.PENDING_SCHEDULING && (access.jadwal || isSelfPdiJob) && job.deliveryMethod == "self_pickup" ->
                        SelfPickupCompleteAction(job, viewModel, state.submitting)
                    job.status == DeliveryStatusKey.PENDING_SCHEDULING && access.jadwal ->
                        AssignAction(job, viewModel, state.submitting, state.drivers)
                    job.status == DeliveryStatusKey.ASSIGNED && isMyDriverJob ->
                        Column {
                            // Fan-out `dispatch` (2026-08-05): unit lain SPK ini
                            // yang dipegang driver yang SAMA ikut berangkat.
                            // Unit driver lain tak tersentuh — SPK yang dipecah
                            // ke dua driver tetap berangkat masing-masing.
                            SpkFanOutNote("Sekali tekan memberangkatkan semua unit SPK ini yang ditugaskan ke kamu.")
                            Spacer(Modifier.height(8.dp))
                            SimpleAction("Berangkat (Dispatch)", state.submitting) { viewModel.dispatch(job.id) {} }
                        }
                    job.status == DeliveryStatusKey.IN_TRANSIT && isMyDriverJob ->
                        DeliverAction(job, viewModel, state.submitting, state.driverChecklist, state.driverChecklistError)
                    // Unit sudah sampai konsumen tapi uangnya belum tercatat masuk.
                    // Berlaku SEMUA jenis pembayaran (2026-07-28) — sebelumnya
                    // non-COD tak punya titik konfirmasi sama sekali.
                    job.status == DeliveryStatusKey.DELIVERED && access.kasir && job.setoranKasirAt.isNullOrBlank() ->
                        SetoranKasirAction(job, viewModel, state.submitting)
                    // Diskon DITOLAK = SPK kembali ke sales (2026-08-06). Sebelum
                    // ini penolakan otomatis melepas unit ke antrian PDI; sekarang
                    // unitnya TETAP tertahan sampai sales memilih. Tanpa cabang ini
                    // layar cuma bilang "Tidak ada aksi pada tahap ini" dan SPK-nya
                    // mandek selamanya dari sisi app — tanpa satu pun error.
                    job.status == DeliveryStatusKey.PENDING_DISCOUNT &&
                        (viewModel.isAdminViewer || (!job.salesUserId.isNullOrBlank() && job.salesUserId == viewModel.currentUserId)) ->
                        DiskonTertahanAction(job, state.jobDiscounts, viewModel, state.submitting)
                    else -> Text(
                        when (job.status) {
                            DeliveryStatusKey.PENDING_PDI -> "Tahap ini ditangani tim PDI cabang."
                            DeliveryStatusKey.PENDING_PERBAIKAN -> "Unit ditahan (checklist Tidak) — menunggu perbaikan & PDI ulang, atau pelepasan kepala cabang."
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

private data class TimelineStep(
    val label: String,
    val timestamp: String?,
    val subtitle: String? = null,
    val skipped: Boolean = false,
    /** done|active|pending|rejected|cancelled (dari server). Fallback lokal
     *  membiarkannya null → status disimpulkan dari ada/tidaknya timestamp. */
    val tone: String? = null,
)

/** Riwayat status SPK (2026-07-26) — mirror `Timeline`/`TimelineStep` web
 *  (`components/delivery/Timeline.tsx`) supaya info lengkap di satu layar,
 *  tak perlu tebak-tebak dari status chip doang. Langkah assign/berangkat
 *  di-skip utk `self_pickup` (job lompat pending_scheduling→delivered
 *  langsung, tak pernah lewat driver beneran). */
@Composable
private fun SpkTimelineCard(
    job: DeliveryJobDto,
    discounts: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList(),
    akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
) {
    // Alur beda per metode (2026-07-26, "sales antar sendiri: penugasan +
    // surat tugas otomatis tanpa Delivery Control"):
    // - "driver" (default): Dibuat -> PDI -> Kasir -> Surat Jalan -> Ditugaskan
    //   -> Berangkat -> Terkirim.
    // - "sales_delivery": surat jalan TETAP dibuat (nomor+cap waktu ke-generate
    //   OTOMATIS begitu kasir konfirmasi, bukan manual DC) + auto-assign sales
    //   sbg driver-nya sendiri — SEMUA step (Surat Jalan/Ditugaskan/Berangkat)
    //   tetap tampil, cuma "siapa yang ngerjain" yang beda (sistem, bukan DC).
    //
    // - "self_pickup": konsumen ambil sendiri ke toko — surat jalan DAN
    //   assign/berangkat driver dua-duanya tak pernah kejadian sama sekali
    //   (kasir konfirmasi lompat LANGSUNG ke pending_scheduling, sales tandai
    //   selesai sendiri).
    //
    // TAK ADA LAGI TAHAP PDI YANG DILEWATI (backend 2026-07-27): `pdiRequired`
    // cuma menentukan SIAPA yang mengerjakan — false = PDI Mandiri oleh sales
    // pemilik SPK, true = tim PDI cabang. Checklist + foto wajib di dua-duanya.
    val isSelfPickup = job.deliveryMethod == "self_pickup"
    val isSalesDelivery = job.deliveryMethod == "sales_delivery"
    val isSelfPdiMethod = isSelfPickup || isSalesDelivery
    val isPdiMandiri = job.pdiRequired == false || isSelfPdiMethod
    // SERVER yang menyusun timeline sejak 2026-07-27 (`delivery/timeline.rs`) —
    // satu sumber, jadi app & web tak bisa lagi diam-diam berbeda isi. Blok
    // penyusun lokal di bawah CUMA fallback untuk server lama yang belum
    // mengirim field `timeline`; jangan tambahkan tahap baru di sini, tambahkan
    // di backend supaya semua klien ikut sekaligus.
    val serverSteps = job.timeline.map { TimelineStep(it.label, it.timestamp, it.detail, tone = it.tone) }
    val steps = serverSteps.ifEmpty { buildList {
        add(TimelineStep("SPK Dibuat", job.createdAt))
        // Peristiwa dari TABEL SAMPING (bug 2026-07-27: dua-duanya tak pernah
        // muncul di timeline mobile). Approval diskon menahan job di
        // `pending_discount`, approval form aki menahan `submit_pdi` — urutannya
        // mengikuti kejadian nyata, jadi keduanya sebelum langkah PDI.
        discounts.firstOrNull()?.let { d ->
            val nilai = if (d.discountType == "percent") "${d.value.toInt()}%" else formatRupiahShort(d.value)
            when (d.status) {
                "approved" -> add(TimelineStep("Diskon Disetujui", d.decidedAt, "$nilai oleh ${d.decidedByName ?: "-"}"))
                "rejected" -> add(TimelineStep("Diskon Ditolak", d.decidedAt, d.decisionNote ?: d.decidedByName))
                else -> add(TimelineStep("Menunggu Approval Diskon", null, "$nilai diajukan ${d.requestedByName ?: "-"}"))
            }
        }
        // Form terbaru yang menentukan gate sekarang (form bisa >1 kalau pernah ditolak lalu diajukan ulang).
        akiForms.maxByOrNull { it.createdAt }?.let { f ->
            add(TimelineStep("Form Pengambilan Aki Diisi", f.createdAt, "${f.merkTipe} oleh ${f.createdByNama}"))
            when (f.approvalStatus) {
                "approved" -> add(TimelineStep("Form Aki Disetujui", f.akiApproverApprovedAt, f.akiApproverApprovedNama))
                "rejected" -> add(TimelineStep("Form Aki Ditolak", f.rejectedAt, f.rejectedReason ?: f.rejectedByNama))
                else -> add(TimelineStep("Menunggu Approval Form Aki", null, "approver pusat belum memutuskan"))
            }
        }
        add(TimelineStep(if (isPdiMandiri) "PDI Mandiri Selesai" else "PDI Selesai", job.pdiAt, job.pdiByName))
        add(TimelineStep("Kasir Konfirmasi", job.spkConfirmedAt))
        if (!isSelfPickup) {
            add(TimelineStep(if (isSalesDelivery) "Surat Jalan Terbit (Otomatis)" else "Surat Jalan Terbit", job.deliveryNoteAt, job.deliveryNoteNo))
            add(TimelineStep(if (isSalesDelivery) "Ditugaskan (Sales Sendiri, Otomatis)" else "Ditugaskan ke Driver", job.assignedAt, job.assignedDriverName))
            add(TimelineStep("Berangkat", job.dispatchedAt))
            // Chat H-1 (088) = SYARAT serah terima; tanpa step ini tak kelihatan
            // kenapa job "diam" di assigned/in_transit. Job pre-088 tak punya cap
            // waktu ini — tampilkan hanya kalau relevan.
            if (!job.consumerChatAt.isNullOrBlank() ||
                job.status == DeliveryStatusKey.ASSIGNED || job.status == DeliveryStatusKey.IN_TRANSIT
            ) {
                add(TimelineStep("Chat Konsumen (H-1)", job.consumerChatAt))
            }
        }
        add(TimelineStep(if (isSelfPickup) "Diambil Konsumen" else "Terkirim", job.deliveredAt, job.deliveredBy))
        // Setoran uang COD driver → kasir (105): terjadi SETELAH delivered dan
        // non-blocking, jadi tanpa step ini tak ada tempat mana pun di detail SPK
        // yang menunjukkan uangnya sudah disetor atau belum.
        if (job.driverTerimaUang == true) {
            add(TimelineStep("Setoran Uang ke Kasir", job.setoranKasirAt, job.setoranKasirByNama))
        }
        if (job.status == DeliveryStatusKey.CANCELLED) add(TimelineStep("Dibatalkan", job.updatedAt, job.cancelReason))
    } }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            steps.forEachIndexed { i, step ->
                val rejected = step.tone == "rejected"
                val done = !rejected && !step.timestamp.isNullOrBlank()
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        when {
                            rejected -> Icons.Rounded.Cancel
                            done -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = when {
                            rejected -> MaterialTheme.colorScheme.error
                            done -> Color(0xFF12B76A)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            step.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                rejected -> MaterialTheme.colorScheme.error
                                done -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (rejected) {
                            Text(
                                listOfNotNull(step.timestamp?.let(::formatWaktuId), step.subtitle).joinToString(" · ").ifBlank { "Ditolak" },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            )
                        } else if (step.skipped) {
                            Text("Dilewati (tanpa PDI)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (done) {
                            Text(
                                listOfNotNull(step.timestamp?.let(::formatWaktuId), step.subtitle).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Menunggu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
                if (i != steps.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Karyawan yang BENAR-BENAR menangani unit ini — beda dari direktori petugas
 * (Panduan Alur) yang daftarnya jabatan se-cabang. Saat unit bermasalah, yang
 * dicari orang adalah "siapa yang meng-PDI unit INI".
 *
 * Nomor sudah dinormalisasi server (`628…`); yang tak punya nomor tetap tampil
 * tanpa tombol — kehilangan cara menghubungi tak boleh berarti kehilangan
 * informasi siapa. Cerminan modal "Kontributor SPK" di web (`PdiDetailPage`),
 * yang di sana namanya juga menautkan ke halaman statistik karyawan; app belum
 * punya layar itu, jadi di sini tindakannya WhatsApp saja.
 */
@Composable
private fun KontributorCard(orang: List<KontributorDto>) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Yang Menangani Unit Ini", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            orang.forEach { k ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(k.nama, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = k.peran.joinToString(" · ") { it.label },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val wa = k.whatsapp?.takeIf { it.isNotBlank() }
                    if (wa != null) {
                        TextButton(onClick = { runCatching { uriHandler.openUri("https://wa.me/$wa") } }) {
                            Text("WhatsApp")
                        }
                    }
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

/**
 * Catatan "aksi ini berlaku se-SPK".
 *
 * Layar detail hanya memuat SATU unit — ia tak punya daftar saudaranya, jadi
 * kalimatnya sengaja tak menyebut jumlah. Menyebut angka yang tidak diketahui
 * lebih buruk daripada tidak menyebut: petugas akan memercayainya.
 */
@Composable
private fun SpkFanOutNote(teks: String) {
    Text(
        teks,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PdiAction(
    job: DeliveryJobDto,
    batchUnits: List<DeliveryJobDto>,
    vm: DeliveryFlowViewModel, submitting: Boolean,
    checklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>,
    requiresAki: Boolean, akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>
) {
    val id = job.id
    // PREFILL dari job — SN/engine yang diisi saat input SPK tampil di form
    // (dulu mulai kosong → SN dari SPK tertimpa NULL di backend, bug live
    // testing 2026-07-24; backend kini juga COALESCE sbg lapis kedua).
    var serial by remember(job.id) { mutableStateOf(job.serialNumber.orEmpty()) }
    var engine by remember(job.id) { mutableStateOf(job.engineNumber.orEmpty()) }
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

    // ── Klaim PDI (111) ──────────────────────────────────────────────────────
    // Server SENGAJA tidak mewajibkan klaim (APK lama tak tahu cara mengklaim),
    // jadi seluruh blok ini murni tampilan: ia mencegah dua petugas mengerjakan
    // unit yang sama, tapi tak pernah menghalangi pekerjaan saat datanya tak ada.
    val ttlJam = photoState.deliveryContext?.pdiClaimTtlHours
    val claim = pdiClaimView(job.pdiClaimedBy, vm.currentUserId, serverSupportsClaim = ttlJam != null)
    // Cerminan `PDI_ROLES` backend (pdi/admin/superadmin) = gate yang SAMA dengan
    // submit PDI, lewat `access.pdi` yang sudah melipat divisi. Sales PDI Mandiri
    // sampai ke sini lewat `isSelfPdiJob` tapi TIDAK berhak mengklaim (403) —
    // jangan menawarkan tombolnya ke dia.
    val bolehKlaim = vm.access.pdi
    pdiClaimLabel(claim, job.pdiClaimedByName)?.let { label ->
        Text(
            label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = if (claim == PdiClaimView.MILIK_SAYA) Color(0xFF12B76A) else Color(0xFFB5670C),
        )
        Spacer(Modifier.height(8.dp))
    }
    if (claim == PdiClaimView.MILIK_ORANG_LAIN) {
        Text(
            "Unit ini sedang dikerjakan petugas lain, jadi form PDI-nya ditutup di sini." +
                (ttlJam?.let { " Klaimnya lepas sendiri setelah $it jam." } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Jalan keluar kalau pengklaimnya pulang sebelum TTL habis (backend
        // mengizinkan admin/superadmin/manager merebut).
        if (vm.isAdminViewer) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { vm.releasePdiClaim(id) }, enabled = !submitting) { Text("Lepas Klaim (Paksa)") }
        }
        return
    }
    if (claim == PdiClaimView.BELUM_DIKLAIM && bolehKlaim) {
        ExpressiveFilledButton(onClick = { vm.claimPdi(id) }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
            Text("Ambil PDI")
        }
        Spacer(Modifier.height(14.dp))
    } else if (claim == PdiClaimView.MILIK_SAYA) {
        ExpressiveOutlinedButton(onClick = { vm.releasePdiClaim(id) }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
            Text("Lepas Klaim")
        }
        Spacer(Modifier.height(14.dp))
    }

    // PDI MASSAL BARANG KECIL — pindah ke sini dari antrian (permintaan user
    // 2026-08-06: antrian PDI dibuat sama seperti antrian kasir, tombolnya di
    // detail). Server menyelesaikan SEKALIGUS semua unit `pending_pdi` sebatch
    // yang harga OTR-nya di bawah ambang, tanpa checklist & nomor rangka.
    //
    // Anchor WAJIB unit KECIL — unit besar dijawab 400. Karena itu id yang
    // dikirim diambil dari hasil [unitPdiKecil], BUKAN `job.id`: layar ini bisa
    // saja sedang membuka barang besar, dan barang kecil di SPK yang sama tetap
    // berhak diselesaikan lewat jalur ini.
    val kecil = unitPdiKecil(batchUnits.ifEmpty { listOf(job) }, photoState.deliveryContext?.barangBesarThreshold)
    if (kecil.isNotEmpty()) {
        ExpressiveFilledButton(
            onClick = { vm.submitPdiKecil(kecil.first().id) {} },
            enabled = !submitting, modifier = Modifier.fillMaxWidth(),
        ) { Text("Selesaikan PDI (${kecil.size} barang kecil)") }
        Text(
            "Tanpa checklist & nomor rangka. Barang besar tetap diisi formulir di bawah.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
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
            // Foto bukti aki (2026-07-24, wajib) — capture→watermark→upload
            // langsung, pola sama foto PO per-barang.
            var akiPhotoUrl by remember { mutableStateOf("") }
            var akiPhotoUploading by remember { mutableStateOf(false) }
            val akiScope = rememberCoroutineScope()
            val akiPhotoFile = remember { File(context.cacheDir, "delivery/aki_$id.jpg").apply { parentFile?.mkdirs() } }
            val akiPhotoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", akiPhotoFile) }
            val akiCam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                if (!ok) return@rememberLauncherForActivityResult
                akiPhotoUploading = true
                akiScope.launch {
                    val url = vm.uploadAkiPhoto(akiPhotoFile)
                    akiPhotoUploading = false
                    if (url != null) akiPhotoUrl = url
                }
            }
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
            Text("Foto Bukti Aki *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (akiPhotoUrl.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Foto terunggah", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    IconButton(onClick = { akiPhotoUrl = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus foto") }
                }
            } else {
                Surface(
                    onClick = { if (!akiPhotoUploading) akiCam.launch(akiPhotoUri) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (akiPhotoUploading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Mengunggah…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ambil / unggah foto", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
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
                            photoUrl = akiPhotoUrl,
                        )
                    ) {}
                },
                enabled = !submitting && tujuan.isNotBlank() && (tujuan != "lainnya" || tujuanLainnya.trim().isNotEmpty()) &&
                    merkFinal.isNotEmpty() && setN > 0 && akiPhotoUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                else Text("Simpan Form Aki")
            }
        } else if (activeAkiForms.all { it.approvalStatus == "approved" }) {
            Text("Form aki disetujui ✓ (${activeAkiForms.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF12B76A))
        } else {
            Text(
                "Form aki menunggu persetujuan approver pusat — PDI belum bisa disimpan sampai lengkap.",
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

/** Konfirmasi SPK kasir (2026-07-26) — backend WAJIB `noTransaksi` non-kosong
 *  sejak migrasi 105 (endpoint dulu tanpa body sama sekali, root cause error
 *  415: `Json` extractor axum menolak request tanpa `Content-Type: application/
 *  json`, yg terjadi kalau body kosong).
 *
 *  COD (`driverTerimaUang`): centang konfirmasi pembayaran OPSIONAL — pada COD
 *  uangnya justru belum ada di kasir, driver baru menagihnya di tempat
 *  konsumen, jadi mewajibkannya membuat SPK mandek permanen di antrian kasir.
 *  Uangnya dikonfirmasi belakangan di tab "Setoran Driver". Nominal DP mode
 *  `dp` TETAP wajib: DP dibayar konsumen di toko, uangnya nyata ada di kasir.
 *  Mirror web `KasirDashboardPage`.
 *
 *  MULTI-UNIT (2026-08-06): [batchUnits] = seluruh unit `pending_spk` SPK ini
 *  (dimuat `loadBatchUnits`). Nomor transaksi GS diketik SEKALI — di GS, SPK
 *  banyak barang adalah SATU transaksi satu nomor, dan server mem-fan-out-kan
 *  konfirmasi ini ke semuanya. Nominal DP diketik PER UNIT: tiap unit COD `dp`
 *  punya DP-nya sendiri, dan server menolak 400 daftar `units[]` yang
 *  menyisakan salah satunya kosong.
 *
 *  [batchUnits] kosong (gagal dimuat / server lama) = jatuh balik ke satu unit,
 *  persis perilaku sebelum fitur ini. Konfirmasinya tetap sah; yang hilang cuma
 *  kesempatan mengetik DP unit lain, yang lalu memakai rencana sales. */
@Composable
private fun KasirConfirmSpkAction(
    job: DeliveryJobDto,
    batchUnits: List<DeliveryJobDto>,
    vm: DeliveryFlowViewModel,
    submitting: Boolean,
) {
    val units = batchUnits.ifEmpty { listOf(job) }
    val multi = units.size > 1
    var noTransaksi by remember(job.id) { mutableStateOf(job.noTransaksi.orEmpty()) }
    var konfirmasiBayar by remember(job.id) { mutableStateOf(false) }
    // Nominal DP per unit-id. Kunci `job.id`: berpindah SPK harus mengosongkan
    // isian, kalau tidak DP SPK sebelumnya ikut terkirim.
    val dp = remember(job.id) { mutableStateMapOf<String, String>() }

    val adaCod = units.any { it.driverTerimaUang == true }
    val unitCodDp = units.filter { it.driverTerimaUang == true && it.codPaymentMode == "dp" }
    // Hanya nominal DP yang menahan tombol; centang konfirmasi tidak (lihat dok di atas).
    val dpLengkap = unitCodDp.all { (dp[it.id]?.toDoubleOrNull() ?: 0.0) > 0.0 }

    Text("Konfirmasi SPK (Kasir)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (multi) {
        Spacer(Modifier.height(4.dp))
        SpkFanOutNote("${units.size} barang dalam SPK ini dikonfirmasi sekaligus — satu nomor transaksi GS untuk semuanya.")
    }
    Spacer(Modifier.height(8.dp))
    ExpressiveTextField(noTransaksi, { noTransaksi = it }, label = "No. Transaksi GS (wajib)", modifier = Modifier.fillMaxWidth())

    // HANYA isian DP yang tinggal di sini. Rincian barangnya sudah dipajang di
    // kartu identitas SPK di atas ([SpkUnitList]) — mengulanginya di area aksi
    // membuat layar memuat daftar yang sama dua kali dan mendorong tombolnya
    // makin jauh ke bawah.
    unitCodDp.forEach { u ->
        Spacer(Modifier.height(10.dp))
        MoneyTextField(
            dp[u.id].orEmpty(), { v -> dp[u.id] = v },
            // Label menyebut BARANGNYA, bukan cuma "DP": dengan beberapa unit
            // COD dp, kolom bernama sama semua tak bisa dibedakan isinya.
            label = if (multi) {
                "DP ${u.namaBarang ?: u.kodeBarang ?: u.kodePengiriman} (wajib) *"
            } else {
                "DP diterima kasir (wajib) *"
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (adaCod) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { konfirmasiBayar = !konfirmasiBayar }) {
            Checkbox(checked = konfirmasiBayar, onCheckedChange = { konfirmasiBayar = it })
            Text("Sudah cek pembayaran benar (opsional)", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Uang yang ditagih driver dikonfirmasi nanti di tab Setoran Driver, setelah barang diantar.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(14.dp))
    ExpressiveFilledButton(
        onClick = {
            vm.confirmSpk(
                id = job.id,
                noTransaksi = noTransaksi,
                kasirKonfirmasiPembayaran = if (adaCod) konfirmasiBayar else null,
                // Field lama dipakai HANYA saat satu unit; di SPK banyak unit
                // `units[]` sudah memuat nominal unit ini juga, dan mengirim
                // keduanya membuat dua sumber untuk angka yang sama.
                kasirDpDiterima = if (!multi) dp[job.id]?.toDoubleOrNull() else null,
                units = if (multi) {
                    unitCodDp.map {
                        com.krisoft.tridjayaelektronik.data.model.ConfirmSpkUnitBody(
                            id = it.id, kasirDpDiterima = dp[it.id]?.toDoubleOrNull(),
                        )
                    }
                } else null,
            ) {}
        },
        enabled = !submitting && noTransaksi.trim().isNotEmpty() && dpLengkap,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(
            when {
                !dpLengkap -> "Isi DP tiap unit COD"
                multi -> "Konfirmasi SPK (${units.size} unit)"
                else -> "Konfirmasi SPK"
            }
        )
    }
}

/**
 * Kasir menutup buku satu unit: nominal yang benar-benar diterima + foto bukti.
 * Non-blocking (tak mengubah status) dan boleh diulang — server menimpa.
 */
/**
 * SPK tertahan di `pending_discount` — apa yang bisa dilakukan pemiliknya.
 *
 * Sampai 2026-08-05, penolakan diskon melepas unit ke antrian PDI dengan
 * sendirinya, jadi tahap ini tak pernah butuh tombol. Sejak 2026-08-06
 * penolakan MENAHAN unit dan mengembalikan SPK ke sales — perubahan yang tak
 * menghasilkan error apa pun di app lama, cuma SPK yang diam.
 *
 * Tiga jalan keluar versi server: ajukan ulang diskon, sunting isi SPK, atau
 * lanjut tanpa diskon. Yang kedua ditawarkan lewat kartu "Ubah Isi SPK" yang
 * terpisah, yang ketiga tombol di sini. Yang PERTAMA belum ada di app —
 * `POST /inventory/discount-requests` tak pernah dipanggil dari HP (diskon
 * hanya lahir bersama SPK), jadi kalimatnya menunjuk ke web alih-alih memberi
 * tombol yang tak akan bekerja.
 */
@Composable
private fun DiskonTertahanAction(
    job: DeliveryJobDto,
    riwayat: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>,
    vm: DeliveryFlowViewModel,
    submitting: Boolean,
) {
    // `createdAt` ISO-8601 → urut leksikografis = urut waktu.
    val terakhir = riwayat.maxByOrNull { it.createdAt }
    val ditolak = terakhir?.takeIf { it.status == "rejected" }
    var konfirmasi by remember(job.id) { mutableStateOf(false) }

    Text("Menunggu Keputusan Diskon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))

    if (ditolak == null) {
        Text(
            if (terakhir == null) {
                "Pengajuan diskon SPK ini sedang menunggu approver. Unit belum masuk antrian PDI sampai ada keputusan."
            } else {
                "Pengajuan diskon masih menunggu keputusan approver."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Diskon ditolak", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
            )
            ditolak.decisionNote?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("Alasan: $it", style = MaterialTheme.typography.bodySmall)
            }
            ditolak.decidedByName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text("Oleh $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "SPK ini TIDAK otomatis lanjut. Pilihanmu: ubah isi SPK lewat tombol di atas, " +
                    "ajukan ulang diskon lewat web, atau lanjutkan tanpa diskon.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    ExpressiveFilledButton(
        onClick = { konfirmasi = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text("Lanjut Tanpa Diskon")
    }

    if (konfirmasi) {
        AlertDialog(
            onDismissRequest = { if (!submitting) konfirmasi = false },
            title = { Text("Lanjut tanpa diskon?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Harga kembali ke harga normal dan SELURUH barang SPK ini masuk antrian PDI. " +
                        "Tak bisa dibatalkan dari sini — kalau masih mau menawar, batalkan dan ajukan diskon lagi lewat web."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = { vm.lanjutTanpaDiskon(ditolak.id, job.id); konfirmasi = false },
                ) { Text("Ya, lanjutkan") }
            },
            dismissButton = { TextButton(onClick = { konfirmasi = false }, enabled = !submitting) { Text("Batal") } },
        )
    }
}

@Composable
private fun SetoranKasirAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    val id = job.id
    var nominal by remember { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/setoran_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val photoState by vm.state.collectAsState()
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.onDeliverPhotoCaptured(file) }

    photoState.deliverPhoto?.takeIf { !photoState.deliverPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeDeliverPhoto() }, onConfirm = { vm.confirmDeliverPhoto() })
    }

    Text("Konfirmasi Pembayaran Diterima", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        if (job.driverTerimaUang == true) "Uang COD yang disetor driver ke kasir."
        else "Pembayaran penjualan ini (transfer/tunai di toko).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Kebalikan dari catatan fan-out di tahap lain, dan justru karena itu
    // WAJIB ada: antrian "Konfirmasi Pembayaran" kini satu baris per SPK
    // (2026-08-06), sedangkan `POST /setoran-kasir` tetap menutup SATU unit.
    // Tanpa kalimat ini, kartu SPK yang tetap muncul setelah dikonfirmasi
    // terbaca sebagai gagal-simpan, padahal sisa barangnya memang belum.
    Text(
        "Berlaku untuk BARANG INI saja — barang lain di SPK yang sama " +
            "dikonfirmasi sendiri-sendiri (nominal tiap barang beda).",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold, color = Color(0xFFB5670C),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "${job.namaBarang ?: job.kodeBarang ?: "-"}${job.tipe?.let { " · $it" } ?: ""}",
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))
    MoneyTextField(nominal, { nominal = it }, label = "Nominal diterima (wajib) *", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    PhotoBox(photoState.deliverPhoto, "Foto bukti (wajib)") { cam.launch(uri) }
    Spacer(Modifier.height(14.dp))
    val hasPhoto = photoState.deliverPhoto != null && photoState.deliverPhotoConfirmed
    val nominalValid = (nominal.toDoubleOrNull() ?: 0.0) > 0.0
    ExpressiveFilledButton(
        onClick = { vm.setoranKasir(id, nominal.toDoubleOrNull() ?: 0.0) {} },
        enabled = !submitting && hasPhoto && nominalValid, modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(if (!hasPhoto) "Ambil foto bukti dulu" else "Konfirmasi Pembayaran")
    }
}

@Composable
private fun DeliveryNoteAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var source by remember { mutableStateOf(job.kodeDealer.orEmpty()) }
    Text("Terbitkan Surat Jalan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    SpkFanOutNote("Nomor surat jalan yang sama dipakai untuk SELURUH unit SPK ini — satu pengiriman, satu lembar.")
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
    Spacer(Modifier.height(4.dp))
    // Fan-out `assign_driver` (2026-08-05). Konsekuensi operasional yang WAJIB
    // disebut: memecah satu SPK ke dua driver tak lagi bisa lewat assign
    // satu-satu — tugaskan sekali, lalu pindahkan unitnya lewat "reassign" di
    // web (jalur itu sengaja TIDAK difan-out justru supaya pemecahan tetap
    // mungkin). Tanpa kalimat ini DC akan mengira app-nya rusak.
    SpkFanOutNote(
        "Driver & jadwal ini berlaku untuk SELURUH unit SPK yang masih menunggu penjadwalan. " +
            "Unit \"diambil sendiri\" dilewati. Mau dipecah ke dua driver? Tugaskan sekali dulu, " +
            "lalu pindahkan unitnya lewat menu reassign di web."
    )
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
    // Sales antar sendiri (2026-07-24): fallback manual kalau auto-assign backend
    // gagal (map_url kosong saat surat jalan terbit) — DC bisa pilih sales pembuat
    // SPK sbg driver, sama seperti opsi driver asli.
    if (job.deliveryMethod == "sales_delivery" && !job.salesUserId.isNullOrBlank()) {
        Text("Sales antar sendiri", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val salesId = job.salesUserId
        val salesLabel = job.salesName?.takeIf { it.isNotBlank() } ?: salesId
        val sel = driverId == salesId
        Surface(
            onClick = { driverId = salesId; driverName = salesLabel },
            shape = RoundedCornerShape(12.dp),
            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sales: $salesLabel", color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
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

/**
 * Timestamp backend → epoch millis, penafsiran ikut BENTUK nilainya.
 *
 * Meneruskan ke [parseTimestampMillis] (router yang sama dipakai label
 * notifikasi & umur SPK) alih-alih parser lokal — dulu berkas ini punya
 * `parseUtcMillis` sendiri yang SELALU menafsir UTC. Sejak backend mengirim WIB
 * polos tanpa penanda (kontrak `tridjaya_shared::waktu`, 2026-07-30), penafsiran
 * itu menaruh `consumerChatAt` 7 jam di MASA DEPAN: `elapsedMin` jadi negatif,
 * jadi gate serah terima menahan driver ~7 jam + jeda padahal server sudah
 * melepasnya setelah `chatMinMinutes`.
 *
 * Nilai lama ber-`Z` tetap dibaca UTC oleh router yang sama.
 */
private fun parseWaktuMillis(ts: String?): Long? =
    parseTimestampMillis(ts?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T'))

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
    Spacer(Modifier.height(4.dp))
    // Fan-out `deliver` (2026-08-05): foto + rating + checklist SEKALI berlaku
    // untuk semua unit SPK ini yang kamu pegang. Yang TIDAK ikut: unit driver
    // lain. Gate-nya (chat H-1 / checklist / foto uang) dinilai server atas
    // unit yang dipanggil — karena itu buka SPK dari unit COD kalau ada, supaya
    // foto uangnya tetap ditagih. Baris DB tetap per unit, jadi hitungan
    // kiriman driver tidak berubah.
    SpkFanOutNote(
        "Foto, rating, dan checklist ini berlaku untuk semua unit SPK ini yang kamu antar sekaligus." +
            if (!needCash) {
                " Kalau SPK ini punya barang COD, buka serah terimanya DARI barang COD itu — " +
                    "foto uang cuma diminta pada barang yang dibuka."
            } else ""
    )
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
    // Jeda minimum chat dari SERVER (menit; 0 = chat wajib tanpa tunggu —
    // pelonggaran live testing 2026-07-23). Backend lama tanpa field → 60.
    val chatMinMin: Long = (photoState.deliveryContext?.chatMinMinutes ?: 60).coerceAtLeast(0).toLong()
    val chatMillis = parseWaktuMillis(job.consumerChatAt)
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(chatMillis) {
        while (true) { nowMillis = System.currentTimeMillis(); delay(30_000) }
    }
    val chatWaitLeftMin: Long? = if (!gate088 || chatMillis == null || chatMinMin <= 0) null else {
        val elapsedMin = (nowMillis - chatMillis) / 60_000
        // coerceIn: jam device mundur (skew) jangan menampilkan sisa > jeda penuh.
        if (elapsedMin >= chatMinMin) null else (chatMinMin - elapsedMin).coerceIn(1, chatMinMin)
    }
    val chatBlocked = serverGateOn && !vm.isAdminViewer && gate088 &&
        (job.consumerChatAt == null || chatWaitLeftMin != null)
    // Teks status chat H-1 — hanya utk non-admin (admin di-bypass server, pesan
    // bergaya blocking di atas tombol aktif = menyesatkan).
    if (!vm.isAdminViewer && gate088 && job.consumerChatAt == null) {
        Spacer(Modifier.height(8.dp))
        val syarat = if (chatMinMin > 0) " (wajib ≥$chatMinMin menit sebelum serah terima)" else ""
        if (serverGateOn) {
            Text("Belum chat konsumen — tandai chat dulu$syarat.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        } else {
            Text("Belum chat konsumen — biasakan tandai chat dulu (aturan wajib segera diberlakukan).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (!vm.isAdminViewer && serverGateOn && chatWaitLeftMin != null) {
        Spacer(Modifier.height(8.dp))
        Text("Chat konsumen tercatat — tunggu ±$chatWaitLeftMin menit lagi (syarat minimal $chatMinMin menit).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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

/** Diambil sendiri (2026-07-24): konsumen ambil unit langsung di cabang — DC/admin
 *  tandai selesai, foto+rating wajib (sama standar [DeliverAction]), TANPA gate
 *  chat-H1/checklist-driver/cash-photo (tak relevan, bukan diantar). Transisi
 *  langsung `pending_scheduling → delivered`. */
@Composable
private fun SelfPickupCompleteAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    val id = job.id
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "delivery/selfpickup_$id.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val photoState by vm.state.collectAsState()
    // Reuse slot foto [DeliveryFlowViewModel.onDeliverPhotoCaptured] — job self_pickup
    // (pending_scheduling) tak pernah bareng job in_transit (deliver) di layar yang sama.
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.onDeliverPhotoCaptured(file) }

    photoState.deliverPhoto?.takeIf { !photoState.deliverPhotoConfirmed }?.let { bmp ->
        PhotoReviewDialog(bmp, onRetake = { vm.retakeDeliverPhoto() }, onConfirm = { vm.confirmDeliverPhoto() })
    }

    Text("Selesai — Diambil Sendiri", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("Konsumen mengambil unit langsung di cabang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    PhotoBox(photoState.deliverPhoto, "Foto serah terima (wajib)") { cam.launch(uri) }
    Spacer(Modifier.height(12.dp))
    Text("Rating (wajib)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
    val hasPhoto = photoState.deliverPhoto != null && photoState.deliverPhotoConfirmed
    ExpressiveFilledButton(
        onClick = { vm.selfPickupComplete(id, rating, comment) {} },
        enabled = !submitting && hasPhoto, modifier = Modifier.fillMaxWidth()
    ) {
        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(if (!hasPhoto) "Ambil foto dulu" else "Tandai Selesai")
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
    // Inset dibaca DI LUAR Dialog — dari jendela Activity, bukan jendela dialog.
    // Jendela dialog sering melaporkan systemBars = 0 (percobaan sebelumnya
    // membacanya dari dalam dan tombolnya TETAP tertutup tombol navigasi
    // 3-tombol; laporan lapangan 2026-08-03). Jendela Activity selalu tahu
    // tinggi bar yang sebenarnya.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    Dialog(
        onDismissRequest = onRetake,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // decorFitsSystemWindows=false WAJIB: defaultnya true bikin dialog "auto-fit" system bar,
        // tapi di device gesture-nav banyak OEM window tetap dianggap sudah fit padahal gesture pill
        // masih digambar DI ATAS konten dialog — WindowInsets.systemBars di bawah lalu terbaca 0,
        // tombol nempel ke tepi bawah persis walau padding-nya sudah ditulis (bug lama, komentar
        // sebelumnya salah kira sudah beres). Matikan auto-fit → insets sungguhan didorong ke sini.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        }
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Cek hasil foto — pastikan watermark jam & lokasi terbaca",
                    color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 12.dp + systemBars.calculateTopPadding())
                )
                Image(
                    bitmap = bitmap.asImageBitmap(), contentDescription = "Pratinjau foto",
                    contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            // Naikkan tombol dari tepi layar: inset navigasi + jarak nyaman.
                            // `coerceAtLeast` = lantai pengaman kalau inset TETAP
                            // terbaca 0 di suatu OEM: 24.dp masih menyisakan jarak
                            // walau tombol navigasi tak terlaporkan sama sekali.
                            bottom = 32.dp + systemBars.calculateBottomPadding().coerceAtLeast(24.dp),
                        ),
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
fun CreateSpkScreen(
    onBack: () -> Unit,
    /** PDI Mandiri (2026-07-26): dipanggil alih-alih [onBack] kalau SPK yang baru
     *  dibuat butuh sales langsung isi form PDI-nya sendiri (bukan skip). */
    onCreated: (String) -> Unit = {},
    viewModel: DeliveryFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
    // Metode pengiriman (2026-07-24): driver biasa (default) | self_pickup | sales_delivery.
    var deliveryMethodSel by remember { mutableStateOf("driver") }
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

    // PDI Mandiri: barang pertama ber-toggle "PDI Mandiri" (pdiRequired=false) ->
    // langsung lompat ke form PDI barang itu (bukan balik ke daftar) supaya sales
    // isi checklist+foto saat itu juga. Tak ada barang mandiri -> balik daftar.
    // Sejak backend 2026-07-27 syarat metode self_pickup/sales_delivery DIBUANG:
    // rute skip PDI tak ada lagi, jadi pdiRequired=false pada metode "driver" pun
    // kini mendarat di pending_pdi dan menunggu sales. Tanpa perubahan ini SPK
    // begitu nyangkut senyap di antrian PDI — sales balik ke daftar, tak tahu
    // masih ada yang harus dia kerjakan.
    // id diambil LANGSUNG dari `result.ids` (sejajar `kodePengiriman`, backend
    // 2026-07-26) — BUKAN reverse-lookup search antrian (percobaan pertama:
    // rapuh, kena filter status role-scoped sales + limit pagination, gagal
    // senyap tanpa error yang kelihatan).
    LaunchedEffect(state.lastCreateResult) {
        val result = state.lastCreateResult ?: return@LaunchedEffect
        val mandiriBaris = items.indexOfFirst { !it.pdiRequired }.takeIf { it >= 0 }?.plus(1)
        val targetIdx = if (mandiriBaris != null) {
            result.kodePengiriman.indexOfFirst { it.contains("-${mandiriBaris}u") }
        } else -1
        val id = result.ids.getOrNull(targetIdx)
        if (id != null) onCreated(id) else onBack()
    }

    fun applyCabangChange(next: String) {
        spkCabang = next; items = emptyList(); barangSearch = ""
        viewModel.searchStok("", next); viewModel.clearSerialCache()
    }

    val totalUnits = items.sumOf { it.qtyInt ?: 0 }
    val itemsValid = items.isNotEmpty() && items.all { it.issues().isEmpty() }
    val mapUrlWajib = deliveryMethodSel == "sales_delivery"
    val mapUrlKurang = mapUrlWajib && mapUrl.isBlank()
    val blocker = spkSubmitBlocker(
        pelanggan = pelanggan, telepon = telepon, nik = nik, mapUrl = mapUrl,
        deliveryMethod = deliveryMethodSel, spkCabang = spkCabang,
        itemsCount = items.size, itemsValid = itemsValid, totalUnits = totalUnits,
    )
    val canSubmit = blocker == null

    TridjayaCollapsibleHeader(title = "Input SPK", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            contentModifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpkSection("1. Pelanggan", sec1, { sec1 = !sec1 }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Dirapikan saat FOKUS LEPAS, bukan tiap ketukan tombol:
                    // sales melihat hasil seragamnya sebelum menyimpan (jadi
                    // tak kaget kalau berubah), tapi kursornya tak pernah
                    // dipindahkan di tengah mengetik. Nilai yang dikirim
                    // dinormalkan lagi saat submit — ini murni pratinjau.
                    ExpressiveTextField(
                        pelanggan, { pelanggan = it }, label = "Nama pelanggan *",
                        modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                            if (!f.isFocused && pelanggan.isNotBlank()) pelanggan = rapikanNama(pelanggan)
                        },
                    )
                    ExpressiveTextField(
                        telepon, { telepon = it }, label = "No. HP *",
                        keyboardType = KeyboardType.Phone,
                        supportingText = "Disimpan sebagai 62… (mis. 6285172083358)",
                        modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                            if (!f.isFocused && telepon.isNotBlank()) telepon = rapikanNomorHp(telepon)
                        },
                    )
                    ExpressiveTextField(alamat, { alamat = it }, label = "Alamat", singleLine = false, modifier = Modifier.fillMaxWidth())
                    ExpressiveTextField(
                        mapUrl, { mapUrl = it },
                        label = if (mapUrlWajib) "Link Lokasi Maps *" else "Link Lokasi Maps",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth(),
                        isError = attemptedSubmit && mapUrlKurang,
                        supportingText = if (mapUrlWajib)
                            "Wajib untuk Sales Antar Sendiri — tanpa ini job masuk antrian Delivery Control, bukan ke kamu."
                        else null
                    )
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
                    DeliveryMethodDropdown(deliveryMethodSel) { next ->
                        deliveryMethodSel = next
                        // COD = uang diambil DRIVER — tak relevan tanpa driver (diambil
                        // sendiri/sales antar sendiri). Clear biar tak nyangkut/ke-submit
                        // diam-diam (koreksi 2026-07-26).
                        if (next != "driver") {
                            items = items.map { it.copy(driverTerimaUang = false, codPaymentMode = "", codDpAmount = "") }
                        }
                    }
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
                    // Cabang barang dilekatkan saat SUBMIT dari `spkCabang`, bukan dibawa
                    // tiap baris — jadi daftar milik cabang lain WAJIB tak bisa ditap sama
                    // sekali. Respons pencarian cabang sebelumnya bisa mendarat setelah
                    // selektor pindah (insiden DLV-M84149DA0: barang Pagaden ter-submit
                    // sebagai Soklat, unitnya masuk antrian PDI cabang yang tak
                    // memegangnya). ViewModel juga membatalkan pencarian lama; penyaring
                    // ini yang membuat invariannya tak bergantung pada urutan balapan.
                    val stokRows = stokRowsForCabang(state.stokResults, state.stokDealer, spkCabang)
                    ExpressiveTextField(barangSearch, { barangSearch = it }, label = "Cari & tambah barang (min. 2 karakter)", modifier = Modifier.fillMaxWidth())
                    when {
                        state.stokLoading -> Text("Mencari…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        state.stokAttempted && stokRows.isEmpty() -> Text("Tidak ditemukan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (stokRows.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            stokRows.forEach { row ->
                                // Baris stok-nol yang muncul HANYA karena sudah punya SPK
                                // berjalan: tampilkan supaya sales tahu barangnya sudah
                                // dipesan (bukan "cabang ini tak punya"), tapi JANGAN bisa
                                // dipilih — memilihnya membuat SPK kedua atas unit yang
                                // sudah dipesan, dan kalau notanya sudah masuk GS, sudah
                                // terjual ke orang lain.
                                val terkunci = row.terkunciKarenaDipesan
                                Surface(
                                    onClick = {
                                        // Prepend + collapse kartu lain (baru = fokus)
                                        items = listOf(newSpkItemDraft(row)) + items.map { it.copy(expanded = false) }
                                        barangSearch = ""
                                        viewModel.ensureSerials(spkCabang, row.kode.trim())
                                    },
                                    enabled = !terkunci,
                                    shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        // 3 baris, BUKAN 1. Nama barang GS menaruh tipe/model di
                                        // BELAKANG ("AC AQUA 1PK AQA-KR9VQCL", "1 SET ACCU DUBSS
                                        // 6-EVF-45.5"), jadi memotongnya di satu baris membuang
                                        // persis bagian yang membedakan satu varian dari varian
                                        // lain — sales melihat beberapa hasil cari yang terlihat
                                        // identik dan tak punya cara memilih yang benar.
                                        Text(row.nama.trim(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        Text("${row.kode} · ${row.kategori} · ${row.merk}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        // Stok + harga langsung di opsi hasil cari (paritas web
                                        // `renderStockRow`): sales tak perlu memilih dulu baru tahu
                                        // barangnya ada berapa. `stok` null = server tak mengirim
                                        // kolomnya -> "-", BUKAN 0 (0 itu pernyataan "habis").
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val stok = row.stok
                                            val stokWarna = when {
                                                stok == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                stok > 0 -> Color(0xFF12B76A)
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                            Surface(color = stokWarna.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                                                Text(
                                                    "Stok: ${stok?.toString() ?: "-"}",
                                                    color = stokWarna,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                rupiah(row.harga),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (row.dipesan > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    color = Color(0xFFF79009).copy(alpha = 0.16f),
                                                    shape = RoundedCornerShape(50),
                                                ) {
                                                    Text(
                                                        "Sudah dipesan · ${row.dipesan} SPK",
                                                        color = Color(0xFFB54708),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        if (terkunci) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Unit ini sudah masuk SPK lain. Kalau SPK itu batal, minta admin " +
                                                    "membatalkannya dulu — stok terbaca lagi setelah GS diperbarui.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
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
                                uploadPoPhoto = { file -> viewModel.uploadPoPhoto(file) },
                                uploadBuktiAcc = { file -> viewModel.uploadBuktiAccPhoto(file) },
                                deliveryMethod = deliveryMethodSel,
                            )
                        }
                    }
                }
            }

            state.actionError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            if (attemptedSubmit) blocker?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            ExpressiveFilledButton(
                onClick = {
                    attemptedSubmit = true
                    if (!canSubmit) return@ExpressiveFilledButton
                    val body = CreateDeliveryBody(
                        // Diseragamkan di sini, bukan saat mengetik: mengubah
                        // teks di tengah pengetikan memindahkan kursor dan
                        // justru bikin salah ketik. Lihat `FormatKonsumen.kt`.
                        customerName = rapikanNama(pelanggan), customerPhone = rapikanNomorHp(telepon),
                        customerAddress = alamat.trim().ifBlank { null },
                        customerMapUrl = mapUrl.trim().ifBlank { null },
                        customerNik = nik.trim().ifBlank { null },
                        salesNik = null,
                        deliveryMethod = deliveryMethodSel.takeIf { it != "driver" },
                        sosmedTiktok = sosTiktok.trim().ifBlank { null },
                        sosmedFacebook = sosFb.trim().ifBlank { null },
                        sosmedInstagram = sosIg.trim().ifBlank { null },
                        keterangan = keterangan.trim().ifBlank { null },
                        items = items.map { it.toItemBody(spkCabang, BranchRegions.dealerRegion(spkCabang)) }
                    )
                    viewModel.createSpk(body)
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
 *  `OptionDropdownField` (`ui/leads/AddLeadScreen.kt`), grouped per region.
 *  `internal` + [label] sejak layar Input Serial Number ikut memilih cabang
 *  (registry SN terpusat) — daftar 13 cabang cukup punya SATU selektor. */
@Composable
internal fun CabangSelector(
    selected: String,
    onSelect: (String) -> Unit,
    label: String = "Cabang SPK *"
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = BranchRegions.DEALER_LABEL[selected] ?: ""
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

/** Dropdown Metode Pengiriman (2026-07-24) — driver biasa / diambil sendiri / sales
 *  antar sendiri (pola sama [CabangSelector]). */
@Composable
private fun DeliveryMethodDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "driver" to "Driver (standar)",
        "self_pickup" to "Diambil Sendiri",
        "sales_delivery" to "Sales Antar Sendiri",
    )
    val label = options.firstOrNull { it.first == selected }?.second ?: options[0].second
    Column {
        Text("Metode Pengiriman", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (k, l) ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(k); expanded = false })
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
                // Satu kartu per SPK (2026-08-06): approve/reject di server
                // FAN-OUT ke seluruh pengajuan `pending` sebatch, jadi daftar
                // per-baris membuat approver menekan N tombol untuk keputusan
                // yang sudah selesai pada tekanan pertama — sisanya dijawab
                // "sudah diputuskan" dan terbaca sebagai kegagalan. Nilai tiap
                // barang tetap dari pengajuannya sendiri; kartu ini yang
                // menotalkannya supaya keputusannya diambil atas angka SPK utuh.
                val grup = state.discounts.groupBy { it.spkBatchKode }.entries.toList()
                items(grup, key = { it.key }) { (kode, pengajuan) ->
                    DiscountSpkCard(
                        kode = kode,
                        pengajuan = pengajuan,
                        submitting = state.submitting,
                        buktiFoto = state.diskonBuktiPhotos,
                        // Anchor = pengajuan pertama; server menyeret sisanya.
                        onApprove = { viewModel.approveDiscount(pengajuan.first().id, "") },
                        onReject = { rejectId = pengajuan.first().id },
                    )
                }
            }
        }
    }

    rejectId?.let { id ->
        var note by remember { mutableStateOf("") }
        // `decisionNote` WAJIB saat menolak (discounts.rs `reject_request`):
        // tanpa isi, server membalas 400 "decisionNote wajib diisi saat menolak".
        // Label sempat menulis "opsional" tanpa `enabled` — tolak dari HP selalu
        // gagal tanpa penjelasan.
        AlertDialog(
            onDismissRequest = { rejectId = null },
            title = { Text("Tolak diskon?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // Dua hal yang berubah 2026-08-06 dan sama-sama tak terlihat
                    // dari tombolnya: keputusan ini menyapu SELURUH barang SPK,
                    // dan penolakan TIDAK melepas unit — SPK berhenti sampai
                    // sales-nya memilih. Approver yang mengira "ditolak = lanjut
                    // tanpa diskon" akan menahan SPK orang tanpa sadar.
                    Text(
                        "Penolakan berlaku untuk SEMUA barang SPK ini, dan unitnya TIDAK otomatis lanjut — " +
                            "SPK kembali ke sales untuk direvisi atau dilanjutkan tanpa diskon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ExpressiveTextField(
                        note, { note = it },
                        label = "Alasan penolakan (wajib)",
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = note.isNotBlank(),
                    onClick = { viewModel.rejectDiscount(id, note.trim()); rejectId = null }
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { rejectId = null }) { Text("Batal") } }
        )
    }
}

/**
 * Satu SPK = satu kartu, berisi baris per barang + total potongan.
 *
 * Keputusan diambil di level SPK karena begitulah server memutuskannya
 * (fan-out ke seluruh pengajuan `pending` sebatch). Nilai per barang tetap
 * ditampilkan apa adanya — approver perlu melihat komposisinya, bukan cuma
 * jumlahnya, dan server memang menerapkan nilai masing-masing baris, bukan
 * nilai anchor.
 */
@Composable
private fun DiscountSpkCard(
    kode: String,
    pengajuan: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>,
    submitting: Boolean,
    buktiFoto: Map<String, AkiPhotoState>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val total = pengajuan.sumOf { it.value }
    val konsumen = pengajuan.firstOrNull()?.jobSummary?.customerName ?: "-"
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    kode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                    Text(
                        "${pengajuan.size} barang", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(konsumen, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            pengajuan.forEach { d ->
                Spacer(Modifier.height(10.dp))
                DiscountBaris(d, buktiFoto[d.id])
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total potongan SPK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(rupiah(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFB5670C))
            }
            if (pengajuan.size > 1) {
                Text(
                    "Satu keputusan berlaku untuk ${pengajuan.size} barang SPK ini.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                )
            }
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

/** Satu barang di dalam kartu SPK — isinya sama persis dengan kartu per-baris
 *  yang lama, minus tombol keputusan (yang kini milik level SPK). */
@Composable
private fun DiscountBaris(d: com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto, bukti: AkiPhotoState?) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                d.jobSummary?.namaBarang ?: d.jobSummary?.kodeBarang ?: "-",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text("−${rupiah(d.value)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFB5670C))
        }
        d.baris?.let {
            Text("baris $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoLine("Harga sebelum", d.hargaSebelum?.let { rupiah(it) })
        InfoLine("Harga sesudah", d.hargaSesudah?.let { rupiah(it) })
        InfoLine("Alasan", d.reason)
        if (!d.accOleh.isNullOrBlank()) InfoLine("Acc oleh (di luar sistem)", d.accOleh)
        when (bukti) {
            // Bisa ditekan untuk ukuran penuh — tulisan di tangkapan layar
            // WA/kwitansi tak terbaca pada thumbnail 140dp ber-Crop.
            is AkiPhotoState.Ada -> {
                Spacer(Modifier.height(8.dp))
                BuktiFotoThumbnail(
                    bitmap = bukti.bitmap,
                    deskripsi = "Bukti acc diskon",
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
            is AkiPhotoState.Memuat -> {
                Spacer(Modifier.height(8.dp))
                Text("Memuat bukti acc…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is AkiPhotoState.Gagal -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bukti acc gagal dimuat — file tidak ada di server atau jaringan putus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            null -> Unit // sales memang tak melampirkan bukti — bukan kegagalan
        }
        InfoLine("Diajukan", d.requestedByName)
    }
}


/**
 * Koreksi salah input SPK oleh administrator (2026-08-01). Dialognya
 * data-driven dari [SPK_EDIT_FIELDS] — menambah field cukup di daftar itu,
 * tak ada 29 `remember` yang harus dijaga sinkron dengan backend.
 *
 * Yang dikirim hanya SELISIH-nya ([buildSpkEditPatch]); field yang tak
 * disentuh tak ikut, supaya dua administrator yang membuka SPK yang sama tak
 * saling menimpa isian yang tak mereka ubah.
 */
@Composable
private fun EditSpkAction(job: DeliveryJobDto, vm: DeliveryFlowViewModel, submitting: Boolean) {
    var show by remember { mutableStateOf(false) }
    // `job.id` sebagai kunci: berpindah unit harus memuat ulang isian, kalau
    // tidak koreksi unit A tersimpan ke unit B.
    var form by remember(job.id) { mutableStateOf(spkEditFormFromJob(job)) }
    var alasan by remember(job.id) { mutableStateOf("") }
    var pesan by remember(job.id) { mutableStateOf<String?>(null) }

    OutlinedButton(
        onClick = { form = spkEditFormFromJob(job); alasan = ""; show = true },
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Ubah Isi SPK") }
    pesan?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    if (show) {
        val patch = buildSpkEditPatch(form, job, alasan)
        AlertDialog(
            onDismissRequest = { if (!submitting) show = false },
            title = { Text("Ubah Isi SPK", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(job.kodePengiriman, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Koreksi salah input. Diskon tak bisa diubah di sini — ajukan lewat menu " +
                            "diskon supaya tetap ada approver yang memutuskan. Total dihitung ulang " +
                            "otomatis dari harga OTR dikurangi diskon berjalan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpkEditGrup.entries.forEach { grup ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            grup.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SPK_EDIT_FIELDS.filter { it.grup == grup }.forEach { f ->
                            Spacer(Modifier.height(8.dp))
                            ExpressiveTextField(
                                value = form[f.key].orEmpty(),
                                onValueChange = { v -> form = form + (f.key to v) },
                                label = f.label,
                                singleLine = f.tipe != SpkEditTipe.TEKS_PANJANG,
                                keyboardType = if (f.tipe == SpkEditTipe.ANGKA) KeyboardType.Number else KeyboardType.Text,
                                supportingText = if (f.tipe == SpkEditTipe.METODE_BAYAR) "cash atau credit" else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ExpressiveTextField(
                        value = alasan,
                        onValueChange = { alasan = it },
                        label = "Alasan koreksi (wajib)",
                        placeholder = "mis. sales salah pilih varian warna",
                        isError = alasan.isNotEmpty() && !spkEditAlasanValid(alasan),
                        supportingText = "Tersimpan di log aktivitas beserta nama Anda.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting && patch != null && spkEditAlasanValid(alasan),
                    onClick = {
                        val body = patch ?: return@TextButton
                        vm.editJob(job.id, body) { konsumenDiubah ->
                            show = false
                            pesan = if (konsumenDiubah > 1) {
                                "Tersimpan · data konsumen ikut diperbarui di $konsumenDiubah unit SPK ini"
                            } else {
                                "Tersimpan"
                            }
                        }
                    }
                ) { Text(if (submitting) "Menyimpan..." else "Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }, enabled = !submitting) { Text("Batal") }
            }
        )
    }
}
