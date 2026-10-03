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
                Box(Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(r)).background(Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.35f)))))
                Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(r)).background(Brush.linearGradient(listOf(Color(0xFF7B5CFA), Color(0xFF2EC5CE)))))
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
