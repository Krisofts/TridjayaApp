package com.krisoft.tridjayaelektronik.ui.homeservice

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.HsRingkasTransaksiDto
import com.krisoft.tridjayaelektronik.data.model.HsTransaksiItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.io.File

/**
 * Lapor komplain — membuat tiket Home Service dari lapangan.
 *
 * Urutannya sengaja sama dengan web: cari konsumen (nama/HP) → pilih transaksi
 * → pilih barang → foto kwitansi → keluhan. Nomor transaksi GS praktis tak
 * pernah dihafal orang lapangan, jadi pencarian konsumen adalah pintu utama,
 * bukan pelengkap.
 */
@Composable
fun HomeServiceLaporScreen(
    onBack: () -> Unit,
    onLihatTiket: (String) -> Unit,
    viewModel: HomeServiceLaporViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val fileKwitansi = remember {
        File(context.cacheDir, "home-service").apply { mkdirs() }.let { File(it, "kwitansi.jpg") }
    }
    val uriKwitansi = remember(fileKwitansi) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileKwitansi)
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.unggahKwitansi(fileKwitansi)
    }

    TridjayaCollapsibleHeader(title = "Lapor Komplain", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tiket = state.tiketJadi
            if (tiket != null) {
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Tiket ${tiket.nomorTiket} dibuat",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildString {
                                append(tiket.namaBarang?.takeIf { it.isNotBlank() } ?: tiket.noTransaksi)
                                // Status garansi DIHITUNG SERVER dari tanggal beli —
                                // ditampilkan, bukan diisi pelapor.
                                when (tiket.dalamGaransi) {
                                    true -> append(" · masih garansi")
                                    false -> append(" · di luar garansi")
                                    null -> Unit
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExpressiveFilledButton(
                                onClick = { onLihatTiket(tiket.id) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Lihat tiket") }
                            ExpressiveOutlinedButton(
                                onClick = { viewModel.laporLagi() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Lapor lagi") }
                        }
                    }
                }
                return@Column
            }

            state.error?.let { pesan ->
                Text(pesan, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }

            if (state.noTransaksi == null) {
                SeksiCari(
                    nama = state.cariNama,
                    hp = state.cariHp,
                    mencari = state.mencari,
                    sudahMencari = state.sudahMencari,
                    hasil = state.hasilCari,
                    onNama = viewModel::ketikNama,
                    onHp = viewModel::ketikHp,
                    onCari = viewModel::cari,
                    onPilih = viewModel::pilihTransaksi,
                )
                return@Column
            }

            SeksiTransaksi(
                noTransaksi = state.noTransaksi.orEmpty(),
                memuat = state.memuatRincian,
                barang = state.barang,
                dipilih = state.barangDipilih,
                serial = state.kontak.serialNumber,
                onPilih = viewModel::pilihBarang,
                onGanti = viewModel::gantiTransaksi,
            )

            KartuKwitansi(
                url = state.fotoKwitansiUrl,
                mengunggah = state.mengunggah,
                onJepret = { kamera.launch(uriKwitansi) },
            )

            OutlinedTextField(
                value = state.deskripsi,
                onValueChange = viewModel::ketikDeskripsi,
                label = { Text("Keluhan konsumen") },
                placeholder = { Text("Contoh: mesin cuci tidak berputar sejak kemarin") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("normal" to "Normal", "mendesak" to "Mendesak").forEach { (kunci, label) ->
                    FilterChip(
                        selected = state.prioritas == kunci,
                        onClick = { viewModel.pilihPrioritas(kunci) },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                "Kontak konsumen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Isian ini menang atas data hasil pengayaan SPK di server, jadi
                // perlu dijelaskan — bukan sekadar "opsional".
                "Terisi otomatis dari data transaksi bila ada. Yang kamu ketik di sini yang dipakai.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.customerNama,
                onValueChange = viewModel::ketikCustomerNama,
                label = { Text("Nama") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.customerHp,
                onValueChange = viewModel::ketikCustomerHp,
                label = { Text("Nomor HP") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.customerAlamat,
                onValueChange = viewModel::ketikCustomerAlamat,
                label = { Text("Alamat") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            val gate = bolehBuatTiket(state.noTransaksi, state.fotoKwitansiUrl, state.deskripsi)
            if (!gate.ok) {
                Text(
                    gate.alasan.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpressiveFilledButton(
                onClick = { viewModel.kirim() },
                enabled = gate.ok && !state.mengirim,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.mengirim) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.mengirim) "Mengirim…" else "Kirim komplain")
            }
        }
    }
}

@Composable
private fun SeksiCari(
    nama: String,
    hp: String,
    mencari: Boolean,
    sudahMencari: Boolean,
    hasil: List<HsRingkasTransaksiDto>,
    onNama: (String) -> Unit,
    onHp: (String) -> Unit,
    onCari: () -> Unit,
    onPilih: (String) -> Unit,
) {
    Text("Cari transaksi konsumen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = nama,
        onValueChange = onNama,
        label = { Text("Nama konsumen") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = hp,
        onValueChange = onHp,
        label = { Text("Nomor HP") },
        modifier = Modifier.fillMaxWidth(),
    )
    ExpressiveFilledButton(
        onClick = onCari,
        enabled = !mencari,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (mencari) "Mencari…" else "Cari")
    }

    if (sudahMencari && hasil.isEmpty() && !mencari) {
        Text(
            "Transaksi tak ditemukan. Coba nomor HP lain, atau ejaan nama yang berbeda.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    hasil.forEach { transaksi ->
        ClayCard(modifier = Modifier.fillMaxWidth().clickable { onPilih(transaksi.noTransaksi) }) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    transaksi.customerNama?.takeIf { it.isNotBlank() } ?: "(tanpa nama)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        append(transaksi.noTransaksi)
                        transaksi.tanggal?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        append(" · ${transaksi.jumlahItem} barang")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                transaksi.contohBarang?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SeksiTransaksi(
    noTransaksi: String,
    memuat: Boolean,
    barang: List<HsTransaksiItemDto>,
    dipilih: HsTransaksiItemDto?,
    serial: String?,
    onPilih: (HsTransaksiItemDto) -> Unit,
    onGanti: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(noTransaksi, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            serial?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Serial: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExpressiveOutlinedButton(onClick = onGanti) { Text("Ganti") }
    }

    if (memuat) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        return
    }
    if (barang.size > 1) {
        Text(
            "Pilih barang yang dikomplain",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    barang.forEach { item ->
        val terpilih = dipilih?.baris == item.baris
        Surface(
            onClick = { onPilih(item) },
            shape = RoundedCornerShape(14.dp),
            color = if (terpilih) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    item.namaBarang?.takeIf { it.isNotBlank() } ?: item.kodeBarang.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (terpilih) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    item.kodeBarang.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KartuKwitansi(url: String?, mengunggah: Boolean, onJepret: () -> Unit) {
    ExpressiveOutlinedButton(
        onClick = onJepret,
        enabled = !mengunggah,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (mengunggah) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                mengunggah -> "Mengunggah…"
                url != null -> "Kwitansi terunggah — foto ulang"
                else -> "Foto kwitansi (wajib)"
            }
        )
    }
}
