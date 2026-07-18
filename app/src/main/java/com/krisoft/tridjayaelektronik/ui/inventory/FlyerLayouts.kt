package com.krisoft.tridjayaelektronik.ui.inventory

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.krisoft.tridjayaelektronik.R
import com.krisoft.tridjayaelektronik.data.ProductImageUrl
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentResult

/** Everything a flyer layout needs to render, bundled so 10 layouts share one signature. */
internal data class FlyerContent(
    val product: ProductAggregate,
    val installment: InstallmentResult?,
    val salesName: String?,
    val salesWhatsapp: String?
)

/** Poster/flyer-styled product summary — this exact region is what gets captured and shared to
 * WhatsApp. Each [FlyerDesignSpec.layout] renders a structurally different composition, all in a
 * modern claymorphism / Canva-3D visual language. */
@Composable
internal fun ProductFlyer(
    product: ProductAggregate,
    installment: InstallmentResult?,
    salesName: String?,
    salesWhatsapp: String?,
    design: FlyerDesignSpec,
    style: FlyerCustomStyle,
    modifier: Modifier = Modifier,
    /** Mode FRESH SALE (deadstock): sticker diskon menimpa layout apa pun. */
    promo: FlyerPromo? = null
) {
    val content = FlyerContent(product, installment, salesName, salesWhatsapp)
    when (design.layout) {
        FlyerLayout.POSTER -> FlyerPosterLayout(content, design, style, modifier)
        FlyerLayout.SPLIT -> FlyerSplitLayout(content, design, style, modifier)
        FlyerLayout.MAGAZINE -> FlyerMagazineLayout(content, design, style, modifier)
        FlyerLayout.MINIMAL -> FlyerMinimalLayout(content, design, style, modifier)
        FlyerLayout.COUPON -> FlyerCouponLayout(content, design, style, modifier)
        FlyerLayout.DIAGONAL -> FlyerDiagonalLayout(content, design, style, modifier)
        FlyerLayout.POLAROID -> FlyerPolaroidLayout(content, design, style, modifier)
        FlyerLayout.BIG_TYPE -> FlyerBigTypeLayout(content, design, style, modifier)
        FlyerLayout.NEON -> FlyerNeonLayout(content, design, style, modifier)
        FlyerLayout.BUBBLE -> FlyerBubbleLayout(content, design, style, modifier)
        FlyerLayout.FRESH_SALE -> FlyerFreshSaleLayout(content, design, style, promo, modifier)
    }
}

/**
 * Judul promo 3D vector-style: ekstrusi bertumpuk (5 lapis kedalaman), outline putih tebal,
 * dan muka huruf bergradasi — efek teks sticker 3D ala Canva, digambar murni vektor.
 */
@Composable
private fun Promo3dText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    face: Brush,
    depth: Color,
    outline: Color = Color.White,
    rotateDeg: Float = 0f
) {
    // Italic ekstra-tebal condensed — gaya headline poster promo, bukan teks label biasa.
    val base = androidx.compose.ui.text.TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        letterSpacing = (-0.5).sp
    )
    Box(modifier = Modifier.rotate(rotateDeg)) {
        // Lapisan kedalaman — makin jauh makin turun, membentuk ekstrusi 3D halus.
        for (i in 6 downTo 1) {
            Text(
                text = text,
                style = base,
                color = depth,
                maxLines = 1,
                modifier = Modifier.offset(x = (i * 0.6f).dp, y = (i * 1.1f).dp)
            )
        }
        // Outline putih tebal di sekeliling huruf.
        Text(
            text = text,
            maxLines = 1,
            style = base.copy(
                color = outline,
                drawStyle = Stroke(width = 10f, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            )
        )
        // Muka huruf bergradasi.
        Text(text = text, maxLines = 1, style = base.copy(brush = face))
    }
}

/**
 * Badge diskon starburst (gerigi matahari) — digambar vektor via Canvas: bayangan jatuh,
 * isian gradasi emas, outline putih, teks rentang diskon di tengah. Pengganti bola polos.
 */
@Composable
private fun DiscountStarburst(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f
            val inner = outer * 0.84f
            val spikes = 14
            val path = Path()
            for (i in 0 until spikes * 2) {
                val r = if (i % 2 == 0) outer else inner
                val angle = (Math.PI * i / spikes).toFloat()
                val x = center.x + r * kotlin.math.cos(angle)
                val y = center.y + r * kotlin.math.sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            // Bayangan jatuh lembut di belakang burst.
            translate(top = 5.dp.toPx()) {
                drawPath(path, Color(0x40122F52))
            }
            drawPath(
                path,
                Brush.linearGradient(listOf(Color(0xFFFFE566), Color(0xFFFFB020), Color(0xFFFF8F00)))
            )
            drawPath(path, color = Color.White, style = Stroke(width = 3.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DISKON",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF7A4A00)
            )
            Text(
                text = "10-50%",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFC62828)
            )
        }
    }
}

// ───────────────────────────── FRESH SALE (layout khusus deadstock) ─────────────────────────────

/**
 * Layout flyer penuh untuk mode FRESH SALE — turunan Cotton Candy (BUBBLE) bertema BIRU:
 * bola-bola pastel biru, judul 3D playful FRESH SALE / DISKON GEDE, foto produk bulat dengan
 * bola diskon "10% hingga 50%" menimpa pojok, pill harga coret→sekarang, urgensi, cicilan, footer.
 */
@Composable
private fun FlyerFreshSaleLayout(
    content: FlyerContent,
    design: FlyerDesignSpec,
    style: FlyerCustomStyle,
    promo: FlyerPromo?,
    modifier: Modifier
) {
    val (product, installment, _, _) = content
    val navy = Color(0xFF12294F)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                // Susunan bola pastel persis pola Cotton Candy, dalam nuansa biru.
                claySphere(size.width * 0.1f, size.height * 0.14f, 46.dp.toPx(), design.accent2, alpha = 0.9f)
                claySphere(size.width * 0.92f, size.height * 0.24f, 34.dp.toPx(), design.accent, alpha = 0.75f)
                claySphere(size.width * 0.9f, size.height * 0.78f, 52.dp.toPx(), design.accent2, alpha = 0.6f)
                claySphere(size.width * 0.08f, size.height * 0.66f, 28.dp.toPx(), design.accent, alpha = 0.5f)
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(14.dp))

        // Judul 3D vector italic tebal: ekstrusi navy + outline putih + muka bergradasi.
        Promo3dText(
            text = "FRESH SALE",
            fontSize = (33 * style.titleScale).sp,
            face = Brush.verticalGradient(listOf(Color(0xFF7DD3FC), Color(0xFF2563EB))),
            depth = navy,
            rotateDeg = -2f
        )
        Spacer(modifier = Modifier.height(6.dp))
        Promo3dText(
            text = "DISKON GEDE",
            fontSize = (25 * style.titleScale).sp,
            face = Brush.verticalGradient(listOf(Color(0xFFFFE566), Color(0xFFFF9800))),
            depth = navy,
            rotateDeg = 1.5f
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = style.caseOf("${product.merk.ifBlank { "TRIDJAYA" }} — ${product.nama}"),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (15 * style.titleScale).sp,
                lineHeight = (20 * style.titleScale).sp
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Bold,
            color = design.titleColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Foto bulat khas Cotton Candy + bola diskon biru menimpa pojoknya.
        Box {
            Box(
                modifier = Modifier
                    .size(268.dp)
                    .clay(Color.White, CircleShape, elevation = 16.dp, spot = design.accent.copy(alpha = 0.55f))
                    .border(6.dp, design.accent, CircleShape)
                    .clip(CircleShape)
            ) {
                FlyerPhoto(
                    product = product,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Crop
                )
            }
            DiscountStarburst(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-12).dp)
                    .rotate(12f)
                    .size(98.dp)
            )
        }

        // Pill harga khas Cotton Candy menimpa dasar foto: coret → sekarang.
        Column(
            modifier = Modifier
                .offset(y = (-24).dp)
                .rotate(-3f)
                .clay(design.accent, RoundedCornerShape(50), elevation = 12.dp, spot = design.accent.copy(alpha = 0.7f))
                .padding(horizontal = 28.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (promo != null) {
                Text(
                    text = "Dulu Rp ${flyerPrice(promo.originalPrice.toInt())}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = design.onAccent.copy(alpha = 0.8f),
                    textDecoration = TextDecoration.LineThrough
                )
            }
            Text(
                text = "SEKARANG CUMA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = design.onAccent.copy(alpha = 0.85f)
            )
            Text(
                text = flyerRupiah(product.harga),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = (24 * style.priceScale).sp),
                fontFamily = style.fontChoice.family,
                fontWeight = FontWeight.Black,
                color = design.onAccent
            )
        }

        Column(modifier = Modifier.offset(y = (-10).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .rotate(-1.5f)
                    .shadow(8.dp, RoundedCornerShape(50), spotColor = Color(0x9912294F))
                    .background(navy, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFFFC93C),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = " SELAGI STOK MASIH ADA — SIAPA CEPAT DIA DAPAT!",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp,
                    color = Color.White
                )
            }

            if (installment != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (installment.dpAmount > 0) {
                    Text(
                        text = "DP mulai Rp ${flyerPrice(installment.dpAmount)} aja! 🤝",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = design.brandColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                ClayTenorRow(content, design)
            }

            Spacer(modifier = Modifier.height(14.dp))
            ClayFooter(content, design)
        }
    }
}

// ───────────────────────────── clay building blocks ─────────────────────────────

/** Puffy "clay" card: soft tinted drop shadow + solid bg + a subtle top-light sheen. */
private fun Modifier.clay(
    bg: Color,
    shape: Shape,
    elevation: Dp = 12.dp,
    spot: Color = Color(0x66000000)
): Modifier = this
    .shadow(elevation, shape, ambientColor = spot, spotColor = spot)
    .background(bg, shape)
    .background(
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.4f), 0.35f to Color.Transparent),
        shape
    )

/** Soft 3D sphere decoration (radial highlight off-center = clay ball). */
private fun DrawScope.claySphere(cx: Float, cy: Float, r: Float, base: Color, alpha: Float = 1f) {
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

private fun FlyerCustomStyle.shadowOrNull(): Shadow? =
    if (textShadow) Shadow(Color(0x40000000), Offset(0f, 3f), 6f) else null

private fun FlyerCustomStyle.caseOf(text: String) = if (uppercase) text.uppercase() else text

private val FlyerCustomStyle.textAlign: TextAlign
    get() = when (titleAlign) {
        FlyerAlign.START -> TextAlign.Start
        FlyerAlign.CENTER -> TextAlign.Center
        FlyerAlign.END -> TextAlign.End
    }

/** Product photo (Coil, uncropped by default) with a text placeholder fallback. */
@Composable
private fun FlyerPhoto(
    product: ProductAggregate,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholderTint: Color = Color(0x801E63E9)
) {
    val imageUrl = remember(product.gambar) { ProductImageUrl.resolve(product.gambar) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            ) {
                if (painter.state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    FlyerPhotoPlaceholder(placeholderTint)
                }
            }
        } else {
            FlyerPhotoPlaceholder(placeholderTint)
        }
    }
}

@Composable
private fun FlyerPhotoPlaceholder(tint: Color) {
    Text(
        text = "Foto\nProduk",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        textAlign = TextAlign.Center
    )
}

/** New header: TE logo floating in a clay pill + the design's promo tagline chip. */
@Composable
private fun ClayHeader(design: FlyerDesignSpec) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clay(Color.White, RoundedCornerShape(16.dp), elevation = 8.dp, spot = design.accent.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_header),
                contentDescription = "Tridjaya Elektronik",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(30.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clay(design.accent, RoundedCornerShape(50), elevation = 8.dp, spot = design.accent.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = design.tagline,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = design.onAccent
            )
        }
    }
}

/** "HEMAT Rp X" badge — computed from the toko-lain price so the saving is a real number. */
@Composable
private fun HematBadge(content: FlyerContent, design: FlyerDesignSpec, modifier: Modifier = Modifier) {
    val installment = content.installment ?: return
    val hemat = installment.tokoLainPrice - content.product.harga.toInt()
    if (hemat <= 0) return
    Box(
        modifier = modifier
            .rotate(-2f)
            .clay(design.accent2, RoundedCornerShape(50), elevation = 6.dp, spot = design.accent2.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = "🔥 HEMAT Rp ${flyerPrice(hemat)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = design.titleColor
        )
    }
}

/** Inline single-line strikethrough price ("Toko Lain Rp 4.850.000"). */
@Composable
private fun FlyerStrikeLine(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f)
        )
        Text(
            text = "Rp ${flyerPrice(value)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textDecoration = TextDecoration.LineThrough
        )
    }
}

/** Clay tenor chips row with its label. */
@Composable
private fun ClayTenorRow(content: FlyerContent, design: FlyerDesignSpec) {
    val installment = content.installment ?: return
    Text(
        text = "CICILAN PER BULAN",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        color = design.textDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        installment.tenors.forEach { tenor ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clay(design.tenorChipBg, RoundedCornerShape(14.dp), elevation = 6.dp, spot = design.accent.copy(alpha = 0.35f))
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${tenor.months} Bulan", style = MaterialTheme.typography.labelSmall, color = design.tenorChipLabel)
                Text(
                    text = flyerPrice(tenor.monthlyAmount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = design.tenorChipText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** New footer: clay CTA panel — order line, WhatsApp, urgency tagline, socials, kode. */
@Composable
private fun ClayFooter(content: FlyerContent, design: FlyerDesignSpec) {
    val cardBg = if (design.darkTheme) Color(0x1FFFFFFF) else Color.White
    val mainText = if (design.darkTheme) Color.White else Color(0xFF1C2438)
    val waColor = if (design.darkTheme) design.accent else design.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clay(cardBg, RoundedCornerShape(18.dp), elevation = 8.dp, spot = design.accent.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📲 Order Sekarang${if (content.salesName.isNullOrBlank()) "!" else " — ${content.salesName}"}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black,
            color = mainText,
            textAlign = TextAlign.Center
        )
        if (!content.salesWhatsapp.isNullOrBlank()) {
            Text(
                text = "WhatsApp ${content.salesWhatsapp}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = waColor,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "⚡ Stok terbatas — siapa cepat dia dapat!",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = mainText.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = "TikTok @rajanyaelektronik • FB Tridjaya Elektronik",
            style = MaterialTheme.typography.labelSmall,
            color = mainText.copy(alpha = 0.55f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Kode ${content.product.kode} • Harga dapat berubah sewaktu-waktu",
            style = MaterialTheme.typography.labelSmall,
            color = mainText.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

private fun flyerRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

private fun flyerPrice(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(".").reversed()

// ───────────────────────────── 1. POSTER (Clay Sky) ─────────────────────────────

@Composable
private fun FlyerPosterLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                claySphere(size.width * 0.92f, size.height * 0.12f, 60.dp.toPx(), design.accent2, alpha = 0.9f)
                claySphere(size.width * 0.06f, size.height * 0.34f, 34.dp.toPx(), design.accent, alpha = 0.5f)
                claySphere(size.width * 0.88f, size.height * 0.62f, 26.dp.toPx(), design.accent2, alpha = 0.55f)
            }
            .padding(16.dp)
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = (26 * style.titleScale).sp, shadow = style.shadowOrNull()
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = design.brandColor,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = style.caseOf(product.nama),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = (19 * style.titleScale).sp,
                lineHeight = (24 * style.titleScale).sp
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Bold,
            color = design.titleColor,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clay(design.panelColor, RoundedCornerShape(26.dp), elevation = 14.dp, spot = design.accent.copy(alpha = 0.5f))
        ) {
            Column {
                FlyerPhoto(product, Modifier.fillMaxWidth().height(250.dp).padding(start = 16.dp, end = 16.dp, top = 16.dp))
                Spacer(modifier = Modifier.height(34.dp))
            }
            if (installment != null) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HematBadge(content, design)
                }
            }
        }

        // Puffy price sticker overlapping the photo card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-26).dp)
                .rotate(-1.5f)
                .padding(horizontal = 26.dp)
                .clay(design.accent, RoundedCornerShape(20.dp), elevation = 14.dp, spot = design.accent.copy(alpha = 0.7f))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HARGA PROMO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = design.onAccent.copy(alpha = 0.8f)
            )
            Text(
                text = flyerRupiah(product.harga),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = (28 * style.priceScale).sp),
                fontFamily = style.fontChoice.family,
                fontWeight = FontWeight.Black,
                color = design.onAccent
            )
        }

        Column(modifier = Modifier.offset(y = (-14).dp)) {
            if (installment != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                    Spacer(modifier = Modifier.width(14.dp))
                    FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
                }
                if (installment.dpAmount > 0) {
                    Text(
                        text = "DP mulai Rp ${flyerPrice(installment.dpAmount)} aja! 🤝",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = design.titleColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                ClayTenorRow(content, design)
            }
            Spacer(modifier = Modifier.height(14.dp))
            ClayFooter(content, design)
        }
    }
}

// ───────────────────────────── 2. SPLIT (Clay Peach) ─────────────────────────────

@Composable
private fun FlyerSplitLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                claySphere(size.width * 0.08f, size.height * 0.10f, 44.dp.toPx(), design.accent2, alpha = 0.7f)
                claySphere(size.width * 0.94f, size.height * 0.5f, 30.dp.toPx(), design.accent, alpha = 0.4f)
            }
            .padding(16.dp)
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = style.caseOf(product.nama),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = (19 * style.titleScale).sp,
                lineHeight = (24 * style.titleScale).sp
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            color = design.titleColor,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = design.brandColor,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth().height(290.dp)) {
            Box(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxSize()
                    .clay(design.panelColor, RoundedCornerShape(24.dp), elevation = 12.dp, spot = design.accent.copy(alpha = 0.5f))
            ) {
                FlyerPhoto(product, Modifier.fillMaxSize().padding(12.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clay(design.accent, RoundedCornerShape(24.dp), elevation = 12.dp, spot = design.accent.copy(alpha = 0.7f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HARGA PROMO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = design.onAccent.copy(alpha = 0.8f)
                )
                Text(
                    text = flyerRupiah(product.harga),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = (22 * style.priceScale).sp,
                        lineHeight = (26 * style.priceScale).sp
                    ),
                    fontFamily = style.fontChoice.family,
                    fontWeight = FontWeight.Black,
                    color = design.onAccent,
                    textAlign = TextAlign.Center
                )
                if (installment != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Toko Lain Rp ${flyerPrice(installment.tokoLainPrice)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = design.onAccent.copy(alpha = 0.9f),
                            textDecoration = TextDecoration.LineThrough
                        )
                        Text(
                            text = "Normal Rp ${flyerPrice(installment.normalPrice)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = design.onAccent.copy(alpha = 0.9f),
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                    if (installment.dpAmount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "DP Rp ${flyerPrice(installment.dpAmount)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = design.onAccent
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        HematBadge(content, design, Modifier.align(Alignment.CenterHorizontally).offset(y = (-16).dp))
        ClayTenorRow(content, design)
        Spacer(modifier = Modifier.height(14.dp))
        ClayFooter(content, design)
    }
}

// ───────────────────────────── 3. MAGAZINE (Sinema) ─────────────────────────────

@Composable
private fun FlyerMagazineLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(580.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(design.bgTop)
    ) {
        FlyerPhoto(
            product = product,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholderTint = Color(0x66FFFFFF)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color(0x66000000), Color.Transparent)))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clay(Color.White, RoundedCornerShape(10.dp), elevation = 6.dp)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_header),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clay(design.accent, RoundedCornerShape(50), elevation = 6.dp, spot = design.accent.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = design.tagline,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = design.onAccent
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = style.fontChoice.family,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = design.brandColor
            )
            Text(
                text = style.caseOf(product.nama),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = (22 * style.titleScale).sp,
                    lineHeight = (27 * style.titleScale).sp,
                    shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 8f)
                ),
                fontFamily = style.fontChoice.family,
                fontWeight = FontWeight.Black,
                color = design.titleColor
            )
        }
        // Glassy clay bottom panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clay(Color(0x40000000), RoundedCornerShape(22.dp), elevation = 10.dp)
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(22.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (installment != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, Color(0xCCFFFFFF))
                    FlyerStrikeLine("Normal", installment.normalPrice, Color(0xCCFFFFFF))
                }
            }
            Text(
                text = flyerRupiah(product.harga),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = (34 * style.priceScale).sp,
                    shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 10f)
                ),
                fontFamily = style.fontChoice.family,
                fontWeight = FontWeight.Black,
                color = design.accent
            )
            if (installment != null && installment.dpAmount > 0) {
                Text(
                    text = "DP mulai Rp ${flyerPrice(installment.dpAmount)} aja! 🤝",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            if (installment != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    installment.tenors.forEach { tenor ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(12.dp))
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${tenor.months} Bln", style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF))
                            Text(
                                flyerPrice(tenor.monthlyAmount),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ClayFooter(content, design)
        }
    }
}

// ───────────────────────────── 4. MINIMAL (Krem Editorial) ─────────────────────────────

@Composable
private fun FlyerMinimalLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(design.bgTop)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_header),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(30.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = design.tagline,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 5.sp,
            color = design.brandColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = design.chipOutline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 5.sp,
            color = design.textDim,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = style.caseOf(product.nama),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = (21 * style.titleScale).sp,
                lineHeight = (27 * style.titleScale).sp
            ),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = design.titleColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clay(design.panelColor, RoundedCornerShape(18.dp), elevation = 10.dp, spot = design.accent.copy(alpha = 0.35f))
        ) {
            FlyerPhoto(product, Modifier.fillMaxWidth().height(230.dp).padding(12.dp))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "HARGA PROMO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
            color = design.textDim
        )
        Text(
            text = flyerRupiah(product.harga),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = (32 * style.priceScale).sp),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = design.titleColor
        )
        Box(modifier = Modifier.width(52.dp).height(3.dp).background(design.accent, RoundedCornerShape(2.dp)))
        if (installment != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
            }
            if (installment.dpAmount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DP Rp ${flyerPrice(installment.dpAmount)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = design.brandColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = installment.tenors.joinToString("   ·   ") { "${it.months} bln ${flyerPrice(it.monthlyAmount)}" },
                style = MaterialTheme.typography.labelSmall,
                color = design.textDim,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        ClayFooter(content, design)
    }
}

// ───────────────────────────── 5. COUPON (Tiket Mint) ─────────────────────────────

@Composable
private fun FlyerCouponLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                claySphere(size.width * 0.9f, size.height * 0.08f, 42.dp.toPx(), design.accent2, alpha = 0.8f)
                claySphere(size.width * 0.06f, size.height * 0.9f, 36.dp.toPx(), design.accent, alpha = 0.4f)
            }
            .padding(16.dp)
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(14.dp))
        val dashColor = design.accent
        val notchColor = lerp(design.bgTop, design.bgBottom, 0.5f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clay(Color.White, RoundedCornerShape(20.dp), elevation = 12.dp, spot = design.accent.copy(alpha = 0.5f))
                .drawBehind {
                    drawRoundRect(
                        color = dashColor.copy(alpha = 0.7f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
                        ),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                }
                .padding(16.dp)
        ) {
            Text(
                text = style.caseOf("${product.merk.ifBlank { "TRIDJAYA" }} — ${product.nama}"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (16 * style.titleScale).sp,
                    lineHeight = (21 * style.titleScale).sp
                ),
                fontFamily = style.fontChoice.family,
                fontWeight = FontWeight.Black,
                color = design.titleColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlyerPhoto(product, Modifier.fillMaxWidth().height(185.dp))
            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .drawBehind {
                            drawLine(
                                color = dashColor.copy(alpha = 0.55f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                            )
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-30).dp)
                        .size(26.dp)
                        .background(notchColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 30.dp)
                        .size(26.dp)
                        .background(notchColor, CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (installment != null) {
                        FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                        FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
                        if (installment.dpAmount > 0) {
                            Text(
                                text = "DP Rp ${flyerPrice(installment.dpAmount)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = design.brandColor
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clay(design.accent, RoundedCornerShape(16.dp), elevation = 8.dp, spot = design.accent.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "HARGA PROMO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = design.onAccent.copy(alpha = 0.85f)
                    )
                    Text(
                        text = flyerRupiah(product.harga),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = (21 * style.priceScale).sp),
                        fontFamily = style.fontChoice.family,
                        fontWeight = FontWeight.Black,
                        color = design.onAccent
                    )
                }
            }
            if (installment != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    installment.tenors.forEach { tenor ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, design.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${tenor.months} Bln", style = MaterialTheme.typography.labelSmall, color = design.textDim)
                            Text(
                                flyerPrice(tenor.monthlyAmount),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = design.titleColor
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HematBadge(content, design, Modifier.align(Alignment.CenterHorizontally).offset(y = (-16).dp))
        Spacer(modifier = Modifier.height(2.dp))
        ClayFooter(content, design)
    }
}

// ───────────────────────────── 6. DIAGONAL (Ombre Pop) ─────────────────────────────

@Composable
private fun FlyerDiagonalLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRect(design.bgBottom)
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.20f)
                    lineTo(0f, size.height * 0.38f)
                    close()
                }
                drawPath(path, design.bgTop)
                claySphere(size.width * 0.9f, size.height * 0.32f, 40.dp.toPx(), design.accent2, alpha = 0.8f)
                claySphere(size.width * 0.08f, size.height * 0.62f, 30.dp.toPx(), design.accent, alpha = 0.35f)
            }
            .padding(16.dp)
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = (22 * style.titleScale).sp, shadow = style.shadowOrNull()
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = design.brandColor
        )
        Text(
            text = style.caseOf(product.nama),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (16 * style.titleScale).sp,
                lineHeight = (21 * style.titleScale).sp
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Bold,
            color = design.titleColor
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clay(design.panelColor, RoundedCornerShape(22.dp), elevation = 12.dp, spot = design.accent.copy(alpha = 0.5f))
        ) {
            FlyerPhoto(product, Modifier.fillMaxSize().padding(12.dp))
            HematBadge(content, design, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-18).dp)
                .rotate(-4f)
                .clay(design.accent, RoundedCornerShape(14.dp), elevation = 12.dp, spot = design.accent.copy(alpha = 0.7f))
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PROMO  ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = design.onAccent.copy(alpha = 0.75f)
                )
                Text(
                    text = flyerRupiah(product.harga),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = (26 * style.priceScale).sp),
                    fontFamily = style.fontChoice.family,
                    fontWeight = FontWeight.Black,
                    color = design.onAccent
                )
            }
        }
        Column(modifier = Modifier.offset(y = (-8).dp)) {
            if (installment != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                    Spacer(modifier = Modifier.width(14.dp))
                    FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
                }
                if (installment.dpAmount > 0) {
                    Text(
                        text = "DP mulai Rp ${flyerPrice(installment.dpAmount)} aja! 🤝",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = design.titleColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                ClayTenorRow(content, design)
            }
            Spacer(modifier = Modifier.height(14.dp))
            ClayFooter(content, design)
        }
    }
}

// ───────────────────────────── 7. POLAROID (Scrapbook) ─────────────────────────────

@Composable
private fun FlyerPolaroidLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                claySphere(size.width * 0.9f, size.height * 0.12f, 52.dp.toPx(), design.accent2, alpha = 0.85f)
                claySphere(size.width * 0.08f, size.height * 0.4f, 30.dp.toPx(), design.accent, alpha = 0.45f)
                claySphere(size.width * 0.85f, size.height * 0.72f, 24.dp.toPx(), design.accent2, alpha = 0.5f)
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = design.brandColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box {
            Column(
                modifier = Modifier
                    .rotate(2.5f)
                    .clay(Color.White, RoundedCornerShape(10.dp), elevation = 16.dp, spot = Color(0x59000000))
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
            ) {
                FlyerPhoto(product, Modifier.fillMaxWidth().height(235.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = product.nama,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (16 * style.titleScale).sp,
                        lineHeight = (20 * style.titleScale).sp
                    ),
                    fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF33415C),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-12).dp)
                    .rotate(8f)
                    .size(118.dp)
                    .clay(design.accent, CircleShape, elevation = 12.dp, spot = design.accent.copy(alpha = 0.7f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PROMO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = design.onAccent.copy(alpha = 0.8f)
                )
                Text(
                    text = flyerRupiah(product.harga).removePrefix("Rp "),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = (16 * style.priceScale).sp),
                    fontFamily = style.fontChoice.family,
                    fontWeight = FontWeight.Black,
                    color = design.onAccent,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "rupiah",
                    style = MaterialTheme.typography.labelSmall,
                    color = design.onAccent.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HematBadge(content, design)
        if (installment != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
            }
            if (installment.dpAmount > 0) {
                Text(
                    text = "DP mulai Rp ${flyerPrice(installment.dpAmount)} aja! 🤝",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = design.titleColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            ClayTenorRow(content, design)
        }
        Spacer(modifier = Modifier.height(14.dp))
        ClayFooter(content, design)
    }
}

// ───────────────────────────── 8. BIG TYPE (Bold Lemon) ─────────────────────────────

@Composable
private fun FlyerBigTypeLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                claySphere(size.width * 0.92f, size.height * 0.16f, 40.dp.toPx(), design.accent2, alpha = 0.9f)
            }
            .padding(18.dp)
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "HARGA PROMO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            color = design.textDim,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = flyerRupiah(product.harga),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = (42 * style.priceScale).sp),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            color = design.titleColor,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        if (installment != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HematBadge(content, design)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = design.chipOutline, thickness = 3.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = style.caseOf(product.nama),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = (24 * style.titleScale).sp,
                lineHeight = (29 * style.titleScale).sp
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            color = design.titleColor,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = design.textDim,
            textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (installment != null) {
                    if (installment.dpAmount > 0) {
                        Text(
                            text = "DP Rp ${flyerPrice(installment.dpAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = design.titleColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    installment.tenors.forEach { tenor ->
                        Text(
                            text = "${tenor.months} BLN — Rp ${flyerPrice(tenor.monthlyAmount)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = design.textDim
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clay(design.panelColor, RoundedCornerShape(20.dp), elevation = 12.dp, spot = design.accent.copy(alpha = 0.5f))
            ) {
                FlyerPhoto(product, Modifier.fillMaxSize().padding(10.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ClayFooter(content, design)
    }
}

// ───────────────────────────── 9. NEON (Cyber Glow) ─────────────────────────────

@Composable
private fun FlyerNeonLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    val glowCyan = Shadow(design.accent, Offset.Zero, 20f)
    val glowPink = Shadow(design.accent2, Offset.Zero, 24f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                claySphere(size.width * 0.9f, size.height * 0.1f, 44.dp.toPx(), design.accent2, alpha = 0.35f)
                claySphere(size.width * 0.08f, size.height * 0.5f, 30.dp.toPx(), design.accent, alpha = 0.25f)
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clay(Color.White, RoundedCornerShape(14.dp), elevation = 8.dp, spot = design.accent.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_header),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, design.accent, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = design.tagline,
                    style = MaterialTheme.typography.labelSmall.copy(shadow = glowCyan),
                    fontWeight = FontWeight.Black,
                    color = design.accent
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = (24 * style.titleScale).sp, shadow = glowCyan
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            color = design.brandColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = style.caseOf(product.nama),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (16 * style.titleScale).sp,
                lineHeight = (21 * style.titleScale).sp
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Bold,
            color = design.titleColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .border(1.dp, design.accent2.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(design.panelColor)
                .border(1.5.dp, design.accent, RoundedCornerShape(20.dp))
        ) {
            FlyerPhoto(product, Modifier.fillMaxSize().padding(12.dp), placeholderTint = design.accent.copy(alpha = 0.6f))
            HematBadge(content, design, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "HARGA PROMO",
            style = MaterialTheme.typography.labelMedium.copy(shadow = glowCyan),
            fontWeight = FontWeight.Black,
            letterSpacing = 5.sp,
            color = design.accent
        )
        Text(
            text = flyerRupiah(product.harga),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = (34 * style.priceScale).sp, shadow = glowPink
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            color = design.accent2
        )
        if (installment != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
            }
            if (installment.dpAmount > 0) {
                Text(
                    text = "DP mulai Rp ${flyerPrice(installment.dpAmount)} aja! 🤝",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = design.titleColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                installment.tenors.forEach { tenor ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, design.accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${tenor.months} Bln", style = MaterialTheme.typography.labelSmall, color = design.accent)
                        Text(
                            flyerPrice(tenor.monthlyAmount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        ClayFooter(content, design)
    }
}

// ───────────────────────────── 10. BUBBLE (Cotton Candy) ─────────────────────────────

@Composable
private fun FlyerBubbleLayout(content: FlyerContent, design: FlyerDesignSpec, style: FlyerCustomStyle, modifier: Modifier) {
    val (product, installment, _, _) = content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(design.bgTop, design.bgBottom)))
            .drawBehind {
                claySphere(size.width * 0.1f, size.height * 0.14f, 46.dp.toPx(), design.accent2, alpha = 0.9f)
                claySphere(size.width * 0.92f, size.height * 0.24f, 34.dp.toPx(), design.accent, alpha = 0.75f)
                claySphere(size.width * 0.9f, size.height * 0.78f, 52.dp.toPx(), design.accent2, alpha = 0.6f)
                claySphere(size.width * 0.08f, size.height * 0.66f, 28.dp.toPx(), design.accent, alpha = 0.5f)
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ClayHeader(design)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = style.caseOf(product.merk.ifBlank { "TRIDJAYA" }),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = (22 * style.titleScale).sp),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = design.brandColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = style.caseOf(product.nama),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (16 * style.titleScale).sp,
                lineHeight = (21 * style.titleScale).sp
            ),
            fontFamily = style.fontChoice.family,
            fontWeight = FontWeight.Bold,
            color = design.titleColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .size(232.dp)
                .clay(Color.White, CircleShape, elevation = 16.dp, spot = design.accent.copy(alpha = 0.55f))
                .border(6.dp, design.accent, CircleShape)
                .clip(CircleShape)
        ) {
            FlyerPhoto(
                product = product,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Crop
            )
        }
        Column(
            modifier = Modifier
                .offset(y = (-26).dp)
                .rotate(-3f)
                .clay(design.accent, RoundedCornerShape(50), elevation = 12.dp, spot = design.accent.copy(alpha = 0.7f))
                .padding(horizontal = 28.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HARGA PROMO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = design.onAccent.copy(alpha = 0.85f)
            )
            Text(
                text = flyerRupiah(product.harga),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = (24 * style.priceScale).sp),
                fontFamily = style.fontChoice.family,
                fontWeight = FontWeight.Black,
                color = design.onAccent
            )
        }
        Column(modifier = Modifier.offset(y = (-12).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HematBadge(content, design)
            if (installment != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FlyerStrikeLine("Toko Lain", installment.tokoLainPrice, design.textDim)
                    FlyerStrikeLine("Normal", installment.normalPrice, design.textDim)
                }
                if (installment.dpAmount > 0) {
                    Text(
                        text = "DP mulai Rp ${flyerPrice(installment.dpAmount)} aja! 🤝",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = design.brandColor
                    )
                }
                ClayTenorRow(content, design)
            }
            Spacer(modifier = Modifier.height(14.dp))
            ClayFooter(content, design)
        }
    }
}
