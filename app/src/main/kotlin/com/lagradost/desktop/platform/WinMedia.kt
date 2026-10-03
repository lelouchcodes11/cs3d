package com.lagradost.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser

/**
 * Windows behaviour of a video player: the display and system stay awake while something plays, and the
 * keyboard media keys (play/pause, next, previous, stop) control the player even when another window
 * has the focus.
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

    @Volatile
    var handler: ((Key) -> Unit)? = null

    @Volatile
    private var started = false

    /** Registers the media keys as global hotkeys on a message loop thread; harmless if already taken */
    fun startMediaKeys() {
        if (started) return
        started = true
        val thread = Thread({
            val user = User32.INSTANCE
            val ids = mapOf(1 to Key.PlayPause, 2 to Key.Next, 3 to Key.Previous, 4 to Key.Stop)
            val vks = mapOf(1 to VK_MEDIA_PLAY_PAUSE, 2 to VK_MEDIA_NEXT_TRACK, 3 to VK_MEDIA_PREV_TRACK, 4 to VK_MEDIA_STOP)
            for ((id, vk) in vks) user.RegisterHotKey(null, id, 0, vk)
            val msg = WinUser.MSG()
            while (user.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WinUser.WM_HOTKEY) {
                    ids[msg.wParam.toInt()]?.let { key -> runCatching { java.awt.EventQueue.invokeLater { handler?.invoke(key) } } }
                }
            }
        }, "media-keys")
        thread.isDaemon = true
        thread.start()
    }
}
