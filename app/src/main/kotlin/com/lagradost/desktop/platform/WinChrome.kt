package com.lagradost.desktop.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.Callback
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap

/**
 * Integrated Windows 11 title bar: the app's content extends into the caption area while the window keeps
 * its native frame behaviour (drag, double-click maximize, Aero Snap, Snap Layouts on the maximize button,
 * resize borders, system menu). Done like FlatLaf's native window border: the AWT window procedure is
 * subclassed, WM_NCCALCSIZE removes the caption, WM_NCHITTEST decides what the pointer is over.
 *
 * Compose draws into a child window (SunAwtCanvas) that covers the whole client area, and Windows asks the
 * deepest child first. So that child answers "transparent" over the caption buttons, the drag band and the top
 * resize edge, which hands the decision (and the clicks) to the frame window.
 *
 * Compose draws the caption buttons (see `WindowCaptionButtons`); the system acts on clicks because the hit
 * test reports them as HTMINBUTTON / HTMAXBUTTON / HTCLOSE. If anything fails the native title bar stays.
 *
 * Full screen is done here too: a borderless window over the whole monitor (what video players do), not AWT's
 * exclusive full screen mode.
 */
object WinChrome {
    /** The subclass is installed: pages must leave room for the caption buttons */
    var enabled by mutableStateOf(false)
        private set

    /** 0 none, 1 minimize, 2 maximize, 3 close: the caption button under the pointer */
    var hover by mutableStateOf(0)

    /** Caption button being pressed (same numbering as [hover]) */
    var pressed by mutableStateOf(0)
        private set

    var maximized by mutableStateOf(false)
        private set

    /** Full screen (borderless over the monitor): no caption buttons, no frame */
    var fullscreen by mutableStateOf(false)
        private set

    /** Picture in picture: a small borderless window above all others, only the strip at its top drags it */
    var pip by mutableStateOf(false)
        private set

    private const val REVEAL_DWELL_MS = 140L
    private var hotSince = 0L

    /** The caption buttons are hidden (maximized window, pointer away from the top): the hit test then reports the band as a drag area only */
    @Volatile
    var captionHidden = false
        private set

    /** Compose: the hidden caption bar is shown now (the pointer is at the top edge of the maximized window) */
    var revealed by mutableStateOf(false)
        private set

    /**
     * The video page keeps the title bar as a row of its own whatever the window state: a bar that slides over the app when the pointer
     * touches the top edge can not be seen over the video (its window is above the app's) and the app could only be closed by leaving the video.
     */
    var keepBar by mutableStateOf(false)

    /** The caption bar hides itself in full screen and, unless Settings > Look says otherwise, while the window is maximized (not in pip) */
    val autoHides: Boolean get() = enabled && (fullscreen || (maximized && com.lagradost.desktop.ui.fluent.Appearance.hideTitleBar)) && !pip && !keepBar

    /** The title bar is a row of its own above the app (a window that is not full screen; a maximized one only when it is set to keep it) */
    val windowedBar: Boolean get() = enabled && !fullscreen && !pip && (!maximized || keepBar || !com.lagradost.desktop.ui.fluent.Appearance.hideTitleBar)

    /** Dev: a pretend pointer position (window px) used instead of the real pointer by [pollReveal] */
    @Volatile
    var debugPointer: IntArray? = null

    private var leftTopSince = 0L

    /**
     * Called ~25 times a second: reveals the bar when the pointer touches the top edge of the maximized window and hides it again
     * a moment after the pointer left the bar.
     */
    fun pollReveal() {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        if (!autoHides) {
            if (revealed) revealed = false
            captionHidden = false
            return
        }
        val p = IntArray(2)
        val inside = runCatching { api.GetCursorPos(p) }.getOrDefault(false)
        val origin = intArrayOf(0, 0)
        api.ClientToScreen(hwnd, origin)
        val client = IntArray(4)
        api.GetClientRect(hwnd, client)
        val dpi = runCatching { api.GetDpiForWindow(hwnd) }.getOrDefault(96).takeIf { it > 0 } ?: 96
        val scale = dpi / 96.0
        val x = debugPointer?.get(0) ?: (p[0] - origin[0])
        val y = debugPointer?.get(1) ?: (p[1] - origin[1])
        val fake = debugPointer
        val over = if (fake != null) true else inside && x >= 0 && x < client[2] - client[0] && y >= -2
        val now = System.currentTimeMillis()
        // a thin strip at the very edge, and the pointer has to stay there a moment (a flick past the edge does not show the bar)
        val hot = (2 * scale).toInt().coerceAtLeast(2)
        val keep = (BUTTON_HEIGHT_DP * scale).toInt() + 2
        // shown at the top edge; hidden at once when the pointer is off the bar (or off the window)
        if (over && y <= hot) {
            if (hotSince == 0L) hotSince = now
            if (now - hotSince >= REVEAL_DWELL_MS) revealed = true
        } else hotSince = 0L
        if (revealed && !(over && y <= keep)) revealed = false
        captionHidden = !revealed
    }

    /** Areas of the band that are controls (search box, avatar, back button, ...): window coordinates, px */
    private val noDrag = ConcurrentHashMap<Any, IntArray>()

    fun setNoDrag(key: Any, left: Int, top: Int, right: Int, bottom: Int) {
        noDrag[key] = intArrayOf(left, top, right, bottom)
    }

    fun clearNoDrag(key: Any) {
        noDrag.remove(key)
    }

    // ---------------------------------------------------------------------------------------------

    private interface User32Ex : StdCallLibrary {
        fun SetWindowLongPtrW(hwnd: WinDef.HWND, index: Int, proc: Callback): Pointer
        fun SetWindowLongPtrW(hwnd: WinDef.HWND, index: Int, value: Long): Long
        fun GetWindowLongPtrW(hwnd: WinDef.HWND, index: Int): Long
        fun CallWindowProcW(prev: Pointer, hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Long
        fun DefWindowProcW(hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Long
        fun GetWindowRect(hwnd: WinDef.HWND, rect: IntArray): Boolean
        fun GetCursorPos(point: IntArray): Boolean
        fun GetClientRect(hwnd: WinDef.HWND, rect: IntArray): Boolean
        fun ClientToScreen(hwnd: WinDef.HWND, point: IntArray): Boolean
        fun IsZoomed(hwnd: WinDef.HWND): Boolean
        fun IsIconic(hwnd: WinDef.HWND): Boolean
        fun ShowWindow(hwnd: WinDef.HWND, cmd: Int): Boolean
        fun GetDpiForWindow(hwnd: WinDef.HWND): Int
        fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int
        fun SetWindowPos(hwnd: WinDef.HWND, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
        fun EnumChildWindows(hwnd: WinDef.HWND, cb: WinUser.WNDENUMPROC, data: Pointer?): Boolean
        fun GetClassNameW(hwnd: WinDef.HWND, buf: CharArray, max: Int): Int
        fun MonitorFromWindow(hwnd: WinDef.HWND, flags: Int): Pointer?
        /** [point]: the POINT structure passed by value, x in the low 32 bits and y in the high ones */
        fun MonitorFromPoint(point: Long, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: Pointer): Boolean
        fun TrackMouseEvent(event: Pointer): Boolean
        fun PostMessageW(hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Boolean
    }

    interface WndProc : Callback {
        fun callback(hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Long
    }

    private val user: User32Ex? by lazy { runCatching { Native.load("user32", User32Ex::class.java) }.getOrNull() }

    private const val GWLP_WNDPROC = -4
    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0xC00000L
    private const val WS_THICKFRAME = 0x40000L
    private const val WM_SIZE = 0x0005
    private const val WM_SIZING = 0x0214
    private const val WM_NCACTIVATE = 0x0086
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCMOUSEMOVE = 0x00A0
    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val WM_NCLBUTTONUP = 0x00A2
    private const val WM_NCLBUTTONDBLCLK = 0x00A3
    private const val WM_SYSCOMMAND = 0x0112
    private const val SC_MINIMIZE = 0xF020
    private const val SC_MAXIMIZE = 0xF030
    private const val SC_RESTORE = 0xF120
    private const val SC_CLOSE = 0xF060
    private const val WM_NCMOUSELEAVE = 0x02A2
    private const val HTCLIENT = 1
    private const val HTLEFT = 10
    private const val HTBOTTOMRIGHT = 17
    private const val HTCAPTION = 2
    private const val HTMINBUTTON = 8
    private const val HTMAXBUTTON = 9
    private const val HTCLOSE = 20
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val HTTRANSPARENT = -1
    private const val SM_CXSIZEFRAME = 32
    private const val SM_CYSIZEFRAME = 33
    private const val SM_CXPADDEDBORDER = 92
    private const val SWP_NOSIZE = 0x1
    private const val SWP_NOMOVE = 0x2
    private const val SWP_NOZORDER = 0x4
    private const val SWP_NOACTIVATE = 0x10
    private const val SWP_FRAMECHANGED = 0x20
    private const val SWP_SHOWWINDOW = 0x40
    private const val SW_MAXIMIZE = 3
    private const val SW_RESTORE = 9
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val TME_LEAVE = 0x2
    private const val TME_NONCLIENT = 0x10

    /** keeps the callbacks reachable for native code */
    private val procs = ArrayList<WndProc>()
    private val subclassed = HashSet<Long>()
    private var topHwnd: WinDef.HWND? = null

    private const val BUTTON_WIDTH_DP = 46
    private const val BUTTON_HEIGHT_DP = 36
    const val DRAG_HEIGHT_DP = 36

    private fun hwndOf(window: Window): WinDef.HWND = WinDef.HWND(Pointer(Native.getComponentID(window)))

    /** Installs the integrated title bar; returns false (and changes nothing) when it is not available */
    fun install(window: Window): Boolean {
        if (enabled) return true
        if (System.getProperty("cloudstream.titlebar") == "native") return false
        val api = user ?: return false
        return runCatching {
            val hwnd = hwndOf(window)
            topHwnd = hwnd
            subclass(api, hwnd) { prev -> object : WndProc {
                override fun callback(hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Long = handle(api, prev(), hwnd, msg, wParam, lParam)
            } }
            refreshChildren(window)
            // recalculate the client area now: the caption disappears
            api.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_FRAMECHANGED)
            maximized = api.IsZoomed(hwnd)
            enabled = true
            true
        }.getOrDefault(false)
    }

    /** Puts the proc made by [make] in front of the window's own one; `prev()` is the proc it replaced */
    private fun subclass(api: User32Ex, hwnd: WinDef.HWND, make: (() -> Pointer) -> WndProc) {
        val prev = arrayOfNulls<Pointer>(1)
        val proc = make { prev[0]!! }
        procs += proc
        prev[0] = api.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, proc)
        subclassed += Pointer.nativeValue(hwnd.pointer)
    }

    /**
     * Compose's canvas window decides the hit test first; it must not claim the caption area. Called again from
     * time to time because the canvas can be recreated.
     */
    fun refreshChildren(window: Window) {
        val api = user ?: return
        runCatching {
            val top = hwndOf(window)
            val found = ArrayList<WinDef.HWND>()
            api.EnumChildWindows(top, WinUser.WNDENUMPROC { h, _ ->
                val buf = CharArray(128)
                val n = api.GetClassNameW(h, buf, buf.size)
                if (n > 0 && String(buf, 0, n) == "SunAwtCanvas" &&
                    Pointer.nativeValue(h.pointer) !in subclassed &&
                    !com.lagradost.desktop.ui.screens.player.NativeVideo.isNativeCanvas(h)) found += h
                true
            }, null)
            for (child in found) {
                subclass(api, child) { prev -> object : WndProc {
                    override fun callback(hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Long = handleChild(api, prev(), top, hwnd, msg, wParam, lParam)
                } }
            }
        }
    }

    private fun frame(api: User32Ex, hwnd: WinDef.HWND): Pair<Int, Int> {
        val dpi = runCatching { api.GetDpiForWindow(hwnd) }.getOrDefault(96).takeIf { it > 0 } ?: 96
        val padded = api.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
        return (api.GetSystemMetricsForDpi(SM_CXSIZEFRAME, dpi) + padded) to (api.GetSystemMetricsForDpi(SM_CYSIZEFRAME, dpi) + padded)
    }

    /** What the screen point in [lParam] is over, for the frame window [top] */
    private fun hitTest(api: User32Ex, top: WinDef.HWND, lParam: Long): Int {
        val sx = (lParam and 0xFFFF).toShort().toInt()
        val sy = ((lParam shr 16) and 0xFFFF).toShort().toInt()
        // relative to the client area: the window rectangle also holds the invisible resize borders
        val origin = intArrayOf(0, 0)
        api.ClientToScreen(top, origin)
        val client = IntArray(4)
        api.GetClientRect(top, client)
        val dpi = runCatching { api.GetDpiForWindow(top) }.getOrDefault(96).takeIf { it > 0 } ?: 96
        return classify(sx - origin[0], sy - origin[1], client[2] - client[0], dpi / 96.0, api.IsZoomed(top))
    }

    private fun handleChild(api: User32Ex, prev: Pointer, top: WinDef.HWND, hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Long {
        if (msg == WM_NCHITTEST && (!fullscreen || revealed)) {
            try {
                // the frame window answers for the caption buttons, the drag band and the top resize edge
                if (hitTest(api, top, lParam) != HTCLIENT) return HTTRANSPARENT.toLong()
            } catch (_: Throwable) {
                // fall through to the default
            }
        }
        return api.CallWindowProcW(prev, hwnd, msg, wParam, lParam)
    }

    fun performCaptionCommand(id: Int) {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        val command = when (id) {
            1 -> SC_MINIMIZE
            2 -> if (fullscreen) {
                com.lagradost.desktop.runtime.AndroidRuntime.host.setFullscreen(false)
                return
            } else if (api.IsZoomed(hwnd)) SC_RESTORE else SC_MAXIMIZE
            else -> SC_CLOSE
        }
        api.PostMessageW(hwnd, WM_SYSCOMMAND, command.toLong(), 0L)
    }

    private fun handle(api: User32Ex, prev: Pointer, hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Long {
        try {
            when (msg) {
                WM_NCACTIVATE -> {
                    // When using custom non-client frame (WM_NCCALCSIZE / DWM), DefWindowProcW with lParam = -1
                    // tells Windows to update the activation state without repainting the non-client title bar.
                    // Bypassing AwtWindow::WmNcActivate avoids acquiring AwtToolkit::GetInstance().GetTreeLock()
                    // on the AWT-Windows toolkit thread, which deadlocks with EDT during owned dialog disposal.
                    return api.DefWindowProcW(hwnd, msg, wParam, -1L)
                }
                WM_NCCALCSIZE -> if (wParam != 0L) {
                    // full screen: the client area is the whole window
                    if (fullscreen) return 0L
                    // let Windows compute the normal frame, then take the caption back for the client area
                    val params = Pointer(lParam)
                    val originalTop = params.getInt(4)
                    val result = api.CallWindowProcW(prev, hwnd, msg, wParam, lParam)
                    val zoomed = api.IsZoomed(hwnd)
                    params.setInt(4, if (zoomed) originalTop + frame(api, hwnd).second else originalTop)
                    return result
                }
                WM_SIZE -> {
                    maximized = api.IsZoomed(hwnd)
                }
                // the small window keeps the video's shape while an edge or a corner is dragged
                WM_SIZING -> if (pip && pipAspect > 0f) {
                    keepPipAspect(api, hwnd, wParam.toInt(), Pointer(lParam))
                    return 1L
                }
                WM_NCHITTEST -> {
                    val result = api.CallWindowProcW(prev, hwnd, msg, wParam, lParam)
                    // the small window is resized by the grips of its picture, which keep its shape and let the video follow: the system's
                    // invisible border around it would resize it without the picture following
                    if (pip && result.toInt() in HTLEFT..HTBOTTOMRIGHT) return HTCLIENT.toLong()
                    if (result.toInt() != HTCLIENT || (fullscreen && !revealed)) return result
                    return hitTest(api, hwnd, lParam).toLong()
                }
                WM_NCMOUSEMOVE -> {
                    hover = when ((wParam and 0xFFFF).toInt()) {
                        HTMINBUTTON -> 1
                        HTMAXBUTTON -> 2
                        HTCLOSE -> 3
                        else -> 0
                    }
                    // ask for WM_NCMOUSELEAVE so the highlight goes away when the pointer leaves the window
                    if (hover != 0) {
                        val event = Memory(24)
                        event.setInt(0, 24)
                        event.setInt(4, TME_LEAVE or TME_NONCLIENT)
                        event.setPointer(8, hwnd.pointer)
                        event.setInt(16, 0)
                        api.TrackMouseEvent(event)
                    }
                }
                WM_NCMOUSELEAVE -> { hover = 0; pressed = 0 }
                // the caption buttons are acted on here: the default handling does not know about them
                // the strip of the small window drags it; a double click must not maximize it
                WM_NCLBUTTONDBLCLK -> if (pip) return 0L
                WM_NCLBUTTONDOWN -> if (captionId((wParam and 0xFFFF).toInt()) != 0) {
                    pressed = captionId((wParam and 0xFFFF).toInt())
                    return 0L
                }
                WM_NCLBUTTONUP -> {
                    val id = captionId((wParam and 0xFFFF).toInt())
                    val was = pressed
                    pressed = 0
                    if (id != 0) {
                        if (id == was) {
                            performCaptionCommand(id)
                        }
                        return 0L
                    }
                }
            }
        } catch (_: Throwable) {
            // never break the window procedure
        }
        return api.CallWindowProcW(prev, hwnd, msg, wParam, lParam)
    }

    // ---------------------------------------------------------------------------------------------
    private fun captionId(hit: Int): Int = when (hit) {
        HTMINBUTTON -> 1
        HTMAXBUTTON -> 2
        HTCLOSE -> 3
        else -> 0
    }

    // ---------------------------------------------------------------------------------------------
    // full screen

    private var savedRect: IntArray? = null
    private var savedZoomed = false
    private var savedStyle = 0L

    /**
     * Full screen as a borderless window over the monitor the window is on, above the taskbar; leaving it puts
     * the window back (maximized again if it was). Returns false when the integrated window is not installed
     * (the caller then uses the AWT full screen).
     */
    fun setFullscreen(window: Window, on: Boolean): Boolean {
        val api = user ?: return false
        val hwnd = topHwnd ?: return false
        if (!enabled) return false
        if (on == fullscreen) return true
        if (on && pip) setPip(window, false, 1.78f)
        return runCatching {
            if (on) {
                savedZoomed = api.IsZoomed(hwnd)
                if (savedZoomed) api.ShowWindow(hwnd, SW_RESTORE)
                val rect = IntArray(4)
                api.GetWindowRect(hwnd, rect)
                savedRect = rect
                val monitor = api.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return false
                val info = Memory(40)
                info.setInt(0, 40)
                if (!api.GetMonitorInfoW(monitor, info)) return false
                val left = info.getInt(4)
                val top = info.getInt(8)
                val width = info.getInt(12) - left
                val height = info.getInt(16) - top
                // no frame at all: the system border and the shadow would show as a line around the picture
                savedStyle = api.GetWindowLongPtrW(hwnd, GWL_STYLE)
                api.SetWindowLongPtrW(hwnd, GWL_STYLE, savedStyle and (WS_CAPTION or WS_THICKFRAME).inv())
                fullscreen = true
                hover = 0
                revealed = false
                api.SetWindowPos(hwnd, Pointer(-1), left, top, width, height, SWP_FRAMECHANGED or SWP_SHOWWINDOW)
            } else {
                fullscreen = false
                revealed = false
                if (savedStyle != 0L) api.SetWindowLongPtrW(hwnd, GWL_STYLE, savedStyle)
                val r = savedRect
                if (r != null) api.SetWindowPos(hwnd, Pointer(-2), r[0], r[1], r[2] - r[0], r[3] - r[1], SWP_FRAMECHANGED or SWP_SHOWWINDOW)
                else api.SetWindowPos(hwnd, Pointer(-2), 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_FRAMECHANGED)
                if (savedZoomed) api.ShowWindow(hwnd, SW_MAXIMIZE)
                maximized = api.IsZoomed(hwnd)
            }
            true
        }.getOrDefault(false)
    }

    private var pipSavedRect: IntArray? = null
    private var pipSavedZoomed = false
    private var pipSavedMinimum: java.awt.Dimension? = null

    /** Where the small window was when it was left (window rectangle), so that it comes back there */
    private var pipRect: IntArray? = null

    /** The picture's width in pixels the user made the small window last time */
    private var pipClientWidth = 0

    /** Width / height of the video in the small window; its window keeps this shape (0: not in picture in picture) */
    @Volatile
    private var pipAspect = 0f

    /** The bar of the small window that shows on hover (title, back to the app, close) */
    const val PIP_STRIP_DP = 30
    private const val PIP_WIDTH_DP = 440
    private const val PIP_MIN_WIDTH_DP = 240

    /** The edge of the screen's work area a dragged small window sticks to within this distance */
    private const val PIP_SNAP_DP = 16

    /**
     * Picture in picture: the window becomes a small borderless one in the corner of the screen's work area, above
     * every other window; leaving restores the size, position and maximized state. [aspect] is width / height of the video.
     * The picture fills the whole window; it is moved by dragging the picture ([pipDragBegin]) and resized by its edges and corners (the
     * window keeps the video's shape, see [keepPipAspect]).
     */
    fun setPip(window: Window, on: Boolean, aspect: Float): Boolean {
        val api = user ?: return false
        val hwnd = topHwnd ?: return false
        if (!enabled) return false
        if (on == pip) return true
        return runCatching {
            if (on) {
                if (fullscreen) setFullscreen(window, false)
                pipSavedZoomed = api.IsZoomed(hwnd)
                if (pipSavedZoomed) api.ShowWindow(hwnd, SW_RESTORE)
                val rect = IntArray(4)
                api.GetWindowRect(hwnd, rect)
                pipSavedRect = rect
                val monitor = api.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return false
                val info = Memory(40)
                info.setInt(0, 40)
                if (!api.GetMonitorInfoW(monitor, info)) return false
                val scale = (runCatching { api.GetDpiForWindow(hwnd) }.getOrDefault(96).takeIf { it > 0 } ?: 96) / 96.0
                // the window holds invisible resize borders at its left, right and bottom: the picture is what is left inside
                val (fx, fy) = frame(api, hwnd)
                pipAspect = aspect.coerceIn(1f, 2.6f)
                val workWidth = info.getInt(28) - info.getInt(20)
                val pictureWidth = (pipClientWidth.takeIf { it > 0 } ?: (PIP_WIDTH_DP * scale).toInt()).coerceIn((PIP_MIN_WIDTH_DP * scale).toInt(), (workWidth * 0.8).toInt())
                val width = pictureWidth + 2 * fx
                val height = (pictureWidth / pipAspect).toInt() + fy
                val margin = (24 * scale).toInt()
                // where it was last time, when that is still on this monitor
                val last = pipRect
                val x = last?.get(0)?.takeIf { it >= info.getInt(20) && it + width <= info.getInt(28) } ?: (info.getInt(28) - width - margin + fx)
                val y = last?.get(1)?.takeIf { it >= info.getInt(24) && it + height <= info.getInt(32) } ?: (info.getInt(32) - height - margin + fy)
                pipSavedMinimum = window.minimumSize
                window.minimumSize = java.awt.Dimension(0, 0)
                pip = true
                hover = 0
                api.SetWindowPos(hwnd, Pointer(-1), x, y, width, height, SWP_FRAMECHANGED or SWP_SHOWWINDOW)
            } else {
                pip = false
                pipAspect = 0f
                pipDragging = false
                val now = IntArray(4)
                if (api.GetWindowRect(hwnd, now)) {
                    pipRect = now
                    pipClientWidth = now[2] - now[0] - 2 * frame(api, hwnd).first
                }
                pipSavedMinimum?.let { window.minimumSize = it }
                val r = pipSavedRect
                if (r != null) api.SetWindowPos(hwnd, Pointer(-2), r[0], r[1], r[2] - r[0], r[3] - r[1], SWP_FRAMECHANGED or SWP_SHOWWINDOW)
                else api.SetWindowPos(hwnd, Pointer(-2), 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_FRAMECHANGED)
                if (pipSavedZoomed) api.ShowWindow(hwnd, SW_MAXIMIZE)
                maximized = api.IsZoomed(hwnd)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * WM_SIZING of the small window (an edge of the system's resize border was dragged): the rectangle is corrected like [fitPip] does.
     * The small window has no such border any more (see the hit test), this stays as the safety net.
     */
    private fun keepPipAspect(api: User32Ex, hwnd: WinDef.HWND, edge: Int, rect: Pointer) {
        val r = intArrayOf(rect.getInt(0), rect.getInt(4), rect.getInt(8), rect.getInt(12))
        fitPip(api, hwnd, edge, r)
        rect.setInt(0, r[0])
        rect.setInt(4, r[1])
        rect.setInt(8, r[2])
        rect.setInt(12, r[3])
    }

    /**
     * Corrects the window rectangle [r] (left, top, right, bottom) so that the picture inside it (the window minus its invisible borders at the left,
     * right and bottom) keeps the shape of the video and is not smaller than the minimum; the edge or corner that is not being dragged stays
     * where it is. [edge] numbers as WMSZ_*: left 1, right 2, top 3, topleft 4, topright 5, bottom 6, bottomleft 7, bottomright 8.
     */
    private fun fitPip(api: User32Ex, hwnd: WinDef.HWND, edge: Int, r: IntArray) {
        var left = r[0]
        var top = r[1]
        var right = r[2]
        var bottom = r[3]
        val (fx, fy) = frame(api, hwnd)
        val scale = (runCatching { api.GetDpiForWindow(hwnd) }.getOrDefault(96).takeIf { it > 0 } ?: 96) / 96.0
        var pictureWidth = right - left - 2 * fx
        var pictureHeight = bottom - top - fy
        // the top and bottom edges change the height: the width follows; every other edge or corner: the height follows the width
        if (edge == 3 || edge == 6) pictureWidth = (pictureHeight * pipAspect).toInt() else pictureHeight = (pictureWidth / pipAspect).toInt()
        val minimum = (PIP_MIN_WIDTH_DP * scale).toInt()
        if (pictureWidth < minimum) { pictureWidth = minimum; pictureHeight = (pictureWidth / pipAspect).toInt() }
        val width = pictureWidth + 2 * fx
        val height = pictureHeight + fy
        if (edge == 1 || edge == 4 || edge == 7) left = right - width else right = left + width
        if (edge == 3 || edge == 4 || edge == 5) top = bottom - height else bottom = top + height
        r[0] = left
        r[1] = top
        r[2] = right
        r[3] = bottom
    }

    // resizing the small window by dragging an edge or a corner of the picture (grips drawn by the player page), read like the move below
    private var resizeEdge = 0
    private val resizeCursor = IntArray(2)
    private val resizeWindow = IntArray(4)

    fun pipResizeBegin(edge: Int) {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        if (!pip) return
        if (pipDragging) {
            // the pointer was pressed a few pixels ago (a drag only starts after a little movement): measure from there, the window follows at once
            dragCursor.copyInto(resizeCursor)
            dragWindow.copyInto(resizeWindow)
            resizeEdge = edge
            return
        }
        cursorPos(api, resizeCursor)
        resizeEdge = if (api.GetWindowRect(hwnd, resizeWindow)) edge else 0
    }

    fun pipResizeMove() {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        if (!pip || resizeEdge == 0) return
        val cursor = IntArray(2)
        if (!cursorPos(api, cursor)) return
        val dx = cursor[0] - resizeCursor[0]
        val dy = cursor[1] - resizeCursor[1]
        val r = resizeWindow.clone()
        val edge = resizeEdge
        if (edge == 1 || edge == 4 || edge == 7) r[0] += dx
        if (edge == 2 || edge == 5 || edge == 8) r[2] += dx
        if (edge == 3 || edge == 4 || edge == 5) r[1] += dy
        if (edge == 6 || edge == 7 || edge == 8) r[3] += dy
        fitPip(api, hwnd, edge, r)
        // not larger than the screen's work area
        val monitor = api.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)
        if (monitor != null) {
            val info = Memory(40)
            info.setInt(0, 40)
            if (api.GetMonitorInfoW(monitor, info) && r[2] - r[0] > (info.getInt(28) - info.getInt(20)) * 0.9) return
        }
        api.SetWindowPos(hwnd, null, r[0], r[1], r[2] - r[0], r[3] - r[1], SWP_NOZORDER or SWP_NOACTIVATE)
        pipMoved = true
    }

    fun pipResizeEnd() {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        if (resizeEdge == 0) return
        resizeEdge = 0
        val now = IntArray(4)
        if (api.GetWindowRect(hwnd, now)) {
            pipRect = now
            pipClientWidth = now[2] - now[0] - 2 * frame(api, hwnd).first
        }
    }

    // moving the small window by dragging the picture. The pointer's own position is read from Windows (the window moves under it, so the
    // position an event carries does not change), which also makes it work from the controls window of the native video player.
    private val dragCursor = IntArray(2)

    /** Dev: a pretend pointer position (screen px) used instead of the real one while dragging the small window */
    @Volatile
    var debugCursor: IntArray? = null

    private fun cursorPos(api: User32Ex, out: IntArray): Boolean {
        debugCursor?.let { out[0] = it[0]; out[1] = it[1]; return true }
        return api.GetCursorPos(out)
    }
    private val dragWindow = IntArray(4)
    private var pipDragging = false
    private var dragMoves = 0

    /** The pointer took the small window somewhere since it was pressed: that release is the end of a drag, not a click */
    @Volatile
    var pipMoved = false

    fun pipDragBegin() {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        if (!pip) return
        cursorPos(api, dragCursor)
        pipDragging = api.GetWindowRect(hwnd, dragWindow)
        dragMoves = 0
        pipMoved = false
        android.util.Log.i("WinChrome", "picture in picture drag begins: pointer ${dragCursor.toList()}, window ${dragWindow.toList()}, ok $pipDragging")
    }

    /** Puts the small window where the pointer has taken it: it stays on a screen and sticks to the edges of the work area it is near */
    fun pipDragMove() {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        if (!pip || !pipDragging) return
        val cursor = IntArray(2)
        if (!cursorPos(api, cursor)) return
        val width = dragWindow[2] - dragWindow[0]
        val height = dragWindow[3] - dragWindow[1]
        var x = dragWindow[0] + cursor[0] - dragCursor[0]
        var y = dragWindow[1] + cursor[1] - dragCursor[1]
        // the work area (not under the taskbar) of the screen the pointer is on
        val monitor = api.MonitorFromPoint((cursor[0].toLong() and 0xFFFFFFFFL) or (cursor[1].toLong() shl 32), MONITOR_DEFAULTTONEAREST)
        if (monitor != null) {
            val info = Memory(40)
            info.setInt(0, 40)
            if (api.GetMonitorInfoW(monitor, info)) {
                val scale = (runCatching { api.GetDpiForWindow(hwnd) }.getOrDefault(96).takeIf { it > 0 } ?: 96) / 96.0
                val snap = (PIP_SNAP_DP * scale).toInt()
                val (fx, fy) = frame(api, hwnd)
                val left = info.getInt(20) - fx
                val top = info.getInt(24)
                val right = info.getInt(28) + fx
                val bottom = info.getInt(32) + fy
                if (Math.abs(x - left) < snap) x = left
                if (Math.abs(x + width - right) < snap) x = right - width
                if (Math.abs(y - top) < snap) y = top
                if (Math.abs(y + height - bottom) < snap) y = bottom - height
                // the whole picture stays on the screen the pointer is on
                x = x.coerceIn(left, maxOf(left, right - width))
                y = y.coerceIn(top, maxOf(top, bottom - height))
            }
        }
        val moved = api.SetWindowPos(hwnd, null, x, y, 0, 0, SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE)
        if (x != dragWindow[0] || y != dragWindow[1]) pipMoved = true
        if (dragMoves++ < 3) android.util.Log.i("WinChrome", "picture in picture drag: window to $x,$y ok $moved")
    }

    fun pipDragEnd() {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        if (!pipDragging) return
        pipDragging = false
        val now = IntArray(4)
        if (api.GetWindowRect(hwnd, now)) pipRect = now
    }

    /** What the pointer is over (window pixels): top resize edge, caption buttons, the drag band or the app */
    fun classify(x: Int, y: Int, width: Int, scale: Double, zoomed: Boolean): Int {
        // resize from the top edge (the system only knows the other edges now)
        val edge = (6 * scale).toInt()
        if (!zoomed && !pip && y < edge) {
            val corner = (12 * scale).toInt()
            return when {
                x < corner -> HTTOPLEFT
                x > width - corner -> HTTOPRIGHT
                else -> HTTOP
            }
        }
        // caption buttons
        val bw = (BUTTON_WIDTH_DP * scale).toInt()
        val bh = (BUTTON_HEIGHT_DP * scale).toInt()
        if (y < bh && !pip && !captionHidden) {
            val fromRight = width - x
            when {
                fromRight in 0 until bw -> return HTCLOSE
                fromRight in bw until bw * 2 -> return HTMAXBUTTON
                fromRight in bw * 2 until bw * 3 -> return HTMINBUTTON
            }
        }
        // draggable band: everything in it that is not a control. The small window has none: it is dragged by its picture (pipDragBegin), which
        // works over the video of the native player too, where the frame window never sees the pointer
        val band = if (pip) 0 else (DRAG_HEIGHT_DP * scale).toInt()
        if (y < band) {
            val inControl = noDrag.values.any { x >= it[0] && x < it[2] && y >= it[1] && y < it[3] }
            if (!inControl) return HTCAPTION
        }
        return HTCLIENT
    }

    /** The size of the window's client area in pixels, which is what the Compose scene should measure; null when unknown or while the window is minimized */
    fun clientSize(): IntArray? {
        val api = user ?: return null
        val hwnd = topHwnd ?: return null
        if (api.IsIconic(hwnd)) return null
        val r = IntArray(4)
        if (!api.GetClientRect(hwnd, r)) return null
        return intArrayOf(r[2] - r[0], r[3] - r[1]).takeIf { it[0] > 0 && it[1] > 0 }
    }

    /** Sends the window its frame and size messages again (WM_NCCALCSIZE, WM_SIZE), which is what a canvas that did not follow a resize needs */
    fun resendFrame() {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        api.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_FRAMECHANGED or 0x10)
    }

    /** Dev: maximize or restore the window */
    fun toggleMaximized(on: Boolean) {
        val api = user ?: return
        val hwnd = topHwnd ?: return
        api.ShowWindow(hwnd, if (on) 3 else 9)
        maximized = api.IsZoomed(hwnd)
    }

    fun isInstalled(): Boolean = enabled
}
