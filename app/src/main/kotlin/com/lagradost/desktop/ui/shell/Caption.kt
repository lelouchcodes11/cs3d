package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.platform.WinChrome
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon

/** Room the pages leave at the right of the top row for the caption buttons (0 with the native title bar) */
val captionInset: Dp
    @Composable get() = if (WinChrome.enabled && !WinChrome.fullscreen && !WinChrome.pip) 138.dp else 0.dp

/**
 * Marks an area of the integrated title bar band as a control, so a press there reaches the app instead of
 * dragging the window.
 */
@Composable
fun Modifier.noWindowDrag(key: Any): Modifier {
    DisposableEffect(key) { onDispose { WinChrome.clearNoDrag(key) } }
    return this.onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        WinChrome.setNoDrag(key, b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
    }
}

/**
 * Minimize / maximize / close of the integrated title bar. Only drawn here: the pointer over them is
 * reported to Windows as caption buttons (Snap Layouts, click handling), see [WinChrome].
 */
@Composable
fun BoxScopeCaptionButtons(modifier: Modifier = Modifier) {
    if (!WinChrome.enabled || WinChrome.fullscreen || WinChrome.pip) return
    val c = Fluent.colors
    Row(modifier) {
        CaptionButton("", 1, c.subtleHover, Color.Unspecified)
        CaptionButton(if (WinChrome.maximized) "" else "", 2, c.subtleHover, Color.Unspecified)
        CaptionButton("", 3, Color(0xFFC42B1C), Color.White)
    }
}

@Composable
private fun CaptionButton(glyph: String, id: Int, hoverFill: Color, hoverTint: Color) {
    val c = Fluent.colors
    val hovered = WinChrome.hover == id
    val pressed = hovered && WinChrome.pressed == id
    Box(
        Modifier.size(46.dp, 32.dp).background(if (pressed) hoverFill.copy(alpha = hoverFill.alpha * 0.7f) else if (hovered) hoverFill else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, size = 10.dp, tint = if (hovered && hoverTint != Color.Unspecified) hoverTint else c.text)
    }
}
