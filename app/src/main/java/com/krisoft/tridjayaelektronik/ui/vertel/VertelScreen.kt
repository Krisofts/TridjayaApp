package com.krisoft.tridjayaelektronik.ui.vertel

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.VertelBarisDto
import com.krisoft.tridjayaelektronik.data.model.VertelRingkasanDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * VERTEL — verifikasi telepon konsumen yang bertransaksi kemarin.
 *
 * Alurnya: baca daftar → tekan Telepon / WA (dibuka aplikasi HP sendiri) →
 * catat hasilnya. **Tak ada integrasi API WhatsApp**; `tel:` dan `wa.me`
 * diserahkan ke aplikasi yang memang sudah dipakai verifikator, jadi tak ada
 * nomor konsumen yang dikirim ke layanan mana pun di luar itu.
 */
@Composable
fun VertelScreen(
    onBack: () -> Unit,
    viewModel: VertelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    VertelIsi(
        state = state,
        onBack = onBack,
        onRefresh = { viewModel.muat() },
        onBersihkanGalat = { viewModel.bersihkanActionError() },
        onBuka = { uri ->
            // `tel:`/`wa.me` ditangani aplikasi lain. HP tanpa dialer atau tanpa
            // WhatsApp melempar ActivityNotFoundException — itu bukan crash yang
            // pantas, cukup beri tahu.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                .onFailure { e ->
                    val pesan = if (e is ActivityNotFoundException) {
                        "Tak ada aplikasi yang bisa membuka tautan ini."
                    } else {
                        "Gagal membuka tautan."
                    }
                    Toast.makeText(context, pesan, Toast.LENGTH_SHORT).show()
                }
        },
        onCatat = { baris, kanal, hasil, komplain, catatan, selesai ->
            viewModel.catat(baris, kanal, hasil, komplain, catatan, selesai)
        },
    )
}

@Composable
private fun VertelIsi(
    state: VertelUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBersihkanGalat: () -> Unit,
    onBuka: (String) -> Unit,
    onCatat: (VertelBarisDto, String, String, Boolean, String, () -> Unit) -> Unit,
) {
    var dialogUntuk by remember { mutableStateOf<VertelBarisDto?>(null) }

    dialogUntuk?.let { baris ->
        DialogCatat(
            baris = baris,
            submitting = state.submitting,
            onDismiss = { dialogUntuk = null },
            onSimpan = { kanal, hasil, komplain, catatan ->
                onCatat(baris, kanal, hasil, komplain, catatan) { dialogUntuk = null }
            },
        )
    }

    TridjayaCollapsibleHeader(title = "Verifikasi Telepon", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val data = state.data
        TridjayaPullRefresh(
            isRefreshing = state.loading && data != null,
            onRefresh = onRefresh,
            modifier = contentModifier,
        ) {
            when {
                state.loading && data == null -> ScrollableCenter { CircularProgressIndicator() }

                state.error != null && data == null -> ScrollableCenter {
                    ExpressiveErrorState(
                        message = state.error ?: "Gagal memuat daftar verifikasi.",
                        onRetry = onRefresh,
                    )
                }

                data == null || data.baris.isEmpty() -> ScrollableCenter {
                    ExpressiveEmptyState(
                        icon = {
                            Icon(
                                Icons.Rounded.PhoneInTalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                        },
                        title = "Tak ada transaksi untuk diverifikasi.",
                        // Kalimat kedua penting: daftar ini hanya selengkap
                        // worker mirror `erp_mirror_sales`, dan mirror yang basi
                        // tampil sebagai layar kosong TANPA error apa pun.
                        subtitle = "Kalau kamu yakin kemarin ada penjualan, mungkin data " +
                            "penjualan dari GS belum tersalin — laporkan ke admin.",
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "ringkasan") { KartuRingkasan(data.tanggal, data.ringkasan) }
                    state.actionError?.let { pesan ->
                        item(key = "galat") {
                            Text(
                                pesan,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable { onBersihkanGalat() },
                            )
                        }
                    }
                    items(data.baris, key = { it.noTransaksi + "|" + it.tanggal }) { baris ->
                        KartuBaris(
                            baris = baris,
                            onTelepon = { telUri(baris)?.let(onBuka) },
                            onWa = { waUri(baris)?.let(onBuka) },
                            onCatat = { dialogUntuk = baris },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuRingkasan(tanggal: String, r: VertelRingkasanDto) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // Tanggalnya dipajang APA ADANYA dari server. App tak pernah
            // menghitung "kemarin" sendiri — lihat [VertelViewModel].
            Text("Transaksi $tanggal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "${r.sudahDitelepon} dari ${r.total} sudah dicatat · ${sisaVertel(r)} sisa",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Terhubung ${r.terhubung} · Komplain ${r.adaKomplain} · Tanpa nomor WA ${r.tanpaNomor}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KartuBaris(
    baris: VertelBarisDto,
    onTelepon: () -> Unit,
    onWa: () -> Unit,
    onCatat: () -> Unit,
) {
    val tel = telUri(baris)
    val wa = waUri(baris)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    baris.customerNama ?: "(tanpa nama)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                baris.panggilan?.let { p ->
                    Text(
                        labelHasil(p.hasil) ?: p.hasil,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${baris.noTransaksi} · ${baris.cabangNama ?: baris.kodeDealer ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(baris.barang, style = MaterialTheme.typography.bodySmall)
            baris.customerHp?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }

            baris.panggilan?.let { p ->
                Spacer(Modifier.height(6.dp))
                val oleh = p.olehNama?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                Text(
                    "Dicatat lewat ${labelKanal(p.kanal) ?: p.kanal}$oleh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (p.adaKomplain) {
                    Text(
                        "KOMPLAIN: ${p.catatan.orEmpty()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                // Tombol telepon memakai nomor MENTAH, jadi ia tetap ada untuk
                // nomor rumah/kantor yang `waNumber`-nya null. Tombol WA hanya
                // muncul kalau SERVER menyatakan nomornya layak — nomor rusak
                // tak pernah ditautkan.
                if (tel != null) {
                    AssistChip(
                        onClick = onTelepon,
                        label = { Text("Telepon") },
                        leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                if (wa != null) {
                    AssistChip(
                        onClick = onWa,
                        label = { Text("WhatsApp") },
                        leadingIcon = { Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            if (tel == null && wa == null) {
                Spacer(Modifier.height(4.dp))
                // Bukan kelalaian verifikator — dan ia tetap perlu bisa menutup
                // barisnya (catat `nomor_salah`), jadi tombol Catat tetap ada.
                Text(
                    "Tak ada nomor yang bisa dihubungi.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCatat, modifier = Modifier.fillMaxWidth()) {
                // Mencatat ulang adalah UPSERT di server, jadi ini memang
                // "perbarui", bukan "tambah baris kedua".
                Text(if (sudahDicatat(baris)) "Perbarui hasil" else "Catat hasil")
            }
        }
    }
}

@Composable
private fun DialogCatat(
    baris: VertelBarisDto,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSimpan: (String, String, Boolean, String) -> Unit,
) {
    var kanal by remember(baris.noTransaksi) {
        mutableStateOf(baris.panggilan?.kanal ?: kanalDefault(baris))
    }
    var hasil by remember(baris.noTransaksi) {
        mutableStateOf(baris.panggilan?.hasil)
    }
    var komplain by remember(baris.noTransaksi) {
        mutableStateOf(baris.panggilan?.adaKomplain ?: false)
    }
    var catatan by remember(baris.noTransaksi) {
        mutableStateOf(baris.panggilan?.catatan.orEmpty())
    }
    val gate = vertelCatatGate(kanal, hasil, komplain, catatan)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hasil verifikasi") },
        text = {
            Column {
                Text(
                    "${baris.customerNama ?: "(tanpa nama)"} · ${baris.noTransaksi}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text("Kanal", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KANAL_PILIHAN.forEach { (slug, label) ->
                        FilterChip(selected = kanal == slug, onClick = { kanal = slug }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Hasil", style = MaterialTheme.typography.labelSmall)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HASIL_PILIHAN.chunked(2).forEach { pasangan ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pasangan.forEach { (slug, label) ->
                                FilterChip(
                                    selected = hasil == slug,
                                    onClick = { hasil = slug },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { komplain = !komplain },
                ) {
                    Checkbox(checked = komplain, onCheckedChange = { komplain = it })
                    Text("Konsumen komplain", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text(if (komplain) "Catatan komplain (wajib)" else "Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Kalimat penahannya ditampilkan, bukan cuma tombol yang mati:
                // tombol mati tanpa sebab terbaca sebagai app rusak.
                gate.alasan?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(kanal, hasil.orEmpty(), komplain, catatan) },
                enabled = !submitting && gate.bolehSimpan,
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Batal") } },
    )
}
