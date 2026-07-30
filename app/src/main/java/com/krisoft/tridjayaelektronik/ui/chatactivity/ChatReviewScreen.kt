package com.krisoft.tridjayaelektronik.ui.chatactivity

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.BuktiChatDto
import com.krisoft.tridjayaelektronik.ui.attendance.formatPunchTime
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.io.File

/**
 * Antrian pemeriksaan bukti chat — kepala cabang menyetujui/menolak bukti anak buahnya.
 *
 * ponytail: TIDAK ada pemutar video di dalam app. Endpoint videonya butuh header
 * `Authorization`, jadi `VideoView` dengan URL polos mustahil, dan satu-satunya cara lain
 * (ExoPlayer/media3 + `DataSource` ber-header) berarti menambah dependency pemutar penuh
 * demi satu tombol. Videonya diunduh ke cache lalu diserahkan ke aplikasi pemutar bawaan HP
 * lewat `FileProvider` + `ACTION_VIEW` — pola yang sama dipakai `UpdateManager` (APK) dan
 * `OpnamePdfExporter` (PDF).
 */
@Composable
fun ChatReviewScreen(
    onBack: () -> Unit,
    viewModel: ChatActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tolakId by remember { mutableStateOf<String?>(null) }
    var alasan by remember { mutableStateOf("") }
    var mengunduhId by remember { mutableStateOf<String?>(null) }
    var pesanPutar by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.muatReview() }

    TridjayaCollapsibleHeader(title = "Periksa Bukti Chat", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.reviewLoading && state.daftarReview.isEmpty() ->
                Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.pesanError != null && state.daftarReview.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveErrorState(
                        message = state.pesanError ?: "Gagal memuat antrian.",
                        onRetry = { viewModel.muatReview() },
                    )
                }

            state.daftarReview.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = {
                            Icon(
                                Icons.Rounded.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                        },
                        title = "Tidak ada bukti yang menunggu.",
                        subtitle = "Semua bukti chat hari ini sudah diperiksa.",
                    )
                }

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "hitungan") {
                    // Kepala cabang perlu tahu sisa yang mengunci absen pulangnya SENDIRI.
                    Text(
                        "${state.daftarReview.size} bukti menunggu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                (pesanPutar ?: state.pesanError)?.let { pesan ->
                    item(key = "pesan") {
                        Text(pesan, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                items(state.daftarReview, key = { it.id }) { bukti ->
                    BarisReview(
                        bukti = bukti,
                        sibuk = state.memutuskanId == bukti.id,
                        mengunduh = mengunduhId == bukti.id,
                        onPutar = {
                            pesanPutar = null
                            mengunduhId = bukti.id
                            viewModel.bukaVideo(bukti.videoUrl.orEmpty(), context.cacheDir) { berkas ->
                                mengunduhId = null
                                if (berkas != null && !bukaPemutarVideo(context, berkas)) {
                                    pesanPutar = "Tidak ada aplikasi pemutar video di HP ini."
                                }
                            }
                        },
                        onSetuju = { viewModel.putuskan(bukti.id, "approved") },
                        onTolak = { tolakId = bukti.id; alasan = "" },
                    )
                }
            }
        }
    }

    tolakId?.let { id ->
        AlertDialog(
            onDismissRequest = { tolakId = null },
            title = { Text("Tolak bukti chat?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Alasan penolakan wajib diisi — karyawan harus tahu apa yang perlu diperbaiki.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alasan,
                        onValueChange = { alasan = it },
                        placeholder = { Text("Alasan…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    // Aturan yang sama dipakai ViewModel sebelum memanggil server.
                    enabled = bolehReview("rejected", alasan).ok,
                    onClick = {
                        viewModel.putuskan(id, "rejected", alasan.trim())
                        tolakId = null
                    },
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { tolakId = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun BarisReview(
    bukti: BuktiChatDto,
    sibuk: Boolean,
    mengunduh: Boolean,
    onPutar: () -> Unit,
    onSetuju: () -> Unit,
    onTolak: () -> Unit,
) {
    val adaVideo = !bukti.videoPurged && !bukti.videoUrl.isNullOrBlank()
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        bukti.karyawanNama.ifBlank { "(tanpa nama)" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${bukti.jumlahChat} chat · ${kategoriLabel(bukti.targetKategori)} " +
                            "(min ${bukti.targetMinimal}) · ${formatPunchTime(bukti.submittedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                    Text(
                        statusLabel(bukti.status),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            ExpressiveOutlinedButton(
                onClick = onPutar,
                enabled = adaVideo && !mengunduh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (mengunduh) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        bukti.videoPurged -> "Video sudah dihapus (retensi 3 hari)"
                        !adaVideo -> "Video tidak ada"
                        mengunduh -> "Mengunduh…"
                        else -> "Putar Video"
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ExpressiveFilledButton(onClick = onSetuju, enabled = !sibuk, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Setuju")
                }
                ExpressiveOutlinedButton(onClick = onTolak, enabled = !sibuk, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tolak")
                }
            }
        }
    }
}

/**
 * Serahkan berkas video ke pemutar bawaan HP. `false` = tak ada aplikasi yang bisa
 * membukanya (atau intent-nya ditolak) — pemanggil menampilkan pesan, bukan crash.
 */
private fun bukaPemutarVideo(context: Context, berkas: File): Boolean = try {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", berkas)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: Exception) {
    false
}
