package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val shown = HashSet<Any>()

/**
 * A block that rises and fades in the first time it is shown, after [delayMs] (a short, growing delay makes rows follow one another instead of
 * landing together). Each [key] does it once per run of the app: going back to a page, or scrolling a row out of sight and back, shows it at
 * once. Off with Settings > Look > Animations: Off.
 */
@Composable
fun Modifier.reveal(key: Any, delayMs: Int = 0, rise: Dp = 26.dp): Modifier {
    if (Appearance.motion == Motion.Off) return this
    val first = remember(key) { shown.add(key) }
    if (!first) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(560, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise.toPx()
    }
}
