package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.data.model.BrokerOption
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField

/**
 * Kartu satu barang SPK multi-unit — tiap barang bawa pembayaran/komisi/order sendiri
 * (mirror kartu item web SalesDeliveryFlowPage). Collapsible utk layar sempit.
 */
@Composable
fun SpkItemCard(
    index: Int,
    item: SpkItemDraft,
    issues: List<String>,
    serialOptions: List<String>,
    brokerResults: List<BrokerOption>,
    brokerSearch: String,
    onBrokerSearch: (String) -> Unit,
    onUpdate: (SpkItemDraft) -> Unit,
    onRemove: () -> Unit,
    onSerialFocus: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            // Header: Barang #N + ringkasan + hapus + expand
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onUpdate(item.copy(expanded = !item.expanded)) }) {
                Column(Modifier.weight(1f)) {
                    Text("Barang #${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(if (item.expanded) item.namaBarang else item.summaryLine(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.expanded) Text("${item.kodeBarang} · ${item.kategori} · ${item.merk}" + (item.stokTersedia?.let { " · stok $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus barang #${index + 1}", tint = MaterialTheme.colorScheme.error) }
                Icon(if (item.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (item.expanded) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveTextField(item.warna, { onUpdate(item.copy(warna = it)) }, label = "Warna", modifier = Modifier.weight(1f))
                    ExpressiveTextField(item.qty, { onUpdate(item.copy(qty = it.filter { c -> c.isDigit() })) }, label = "Qty" + (item.stokTersedia?.let { " (stok $it)" } ?: ""), keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(item.serialNumber, { onUpdate(item.copy(serialNumber = it)) }, label = "No. Rangka/Serial (opsional)", modifier = Modifier.fillMaxWidth())
                val availSerial = serialOptions.filter { it != item.serialNumber }
                if (availSerial.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Serial tersedia (ketuk):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availSerial.take(5).forEach { sn ->
                            Surface(onClick = { onUpdate(item.copy(serialNumber = sn)) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                                Text(sn, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                } else {
                    // Trigger fetch lazy saat kartu dibuka (fail-soft, cache di VM)
                    onSerialFocus()
                }
                Spacer(Modifier.height(10.dp))
                ExpressiveTextField(item.hargaOtr, { onUpdate(item.copy(hargaOtr = it.filter { c -> c.isDigit() })) }, label = if (item.isCredit) "Harga OTR *" else "Harga Jual *", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveTextField(item.diskon, { onUpdate(item.copy(diskon = it.filter { c -> c.isDigit() })) }, label = "Diskon", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    ExpressiveTextField(item.alasanDiskon, { onUpdate(item.copy(alasanDiskon = it)) }, label = if ((item.diskon.toLongOrNull() ?: 0L) > 0) "Alasan diskon *" else "Alasan diskon", modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))
                Text("Pembayaran", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to "Cash", "credit" to "Kredit").forEach { (k, l) ->
                        val sel = item.paymentType == k
                        Surface(onClick = { onUpdate(if (k == "cash") item.copy(paymentType = k, fincoy = "", fincoyLain = "") else item.copy(paymentType = k)) }, shape = RoundedCornerShape(50), color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }
                }
                if (item.isCredit) {
                    Spacer(Modifier.height(10.dp))
                    ItemFincoyDropdown(item.fincoy) { onUpdate(item.copy(fincoy = it)) }
                    if (item.fincoy == FINCOY_LAINNYA) {
                        Spacer(Modifier.height(8.dp))
                        ExpressiveTextField(item.fincoyLain, { onUpdate(item.copy(fincoyLain = it)) }, label = "Nama fincoy/leasing lain *", modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveTextField(item.dpNet, { onUpdate(item.copy(dpNet = it.filter { c -> c.isDigit() })) }, label = "DP Net", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                        ExpressiveTextField(item.pembayaran1, { onUpdate(item.copy(pembayaran1 = it.filter { c -> c.isDigit() })) }, label = "Pembayaran 1", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveTextField(item.angsuran, { onUpdate(item.copy(angsuran = it.filter { c -> c.isDigit() })) }, label = "Angsuran", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                        ExpressiveTextField(item.tenor, { onUpdate(item.copy(tenor = it.filter { c -> c.isDigit() })) }, label = "Tenor (bln)", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onUpdate(item.copy(driverTerimaUang = !item.driverTerimaUang)) }) {
                    Checkbox(checked = item.driverTerimaUang, onCheckedChange = { onUpdate(item.copy(driverTerimaUang = it)) })
                    Text("Driver terima uang dari konsumen", style = MaterialTheme.typography.bodyMedium)
                }
                if (item.driverTerimaUang) {
                    ExpressiveTextField(item.nominalTerimaUang, { onUpdate(item.copy(nominalTerimaUang = it.filter { c -> c.isDigit() })) }, label = "Nominal diterima driver *", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(12.dp))
                Text("Sumber Order", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sales" to "Sales", "kbk" to "KBK").forEach { (k, l) ->
                        val sel = item.orderSource == k
                        Surface(onClick = { onUpdate(if (k == "sales") item.copy(orderSource = k, kbkBrokerKode = "", kbkBrokerNama = "") else item.copy(orderSource = k)) }, shape = RoundedCornerShape(50), color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (!item.isKbk) {
                    ExpressiveTextField(item.komisiSales, { onUpdate(item.copy(komisiSales = it.filter { c -> c.isDigit() })) }, label = "Komisi Sales", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                } else {
                    if (item.kbkBrokerKode.isBlank()) {
                        ExpressiveTextField(brokerSearch, onBrokerSearch, label = "Cari broker KBK (min. 2 karakter) *", modifier = Modifier.fillMaxWidth())
                        if (brokerResults.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                brokerResults.forEach { b ->
                                    Surface(onClick = { onUpdate(item.copy(kbkBrokerKode = b.kode, kbkBrokerNama = b.nama)); onBrokerSearch("") }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text(b.nama, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(b.kode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.kbkBrokerNama.ifBlank { item.kbkBrokerKode }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(item.kbkBrokerKode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onUpdate(item.copy(kbkBrokerKode = "", kbkBrokerNama = "")) }) { Text("Ganti") }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveTextField(item.komisiKbk, { onUpdate(item.copy(komisiKbk = it.filter { c -> c.isDigit() })) }, label = "Komisi KBK", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                        ExpressiveTextField(item.noHpKbk, { onUpdate(item.copy(noHpKbk = it)) }, label = "No. HP KBK", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
                    }
                }

                if (issues.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        issues.forEach { Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
            }
        }
    }
}

/** Dropdown fincoy per-item (pola CabangSelector). */
@Composable
private fun ItemFincoyDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) { "" -> "Pilih leasing…"; FINCOY_LAINNYA -> "Lainnya…"; else -> selected }
    Column {
        Text("Fincoy / Leasing *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)).clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                FINCOY_PARTNERS.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { onSelect(p); expanded = false }) }
                DropdownMenuItem(text = { Text("Lainnya…") }, onClick = { onSelect(FINCOY_LAINNYA); expanded = false })
            }
        }
    }
}
