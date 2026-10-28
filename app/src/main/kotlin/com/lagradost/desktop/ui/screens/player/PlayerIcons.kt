package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The tools of the player's bar */
internal enum class PlayerGlyph { Subtitles, Sources, Speed, Episodes, Pip, Settings, Fullscreen, ExitFullscreen, Tracks, Previous, Next }

/**
 * The icons of the player's buttons, drawn on a 24 x 24 grid with one stroke weight (1.9), round ends and round joins, so they are the same
 * weight and the same optical size whatever they show and stay sharp at every scale (the font glyphs had different weights and sizes, and
 * several (the server link, the playlist) were hard to tell apart at 18 dp).
 */
@Composable
internal fun PlayerIcon(glyph: PlayerGlyph, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = Color.White) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, tint) }
}

private const val WEIGHT = 1.9f

private fun DrawScope.drawGlyph(glyph: PlayerGlyph, tint: Color) {
    val u = size.width / 24f
    val stroke = Stroke(WEIGHT * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), WEIGHT * u, StrokeCap.Round)
    fun box(l: Float, t: Float, r: Float, b: Float, radius: Float, fill: Boolean = false) =
        drawRoundRect(tint, p(l, t), Size((r - l) * u, (b - t) * u), CornerRadius(radius * u), style = if (fill) Fill else stroke)
    // [solid]: filled as well as outlined, so the corners of a small triangle are as round as the lines
    fun path(solid: Boolean = false, build: Path.(Float) -> Unit) {
        val shape = Path().apply { build(u) }
        if (solid) drawPath(shape, tint, style = Fill)
        drawPath(shape, tint, style = stroke)
    }
    fun dot(x: Float, y: Float, r: Float) = drawCircle(tint, r * u, p(x, y))

    when (glyph) {
        PlayerGlyph.Subtitles -> {
            box(3f, 5f, 21f, 19f, 3.5f)
            line(7f, 11f, 10f, 11f); line(13f, 11f, 17f, 11f)
            line(7f, 15f, 12f, 15f); line(14.5f, 15f, 17f, 15f)
        }
        PlayerGlyph.Sources -> {
            // two servers one above the other, each with its light: "another server"
            box(3.5f, 4f, 20.5f, 10.5f, 2.5f)
            box(3.5f, 13.5f, 20.5f, 20f, 2.5f)
            dot(7.5f, 7.25f, 1.1f); dot(7.5f, 16.75f, 1.1f)
            line(12f, 7.25f, 17f, 7.25f); line(12f, 16.75f, 17f, 16.75f)
        }
        PlayerGlyph.Speed -> {
            // a gauge: an open arc and a needle
            drawArc(tint, 150f, 240f, false, p(3f, 4f), Size(18f * u, 18f * u), style = stroke)
            line(12f, 13f, 16.2f, 8.4f)
            dot(12f, 13f, 1.5f)
        }
        PlayerGlyph.Episodes -> {
            line(4f, 7f, 20f, 7f)
            line(4f, 12f, 14f, 12f)
            line(4f, 17f, 10f, 17f)
            // the play mark of a playlist
            path(solid = true) { u -> moveTo(15f * u, 14.5f * u); lineTo(20.5f * u, 17.5f * u); lineTo(15f * u, 20.5f * u); close() }
        }
        PlayerGlyph.Pip -> {
            box(3f, 5f, 21f, 19f, 3.5f)
            box(12f, 11f, 18f, 16f, 1.6f, fill = true)
        }
        PlayerGlyph.Settings -> {
            // three sliders; the line is cut around each knob
            for ((y, kx) in listOf(6f to 15f, 12f to 8f, 18f to 14f)) {
                line(4f, y, kx - 3.1f, y); line(kx + 3.1f, y, 20f, y)
                drawCircle(tint, 2.1f * u, p(kx, y), style = stroke)
            }
        }
        PlayerGlyph.Fullscreen -> {
            path { u -> moveTo(4f * u, 9f * u); lineTo(4f * u, 5f * u); quadraticTo(4f * u, 4f * u, 5f * u, 4f * u); lineTo(9f * u, 4f * u) }
            path { u -> moveTo(15f * u, 4f * u); lineTo(19f * u, 4f * u); quadraticTo(20f * u, 4f * u, 20f * u, 5f * u); lineTo(20f * u, 9f * u) }
            path { u -> moveTo(20f * u, 15f * u); lineTo(20f * u, 19f * u); quadraticTo(20f * u, 20f * u, 19f * u, 20f * u); lineTo(15f * u, 20f * u) }
            path { u -> moveTo(9f * u, 20f * u); lineTo(5f * u, 20f * u); quadraticTo(4f * u, 20f * u, 4f * u, 19f * u); lineTo(4f * u, 15f * u) }
        }
        PlayerGlyph.ExitFullscreen -> {
            path { u -> moveTo(4f * u, 9f * u); lineTo(8f * u, 9f * u); quadraticTo(9f * u, 9f * u, 9f * u, 8f * u); lineTo(9f * u, 4f * u) }
            path { u -> moveTo(15f * u, 4f * u); lineTo(15f * u, 8f * u); quadraticTo(15f * u, 9f * u, 16f * u, 9f * u); lineTo(20f * u, 9f * u) }
            path { u -> moveTo(20f * u, 15f * u); lineTo(16f * u, 15f * u); quadraticTo(15f * u, 15f * u, 15f * u, 16f * u); lineTo(15f * u, 20f * u) }
            path { u -> moveTo(9f * u, 20f * u); lineTo(9f * u, 16f * u); quadraticTo(9f * u, 15f * u, 8f * u, 15f * u); lineTo(4f * u, 15f * u) }
        }
        PlayerGlyph.Tracks -> {
            // the sound as bars: the audio (and video) tracks
            line(4.5f, 10f, 4.5f, 14f); line(8.25f, 7f, 8.25f, 17f); line(12f, 4f, 12f, 20f); line(15.75f, 8f, 15.75f, 16f); line(19.5f, 10.5f, 19.5f, 13.5f)
        }
        PlayerGlyph.Previous -> {
            line(6f, 6f, 6f, 18f)
            path(solid = true) { u -> moveTo(18f * u, 6.5f * u); lineTo(10f * u, 12f * u); lineTo(18f * u, 17.5f * u); close() }
        }
        PlayerGlyph.Next -> {
            line(18f, 6f, 18f, 18f)
            path(solid = true) { u -> moveTo(6f * u, 6.5f * u); lineTo(14f * u, 12f * u); lineTo(6f * u, 17.5f * u); close() }
        }
    }
}
