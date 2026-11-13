package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

/** Radius scale from the design: 4 / 6 / 8 / 12 / 16 / 20 dp. */
object RealScaleShape {
    val Xs = RoundedCornerShape(4.dp)
    val Sm = RoundedCornerShape(6.dp)
    val Md = RoundedCornerShape(8.dp)
    val Lg = RoundedCornerShape(12.dp)
    val Xl = RoundedCornerShape(16.dp)
    val Xxl = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val SheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

/** The one shadow the design allows: 0 1px 3px rgba(20,20,19,.08). Used rarely. */
fun Modifier.softShadow(): androidx.compose.ui.Modifier =
    this.shadow(elevation = 3.dp, shape = RealScaleShape.Md,
        ambientColor = Color(0x14141413), spotColor = Color(0x14141413), clip = false)
