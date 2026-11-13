package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/** Remembers the collapsed state of the collapsing header border once the content scrolled past 4px. */
@Composable
fun rememberCollapseBorder(thresholdPx: Int = 4): Pair<Boolean, (Float) -> Unit> {
    val scrolled = remember { mutableStateOf(false) }
    val onScroll: (Float) -> Unit = remember {
        { offset ->
            val v = offset > thresholdPx
            if (v != scrolled.value) scrolled.value = v
        }
    }
    return scrolled.value to onScroll
}

/**
 * Top bar — design: h-14, transparent until content scrolls, hairline border when scrolled,
 * display-style title, close (X) or back chevron variant, optional trailing actions.
 */
@Composable
fun RealScaleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    close: Boolean = false,
    scrolled: Boolean = false,
    right: @Composable (() -> Unit)? = null,
) {
    val c = LocalRealScaleColors.current
    Column(modifier.fillMaxWidth().background(c.canvas)) {
        Box(Modifier.fillMaxWidth().statusBarsPadding())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (onBack != null) {
                RealScaleIconButton(
                    onClick = onBack,
                    variant = IconButtonVariant.Plain,
                    contentDescription = if (close) "Close" else "Back",
                    icon = painterResource(if (close) R.drawable.ic_lucide_x else R.drawable.ic_lucide_chevron_left),
                )
            }
            Text(
                title,
                style = RealScaleType.Display28,
                color = c.ink,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 10.dp else 0.dp),
            )
            if (right != null) right()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (scrolled) c.hairline else Color.Transparent),
        )
    }
}

/** Bottom bar container — design: hairline top border, canvas bg, comfortable bottom inset. */
@Composable
fun RealScaleBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = LocalRealScaleColors.current
    Column(modifier.fillMaxWidth().background(c.canvas)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            content()
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp))
    }
}
