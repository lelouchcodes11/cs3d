package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Play and pause as one shape that morphs: the triangle splits into the two bars (and back) on a spring, instead of one glyph
 * swapping for the other. Two quads, point by point from the triangle to the bars (the right half of the triangle is a quad
 * that has two points on its tip).
 */
@Composable
fun PlayPauseMorph(playing: Boolean, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val k by animateFloatAsState(
        if (playing) 1f else 0f,
        FluentMotion.springSoft(),
        label = "playPauseMorph",
    )
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun p(x: Float, y: Float) = Offset(x * w, y * h)
        fun mix(a: Float, b: Float) = a + (b - a) * k
        // both quads are contours of ONE path: two paths side by side leave a faint line along their shared edge (the antialiased edges of each
        // cover only part of the pixel), which showed as a dark strip through the play triangle
        val shape = Path()
        fun quad(play: List<Pair<Float, Float>>, pause: List<Pair<Float, Float>>) {
            for (i in 0 until 4) {
                val o = p(mix(play[i].first, pause[i].first), mix(play[i].second, pause[i].second))
                if (i == 0) shape.moveTo(o.x, o.y) else shape.lineTo(o.x, o.y)
            }
            shape.close()
        }
        // play: the triangle (0.30,0.14) (0.30,0.86) (0.82,0.50), cut at x = 0.56 into a left quad and a right triangle
        quad(
            listOf(0.30f to 0.14f, 0.56f to 0.32f, 0.56f to 0.68f, 0.30f to 0.86f),
            listOf(0.26f to 0.16f, 0.42f to 0.16f, 0.42f to 0.84f, 0.26f to 0.84f),
        )
        quad(
            listOf(0.56f to 0.32f, 0.82f to 0.50f, 0.82f to 0.50f, 0.56f to 0.68f),
            listOf(0.58f to 0.16f, 0.74f to 0.16f, 0.74f to 0.84f, 0.58f to 0.84f),
        )
        drawPath(shape, tint)
    }
}
