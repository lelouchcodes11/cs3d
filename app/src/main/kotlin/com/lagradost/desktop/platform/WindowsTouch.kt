package com.lagradost.desktop.platform

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Windows marks mouse messages promoted from fingers. Never classify a real mouse or pen as touch. */
internal fun isPromotedTouch(extraInfo: Long): Boolean =
    extraInfo and 0xFFFFFF80L == 0xFF515780L

/** Observe, but do not consume or synthesize, native input. Windows still owns tap-to-click. */
object WindowsTouch {
    @Volatile var lastPrimaryWasTouch = false
        private set

    /**
     * A finger held still on the window for [HOLD_MS]: `true` with the place on the screen (pixels) when it has been held that long, `false` when it
     * lifts (or a second finger arrives, or it moves). Called on the UI thread. Windows does not hand a still finger to an app as a mouse press: a
     * short touch becomes a click when it lifts, a long one a right click, so a long press cannot be seen in the mouse events; the touch messages
     * (WM_POINTER*) that arrive first can.
     */
    @Volatile var holdListener: ((started: Boolean, x: Int, y: Int) -> Unit)? = null

    const val HOLD_MS = 350L
    private const val HOLD_SLOP_PX = 24

    private interface User32 : StdCallLibrary {
        fun GetMessageExtraInfo(): Long
        fun GetWindowLongPtrW(hwnd: HWND, index: Int): Pointer?
        fun SetWindowLongPtrW(hwnd: HWND, index: Int, callback: Callback): Pointer?
        fun CallWindowProcW(previous: Pointer, hwnd: HWND, message: Int, wp: Long, lp: Long): Long
        fun EnumChildWindows(hwnd: HWND, callback: WinUser.WNDENUMPROC, data: Pointer?): Boolean
        fun GetClassNameW(hwnd: HWND, buffer: CharArray, count: Int): Int
        fun GetPointerType(pointerId: Int, pointerType: IntByReference): Boolean
    }
    private interface Proc : Callback {
        fun callback(hwnd: HWND, message: Int, wp: Long, lp: Long): Long
    }
    private val api by lazy {
        if (!System.getProperty("os.name", "").startsWith("Windows")) null
        else runCatching { Native.load("user32", User32::class.java) }.getOrNull()
    }
    // Strong references are required while Windows can call back. Remove them on WM_NCDESTROY.
    private val installed = java.util.concurrent.ConcurrentHashMap<Long, Proc>()

    private val timer by lazy { Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "touch-hold").also { it.isDaemon = true } } }
    private val lock = Any()
    private var fingerId = -1
    private var fingerX = 0
    private var fingerY = 0
    private var pending: ScheduledFuture<*>? = null
    private var holding = false

    private fun fingerDown(id: Int, x: Int, y: Int) {
        synchronized(lock) {
            // a second finger before the hold has started (a pinch, a two-finger swipe) is not a hold; once it has started, other pointers that Windows makes
            // while the finger stays (the right click it makes of a long press) do not end it: only the finger itself lifting does
            if (fingerId != -1 && fingerId != id) { if (!holding) endHold(); return }
            fingerId = id; fingerX = x; fingerY = y; holding = false
            pending?.cancel(false)
            pending = timer.schedule({
                val fire = synchronized(lock) { if (fingerId == id && !holding) { holding = true; true } else false }
                if (fire) java.awt.EventQueue.invokeLater { holdListener?.invoke(true, x, y) }
            }, HOLD_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun fingerMoved(id: Int, x: Int, y: Int) {
        synchronized(lock) {
            if (id != fingerId || holding) return
            if (Math.hypot((x - fingerX).toDouble(), (y - fingerY).toDouble()) > HOLD_SLOP_PX) { pending?.cancel(false); pending = null; fingerId = -1 }
        }
    }

    private fun fingerUp(id: Int) {
        synchronized(lock) { if (id == fingerId) endHold() }
    }

    /** Must be called with [lock] held */
    private fun endHold() {
        pending?.cancel(false); pending = null
        val wasHolding = holding
        holding = false
        fingerId = -1
        if (wasHolding) java.awt.EventQueue.invokeLater { holdListener?.invoke(false, 0, 0) }
    }

    fun install(window: Window): Boolean {
        val user = api ?: return false
        if (!window.isDisplayable) return false
        return runCatching {
            val top = HWND(Pointer(Native.getComponentID(window)))
            observe(user, top)
            user.EnumChildWindows(top, WinUser.WNDENUMPROC { child, _ ->
                val buffer = CharArray(128)
                val n = user.GetClassNameW(child, buffer, buffer.size)
                if (n > 0 && String(buffer, 0, n) == "SunAwtCanvas") observe(user, child)
                true
            }, null)
            installed.containsKey(Pointer.nativeValue(top.pointer))
        }.onFailure { android.util.Log.w("WindowsTouch", "Touch source detection unavailable; use Touchscreen swipes: On if needed") }.getOrDefault(false)
    }

    private fun observe(user: User32, hwnd: HWND) {
        val id = Pointer.nativeValue(hwnd.pointer)
        if (installed.containsKey(id)) return
        var previous = user.GetWindowLongPtrW(hwnd, -4) ?: return
        val callback = object : Proc {
            override fun callback(hwnd: HWND, message: Int, wp: Long, lp: Long): Long {
                val before = previous
                try {
                    when (message) {
                        0x0201 -> lastPrimaryWasTouch = isPromotedTouch(user.GetMessageExtraInfo()) // WM_LBUTTONDOWN, queried on the native message thread
                        0x0246, 0x0245 -> if (holdListener != null) touchPointer(user, message, wp, lp) // WM_POINTERDOWN, WM_POINTERUPDATE
                        0x0247, 0x024C -> if (holdListener != null || holding) fingerUp((wp and 0xFFFF).toInt()) // WM_POINTERUP, WM_POINTERCAPTURECHANGED
                    }
                } catch (_: Throwable) {
                    lastPrimaryWasTouch = false
                }
                return try {
                    user.CallWindowProcW(before, hwnd, message, wp, lp)
                } finally {
                    if (message == 0x0082) installed.remove(id) // WM_NCDESTROY
                }
            }
        }
        installed[id] = callback
        val replaced = user.SetWindowLongPtrW(hwnd, -4, callback)
        if (replaced == null || Pointer.nativeValue(replaced) == 0L) installed.remove(id)
        else previous = replaced
    }

    /** A pointer message: only a finger (not the mouse or a pen) is looked at */
    private fun touchPointer(user: User32, message: Int, wp: Long, lp: Long) {
        val pointer = (wp and 0xFFFF).toInt()
        val type = IntByReference()
        if (!user.GetPointerType(pointer, type) || type.value != 2) return // PT_TOUCH
        val x = (lp and 0xFFFF).toShort().toInt()
        val y = ((lp shr 16) and 0xFFFF).toShort().toInt()
        if (message == 0x0246) fingerDown(pointer, x, y) else fingerMoved(pointer, x, y)
    }
}
