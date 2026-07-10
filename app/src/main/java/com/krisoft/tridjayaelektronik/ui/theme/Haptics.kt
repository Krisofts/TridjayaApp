package com.krisoft.tridjayaelektronik.ui.theme

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Wraps a click so it fires a light tactile "click" haptic first — Tridjaya's take on Rhythm's
 * `HapticUtils.performHapticFeedback`. Used by the shared Expressive components + floating nav so
 * every primary interaction has a subtle buzz.
 */
@Composable
fun rememberHapticClick(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return remember(view, onClick) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
    }
}
