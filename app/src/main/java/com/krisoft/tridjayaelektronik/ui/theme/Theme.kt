package com.krisoft.tridjayaelektronik.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.krisoft.tridjayaelektronik.data.DarkMode
import com.krisoft.tridjayaelektronik.data.StyleMode
import com.krisoft.tridjayaelektronik.data.ThemeState

/**
 * App theme. Colours come from the user's chosen preset ([colorSchemeFor], ported from Rhythm),
 * or from Material You dynamic colour when enabled on Android 12+. Dark mode follows the user's
 * choice (system / light / dark). Typography + shapes are fixed to the Rhythm-derived scales.
 */
@Composable
fun TridjayaAppTheme(
    themeState: ThemeState = ThemeState(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeState.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    val context = LocalContext.current
    val colors = when {
        themeState.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> colorSchemeFor(themeState.scheme, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // True edge-to-edge: transparent system bars, content draws behind them.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val shapes = when (themeState.styleMode) {
        StyleMode.EXPRESSIVE -> TridjayaShapesExpressive
        StyleMode.MATERIAL -> TridjayaShapesMaterial
    }

    MaterialTheme(
        colorScheme = colors,
        typography = TridjayaTypography,
        shapes = shapes,
        content = content
    )
}
