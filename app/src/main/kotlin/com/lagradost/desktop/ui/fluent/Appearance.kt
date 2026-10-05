package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where the main navigation sits */
enum class NavPosition(val label: String) { Left("Left"), Right("Right"), Top("Top"), Bottom("Bottom dock") }

/** How the side rail shows its names */
enum class NavStyle(val label: String) { Hover("Expand on hover"), Icons("Icons only"), Labels("Always expanded") }

enum class Density(val label: String, val scale: Float) { Compact("Compact", 0.85f), Standard("Standard", 1f), Comfortable("Comfortable", 1.15f) }

enum class PosterSize(val label: String, val width: Dp) { Small("Small", 132.dp), Medium("Medium", 160.dp), Large("Large", 196.dp) }

/** What is behind the pages */
enum class Backdrop(val label: String) { Ambient("Ambient (artwork colours)"), Solid("Solid"), Black("Pure black (OLED)") }

enum class Motion(val label: String, val factor: Float) { Full("Full", 1f), Reduced("Reduced", 0.55f), Off("Off", 0f) }

enum class PlayerStyle(val label: String) { Modern("Modern (floating bar)"), Classic("Classic (full width)") }

/**
 * User customisation of the look (Settings > Appearance > Layout and style). Every value is Compose state, so the whole UI
 * follows a change at once, and is saved in the app preferences.
 */
object Appearance {
    var navPosition by mutableStateOf(NavPosition.Top)
    var navStyle by mutableStateOf(NavStyle.Hover)

    /** Corner radius of cards, posters and dialogs (dp); controls use half of it */
    var cornerRadius by mutableIntStateOf(12)
    var density by mutableStateOf(Density.Standard)
    var posterSize by mutableStateOf(PosterSize.Medium)
    var backdrop by mutableStateOf(Backdrop.Ambient)

    /** See-through bars and panels over artwork */
    var glass by mutableStateOf(true)
    var motion by mutableStateOf(Motion.Full)

    /** Size of everything (100 = Windows scale) */
    var uiScale by mutableFloatStateOf(1f)
    var playerStyle by mutableStateOf(PlayerStyle.Modern)

    /** The animated logo while the app starts */
    var startupAnimation by mutableStateOf(true)

    /** Shelves zoom the poster under the pointer */
    var hoverZoom by mutableStateOf(true)

    /** Video: mpv draws with its own GPU renderer in a window of its own and the controls float above it (beta); applies to the next video */
    var nativePlayer by mutableStateOf(false)

    /** Native player only: the Anime4K neural filters sharpen and enlarge low resolution anime (needs a fairly strong graphics card) */
    var anime4k by mutableStateOf(false)

    /** Video: blends the frames around each screen refresh so 24 or 25 fps film does not judder on a 60 Hz screen */
    var smoothMotion by mutableStateOf(true)

    /** Bottom dock only: it slides away while a page is scrolled down and comes back on scrolling up or at the bottom edge (off by default) */
    var dockAutoHide by mutableStateOf(false)

    private const val PREFIX = "desktop_look_"

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default

    fun load() {
        runCatching {
            val p = prefs()
            navPosition = enumOf(p.getString(PREFIX + "nav", null), NavPosition.Top)
            navStyle = enumOf(p.getString(PREFIX + "nav_style", null), NavStyle.Hover)
            cornerRadius = p.getInt(PREFIX + "radius", 12).coerceIn(0, 28)
            density = enumOf(p.getString(PREFIX + "density", null), Density.Standard)
            posterSize = enumOf(p.getString(PREFIX + "poster", null), PosterSize.Medium)
            backdrop = enumOf(p.getString(PREFIX + "backdrop", null), Backdrop.Ambient)
            glass = p.getBoolean(PREFIX + "glass", true)
            motion = enumOf(p.getString(PREFIX + "motion", null), Motion.Full)
            uiScale = p.getFloat(PREFIX + "scale", 1f).coerceIn(0.8f, 1.4f)
            playerStyle = enumOf(p.getString(PREFIX + "player", null), PlayerStyle.Modern)
            startupAnimation = p.getBoolean(PREFIX + "startup", true)
            hoverZoom = p.getBoolean(PREFIX + "hover_zoom", true)
            smoothMotion = p.getBoolean(PREFIX + "smooth_motion", true)
            nativePlayer = p.getBoolean(PREFIX + "native_player", false)
            anime4k = p.getBoolean(PREFIX + "anime4k", false)
            dockAutoHide = p.getBoolean(PREFIX + "dock_auto_hide", false)
        }
    }

    /** Saves the current values (called by the settings page after each change) */
    fun save() {
        runCatching {
            prefs().edit()
                .putString(PREFIX + "nav", navPosition.name)
                .putString(PREFIX + "nav_style", navStyle.name)
                .putInt(PREFIX + "radius", cornerRadius)
                .putString(PREFIX + "density", density.name)
                .putString(PREFIX + "poster", posterSize.name)
                .putString(PREFIX + "backdrop", backdrop.name)
                .putBoolean(PREFIX + "glass", glass)
                .putString(PREFIX + "motion", motion.name)
                .putFloat(PREFIX + "scale", uiScale)
                .putString(PREFIX + "player", playerStyle.name)
                .putBoolean(PREFIX + "startup", startupAnimation)
                .putBoolean(PREFIX + "hover_zoom", hoverZoom)
                .putBoolean(PREFIX + "smooth_motion", smoothMotion)
                .putBoolean(PREFIX + "native_player", nativePlayer)
                .putBoolean(PREFIX + "anime4k", anime4k)
                .putBoolean(PREFIX + "dock_auto_hide", dockAutoHide)
                .apply()
        }
    }

    fun reset() {
        navPosition = NavPosition.Top; navStyle = NavStyle.Hover; cornerRadius = 12; density = Density.Standard
        posterSize = PosterSize.Medium; backdrop = Backdrop.Ambient; glass = true; motion = Motion.Full; uiScale = 1f
        playerStyle = PlayerStyle.Modern; startupAnimation = true; hoverZoom = true; smoothMotion = true; nativePlayer = false; anime4k = false; dockAutoHide = false
        save()
    }

    /** Spacing scaled by the density choice */
    fun space(base: Dp): Dp = base * density.scale
}

/** Motion tokens: WinUI-like curves, durations scaled (or switched off) by [Appearance.motion] */
object FluentMotion {
    /** decelerate: things that arrive */
    val enter: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

    /** accelerate: things that leave */
    val exit: Easing = CubicBezierEasing(0.7f, 0f, 1f, 0.5f)

    /** standard point to point */
    val standard: Easing = CubicBezierEasing(0.55f, 0.55f, 0f, 1f)

    fun ms(base: Int): Int = (base * Appearance.motion.factor).toInt()

    fun <T> tweenIn(base: Int): FiniteAnimationSpec<T> = if (Appearance.motion == Motion.Off) snap() else tween(ms(base), easing = enter)
    fun <T> tweenOut(base: Int): FiniteAnimationSpec<T> = if (Appearance.motion == Motion.Off) snap() else tween(ms(base), easing = exit)
    fun <T> tweenStd(base: Int): FiniteAnimationSpec<T> = if (Appearance.motion == Motion.Off) snap() else tween(ms(base), easing = standard)
}
