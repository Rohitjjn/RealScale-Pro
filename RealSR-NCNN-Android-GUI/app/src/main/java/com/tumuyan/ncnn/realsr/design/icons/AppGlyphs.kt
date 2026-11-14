package com.tumuyan.ncnn.realsr.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The two first-run glyphs, written by hand instead of generated.
 *
 * They sit in the 64 dp tile of the onboarding permission screens — the only illustration a new user
 * sees before the app is usable — so they use nothing but absolute `M`/`L`/`Q`/`C`/`Z` commands:
 * no arcs, no implicit lineto, no number written straight after another one (`0 0-3.4`). That makes
 * them immune to parser corner cases in `addPathNodes` and they are pinned by
 * `DesignIconInstrumentedTest`, which rasterises them and counts ink pixels.
 */
object AppGlyphs {

    /** Photo / gallery: a rounded frame with a horizon and a sun. */
    val Photo: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppPhoto",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            stroke("M 8 4 L 16 4 Q 19 4 19 7 L 19 17 Q 19 20 16 20 L 8 20 Q 5 20 5 17 L 5 7 Q 5 4 8 4 Z")
            stroke("M 5.6 17.2 L 10 12.8 L 13 15.8 L 15.4 13.4 L 18.4 16.4")
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addSvgPath(
                    "M 9 7.3 C 9.72 7.3 10.3 7.88 10.3 8.6 C 10.3 9.32 9.72 9.9 9 9.9 " +
                        "C 8.28 9.9 7.7 9.32 7.7 8.6 C 7.7 7.88 8.28 7.3 9 7.3 Z"
                )
            }
        }.build()
    }

    /** Notification: a bell with its clapper. */
    val Bell: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppBell",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            stroke(
                "M 6.5 17 C 8 15.5 8.5 14 8.5 10.5 C 8.5 7.4 10 5 12 5 " +
                    "C 14 5 15.5 7.4 15.5 10.5 C 15.5 14 16 15.5 17.5 17 Z"
            )
            stroke("M 10.2 20 C 10.6 20.8 11.3 21.3 12 21.3 C 12.7 21.3 13.4 20.8 13.8 20")
        }.build()
    }

    private fun ImageVector.Builder.stroke(data: String) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            addSvgPath(data)
        }
    }
}
