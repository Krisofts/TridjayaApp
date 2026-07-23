package com.krisoft.tridjayaelektronik.ui.deliveryflow

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AkiFormDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/** Menu "Pengambilan Aki" — riwayat form (082) + tandai aki bekas dikembalikan. Pola loading/error/empty
 *  sama dengan [DeliveryQueueScreen]/[DiscountApprovalScreen]; RBAC (admin/manager/pdi) di backend. */
@Composable
fun AkiListScreen(onBack: () -> Unit, viewModel: DeliveryFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadAkiForms() }
    var confirmId by remember { mutableStateOf<String?>(null) }
    var rejectId by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }
    /** Admin/manager wajib pilih slot eksplisit saat approve (backend 400 tanpa slot). */
    var slotPickId by remember { mutableStateOf<String?>(null) }

    TridjayaCollapsibleHeader(title = "Pengambilan Aki", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.loading && state.akiList.isEmpty() ->
                Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.akiList.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = { viewModel.loadAkiForms() })
                }
            state.akiList.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = { Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                        title = "Belum ada form aki", subtitle = "Belum ada pengambilan aki yang tercatat."
                    )
                }
            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.actionError?.let { item { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) } }
                items(state.akiList, key = { it.id }) { form ->
                    AkiCard(
                        form, state.submitting,
                        // Tombol approve/reject HANYA utk approver (kepala-cabang/
                        // admin-penjualan/kasir/grant aki-approval/admin/manager) —
                        // pembaca lain (PDI) jangan lihat tombol yang pasti 403.
                        canApprove = viewModel.canApproveAki,
                        // Tandai-dikembalikan: backend pdi (cabang form) / admin saja.
                        canReturn = viewModel.access.pdi,
                        onApprove = {
                            if (viewModel.akiNeedsSlot) slotPickId = form.id
                            else viewModel.approveAki(form.id)
                        },
                        onReject = { rejectId = form.id; rejectReason = "" },
                        onMarkReturned = { confirmId = form.id }
                    )
                }
            }
        }
    }

    confirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text("Tandai dikembalikan?", fontWeight = FontWeight.Bold) },
            text = { Text("Aki bekas untuk form ini akan ditandai sudah dikembalikan.") },
            confirmButton = { TextButton(onClick = { viewModel.markAkiReturned(id); confirmId = null }) { Text("Tandai") } },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text("Batal") } }
        )
    }

    // Dialog pilih slot approval (admin/manager) — paritas web AkiApprovalPage.
    slotPickId?.let { id ->
        AlertDialog(
            onDismissRequest = { slotPickId = null },
            title = { Text("Setujui sebagai slot?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Admin/manager wajib memilih slot approval eksplisit.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "kacab" to "Kepala Cabang",
                        "admin_penjualan" to "Admin Penjualan",
                        "aki_approver" to "Approver Aki (Kasir)",
                    ).forEach { (slot, label) ->
                        Surface(
                            onClick = { viewModel.approveAki(id, slot); slotPickId = null },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { slotPickId = null }) { Text("Batal") } }
        )
    }

    rejectId?.let { id ->
        AlertDialog(
            onDismissRequest = { rejectId = null },
            title = { Text("Tolak form aki?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Alasan penolakan wajib diisi.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder = { Text("Alasan…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rejectReason.isNotBlank(),
                    onClick = { viewModel.rejectAki(id, rejectReason.trim()); rejectId = null }
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { rejectId = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun AkiCard(form: AkiFormDto, submitting: Boolean, canApprove: Boolean, canReturn: Boolean, onApprove: () -> Unit, onReject: () -> Unit, onMarkReturned: () -> Unit) {
    val sudah = form.akiBekasStatus == "sudah"
    val approved = form.approvalStatus == "approved"
    val rejected = form.approvalStatus == "rejected"
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${form.tanggal} · ${form.pengambilNama}",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                ApprovalBadge(approved, rejected)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                akiTujuanLabel(form.tujuan) + (form.tujuanLainnya?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${form.merkTipe} · ${form.jumlahPcs} pcs",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Status 3 slot approval — siapa sudah setuju, siapa masih ditunggu.
            if (!rejected) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SlotChip("Kacab", form.kacabApprovedNama, Modifier.weight(1f))
                    SlotChip("Adm. Penjualan", form.adminPenjualanApprovedNama, Modifier.weight(1f))
                    SlotChip("Kasir/Approver", form.akiApproverApprovedNama, Modifier.weight(1f))
                }
            }
            // Ditolak → tampilkan alasan, tanpa aksi.
            if (rejected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ditolak" + (form.rejectedByNama?.takeIf { it.isNotBlank() }?.let { " oleh $it" } ?: "") +
                        (form.rejectedReason?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
            // Belum disetujui (dan belum ditolak) → tombol SETUJUI / TOLAK HANYA
            // utk approver (canApprove — SpkAccessPolicy); pembaca lain (PDI)
            // cuma lihat status.
            else if (!approved && canApprove) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveFilledButton(onClick = onApprove, enabled = !submitting, modifier = Modifier.weight(1f)) {
                        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Setujui")
                    }
                    OutlinedButton(onClick = onReject, enabled = !submitting, modifier = Modifier.weight(1f)) {
                        Text("Tolak", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // Aksi logistik TERPISAH dari chain approval: tandai aki bekas
            // dikembalikan — berlaku utk form approved MAUPUN rejected (backend
            // mark_return tak mensyaratkan approved; aki yang terlanjur diambil
            // pada form rejected tetap harus tercatat kembalinya — review 2026-07-23).
            if ((approved || rejected) && !sudah && canReturn) {
                Spacer(Modifier.height(10.dp))
                ExpressiveFilledButton(onClick = onMarkReturned, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Tandai Dikembalikan")
                }
            }
        }
    }
}

/** Chip status satu slot approval: nama approver bila sudah, "menunggu" bila belum. */
@Composable
private fun SlotChip(label: String, approvedBy: String?, modifier: Modifier = Modifier) {
    val done = !approvedBy.isNullOrBlank()
    val color = if (done) Color(0xFF12B76A) else Color(0xFFB5670C)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (done) "✓ ${approvedBy!!.trim()}" else "menunggu",
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ApprovalBadge(approved: Boolean, rejected: Boolean = false) {
    val color = when {
        rejected -> Color(0xFFD92D20)
        approved -> Color(0xFF12B76A)
        else -> Color(0xFFB5670C)
    }
    val label = when {
        rejected -> "Ditolak"
        approved -> "Disetujui"
        else -> "Menunggu approval"
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

