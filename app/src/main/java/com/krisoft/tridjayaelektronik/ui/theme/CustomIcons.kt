package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/** Simple bullseye/target icon — Material's core icon set has no "target" glyph. */
@Composable
fun TargetIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        drawCircle(color = tint, radius = radius, style = Stroke(width = radius * 0.22f))
        drawCircle(color = tint, radius = radius * 0.58f, style = Stroke(width = radius * 0.2f))
        drawCircle(color = tint, radius = radius * 0.22f)
    }
}

/** Simple ascending-bars icon for KPI/performance sections. */
@Composable
fun TrendBarsIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val barWidth = size.width / 5f
        val gap = barWidth / 2f
        val heights = listOf(0.45f, 0.7f, 1f)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f, barWidth * 0.3f)
            )
        }
    }
}
