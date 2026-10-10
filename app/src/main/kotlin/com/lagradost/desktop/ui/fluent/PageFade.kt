package com.lagradost.desktop.ui.fluent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The bottom of a full-bleed header (the banner of Home, the head of a title page) fades into the page. Where the page behind it is one plain
 * colour (the floating bar and dock looks, no artwork tint, no wallpaper) that is a gradient of the page colour drawn over the header: nothing
 * else to do. Over a tinted page the picture itself has to fade out, which draws the whole header into an off-screen layer of its own first and
 * blends a mask into it, on every frame the header is on screen (a lot for a weak graphics chip).
 */
@Composable
fun Modifier.fadeIntoPage(start: Float = 0.6f): Modifier {
    val c = Fluent.colors
    val plain = Appearance.backdrop != Backdrop.Ambient && Appearance.wallpaper.isBlank() &&
        (Appearance.navPosition == NavPosition.Floating || Appearance.navPosition == NavPosition.Dock)
    val page = c.bg
    return if (plain) {
        // clipped to the header: a picture that is shifted for the parallax would show below it, over what comes next
        this.clipToBounds().drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(start to Color.Transparent, 1f to page))
        }
    } else {
        this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(start to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }
    }
}

/**
 * The ends of a row that scrolls sideways (chips, tabs) fade into the page where more of it is hidden, instead of the last chip being cut in half.
 * Put it before the scroll modifier (`.scrollEdgeFade(state).horizontalScroll(state)`). Only over a plain page, where the fade is a gradient of the
 * page colour; elsewhere the row is left as it was.
 */
@Composable
fun Modifier.scrollEdgeFade(state: androidx.compose.foundation.ScrollState, fade: androidx.compose.ui.unit.Dp = 36.dp): Modifier =
    edgeFade(fade, { state.value > 0 }, { state.value < state.maxValue })

/** The same for a lazy row ([androidx.compose.foundation.lazy.LazyRow] with this state) */
@Composable
fun Modifier.scrollEdgeFade(state: androidx.compose.foundation.lazy.LazyListState, fade: androidx.compose.ui.unit.Dp = 36.dp): Modifier =
    edgeFade(fade, { state.canScrollBackward }, { state.canScrollForward })

@Composable
private fun Modifier.edgeFade(fade: androidx.compose.ui.unit.Dp, back: () -> Boolean, forward: () -> Boolean): Modifier {
    val plain = Appearance.backdrop != Backdrop.Ambient && Appearance.wallpaper.isBlank() &&
        (Appearance.navPosition == NavPosition.Floating || Appearance.navPosition == NavPosition.Dock)
    if (!plain) return this
    val page = Fluent.colors.bg
    return this.drawWithContent {
        drawContent()
        val w = fade.toPx().coerceAtMost(size.width / 2f)
        if (back()) {
            drawRect(Brush.horizontalGradient(0f to page, 1f to Color.Transparent, startX = 0f, endX = w), size = androidx.compose.ui.geometry.Size(w, size.height))
        }
        if (forward()) {
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 1f to page, startX = size.width - w, endX = size.width),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height),
            )
        }
    }
}
