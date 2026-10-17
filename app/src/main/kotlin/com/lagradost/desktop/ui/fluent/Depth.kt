package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Motion curves taken from the way NuvioDesktop opens things (reference/nuvio-desktop, PosterOpenMotion and DetailsContentReveal): a long, soft
 * landing for content that arrives, and a fast start with a long tail for things that grow.
 */
object MotionCurves {
    /** content that comes in: leaves at once, settles slowly */
    val easing = CubicBezierEasing(0.5f, 0f, 0.14f, 1f)

    /** a thing that grows or moves to a new place */
    val expand = CubicBezierEasing(0.345f, 0.602f, 0.186f, 1f)
}

/**
 * The top-lit hairline of a card or poster (bright at the top edge, nearly gone at the bottom): a card looks like a piece with a thickness
 * under a light above it, which a flat all-round outline does not. [strength] 1 is the quiet resting look, more for a hovered card.
 */
@Composable
fun depthEdge(strength: Float = 1f): Brush {
    val dark = Fluent.colors.dark
    val tint = if (dark) Color.White else Color.Black
    val top = (if (dark) 0.17f else 0.12f) * strength
    return Brush.verticalGradient(0f to tint.copy(alpha = top.coerceAtMost(0.6f)), 0.45f to tint.copy(alpha = top * 0.35f), 1f to tint.copy(alpha = top * 0.18f))
}
