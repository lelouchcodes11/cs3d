package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Motion curves for opening things: a long, soft landing for content that arrives, and a fast start with a long tail for things that grow.
 */
object MotionCurves {
    /** content that comes in: leaves at once, settles slowly */
    val easing = CubicBezierEasing(0.5f, 0f, 0.14f, 1f)

    /** a thing that grows or moves to a new place */
    val expand = CubicBezierEasing(0.345f, 0.602f, 0.186f, 1f)
}
