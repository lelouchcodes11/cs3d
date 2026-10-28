package com.lagradost.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * What Windows itself says about the windows of this app and the pointer, independent of what Compose was told (see ControlsGuard): where the
 * pointer is, which window is under it, whether a window is visible. Cheap calls; every function answers null / 0 / false when Windows is not
 * there or a call fails, so the safety net never breaks the thing it guards.
 */
object WinProbe {
    private const val GA_ROOT = 2
    private const val SWP_NOSIZE = 0x1
    private const val SWP_NOMOVE = 0x2
    private const val SWP_NOACTIVATE = 0x10
    private const val SWP_SHOWWINDOW = 0x40

    private val user: User32? by lazy { runCatching { User32.INSTANCE }.getOrNull() }

    /** WindowFromPoint takes its POINT by value: x in the low 32 bits, y in the high ones (as WinChrome passes it to MonitorFromPoint) */
    private interface PointApi : com.sun.jna.win32.StdCallLibrary {
        fun WindowFromPoint(point: Long): Pointer?
    }

    private val pointApi: PointApi? by lazy { runCatching { Native.load("user32", PointApi::class.java) }.getOrNull() }

    /** The pointer, in screen pixels */
    fun cursor(): IntArray? = runCatching {
        val p = WinDef.POINT()
        if (user?.GetCursorPos(p) == true) intArrayOf(p.x, p.y) else null
    }.getOrNull()

    /** The top-level window under the screen point (0: none) */
    fun rootAt(x: Int, y: Int): Long = runCatching {
        val api = user ?: return 0L
        val hit = pointApi?.WindowFromPoint((y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)) ?: return 0L
        val root = api.GetAncestor(WinDef.HWND(hit), GA_ROOT) ?: return Pointer.nativeValue(hit)
        Pointer.nativeValue(root.pointer)
    }.getOrDefault(0L)

    private val handles = java.util.WeakHashMap<Window, Long>()

    /** The native window of an AWT window (0: it has no peer yet). Asking AWT for it takes the surface lock, so the answer is kept while the window is the same one. */
    fun handleOf(window: Window?): Long = runCatching {
        if (window == null || !window.isDisplayable) return 0L
        val known = synchronized(handles) { handles[window] }
        if (known != null && known != 0L && user?.IsWindow(hwnd(known)) == true) return known
        val h = Native.getComponentID(window)
        synchronized(handles) { handles[window] = h }
        h
    }.getOrDefault(0L)

    private fun hwnd(h: Long): WinDef.HWND = WinDef.HWND(Pointer.createConstant(h))

    fun isVisible(h: Long): Boolean = h != 0L && runCatching { user?.IsWindowVisible(hwnd(h)) == true }.getOrDefault(false)

    /** left, top, right, bottom in screen pixels */
    fun rect(h: Long): IntArray? = runCatching {
        if (h == 0L) return null
        val r = WinDef.RECT()
        if (user?.GetWindowRect(hwnd(h), r) == true) intArrayOf(r.left, r.top, r.right, r.bottom) else null
    }.getOrNull()

    /** Test only (-Dcloudstream.overlaytest=blind): the window lets every pointer event through to what is below it, as a broken controls window would */
    fun makeClickThrough(h: Long): Boolean = runCatching {
        val api = user ?: return false
        val style = api.GetWindowLong(hwnd(h), -20)
        api.SetWindowLong(hwnd(h), -20, style or 0x20 or 0x80000)
        true
    }.getOrDefault(false)

    /** The window to the top of the windows that are not "always on top", without taking the keyboard focus and without moving it */
    fun raise(h: Long): Boolean = runCatching {
        h != 0L && user?.SetWindowPos(hwnd(h), WinDef.HWND(Pointer.createConstant(0)), 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE or SWP_SHOWWINDOW) == true
    }.getOrDefault(false)
}
