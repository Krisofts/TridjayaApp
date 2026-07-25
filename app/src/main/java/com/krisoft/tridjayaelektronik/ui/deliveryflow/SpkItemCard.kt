package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.model.BrokerOption
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import kotlinx.coroutines.launch
import java.io.File

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
    /** Watermark+upload foto PO barang ini, return URL (null = gagal). */
    uploadPoPhoto: suspend (File) -> String?,
    /** Metode pengiriman SPK (header, bukan per-barang): "driver" | "self_pickup" |
     *  "sales_delivery". COD (uang diambil driver) cuma relevan "driver" (2026-07-26). */
    deliveryMethod: String = "driver",
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
                ExpressiveTextField(
                    item.serialNumber, { onUpdate(item.copy(serialNumber = it)) }, label = "No. Rangka/Serial (opsional)", modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { BarcodeScanButton { sn -> onUpdate(item.copy(serialNumber = sn)) } }
                )
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

                // Metode PDI: "Tidak PDI" = skip total (cuma valid metode "driver").
                // Utk "Diambil Sendiri"/"Sales Antar Sendiri" (2026-07-26) opsi kedua jadi
                // "PDI Mandiri" — checklist+foto TETAP wajib, cuma dikerjakan sales sendiri
                // (langsung diarahkan ke form PDI begitu SPK ini selesai dibuat), bukan skip.
                val isSelfPdiMethod = deliveryMethod == "self_pickup" || deliveryMethod == "sales_delivery"
                Spacer(Modifier.height(10.dp))
                Text("Metode PDI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "PDI (tim PDI)", false to if (isSelfPdiMethod) "PDI Mandiri" else "Tidak PDI").forEach { (v, l) ->
                        val sel = item.pdiRequired == v
                        Surface(onClick = { onUpdate(item.copy(pdiRequired = v)) }, shape = RoundedCornerShape(50), color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }
                }
                if (!item.pdiRequired) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isSelfPdiMethod)
                            "Sales isi checklist PDI + foto unit sendiri — langsung diarahkan ke form-nya begitu SPK ini selesai dibuat. Kasir baru bisa proses setelah PDI mandiri ini lengkap."
                        else
                            "Barang lompat langsung ke kasir tanpa checklist PDI — sales bertanggung jawab cek barang sendiri.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Pembayaran", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to "Cash", "credit" to "Kredit").forEach { (k, l) ->
                        val sel = item.paymentType == k
                        Surface(
                            onClick = {
                                onUpdate(
                                    if (k == "cash") item.copy(paymentType = k, fincoy = "", fincoyLain = "", preOrderId = "", poPhotoUrl = "")
                                    else item.copy(paymentType = k, driverTerimaUang = false, codPaymentMode = "", codDpAmount = "")
                                )
                            },
                            shape = RoundedCornerShape(50), color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)
                        ) {
                            Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }
                }
                if (item.isCredit) {
                    // Pre Order ID + foto PO cuma relevan buat kredit (leasing/fincoy
                    // butuh bukti PO), tetap opsional (koreksi 2026-07-26 — sebelumnya
                    // tampil unconditional buat cash & kredit).
                    Spacer(Modifier.height(10.dp))
                    ExpressiveTextField(item.preOrderId, { onUpdate(item.copy(preOrderId = it)) }, label = "Pre Order ID (opsional)", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    PoPhotoField(poPhotoUrl = item.poPhotoUrl, onUploaded = { url -> onUpdate(item.copy(poPhotoUrl = url)) }, uploadPoPhoto = uploadPoPhoto)
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

                // COD Full Payment/DP (2026-07-25, cash-only) — mirror web SalesDeliveryFlowPage.
                // driverTerimaNominal DIHITUNG backend dari hargaOtr+codPaymentMode+codDpAmount,
                // bukan lagi input manual (cegah mismatch DP vs sisa).
                if (!item.isCredit && deliveryMethod == "driver") {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val next = !item.driverTerimaUang
                            onUpdate(item.copy(driverTerimaUang = next, codPaymentMode = if (next) item.codPaymentMode else "", codDpAmount = if (next) item.codDpAmount else ""))
                        }
                    ) {
                        Checkbox(checked = item.driverTerimaUang, onCheckedChange = { checked ->
                            onUpdate(item.copy(driverTerimaUang = checked, codPaymentMode = if (checked) item.codPaymentMode else "", codDpAmount = if (checked) item.codDpAmount else ""))
                        })
                        Text("COD (uang diambil driver saat kirim)", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (item.driverTerimaUang) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf("full" to "Full Payment", "dp" to "DP").forEach { (k, l) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onUpdate(item.copy(codPaymentMode = k)) }
                                ) {
                                    RadioButton(selected = item.codPaymentMode == k, onClick = { onUpdate(item.copy(codPaymentMode = k)) })
                                    Text(l, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (item.codPaymentMode == "dp") {
                            Spacer(Modifier.height(6.dp))
                            ExpressiveTextField(item.codDpAmount, { onUpdate(item.copy(codDpAmount = it.filter { c -> c.isDigit() })) }, label = "Jumlah DP *", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            val otr = item.hargaOtr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                            val dp = item.codDpAmount.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                            Text(
                                "Sisa diambil driver: ${formatRupiahSimple((otr - dp).coerceAtLeast(0.0))}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (item.codPaymentMode == "full") {
                            Spacer(Modifier.height(4.dp))
                            val otr = item.hargaOtr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                            Text(
                                "Sisa diambil driver: ${formatRupiahSimple(otr)} (penuh)",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

private fun formatRupiahSimple(value: Double): String =
    "Rp" + value.toLong().toString().reversed().chunked(3).joinToString(".").reversed()

/** Foto PO per-barang: capture kamera → watermark → upload langsung (bukan
 *  slot review terpisah spt PDI/deliver — pola sama web `uploadDeliveryPhoto`
 *  on-file-select, cukup 1x aksi). File cache per-composable-instance (aman
 *  walau ada beberapa kartu barang sekaligus di layar). */
@Composable
private fun PoPhotoField(
    poPhotoUrl: String,
    onUploaded: (String) -> Unit,
    uploadPoPhoto: suspend (File) -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val file = remember { File(context.cacheDir, "delivery/po_item_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        uploading = true
        error = null
        scope.launch {
            val url = uploadPoPhoto(file)
            uploading = false
            if (url != null) onUploaded(url) else error = "Gagal unggah foto PO"
        }
    }

    Text("Foto PO (opsional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    if (poPhotoUrl.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Foto PO terunggah", style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = { onUploaded("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus foto PO") }
        }
    } else {
        Surface(
            onClick = { if (!uploading) cam.launch(uri) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (uploading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Mengunggah…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ambil / unggah foto PO", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (error != null) {
        Spacer(Modifier.height(4.dp))
        Text(error!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
