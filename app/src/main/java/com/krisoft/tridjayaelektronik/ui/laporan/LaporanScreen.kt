package com.krisoft.tridjayaelektronik.ui.laporan

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.export.LaporanVerifikatorXlsx
import com.krisoft.tridjayaelektronik.ui.deliveryflow.PeriodeFilterRow
import com.krisoft.tridjayaelektronik.ui.deliveryflow.PeriodeSpk
import com.krisoft.tridjayaelektronik.ui.deliveryflow.rentangPeriode
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * "Laporan Verifikator" — ekspor satu berkas `.xlsx` berisi tiga sheet:
 * VERTEL, Home Service, Pemasangan AC.
 *
 * PDI SENGAJA tidak termasuk (keputusan user 2026-08-24); alasan teknisnya
 * ditulis panjang di `LaporanPlan.kt` dan diringkas di kartu layar ini supaya
 * yang membukanya tahu — bukan menduga app-nya lupa.
 */
@Composable
fun LaporanScreen(
    onBack: () -> Unit,
    viewModel: LaporanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var periode by remember { mutableStateOf(PeriodeSpk.HARI_INI) }
    val rentang = rentangPeriode(periode)

    // Bahan siap → tulis workbook lalu buka share sheet. Penulisan berkasnya
    // sendiri ada di IO (lihat exporter), jadi aman dipicu dari sini.
    LaunchedEffect(state.siap) {
        val bahan = state.siap ?: return@LaunchedEffect
        val hasil = runCatching {
            LaporanVerifikatorXlsx.export(
                context = context,
                dari = bahan.dari,
                sampai = bahan.sampai,
                vertel = bahan.vertel,
                vertelTerpotong = bahan.vertelTerpotong,
                homeService = bahan.homeService,
                homeServiceTerpotong = bahan.homeServiceTerpotong,
                pemasanganAc = bahan.pemasanganAc,
                acTerpotong = bahan.acTerpotong,
            )
        }
        hasil.onSuccess { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Kirim laporan"))
        }.onFailure {
            Toast.makeText(context, "Gagal menulis berkas laporan.", Toast.LENGTH_LONG).show()
        }
        viewModel.bersihkan()
    }

    TridjayaCollapsibleHeader(title = "Laporan Verifikator", onBack = onBack) { contentModifier ->
        Column(
            contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PeriodeFilterRow(dipilih = periode, onPilih = { periode = it })

            ClayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Isi laporan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    SumberLaporan.entries.forEach {
                        Text("• ${it.judulSheet} — ${it.label}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Kalimat ini WAJIB ada di layar, bukan cuma di kode: tanpa
                    // itu orang yang memakai laporan menyimpulkan sheet PDI-nya
                    // lupa dibuat, lalu melaporkannya sebagai bug berulang kali.
                    Text(
                        "PDI belum bisa dimasukkan: server membatasi bacaan akun verifikator ke SPK " +
                            "yang ia buat sendiri, sehingga sheet PDI akan selalu kosong dan kosongnya " +
                            "tak bisa dibedakan dari \"memang tidak ada\". Perlu perubahan di server dulu.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "VERTEL diambil per hari, jadi periode yang panjang butuh waktu lebih lama.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.peringatan.takeIf { it.isNotEmpty() }?.let { daftar ->
                ClayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Sebagian data tak terambil",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        daftar.forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            state.kemajuan?.let { k ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    LinearProgressIndicator(progress = { k.persen }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${k.keterangan} (${k.selesai}/${k.total})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.tarik(rentang.dari, rentang.sampai) },
                enabled = !state.menarik,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                if (state.menarik) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(0.dp))
                    Text("  Menarik data…")
                } else {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Buat & kirim Excel")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
