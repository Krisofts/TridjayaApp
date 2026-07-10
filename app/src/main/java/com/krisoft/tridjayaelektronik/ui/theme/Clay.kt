package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard card (rounded per [MaterialTheme.shapes]) matching the "expressive" reference style.
 * Built on [Surface] (not Material3's `Card`) so tonal and shadow elevation stay independent:
 * [elevation] drives the cheap tonal color-overlay look used by every list row, while
 * [shadowElevation] defaults to 0 — a real drop shadow forces its own re-drawn layer per frame,
 * which is wasteful multiplied across ~15-20 visible rows during a scroll fling.
 * Kept as a thin wrapper so call sites don't need to change if the shared card style evolves.
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    elevation: Dp = 1.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = elevation,
        shadowElevation = shadowElevation,
        content = content
    )
}
