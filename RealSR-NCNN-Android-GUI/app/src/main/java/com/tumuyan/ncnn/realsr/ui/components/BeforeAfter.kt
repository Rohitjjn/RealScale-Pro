package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors

/**
 * Before/after comparison — wipe reveal controlled by [pos] (0 = fully before, 1 = fully after).
 * Drag anywhere to reposition the wipe; [showOriginal] forces the before image.
 * An optional [sweepFromFraction] animates the wipe into place on first composition.
 */
@Composable
fun RealScaleBeforeAfter(
    beforeModel: Any?,
    afterModel: Any?,
    altDescription: String,
    modifier: Modifier = Modifier,
    initialFraction: Float = 0.5f,
    sweepFromFraction: Float? = null,
    showOriginal: Boolean = false,
    cornerRadius: Float = 12f,
) {
    val c = LocalRealScaleColors.current
    val pos = remember { mutableStateOf(sweepFromFraction ?: initialFraction) }

    // Optional initial sweep animation: sweepFromFraction -> initialFraction over ~900 ms.
    if (sweepFromFraction != null) {
        androidx.compose.runtime.LaunchedEffect(sweepFromFraction) {
            var t = 0f
            val start = sweepFromFraction
            val end = initialFraction
            val startTime = System.currentTimeMillis()
            while (t < 1f) {
                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
                t = (elapsed / 900L).coerceIn(0f, 1f)
                pos.value = start + (end - start) * t
                if (t < 1f) kotlinx.coroutines.delay(16)
            }
        }
    }

    val shown = if (showOriginal) 1f else pos.value
    val width = remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .background(c.card)
            .onGloballyPositioned { width.value = it.size.width.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun fractionFromX(x: Float): Float = (x / width.value.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    pos.value = fractionFromX(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        pos.value = fractionFromX(change.position.x)
                    }
                }
            },
    ) {
        // After (full, underneath)
        AsyncImage(
            model = afterModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Before (clipped to the left [shown] fraction)
        BoxWithConstraints {
            val density = LocalDensity.current
            val beforeWidth = with(density) {
                (maxWidth.toPx() * shown).coerceIn(0f, maxWidth.toPx()).toDp()
            }
            Box(
                modifier = Modifier
                    .width(beforeWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                AsyncImage(
                    model = beforeModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
