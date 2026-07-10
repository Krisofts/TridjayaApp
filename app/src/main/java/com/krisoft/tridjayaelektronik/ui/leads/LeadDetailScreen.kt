package com.krisoft.tridjayaelektronik.ui.leads

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveShapes
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

private fun statusColor(status: String): Color = when (status.lowercase()) {
    "won" -> Color(0xFF2E7D32)
    "lost" -> Color(0xFFC62828)
    else -> Color(0xFF1565C0)
}

private fun statusLabel(status: String): String = when (status.lowercase()) {
    "won" -> "Menang"
    "lost" -> "Hilang"
    else -> "Open"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailScreen(
    onBack: () -> Unit,
    viewModel: LeadDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLostDialog by remember { mutableStateOf(false) }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    TridjayaCollapsibleHeader(title = state.lead?.nama ?: "Detail Prospek", onBack = onBack) { contentModifier ->
        Box(modifier = contentModifier) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.lead == null && state.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat prospek.",
                            onRetry = viewModel::load
                        )
                    }
                }
                state.lead == null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Prospek tidak ditemukan")
                    }
                }
                else -> {
                    val lead = state.lead!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarInset + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LeadHeroCard(lead = lead)

                        QuickActions(
                            onWhatsApp = {
                                val text = "Halo ${lead.nama}, "
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "https://wa.me/${lead.phone}?text=${android.net.Uri.encode(text)}".toUri())
                                    )
                                }
                            },
                            onCall = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${lead.phone}".toUri()))
                                }
                            }
                        )

                        if (lead.estimatedValue > 0) {
                            EstimatedValueCard(lead.estimatedValue)
                        }

                        StatusActionsRow(
                            status = lead.status,
                            isUpdating = state.isUpdatingStatus,
                            onMarkWon = viewModel::markWon,
                            onMarkLost = { showLostDialog = true },
                            onReopen = viewModel::reopen
                        )

                        if (state.pipeline != null) {
                            PipelineCard(
                                pipeline = state.pipeline!!,
                                currentStageId = lead.stageId,
                                isMoving = state.isMovingStage,
                                onSelectStage = viewModel::moveStage
                            )
                        }

                        LeadInfoCard(lead = lead)

                        if (state.errorMessage != null) {
                            Text(text = state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showLostDialog) {
        LostReasonDialog(
            onDismiss = { showLostDialog = false },
            onConfirm = { reason ->
                viewModel.markLost(reason)
                showLostDialog = false
            }
        )
    }
}

/** Hero header: large status-tinted avatar, name, phone and a status badge. */
@Composable
private fun LeadHeroCard(lead: LeadDto) {
    val accent = statusColor(lead.status)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = lead.nama.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lead.nama,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = lead.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(color = accent, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = statusLabel(lead.status),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onWhatsApp: () -> Unit, onCall: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ExpressiveFilledButton(
            onClick = onWhatsApp,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FA855), contentColor = Color.White),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("WhatsApp")
        }
        ExpressiveOutlinedButton(
            onClick = onCall,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Telepon")
        }
    }
}

/** Prominent estimated-value card — money is front-and-centre in a CRM. */
@Composable
private fun EstimatedValueCard(value: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Estimasi Nilai",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatRupiah(value),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatusActionsRow(
    status: String,
    isUpdating: Boolean,
    onMarkWon: () -> Unit,
    onMarkLost: () -> Unit,
    onReopen: () -> Unit
) {
    when (status.lowercase()) {
        "open" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExpressiveFilledButton(
                    onClick = onMarkWon,
                    enabled = !isUpdating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tandai Menang")
                }
                ExpressiveOutlinedButton(
                    onClick = onMarkLost,
                    enabled = !isUpdating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tandai Hilang")
                }
            }
        }
        "won", "lost" -> {
            ExpressiveOutlinedButton(
                onClick = onReopen,
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buka Kembali")
            }
        }
    }
}

/** Pipeline stage card: progress bar + tappable stage chips (current highlighted). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipelineCard(
    pipeline: PipelineDto,
    currentStageId: Long,
    isMoving: Boolean,
    onSelectStage: (Long) -> Unit
) {
    val stages = remember(pipeline) { pipeline.stages.sortedBy { it.urutan } }
    val currentIndex = stages.indexOfFirst { it.id == currentStageId }.coerceAtLeast(0)
    val progress = if (stages.isEmpty()) 0f else (currentIndex + 1).toFloat() / stages.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tahap Pipeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${currentIndex + 1}/${stages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.forEachIndexed { index, stage ->
                    val isCurrent = index == currentIndex
                    val isDone = index < currentIndex
                    val container = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isDone -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = when {
                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                        isDone -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        onClick = { if (!isMoving) onSelectStage(stage.id) },
                        shape = RoundedCornerShape(50),
                        color = container,
                        contentColor = contentColor
                    ) {
                        Text(
                            text = "${index + 1}. ${stage.nama}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Labeled info card with icon rows — source, location, created date, note, lost reason. */
@Composable
private fun LeadInfoCard(lead: LeadDto) {
    val rows = buildList {
        if (!lead.source.isNullOrBlank()) add(Triple(Icons.Rounded.Campaign, "Sumber", lead.source!!))
        if (!lead.lokasi.isNullOrBlank()) add(Triple(Icons.Rounded.Place, "Lokasi", lead.lokasi!!))
        if (lead.createdAt.isNotBlank()) add(Triple(Icons.Rounded.CalendarMonth, "Ditambahkan", formatDate(lead.createdAt)))
        if (!lead.catatan.isNullOrBlank()) add(Triple(Icons.Rounded.Notes, "Catatan", lead.catatan!!))
        if (lead.status.equals("lost", ignoreCase = true) && !lead.lostReason.isNullOrBlank()) {
            add(Triple(Icons.Rounded.Cancel, "Alasan Hilang", lead.lostReason!!))
        }
    }
    if (rows.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = "Informasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            rows.forEach { (icon, label, value) ->
                InfoRow(icon = icon, label = label, value = value)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LostReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExpressiveShapes.Large,
        title = { Text("Tandai Hilang", fontWeight = FontWeight.Bold) },
        text = {
            ExpressiveTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Alasan",
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            ExpressiveFilledButton(
                onClick = { if (reason.isNotBlank()) onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) {
                Text("Konfirmasi")
            }
        },
        dismissButton = {
            ExpressiveOutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

private fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

/** Best-effort "yyyy-MM-dd…" → "d Mmm yyyy" (Indonesian months); falls back to the raw string. */
private fun formatDate(raw: String): String {
    val datePart = raw.substringBefore('T').substringBefore(' ').trim()
    val parts = datePart.split("-")
    if (parts.size != 3) return raw
    val month = parts[1].toIntOrNull() ?: return raw
    val day = parts[2].toIntOrNull() ?: return raw
    if (month !in 1..12) return raw
    val months = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    return "$day ${months[month]} ${parts[0]}"
}
