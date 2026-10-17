package com.lagradost.desktop.ui.fluent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** How the one-pixel borders are drawn (dev: /border?mode=...); read while drawing, so a change redraws every border */
object EdgeStyle {
    /** "stroke": a one pixel stroke centred half a pixel inside the edge (the GPU's analytic circle / rounded-rectangle shaders); "ring": a filled ring */
    var mode by mutableStateOf(System.getProperty("cloudstream.border") ?: "stroke")

    /** Width of the line in device pixels */
    var width by mutableStateOf((System.getProperty("cloudstream.borderwidth") ?: "1").toFloatOrNull() ?: 1f)
}

/**
 * A filled ring (the outline of the shape minus the same outline one pixel in, even-odd). Both outlines have to be built by hand for the
 * rounded ones: the inner one needs a radius one pixel smaller, else the ring is 1.4 px thick in the corners and 1 px on the straight sides.
 */
private fun ring(size: Size, shape: Shape, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density, w: Float): Path? {
    if (size.width < 4f || size.height < 4f) return null
    val outer = shape.createOutline(size, layoutDirection, density)
    val path = Path().apply { fillType = PathFillType.EvenOdd; addOutline(outer) }
    val inner: Path = when (outer) {
        is Outline.Rectangle -> Path().apply { addRect(outer.rect.deflate(w)) }
        is Outline.Rounded -> Path().apply {
            val r = outer.roundRect
            addRoundRect(
                RoundRect(
                    r.left + w, r.top + w, r.right - w, r.bottom - w,
                    CornerRadius((r.topLeftCornerRadius.x - w).coerceAtLeast(0f), (r.topLeftCornerRadius.y - w).coerceAtLeast(0f)),
                    CornerRadius((r.topRightCornerRadius.x - w).coerceAtLeast(0f), (r.topRightCornerRadius.y - w).coerceAtLeast(0f)),
                    CornerRadius((r.bottomRightCornerRadius.x - w).coerceAtLeast(0f), (r.bottomRightCornerRadius.y - w).coerceAtLeast(0f)),
                    CornerRadius((r.bottomLeftCornerRadius.x - w).coerceAtLeast(0f), (r.bottomLeftCornerRadius.y - w).coerceAtLeast(0f)),
                ),
            )
        }
        is Outline.Generic -> Path().apply { addOutline(shape.createOutline(Size(size.width - 2 * w, size.height - 2 * w), layoutDirection, density)); translate(Offset(w, w)) }
    }
    path.addPath(inner)
    return path
}

/** The stroked edge: the outline pulled in by half the line width so that the whole line is inside the box */
private fun DrawScope.strokeEdge(outline: Outline, color: Color?, brush: Brush?, w: Float) {
    val style = Stroke(width = w)
    val half = w / 2f
    fun paint(draw: (Color) -> Unit, drawBrush: (Brush) -> Unit) {
        if (brush != null) drawBrush(brush) else if (color != null && color.alpha > 0f) draw(color)
    }
    when (outline) {
        is Outline.Rectangle -> {
            val r = outline.rect
            paint(
                { drawRect(it, Offset(r.left + half, r.top + half), Size(r.width - w, r.height - w), style = style) },
                { drawRect(it, Offset(r.left + half, r.top + half), Size(r.width - w, r.height - w), style = style) },
            )
        }
        is Outline.Rounded -> {
            val r = outline.roundRect
            val corner = CornerRadius((r.topLeftCornerRadius.x - half).coerceAtLeast(0f), (r.topLeftCornerRadius.y - half).coerceAtLeast(0f))
            val at = Offset(r.left + half, r.top + half)
            val sz = Size(r.width - w, r.height - w)
            paint({ drawRoundRect(it, at, sz, corner, style) }, { drawRoundRect(it, at, sz, corner, style = style) })
        }
        is Outline.Generic -> paint({ drawPath(outline.path, it, style = style) }, { drawPath(outline.path, it, style = style) })
    }
}

fun Modifier.hairBorder(color: Color, shape: Shape): Modifier = this.drawWithCache {
    // one density-independent pixel: 1 device pixel at 100 %, 1.5 at 150 % (a one-pixel line on a curve shows as a string of dots, a line the width
    // of the scale does not)
    val w = maxOf(1f, EdgeStyle.width * 1.dp.toPx())
    if (EdgeStyle.mode == "ring") {
        val path = ring(size, shape, layoutDirection, this, w)
        onDrawWithContent {
            drawContent()
            if (path != null && color.alpha > 0f) drawPath(path, color)
        }
    } else {
        val outline = if (size.width < 4f || size.height < 4f) null else shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            if (outline != null) strokeEdge(outline, color, null, w)
        }
    }
}

fun Modifier.hairBorder(brush: Brush, shape: Shape): Modifier = this.drawWithCache {
    // one density-independent pixel: 1 device pixel at 100 %, 1.5 at 150 % (a one-pixel line on a curve shows as a string of dots, a line the width
    // of the scale does not)
    val w = maxOf(1f, EdgeStyle.width * 1.dp.toPx())
    if (EdgeStyle.mode == "ring") {
        val path = ring(size, shape, layoutDirection, this, w)
        onDrawWithContent {
            drawContent()
            if (path != null) drawPath(path, brush)
        }
    } else {
        val outline = if (size.width < 4f || size.height < 4f) null else shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            if (outline != null) strokeEdge(outline, null, brush, w)
        }
    }
}
