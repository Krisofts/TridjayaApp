package com.krisoft.tridjayaelektronik.ui.raport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.io.File

/**
 * Input Aktivitas (raport harian) — BETA.
 *
 * Satu baris per jobdesk posisi karyawan; buktinya foto kamera ber-watermark
 * atau "tanpa bukti + alasan". Kirim per baris (server upsert per baris), jadi
 * sinyal putus di tengah tak membatalkan yang sudah masuk. Video & unggah dari
 * galeri belum ada di sini — masih lewat web.
 */
@Composable
fun RaportScreen(
    onBack: () -> Unit,
    viewModel: RaportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Kamera: satu launcher dipakai semua baris — index+file yang sedang
    // dijepret dipegang di sini karena callback-nya baru jalan setelah kembali
    // dari app kamera.
    var pending by remember { mutableStateOf<Pair<Int, File>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.let { (index, file) -> if (ok) viewModel.submitWithPhoto(index, file) }
        pending = null
    }
    var alasanUntukIndex by remember { mutableStateOf<Int?>(null) }

    TridjayaCollapsibleHeader(title = "Input Aktivitas", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(modifier = contentModifier.fillMaxSize()) {
            when {
                state.isLoading -> Column(modifier = Modifier.padding(top = 4.dp)) {
                    repeat(5) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveErrorState(
                        message = state.error ?: "Gagal memuat",
                        onRetry = viewModel::refresh
                    )
                }

                state.jobdesks.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveEmptyState(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = "Jobdesk belum diatur",
                        subtitle = "Divisi \"${state.divisi.ifBlank { "-" }}\" belum punya daftar jobdesk " +
                            "di master raport. Minta PIC Raport menambahkannya."
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Ringkasan(state) }
                    state.message?.let { pesan ->
                        item {
                            ExpressiveInlineError(
                                message = pesan,
                                onRetry = viewModel::consumeMessage,
                                retryLabel = "Tutup"
                            )
                        }
                    }
                    itemsIndexed(state.jobdesks) { index, jobdesk ->
                        JobdeskRow(
                            nomor = index + 1,
                            jobdesk = jobdesk,
                            terkirim = state.submitted[index],
                            busy = state.busyIndex == index,
                            enabled = state.busyIndex == null,
                            onFoto = {
                                val file = File(context.cacheDir, "raport/jobdesk_$index.jpg")
                                    .apply { parentFile?.mkdirs() }
                                pending = index to file
                                camera.launch(
                                    FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                )
                            },
                            onTanpaBukti = { alasanUntukIndex = index },
                        )
                    }
                }
            }
        }
    }

    alasanUntukIndex?.let { index ->
        AlasanDialog(
            onDismiss = { alasanUntukIndex = null },
            onSubmit = { alasan ->
                alasanUntukIndex = null
                viewModel.submitWithoutEvidence(index, alasan)
            }
        )
    }
}

@Composable
private fun Ringkasan(state: RaportUiState) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.posisi.ifBlank { "Laporan aktivitas harian" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.size(8.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "BETA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            text = "${state.terkirim}/${state.jobdesks.size} jobdesk terkirim hari ini · " +
                "bukti foto diambil langsung dari kamera",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JobdeskRow(
    nomor: Int,
    jobdesk: String,
    terkirim: RaportItemDto?,
    busy: Boolean,
    enabled: Boolean,
    onFoto: () -> Unit,
    onTanpaBukti: () -> Unit,
) {
    val status = rowStatus(terkirim)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "$nomor. $jobdesk",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(6.dp))
            StatusChip(status, terkirim)
            Spacer(Modifier.size(10.dp))
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Mengirim…", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveFilledButton(
                        onClick = onFoto,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (terkirim == null) "Foto bukti" else "Ganti foto")
                    }
                    ExpressiveOutlinedButton(
                        onClick = onTanpaBukti,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text("Tanpa bukti") }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: RaportRowStatus, terkirim: RaportItemDto?) {
    val (label, warna) = when (status) {
        RaportRowStatus.BELUM -> "Belum dikirim" to MaterialTheme.colorScheme.surfaceContainerHighest
        RaportRowStatus.MENUNGGU -> "Menunggu review PIC" to MaterialTheme.colorScheme.tertiaryContainer
        RaportRowStatus.DISETUJUI -> "Disetujui PIC" to MaterialTheme.colorScheme.primaryContainer
        RaportRowStatus.DITOLAK -> "Ditolak PIC" to MaterialTheme.colorScheme.errorContainer
    }
    Column {
        Surface(shape = MaterialTheme.shapes.small, color = warna) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        // Alasan "tanpa bukti" & komentar reviewer ditampilkan apa adanya —
        // keduanya yang menentukan nilai raport, jadi jangan disembunyikan.
        terkirim?.employeeNote?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Alasan: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        terkirim?.reviewerComment?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Catatan PIC: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AlasanDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var alasan by remember { mutableStateOf("") }
    val cukup = alasan.trim().length >= RaportViewModel.MIN_REASON_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tanpa bukti") },
        text = {
            Column {
                Text(
                    "Jobdesk tanpa bukti foto biasanya dinilai 0 oleh PIC. Jelaskan alasannya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                ExpressiveTextField(
                    value = alasan,
                    onValueChange = { alasan = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Alasan (minimal ${RaportViewModel.MIN_REASON_LENGTH} karakter)",
                    singleLine = false,
                    isError = alasan.isNotBlank() && !cukup,
                    supportingText = "${alasan.trim().length}/${RaportViewModel.MIN_REASON_LENGTH}",
                )
            }
        },
        confirmButton = {
            ExpressiveFilledButton(onClick = { onSubmit(alasan) }, enabled = cukup) { Text("Kirim") }
        },
        dismissButton = { ExpressiveOutlinedButton(onClick = onDismiss) { Text("Batal") } }
    )
}
