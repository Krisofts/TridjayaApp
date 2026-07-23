package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/** Hub alur SPK — satu pintu: input, riwayat, antrian per tahap, approval diskon.
 *  Semua entri tampil utk semua role (RBAC di backend, konsisten kebijakan tile lama). */
@Composable
fun SpkHubScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    TridjayaCollapsibleHeader(title = "SPK & Pengiriman", onBack = onBack) { contentModifier ->
        Column(
            contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HubEntry(Icons.Rounded.Description, "Input SPK", "Catat penjualan baru (multi-barang)", Color(0xFF1E63E9)) { onNavigate("input") }
            HubEntry(Icons.Rounded.History, "Riwayat SPK", "Semua SPK & status terkini", Color(0xFF667085)) { onNavigate("history") }
            Spacer(Modifier.height(4.dp))
            Text("Antrian per tahap", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HubEntry(Icons.Rounded.Discount, "Approval Diskon", "Setujui/tolak pengajuan diskon", Color(0xFFB5670C)) { onNavigate("diskon") }
            HubEntry(Icons.Rounded.FactCheck, "PDI", "Inspeksi unit sebelum kirim", Color(0xFF6941C6)) { onNavigate("pdi") }
            HubEntry(Icons.Rounded.BatteryChargingFull, "Pengambilan Aki", "Daftar form aki + tandai dikembalikan", Color(0xFF9C27B0)) { onNavigate("aki") }
            HubEntry(Icons.Rounded.PointOfSale, "Kasir SPK", "Konfirmasi SPK ke GS", Color(0xFF0086C9)) { onNavigate("kasir") }
            HubEntry(Icons.Rounded.Receipt, "Surat Jalan", "Terbitkan surat jalan", Color(0xFF0E9384)) { onNavigate("note") }
            HubEntry(Icons.Rounded.CalendarToday, "Penjadwalan", "Assign driver + jadwal kirim", Color(0xFF1565C0)) { onNavigate("jadwal") }
            HubEntry(Icons.Rounded.LocalShipping, "Tugas Antar (Driver)", "Berangkat, chat konsumen, serah terima", Color(0xFF12B76A)) { onNavigate("driver") }
        }
    }
}

@Composable
private fun HubEntry(icon: ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
