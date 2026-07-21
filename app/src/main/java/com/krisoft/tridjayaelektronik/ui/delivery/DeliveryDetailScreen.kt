package com.krisoft.tridjayaelektronik.ui.delivery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krisoft.tridjayaelektronik.data.model.DeliveryDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveShapes
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Detail satu pengiriman + stepper workflow. Tombol "Lanjutkan" memajukan satu tahap,
 * "Tandai Gagal" memindah ke status terminal, "Jadwalkan Ulang" mengembalikan yang gagal.
 * Share via state-swap dari [DeliveryListScreen], jadi tombol back sistem ditangani sendiri.
 */
@Composable
fun DeliveryDetailScreen(
    deliveryId: String,
    viewModel: DeliveryViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsState()
    val delivery = state.items.firstOrNull { it.id == deliveryId }
    val context = LocalContext.current
    var showFailDialog by remember { mutableStateOf(false) }

    if (delivery == null) {
        TridjayaCollapsibleHeader(title = "Detail Pengiriman", onBack = onBack) { contentModifier ->
            Box(modifier = contentModifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Pengiriman tidak ditemukan")
            }
        }
        return
    }

    val status = DeliveryStatus.from(delivery.status)
    val payment = DeliveryPayment.from(delivery.paymentStatus)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    TridjayaCollapsibleHeader(title = "Detail Pengiriman", onBack = onBack) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBottom + 24.dp)
        ) {
            DeliveryHeroCard(
                delivery = delivery,
                status = status,
                payment = payment,
                onWhatsApp = {
                    delivery.customerPhone?.let {
                        openDeliveryWhatsApp(context, it, buildDeliveryMessage(delivery.customerName, delivery.itemName, status))
                    }
                },
                onCall = { delivery.customerPhone?.let { dialDeliveryPhone(context, it) } },
                onMap = { openDeliveryMap(context, delivery.address) }
            )

            if (status == DeliveryStatus.PDI) {
                Spacer(modifier = Modifier.height(16.dp))
                PdiChecklistCard(
                    checked = delivery.pdiChecked,
                    onToggle = { viewModel.togglePdiCheck(delivery.id, it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            DeliveryStepperCard(
                current = status,
                pdiComplete = viewModel.isPdiComplete(delivery),
                onAdvance = { viewModel.advanceStatus(delivery.id) },
                onReschedule = { viewModel.reschedule(delivery.id) }
            )

            if (status != DeliveryStatus.TERKIRIM && status != DeliveryStatus.GAGAL) {
                Spacer(modifier = Modifier.height(12.dp))
                ExpressiveOutlinedButton(
                    onClick = { showFailDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tandai Gagal", color = DeliveryStatus.GAGAL.color)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            DeliveryInfoCard(delivery)
        }
    }

    if (showFailDialog) {
        FailReasonDialog(
            onDismiss = { showFailDialog = false },
            onConfirm = { reason ->
                viewModel.markFailed(delivery.id, reason)
                showFailDialog = false
            }
        )
    }
}

@Composable
private fun DeliveryHeroCard(
    delivery: DeliveryDto,
    status: DeliveryStatus,
    payment: DeliveryPayment,
    onWhatsApp: () -> Unit,
    onCall: () -> Unit,
    onMap: () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(status.color.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = delivery.customerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (delivery.quantity > 1) "${delivery.itemName} · ${delivery.quantity} unit" else delivery.itemName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeliveryStatusBadge(status)
                DeliveryPaymentBadge(payment)
            }
            if (delivery.estimatedValue > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDeliveryRupiah(delivery.estimatedValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveFilledButton(
                    onClick = onWhatsApp,
                    enabled = !delivery.customerPhone.isNullOrBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FA855), contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WA")
                }
                ExpressiveOutlinedButton(onClick = onCall, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Telepon")
                }
                ExpressiveOutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Peta")
                }
            }
        }
    }
}

/** Kartu checklist PDI — dicentang sebelum barang boleh Dikirim. */
@Composable
private fun PdiChecklistCard(checked: List<String>, onToggle: (String) -> Unit) {
    val doneCount = PDI_ITEMS.count { it.key in checked }
    val accent = DeliveryStatus.PDI.color
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DeliveryStatus.PDI.icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inspeksi Pra-Kirim (PDI)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    text = "$doneCount/${PDI_ITEMS.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = if (doneCount == PDI_ITEMS.size) DeliveryStatus.TERKIRIM.color else accent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            PDI_ITEMS.forEach { pdiItem ->
                val isChecked = pdiItem.key in checked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(pdiItem.key) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (isChecked) accent else Color.Transparent,
                                RoundedCornerShape(7.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isChecked) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color.Transparent, RoundedCornerShape(7.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = pdiItem.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            if (doneCount < PDI_ITEMS.size) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lengkapi semua poin untuk bisa lanjut ke Dikirim.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Stepper horizontal 5 tahap + tombol "Lanjutkan"; banner + "Jadwalkan Ulang" bila gagal. */
@Composable
private fun DeliveryStepperCard(
    current: DeliveryStatus,
    pdiComplete: Boolean,
    onAdvance: () -> Unit,
    onReschedule: () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Alur Pengiriman", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(14.dp))

            if (current == DeliveryStatus.GAGAL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeliveryStatus.GAGAL.color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(DeliveryStatus.GAGAL.icon, contentDescription = null, tint = DeliveryStatus.GAGAL.color, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Pengiriman ini ditandai GAGAL",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeliveryStatus.GAGAL.color
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ExpressiveFilledButton(onClick = onReschedule, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Jadwalkan Ulang")
                }
                return@Column
            }

            val steps = DeliveryStatus.happyPath
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                steps.forEachIndexed { index, step ->
                    val isDone = step.order < current.order
                    val isCurrent = step == current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(78.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = if (isDone || isCurrent) step.color else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    step.icon,
                                    contentDescription = null,
                                    tint = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = step.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                            color = when {
                                isCurrent -> step.color
                                isDone -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index != steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .width(14.dp)
                                .height(2.dp)
                                .background(
                                    if (steps[index + 1].order <= current.order) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
            }

            current.next()?.let { next ->
                // Dari PDI hanya boleh lanjut bila checklist lengkap.
                val gatedByPdi = current == DeliveryStatus.PDI && !pdiComplete
                Spacer(modifier = Modifier.height(14.dp))
                ExpressiveFilledButton(
                    onClick = onAdvance,
                    enabled = !gatedByPdi,
                    colors = ButtonDefaults.buttonColors(containerColor = next.color, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (gatedByPdi) "Selesaikan PDI dulu" else "Lanjutkan: ${next.label}")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DeliveryInfoCard(delivery: DeliveryDto) {
    val rows = buildList {
        delivery.spkNumber?.takeIf { it.isNotBlank() }?.let { add(Triple(Icons.Rounded.ReceiptLong, "No. SPK", it)) }
        add(Triple(Icons.Rounded.ShoppingBag, "Barang", if (delivery.quantity > 1) "${delivery.itemName} (${delivery.quantity} unit)" else delivery.itemName))
        add(Triple(Icons.Rounded.Place, "Alamat", delivery.address))
        add(Triple(Icons.Rounded.Person, "Sales", delivery.salesName))
        add(Triple(Icons.Rounded.Business, "Cabang Pengirim", delivery.senderBranch))
        add(Triple(Icons.Rounded.Payments, "Pembayaran", DeliveryPayment.from(delivery.paymentStatus).label))
        delivery.scheduledDate?.let { add(Triple(Icons.Rounded.CalendarMonth, "Jadwal Kirim", formatDeliveryDate(it))) }
        if (delivery.createdAt.isNotBlank()) add(Triple(Icons.Rounded.CalendarMonth, "Dibuat", formatDeliveryDate(delivery.createdAt)))
        delivery.note?.takeIf { it.isNotBlank() }?.let { add(Triple(Icons.AutoMirrored.Rounded.Notes, "Catatan", it)) }
    }

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Informasi Pengiriman",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            rows.forEachIndexed { index, (icon, label, value) ->
                InfoRow(icon, label, value)
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ID Pengiriman: ${delivery.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FailReasonDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExpressiveShapes.Large,
        title = { Text("Tandai Gagal", fontWeight = FontWeight.Bold) },
        text = {
            ExpressiveTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Alasan (opsional)",
                placeholder = "Pelanggan tidak di tempat, alamat salah, dsb.",
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            ExpressiveFilledButton(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = DeliveryStatus.GAGAL.color, contentColor = Color.White)
            ) { Text("Tandai Gagal") }
        },
        dismissButton = {
            ExpressiveOutlinedButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
