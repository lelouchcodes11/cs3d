package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val shown = HashSet<Any>()

/** -Dcloudstream.revealscroll=keep: fade in what scrolls into view too (to measure what it costs) */
private val SKIP_WHILE_SCROLLING = System.getProperty("cloudstream.revealscroll") != "keep"

/**
 * A block that rises and fades in the first time it is shown, after [delayMs] (a short, growing delay makes rows follow one another instead of
 * landing together). Each [key] does it once per run of the app: going back to a page, or scrolling a row out of sight and back, shows it at
 * once. Off with Settings > Look > Animations: Off.
 */
@Composable
fun Modifier.reveal(key: Any, delayMs: Int = 0, rise: Dp = 14.dp): Modifier {
    if (Appearance.motion == Motion.Off) return this
    // a card or a row that comes into view while the page is being scrolled is shown at once: its fade is a render target per card (measured: a shelf
    // of 12 cards cost 300 ms of graphics work in its first draw with the fade and 25 ms without), and nobody waits for what they are scrolling to
    val first = remember(key) { val fresh = shown.add(key); fresh && !(ScrollActivity.active && SKIP_WHILE_SCROLLING) }
    if (!first) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        // a cascade longer than a blink is slower than the content it shows: rows follow one another over 120 ms at most
        delay(delayMs.coerceAtMost(120).toLong()); progress.animateTo(1f, FluentMotion.tweenIn(FluentMotion.BASE + 60))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise.coerceAtMost(14.dp).toPx()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
