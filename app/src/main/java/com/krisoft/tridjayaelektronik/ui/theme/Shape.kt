package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Expressive shape scale (Rhythm-style): large, rounded corners (8/12/16/24/32 dp).
val TridjayaShapesExpressive = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Standard Material 3 shape scale: tighter, more conventional corners (4/8/12/16/28 dp).
val TridjayaShapesMaterial = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Backwards-compatible default (Expressive) for any remaining direct references.
val TridjayaShapes = TridjayaShapesExpressive
