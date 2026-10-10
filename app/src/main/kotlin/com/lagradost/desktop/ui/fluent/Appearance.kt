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
enum class NavPosition(val label: String) { Floating("Floating bar"), Dock("Floating dock"), Left("Left"), Right("Right"), Top("Top"), Bottom("Bottom dock") }

/** How the side rail shows its names */
enum class NavStyle(val label: String) { Hover("Expand on hover"), Icons("Icons only"), Labels("Always expanded") }

enum class Density(val label: String, val scale: Float) { Compact("Compact", 0.85f), Standard("Standard", 1f), Comfortable("Comfortable", 1.15f) }

enum class PosterSize(val label: String, val width: Dp) { Small("Small", 132.dp), Medium("Medium", 160.dp), Large("Large", 196.dp) }

/** What is behind the pages */
enum class Backdrop(val label: String) { Ambient("Ambient (artwork colours)"), Solid("Solid"), Black("Pure black (OLED)") }

enum class Motion(val label: String, val factor: Float) { Full("Full", 1f), Reduced("Reduced", 0.55f), Off("Off", 0f) }

enum class PlayerStyle(val label: String) { Modern("Modern (floating bar)"), Classic("Classic (full width)") }

/**
 * Who decodes the sound. SW: the app, for speakers and headphones. HW: Dolby Digital and DTS go undecoded to an AV receiver or TV,
 * which decodes them (HDMI or optical). HW+: also Dolby Digital Plus, TrueHD and DTS-HD (HDMI receivers). [spdif] is mpv's audio-spdif
 * list; mpv decodes a track itself when the device refuses it.
 */
enum class AudioDecoder(val label: String, val detail: String, val spdif: String) {
    Software("Software (SW)", "Decoded by the app. For speakers and headphones.", ""),
    Hardware("Hardware (HW)", "Dolby Digital and DTS are decoded by your AV receiver or TV (HDMI or optical).", "ac3,dts"),
    HardwarePlus("Hardware+ (HW+)", "Also Dolby Digital Plus, Dolby TrueHD and DTS-HD (HDMI receivers).", "ac3,eac3,dts,dts-hd,truehd"),
}

/**
 * User customisation of the look (Settings > Appearance > Layout and style). Every value is Compose state, so the whole UI
 * follows a change at once, and is saved in the app preferences.
 */
object Appearance {
    var navPosition by mutableStateOf(NavPosition.Floating)
    var navStyle by mutableStateOf(NavStyle.Hover)

    /** Corner radius of cards, posters and dialogs (dp); controls use half of it */
    var cornerRadius by mutableIntStateOf(12)
    var density by mutableStateOf(Density.Standard)
    var posterSize by mutableStateOf(PosterSize.Large)
    var backdrop by mutableStateOf(Backdrop.Black)

    /** See-through bars and panels over artwork */
    var glass by mutableStateOf(true)
    var motion by mutableStateOf(Motion.Full)

    /** Size of everything (100 = Windows scale) */
    var uiScale by mutableFloatStateOf(1f)
    var playerStyle by mutableStateOf(PlayerStyle.Modern)

    /** The animated logo while the app starts */
    var startupAnimation by mutableStateOf(true)

    /** The main action on artwork (Play, Watch now) is a flat white button; off: filled with the theme's accent colour */
    var whitePrimary by mutableStateOf(true)

    /** Shelves zoom the poster under the pointer */
    var hoverZoom by mutableStateOf(true)

    /** Video: mpv draws with its own GPU renderer in a window of its own and the controls float above it (beta); applies to the next video */
    var nativePlayer by mutableStateOf(false)

    /** The automatic choice of a source puts 4K, REMUX and very large files behind the lighter ones: they stall on slow hosts and weak graphics cards (the Sources list still has them) */
    var smoothSources by mutableStateOf(true)

    /** A maximized window hides its title bar; it slides in when the pointer touches the top edge (full screen always does this) */
    var hideTitleBar by mutableStateOf(true)

    /** Native player only: the Anime4K neural filters sharpen and enlarge low resolution anime (needs a fairly strong graphics card) */
    var anime4k by mutableStateOf(false)

    /** Automatically skip intro/outro skip stamps */
    var autoSkipStamps by mutableStateOf(false)

    /** Show a 5-second countdown timer before auto-skipping */
    var autoSkipDelay5s by mutableStateOf(false)

    /** Show remaining time (-18:42) instead of total duration on the seek bar */
    var showRemainingTime by mutableStateOf(false)

    /** Who decodes the sound (SW / HW / HW+, see [AudioDecoder]); changed in the player it applies at once. A state of its own for the settings list */
    val audioDecoderState = mutableStateOf(AudioDecoder.Software)
    var audioDecoder by audioDecoderState

    /** Video: blends the frames around each screen refresh so 24 or 25 fps film does not judder on a 60 Hz screen */
    var smoothMotion by mutableStateOf(true)

    /** Bottom dock only: it slides away while a page is scrolled down and comes back on scrolling up or at the bottom edge (off by default) */
    var dockAutoHide by mutableStateOf(false)

    /** Subtitles: false = a styled subtitle (ASS, coloured SRT) keeps its own look, true = the user style for every subtitle ("universal") */
    var subtitleUniversal by mutableStateOf(false)

    /** Title information (artwork, cast, ratings, where to watch) from TMDB; off means no request to it is made */
    var tmdbEnabled by mutableStateOf(true)

    /** Country code for age ratings and streaming services; empty follows the Windows region */
    var tmdbRegion by mutableStateOf("")

    /** Colour theme (see Themes): tinted surfaces and accent */
    var theme by mutableStateOf("classic")

    /** Two soft colour glows behind the pages (the theme names the colours; Classic has none) */
    var themeGlow by mutableStateOf(true)

    /** A picture of the user's own behind every page (a copy in the data folder; empty = none) and how much of the page colour covers it */
    var wallpaper by mutableStateOf("")
    var wallpaperDim by mutableStateOf(0.8f)

    /** Profile pictures that move (see Profiles.kt); off keeps them still */
    var animatedProfiles by mutableStateOf(true)

    /** Episode lists: stills and descriptions of episodes you have not reached yet are blurred / hidden, so they cannot spoil the story; titles too when [hideSpoilerTitles] */
    var hideSpoilers by mutableStateOf(true)
    var hideSpoilerTitles by mutableStateOf(false)

    /** The banner at the top of Home and the Continue watching row can be switched off */
    var homeBanner by mutableStateOf(true)
    var homeContinue by mutableStateOf(true)

    private const val PREFIX = "desktop_look_"

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default

    fun load() {
        runCatching {
            val p = prefs()
            // the cinema look (2026-10-09): floating icon dock, flat black page, larger posters. Once, a user who still has the old defaults gets it;
            // whoever chose something else keeps it
            val cinema = p.getBoolean(PREFIX + "cinema_v1", false)
            val savedNav = p.getString(PREFIX + "nav", null)
            navPosition = if (!cinema && (savedNav == null || savedNav == "Top")) NavPosition.Floating else enumOf(savedNav, NavPosition.Floating)
            navStyle = enumOf(p.getString(PREFIX + "nav_style", null), NavStyle.Hover)
            cornerRadius = p.getInt(PREFIX + "radius", 12).coerceIn(0, 28)
            density = enumOf(p.getString(PREFIX + "density", null), Density.Standard)
            val savedPoster = p.getString(PREFIX + "poster", null)
            posterSize = if (!cinema && (savedPoster == null || savedPoster == "Medium")) PosterSize.Large else enumOf(savedPoster, PosterSize.Large)
            // the minimal look (2026-10-07): a flat page by default; once, an older saved "Ambient" becomes "Solid" too (it can be picked again)
            val savedBackdrop = if (!p.getBoolean(PREFIX + "minimal_v1", false)) null else p.getString(PREFIX + "backdrop", null)
            backdrop = if (!cinema && (savedBackdrop == null || savedBackdrop == "Solid")) Backdrop.Black else enumOf(savedBackdrop, Backdrop.Black)
            glass = p.getBoolean(PREFIX + "glass", true)
            motion = enumOf(p.getString(PREFIX + "motion", null), Motion.Full)
            uiScale = p.getFloat(PREFIX + "scale", 1f).coerceIn(0.8f, 1.4f)
            playerStyle = enumOf(p.getString(PREFIX + "player", null), PlayerStyle.Modern)
            startupAnimation = p.getBoolean(PREFIX + "startup", true)
            hoverZoom = p.getBoolean(PREFIX + "hover_zoom", true)
            whitePrimary = p.getBoolean(PREFIX + "white_primary", true)
            smoothMotion = p.getBoolean(PREFIX + "smooth_motion", true)
            nativePlayer = p.getBoolean(PREFIX + "native_player", false)
            smoothSources = p.getBoolean(PREFIX + "smooth_sources", true)
            hideTitleBar = p.getBoolean(PREFIX + "hide_title_bar", true)
            anime4k = p.getBoolean(PREFIX + "anime4k", false)
            audioDecoder = enumOf(p.getString(PREFIX + "audio_decoder", null), AudioDecoder.Software)
            dockAutoHide = p.getBoolean(PREFIX + "dock_auto_hide", false)
            subtitleUniversal = p.getBoolean(PREFIX + "sub_universal", false)
            tmdbEnabled = p.getBoolean(PREFIX + "tmdb", true)
            tmdbRegion = p.getString(PREFIX + "tmdb_region", "") ?: ""
            // nothing saved yet: the colourful Midnight theme, unless the user had chosen an accent colour (then the Windows look stays)
            theme = p.getString(PREFIX + "theme", null) ?: if (p.contains("desktop_accent_color")) "classic" else Themes.DEFAULT_ID
            themeGlow = p.getBoolean(PREFIX + "theme_glow", true)
            wallpaper = (p.getString(PREFIX + "wallpaper", "") ?: "").takeIf { java.io.File(it).isFile } ?: ""
            wallpaperDim = p.getFloat(PREFIX + "wallpaper_dim", 0.8f).coerceIn(0.3f, 0.95f)
            animatedProfiles = p.getBoolean(PREFIX + "animated_profiles", true)
            homeBanner = p.getBoolean(PREFIX + "home_banner", true)
            homeContinue = p.getBoolean(PREFIX + "home_continue", true)
            hideSpoilers = p.getBoolean(PREFIX + "hide_spoilers", true)
            hideSpoilerTitles = p.getBoolean(PREFIX + "hide_spoiler_titles", false)
            autoSkipStamps = p.getBoolean(PREFIX + "auto_skip_stamps", false)
            autoSkipDelay5s = p.getBoolean(PREFIX + "auto_skip_delay_5s", false)
            showRemainingTime = p.getBoolean(PREFIX + "show_remaining_time", false)
            if (!cinema) save()
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
                .putBoolean(PREFIX + "minimal_v1", true)
                .putBoolean(PREFIX + "cinema_v1", true)
                .putBoolean(PREFIX + "white_primary", whitePrimary)
                .putBoolean(PREFIX + "glass", glass)
                .putString(PREFIX + "motion", motion.name)
                .putFloat(PREFIX + "scale", uiScale)
                .putString(PREFIX + "player", playerStyle.name)
                .putBoolean(PREFIX + "startup", startupAnimation)
                .putBoolean(PREFIX + "hover_zoom", hoverZoom)
                .putBoolean(PREFIX + "smooth_motion", smoothMotion)
                .putBoolean(PREFIX + "native_player", nativePlayer)
                .putBoolean(PREFIX + "smooth_sources", smoothSources)
                .putBoolean(PREFIX + "hide_title_bar", hideTitleBar)
                .putBoolean(PREFIX + "anime4k", anime4k)
                .putBoolean(PREFIX + "auto_skip_stamps", autoSkipStamps)
                .putBoolean(PREFIX + "auto_skip_delay_5s", autoSkipDelay5s)
                .putBoolean(PREFIX + "show_remaining_time", showRemainingTime)
                .putString(PREFIX + "audio_decoder", audioDecoder.name)
                .putBoolean(PREFIX + "dock_auto_hide", dockAutoHide)
                .putBoolean(PREFIX + "sub_universal", subtitleUniversal)
                .putBoolean(PREFIX + "tmdb", tmdbEnabled)
                .putString(PREFIX + "tmdb_region", tmdbRegion)
                .putString(PREFIX + "theme", theme)
                .putBoolean(PREFIX + "theme_glow", themeGlow)
                .putString(PREFIX + "wallpaper", wallpaper)
                .putFloat(PREFIX + "wallpaper_dim", wallpaperDim)
                .putBoolean(PREFIX + "animated_profiles", animatedProfiles)
                .putBoolean(PREFIX + "home_banner", homeBanner)
                .putBoolean(PREFIX + "home_continue", homeContinue)
                .putBoolean(PREFIX + "hide_spoilers", hideSpoilers)
                .putBoolean(PREFIX + "hide_spoiler_titles", hideSpoilerTitles)
                .apply()
        }
    }

    fun reset() {
        navPosition = NavPosition.Floating; navStyle = NavStyle.Hover; cornerRadius = 12; density = Density.Standard
        posterSize = PosterSize.Large; backdrop = Backdrop.Black; whitePrimary = true; glass = true; motion = Motion.Full; uiScale = 1f
        playerStyle = PlayerStyle.Modern; startupAnimation = true; hoverZoom = true; smoothMotion = true; nativePlayer = false; smoothSources = true; hideTitleBar = true; anime4k = false; autoSkipStamps = false; autoSkipDelay5s = false; showRemainingTime = false; dockAutoHide = false; tmdbEnabled = true; tmdbRegion = ""; theme = Themes.DEFAULT_ID; themeGlow = true; hideSpoilers = true; hideSpoilerTitles = false
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
    /** like [tweenIn] but starting [delayMs] late: the first frames of a new page are spent composing it, and an animation that has already begun looks like a pop */
    fun <T> tweenInAfter(base: Int, delayMs: Int): FiniteAnimationSpec<T> = if (Appearance.motion == Motion.Off) snap() else tween(ms(base), delayMillis = delayMs, easing = enter)
    fun <T> tweenOut(base: Int): FiniteAnimationSpec<T> = if (Appearance.motion == Motion.Off) snap() else tween(ms(base), easing = exit)
    fun <T> tweenStd(base: Int): FiniteAnimationSpec<T> = if (Appearance.motion == Motion.Off) snap() else tween(ms(base), easing = standard)
}
