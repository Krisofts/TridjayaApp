package com.krisoft.tridjayaelektronik.ui.birthday

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dekorasi kartu ucapan ulang tahun — padanan `BirthdayCardArt.tsx` di web.
 *
 * Dipisah dari [BirthdayPopup] dengan alasan yang sama seperti di web: berkas
 * popup soal logika (muat, halaman, tutup), berkas ini soal rupa. Semuanya
 * digambar `Canvas`, bukan berkas gambar — balon mengilap, pita melengkung,
 * dan ukiran sudut tak bisa dibuat meyakinkan dari kotak berwarna, dan bentuk
 * vektor tetap tajam di layar kepadatan berapa pun tanpa menambah aset.
 *
 * Warna hex mentah, bukan token tema: kartu ini meniru desain cetak
 * perusahaan yang warnanya tetap (biru dongker + emas) — pengecualian yang
 * sama persis dengan yang sudah ditulis di versi web.
 */

val NAVY_DEEP = Color(0xFF08183C)
val NAVY_MID = Color(0xFF14306E)
val NAVY_SOFT = Color(0xFF1D3F8A)
val NAVY_CREAM = Color(0xFFFDF8EC)

val GOLD_DARK = Color(0xFFA97C22)
val GOLD_BASE = Color(0xFFD9A441)
val GOLD_LIGHT = Color(0xFFF4D692)
val GOLD_PALE = Color(0xFFFFF3D4)

/**
 * Posisi confetti & kilau dihitung deterministik dari indeks, BUKAN acak —
 * catatan yang sama ada di versi web: dengan `Random`, tiap recomposition
 * memindahkan semua kepingan sekaligus dan terlihat berkedip.
 */
data class ConfettiPiece(
    val xFraction: Float,
    val phase: Float,
    val speed: Float,
    val sizePx: Float,
    val color: Color,
    val round: Boolean,
)

private val CONFETTI_COLORS = listOf(GOLD_BASE, GOLD_LIGHT, GOLD_PALE, Color.White)

val CONFETTI: List<ConfettiPiece> = List(26) { i ->
    ConfettiPiece(
        xFraction = ((i * 41) % 100) / 100f,
        phase = ((i * 17) % 45) / 45f,
        speed = 0.55f + ((i * 11) % 30) / 40f,
        sizePx = 5f + (i % 3) * 3f,
        color = CONFETTI_COLORS[i % CONFETTI_COLORS.size],
        round = i % 3 == 0,
    )
}

data class SparkleSpot(val xFraction: Float, val yFraction: Float, val sizePx: Float, val phase: Float)

val SPARKLES: List<SparkleSpot> = List(14) { i ->
    SparkleSpot(
        xFraction = ((i * 29 + 7) % 92) / 100f,
        yFraction = ((i * 47 + 12) % 88) / 100f,
        sizePx = 12f + (i % 3) * 7f,
        phase = ((i * 23) % 40) / 40f,
    )
}

/** Naik-turun halus balon, dari satu jam bersama supaya tak ada animasi per-elemen. */
fun bob(progress: Float, phase: Float, amplitude: Float): Float =
    sin(((progress + phase) * 2f * PI).toFloat()) * amplitude

// ── Bentuk ───────────────────────────────────────────────────────────────────

/** Balon emas mengilap + tali melengkung. `unit` = tinggi balon dalam px. */
fun DrawScope.drawBalloon(center: Offset, unit: Float, alpha: Float = 1f) {
    val rx = unit * 0.23f
    val ry = unit * 0.29f
    translate(center.x, center.y) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(GOLD_PALE, GOLD_BASE, GOLD_DARK),
                center = Offset(-rx * 0.3f, -ry * 0.4f),
                radius = rx * 2.2f,
            ),
            topLeft = Offset(-rx, -ry),
            size = Size(rx * 2, ry * 2),
            alpha = alpha,
        )
        // Kilau — ini yang bikin balon terbaca "mengilap", bukan sekadar oval.
        rotate(degrees = -22f, pivot = Offset(-rx * 0.35f, -ry * 0.35f)) {
            drawOval(
                color = Color.White.copy(alpha = 0.55f * alpha),
                topLeft = Offset(-rx * 0.35f - rx * 0.28f, -ry * 0.35f - ry * 0.33f),
                size = Size(rx * 0.56f, ry * 0.66f),
            )
        }
        // Simpul.
        val knot = Path().apply {
            moveTo(-unit * 0.04f, ry)
            lineTo(0f, ry + unit * 0.05f)
            lineTo(unit * 0.04f, ry)
            close()
        }
        drawPath(knot, GOLD_DARK, alpha = alpha)
        // Tali: dua lengkung berlawanan, bukan garis lurus.
        val tali = Path().apply {
            moveTo(0f, ry + unit * 0.05f)
            quadraticBezierTo(unit * 0.07f, ry + unit * 0.17f, -unit * 0.03f, ry + unit * 0.25f)
            quadraticBezierTo(-unit * 0.12f, ry + unit * 0.33f, -unit * 0.01f, ry + unit * 0.45f)
        }
        drawPath(tali, GOLD_LIGHT, alpha = 0.75f * alpha, style = Stroke(width = unit * 0.014f))
    }
}

/** Pita menjuntai dari sudut atas. `flip` = cermin untuk sudut kanan. */
fun DrawScope.drawStreamer(origin: Offset, height: Float, flip: Boolean, alpha: Float = 0.85f) {
    val w = height * 0.55f
    translate(origin.x, origin.y) {
        scale(scaleX = if (flip) -1f else 1f, scaleY = 1f, pivot = Offset.Zero) {
            val kuas = Brush.linearGradient(
                colors = listOf(GOLD_LIGHT, GOLD_BASE, GOLD_DARK),
                start = Offset.Zero,
                end = Offset(w, height),
            )
            val tebal = Path().apply {
                moveTo(w * 0.05f, 0f)
                quadraticBezierTo(w * 0.33f, height * 0.2f, w * 0.08f, height * 0.37f)
                quadraticBezierTo(w * -0.2f, height * 0.55f, w * 0.1f, height * 0.72f)
                quadraticBezierTo(w * 0.33f, height * 0.86f, w * 0.02f, height)
            }
            drawPath(tebal, kuas, alpha = alpha, style = Stroke(width = height * 0.041f))
            val tipis = Path().apply {
                moveTo(w * 0.33f, 0f)
                quadraticBezierTo(w * 0.55f, height * 0.24f, w * 0.28f, height * 0.42f)
                quadraticBezierTo(w * 0.08f, height * 0.62f, w * 0.36f, height * 0.8f)
            }
            drawPath(tipis, kuas, alpha = alpha * 0.6f, style = Stroke(width = height * 0.018f))
        }
    }
}

/**
 * Ukiran sudut — memberi kesan kartu cetak, bukan kotak layar.
 * `flipX`/`flipY` memutar bentuk yang sama ke tiga sudut lain.
 */
fun DrawScope.drawCornerFlourish(origin: Offset, unit: Float, flipX: Boolean, flipY: Boolean) {
    translate(origin.x, origin.y) {
        scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, pivot = Offset.Zero) {
            val luar = Path().apply {
                moveTo(0f, unit * 0.47f)
                quadraticBezierTo(0f, 0f, unit * 0.47f, 0f)
            }
            drawPath(luar, GOLD_BASE, style = Stroke(width = unit * 0.028f))
            val dalam = Path().apply {
                moveTo(0f, unit * 0.66f)
                quadraticBezierTo(0f, 0f, unit * 0.66f, 0f)
            }
            drawPath(dalam, GOLD_LIGHT, alpha = 0.7f, style = Stroke(width = unit * 0.014f))
            val sulur = Path().apply {
                moveTo(unit * 0.11f, unit * 0.36f)
                quadraticBezierTo(unit * 0.16f, unit * 0.11f, unit * 0.39f, unit * 0.05f)
            }
            drawPath(sulur, GOLD_LIGHT, alpha = 0.5f, style = Stroke(width = unit * 0.014f))
            drawCircle(GOLD_LIGHT, radius = unit * 0.031f, center = Offset(unit * 0.47f, 0f))
            drawCircle(GOLD_LIGHT, radius = unit * 0.031f, center = Offset(0f, unit * 0.47f))
        }
    }
}

/** Bintang empat sudut yang berkelip di area kosong. */
fun DrawScope.drawSparkle(center: Offset, size: Float, alpha: Float) {
    val h = size / 2f
    val tipis = size * 0.09f
    val star = Path().apply {
        moveTo(center.x, center.y - h)
        lineTo(center.x + tipis, center.y - tipis)
        lineTo(center.x + h, center.y)
        lineTo(center.x + tipis, center.y + tipis)
        lineTo(center.x, center.y + h)
        lineTo(center.x - tipis, center.y + tipis)
        lineTo(center.x - h, center.y)
        lineTo(center.x - tipis, center.y - tipis)
        close()
    }
    drawPath(star, GOLD_LIGHT, alpha = alpha)
}

/** Pita bersimpul di kaki kartu. `width` = lebar total pita. */
fun DrawScope.drawBow(center: Offset, width: Float) {
    val u = width / 160f
    val kuas = Brush.verticalGradient(
        colors = listOf(GOLD_LIGHT, GOLD_DARK),
        startY = center.y - 22f * u,
        endY = center.y + 32f * u,
    )
    translate(center.x - 80f * u, center.y - 22f * u) {
        val kiri = Path().apply {
            moveTo(80f * u, 22f * u)
            quadraticBezierTo(42f * u, 0f, 24f * u, 16f * u)
            quadraticBezierTo(10f * u, 30f * u, 36f * u, 38f * u)
            quadraticBezierTo(58f * u, 44f * u, 80f * u, 22f * u)
            close()
        }
        drawPath(kiri, kuas)
        val kanan = Path().apply {
            moveTo(80f * u, 22f * u)
            quadraticBezierTo(118f * u, 0f, 136f * u, 16f * u)
            quadraticBezierTo(150f * u, 30f * u, 124f * u, 38f * u)
            quadraticBezierTo(102f * u, 44f * u, 80f * u, 22f * u)
            close()
        }
        drawPath(kanan, kuas)
        val juntaiKiri = Path().apply {
            moveTo(74f * u, 24f * u)
            quadraticBezierTo(58f * u, 42f * u, 48f * u, 52f * u)
        }
        drawPath(juntaiKiri, kuas, style = Stroke(width = 7f * u))
        val juntaiKanan = Path().apply {
            moveTo(86f * u, 24f * u)
            quadraticBezierTo(102f * u, 42f * u, 112f * u, 52f * u)
        }
        drawPath(juntaiKanan, kuas, style = Stroke(width = 7f * u))
        drawOval(
            color = GOLD_BASE,
            topLeft = Offset(70f * u, 14f * u),
            size = Size(20f * u, 18f * u),
        )
        drawOval(
            color = GOLD_PALE,
            topLeft = Offset(70f * u, 14f * u),
            size = Size(20f * u, 18f * u),
            style = Stroke(width = 1.5f * u),
        )
    }
}
