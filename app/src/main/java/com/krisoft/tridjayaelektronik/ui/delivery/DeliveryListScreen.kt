package com.krisoft.tridjayaelektronik.ui.delivery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.DeliveryDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Daftar jadwal pengiriman + workflow. Chip status ikut ter-scroll bersama list, pencarian toggle
 * di appbar, dan memilih kartu membuka [DeliveryDetailScreen] via state-swap (pola Opname/Indent).
 * Sepenuhnya data dummy — lihat [DeliveryViewModel].
 */
@Composable
fun DeliveryListScreen(
    onBack: () -> Unit,
    viewModel: DeliveryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showSpkForm by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(showSearch) { if (showSearch) searchFocus.requestFocus() }

    selectedId?.let { id ->
        DeliveryDetailScreen(
            deliveryId = id,
            viewModel = viewModel,
            onBack = { selectedId = null }
        )
        return
    }

    val visible = state.visible

    TridjayaCollapsibleHeader(
        title = "Pengiriman",
        onBack = onBack,
        actions = {
            ExpressiveFilledIconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.onSearchChange("")
                }
            ) {
                Icon(
                    if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (showSearch) "Tutup pencarian" else "Cari pengiriman"
                )
            }
        }
    ) { contentModifier ->
        Box(modifier = contentModifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ExpressiveTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(searchFocus),
                        placeholder = "Cari pelanggan, barang, atau cabang…",
                        trailingIcon = if (state.search.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.onSearchChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus teks")
                                }
                            }
                        } else null
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    item(key = "status_filter") {
                        DeliveryStatusChips(
                            items = state.items,
                            selected = state.statusFilter,
                            onSelect = viewModel::setStatusFilter
                        )
                    }
                    if (visible.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ExpressiveEmptyState(
                                    icon = { Icon(Icons.Rounded.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    title = "Belum ada pengiriman",
                                    subtitle = "Tekan tombol Input SPK untuk membuat pesanan baru"
                                )
                            }
                        }
                    } else {
                        items(visible, key = { it.id }) { delivery ->
                            DeliveryCard(
                                delivery = delivery,
                                onClick = { selectedId = delivery.id }
                            )
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { showSpkForm = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Input SPK") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }

    if (showSpkForm) {
        SpkFormSheet(
            defaultSales = viewModel.currentSalesName,
            defaultBranch = viewModel.currentBranch,
            onDismiss = { showSpkForm = false },
            onSubmit = { customer, phone, item, qty, payment, address, branch, value, note ->
                val id = viewModel.createSpk(
                    customerName = customer,
                    customerPhone = phone,
                    itemName = item,
                    quantity = qty,
                    paymentStatus = payment,
                    address = address,
                    senderBranch = branch,
                    salesName = viewModel.currentSalesName,
                    estimatedValue = value,
                    note = note
                )
                showSpkForm = false
                selectedId = id
            }
        )
    }
}

@Composable
private fun DeliveryStatusChips(
    items: List<DeliveryDto>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    val counts = remember(items) { items.groupingBy { it.status.lowercase() }.eachCount() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { selected?.let(onSelect) },
                label = { Text("Semua (${items.size})") },
                shape = RoundedCornerShape(50)
            )
        }
        items(DeliveryStatus.entries.toList()) { status ->
            val count = counts[status.key] ?: 0
            FilterChip(
                selected = selected == status.key,
                onClick = { onSelect(status.key) },
                label = { Text(if (count > 0) "${status.label} ($count)" else status.label) },
                leadingIcon = {
                    Box(modifier = Modifier.size(8.dp).background(status.color, CircleShape))
                },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/** Kartu ringkas pengiriman: badge status, pelanggan + barang, alamat, cabang + payment + WA cepat. */
@Composable
private fun DeliveryCard(delivery: DeliveryDto, onClick: () -> Unit) {
    val status = DeliveryStatus.from(delivery.status)
    val payment = DeliveryPayment.from(delivery.paymentStatus)
    val context = LocalContext.current

    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeliveryStatusBadge(status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDeliveryDate(delivery.scheduledDate ?: delivery.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(status.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = delivery.customerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (delivery.quantity > 1) "${delivery.itemName} · ${delivery.quantity} unit" else delivery.itemName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!delivery.customerPhone.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            openDeliveryWhatsApp(
                                context,
                                delivery.customerPhone,
                                buildDeliveryMessage(delivery.customerName, delivery.itemName, status)
                            )
                        },
                        shape = CircleShape,
                        color = Color(0xFF25D366).copy(alpha = 0.14f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Chat,
                                contentDescription = "Chat WhatsApp ${delivery.customerName}",
                                tint = Color(0xFF1B9E4B),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = delivery.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeliveryPaymentBadge(payment)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = delivery.senderBranch,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Lihat Detail",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun DeliveryStatusBadge(status: DeliveryStatus) {
    Surface(color = status.color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status.label,
                color = status.color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun DeliveryPaymentBadge(payment: DeliveryPayment) {
    Surface(color = payment.color.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
        Text(
            text = payment.label,
            color = payment.color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
        )
    }
}

/**
 * Form Input SPK (Surat Pesanan) oleh sales — membuat order pengiriman baru. Field inti selaras
 * kontrak backend `POST /api/sales/delivery-schedules`; nama sales & cabang terisi otomatis dari
 * akun login. Wajib: pelanggan, barang, alamat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpkFormSheet(
    defaultSales: String,
    defaultBranch: String,
    onDismiss: () -> Unit,
    onSubmit: (
        customer: String, phone: String, item: String, qty: Int, payment: String,
        address: String, branch: String, value: Double, note: String
    ) -> Unit
) {
    var customer by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var item by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var payment by remember { mutableStateOf(DeliveryPayment.CASH) }
    var address by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf(defaultBranch) }
    var valueText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val canSubmit = customer.isNotBlank() && item.isNotBlank() && address.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text("Input SPK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Sales: ${defaultSales.ifBlank { "-" }} · order baru masuk tahap SPK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            ExpressiveTextField(value = customer, onValueChange = { customer = it }, label = "Nama Pelanggan", modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveTextField(value = phone, onValueChange = { phone = it }, label = "No. WhatsApp (opsional)", modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveTextField(value = item, onValueChange = { item = it }, label = "Nama Barang", modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveTextField(value = qtyText, onValueChange = { qtyText = it.filter { c -> c.isDigit() } }, label = "Jumlah", modifier = Modifier.width(110.dp))
                ExpressiveTextField(value = valueText, onValueChange = { valueText = it.filter { c -> c.isDigit() } }, label = "Perkiraan Nilai (Rp)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text("Pembayaran", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeliveryPayment.entries.forEach { p ->
                    FilterChip(
                        selected = payment == p,
                        onClick = { payment = p },
                        label = { Text(p.label) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveTextField(value = address, onValueChange = { address = it }, label = "Alamat Pengiriman", singleLine = false, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveTextField(value = branch, onValueChange = { branch = it }, label = "Cabang Pengirim", modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveTextField(value = note, onValueChange = { note = it }, label = "Catatan (opsional)", singleLine = false, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(20.dp))
            ExpressiveFilledButton(
                onClick = {
                    onSubmit(
                        customer, phone, item, qtyText.toIntOrNull() ?: 1, payment.key,
                        address, branch.ifBlank { defaultBranch }, valueText.toDoubleOrNull() ?: 0.0, note
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buat SPK")
            }
        }
    }
}
