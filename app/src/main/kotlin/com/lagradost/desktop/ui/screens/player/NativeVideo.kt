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

    /** The window handle of [canvas] cached as soon as known */
    @Volatile
    var canvasHwnd: Long = 0L

    /** Persistent set of all native canvas HWNDs ever created for NativeVideo, so WinChrome never subclasses them even during/after teardown */
    private val nativeCanvasHwnds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /** The controls window is up: dialogs and messages are drawn there then (the main window's own layer is behind the video) */
    var overlayActive by mutableStateOf(false)

    /** The native renderer could not be used; the compatible player is used for the rest of this run */
    var broken by mutableStateOf(false)

    fun available(): Boolean = DesktopPlatform.isWindows && Appearance.nativePlayer && !broken

    private interface ClassApi : com.sun.jna.win32.StdCallLibrary {
        fun SetClassLongPtrW(h: Pointer, index: Int, value: Pointer?): Pointer?
        fun GetStockObject(index: Int): Pointer?
    }

    private val classApi: ClassApi? by lazy { runCatching { Native.load("user32", ClassApi::class.java) }.getOrNull() }
    private val gdiApi: ClassApi? by lazy { runCatching { Native.load("gdi32", ClassApi::class.java) }.getOrNull() }

    /**
     * Done once at the start (see Warmups): a throwaway window of the video window's kind, off screen, gives that window class its black brush, so
     * that the first video of a run does not open with the white the very first window of the class shows (measured: pure white for about 0.4 s).
     */
    fun warmUp() {
        if (!DesktopPlatform.isWindows) return
        java.awt.EventQueue.invokeLater {
            runCatching {
                val w = javax.swing.JWindow()
                val c = Canvas().apply { background = java.awt.Color.BLACK }
                w.add(c)
                w.setBounds(-32000, -32000, 1, 1)
                w.addNotify() // the windows exist, nothing is shown
                blackBrush(c)
                w.dispose()
            }
        }
    }

    /** The window's own background brush is black from its first paint (a new window of the default class is white until something paints it) */
    fun blackBrush(c: Canvas) {
        runCatching {
            val h = Native.getComponentPointer(c) ?: return
            val brush = gdiApi?.GetStockObject(4) ?: return // BLACK_BRUSH
            classApi?.SetClassLongPtrW(h, -10, brush) // GCLP_HBRBACKGROUND
        }
    }

    /** Checks whether [h] is the canvas window mpv draws into, so WinChrome does not subclass it */
    fun isNativeCanvas(h: com.sun.jna.platform.win32.WinDef.HWND?): Boolean {
        if (h == null) return false
        val hwndVal = Pointer.nativeValue(h.pointer)
        if (hwndVal in nativeCanvasHwnds) return true
        if (canvasHwnd != 0L && hwndVal == canvasHwnd) return true
        val c = canvas ?: return false
        val ptr = runCatching { Native.getComponentPointer(c) }.getOrNull() ?: return false
        val value = Pointer.nativeValue(ptr)
        if (value != 0L) {
            canvasHwnd = value
            nativeCanvasHwnds.add(value)
        }
        return hwndVal == value
    }

    /** The window handle of [canvas] for mpv; waits up to 3 s for the canvas to have a window (never on the UI thread: 0 then) */
    fun handle(): Long {
        val deadline = System.currentTimeMillis() + 3_000
        while (true) {
            val c = canvas
            if (c != null && c.isDisplayable) runCatching { Native.getComponentPointer(c) }.getOrNull()?.let {
                val v = Pointer.nativeValue(it)
                if (v != 0L) {
                    canvasHwnd = v
                    nativeCanvasHwnds.add(v)
                    return v
                }
            }
            if (System.currentTimeMillis() > deadline || java.awt.EventQueue.isDispatchThread()) return 0L
            Thread.sleep(50)
        }
    }
}

/**
 * The video window: fills its place on the page. It stays hidden until [visible] (the first picture is there): a window of its own shows white
 * for a moment when it opens (measured: about 0.4 s of pure white after Play) and the loading screen of the page is seen instead.
 */
@Composable
fun NativeVideoHost(modifier: Modifier, visible: Boolean = true) {
    val canvas = remember {
        Canvas().apply {
            background = java.awt.Color.BLACK
            addHierarchyListener { if (isDisplayable) NativeVideo.blackBrush(this) }
        }
    }
    DisposableEffect(canvas) {
        NativeVideo.canvas = canvas
        MpvPlayer.nativeWindow = NativeVideo::handle
        onDispose {
            if (NativeVideo.canvas === canvas) {
                NativeVideo.canvas = null
                NativeVideo.canvasHwnd = 0L
            }
            MpvPlayer.nativeWindow = null
            // Proactively detach the canvas from mpv so VO stops rendering before Canvas peer is destroyed
            MpvPlayer.active?.detachNativeWindow()
        }
    }
    // a black panel around the video window (the default one is light grey) and the window's own background brush black (the default one is white):
    // both showed as a bright flash for about 0.4 s when the page opened
    SwingPanel(
        factory = { javax.swing.JPanel(java.awt.BorderLayout()).apply { background = java.awt.Color.BLACK; isOpaque = true; add(canvas, java.awt.BorderLayout.CENTER) } },
        modifier = modifier, background = androidx.compose.ui.graphics.Color.Black,
    )
}

/** Where the video window is on the screen (its panel's place when it is hidden); null while the page is not on screen */
private fun videoOrigin(canvas: Canvas): java.awt.Point? {
    if (canvas.isShowing) return canvas.locationOnScreen
    val host = canvas.parent ?: return null
    if (!host.isShowing) return null
    return host.locationOnScreen.also { it.translate(canvas.x, canvas.y) }
}

/**
 * The controls window: transparent, owned by the main window (so it is always above it and goes with it when it is minimised), kept exactly over
 * the video. It does not take the keyboard focus unless [focusable] (a dialog with a text field is open): the keys stay with the main window.
 */
@Composable
fun NativeOverlayWindow(focusable: Boolean, content: @Composable () -> Unit) {
    val canvas = NativeVideo.canvas ?: return
    // the window is not shown before it has its place over the video: it used to appear small at the top left of the screen and then jump
    var placed by remember(canvas) { mutableStateOf(false) }
    val state = rememberDialogState(
        position = runCatching { videoOrigin(canvas)?.let { WindowPosition(it.x.dp, it.y.dp) } }.getOrNull() ?: WindowPosition(0.dp, 0.dp),
        size = if (canvas.width > 0 && canvas.height > 0) DpSize(canvas.width.dp, canvas.height.dp) else DpSize(400.dp, 300.dp),
    )
    DialogWindow(onCloseRequest = {}, state = state, visible = placed, undecorated = true, transparent = true, resizable = false, focusable = focusable) {
        DisposableEffect(Unit) {
            // Compose makes a dialog window modal, and a modal one DISABLES the window it belongs to: the app's window could not be brought
            // back from the taskbar, did not take the keys, and closing the modal dialog (leaving the page, above all from full screen) was
            // what froze the app. This is only a layer over the video: a modeless one is all that is needed.
            val d = window
            if (d.modalityType != java.awt.Dialog.ModalityType.MODELESS) {
                val shown = d.isVisible
                if (shown) d.isVisible = false
                d.modalityType = java.awt.Dialog.ModalityType.MODELESS
                if (shown) d.isVisible = true
            }
            NativeVideo.overlayActive = true
            onDispose { NativeVideo.overlayActive = false }
        }
        FluentTheme { ScaledContent { content() } }
    }
    LaunchedEffect(canvas) {
        var lastX = Int.MIN_VALUE; var lastY = 0; var lastW = 0; var lastH = 0
        while (true) {
            runCatching {
                val at = videoOrigin(canvas)
                if (at != null) {
                    if (at.x != lastX || at.y != lastY) { state.position = WindowPosition(at.x.dp, at.y.dp); lastX = at.x; lastY = at.y }
                    if (canvas.width != lastW || canvas.height != lastH) { state.size = DpSize(canvas.width.dp, canvas.height.dp); lastW = canvas.width; lastH = canvas.height }
                    // in place: let the window take its bounds, then show it
                    if (!placed && lastW > 0 && lastH > 0) { delay(80); placed = true }
                }
            }
            delay(16)
        }
    }
}
