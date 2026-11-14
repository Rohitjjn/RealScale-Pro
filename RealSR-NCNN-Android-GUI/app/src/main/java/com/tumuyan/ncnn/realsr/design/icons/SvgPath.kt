package com.tumuyan.ncnn.realsr.design.icons

import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes

/**
 * Replays SVG path data into a [PathBuilder].
 *
 * `addPathNodes(str)` only *parses* the string and returns the nodes — it does not touch the
 * builder it is called from. Calling it as a bare statement inside `path { }` therefore compiled
 * fine and added nothing, which left every generated glyph with zero geometry and drew 49 invisible
 * icons: the onboarding permission tiles showed their card but no photo or bell inside.
 *
 * `DesignIconInstrumentedTest` guards both halves: every glyph must carry nodes, and the two
 * first-run glyphs must rasterise ink on the emulator.
 */
internal fun PathBuilder.addSvgPath(data: String?) {
    for (node in addPathNodes(data)) {
        when (node) {
            is PathNode.Close -> close()
            is PathNode.MoveTo -> moveTo(node.x, node.y)
            is PathNode.RelativeMoveTo -> moveToRelative(node.dx, node.dy)
            is PathNode.LineTo -> lineTo(node.x, node.y)
            is PathNode.RelativeLineTo -> lineToRelative(node.dx, node.dy)
            is PathNode.HorizontalTo -> horizontalLineTo(node.x)
            is PathNode.RelativeHorizontalTo -> horizontalLineToRelative(node.dx)
            is PathNode.VerticalTo -> verticalLineTo(node.y)
            is PathNode.RelativeVerticalTo -> verticalLineToRelative(node.dy)
            is PathNode.CurveTo ->
                curveTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
            is PathNode.RelativeCurveTo ->
                curveToRelative(node.dx1, node.dy1, node.dx2, node.dy2, node.dx3, node.dy3)
            is PathNode.ReflectiveCurveTo ->
                reflectiveCurveTo(node.x1, node.y1, node.x2, node.y2)
            is PathNode.RelativeReflectiveCurveTo ->
                reflectiveCurveToRelative(node.dx1, node.dy1, node.dx2, node.dy2)
            is PathNode.QuadTo -> quadTo(node.x1, node.y1, node.x2, node.y2)
            is PathNode.RelativeQuadTo -> quadToRelative(node.dx1, node.dy1, node.dx2, node.dy2)
            is PathNode.ReflectiveQuadTo -> reflectiveQuadTo(node.x1, node.y1)
            is PathNode.RelativeReflectiveQuadTo -> reflectiveQuadToRelative(node.dx, node.dy)
            is PathNode.ArcTo -> arcTo(
                node.horizontalEllipseRadius,
                node.verticalEllipseRadius,
                node.theta,
                node.isMoreThanHalf,
                node.isPositiveArc,
                node.arcStartX,
                node.arcStartY,
            )
            is PathNode.RelativeArcTo -> arcToRelative(
                node.horizontalEllipseRadius,
                node.verticalEllipseRadius,
                node.theta,
                node.isMoreThanHalf,
                node.isPositiveArc,
                node.arcStartDx,
                node.arcStartDy,
            )
        }
    }
}
