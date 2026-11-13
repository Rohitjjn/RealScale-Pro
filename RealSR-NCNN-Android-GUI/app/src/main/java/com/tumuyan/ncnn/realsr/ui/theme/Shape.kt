package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale from Claude `DESIGN.md`:
 * xs=4dp, sm=6dp, md=8dp (buttons/inputs), lg=12dp (editorial cards), xl=16dp (hero canvas).
 */
val RealScaleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)
