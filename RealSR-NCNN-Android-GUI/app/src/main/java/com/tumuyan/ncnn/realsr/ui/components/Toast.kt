package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/** App-wide toast — design: bottom-28, inverse pill, 15px, 3s. */
@Composable
fun RealScaleToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    val c = LocalRealScaleColors.current
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(androidx.compose.animation.core.tween(150)) + slideInVertically(androidx.compose.animation.core.tween(200, easing = com.tumuyan.ncnn.realsr.ui.theme.SpringEasing)) { it / 3 },
            exit = fadeOut(androidx.compose.animation.core.tween(150)) + slideOutVertically(androidx.compose.animation.core.tween(120)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .padding(bottom = 112.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.inverse)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    style = RealScaleType.Body14,
                    color = c.onInverse,
                )
            }
        }
    }
}
