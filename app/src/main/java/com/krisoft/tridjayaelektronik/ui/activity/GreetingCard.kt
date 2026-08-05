package com.krisoft.tridjayaelektronik.ui.activity

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Soft 3D sphere decoration (radial highlight off-center), same clay language as the flyers. */
private fun DrawScope.greetingSphere(cx: Float, cy: Float, r: Float, base: Color, alpha: Float = 1f) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(base, Color.White, 0.55f), base, lerp(base, Color.Black, 0.08f)),
            center = Offset(cx - r * 0.35f, cy - r * 0.4f),
            radius = r * 1.7f
        ),
        radius = r,
        center = Offset(cx, cy),
        alpha = alpha
    )
}

private data class GreetingUi(
    val label: String,
    val quote: String,
    val icon: ImageVector,
    /** Gradien latar kartu greeting. */
    val gradient: List<Color>,
    /** Warna teks di atas gradien. */
    val onColor: Color,
    /** Tint ikon (harus kontras dengan badge putih). */
    val iconTint: Color
)

/** Tema sapaan per waktu — background + ikon berubah pagi/siang/sore/malam. */
private fun timeGreeting(hour: Int): GreetingUi = when (hour) {
    in 5..10 -> GreetingUi(   // Pagi — sunrise hangat
        "Selamat Pagi",
        "Awali harimu dengan senyuman — semoga rezeki & closing mengalir deras hari ini!",
        Icons.Rounded.WbSunny,
        listOf(Color(0xFFFFD07A), Color(0xFFFF9F5A)),
        Color(0xFF3E2A12),
        Color(0xFFF57C00)
    )
    in 11..14 -> GreetingUi(  // Siang — langit cerah
        "Selamat Siang",
        "Tetap semangat & fokus — jangan lupa follow up prospekmu, ya!",
        Icons.Rounded.LightMode,
        listOf(Color(0xFF57ABFF), Color(0xFF2E7CF6)),
        Color.White,
        Color(0xFFFB8C00)
    )
    in 15..18 -> GreetingUi(  // Sore — sunset
        "Selamat Sore",
        "Sedikit lagi! Cek progres targetmu sebelum hari berakhir.",
        Icons.Rounded.WbTwilight,
        listOf(Color(0xFFFF9E6D), Color(0xFFE8577D)),
        Color.White,
        Color(0xFFF4511E)
    )
    else -> GreetingUi(       // Malam
        "Selamat Malam",
        "Kerja kerasmu hari ini luar biasa. Istirahat yang cukup, ya!",
        Icons.Rounded.DarkMode,
        listOf(Color(0xFF3B4A8C), Color(0xFF1E2547)),
        Color.White,
        Color(0xFF3F51B5)
    )
}

/** Tema sapaan musiman (override tema waktu). Tambah case lain untuk perayaan berikutnya
 *  (mis. Desember/Tahun Baru, Ramadan) — tinggal daftarkan bulannya di sini. */
private fun seasonalGreeting(month: Int): GreetingUi? = when (month) {
    Calendar.AUGUST -> GreetingUi(   // Agustus — Kemerdekaan RI
        "Dirgahayu Indonesia",
        "Merdeka! Bawa semangat 45 untuk gebrak target bulan ini.",
        Icons.Rounded.Flag,
        listOf(Color(0xFFF44336), Color(0xFFB71C1C)),
        Color.White,
        Color(0xFFE53935)
    )
    else -> null
}

/**
 * Kartu sapaan. **Pindah dari `ui/home/HomeScreen.kt` ke sini** — sejak redesain
 * Activity, sapaan milik layar pertama app (Activity), bukan dashboard lama
 * (tab Operasional). Di Operasional slot itu kini murni milik `EventCarousel`:
 * kalau tak ada event aktif, tak ada apa-apa di sana.
 *
 * Sengaja `internal` (bukan `private`) supaya bisa tinggal di file sendiri,
 * terpisah dari `ActivityScreen.kt` yang sudah panjang.
 *
 * [cabang] ikut di baris tanggal — dulu `GreetingRow` di Activity menampilkan
 * "tanggal · cabang", dan kartu ini menggantikannya, jadi info cabang tak boleh
 * hilang. Kosong = baris tanggal saja (mis. dipakai di layar tanpa data cabang).
 */
@Composable
internal fun GreetingCard(userName: String, cabang: String = "") {
    val cal = remember { Calendar.getInstance() }
    val hour = remember { cal.get(Calendar.HOUR_OF_DAY) }
    val month = remember { cal.get(Calendar.MONTH) }
    // Musiman (mis. Agustus) menimpa tema waktu; kalau tidak, pakai tema pagi/siang/sore/malam.
    val greeting = remember(hour, month) { seasonalGreeting(month) ?: timeGreeting(hour) }
    val tanggal = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("in", "ID")).format(cal.time) }
    val subtitle = remember(tanggal, cabang) {
        listOf(tanggal, cabang).filter { it.isNotBlank() }.joinToString(" · ")
    }

    // ── Animasi berkelanjutan ────────────────────────────────────────────────
    val transition = rememberInfiniteTransition(label = "greeting")
    val iconScale by transition.animateFloat(
        1f, 1.08f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconScale"
    )
    val iconBob by transition.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconBob"
    )
    // Gradien latar "bernapas" — geser titik awal/akhir pelan.
    val gradientPan by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradientPan"
    )
    // Sphere dekoratif mengambang.
    val sphereDrift by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sphereDrift"
    )
    // Kilau (shimmer) menyapu diagonal, jeda di antara sapuan.
    val shimmer by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    // Entrance: muncul dengan fade + naik sedikit.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "enter"
    )

    val shape = RoundedCornerShape(26.dp)
    val base = greeting.gradient.first()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .graphicsLayer { alpha = enter; translationY = (1f - enter) * 24.dp.toPx() }
            .shadow(12.dp, shape, ambientColor = base.copy(alpha = 0.5f), spotColor = base.copy(alpha = 0.5f))
            .clip(shape)
            .drawBehind {
                val w = size.width
                val h = size.height
                // 1) Gradien latar bergerak pelan.
                val pan = (gradientPan - 0.5f) * w * 0.45f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = greeting.gradient,
                        start = Offset(pan, 0f),
                        end = Offset(w + pan, h)
                    )
                )
                // 2) Sheen glossy di atas.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.20f),
                        0.45f to Color.Transparent
                    )
                )
                // 3) Sphere mengambang.
                val d = (sphereDrift - 0.5f) * 14.dp.toPx()
                greetingSphere(w * 0.90f, h * 0.18f + d, 30.dp.toPx(), Color.White, alpha = 0.16f)
                greetingSphere(w * 0.80f, h * 0.92f - d, 18.dp.toPx(), Color.White, alpha = 0.14f)
                greetingSphere(w * 0.97f, h * 0.62f + d * 0.6f, 12.dp.toPx(), Color.White, alpha = 0.12f)
                // 4) Kilau menyapu (band diagonal translusen).
                val band = w * 0.16f
                val sx = -band + shimmer * (w + band * 2)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                        start = Offset(sx - band, 0f),
                        end = Offset(sx + band, h)
                    )
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ikon vektor di badge kaca putih, ditint tema, dengan pulse + bob halus.
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        translationY = iconBob * 5.dp.toPx()
                    }
                    .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
                    .background(Color.White.copy(alpha = 0.94f), CircleShape)
                    .size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = greeting.icon,
                    contentDescription = null,
                    tint = greeting.iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userName.isBlank()) greeting.label else "${greeting.label}, ${userName.substringBefore(' ')}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = greeting.onColor
                )
                Text(
                    text = greeting.quote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = greeting.onColor.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = greeting.onColor.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
