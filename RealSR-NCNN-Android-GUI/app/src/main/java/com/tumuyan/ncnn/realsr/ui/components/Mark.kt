package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.theme.SpringEasing

/**
 * RealScale-Pro brand mark. Per the task, the attached master logo is used verbatim everywhere
 * the design shows its placeholder <Mark/> (splash, onboarding, About, empty states) — never redrawn.
 * [animate] plays the design's rise-in (draw → pop) as a fade+scale-in for the raster asset.
 */
@Composable
fun RealScaleMark(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    animate: Boolean = false,
    contentDescription: String? = null,
) {
    val scale = remember { Animatable(if (animate) 0.6f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            alpha.animateTo(1f, tween(450, delayMillis = 100, easing = SpringEasing))
        }
    }
    Image(
        painter = painterResource(R.drawable.brand_logo_medallion),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .scale(if (animate) scale.value else 1f)
            .alpha(if (animate) alpha.value else 1f),
    )
}
