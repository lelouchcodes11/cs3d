package com.lagradost.desktop.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/** The Windows accent palette (Light3 .. Dark3), as in the Settings > Personalization > Colors page */
class AccentPalette(
    val light3: Color,
    val light2: Color,
    val light1: Color,
    val base: Color,
    val dark1: Color,
    val dark2: Color,
    val dark3: Color,
) {
    companion object {
        val Default = AccentPalette(
            Color(0xFF99EBFF), Color(0xFF4CC2FF), Color(0xFF0091F8), Color(0xFF0078D4),
            Color(0xFF0067C0), Color(0xFF003E92), Color(0xFF001A68),
        )
    }
}

/** Reads the user's Windows theme (light/dark, accent) and tweaks the window frame with DWM */
object WinTheme {
    private const val PERSONALIZE = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    private const val ACCENT = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Accent"

    /** True when Windows apps use the dark theme (default: dark) */
    fun systemIsDark(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, PERSONALIZE, "AppsUseLightTheme") == 0
    }.getOrDefault(true)

    fun systemAccent(): AccentPalette = runCatching {
        val bytes = Advapi32Util.registryGetBinaryValue(WinReg.HKEY_CURRENT_USER, ACCENT, "AccentPalette")
        fun c(i: Int) = Color(
            red = (bytes[i * 4].toInt() and 0xFF) / 255f,
            green = (bytes[i * 4 + 1].toInt() and 0xFF) / 255f,
            blue = (bytes[i * 4 + 2].toInt() and 0xFF) / 255f,
        )
        AccentPalette(c(0), c(1), c(2), c(3), c(4), c(5), c(6))
    }.getOrDefault(AccentPalette.Default)

    /** Bumped when the system theme/accent changed, observed by the theme provider */
    var systemRevision by mutableStateOf(0)
        private set

    private var lastSnapshot: Pair<Boolean, Color>? = null

    /** Polled every few seconds by the theme provider; cheap registry reads */
    fun refreshIfChanged() {
        val snapshot = systemIsDark() to systemAccent().base
        if (lastSnapshot != null && lastSnapshot != snapshot) systemRevision++
        lastSnapshot = snapshot
    }

    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: Dwm? by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private fun colorRef(c: Color): Int {
        val r = (c.red * 255f + 0.5f).toInt()
        val g = (c.green * 255f + 0.5f).toInt()
        val b = (c.blue * 255f + 0.5f).toInt()
        return (b shl 16) or (g shl 8) or r
    }

    /** Dark title bar, caption + text colour of the app's background, rounded corners (Windows 11) */
    fun styleWindow(window: Window, dark: Boolean, caption: Color, text: Color, fullscreen: Boolean = false) {
        val api = dwm ?: return
        runCatching {
            val hwnd = WinDef.HWND(Pointer(Native.getComponentID(window)))
            fun set(attr: Int, v: Int) = api.DwmSetWindowAttribute(hwnd, attr, IntByReference(v), 4)
            set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
            // full screen: square corners and no border colour, nothing around the picture
            set(DWMWA_WINDOW_CORNER_PREFERENCE, if (fullscreen) 1 else 2) // DWMWCP_DONOTROUND / DWMWCP_ROUND
            set(DWMWA_CAPTION_COLOR, colorRef(caption))
            set(DWMWA_TEXT_COLOR, colorRef(text))
            set(DWMWA_BORDER_COLOR, if (fullscreen) -2 else colorRef(caption)) // -2 = DWMWA_COLOR_NONE
        }
    }
}
