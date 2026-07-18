package com.krisoft.tridjayaelektronik.ui.inventory

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Which structural layout a flyer design uses — every entry renders a genuinely different
 * composition, not just a palette swap. */
internal enum class FlyerLayout {
    POSTER,      // clay poster: floating photo card + puffy price sticker
    SPLIT,       // photo card left, clay price panel right
    MAGAZINE,    // full-bleed photo, glassy bottom panel
    MINIMAL,     // airy cream editorial, thin rules
    COUPON,      // clay voucher ticket with perforation
    DIAGONAL,    // pastel diagonal color blocks + ribbon
    POLAROID,    // rotated polaroid card on sphere-decorated background
    BIG_TYPE,    // typography-first: huge price dominates
    NEON,        // dark glow tech look
    BUBBLE,      // cotton-candy spheres, round photo, pill price
    FRESH_SALE   // layout khusus mode deadstock: judul 3D playful + bola diskon + harga coret
}

/**
 * One swipeable flyer design: layout + palette + promo copy for [ProductFlyer].
 * All colors are fixed (not theme-driven) so a shared flyer image always looks identical
 * regardless of the sender's app theme.
 */
internal data class FlyerDesignSpec(
    val name: String,
    val layout: FlyerLayout,
    /** Header tagline chip copy (each design gets its own promo voice). */
    val tagline: String,
    val bgTop: Color,
    val bgBottom: Color,
    /** Primary highlight (price sticker/banner bg, WhatsApp line, sphere tint). */
    val accent: Color,
    /** Text color on top of [accent]. */
    val onAccent: Color,
    /** Secondary highlight (spheres, glow #2, decorations). */
    val accent2: Color = Color(0xFFFFC93C),
    /** Product-name/main text color on the poster background. */
    val titleColor: Color,
    /** Brand (merk) display color. */
    val brandColor: Color,
    /** Secondary/dim text on the poster background. */
    val textDim: Color,
    /** Faintest text (kode/disclaimer). */
    val textFaint: Color,
    /** Photo/clay panel background. */
    val panelColor: Color = Color.White,
    val chipOutline: Color,
    val tenorChipBg: Color = Color.White,
    val tenorChipLabel: Color = Color(0xFF6B7A90),
    val tenorChipText: Color = Color(0xFF15294D),
    /** Dark-themed design → footers/cards switch to translucent-light treatment. */
    val darkTheme: Boolean = false
)

internal val FLYER_DESIGNS = listOf(
    FlyerDesignSpec(
        name = "Clay Sky",
        layout = FlyerLayout.POSTER,
        tagline = "✨ PROMO SPESIAL",
        bgTop = Color(0xFFDBEAFE), bgBottom = Color(0xFFEFF6FF),
        accent = Color(0xFF3B82F6), onAccent = Color.White,
        accent2 = Color(0xFF93C5FD),
        titleColor = Color(0xFF12294F), brandColor = Color(0xFF2563EB),
        textDim = Color(0x9912294F), textFaint = Color(0x7312294F),
        chipOutline = Color(0x3312294F),
        tenorChipLabel = Color(0xFF5B6C8F), tenorChipText = Color(0xFF12294F)
    ),
    FlyerDesignSpec(
        name = "Clay Peach",
        layout = FlyerLayout.SPLIT,
        tagline = "🔥 SUPER DEAL",
        bgTop = Color(0xFFFFE4D6), bgBottom = Color(0xFFFFF3EB),
        accent = Color(0xFFFF6B4A), onAccent = Color.White,
        accent2 = Color(0xFFFFB59E),
        titleColor = Color(0xFF4A1F10), brandColor = Color(0xFFE8502E),
        textDim = Color(0x994A1F10), textFaint = Color(0x734A1F10),
        chipOutline = Color(0x334A1F10),
        tenorChipLabel = Color(0xFFA06B57), tenorChipText = Color(0xFF4A1F10)
    ),
    FlyerDesignSpec(
        name = "Sinema Biru",
        layout = FlyerLayout.MAGAZINE,
        tagline = "🎬 EDISI TERBATAS",
        bgTop = Color(0xFF081226), bgBottom = Color(0xFF102A54),
        accent = Color(0xFF38BDF8), onAccent = Color(0xFF041E33),
        accent2 = Color(0xFF2563EB),
        titleColor = Color.White, brandColor = Color(0xFF7DD3FC),
        textDim = Color(0xCCFFFFFF), textFaint = Color(0x99FFFFFF),
        chipOutline = Color(0x4D38BDF8),
        darkTheme = true
    ),
    FlyerDesignSpec(
        name = "Krem Editorial",
        layout = FlyerLayout.MINIMAL,
        tagline = "PILIHAN TERBAIK",
        bgTop = Color(0xFFF7F3EC), bgBottom = Color(0xFFF7F3EC),
        accent = Color(0xFFB08A3E), onAccent = Color.White,
        accent2 = Color(0xFFE3D5BC),
        titleColor = Color(0xFF2E2618), brandColor = Color(0xFF8A6A2E),
        textDim = Color(0x992E2618), textFaint = Color(0x732E2618),
        panelColor = Color(0xFFFFFFFF),
        chipOutline = Color(0x2E2E2618),
        tenorChipLabel = Color(0xFF9A855E), tenorChipText = Color(0xFF2E2618)
    ),
    FlyerDesignSpec(
        name = "Tiket Mint",
        layout = FlyerLayout.COUPON,
        tagline = "💸 KUPON HEMAT",
        bgTop = Color(0xFFD7F5E9), bgBottom = Color(0xFFEAFBF4),
        accent = Color(0xFF10B981), onAccent = Color.White,
        accent2 = Color(0xFF6EE7B7),
        titleColor = Color(0xFF0B3D2E), brandColor = Color(0xFF059669),
        textDim = Color(0x990B3D2E), textFaint = Color(0x730B3D2E),
        chipOutline = Color(0x330B3D2E),
        tenorChipLabel = Color(0xFF4E8A75), tenorChipText = Color(0xFF0B3D2E)
    ),
    FlyerDesignSpec(
        name = "Ombre Pop",
        layout = FlyerLayout.DIAGONAL,
        tagline = "⚡ MEGA PROMO",
        bgTop = Color(0xFFE4DAFF), bgBottom = Color(0xFFD6ECFF),
        accent = Color(0xFF8B5CF6), onAccent = Color.White,
        accent2 = Color(0xFFC4B5FD),
        titleColor = Color(0xFF2B1B5E), brandColor = Color(0xFF7C3AED),
        textDim = Color(0x992B1B5E), textFaint = Color(0x732B1B5E),
        chipOutline = Color(0x332B1B5E),
        tenorChipLabel = Color(0xFF7E6BAF), tenorChipText = Color(0xFF2B1B5E)
    ),
    FlyerDesignSpec(
        name = "Scrapbook",
        layout = FlyerLayout.POLAROID,
        tagline = "📸 KOLEKSI FAVORIT",
        bgTop = Color(0xFFEDE7FF), bgBottom = Color(0xFFFDF1F7),
        accent = Color(0xFF14B8A6), onAccent = Color.White,
        accent2 = Color(0xFFF9A8D4),
        titleColor = Color(0xFF3B2A6E), brandColor = Color(0xFF0D9488),
        textDim = Color(0x993B2A6E), textFaint = Color(0x733B2A6E),
        chipOutline = Color(0x333B2A6E),
        tenorChipLabel = Color(0xFF8B7BB8), tenorChipText = Color(0xFF3B2A6E)
    ),
    FlyerDesignSpec(
        name = "Royal Gold",
        layout = FlyerLayout.BIG_TYPE,
        tagline = "👑 PREMIUM DEAL",
        bgTop = Color(0xFF12121A), bgBottom = Color(0xFF1E1B2E),
        accent = Color(0xFFE8B84B), onAccent = Color(0xFF241A00),
        accent2 = Color(0xFF8B5CF6),
        titleColor = Color(0xFFF5EFDD), brandColor = Color(0xFFE8B84B),
        textDim = Color(0xB3F5EFDD), textFaint = Color(0x80F5EFDD),
        panelColor = Color(0xFF201D30),
        chipOutline = Color(0x59E8B84B),
        tenorChipBg = Color(0xFF262238),
        tenorChipLabel = Color(0xFFB8A97C), tenorChipText = Color(0xFFF5EFDD),
        darkTheme = true
    ),
    FlyerDesignSpec(
        name = "Cyber Glow",
        layout = FlyerLayout.NEON,
        tagline = "🚀 CYBER DEAL",
        bgTop = Color(0xFF07071A), bgBottom = Color(0xFF101030),
        accent = Color(0xFF22D3EE), onAccent = Color(0xFF041219),
        accent2 = Color(0xFFF472B6),
        titleColor = Color.White, brandColor = Color(0xFF22D3EE),
        textDim = Color(0xB3FFFFFF), textFaint = Color(0x80FFFFFF),
        panelColor = Color(0xFF141435),
        chipOutline = Color(0x5922D3EE),
        darkTheme = true
    ),
    FlyerDesignSpec(
        name = "Cotton Candy",
        layout = FlyerLayout.BUBBLE,
        tagline = "🎉 PROMO CERIA",
        bgTop = Color(0xFFFDE7F3), bgBottom = Color(0xFFE7EFFF),
        accent = Color(0xFFEC4899), onAccent = Color.White,
        accent2 = Color(0xFF93C5FD),
        titleColor = Color(0xFF4A1D5E), brandColor = Color(0xFFDB2777),
        textDim = Color(0x994A1D5E), textFaint = Color(0x734A1D5E),
        chipOutline = Color(0x334A1D5E),
        tenorChipLabel = Color(0xFFA478A8), tenorChipText = Color(0xFF4A1D5E)
    )
)

/** Desain khusus mode FRESH SALE — dipakai MENGGANTIKAN seluruh daftar desain saat mode
 *  deadstock aktif (bukan sekadar sticker tempel di desain biasa). */
internal val FRESH_SALE_DESIGN = FlyerDesignSpec(
    name = "FRESH SALE",
    layout = FlyerLayout.FRESH_SALE,
    tagline = "⚡ FRESH SALE",
    bgTop = Color(0xFFDBEAFE), bgBottom = Color(0xFFE7F0FF),
    accent = Color(0xFF2563EB), onAccent = Color.White,
    accent2 = Color(0xFF93C5FD),
    titleColor = Color(0xFF12294F), brandColor = Color(0xFF2563EB),
    textDim = Color(0x9912294F), textFaint = Color(0x7312294F),
    chipOutline = Color(0x3312294F),
    tenorChipLabel = Color(0xFF5B6C8F), tenorChipText = Color(0xFF12294F)
)

/** Mode promo cuci gudang untuk barang deadstock: diskon otomatis + sticker FRESH SALE. */
internal data class FlyerPromo(
    /** Harga asli sebelum diskon — dicoret di sticker. */
    val originalPrice: Double,
    /** Persen diskon (mis. 10). */
    val percent: Int
)

/** Persen diskon otomatis untuk barang deadstock. */
internal const val FRESH_SALE_PERCENT = 10

/** Harga FRESH SALE: turun [FRESH_SALE_PERCENT]% lalu dibulatkan ke ribuan ke bawah
 *  supaya angkanya enak dibaca di flyer (mis. 4.499.000 bukan 4.499.100). */
internal fun freshSalePrice(harga: Double): Double =
    kotlin.math.floor(harga * (100 - FRESH_SALE_PERCENT) / 100.0 / 1000.0) * 1000.0

/** User-tunable text styling for the flyer, edited from the customization bottom sheet. */
internal data class FlyerCustomStyle(
    val fontChoice: FlyerFont = FlyerFont.DEFAULT,
    val titleScale: Float = 1f,
    val priceScale: Float = 1f,
    val titleAlign: FlyerAlign = FlyerAlign.START,
    val textShadow: Boolean = true,
    val uppercase: Boolean = true,
    /** Tampilkan blok cicilan/DP/harga coret di flyer — matikan untuk flyer harga cash bersih. */
    val showInstallment: Boolean = true
)

internal enum class FlyerFont(val label: String, val family: FontFamily) {
    DEFAULT("Bawaan", FontFamily.Default),
    SERIF("Serif", FontFamily.Serif),
    SANS("Sans", FontFamily.SansSerif),
    MONO("Mono", FontFamily.Monospace),
    CURSIVE("Script", FontFamily.Cursive)
}

internal enum class FlyerAlign(val label: String) { START("Kiri"), CENTER("Tengah"), END("Kanan") }
