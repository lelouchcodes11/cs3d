package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Settings > Look > Screen dimmer: black over everything, for a room darker than the screen's lowest brightness. It takes no clicks (a plain
 * background is not hit). Drawn last in the main window and in the player's controls window, which lies over the video.
 */
@Composable
fun DimmerLayer() {
    val dim = Appearance.dimmer
    if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
}
