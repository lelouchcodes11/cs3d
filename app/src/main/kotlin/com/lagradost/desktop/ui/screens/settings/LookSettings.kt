package com.lagradost.desktop.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.lagradost.desktop.ui.fluent.Themes
import com.lagradost.desktop.ui.fluent.FluentSettings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.Backdrop
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.Density
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Motion
import com.lagradost.desktop.ui.fluent.NavPosition
import com.lagradost.desktop.ui.fluent.NavStyle
import com.lagradost.desktop.ui.fluent.PlayerStyle
import com.lagradost.desktop.ui.fluent.PosterSize
import com.lagradost.desktop.ui.fluent.Slider
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** Settings > Appearance > Layout and style: where the navigation sits, corners, sizes, backdrop, motion */
@Composable
fun LayoutAndStyleCards() {
    val c = Fluent.colors
    fun changed() = Appearance.save()
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 2.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        FText("Layout and style", style = Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
        Button("Reset to default", { Appearance.reset() }, kind = ButtonKind.Subtle, icon = Icons.Refresh)
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // navigation position: four small windows to pick from
        val shape = RoundedCornerShape(FluentShapes.card)
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).border(Dp.Hairline, c.stroke, shape).padding(16.dp)) {
            FText("Navigation position", style = Fluent.type.body)
            FText("Where the page list sits: a rail at either side, tabs in the title bar, or a floating dock.", style = Fluent.type.caption, color = c.textSecondary)
            Box(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (p in NavPosition.entries) NavPreview(p, Appearance.navPosition == p) { Appearance.navPosition = p; changed() }
            }
        }
        if (Appearance.navPosition == NavPosition.Bottom) {
            SettingsCard("Hide the dock while scrolling", "The dock slides away when you scroll a page down and comes back when you scroll up or move the pointer to the bottom edge.") {
                ToggleSwitch(Appearance.dockAutoHide, { Appearance.dockAutoHide = it; if (!it) com.lagradost.desktop.ui.shell.ShellState.dockHidden = false; changed() })
            }
        }
        if (Appearance.navPosition == NavPosition.Left || Appearance.navPosition == NavPosition.Right) {
            SettingsCard("Side rail", "Show the page names when the pointer is on the rail, never, or all the time.") {
                ComboBox(NavStyle.entries.toList(), Appearance.navStyle, { it.label }, { Appearance.navStyle = it; changed() }, minWidth = 190.dp)
            }
        }
        // corner radius with a live sample
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).border(Dp.Hairline, c.stroke, shape).padding(16.dp)) {
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
                Box(Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(r)).background(c.control).border(Dp.Hairline, c.stroke, RoundedCornerShape(r)))
                Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(r)).background(c.control).border(Dp.Hairline, c.stroke, RoundedCornerShape(r)))
                Column(Modifier.size(width = 190.dp, height = 96.dp).clip(RoundedCornerShape(FluentShapes.overlay)).background(c.flyout).border(Dp.Hairline, c.stroke, RoundedCornerShape(FluentShapes.overlay)).padding(12.dp)) {
                    FText("Dialog", style = Fluent.type.bodyStrong)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(70.dp, 26.dp).clip(RoundedCornerShape(FluentShapes.control)).background(c.accent))
                        Box(Modifier.size(70.dp, 26.dp).clip(RoundedCornerShape(FluentShapes.control)).background(c.control).border(Dp.Hairline, c.stroke, RoundedCornerShape(FluentShapes.control)))
                    }
                }
            }
        }
        SettingsCard("Backdrop", "Ambient tints the window with the colours of the artwork you look at.") {
            ComboBox(Backdrop.entries.toList(), Appearance.backdrop, { it.label }, { Appearance.backdrop = it; changed() }, minWidth = 220.dp)
        }
        SettingsCard("See-through bars", "Bars, the dock and the open rail let the page show through a little.") {
            ToggleSwitch(Appearance.glass, { Appearance.glass = it; changed() })
        }
        SettingsCard("Poster size", "Size of the posters in shelves and grids.") {
            ComboBox(PosterSize.entries.toList(), Appearance.posterSize, { it.label }, { Appearance.posterSize = it; changed() }, minWidth = 150.dp)
        }
        SettingsCard("Spacing", "Room between and around items.") {
            ComboBox(Density.entries.toList(), Appearance.density, { it.label }, { Appearance.density = it; changed() }, minWidth = 150.dp)
        }
        SettingsCard("Interface size", "Makes everything larger or smaller than the Windows scale: ${(Appearance.uiScale * 100).toInt()} %") {
            Slider(Appearance.uiScale, { Appearance.uiScale = (Math.round(it * 20f) / 20f) }, Modifier.width(220.dp), valueRange = 0.8f..1.3f, onValueChangeFinished = { changed() })
        }
        SettingsCard("Animations", "Page transitions and hover effects.") {
            ComboBox(Motion.entries.toList(), Appearance.motion, { it.label }, { Appearance.motion = it; changed() }, minWidth = 150.dp)
        }
        SettingsCard("Zoom on hover", "Posters and dock items grow a little under the pointer.") {
            ToggleSwitch(Appearance.hoverZoom, { Appearance.hoverZoom = it; changed() })
        }
        SettingsCard("Native GPU player (beta)", "mpv draws the video with its own GPU renderer in a window of its own, in step with your screen's refresh, and the controls float above it. Smoothest motion and the least CPU, but still being tested: if the picture or the controls misbehave, switch it off. Applies to the next video.") {
            ToggleSwitch(Appearance.nativePlayer, { Appearance.nativePlayer = it; changed() })
        }
        SettingsCard("Anime upscaling (Anime4K)", "Neural filters that clean up and enlarge low resolution anime. Works with the native GPU player only, and asks a lot of the graphics card. Applies to the next video. Anime4K by bloc97 (MIT).") {
            ToggleSwitch(Appearance.anime4k, { Appearance.anime4k = it; changed() })
        }
        SettingsCard("Smooth motion", "Blends the frames of 24 and 25 frames per second video so pans and scrolling credits do not judder on a 60 Hz screen. Turn it off if you prefer the pure frames.") {
            ToggleSwitch(Appearance.smoothMotion, { Appearance.smoothMotion = it; changed() })
        }
        SettingsCard("Player controls", "Modern: a floating bar over the picture. Classic: a full-width bar at the bottom.") {
            ComboBox(PlayerStyle.entries.toList(), Appearance.playerStyle, { it.label }, { Appearance.playerStyle = it; changed() }, minWidth = 210.dp)
        }
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
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).background(c.flyout, RoundedCornerShape(5.dp)).border(Dp.Hairline, c.strokeStrong, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 3.dp),
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

/** Settings for the information TMDB adds to title pages, the Home banner and Explore */
@Composable
fun TitleInfoCards() {
    val c = Fluent.colors
    fun changed() = Appearance.save()
    FText("Title information", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp, top = 24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SettingsCard("Title information from TMDB", "Logos and backdrops, cast photos, ratings, reviews, trailers, where a title streams, and the Explore lists. Turn it off and the app never contacts TMDB (extensions' own information is shown). This product uses the TMDB API but is not endorsed or certified by TMDB.") {
            ToggleSwitch(Appearance.tmdbEnabled, { Appearance.tmdbEnabled = it; changed() })
        }
        if (Appearance.tmdbEnabled) SettingsCard("Country", "Decides the age rating and the streaming services shown on title pages and in the Explore filters.") {
            val codes = listOf("") + com.lagradost.desktop.tmdb.Tmdb.regions.keys
            ComboBox(codes, Appearance.tmdbRegion, { if (it.isEmpty()) "Windows region (${com.lagradost.desktop.tmdb.Tmdb.region})" else com.lagradost.desktop.tmdb.Tmdb.regions[it] ?: it }, { Appearance.tmdbRegion = it; changed() }, minWidth = 230.dp)
        }
    }
}

/** The colour theme: a small preview of each, and the glow behind the pages */
@Composable
fun ThemeCards() {
    val c = Fluent.colors
    fun changed() = Appearance.save()
    FText("Theme", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp, top = 20.dp))
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.card, shape).border(Dp.Hairline, c.stroke, shape).padding(16.dp)) {
        FText("Colours", style = Fluent.type.body)
        FText("The accent, the tint of the surfaces and the glow behind the pages. Classic keeps the plain Windows look.", style = Fluent.type.caption, color = c.textSecondary)
        Box(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (p in listOf(Themes.classic) + Themes.all) {
                val selected = Appearance.theme == p.id
                val accent = p.accent ?: Color(0xFF4CC2FF)
                val tile = RoundedCornerShape(FluentShapes.card)
                Column(
                    Modifier.width(132.dp).clip(tile).fluentClickable(rememberInteraction(), true, tile, androidx.compose.ui.semantics.Role.RadioButton) {
                        Appearance.theme = p.id
                        // the theme's own accent shows unless the user picks another one afterwards
                        if (p.accent != null) FluentSettings.chooseAccent(null)
                        changed()
                    },
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(84.dp).clip(tile).background(p.layer)
                            .drawBehind {
                                p.glowA?.let { drawRect(Brush.radialGradient(listOf(it.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.1f), radius = size.width * 0.8f)) }
                                p.glowB?.let { drawRect(Brush.radialGradient(listOf(it.copy(alpha = 0.40f), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.95f), radius = size.width * 0.7f)) }
                            }
                            .border(if (selected) 2.dp else Dp.Hairline, if (selected) accent else c.stroke, tile).padding(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(width = 46.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                            Box(Modifier.size(width = 80.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(p.tint.copy(alpha = 0.55f)))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(3) { Box(Modifier.size(width = 26.dp, height = 28.dp).clip(RoundedCornerShape(5.dp)).background(p.tint.copy(alpha = 0.16f))) }
                            }
                        }
                    }
                    FText(p.name, style = Fluent.type.caption, color = if (selected) c.text else c.textSecondary, modifier = Modifier.padding(top = 6.dp, start = 2.dp), maxLines = 1)
                }
            }
        }
    }
    Box(Modifier.height(3.dp))
    SettingsCard("Wallpaper", if (Appearance.wallpaper.isBlank()) "A picture of your own behind every page, dimmed so the text stays readable." else java.io.File(Appearance.wallpaper).name) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Appearance.wallpaper.isNotBlank()) Button("Remove", { Appearance.wallpaper = ""; changed() }, kind = ButtonKind.Subtle)
            Button("Choose picture…", { pickWallpaper()?.let { Appearance.wallpaper = it; changed() } }, kind = ButtonKind.Standard, icon = Icons.Folder)
        }
    }
    if (Appearance.wallpaper.isNotBlank()) SettingsCard("Wallpaper dimming", "How much of the page colour covers the picture: ${(Appearance.wallpaperDim * 100).toInt()} %") {
        Slider(Appearance.wallpaperDim, { Appearance.wallpaperDim = (Math.round(it * 20f) / 20f) }, Modifier.width(220.dp), valueRange = 0.3f..0.95f, onValueChangeFinished = { changed() })
    }
    SettingsCard("Home banner", "The large rotating banner at the top of Home.") {
        ToggleSwitch(Appearance.homeBanner, { Appearance.homeBanner = it; changed() })
    }
    SettingsCard("Continue watching on Home", "The row of titles you started (the full list is under See all, in History).") {
        ToggleSwitch(Appearance.homeContinue, { Appearance.homeContinue = it; changed() })
    }
    SettingsCard("Colour glow", "Two soft glows of the theme's colours in the corners of every page.") {
        ToggleSwitch(Appearance.themeGlow, { Appearance.themeGlow = it; changed() })
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
