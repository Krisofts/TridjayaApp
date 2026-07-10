package com.krisoft.tridjayaelektronik.data

import android.content.Context
import com.krisoft.tridjayaelektronik.ui.theme.AppColorScheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class DarkMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap")
}

/** Shape/component style: Expressive = large rounded corners; Material = standard M3 corners. */
enum class StyleMode(val label: String) {
    EXPRESSIVE("Expressive"),
    MATERIAL("Material")
}

data class ThemeState(
    val scheme: AppColorScheme = AppColorScheme.DEFAULT,
    val dynamicColor: Boolean = false,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val styleMode: StyleMode = StyleMode.EXPRESSIVE
)

/**
 * Persists the user's theme choices (colour scheme, Material You dynamic colour, dark mode) in
 * plain SharedPreferences and exposes them as a [StateFlow] the root [MainActivity] observes so
 * the whole app recomposes on change. Tridjaya's take on Rhythm's ThemeViewModel.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ThemeState> = _state.asStateFlow()

    private fun load(): ThemeState = ThemeState(
        scheme = AppColorScheme.fromName(prefs.getString(KEY_SCHEME, null)),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        darkMode = runCatching { DarkMode.valueOf(prefs.getString(KEY_DARK, DarkMode.SYSTEM.name)!!) }
            .getOrDefault(DarkMode.SYSTEM),
        styleMode = runCatching { StyleMode.valueOf(prefs.getString(KEY_STYLE, StyleMode.EXPRESSIVE.name)!!) }
            .getOrDefault(StyleMode.EXPRESSIVE)
    )

    fun setScheme(scheme: AppColorScheme) = persist { it.copy(scheme = scheme) }
    fun setDynamicColor(enabled: Boolean) = persist { it.copy(dynamicColor = enabled) }
    fun setDarkMode(mode: DarkMode) = persist { it.copy(darkMode = mode) }
    fun setStyleMode(mode: StyleMode) = persist { it.copy(styleMode = mode) }

    private fun persist(transform: (ThemeState) -> ThemeState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putString(KEY_SCHEME, next.scheme.name)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putString(KEY_DARK, next.darkMode.name)
            .putString(KEY_STYLE, next.styleMode.name)
            .apply()
    }

    private companion object {
        const val KEY_SCHEME = "scheme"
        const val KEY_DYNAMIC = "dynamic"
        const val KEY_DARK = "dark_mode"
        const val KEY_STYLE = "style_mode"
    }
}
