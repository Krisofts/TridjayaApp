package com.krisoft.tridjayaelektronik.ui.settings

import androidx.lifecycle.ViewModel
import com.krisoft.tridjayaelektronik.data.DarkMode
import com.krisoft.tridjayaelektronik.data.StyleMode
import com.krisoft.tridjayaelektronik.data.ThemePreferences
import com.krisoft.tridjayaelektronik.ui.theme.AppColorScheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val preferences: ThemePreferences
) : ViewModel() {
    val state = preferences.state
    fun setScheme(scheme: AppColorScheme) = preferences.setScheme(scheme)
    fun setDynamicColor(enabled: Boolean) = preferences.setDynamicColor(enabled)
    fun setDarkMode(mode: DarkMode) = preferences.setDarkMode(mode)
    fun setStyleMode(mode: StyleMode) = preferences.setStyleMode(mode)
}
