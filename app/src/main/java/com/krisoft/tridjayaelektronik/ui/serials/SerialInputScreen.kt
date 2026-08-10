package com.krisoft.tridjayaelektronik.ui.serials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK
import com.krisoft.tridjayaelektronik.data.KONDISI_PILIHAN
import com.krisoft.tridjayaelektronik.data.kondisiLabel
import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import com.krisoft.tridjayaelektronik.ui.deliveryflow.BarcodeScanButton
import com.krisoft.tridjayaelektronik.ui.deliveryflow.CabangSelector
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * Input Serial Number (admin-stok) — **dua pekerjaan, dua pilihan**:
 *
 * - [SerialInputMode.TETAPKAN] — barang yang SUDAH bernomor seri pabrik, SN-nya
 *   tinggal didaftarkan ke registry (satu per baris).
 * - [SerialInputMode.BUAT_BARU] — barang yang memang tak pernah punya nomor
 *   pabrik (sofa, kursi): kodenya dibuat sistem (`GEN-…`), dicetak lewat web,
 *   lalu ditempel ke unitnya.
 *
 * Keduanya dulu ditumpuk dalam satu form, dan yang "buat baru" tersembunyi di
 * kaki halaman sesudah produk dipilih. Di web keduanya memang menu terpisah
 * (`AdminStokSerialInputPage.tsx` + `SerialGeneratePage.tsx`).
 *
 * Registry inilah yang jadi bahan verifikasi lapangan: petugas cabang men-scan
 * barcode tiap unit saat opname, dan server menolak serial yang sama dua kali
 * dalam satu sesi (`duplikat_dalam_sesi`). Produk yang SN-nya belum ditetapkan
 * di sini tak bisa diverifikasi sama sekali di sana — karena itu daftar produk
 * membawa badge cakupan + filter "Belum lengkap", bukan cuma kotak pencarian.
 */
@Composable
fun SerialInputScreen(
    onBack: () -> Unit,
    viewModel: SerialInputViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    val mode = state.mode
    if (mode == null) {
        ModeChooserScreen(onBack = onBack, onChoose = viewModel::chooseMode)
        return
    }

    if (state.selected != null) {
        when (mode) {
            SerialInputMode.TETAPKAN -> TetapkanFormScreen(
                state = state,
                onEntriChange = viewModel::onEntriChange,
                onTambah = viewModel::tambahEntri,
                onHapus = viewModel::hapusEntri,
                onKondisiBerikutnyaChange = viewModel::onKondisiBerikutnyaChange,
                onKeteranganBerikutnyaChange = viewModel::onKeteranganBerikutnyaChange,
                onBukaPemilihKondisi = viewModel::bukaPemilihKondisi,
                onTutupPemilihKondisi = viewModel::tutupPemilihKondisi,
                onSetKondisiUnit = viewModel::setKondisiUnit,
                onBukaDetail = viewModel::bukaDetailUnit,
                onTutupDetail = viewModel::tutupDetailUnit,
                onSimpanKondisiTercatat = viewModel::simpanKondisiUnit,
                onSave = viewModel::save,
                onBack = viewModel::clearSelection
            )
            SerialInputMode.BUAT_BARU -> BuatBaruFormScreen(
                state = state,
                onGenerateCountChange = viewModel::onGenerateCountChange,
                onMintaKonfirmasi = viewModel::mintaKonfirmasiGenerate,
                onBatalKonfirmasi = viewModel::batalkanKonfirmasiGenerate,
                onGenerate = viewModel::generateSerials,
                onBack = viewModel::clearSelection
            )
        }
        return
    }

    ProductPickerScreen(
        state = state,
        mode = mode,
        onBack = viewModel::clearMode,
        onSearchChange = viewModel::onSearchChange,
        onFilterChange = viewModel::onFilterChange,
        onCabangChange = viewModel::changeCabang,
        onRefresh = viewModel::refreshStok,
        onRetry = viewModel::retry,
        onSelect = viewModel::selectProduct
    )
}

// ── Layar pilihan ────────────────────────────────────────────────────────────

@Composable
private fun ModeChooserScreen(onBack: () -> Unit, onChoose: (SerialInputMode) -> Unit) {
    TridjayaCollapsibleHeader(title = "Input Serial Number", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navBottom)
        ) {
            Text(
                text = "Pilih pekerjaan yang mau dilakukan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ModeCard(
                icon = Icons.Rounded.Numbers,
                judul = SerialInputMode.TETAPKAN.judul,
                keterangan = "Barang sudah punya nomor seri pabrik — daftarkan SN-nya " +
                    "ke produk yang belum memilikinya, satu per baris.",
                onClick = { onChoose(SerialInputMode.TETAPKAN) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModeCard(
                icon = Icons.Rounded.QrCode2,
                judul = SerialInputMode.BUAT_BARU.judul,
                keterangan = "Barang tanpa nomor seri pabrik (sofa, kursi) — sistem membuat " +
                    "kode pengganti, labelnya dicetak lewat web lalu ditempel ke tiap unit.",
                onClick = { onChoose(SerialInputMode.BUAT_BARU) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Setelah SN terdaftar, petugas cabang memverifikasi barangnya dengan " +
                    "scan barcode saat stok opname — satu unit hanya terhitung sekali per sesi.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    judul: String,
    keterangan: String,
    onClick: () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = judul, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = keterangan,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Pemilih produk (dipakai kedua mode) ──────────────────────────────────────

@Composable
private fun ProductPickerScreen(
    state: SerialInputUiState,
    mode: SerialInputMode,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (FilterKelengkapan) -> Unit,
    onCabangChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (StokCabangRow) -> Unit
) {
    // State-swap di dalam route ini (bukan nav destination sendiri) — pola sama
    // PayrollScreen/IndentDetailScreen.
    BackHandler(onBack = onBack)

    // Daftar stok satu cabang bisa ribuan baris dan layar ini punya empat sumber
    // recomposition (ketikan, chip, pull-refresh, hasil simpan). Tanpa `remember`
    // seluruh daftar disaring ULANG tiap ketukan huruf; statusnya juga dihitung
    // sekali di sini, bukan dua kali (sekali menyaring, sekali menggambar badge).
    val filtered = remember(
        state.items,
        state.search,
        state.filter,
        state.coverage,
        state.coverageTruncated,
        state.coverageError
    ) {
        val query = state.search.trim()
        state.items.mapNotNull { row ->
            val cocokTeks = query.isBlank() ||
                row.kode.contains(query, ignoreCase = true) ||
                row.nama.contains(query, ignoreCase = true)
            if (!cocokTeks) return@mapNotNull null
            val status = kelengkapanSerial(
                kodeBarang = row.kode,
                stok = row.stok ?: 0,
                coverage = state.coverage,
                truncated = state.coverageTruncated,
                coverageGagal = state.coverageError != null
            )
            if (lolosFilterKelengkapan(status, state.filter)) row to status else null
        }
    }

    TridjayaCollapsibleHeader(title = mode.judul, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            Text(
                text = when (mode) {
                    SerialInputMode.TETAPKAN -> "Pilih cabang & produk, lalu masukkan serial number satu per baris."
                    SerialInputMode.BUAT_BARU -> "Pilih cabang & produk, lalu tentukan berapa kode pengganti yang dibuat."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CabangSelector(
                    selected = state.dealerCode.orEmpty(),
                    onSelect = onCabangChange,
                    label = "Cabang"
                )
            }
            ExpressiveTextField(
                value = state.search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = "Cari kode atau nama produk"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterKelengkapan.entries.forEach { pilihan ->
                    FilterChip(
                        selected = state.filter == pilihan,
                        onClick = { onFilterChange(pilihan) },
                        label = { Text(pilihan.label) }
                    )
                }
            }

            CoverageNotice(state = state)

            TridjayaPullRefresh(
                isRefreshing = state.itemsLoading && state.items.isNotEmpty(),
                onRefresh = onRefresh
            ) {
                when {
                    (state.loadingContext || state.itemsLoading) && state.items.isEmpty() -> {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            repeat(6) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                        }
                    }
                    state.contextError != null && state.items.isEmpty() -> {
                        ScrollableCenter {
                            ExpressiveErrorState(message = state.contextError ?: "Gagal memuat", onRetry = onRetry)
                        }
                    }
                    filtered.isEmpty() -> {
                        ScrollableCenter {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.Numbers, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Produk tidak ditemukan",
                                subtitle = if (state.filter == FilterKelengkapan.SEMUA) {
                                    "Tidak ada produk stok cabang yang cocok dengan pencarian."
                                } else {
                                    "Tidak ada produk berstatus \"${state.filter.label}\" yang cocok. " +
                                        "Coba filter \"Semua\"."
                                }
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + navBottom),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.first.kode }) { (row, status) ->
                                ProductRow(
                                    row = row,
                                    status = status,
                                    snTercatat = state.coverage[row.kode]?.serial,
                                    onClick = { onSelect(row) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kenapa cakupan tak bisa dipercaya WAJIB kelihatan: badge "?" tanpa alasan
 * terbaca sebagai bug, dan admin yang tak tahu petanya bolong akan mendaftarkan
 * ulang SN yang sebenarnya sudah ada.
 */
@Composable
private fun CoverageNotice(state: SerialInputUiState) {
    val pesan = when {
        state.coverageError != null ->
            "⚠️ Cakupan SN gagal dimuat (${state.coverageError}). Status per produk tak bisa " +
                "dipastikan — periksa dulu SN tercatat sebelum mendaftarkan ulang."
        state.coverageTruncated ->
            "⚠️ Cakupan dipotong di batas server — produk tanpa badge belum tentu nol SN."
        else -> null
    } ?: return

    Text(
        text = pesan,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ProductRow(
    row: StokCabangRow,
    status: Kelengkapan,
    snTercatat: Int?,
    onClick: () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.nama.ifBlank { row.kode }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "${row.kode} · Stok: ${row.stok ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            KelengkapanBadge(status = status, snTercatat = snTercatat, stok = row.stok ?: 0)
        }
    }
}

@Composable
private fun KelengkapanBadge(status: Kelengkapan, snTercatat: Int?, stok: Int) {
    val (teks, warna) = when (status) {
        // `snTercatat` bisa null saat cakupan tak termuat — angkanya diganti "?"
        // alih-alih 0, karena "0" adalah vonis yang justru tak boleh diambil.
        Kelengkapan.LENGKAP -> "SN ${snTercatat ?: 0}/$stok" to MaterialTheme.colorScheme.primary
        Kelengkapan.BELUM -> "SN ${snTercatat ?: 0}/$stok" to MaterialTheme.colorScheme.error
        Kelengkapan.TAK_DIKETAHUI -> "SN ?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = warna.copy(alpha = 0.12f),
        contentColor = warna,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = teks,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Mode 1: tetapkan SN pabrik ───────────────────────────────────────────────

@Composable
private fun TetapkanFormScreen(
    state: SerialInputUiState,
    onEntriChange: (String) -> Unit,
    onTambah: (String) -> Unit,
    onHapus: (String) -> Unit,
    onKondisiBerikutnyaChange: (String?) -> Unit,
    onKeteranganBerikutnyaChange: (String) -> Unit,
    onBukaPemilihKondisi: (String) -> Unit,
    onTutupPemilihKondisi: () -> Unit,
    onSetKondisiUnit: (String, String?, String?) -> Unit,
    onBukaDetail: (String) -> Unit,
    onTutupDetail: () -> Unit,
    onSimpanKondisiTercatat: (String, String?, String?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val product = state.selected ?: return
    val stok = product.stok ?: 0
    val remaining = (stok - state.existingCount).coerceAtLeast(0)
    val daftar = state.daftar
    val countMismatch = daftar.isNotEmpty() && daftar.size != remaining

    state.kondisiUntukUnit?.let { serial ->
        val unit = daftar.firstOrNull { it.serial == serial }
        if (unit != null) {
            PemilihKondisiDialog(
                unit = unit,
                onTutup = onTutupPemilihKondisi,
                onPilih = { kondisi, keterangan -> onSetKondisiUnit(serial, kondisi, keterangan) }
            )
        }
    }

    state.detailSerial?.let { serial ->
        val baris = state.tercatat.firstOrNull { it.serialNumber == serial }
        if (baris != null) {
            DetailUnitTercatatDialog(
                baris = baris,
                state = state,
                onTutup = onTutupDetail,
                onSimpan = { kondisi, keterangan -> onSimpanKondisiTercatat(serial, kondisi, keterangan) }
            )
        }
    }

    TridjayaCollapsibleHeader(title = SerialInputMode.TETAPKAN.judul, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navBottom)
        ) {
            ProductSummaryCard(state = state, product = product, stok = stok, remaining = remaining)

            Spacer(modifier = Modifier.height(12.dp))

            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Scan barcode tiap unit, atau ketik serialnya kalau barcode-nya rusak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveTextField(
                            value = state.entri,
                            onValueChange = onEntriChange,
                            modifier = Modifier.weight(1f),
                            placeholder = "Serial number unit",
                            // Scan masuk lewat PINTU YANG SAMA dengan tombol Tambah
                            // supaya aturan normalisasi & duplikat tak bercabang.
                            trailingIcon = { BarcodeScanButton(contentDescription = "Scan serial unit") { onTambah(it) } }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExpressiveFilledButton(
                            onClick = { onTambah(state.entri) },
                            enabled = state.entri.isNotBlank()
                        ) {
                            Text("Tambah")
                        }
                    }
                    state.entriError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Status untuk unit berikutnya",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Menempel ke tiap unit yang discan sesudah ini, sampai diganti. " +
                            "Bisa dikoreksi per unit lewat daftar di bawah.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Belum ditetapkan" adalah pilihan SAH dan defaultnya —
                        // NULL di registry berarti "tak ada pembanding" saat opname
                        // membandingkan vonis, bukan "layak".
                        FilterChip(
                            selected = state.kondisiBerikutnya == null,
                            onClick = { onKondisiBerikutnyaChange(null) },
                            label = { Text("Belum ditetapkan") }
                        )
                        KONDISI_PILIHAN.forEach { pilihan ->
                            FilterChip(
                                selected = state.kondisiBerikutnya == pilihan,
                                onClick = { onKondisiBerikutnyaChange(pilihan) },
                                label = { Text(kondisiLabel(pilihan)) }
                            )
                        }
                    }
                    if (kondisiPakaiKeterangan(state.kondisiBerikutnya)) {
                        ExpressiveTextField(
                            value = state.keteranganBerikutnya,
                            onValueChange = onKeteranganBerikutnyaChange,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            placeholder = "Keterangan (mis. layar retak)"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (daftar.isEmpty()) {
                Text(
                    text = "Belum ada unit dimasukkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${daftar.size} unit siap disimpan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Column biasa, BUKAN LazyColumn: seluruh form ini sudah di dalam
                // `verticalScroll`, dan menyarangkan dua scroller sejenis melempar
                // IllegalStateException saat dirender.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    daftar.forEachIndexed { index, unit ->
                        ClayCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .clickable { onBukaPemilihKondisi(unit.serial) }
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = unit.serial, style = MaterialTheme.typography.bodyMedium)
                                    unit.keterangan?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                KondisiBadge(kondisi = unit.kondisi)
                                IconButton(onClick = { onHapus(unit.serial) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Hapus ${unit.serial} dari daftar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (countMismatch) {
                Text(
                    text = "⚠️ Jumlah unit (${daftar.size}) tak sama dengan sisa kebutuhan ($remaining) — " +
                        "stok GS mungkin sudah berubah. Tetap bisa disimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            state.formError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            state.result?.let { result ->
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "${result.inserted} serial number berhasil disimpan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (result.skipped.isNotEmpty()) {
                        Text(
                            text = "${result.skipped.size} dilewati: " +
                                result.skipped.joinToString(", ") { "${it.serialNumber} (${it.reason.ifBlank { "dilewati" }})" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (state.kondisiUpdated > 0) {
                        Text(
                            text = "Status ${state.kondisiUpdated} unit ikut tersimpan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Serial SUDAH masuk registry walau vonisnya gagal — itu wajib
            // dikatakan, kalau tidak petugas mengira seluruh simpanannya batal
            // lalu mengulang dari nol.
            state.kondisiError?.let {
                Text(
                    text = "⚠️ Serial sudah tersimpan, tapi statusnya belum: $it\n" +
                        "Tekan Simpan lagi untuk mencoba ulang — serial yang sudah ada akan dilewati.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SnTercatatSection(state = state, onBukaDetail = onBukaDetail)

            Spacer(modifier = Modifier.height(16.dp))

            ExpressiveFilledButton(
                onClick = onSave,
                enabled = !state.saving && daftar.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Simpan ${daftar.size} Serial Number")
                }
            }
        }
    }
}

/**
 * Vonis kondisi satu unit. "Belum" ditampilkan NETRAL, bukan hijau: ia bukan
 * kabar baik maupun buruk, melainkan pernyataan bahwa belum ada yang memutuskan.
 * Mewarnainya seperti "Layak" akan membuat unit tanpa vonis terbaca sebagai unit
 * yang sudah diperiksa dan lolos.
 */
@Composable
private fun KondisiBadge(kondisi: String?) {
    val (teks, warna) = when (kondisi) {
        null -> "Belum" to MaterialTheme.colorScheme.onSurfaceVariant
        KONDISI_LAYAK -> kondisiLabel(kondisi) to MaterialTheme.colorScheme.primary
        else -> kondisiLabel(kondisi) to MaterialTheme.colorScheme.error
    }
    Surface(
        color = warna.copy(alpha = 0.12f),
        contentColor = warna,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = teks,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Koreksi vonis SATU unit yang sudah masuk daftar — untuk salah tekan chip lengket. */
@Composable
private fun PemilihKondisiDialog(
    unit: UnitEntri,
    onTutup: () -> Unit,
    onPilih: (String?, String?) -> Unit
) {
    var kondisi by remember(unit.serial) { mutableStateOf(unit.kondisi) }
    var keterangan by remember(unit.serial) { mutableStateOf(unit.keterangan.orEmpty()) }

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Status ${unit.serial}") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = kondisi == null,
                        onClick = { kondisi = null },
                        label = { Text("Belum") }
                    )
                    KONDISI_PILIHAN.forEach { pilihan ->
                        FilterChip(
                            selected = kondisi == pilihan,
                            onClick = { kondisi = pilihan },
                            label = { Text(kondisiLabel(pilihan)) }
                        )
                    }
                }
                if (kondisiPakaiKeterangan(kondisi)) {
                    ExpressiveTextField(
                        value = keterangan,
                        onValueChange = { keterangan = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        placeholder = "Keterangan (mis. layar retak)"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPilih(kondisi, keterangan.takeIf { kondisiPakaiKeterangan(kondisi) })
            }) { Text("Simpan status") }
        },
        dismissButton = { TextButton(onClick = onTutup) { Text("Batal") } }
    )
}

/**
 * Daftar unit yang SUDAH tercatat di registry produk ini, lengkap dengan jejak
 * siapa/kapan menetapkan kondisinya dan dari mana barisnya masuk.
 *
 * Ada supaya penetapan SN bisa DIEVALUASI, bukan cuma ditumpuk: sebelum ini,
 * satu-satunya angka yang terlihat adalah "SN tercatat: 3" — tak ada cara
 * memeriksa unit mana saja, siapa memutuskan kondisinya, atau apakah vonisnya
 * masih benar.
 */
@Composable
private fun SnTercatatSection(state: SerialInputUiState, onBukaDetail: (String) -> Unit) {
    Text(
        text = "SN sudah tercatat (${state.tercatat.size})",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    if (state.existingLoading) {
        Text(
            text = "Memuat…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        return
    }
    if (state.tercatat.isEmpty()) {
        Text(
            text = "Belum ada satu pun unit produk ini yang terdaftar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        return
    }
    Text(
        text = "Ketuk satu unit untuk mengubah kondisi/keterangan dan melihat riwayatnya.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.tercatat.forEach { baris ->
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .clickable { onBukaDetail(baris.serialNumber) }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = baris.serialNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        // Tag leasing ditandai TERPISAH dari kondisi: ia bukan unit
                        // fisik, jadi memberinya badge kondisi akan menyarankan ada
                        // barang yang bisa diperiksa.
                        if (!baris.isSerial) {
                            Text(
                                text = "tag leasing",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            KondisiBadge(kondisi = baris.kondisi)
                        }
                    }
                    baris.kondisiKeterangan?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        text = jejakUnit(baris),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** Panel evaluasi satu unit terdaftar: ubah vonis + baca riwayat perubahannya. */
@Composable
private fun DetailUnitTercatatDialog(
    baris: SerialRegistryRow,
    state: SerialInputUiState,
    onTutup: () -> Unit,
    onSimpan: (String?, String?) -> Unit
) {
    var kondisi by remember(baris.serialNumber) { mutableStateOf(baris.kondisi) }
    var keterangan by remember(baris.serialNumber) { mutableStateOf(baris.kondisiKeterangan.orEmpty()) }

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text(baris.serialNumber) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = jejakUnit(baris),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KONDISI_PILIHAN.forEach { pilihan ->
                        FilterChip(
                            selected = kondisi == pilihan,
                            onClick = { kondisi = pilihan },
                            label = { Text(kondisiLabel(pilihan)) }
                        )
                    }
                }
                if (kondisiPakaiKeterangan(kondisi)) {
                    ExpressiveTextField(
                        value = keterangan,
                        onValueChange = { keterangan = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        placeholder = "Keterangan (mis. layar retak)"
                    )
                }
                state.detailError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Riwayat perubahan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                when {
                    state.riwayatLoading -> Text(
                        text = "Memuat riwayat…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Riwayat gagal TIDAK memblokir penyuntingan — ia alat baca,
                    // dan menutup tombol simpan karenanya akan menghentikan
                    // pekerjaan atas alasan yang tak ada hubungannya.
                    state.riwayatError != null -> Text(
                        text = "Riwayat gagal dimuat: ${state.riwayatError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    state.riwayat.isEmpty() -> Text(
                        text = "Belum pernah diubah sejak didaftarkan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.riwayat.forEach { log ->
                            val dari = log.kondisiLama?.let { kondisiLabel(it) } ?: "belum ditetapkan"
                            Text(
                                text = "$dari → ${kondisiLabel(log.kondisiBaru)}" +
                                    (log.keterangan?.takeIf { it.isNotBlank() }?.let { " · \"$it\"" } ?: "") +
                                    "\n${log.changedByName ?: "tak diketahui"}" +
                                    (log.changedAt?.let { " · ${waktuSingkat(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.riwayatTruncated) {
                            Text(
                                text = "Masih ada perubahan lebih lama yang tak ditampilkan.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(kondisi, keterangan.takeIf { kondisiPakaiKeterangan(kondisi) }) },
                enabled = !state.detailSaving && baris.isSerial
            ) { Text(if (state.detailSaving) "Menyimpan…" else "Simpan kondisi") }
        },
        dismissButton = { TextButton(onClick = onTutup) { Text("Tutup") } }
    )
}

// ── Mode 2: buat kode pengganti (GEN-) ───────────────────────────────────────

@Composable
private fun BuatBaruFormScreen(
    state: SerialInputUiState,
    onGenerateCountChange: (String) -> Unit,
    onMintaKonfirmasi: () -> Unit,
    onBatalKonfirmasi: () -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val product = state.selected ?: return
    val stok = product.stok ?: 0
    val remaining = (stok - state.existingCount).coerceAtLeast(0)

    // Konfirmasi WAJIB: tombolnya menulis registry seketika dan app tak punya
    // cara membatalkannya — kode yang telanjur dibuat hanya bisa dihapus lewat
    // DB. Dialognya menyebut nama barangnya, karena kesalahan yang sudah terjadi
    // (2026-08-10) bukan salah jumlah melainkan salah PRODUK.
    if (state.konfirmasiGenerate) {
        AlertDialog(
            onDismissRequest = onBatalKonfirmasi,
            title = { Text("Buat kode untuk barang ini?") },
            text = {
                Text(
                    "${state.generateCount} kode GEN- akan dibuat untuk " +
                        "${product.nama.ifBlank { product.kode }} (${product.kode}) dan LANGSUNG " +
                        "tercatat di registry. Tidak bisa dibatalkan dari app.\n\n" +
                        "Pakai ini hanya untuk barang yang memang tak punya nomor seri pabrik. " +
                        "Kalau barangnya sudah bernomor, pakai menu Tetapkan SN ke Produk."
                )
            },
            confirmButton = { TextButton(onClick = onGenerate) { Text("Ya, buat kode") } },
            dismissButton = { TextButton(onClick = onBatalKonfirmasi) { Text("Batal") } }
        )
    }

    TridjayaCollapsibleHeader(title = SerialInputMode.BUAT_BARU.judul, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navBottom)
        ) {
            ProductSummaryCard(state = state, product = product, stok = stok, remaining = remaining)

            Spacer(modifier = Modifier.height(12.dp))

            // Barang tanpa serial pabrik (sofa, kursi): kodenya dibuat sistem,
            // ditempel ke unitnya, lalu discan seperti serial biasa saat opname.
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Buat kode pengganti (GEN-…) lalu tempel labelnya ke tiap unit. " +
                            "Kode langsung tercatat di registry begitu dibuat — menekan tombol ini " +
                            "dua kali berarti dua set kode nyata untuk barang yang sama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveTextField(
                            value = state.generateCount,
                            onValueChange = onGenerateCountChange,
                            modifier = Modifier.weight(1f),
                            placeholder = "Jumlah unit",
                            keyboardType = KeyboardType.Number
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExpressiveFilledButton(
                            onClick = onMintaKonfirmasi,
                            enabled = !state.generating && state.generateCount.isNotBlank()
                        ) {
                            if (state.generating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Buat Kode")
                            }
                        }
                    }
                }
            }

            state.formError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.generated.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "${state.generated.size} kode dibuat:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = state.generated.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "Cetak labelnya lewat web (menu Buat Kode Serial) — HP tak terhubung printer label.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSummaryCard(
    state: SerialInputUiState,
    product: StokCabangRow,
    stok: Int,
    remaining: Int
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = product.nama.ifBlank { product.kode }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = product.kode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Stok: $stok · SN tercatat: ${if (state.existingLoading) "…" else state.existingCount} · " +
                    "Butuh lagi: $remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Tanpa baris ini, "SN tercatat" yang lebih kecil dari isi registry
            // terbaca sebagai hitungan yang salah — padahal tag leasing memang
            // sengaja tak dihitung sebagai unit fisik.
            if (!state.existingLoading && state.tagLeasingCount > 0) {
                Text(
                    text = "Registry juga memuat ${state.tagLeasingCount} tag leasing — tidak dihitung sebagai unit.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
