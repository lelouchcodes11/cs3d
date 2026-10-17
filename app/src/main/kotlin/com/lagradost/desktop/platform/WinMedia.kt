package com.lagradost.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference

/**
 * Windows behaviour of a video player: the display and system stay awake while something plays, and the
 * keyboard media keys (play/pause, next, previous, stop) control the player.
 *
 * The media keys follow the focus: while a window of this app is in front they are the player's and nobody else sees them (a browser next to
 * the player kept pausing its own video); while another program is in front they still reach the player as global hot keys, but only while a
 * player page is open, so that music in another program gets its keys back when the player is left.
 */
object WinMedia {
    private interface Kernel32Power : Library {
        fun SetThreadExecutionState(flags: Int): Int
    }

    private val kernel by lazy { runCatching { Native.load("kernel32", Kernel32Power::class.java) }.getOrNull() }

    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_SYSTEM_REQUIRED = 0x00000001
    private const val ES_DISPLAY_REQUIRED = 0x00000002

    // SetThreadExecutionState(ES_CONTINUOUS) belongs to the calling thread and ends with it: one long-lived thread owns it
    private val awakeThread = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "keep-awake").apply { isDaemon = true } }

    /** Keeps the screen on and the PC awake */
    fun keepAwake(on: Boolean) {
        val flags = if (on) ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED else ES_CONTINUOUS
        awakeThread.execute { runCatching { kernel?.SetThreadExecutionState(flags) } }
    }

    enum class Key { PlayPause, Next, Previous, Stop }

    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3
    private const val WM_KEYDOWN = 0x0100
    private const val WM_SYSKEYDOWN = 0x0104
    private const val WM_APP_SYNC = 0x8000 + 1

    private val vkToKey = mapOf(
        VK_MEDIA_PLAY_PAUSE to Key.PlayPause,
        VK_MEDIA_NEXT_TRACK to Key.Next,
        VK_MEDIA_PREV_TRACK to Key.Previous,
        VK_MEDIA_STOP to Key.Stop,
    )

    /** Set while a player page is open; the keys are taken (hot keys, hook) only then */
    @Volatile
    var handler: ((Key) -> Unit)? = null
        set(value) {
            field = value
            // the hot keys belong to the thread that made them: it is asked to make or drop them
            val id = threadId
            if (id != 0) runCatching { User32.INSTANCE.PostThreadMessage(id, WM_APP_SYNC, WinDef.WPARAM(0), WinDef.LPARAM(0)) }
        }

    @Volatile
    private var started = false

    @Volatile
    private var threadId = 0

    // kept for as long as the hook exists: the callback object must not be collected
    private var hookProc: WinUser.LowLevelKeyboardProc? = null
    private var hook: WinUser.HHOOK? = null

    /** The window in front belongs to this process (the player's controls window and the main window are both ours) */
    private fun foregroundIsOurs(user: User32): Boolean {
        val front = user.GetForegroundWindow() ?: return false
        val pid = IntByReference()
        user.GetWindowThreadProcessId(front, pid)
        return pid.value == Kernel32.INSTANCE.GetCurrentProcessId()
    }

    private fun deliver(key: Key) {
        runCatching { java.awt.EventQueue.invokeLater { handler?.invoke(key) } }
    }

    /** Starts the message loop thread that owns the hot keys and the keyboard hook (they are made while a [handler] is set) */
    fun startMediaKeys() {
        if (started) return
        started = true
        val thread = Thread({
            val user = User32.INSTANCE
            val msg = WinUser.MSG()
            // the thread's message queue exists after the first look at it; only then can it be sent messages
            user.PeekMessage(msg, null, 0, 0, 0)
            val ids = mapOf(1 to Key.PlayPause, 2 to Key.Next, 3 to Key.Previous, 4 to Key.Stop)
            val vks = mapOf(1 to VK_MEDIA_PLAY_PAUSE, 2 to VK_MEDIA_NEXT_TRACK, 3 to VK_MEDIA_PREV_TRACK, 4 to VK_MEDIA_STOP)
            var taken = false
            fun sync() {
                val want = handler != null
                if (want && !taken) {
                    taken = true
                    // a key that cannot be had (another program holds it) is harmless: the hook below still serves the front window
                    for ((id, vk) in vks) user.RegisterHotKey(null, id, 0, vk)
                    runCatching {
                        val proc = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
                            val key = if (nCode >= 0) vkToKey[info.vkCode] else null
                            if (key != null && handler != null && foregroundIsOurs(user)) {
                                val down = wParam.toInt() == WM_KEYDOWN || wParam.toInt() == WM_SYSKEYDOWN
                                if (down) deliver(key)
                                // seen by nobody else: key down and key up
                                WinDef.LRESULT(1)
                            } else {
                                user.CallNextHookEx(hook, nCode, wParam, WinDef.LPARAM(com.sun.jna.Pointer.nativeValue(info.pointer)))
                            }
                        }
                        hookProc = proc
                        hook = user.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, proc, Kernel32.INSTANCE.GetModuleHandle(null), 0)
                    }
                } else if (!want && taken) {
                    taken = false
                    for (id in vks.keys) user.UnregisterHotKey(null, id)
                    runCatching { hook?.let { user.UnhookWindowsHookEx(it) } }
                    hook = null
                    hookProc = null
                }
            }
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            sync()
            while (user.GetMessage(msg, null, 0, 0) > 0) {
                when (msg.message) {
                    WinUser.WM_HOTKEY -> ids[msg.wParam.toInt()]?.let { key -> if (handler != null) deliver(key) }
                    WM_APP_SYNC -> sync()
                }
            }
        }, "media-keys")
        thread.isDaemon = true
        thread.start()
    }
}
