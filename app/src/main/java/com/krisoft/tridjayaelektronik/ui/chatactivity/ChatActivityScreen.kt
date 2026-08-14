package com.krisoft.tridjayaelektronik.ui.chatactivity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AktivitasChatTodayDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.util.bacaInfoBerkas
import kotlinx.coroutines.delay

private val HIJAU = Color(0xFF12B76A)
private val KUNING = Color(0xFFB5670C)
private val MERAH = Color(0xFFF04438)

/**
 * Bukti aktivitas chat harian — karyawan mengirim jumlah chat + video rekaman layar
 * percakapan, yang jadi syarat absen pulang.
 *
 * Video **dipilih dari galeri, bukan direkam di dalam app**: buktinya adalah rekaman layar
 * WhatsApp, dan Android tak mengizinkan satu app merekam layar app lain. Karena itu tak ada
 * tombol "rekam" di sini, hanya pemilih berkas + panduan memakai perekam bawaan HP.
 */
@Composable
fun ChatActivityScreen(
    onBack: () -> Unit,
    viewModel: ChatActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pilihVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (nama, ukuran) = bacaInfoBerkas(context.contentResolver, uri)
            viewModel.pilihVideo(uri, ukuran, nama)
        }
    }

    // Hanya pesan SUKSES yang hilang sendiri. Pesan error dibiarkan sampai karyawan
    // bertindak — kalau ikut hilang, kegagalan unggah 20 MB lewat begitu saja tanpa jejak.
    LaunchedEffect(state.pesanSukses) {
        if (state.pesanSukses != null) {
            delay(4000)
            viewModel.bersihkanPesan()
        }
    }

    // Banner error duduk di DASAR kolom yang bisa di-scroll, sedangkan tombol Kirim
    // ada di tengah. Tanpa gulir otomatis ini, unggahan yang gagal terlihat seperti
    // "tombol menyala lagi, tak terjadi apa-apa" — alasannya ada, cuma di luar layar.
    // Itu keluhan nyata dari lapangan. Banner error = elemen TERAKHIR kolom, jadi
    // menggulir ke `maxValue` selalu memunculkannya; kalau nanti ada elemen baru di
    // bawahnya, gulir ini harus diarahkan ulang.
    val scrollState = rememberScrollState()
    LaunchedEffect(state.pesanError) {
        if (state.pesanError != null) scrollState.animateScrollTo(scrollState.maxValue)
    }
    TridjayaCollapsibleHeader(title = "Bukti Chat Harian", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val today = state.today
        TridjayaPullRefresh(
            isRefreshing = state.loading && today != null,
            onRefresh = { viewModel.muat() },
            modifier = contentModifier,
        ) {
            when {
                state.loading && today == null ->
                    ScrollableCenter { CircularProgressIndicator() }

                today == null ->
                    ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.pesanError ?: "Gagal memuat status hari ini.",
                            onRetry = { viewModel.muat() },
                        )
                    }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                ) {
                    KartuStatus(today, state)

                    val bukti = today.bukti
                    if (bukti?.status == "rejected") {
                        Spacer(Modifier.height(12.dp))
                        Banner(
                            warna = MERAH,
                            ikon = Icons.Rounded.Warning,
                            judul = "Bukti ditolak kepala cabang",
                            isi = bukti.alasanTolak?.takeIf { it.isNotBlank() }
                                ?: "Tanpa alasan tertulis — tanyakan ke kepala cabang.",
                        ) {
                            Spacer(Modifier.height(10.dp))
                            ExpressiveOutlinedButton(
                                onClick = { pilihVideo.launch("video/*") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ganti Video")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    BannerCheckout(today)

                    if (bukti?.videoPurged == true) {
                        Spacer(Modifier.height(12.dp))
                        Banner(
                            warna = MaterialTheme.colorScheme.onSurfaceVariant,
                            ikon = Icons.Rounded.DeleteForever,
                            judul = "Video sudah dihapus (retensi 24 jam)",
                            isi = "Jumlah chat dan hasil pemeriksaannya tetap tercatat.",
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    if (!today.wajib) {
                        // Hari libur / saklar fitur mati / belum check-in — form disembunyikan,
                        // alasannya datang dari server supaya tak berselisih dengan aturan absen.
                        Banner(
                            warna = MaterialTheme.colorScheme.primary,
                            ikon = Icons.Rounded.Info,
                            judul = "Bukti chat tidak diminta hari ini",
                            isi = today.alasanTidakWajib?.takeIf { it.isNotBlank() },
                        )
                    } else {
                        FormKirim(
                            state = state,
                            onUbahJumlah = viewModel::ubahJumlah,
                            onPilihVideo = { pilihVideo.launch("video/*") },
                            onKirim = { viewModel.kirim(context.contentResolver) },
                        )
                    }

                    state.pesanSukses?.let {
                        Spacer(Modifier.height(12.dp))
                        Banner(warna = HIJAU, ikon = Icons.Rounded.CheckCircle, judul = it, isi = null)
                    }
                    state.pesanError?.let {
                        Spacer(Modifier.height(12.dp))
                        Banner(warna = MERAH, ikon = Icons.Rounded.Warning, judul = it, isi = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuStatus(today: AktivitasChatTodayDto, state: ChatActivityUiState) {
    val bukti = today.bukti
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(kategoriLabel(today.targetKategori), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Target minimal ${today.targetMinimal} chat per hari",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                    Text(
                        statusLabel(bukti?.status ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (state.sisa > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Kurang ${state.sisa} chat lagi dari angka yang kamu isi.",
                    style = MaterialTheme.typography.labelMedium,
                    color = KUNING,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (bukti != null && bukti.jumlahChat > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tercatat di server: ${bukti.jumlahChat} chat" +
                        (if (bukti.revisiKe > 0) " · revisi ke-${bukti.revisiKe}" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Status gembok absen pulang. Kalimatnya diambil APA ADANYA dari server
 * ([AktivitasChatTodayDto.alasanCheckoutTertutup]) — kalau layar menyusun kalimatnya sendiri,
 * ia bisa berselisih dengan pesan yang ditolak endpoint check-out.
 */
@Composable
private fun BannerCheckout(today: AktivitasChatTodayDto) {
    if (today.checkoutTerbuka) {
        Banner(
            warna = HIJAU,
            ikon = Icons.Rounded.LockOpen,
            judul = "Absen pulang terbuka",
            isi = null,
        )
    } else {
        Banner(
            warna = KUNING,
            ikon = Icons.Rounded.Lock,
            judul = "Absen pulang terkunci",
            isi = today.alasanCheckoutTertutup?.takeIf { it.isNotBlank() },
        )
    }
}

@Composable
private fun FormKirim(
    state: ChatActivityUiState,
    onUbahJumlah: (String) -> Unit,
    onPilihVideo: () -> Unit,
    onKirim: () -> Unit,
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Kirim Bukti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "Rekam layar chat pakai perekam bawaan HP (setel 720p), lalu pilih berkasnya di sini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            ExpressiveTextField(
                value = state.jumlahChatInput,
                onValueChange = onUbahJumlah,
                label = "Jumlah chat hari ini",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            ExpressiveOutlinedButton(onClick = onPilihVideo, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.videoUri == null) "Pilih Video" else "Ganti Video")
            }
            if (state.videoUri != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.videoNama.ifBlank { "Video terpilih" } + " · " + formatUkuranBerkas(state.videoUkuranBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            ExpressiveFilledButton(
                onClick = onKirim,
                enabled = state.gate.ok && !state.mengirim,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.mengirim) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.mengirim) "Mengirim…" else "Kirim Bukti")
            }
            val alasan = if (state.mengirim) "Video sedang diunggah, jangan tutup layar ini." else state.gate.alasan
            if (alasan != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    alasan,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun Banner(
    warna: Color,
    ikon: ImageVector,
    judul: String,
    isi: String?,
    tambahan: @Composable (() -> Unit)? = null,
) {
    Surface(color = warna.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ikon, contentDescription = null, tint = warna, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(judul, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = warna)
            }
            if (isi != null) {
                Spacer(Modifier.height(4.dp))
                Text(isi, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            tambahan?.invoke()
        }
    }
}

internal fun kategoriLabel(targetKategori: String): String =
    if (targetKategori == "sales") "Sales" else "Non-Sales"
