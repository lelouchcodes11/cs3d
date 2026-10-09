package com.lagradost.desktop.platform

import android.app.Notification
import android.util.Log
import com.lagradost.desktop.runtime.Notifications
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import kotlin.concurrent.thread

/**
 * The tray icon (click = open the window, right click = Open / Exit) and the desktop notifications.
 *
 * Notifications are always silent: they are sent with `NIIF_NOSOUND`, so Windows never plays its notification sound for them
 * (the java.awt tray icon cannot set that flag, which is why this talks to the shell itself).
 * - Ongoing/progress notifications (downloads) are shown in the app's own UI only.
 * - Alerts (download complete/failed, updates, ...) are shown as a balloon/toast; clicking one runs its contentIntent.
 */
object DesktopTrayNotifications {
    private const val TAG = "DesktopTray"

    private const val WM_TRAY = 0x8001 // WM_APP + 1
    private const val WM_LBUTTONUP = 0x0202
    private const val WM_LBUTTONDBLCLK = 0x0203
    private const val WM_RBUTTONUP = 0x0205
    private const val WM_CONTEXTMENU = 0x007B
    private const val WM_NULL = 0x0000
    private const val NIN_BALLOONUSERCLICK = 0x0405

    private const val NIM_ADD = 0
    private const val NIM_MODIFY = 1
    private const val NIM_DELETE = 2
    private const val NIF_MESSAGE = 0x01
    private const val NIF_ICON = 0x02
    private const val NIF_TIP = 0x04
    private const val NIF_INFO = 0x10
    private const val NIIF_INFO = 0x01
    private const val NIIF_WARNING = 0x02
    private const val NIIF_ERROR = 0x03
    private const val NIIF_NOSOUND = 0x10

    private const val MF_STRING = 0x0
    private const val MF_SEPARATOR = 0x800
    private const val TPM_RIGHTBUTTON = 0x0002
    private const val TPM_RETURNCMD = 0x0100
    private const val CMD_OPEN = 1
    private const val CMD_EXIT = 2

    @Structure.FieldOrder(
        "cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip", "dwState", "dwStateMask",
        "szInfo", "uVersion", "szInfoTitle", "dwInfoFlags", "guidItem", "hBalloonIcon",
    )
    class NotifyIconData : Structure() {
        @JvmField var cbSize = 0
        @JvmField var hWnd: Pointer? = null
        @JvmField var uID = 0
        @JvmField var uFlags = 0
        @JvmField var uCallbackMessage = 0
        @JvmField var hIcon: Pointer? = null
        @JvmField var szTip = CharArray(128)
        @JvmField var dwState = 0
        @JvmField var dwStateMask = 0
        @JvmField var szInfo = CharArray(256)
        @JvmField var uVersion = 0
        @JvmField var szInfoTitle = CharArray(64)
        @JvmField var dwInfoFlags = 0
        @JvmField var guidItem = ByteArray(16)
        @JvmField var hBalloonIcon: Pointer? = null
    }

    private interface Shell32Lib : StdCallLibrary {
        fun Shell_NotifyIconW(message: Int, data: NotifyIconData): Boolean
    }

    private interface User32Lib : StdCallLibrary {
        fun CreatePopupMenu(): Pointer?
        fun AppendMenuW(menu: Pointer, flags: Int, id: Long, text: com.sun.jna.WString?): Boolean
        fun TrackPopupMenu(menu: Pointer, flags: Int, x: Int, y: Int, reserved: Int, hwnd: WinDef.HWND, rect: Pointer?): Int
        fun DestroyMenu(menu: Pointer): Boolean
        fun GetCursorPos(point: IntArray): Boolean
        fun SetForegroundWindow(hwnd: WinDef.HWND): Boolean
        fun PostMessageW(hwnd: WinDef.HWND, msg: Int, wParam: Long, lParam: Long): Boolean
        fun RegisterWindowMessageW(name: com.sun.jna.WString): Int
        fun CreateIconFromResourceEx(bits: ByteArray, size: Int, icon: Boolean, version: Int, cx: Int, cy: Int, flags: Int): Pointer?
        fun GetSystemMetrics(index: Int): Int
    }

    private val shell: Shell32Lib? by lazy { runCatching { Native.load("shell32", Shell32Lib::class.java) }.getOrNull() }
    private val user: User32Lib? by lazy { runCatching { Native.load("user32", User32Lib::class.java) }.getOrNull() }

    @Volatile
    private var hwnd: WinDef.HWND? = null

    @Volatile
    private var hicon: Pointer? = null

    @Volatile
    private var windowProvider: () -> Window? = { null }

    @Volatile
    private var lastClickedNotification: Notification? = null

    private var taskbarCreated = 0
    private var initialized = false

    // the window procedure must stay referenced for as long as the window exists
    private val proc = object : WinUser.WindowProc {
        override fun callback(h: WinDef.HWND, msg: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT {
            try {
                when {
                    msg == WM_TRAY -> onTrayMessage(h, (lParam.toLong() and 0xFFFF).toInt())
                    taskbarCreated != 0 && msg == taskbarCreated -> addIcon() // Explorer restarted: the icon is gone
                }
            } catch (t: Throwable) {
                Log.w(TAG, "tray message $msg failed: $t")
            }
            return User32.INSTANCE.DefWindowProc(h, msg, wParam, lParam)
        }
    }

    fun init(windowProvider: () -> Window?) {
        if (initialized) return
        initialized = true
        this.windowProvider = windowProvider
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        thread(name = "cloudstream-tray", isDaemon = true) {
            try {
                runTrayWindow()
            } catch (t: Throwable) {
                Log.e(TAG, "tray failed: $t")
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread { removeIcon() })
        // connect to the Android Notifications sink
        Notifications.addSink { _, notification ->
            if (notification != null) handleNotification(notification)
        }
    }

    private fun runTrayWindow() {
        val u = user ?: return
        taskbarCreated = u.RegisterWindowMessageW(com.sun.jna.WString("TaskbarCreated"))
        val instance = Kernel32.INSTANCE.GetModuleHandle(null)
        val className = "CloudStreamTrayWindow"
        val wc = WinUser.WNDCLASSEX()
        wc.cbSize = wc.size()
        wc.lpfnWndProc = proc
        wc.hInstance = instance
        wc.lpszClassName = className
        User32.INSTANCE.RegisterClassEx(wc)
        // an ordinary (never shown) top-level window, so that it also receives the "TaskbarCreated" broadcast
        val h = User32.INSTANCE.CreateWindowEx(0, className, "CloudStream tray", 0, 0, 0, 0, 0, null, null, instance, null) ?: return
        hwnd = h
        loadIcon()
        addIcon()
        val m = WinUser.MSG()
        while (User32.INSTANCE.GetMessage(m, null, 0, 0) > 0) {
            User32.INSTANCE.TranslateMessage(m)
            User32.INSTANCE.DispatchMessage(m)
        }
    }

    private fun loadIcon() {
        val u = user ?: return
        runCatching {
            val bytes = DesktopTrayNotifications::class.java.getResourceAsStream("/app-icon.png")?.use { it.readBytes() } ?: return
            val size = u.GetSystemMetrics(49).takeIf { it > 0 } ?: 16 // SM_CXSMICON
            hicon = u.CreateIconFromResourceEx(bytes, bytes.size, true, 0x00030000, size, size, 0)
        }
    }

    private fun data(flags: Int): NotifyIconData = NotifyIconData().also {
        it.cbSize = it.size()
        it.hWnd = hwnd?.pointer
        it.uID = 1
        it.uFlags = flags
    }

    private fun addIcon() {
        val d = data(NIF_MESSAGE or NIF_ICON or NIF_TIP)
        d.uCallbackMessage = WM_TRAY
        d.hIcon = hicon
        fill(d.szTip, "CloudStream")
        if (shell?.Shell_NotifyIconW(NIM_ADD, d) != true) Log.w(TAG, "Shell_NotifyIcon(NIM_ADD) failed") else Log.i(TAG, "tray icon added")
    }

    private fun removeIcon() {
        runCatching { if (hwnd != null) shell?.Shell_NotifyIconW(NIM_DELETE, data(0)) }
    }

    private fun fill(target: CharArray, text: String) {
        val n = minOf(text.length, target.size - 1)
        text.toCharArray(target, 0, 0, n)
        for (i in n until target.size) target[i] = '\u0000'
    }

    private fun onTrayMessage(h: WinDef.HWND, event: Int) {
        when (event) {
            WM_LBUTTONUP, WM_LBUTTONDBLCLK -> bringToFront()
            WM_RBUTTONUP, WM_CONTEXTMENU -> showMenu(h)
            NIN_BALLOONUSERCLICK -> {
                val n = lastClickedNotification
                lastClickedNotification = null
                if (n?.contentIntent != null) {
                    try {
                        n.contentIntent?.send()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error sending notification contentIntent", t)
                    }
                }
                bringToFront()
            }
        }
    }

    private fun showMenu(h: WinDef.HWND) {
        val u = user ?: return
        val menu = u.CreatePopupMenu() ?: return
        try {
            u.AppendMenuW(menu, MF_STRING, CMD_OPEN.toLong(), com.sun.jna.WString("Open CloudStream"))
            u.AppendMenuW(menu, MF_SEPARATOR, 0, null)
            u.AppendMenuW(menu, MF_STRING, CMD_EXIT.toLong(), com.sun.jna.WString("Exit"))
            val p = IntArray(2)
            u.GetCursorPos(p)
            u.SetForegroundWindow(h) // without it the menu does not close when clicking elsewhere
            val cmd = u.TrackPopupMenu(menu, TPM_RIGHTBUTTON or TPM_RETURNCMD, p[0], p[1], 0, h, null)
            u.PostMessageW(h, WM_NULL, 0, 0)
            when (cmd) {
                CMD_OPEN -> bringToFront()
                CMD_EXIT -> kotlin.system.exitProcess(0)
            }
        } finally {
            u.DestroyMenu(menu)
        }
    }

    private fun bringToFront() {
        val window = windowProvider() ?: return
        EventQueue.invokeLater {
            try {
                window.isVisible = true
                if (window is Frame && window.extendedState and Frame.ICONIFIED != 0) window.extendedState = Frame.NORMAL
                window.toFront()
                window.requestFocus()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to bring window to front: $t")
            }
        }
    }

    private fun handleNotification(notification: Notification) {
        if (hwnd == null) return
        // Progress notifications -> the in-app downloads UI only
        if (notification.ongoing || notification.progress >= 0 || notification.progressIndeterminate) return

        val title = notification.title?.toString()
        val text = (notification.bigText ?: notification.text ?: notification.subText)?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        lastClickedNotification = notification
        val displayTitle = if (!title.isNullOrBlank()) title else "CloudStream"
        val displayText = text ?: ""
        val kind = when {
            displayText.contains("error", ignoreCase = true) || displayText.contains("failed", ignoreCase = true) -> NIIF_ERROR
            displayText.contains("warning", ignoreCase = true) -> NIIF_WARNING
            else -> NIIF_INFO
        }
        try {
            val d = data(NIF_INFO)
            fill(d.szInfoTitle, displayTitle)
            fill(d.szInfo, displayText)
            d.dwInfoFlags = kind or NIIF_NOSOUND
            if (shell?.Shell_NotifyIconW(NIM_MODIFY, d) != true) Log.w(TAG, "Shell_NotifyIcon(NIM_MODIFY) failed")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to display notification: $t")
        }
    }
}
