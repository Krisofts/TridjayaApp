package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** SATU sumber ambang deadstock untuk seluruh app: chip filter Deadstock, mode FRESH SALE, dan
 *  label badge ini semuanya memakai [com.krisoft.tridjayaelektronik.data.local.DEADSTOCK_MIN_DAYS]
 *  (180 hari, pilihan user) — jangan buat konstanta tandingan lagi; dulu badge sempat 90 hari
 *  sendiri dan produk berlabel Deadstock malah hilang saat difilter Deadstock. */
private val DEADSTOCK_THRESHOLD = com.krisoft.tridjayaelektronik.data.local.DEADSTOCK_MIN_DAYS
private const val DANGER_MIN_DAYS = 90L
private const val WARNING_MIN_DAYS = 30L

/** Warna aging: hijau (<30 hr), oranye (30–89 hr), merah (>=90 hr; label Deadstock mulai 180). */
fun agingColor(umurHari: Long): Color = when {
    umurHari >= DANGER_MIN_DAYS -> Color(0xFFC62828)
    umurHari >= WARNING_MIN_DAYS -> Color(0xFFB5670C)
    else -> Color(0xFF2E7D32)
}

fun isDeadstock(umurHari: Long): Boolean = umurHari >= DEADSTOCK_THRESHOLD

/**
 * Badge umur stok (aging) — porting kolom Kondisi dari web Stok All Cabang.
 * Tampil "N hr" (atau "Deadstock · N hr" untuk stok >= 180 hari) dengan tint sesuai umur.
 */
@Composable
fun AgingBadge(umurHari: Long, modifier: Modifier = Modifier) {
    val color = agingColor(umurHari)
    val label = if (isDeadstock(umurHari)) "Deadstock · $umurHari hr" else "$umurHari hr"
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.13f), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
