package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight 1.75dp-stroke editorial vector icons tailored to Claude's calm hairline aesthetic.
 * Avoids bundling the 20 MB `material-icons-extended` dependency.
 */
object RealScaleIcons {

    val PhotoFrame: ImageVector by lazy {
        strokeIcon("PhotoFrame") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                lineTo(20f, 18f)
                lineTo(4f, 18f)
                close()
                moveTo(4f, 15f)
                lineTo(9f, 10.5f)
                lineTo(13.5f, 14.5f)
                lineTo(16.5f, 12f)
                lineTo(20f, 15.5f)
                moveTo(8.5f, 9.2f)
                lineTo(8.51f, 9.2f)
            }
        }
    }

    val Sparkle: ImageVector by lazy {
        strokeIcon("Sparkle") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(14.2f, 9.8f)
                lineTo(21f, 12f)
                lineTo(14.2f, 14.2f)
                lineTo(12f, 21f)
                lineTo(9.8f, 14.2f)
                lineTo(3f, 12f)
                lineTo(9.8f, 9.8f)
                close()
            }
        }
    }

    val ModelHub: ImageVector by lazy {
        strokeIcon("ModelHub") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(20f, 7.5f)
                lineTo(20f, 16.5f)
                lineTo(12f, 21f)
                lineTo(4f, 16.5f)
                lineTo(4f, 7.5f)
                close()
                moveTo(12f, 12f)
                lineTo(20f, 7.5f)
                moveTo(12f, 12f)
                lineTo(4f, 7.5f)
                moveTo(12f, 12f)
                lineTo(12f, 21f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        strokeIcon("Settings") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(4f, 12f)
                lineTo(20f, 12f)
                moveTo(4f, 17f)
                lineTo(20f, 17f)
                moveTo(9f, 5f)
                lineTo(9f, 9f)
                moveTo(15f, 10f)
                lineTo(15f, 14f)
                moveTo(10f, 15f)
                lineTo(10f, 19f)
            }
        }
    }

    val ArrowBack: ImageVector by lazy {
        strokeIcon("ArrowBack") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(11f, 6f)
                lineTo(5f, 12f)
                lineTo(11f, 18f)
            }
        }
    }

    val Download: ImageVector by lazy {
        strokeIcon("Download") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 4f)
                lineTo(12f, 15f)
                moveTo(7.5f, 11f)
                lineTo(12f, 15.5f)
                lineTo(16.5f, 11f)
                moveTo(5f, 19.5f)
                lineTo(19f, 19.5f)
            }
        }
    }

    val Pause: ImageVector by lazy {
        strokeIcon("Pause") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(9f, 6f)
                lineTo(9f, 18f)
                moveTo(15f, 6f)
                lineTo(15f, 18f)
            }
        }
    }

    val Play: ImageVector by lazy {
        strokeIcon("Play") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 5.5f)
                lineTo(18f, 12f)
                lineTo(8f, 18.5f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        strokeIcon("Close") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f)
                lineTo(6.5f, 17.5f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        strokeIcon("Trash") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 7f)
                lineTo(19.5f, 7f)
                moveTo(9.5f, 7f)
                lineTo(9.5f, 4.5f)
                lineTo(14.5f, 4.5f)
                lineTo(14.5f, 7f)
                moveTo(6.5f, 7f)
                lineTo(7.5f, 19.5f)
                lineTo(16.5f, 19.5f)
                lineTo(17.5f, 7f)
            }
        }
    }

    val Check: ImageVector by lazy {
        strokeIcon("Check") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12.5f)
                lineTo(9.8f, 17.2f)
                lineTo(19f, 7.2f)
            }
        }
    }

    val Share: ImageVector by lazy {
        strokeIcon("Share") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 15f)
                lineTo(12f, 4f)
                moveTo(8f, 8f)
                lineTo(12f, 4f)
                lineTo(16f, 8f)
                moveTo(5f, 13f)
                lineTo(5f, 19.5f)
                lineTo(19f, 19.5f)
                lineTo(19f, 13f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 8f)
                lineTo(19f, 4f)
                lineTo(15f, 4f)
                moveTo(18.5f, 7.5f)
                lineTo(15.5f, 5.2f)
                lineTo(11.5f, 4.8f)
                lineTo(7.5f, 6.5f)
                lineTo(5.2f, 10.5f)
                lineTo(5.8f, 15f)
                lineTo(8.8f, 18.2f)
                lineTo(13.2f, 19f)
                lineTo(17.5f, 17f)
            }
        }
    }

    val SplitHandle: ImageVector by lazy {
        strokeIcon("SplitHandle") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(9.5f, 8.5f)
                lineTo(6f, 12f)
                lineTo(9.5f, 15.5f)
                moveTo(14.5f, 8.5f)
                lineTo(18f, 12f)
                lineTo(14.5f, 15.5f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        strokeIcon("ChevronDown") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9.5f)
                lineTo(12f, 15.5f)
                lineTo(18f, 9.5f)
            }
        }
    }

    val ChevronUp: ImageVector by lazy {
        strokeIcon("ChevronUp") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 14.5f)
                lineTo(12f, 8.5f)
                lineTo(18f, 14.5f)
            }
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.5f, 10.5f)
                lineTo(15.1f, 12.6f)
                lineTo(13.6f, 14.3f)
                lineTo(11.5f, 15.2f)
                lineTo(9.2f, 15.1f)
                lineTo(7.2f, 14.0f)
                lineTo(5.9f, 12.1f)
                lineTo(5.6f, 9.8f)
                lineTo(6.5f, 7.6f)
                lineTo(8.3f, 6.1f)
                lineTo(10.6f, 5.6f)
                lineTo(12.9f, 6.2f)
                lineTo(14.6f, 7.8f)
                close()
                moveTo(14.5f, 14.5f)
                lineTo(19.5f, 19.5f)
            }
        }
    }

    val LibraryGrid: ImageVector by lazy {
        strokeIcon("LibraryGrid") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 4f); lineTo(10.5f, 4f); lineTo(10.5f, 10.5f); lineTo(4f, 10.5f); close()
                moveTo(13.5f, 4f); lineTo(20f, 4f); lineTo(20f, 10.5f); lineTo(13.5f, 10.5f); close()
                moveTo(4f, 13.5f); lineTo(10.5f, 13.5f); lineTo(10.5f, 20f); lineTo(4f, 20f); close()
                moveTo(13.5f, 13.5f); lineTo(20f, 13.5f); lineTo(20f, 20f); lineTo(13.5f, 20f); close()
            }
        }
    }

    private inline fun strokeIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()
}
