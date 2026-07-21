package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Selectable colour-scheme presets, ported from the Rhythm app reference
 * (github.com/cromaguy/Rhythm). Each preset only re-tints the primary/secondary/tertiary triad;
 * neutral roles (background, surface, error, outline, surface-containers) come from [Color.kt]
 * so contrast + elevation stay consistent across presets.
 */
enum class AppColorScheme(val label: String, val swatch: Color) {
    DEFAULT("Tridjaya Web", Color(0xFF465FFF)),
    LAVENDER("Lavender", Color(0xFF7C4DFF)),
    ROSE("Rose", Color(0xFFE91E63)),
    WARM("Warm", Color(0xFFFF6B35)),
    AMBER("Amber", Color(0xFFFF6F00)),
    FOREST("Forest", Color(0xFF2E7D32)),
    MINT("Mint", Color(0xFF0097A7)),
    COOL("Cool", Color(0xFF1E88E5)),
    OCEAN("Ocean", Color(0xFF006064));

    companion object {
        fun fromName(name: String?): AppColorScheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

fun colorSchemeFor(scheme: AppColorScheme, dark: Boolean): ColorScheme = when (scheme) {
    // Default is now matched 1:1 to the web dashboard's design tokens (tridjaya.com, React/Tailwind)
    // at the user's request — indigo primary #465FFF + the web's own 6-tier surface scale, not the
    // shared M3-purple neutrals other presets use. Supersedes the old flyer-blue "Biru Tridjaya" look.
    AppColorScheme.DEFAULT -> tridjayaWebScheme(dark)

    AppColorScheme.WARM ->
        if (dark) darkTriad(Color(0xFFFFB59A), Color(0xFF5F1500), Color(0xFFC84520), Color(0xFFFFDDD2), Color(0xFFFFD499), Color(0xFF4A2800), Color(0xFFD97E00), Color(0xFFFFDDB6), Color(0xFFFFE099), Color(0xFF442B00), Color(0xFFFFA91F), Color(0xFFFFE8B6))
        else lightTriad(Color(0xFFFF6B35), Color.White, Color(0xFFFFDDD2), Color(0xFF3E0400), Color(0xFFF7931E), Color.White, Color(0xFFFFDDB6), Color(0xFF2C1600), Color(0xFFFFC857), Color(0xFF432A0D), Color(0xFFFFE8B6), Color(0xFF261900))

    AppColorScheme.COOL ->
        if (dark) darkTriad(Color(0xFF90CAF9), Color(0xFF003258), Color(0xFF004A77), Color(0xFFD1E4FF), Color(0xFF4DB6AC), Color(0xFF003731), Color(0xFF005048), Color(0xFFB2DFDB), Color(0xFF4DD0E1), Color(0xFF00363D), Color(0xFF004F58), Color(0xFFB2EBF2))
        else lightTriad(Color(0xFF1E88E5), Color.White, Color(0xFFD1E4FF), Color(0xFF001D36), Color(0xFF00897B), Color.White, Color(0xFFB2DFDB), Color(0xFF00201D), Color(0xFF80DEEA), Color(0xFF003640), Color(0xFFB2EBF2), Color(0xFF002025))

    AppColorScheme.FOREST ->
        if (dark) darkTriad(Color(0xFF81C784), Color(0xFF0D5016), Color(0xFF1B5E20), Color(0xFFC8E6C9), Color(0xFFAED581), Color(0xFF1B5E20), Color(0xFF33691E), Color(0xFFDCEDC8), Color(0xFFDCE775), Color(0xFF3F5100), Color(0xFF5A7700), Color(0xFFE7F5E1))
        else lightTriad(Color(0xFF2E7D32), Color.White, Color(0xFFC8E6C9), Color(0xFF0D5016), Color(0xFF558B2F), Color.White, Color(0xFFDCEDC8), Color(0xFF1B5E20), Color(0xFF9CCC65), Color(0xFF2E5016), Color(0xFFE7F5E1), Color(0xFF223608))

    AppColorScheme.ROSE ->
        if (dark) darkTriad(Color(0xFFF48FB1), Color(0xFF560027), Color(0xFFC2185B), Color(0xFFF8BBD0), Color(0xFFFFAB91), Color(0xFF5F000A), Color(0xFFAD1457), Color(0xFFFFCDD2), Color(0xFFFF80AB), Color(0xFF5C002E), Color(0xFFD81B60), Color(0xFFFFE0EC))
        else lightTriad(Color(0xFFE91E63), Color.White, Color(0xFFF8BBD0), Color(0xFF3E001D), Color(0xFFC2185B), Color.White, Color(0xFFFFCDD2), Color(0xFF300016), Color(0xFFFF80AB), Color(0xFF5C002E), Color(0xFFFFE0EC), Color(0xFF31000F))

    AppColorScheme.OCEAN ->
        if (dark) darkTriad(Color(0xFF4DD0E1), Color(0xFF00363C), Color(0xFF006064), Color(0xFFB2EBF2), Color(0xFF4DD0E1), Color(0xFF00363C), Color(0xFF00838F), Color(0xFFB2EBF2), Color(0xFF00BCD4), Color(0xFF00363C), Color(0xFF00838F), Color(0xFFB2EBF2))
        else lightTriad(Color(0xFF006064), Color.White, Color(0xFFB2EBF2), Color(0xFF001B2E), Color(0xFF00838F), Color.White, Color(0xFFB2DFDB), Color(0xFF00201D), Color(0xFF00ACC1), Color.White, Color(0xFFB2EBF2), Color(0xFF002025))

    AppColorScheme.AMBER ->
        if (dark) darkTriad(Color(0xFFFF8F00), Color(0xFF2E2416), Color(0xFFFF6F00), Color(0xFF4E2600), Color(0xFFFFC107), Color(0xFF2E2416), Color(0xFFFF8F00), Color(0xFF4E2600), Color(0xFFFFD54F), Color(0xFF2E2416), Color(0xFFFFC107), Color(0xFF4E2600))
        else lightTriad(Color(0xFFFF6F00), Color.White, Color(0xFFFFCC02), Color(0xFF2E2416), Color(0xFFFF8F00), Color(0xFF2E2416), Color(0xFFFFE0B2), Color(0xFF442C18), Color(0xFFFFC107), Color(0xFF2E2416), Color(0xFFFFECB3), Color(0xFF442C18))

    AppColorScheme.LAVENDER ->
        if (dark) darkTriad(Color(0xFF9C7BFF), Color(0xFF2C1B69), Color(0xFFC4B5FF), Color(0xFF23005C), Color(0xFFCEB3FF), Color(0xFF352B4B), Color(0xFFE0BBFF), Color(0xFF3F2A6C), Color(0xFFE5B5FF), Color(0xFF44196A), Color(0xFFFFD6FF), Color(0xFF52147A))
        else lightTriad(Color(0xFF7C4DFF), Color.White, Color(0xFFEAEAFF), Color(0xFF23005C), Color(0xFF9575CD), Color.White, Color(0xFFEDE7F3), Color(0xFF3F2A6C), Color(0xFFBA68C8), Color.White, Color(0xFFFFE4FF), Color(0xFF52147A))

    AppColorScheme.MINT ->
        if (dark) darkTriad(Color(0xFF4FC3F7), Color(0xFF0D3447), Color(0xFF81D4FA), Color(0xFF001B2E), Color(0xFF4DD0E1), Color(0xFF00363C), Color(0xFF26C6DA), Color(0xFF0F3740), Color(0xFF00BCD4), Color(0xFF00363C), Color(0xFF4DD0E1), Color(0xFF00363C))
        else lightTriad(Color(0xFF0097A7), Color.White, Color(0xFFB2EBF2), Color(0xFF001B2E), Color(0xFF00ACC1), Color.White, Color(0xFFE0F2F1), Color(0xFF0F3740), Color(0xFF00BCD4), Color.White, Color(0xFFE1F5FE), Color(0xFF00363C))
}

/**
 * Default scheme matched 1:1 to the web dashboard's design tokens (tridjaya.com — React/Tailwind,
 * `frontend/src/index.css`): indigo primary #465FFF (same value in both modes), cyan secondary,
 * orange tertiary, and the web's own 6-tier surface scale (surface/surface-low/surface-container/
 * surface-high/surface-highest) mapped onto M3's surface-container roles — not the shared M3-purple
 * neutrals the other presets use. `outline`/`outlineVariant` intentionally use the web's *dashboard*
 * override value (crisper `--color-outline`, not the softer public-site `--color-outline-variant`)
 * since this app is the "dashboard" context, not the marketing site.
 */
private fun tridjayaWebScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF465FFF), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF7592FF), onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF0BA5EC), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFF065986), onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFFB6514), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF9C2A10), onTertiaryContainer = Color(0xFFFFE0CC),
    error = Color(0xFFF04438), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF7A271A), onErrorContainer = Color(0xFFFEE4E2),
    background = Color(0xFF101828), onBackground = Color(0xFFF2F4F7), surface = Color(0xFF101828), onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF344054), onSurfaceVariant = Color(0xFF98A2B3), outline = Color(0xFF344054), outlineVariant = Color(0xFF344054),
    scrim = Color.Black, inverseSurface = Color(0xFFFFFFFF), inverseOnSurface = Color(0xFF101828), inversePrimary = Color(0xFF3641F5),
    surfaceDim = Color(0xFF0A0F1C), surfaceBright = Color(0xFF667085),
    surfaceContainerLowest = Color(0xFF0A0F1C), surfaceContainerLow = Color(0xFF141C2E), surfaceContainer = Color(0xFF1D2939),
    surfaceContainerHigh = Color(0xFF344054), surfaceContainerHighest = Color(0xFF475467)
) else lightColorScheme(
    primary = Color(0xFF465FFF), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFDDE9FF), onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF0086C9), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFF0F9FF), onSecondaryContainer = Color(0xFF003544),
    tertiary = Color(0xFFEC4A0A), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFFFF6ED), onTertiaryContainer = Color(0xFF4A1D02),
    error = Color(0xFFF04438), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFEE4E2), onErrorContainer = Color(0xFF7A271A),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF101828), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFF2F4F7), onSurfaceVariant = Color(0xFF667085), outline = Color(0xFFE4E7EC), outlineVariant = Color(0xFFE4E7EC),
    scrim = Color.Black, inverseSurface = Color(0xFF101828), inverseOnSurface = Color(0xFFF2F4F7), inversePrimary = Color(0xFF7592FF),
    surfaceDim = Color(0xFFE4E7EC), surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFCFCFD), surfaceContainer = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFF2F4F7), surfaceContainerHighest = Color(0xFFE4E7EC)
)

private fun lightTriad(
    p: Color, op: Color, pc: Color, opc: Color,
    s: Color, os: Color, sc: Color, osc: Color,
    t: Color, ot: Color, tc: Color, otc: Color
): ColorScheme = lightColorScheme(
    primary = p, onPrimary = op, primaryContainer = pc, onPrimaryContainer = opc,
    secondary = s, onSecondary = os, secondaryContainer = sc, onSecondaryContainer = osc,
    tertiary = t, onTertiary = ot, tertiaryContainer = tc, onTertiaryContainer = otc,
    error = ErrorLight, onError = OnErrorLight, errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight, onBackground = OnBackgroundLight, surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight, outline = OutlineLight, outlineVariant = OutlineVariantLight,
    scrim = Color.Black, inverseSurface = InverseSurfaceLight, inverseOnSurface = InverseOnSurfaceLight, inversePrimary = InversePrimaryLight,
    surfaceDim = SurfaceContainerLowestLight, surfaceBright = SurfaceContainerHighestLight,
    surfaceContainerLowest = SurfaceContainerLowestLight, surfaceContainerLow = SurfaceContainerLowLight, surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight, surfaceContainerHighest = SurfaceContainerHighestLight
)

private fun darkTriad(
    p: Color, op: Color, pc: Color, opc: Color,
    s: Color, os: Color, sc: Color, osc: Color,
    t: Color, ot: Color, tc: Color, otc: Color
): ColorScheme = darkColorScheme(
    primary = p, onPrimary = op, primaryContainer = pc, onPrimaryContainer = opc,
    secondary = s, onSecondary = os, secondaryContainer = sc, onSecondaryContainer = osc,
    tertiary = t, onTertiary = ot, tertiaryContainer = tc, onTertiaryContainer = otc,
    error = ErrorDark, onError = OnErrorDark, errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark, onBackground = OnBackgroundDark, surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark, outline = OutlineDark, outlineVariant = OutlineVariantDark,
    scrim = Color.Black, inverseSurface = InverseSurfaceDark, inverseOnSurface = InverseOnSurfaceDark, inversePrimary = InversePrimaryDark,
    surfaceDim = SurfaceContainerLowestDark, surfaceBright = SurfaceContainerHighestDark,
    surfaceContainerLowest = SurfaceContainerLowestDark, surfaceContainerLow = SurfaceContainerLowDark, surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark, surfaceContainerHighest = SurfaceContainerHighestDark
)
