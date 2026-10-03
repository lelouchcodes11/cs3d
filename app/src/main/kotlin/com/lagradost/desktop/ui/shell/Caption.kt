package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxScope
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
    @Composable get() = 0.dp // the caption buttons are in the title bar, never over the pages

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

/** Height of the title bar; the caption buttons are as high (the hit test of [WinChrome] knows the same number) */
val TitleBarHeight = 36.dp

/**
 * The title bar: back button, the app mark and the title at the left, minimize / maximize / close at the right; one full width opaque bar.
 * The buttons are only drawn here: the pointer over them is reported to Windows as caption buttons (Snap Layouts, click handling),
 * see [WinChrome]. The empty part drags the window.
 *
 * A window that is not maximized shows it as a row of its own above the app ([ExternalTitleBar]: the app starts below it).
 * A maximized window hides it; the pointer at the top edge brings it back over the app ([RevealedTitleBar]) and it goes away
 * at once when the pointer moves off it.
 */
@Composable
private fun TitleBarRow(modifier: Modifier = Modifier) {
    val c = Fluent.colors
    Row(
        modifier.fillMaxWidth().background(c.bg).drawBehind { drawLine(c.stroke, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp))
        com.lagradost.desktop.ui.fluent.IconButton(
            com.lagradost.desktop.ui.fluent.Icons.Back, { com.lagradost.desktop.core.Navigator.back() },
            Modifier.noWindowDrag("barBack"), tooltip = "Back (Alt+Left)", enabled = com.lagradost.desktop.core.Navigator.canGoBack, size = 30.dp, iconSize = 14.dp,
        )
        Box(Modifier.width(8.dp))
        com.lagradost.desktop.ui.components.brandLogo()?.let { androidx.compose.foundation.Image(it, null, Modifier.size(16.dp)) }
        Box(Modifier.width(8.dp))
        com.lagradost.desktop.ui.fluent.FText(ShellState.windowTitle, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(max = 420.dp))
        Box(Modifier.weight(1f))
        CaptionButtons()
    }
}

/** The bar of a window that is not maximized: above the app, which starts below it (the row grows and shrinks with the window state) */
@Composable
fun ExternalTitleBar() {
    val show = WinChrome.windowedBar
    val h by androidx.compose.animation.core.animateDpAsState(if (show) TitleBarHeight else 0.dp, com.lagradost.desktop.ui.fluent.FluentMotion.tweenStd(140))
    if (h > 0.dp) Box(Modifier.fillMaxWidth().height(h).clip(androidx.compose.ui.graphics.RectangleShape)) {
        TitleBarRow(Modifier.requiredHeight(TitleBarHeight))
    }
}

/** The bar of a maximized window: over the app while the pointer is at the top edge, gone the moment it leaves */
@Composable
fun BoxScope.RevealedTitleBar() {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            WinChrome.pollReveal()
            kotlinx.coroutines.delay(20)
        }
    }
    if (!WinChrome.enabled || WinChrome.fullscreen || WinChrome.pip || !WinChrome.autoHides) return
    val enter = androidx.compose.animation.slideInVertically(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(120)) { -it } + androidx.compose.animation.fadeIn(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(100))
    val exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(70)) { -it } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(60))
    androidx.compose.animation.AnimatedVisibility(WinChrome.revealed, Modifier.align(Alignment.TopStart).fillMaxWidth(), enter = enter, exit = exit) {
        TitleBarRow(Modifier.requiredHeight(TitleBarHeight))
    }
}

@Composable
private fun CaptionButtons() {
    val c = Fluent.colors
    CaptionButton("", 1, c.subtleHover, Color.Unspecified)
    CaptionButton(if (WinChrome.maximized) "" else "", 2, c.subtleHover, Color.Unspecified)
    CaptionButton("", 3, Color(0xFFC42B1C), Color.White)
}

@Composable
private fun CaptionButton(glyph: String, id: Int, hoverFill: Color, hoverTint: Color) {
    val c = Fluent.colors
    val hovered = WinChrome.hover == id
    val pressed = hovered && WinChrome.pressed == id
    Box(
        Modifier.size(46.dp, TitleBarHeight).background(if (pressed) hoverFill.copy(alpha = hoverFill.alpha * 0.7f) else if (hovered) hoverFill else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, size = 10.dp, tint = if (hovered && hoverTint != Color.Unspecified) hoverTint else c.text)
    }
}
