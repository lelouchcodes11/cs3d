package com.lagradost.desktop.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.player.MpvPlayer
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.FluentTheme
import com.lagradost.desktop.ui.fluent.ScaledContent
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import java.awt.Canvas

/**
 * The native video player (beta): mpv draws with its own GPU renderer into a window of its own (a canvas of the page, so it moves and resizes with
 * the app) and the controls are a second, transparent window above it, because a window of the graphics card can not be drawn over by the app's
 * own surface. Tested in `tools/EmbedProto.kt`; SwingPanel's "interop blending" does not show such a window, an owned transparent window does.
 */
object NativeVideo {
    /** The canvas mpv draws into while a native video page is open */
    var canvas by mutableStateOf<Canvas?>(null)

    /** The controls window is up: dialogs and messages are drawn there then (the main window's own layer is behind the video) */
    var overlayActive by mutableStateOf(false)

    /** The native renderer could not be used; the compatible player is used for the rest of this run */
    var broken by mutableStateOf(false)

    fun available(): Boolean = DesktopPlatform.isWindows && Appearance.nativePlayer && !broken

    /** The window handle of [canvas] for mpv; waits up to 3 s for the canvas to have a window (never on the UI thread: 0 then) */
    fun handle(): Long {
        val deadline = System.currentTimeMillis() + 3_000
        while (true) {
            val c = canvas
            if (c != null && c.isDisplayable) runCatching { Native.getComponentPointer(c) }.getOrNull()?.let { return Pointer.nativeValue(it) }
            if (System.currentTimeMillis() > deadline || java.awt.EventQueue.isDispatchThread()) return 0L
            Thread.sleep(50)
        }
    }
}

/** The video window: fills its place on the page, black until mpv draws */
@Composable
fun NativeVideoHost(modifier: Modifier) {
    val canvas = remember { Canvas().apply { background = java.awt.Color.BLACK } }
    DisposableEffect(canvas) {
        NativeVideo.canvas = canvas
        MpvPlayer.nativeWindow = NativeVideo::handle
        onDispose {
            if (NativeVideo.canvas === canvas) NativeVideo.canvas = null
            MpvPlayer.nativeWindow = null
        }
    }
    SwingPanel(factory = { canvas }, modifier = modifier)
}

/**
 * The controls window: transparent, owned by the main window (so it is always above it and goes with it when it is minimised), kept exactly over
 * the video. It does not take the keyboard focus unless [focusable] (a dialog with a text field is open): the keys stay with the main window.
 */
@Composable
fun NativeOverlayWindow(focusable: Boolean, content: @Composable () -> Unit) {
    val canvas = NativeVideo.canvas ?: return
    val state = rememberDialogState(position = WindowPosition(0.dp, 0.dp), size = DpSize(400.dp, 300.dp))
    DialogWindow(onCloseRequest = {}, state = state, undecorated = true, transparent = true, resizable = false, focusable = focusable) {
        DisposableEffect(Unit) {
            NativeVideo.overlayActive = true
            onDispose { NativeVideo.overlayActive = false }
        }
        FluentTheme { ScaledContent { content() } }
    }
    LaunchedEffect(canvas) {
        var lastX = Int.MIN_VALUE; var lastY = 0; var lastW = 0; var lastH = 0
        while (true) {
            runCatching {
                if (canvas.isShowing) {
                    val at = canvas.locationOnScreen
                    if (at.x != lastX || at.y != lastY) { state.position = WindowPosition(at.x.dp, at.y.dp); lastX = at.x; lastY = at.y }
                    if (canvas.width != lastW || canvas.height != lastH) { state.size = DpSize(canvas.width.dp, canvas.height.dp); lastW = canvas.width; lastH = canvas.height }
                }
            }
            delay(16)
        }
    }
}
