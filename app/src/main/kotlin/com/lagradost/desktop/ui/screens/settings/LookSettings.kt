package com.lagradost.desktop.ui.screens.settings

import com.lagradost.desktop.ui.fluent.hairBorder

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.Backdrop
import com.lagradost.desktop.ui.fluent.Badge
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ButtonStyle
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.Density
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.FluentSettings
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Motion
import com.lagradost.desktop.ui.fluent.NavPosition
import com.lagradost.desktop.ui.fluent.NavStyle
import com.lagradost.desktop.ui.fluent.PillButton
import com.lagradost.desktop.ui.fluent.PlayerStyle
import com.lagradost.desktop.ui.fluent.PosterSize
import com.lagradost.desktop.ui.fluent.Slider
import com.lagradost.desktop.ui.fluent.SurfaceTint
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.ThemeMode
import com.lagradost.desktop.ui.fluent.Themes
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.ui.fluent.Tooltip
import com.lagradost.desktop.ui.fluent.Upscaler
import com.lagradost.desktop.ui.fluent.contrastText
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** Settings > Appearance > Layout: where the navigation sits, corners, sizes, motion */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GroupBuilder.layoutItems() {
    val c = Fluent.colors
    fun changed() = Appearance.save()
    action { Button("Reset to default", { Appearance.reset() }, kind = ButtonKind.Subtle, icon = Icons.Refresh) }
    val shape = RoundedCornerShape(FluentShapes.card)
    // navigation position: small windows to pick from
    block(
        "Navigation position",
        "Where the page list sits: a capsule floating at the top of the page, a pill of icons at the left, a rail at either side, tabs in the title bar, or a dock at the bottom.",
        "menu bar dock rail tabs top bottom left right capsule sidebar",
    ) {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(16.dp)) {
            FText("Navigation position", style = Fluent.type.body)
            FText("Where the page list sits: a capsule floating at the top of the page, a pill of icons at the left, a rail at either side, tabs in the title bar, or a dock at the bottom.", style = Fluent.type.caption, color = c.textSecondary)
            Box(Modifier.height(14.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (p in NavPosition.entries) NavPreview(p, Appearance.navPosition == p) { Appearance.navPosition = p; changed() }
            }
        }
    }
    if (Appearance.navPosition == NavPosition.Bottom) {
        card("Hide the dock while scrolling", "The dock slides away when you scroll a page down and comes back when you scroll up or move the pointer to the bottom edge.") {
            ToggleSwitch(Appearance.dockAutoHide, { Appearance.dockAutoHide = it; if (!it) com.lagradost.desktop.ui.shell.ShellState.dockHidden = false; changed() })
        }
    }
    if (Appearance.navPosition == NavPosition.Left || Appearance.navPosition == NavPosition.Right) {
        card("Side rail", "Show the page names when the pointer is on the rail, never, or all the time.") {
            ComboBox(NavStyle.entries.toList(), Appearance.navStyle, { it.label }, { Appearance.navStyle = it; changed() }, minWidth = 190.dp)
        }
    }
    // corner radius with a live sample
    block("Corner radius", "Roundness of cards, posters, dialogs and buttons: ${Appearance.cornerRadius} px", "round rounded corners") {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FText("Corner radius", style = Fluent.type.body)
                    FText("Roundness of cards, posters, dialogs and buttons: ${Appearance.cornerRadius} px", style = Fluent.type.caption, color = c.textSecondary)
                }
                Slider(Appearance.cornerRadius.toFloat(), { Appearance.cornerRadius = it.toInt() }, Modifier.width(240.dp), valueRange = 0f..28f, steps = 1f, onValueChangeFinished = { changed() })
            }
            Box(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val r = FluentShapes.card
                Box(Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(r)).background(c.control).hairBorder(c.stroke, RoundedCornerShape(r)))
                Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(r)).background(c.control).hairBorder(c.stroke, RoundedCornerShape(r)))
                Column(Modifier.size(width = 190.dp, height = 96.dp).clip(RoundedCornerShape(FluentShapes.overlay)).background(c.flyout).hairBorder(c.stroke, RoundedCornerShape(FluentShapes.overlay)).padding(12.dp)) {
                    FText("Dialog", style = Fluent.type.bodyStrong)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(70.dp, 26.dp).clip(RoundedCornerShape(FluentShapes.control)).background(c.accent))
                        Box(Modifier.size(70.dp, 26.dp).clip(RoundedCornerShape(FluentShapes.control)).background(c.control).hairBorder(c.stroke, RoundedCornerShape(FluentShapes.control)))
                    }
                }
            }
        }
    }
    card("Interface size", "Makes everything larger or smaller than the Windows scale: ${(Appearance.uiScale * 100).toInt()} %", "zoom scale text size dpi") {
        Slider(Appearance.uiScale, { Appearance.uiScale = (Math.round(it * 20f) / 20f) }, Modifier.width(220.dp), valueRange = 0.8f..1.3f, onValueChangeFinished = { changed() })
    }
    card("Spacing", "Room between and around items.", "density compact") {
        ComboBox(Density.entries.toList(), Appearance.density, { it.label }, { Appearance.density = it; changed() }, minWidth = 150.dp)
    }
    card("Animations", "Page transitions and hover effects.", "motion transitions reduce") {
        ComboBox(Motion.entries.toList(), Appearance.motion, { it.label }, { Appearance.motion = it; changed() }, minWidth = 150.dp)
    }
    card("Zoom on hover", "Posters and dock items grow a little under the pointer.", "hover") {
        ToggleSwitch(Appearance.hoverZoom, { Appearance.hoverZoom = it; changed() })
    }
}

/** Settings > Appearance > Background: what is behind the pages */
@Composable
fun GroupBuilder.backgroundItems() {
    fun changed() = Appearance.save()
    card(
        "Backdrop",
        "Ambient tints the window with the colours of the artwork you look at." +
            if (Appearance.theme != "classic" && Appearance.backdrop == Backdrop.Black) " Pure black is the look of the Classic theme: a colour theme brings its own page colour." else "",
        "ambient black background",
    ) {
        ComboBox(Backdrop.entries.toList(), Appearance.backdrop, { it.label }, { Appearance.backdrop = it; changed() }, minWidth = 220.dp)
    }
    card("See-through bars", "Bars, the dock and the open rail let the page show through a little.", "glass transparent blur acrylic") {
        ToggleSwitch(Appearance.glass, { Appearance.glass = it; changed() })
    }
    card("Wallpaper", if (Appearance.wallpaper.isBlank()) "A picture of your own behind every page, dimmed so the text stays readable." else java.io.File(Appearance.wallpaper).name, "picture image background") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Appearance.wallpaper.isNotBlank()) Button("Remove", { Appearance.wallpaper = ""; changed() }, kind = ButtonKind.Subtle)
            Button("Choose picture…", { pickWallpaper()?.let { Appearance.wallpaper = it; changed() } }, kind = ButtonKind.Standard, icon = Icons.Folder)
        }
    }
    if (Appearance.wallpaper.isNotBlank()) card("Wallpaper dimming", "How much of the page colour covers the picture: ${(Appearance.wallpaperDim * 100).toInt()} %") {
        Slider(Appearance.wallpaperDim, { Appearance.wallpaperDim = (Math.round(it * 20f) / 20f) }, Modifier.width(220.dp), valueRange = 0.3f..0.95f, onValueChangeFinished = { changed() })
    }
}

/** Settings > Appearance > Home: what Home shows and how big the posters are */
@Composable
fun GroupBuilder.homeItems() {
    fun changed() = Appearance.save()
    card("Home banner", "The large rotating banner at the top of Home.", "featured carousel") {
        ToggleSwitch(Appearance.homeBanner, { Appearance.homeBanner = it; changed() })
    }
    card("Continue watching on Home", "The row of titles you started (the full list is under See all, in History).", "resume history") {
        ToggleSwitch(Appearance.homeContinue, { Appearance.homeContinue = it; changed() })
    }
    card("Poster size", "Size of the posters in shelves and grids.", "cards thumbnails") {
        ComboBox(PosterSize.entries.toList(), Appearance.posterSize, { it.label }, { Appearance.posterSize = it; changed() }, minWidth = 150.dp)
    }
}

/** Settings > General > Window: the title bar and several windows */
@Composable
fun GroupBuilder.windowItems() {
    fun changed() = Appearance.save()
    card("Hide the title bar when maximized", "A maximized window shows only the app: the title bar slides in when the pointer touches the top edge of the screen. Full screen always works this way.", "maximise fullscreen") {
        ToggleSwitch(Appearance.hideTitleBar, { Appearance.hideTitleBar = it; changed() })
    }
    card("Several windows", "Starting the app again while it runs opens another window with its own player, so two things can play side by side. All windows share the same data and settings. Off: a second start only brings the first window to the front.", "multiple instances second") {
        var multi by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.lagradost.desktop.platform.MultiInstance.enabled()) }
        ToggleSwitch(multi, { multi = it; com.lagradost.desktop.platform.MultiInstance.setEnabled(it) })
    }
}

/** Settings > Player > Video: the renderer, the picture and how it is enlarged */
@Composable
fun GroupBuilder.videoItems() {
    fun changed() = Appearance.save()
    card("Native GPU player (beta)", "mpv draws the video with its own GPU renderer, in step with your screen's refresh: the smoothest motion and the least CPU. Still being tested: if the picture or the controls misbehave, switch it off. Applies to the next video.", "mpv renderer hardware") {
        ToggleSwitch(Appearance.nativePlayer, { Appearance.nativePlayer = it; changed() })
    }
    card("Upscaling", "How the picture is enlarged when it is shown larger than it is: mpv's filters, FSR, or Anime4K for anime (modes A, B, C; they ask a lot of the graphics card). Native GPU player only; also in the player's menu. Anime4K by bloc97, FSR and CAS by AMD, ported by agyild (MIT).", "anime4k fsr scaler sharpen") {
        ComboBox(Upscaler.entries.toList(), Appearance.upscaler, { it.label }, { Appearance.upscaler = it; changed() }, minWidth = 240.dp)
    }
    card("Smooth motion", "Blends the frames of 24 and 25 frames per second video so pans and scrolling credits do not judder on a 60 Hz screen. Turn it off if you prefer the pure frames.", "interpolation judder fps") {
        ToggleSwitch(Appearance.smoothMotion, { Appearance.smoothMotion = it; changed() })
    }
}

/** Settings > Player > Sources: how the sources of a title are ordered */
@Composable
fun GroupBuilder.smoothSourcesItem() {
    fun changed() = Appearance.save()
    card("Prefer smooth sources", "4K, REMUX and very large files are tried after the lighter ones: they stall on slow hosts and drop frames on a weak graphics card. All of them stay in the Sources list. Off: the highest quality is tried first.", "4k remux buffering stall quality") {
        ToggleSwitch(Appearance.smoothSources, { Appearance.smoothSources = it; changed() })
    }
}

/** Settings > Player > Controls: the bar over the video */
@Composable
fun GroupBuilder.controlItems() {
    fun changed() = Appearance.save()
    card("Player controls", "Modern: a floating bar over the picture. Classic: a full-width bar at the bottom.", "style bar") {
        ComboBox(PlayerStyle.entries.toList(), Appearance.playerStyle, { it.label }, { Appearance.playerStyle = it; changed() }, minWidth = 210.dp)
    }
    card("Hide the player controls", "How long after the last mouse movement the controls go away. They stay while the pointer is on them, a button is held or a menu is open. The keyboard shortcuts work either way.", "auto hide timeout cursor") {
        ComboBox(listOf(2, 4, 8, 15, 0), Appearance.controlsHideSeconds, { if (it == 0) "Never" else "After $it s" }, { Appearance.controlsHideSeconds = it; changed() }, minWidth = 150.dp)
    }
    card("Seek preview", "A picture of the scene above the time when you point at the seek bar, read by a second, light player in the background. Turn it off to save bandwidth on a slow connection.", "thumbnail scrub bar") {
        ToggleSwitch(Appearance.seekPreviews, { Appearance.seekPreviews = it; changed() })
    }
}

/** A tiny window drawing with the navigation at [p] */
@Composable
private fun NavPreview(p: NavPosition, selected: Boolean, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val frame by animateColorAsState(if (selected) c.accent else if (hovered) c.strokeStrong else c.stroke, FluentMotion.tweenStd(160))
    val shape = RoundedCornerShape(FluentShapes.small.coerceAtLeast(4.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(132.dp, 84.dp).clip(shape).background(c.bg, shape).border(if (selected) 2.dp else 1.dp, frame, shape)
                .fluentClickable(source, true, shape, Role.RadioButton, onClick).padding(6.dp),
        ) {
            val bar = c.textTertiary.copy(alpha = 0.5f)
            val page = c.layer
            val accent = c.accent
            when (p) {
                NavPosition.Left, NavPosition.Right -> Row(Modifier.fillMaxSize()) {
                    val rail = @Composable {
                        Column(Modifier.width(12.dp).fillMaxHeight().padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).background(accent, RoundedCornerShape(2.dp)))
                            repeat(3) { Box(Modifier.size(6.dp).background(bar, RoundedCornerShape(2.dp))) }
                        }
                    }
                    if (p == NavPosition.Left) rail()
                    Box(Modifier.weight(1f).fillMaxHeight().background(page, RoundedCornerShape(3.dp)))
                    if (p == NavPosition.Right) rail()
                }
                NavPosition.Floating -> Box(Modifier.fillMaxSize().background(page, RoundedCornerShape(3.dp))) {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 3.dp).background(c.flyout, RoundedCornerShape(5.dp)).hairBorder(c.strokeStrong, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(Modifier.size(14.dp, 6.dp).background(accent, RoundedCornerShape(3.dp)))
                        repeat(3) { Box(Modifier.size(8.dp, 6.dp).background(bar, RoundedCornerShape(3.dp))) }
                    }
                }
                NavPosition.Dock -> Box(Modifier.fillMaxSize().background(page, RoundedCornerShape(3.dp))) {
                    Column(
                        Modifier.align(Alignment.CenterStart).padding(start = 4.dp).background(c.flyout, RoundedCornerShape(5.dp)).hairBorder(c.strokeStrong, RoundedCornerShape(5.dp)).padding(horizontal = 3.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(accent, RoundedCornerShape(2.dp)))
                        repeat(4) { Box(Modifier.size(6.dp).background(bar, RoundedCornerShape(2.dp))) }
                    }
                }
                NavPosition.Top -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp, 5.dp).background(accent, RoundedCornerShape(3.dp)))
                        repeat(3) { Box(Modifier.size(12.dp, 5.dp).background(bar, RoundedCornerShape(3.dp))) }
                    }
                    Box(Modifier.height(3.dp))
                    Box(Modifier.fillMaxSize().background(page, RoundedCornerShape(3.dp)))
                }
                NavPosition.Bottom -> Box(Modifier.fillMaxSize().background(page, RoundedCornerShape(3.dp))) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).background(c.flyout, RoundedCornerShape(5.dp)).hairBorder(c.strokeStrong, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(accent, RoundedCornerShape(2.dp)))
                        repeat(4) { Box(Modifier.size(6.dp).background(bar, RoundedCornerShape(2.dp))) }
                    }
                }
            }
        }
        Box(Modifier.height(6.dp))
        FText(p.label, style = Fluent.type.caption, color = if (selected) c.text else c.textSecondary)
    }
}

/** Settings > Appearance > Title pages: the information TMDB adds to title pages, the Home banner and Explore */
@Composable
fun GroupBuilder.titleInfoItems() {
    fun changed() = Appearance.save()
    card("Title information from TMDB", "Logos, backdrops, cast, ratings, reviews, trailers, where a title streams and the Explore lists; anime also get prequels and sequels from AniList. Off: the app never contacts TMDB or AniList for this. This product uses the TMDB API but is not endorsed or certified by TMDB.", "tmdb anilist cast logos ratings reviews privacy") {
        ToggleSwitch(Appearance.tmdbEnabled, { Appearance.tmdbEnabled = it; changed() })
    }
    if (Appearance.tmdbEnabled) card("Country", "Decides the age rating and the streaming services shown on title pages and in the Explore filters.", "region") {
        val codes = listOf("") + com.lagradost.desktop.tmdb.Tmdb.regions.keys
        ComboBox(codes, Appearance.tmdbRegion, { if (it.isEmpty()) "Windows region (${com.lagradost.desktop.tmdb.Tmdb.region})" else com.lagradost.desktop.tmdb.Tmdb.regions[it] ?: it }, { Appearance.tmdbRegion = it; changed() }, minWidth = 230.dp)
    }
}

/** Episode lists: hide what is ahead of the viewer */
@Composable
fun GroupBuilder.spoilerItems() {
    fun changed() = Appearance.save()
    card("Hide spoilers in episode lists", "Blurs the still and hides the description of every episode after the one you watch next. The eye on a covered episode shows it.", "blur episode thumbnails description") {
        ToggleSwitch(Appearance.hideSpoilers, { Appearance.hideSpoilers = it; changed() })
    }
    if (Appearance.hideSpoilers) card("Hide the titles as well", "A covered episode is called just \"Episode 12\" until you reach it.", "episode names") {
        ToggleSwitch(Appearance.hideSpoilerTitles, { Appearance.hideSpoilerTitles = it; changed() })
    }
}

private val richColorSwatches = listOf(
    "Electric Blue" to 0xFF3B82F6L,
    "Sky Cyan" to 0xFF38BDF8L,
    "Teal" to 0xFF14B8A6L,
    "Emerald" to 0xFF10B981L,
    "Sage Green" to 0xFF84CC16L,
    "Amber Gold" to 0xFFF59E0BL,
    "Warm Orange" to 0xFFF97316L,
    "Crimson Red" to 0xFFF43F5EL,
    "Rose Pink" to 0xFFEC4899L,
    "Sakura" to 0xFFF472B6L,
    "Orchid" to 0xFFD946EFL,
    "Purple" to 0xFFA855F7L,
    "Indigo" to 0xFF6366F1L,
    "Lavender" to 0xFFB4BEFEL,
    "Warm Gold" to 0xFFEAB308L,
    "Pure White" to 0xFFFFFFFFL,
)

private fun parseHexColor(input: String): Color? {
    val clean = input.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (clean.length != 6 && clean.length != 8) return null
    val parsed = clean.toLongOrNull(16) ?: return null
    val argb = if (clean.length == 6) (0xFF000000L or parsed) else parsed
    return Color(argb)
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

/** Settings > Appearance > Theme & colours: themes, accent colours, independent button/pill colours, fill styles and surface tinting */
@Composable
fun GroupBuilder.themeItems() {
    val c = Fluent.colors
    fun changed() = Appearance.save()
    action {
            Button(
                "Reset colours",
                {
                    Appearance.theme = "classic"
                    Appearance.buttonStyle = ButtonStyle.White
                    Appearance.surfaceTint = SurfaceTint.Balanced
                    Appearance.navAccentPill = false
                    Appearance.themeGlow = false
                    FluentSettings.chooseAccent(null)
                    FluentSettings.chooseButtonColor(null)
                    changed()
                },
                kind = ButtonKind.Subtle,
                icon = Icons.Refresh,
            )
    }

    val shape = RoundedCornerShape(FluentShapes.card)
    card("App mode", "Follow Windows, or always use light or dark surfaces.") {
        ComboBox(
            ThemeMode.entries.toList(),
            FluentSettings.themeMode,
            { when (it) { ThemeMode.System -> "Use system setting"; ThemeMode.Light -> "Light"; ThemeMode.Dark -> "Dark" } },
            { FluentSettings.chooseMode(it) },
            minWidth = 190.dp,
        )
    }

    // 1. Clean Flat Theme Gallery (grid of all 20 themes)
    block("Colour theme", "Classic keeps the default OLED black surfaces untouched. Pick any other palette for flat dark surfaces and a matching accent colour.", "palette preset catppuccin nord dracula gruvbox slate graphite") {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FText("Colour theme", style = Fluent.type.bodyStrong)
                    FText("Classic keeps the default OLED black surfaces untouched. Pick any other palette for flat dark surfaces and a matching accent colour.", style = Fluent.type.caption, color = c.textSecondary)
                }
                val activeTheme = Themes.byId(Appearance.theme)
                Badge(activeTheme.name, accent = true)
            }
            Box(Modifier.height(14.dp))
            val allThemes = remember { listOf(Themes.classic) + Themes.all }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cols = (maxWidth / 168.dp).toInt().coerceIn(2, 5)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (rowThemes in allThemes.chunked(cols)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (p in rowThemes) {
                                ThemeTile(p, selected = Appearance.theme == p.id, modifier = Modifier.weight(1f)) {
                                    Appearance.theme = p.id
                                    FluentSettings.chooseAccent(null)
                                    FluentSettings.chooseButtonColor(null)
                                    changed()
                                }
                            }
                            repeat(cols - rowThemes.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Accent Colour Picker (Swatches + Custom Hex)
    block("Accent colour", "Tints switches, sliders, active tabs, focus rings and links. Override the theme's accent or enter any hex colour.", "color hex swatch highlight") {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(16.dp)) {
            var customHex by remember(FluentSettings.accentOverride) {
                mutableStateOf(FluentSettings.accentOverride?.let { colorToHex(it) } ?: "")
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FText("Accent colour", style = Fluent.type.bodyStrong)
                    FText("Tints switches, sliders, active tabs, focus rings and links. Override the theme's accent or enter any hex colour.", style = Fluent.type.caption, color = c.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(c.accent).border(1.dp, c.strokeStrong, CircleShape))
                    TextBox(
                        value = customHex,
                        onValueChange = { text ->
                            customHex = text
                            parseHexColor(text)?.let { FluentSettings.chooseAccent(it) }
                        },
                        modifier = Modifier.width(118.dp),
                        placeholder = "#RRGGBB",
                    )
                }
            }
            Box(Modifier.height(12.dp))
            ColorSwatchGrid(
                selectedColor = FluentSettings.accentOverride,
                defaultLabel = if (Appearance.theme == "classic") "Windows default" else "Theme default",
                defaultPreview = Themes.byId(Appearance.theme).accent ?: Color(0xFF3B82F6),
                onSelect = { FluentSettings.chooseAccent(it) },
            )
        }
    }

    // 3. Independent Button, Chip & Pill Colour Picker (Swatches + Custom Hex)
    block("Button, chip & pill colour", "Keep the default crisp white buttons and pills, match the accent colour, or pick any solid colour for primary buttons (Watch now, Play), selected chips and active pills.", "color hex swatch primary") {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(16.dp)) {
            val isDefaultWhite = FluentSettings.buttonColorOverride == null && Appearance.buttonStyle == ButtonStyle.White
            val isMatchAccent = FluentSettings.buttonColorOverride == null && Appearance.buttonStyle != ButtonStyle.White
            var buttonHex by remember(FluentSettings.buttonColorOverride) {
                mutableStateOf(FluentSettings.buttonColorOverride?.let { colorToHex(it) } ?: "")
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FText("Button, chip & pill colour", style = Fluent.type.bodyStrong)
                    FText("Keep the default crisp white buttons and pills, match the accent colour, or pick any solid colour for primary buttons (Watch now, Play), selected chips and active pills.", style = Fluent.type.caption, color = c.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val previewDot = if (isDefaultWhite) c.text else c.button
                    Box(Modifier.size(20.dp).clip(CircleShape).background(previewDot).border(1.dp, c.strokeStrong, CircleShape))
                    TextBox(
                        value = buttonHex,
                        onValueChange = { text ->
                            buttonHex = text
                            parseHexColor(text)?.let { FluentSettings.chooseButtonColor(it) }
                        },
                        modifier = Modifier.width(118.dp),
                        placeholder = "#RRGGBB",
                    )
                }
            }
            Box(Modifier.height(12.dp))
            ButtonColorSwatchGrid(
                selectedColor = FluentSettings.buttonColorOverride,
                isDefaultWhite = isDefaultWhite,
                isMatchAccent = isMatchAccent,
                accentPreview = c.accent,
                onSelectDefaultWhite = {
                    FluentSettings.chooseButtonColor(null)
                    Appearance.buttonStyle = ButtonStyle.White
                    changed()
                },
                onSelectMatchAccent = {
                    FluentSettings.chooseButtonColor(null)
                    if (Appearance.buttonStyle == ButtonStyle.White) Appearance.buttonStyle = ButtonStyle.Solid
                    changed()
                },
                onSelectColor = { color ->
                    FluentSettings.chooseButtonColor(color)
                },
            )

            Box(Modifier.height(14.dp))
            // Live interactive preview bar
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(FluentShapes.control))
                    .background(c.layer, RoundedCornerShape(FluentShapes.control))
                    .hairBorder(c.stroke, RoundedCornerShape(FluentShapes.control))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FText("Live preview", style = Fluent.type.caption, color = c.textSecondary, modifier = Modifier.padding(end = 4.dp))
                PillButton("Watch now", Icons.Play, primary = true, onClick = {})
                Button("Add to list", onClick = {}, kind = ButtonKind.Accent, icon = Icons.Add)
                Button("Trailer", onClick = {}, kind = ButtonKind.Standard, icon = Icons.Video)
                Chip("Movies", selected = true, onClick = {})
                Chip("Series", selected = false, onClick = {})
                Spacer(Modifier.weight(1f))
                Badge("4K HDR", accent = true)
            }
        }
    }

    // 4. Button & Surface Style Controls
    card("Button & pill style", "White (default) keeps primary buttons and pills crisp white unless a custom button colour is chosen. Solid colour and Tinted glass use the button or accent colour.") {
        ComboBox(ButtonStyle.entries.toList(), Appearance.buttonStyle, { it.label }, { Appearance.buttonStyle = it; changed() }, minWidth = 190.dp)
    }
    card("Coloured navigation highlight", "Fill the active tab in the floating bar, side rail and dock with your button colour instead of neutral dark glass.") {
        ToggleSwitch(Appearance.navAccentPill, { Appearance.navAccentPill = it; changed() })
    }
    card("Surface & border tint", "How strongly cards, menus, controls and borders absorb the theme or accent colour.") {
        ComboBox(SurfaceTint.entries.toList(), Appearance.surfaceTint, { it.label }, { Appearance.surfaceTint = it; changed() }, minWidth = 190.dp)
    }
}

@Composable
private fun ThemeTile(p: com.lagradost.desktop.ui.fluent.ThemePreset, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val isClassic = p.id == "classic"
    val accent = p.accent ?: Color(0xFF4CC2FF)
    val primaryPreviewColor = if (isClassic) Color.White else accent
    val tileShape = RoundedCornerShape(FluentShapes.card)
    val borderColor by animateColorAsState(
        if (selected) accent else if (hovered) c.strokeStrong else c.stroke,
        FluentMotion.tweenStd(160),
    )

    Column(
        modifier
            .clip(tileShape)
            .background(if (selected || hovered) c.subtleHover else Color.Transparent, tileShape)
            .border(if (selected) 1.5.dp else Dp.Hairline, borderColor, tileShape)
            .fluentClickable(source, true, tileShape, Role.RadioButton, onClick)
            .padding(8.dp),
    ) {
        val innerShape = RoundedCornerShape((FluentShapes.card - 3.dp).coerceAtLeast(4.dp))
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(innerShape).background(p.bg)
                .hairBorder(p.tint.copy(alpha = 0.18f), innerShape)
                .padding(8.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                // Mini top navigation bar + active pill (flat solid, no gradients)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.background(p.flyout, RoundedCornerShape(50))
                            .hairBorder(p.tint.copy(alpha = 0.20f), RoundedCornerShape(50))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(16.dp, 5.dp).clip(RoundedCornerShape(50)).background(if (isClassic) Color(0x2EFFFFFF) else accent))
                        Box(Modifier.size(8.dp, 5.dp).clip(RoundedCornerShape(50)).background(p.tint.copy(alpha = 0.30f)))
                        Box(Modifier.size(8.dp, 5.dp).clip(RoundedCornerShape(50)).background(p.tint.copy(alpha = 0.30f)))
                    }
                    if (selected) {
                        Box(
                            Modifier.size(16.dp).clip(CircleShape).background(accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Check, size = 10.dp, tint = contrastText(accent))
                        }
                    }
                }

                // Mini hero CTA + cards row (flat solid surfaces)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(
                        Modifier.weight(1f)
                            .background(p.layer, RoundedCornerShape(4.dp))
                            .hairBorder(p.tint.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                            .padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(p.tint.copy(alpha = 0.60f)))
                        Box(Modifier.size(width = 28.dp, height = 7.dp).clip(RoundedCornerShape(50)).background(primaryPreviewColor))
                    }
                    repeat(2) {
                        Box(
                            Modifier.size(width = 20.dp, height = 28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(p.layer)
                                .hairBorder(p.tint.copy(alpha = 0.20f), RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
        Box(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            FText(p.name, style = if (selected) Fluent.type.bodyStrong else Fluent.type.caption, color = if (selected) c.text else c.textSecondary, maxLines = 1)
        }
        if (p.subtitle.isNotBlank()) {
            FText(p.subtitle, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1, modifier = Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun ColorSwatchGrid(
    selectedColor: Color?,
    defaultLabel: String,
    defaultPreview: Color,
    onSelect: (Color?) -> Unit,
) {
    val c = Fluent.colors
    val pillShape = RoundedCornerShape(50)
    val defaultSelected = selectedColor == null
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Default / Auto chip
        Box(
            Modifier.height(28.dp).clip(pillShape)
                .background(if (defaultSelected) defaultPreview.copy(alpha = 0.18f) else c.control, pillShape)
                .border(if (defaultSelected) 1.5.dp else Dp.Hairline, if (defaultSelected) defaultPreview else c.strokeStrong, pillShape)
                .fluentClickable(rememberInteraction(), true, pillShape, Role.RadioButton) { onSelect(null) }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(defaultPreview))
                FText(defaultLabel, style = Fluent.type.caption, color = if (defaultSelected) c.text else c.textSecondary)
            }
        }

        // Swatches
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            richColorSwatches.forEach { (name, argb) ->
                val swatchColor = Color(argb)
                val isSelected = selectedColor?.value == swatchColor.value
                Tooltip(name) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(swatchColor, CircleShape)
                            .border(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) c.text else c.strokeStrong,
                                CircleShape,
                            )
                            .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { onSelect(swatchColor) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Check, size = 12.dp, tint = contrastText(swatchColor))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonColorSwatchGrid(
    selectedColor: Color?,
    isDefaultWhite: Boolean,
    isMatchAccent: Boolean,
    accentPreview: Color,
    onSelectDefaultWhite: () -> Unit,
    onSelectMatchAccent: () -> Unit,
    onSelectColor: (Color) -> Unit,
) {
    val c = Fluent.colors
    val pillShape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. White (default) chip
        Box(
            Modifier.height(28.dp).clip(pillShape)
                .background(if (isDefaultWhite) c.text.copy(alpha = 0.16f) else c.control, pillShape)
                .border(if (isDefaultWhite) 1.5.dp else Dp.Hairline, if (isDefaultWhite) c.text else c.strokeStrong, pillShape)
                .fluentClickable(rememberInteraction(), true, pillShape, Role.RadioButton, onSelectDefaultWhite)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White).hairBorder(c.strokeStrong, CircleShape))
                FText("White (default)", style = Fluent.type.caption, color = if (isDefaultWhite) c.text else c.textSecondary)
            }
        }

        // 2. Match accent chip
        Box(
            Modifier.height(28.dp).clip(pillShape)
                .background(if (isMatchAccent) accentPreview.copy(alpha = 0.18f) else c.control, pillShape)
                .border(if (isMatchAccent) 1.5.dp else Dp.Hairline, if (isMatchAccent) accentPreview else c.strokeStrong, pillShape)
                .fluentClickable(rememberInteraction(), true, pillShape, Role.RadioButton, onSelectMatchAccent)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accentPreview))
                FText("Match accent", style = Fluent.type.caption, color = if (isMatchAccent) c.text else c.textSecondary)
            }
        }

        // 3. Solid color swatches
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            richColorSwatches.forEach { (name, argb) ->
                val swatchColor = Color(argb)
                val isSelected = selectedColor?.value == swatchColor.value
                Tooltip(name) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(swatchColor, CircleShape)
                            .border(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) c.text else c.strokeStrong,
                                CircleShape,
                            )
                            .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { onSelectColor(swatchColor) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Check, size = 12.dp, tint = contrastText(swatchColor))
                        }
                    }
                }
            }
        }
    }
}

/** A picture chosen in the Windows file dialog, copied into the data folder (the original may move); null when cancelled */
private fun pickWallpaper(): String? {
    val dialog = java.awt.FileDialog(com.lagradost.desktop.ui.DesktopUiHost.window, "Choose a wallpaper", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, n -> n.lowercase().substringAfterLast('.') in setOf("png", "jpg", "jpeg", "webp", "bmp") }
    dialog.isVisible = true
    val file = dialog.file?.let { java.io.File(dialog.directory, it) }?.takeIf { it.isFile } ?: return null
    return runCatching {
        val dir = java.io.File(com.lagradost.desktop.runtime.AndroidRuntime.dataDir, "files").also { it.mkdirs() }
        // the picture that is already the wallpaper (a copy in this folder) is kept as it is
        if (file.absoluteFile.parentFile == dir.absoluteFile && file.name.startsWith("wallpaper.")) return@runCatching file.absolutePath
        val copy = java.io.File(dir, "wallpaper." + file.extension.lowercase().ifBlank { "png" })
        file.copyTo(copy, overwrite = true)
        dir.listFiles { f -> f.name.startsWith("wallpaper.") && f != copy }?.forEach { it.delete() }
        copy.absolutePath
    }.getOrElse { file.absolutePath }
}
