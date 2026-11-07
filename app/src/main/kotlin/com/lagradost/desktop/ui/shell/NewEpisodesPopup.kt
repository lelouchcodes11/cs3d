package com.lagradost.desktop.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.notify.UpdateNotices
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.components.SoftImage
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.NavPosition
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.rememberInteraction
import kotlinx.coroutines.delay

/**
 * The small card in the bottom right corner when new episodes are there: at the start for the unread ones of an earlier run, and when a check
 * finds one. "EP 6 · Overgeared · Episode 6 aired · +2 more"; a click opens the Schedule's notifications, it leaves on its own after a while
 * (not while the pointer is on it).
 */
@Composable
internal fun NewEpisodesPopup() {
    val list = UpdateNotices.popup
    val first = list.firstOrNull()
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = first != null
    // the last card stays drawn while it slides out
    val last = remember { arrayOfNulls<UpdateNotices.Notice>(1) }
    if (first != null) last[0] = first
    val n = first ?: last[0] ?: return
    val more = (list.size - 1).coerceAtLeast(0)
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(first?.key, hovered) {
        if (first == null || hovered) return@LaunchedEffect
        delay(12_000)
        UpdateNotices.popup = emptyList()
    }
    val dockRoom = if (Appearance.navPosition == NavPosition.Bottom) 84.dp else 0.dp
    Box(Modifier.fillMaxSize().padding(end = 24.dp, bottom = 24.dp + dockRoom), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            shown,
            enter = fadeIn(FluentMotion.tweenIn(220)) + slideInHorizontally(FluentMotion.tweenIn(340)) { it / 2 },
            exit = fadeOut(FluentMotion.tweenStd(180)) + slideOutHorizontally(FluentMotion.tweenStd(240)) { it / 2 },
        ) {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier.width(340.dp).height(96.dp).shadow(22.dp, shape, clip = false).clip(shape).background(c.flyout, shape)
                    .hoverable(source)
                    .fluentClickable(source, true, shape, Role.Button) {
                        UpdateNotices.popup = emptyList()
                        Navigator.go(Route.Schedule)
                    },
            ) {
                val scrim = if (c.dark) Color.Black else Color.White
                SoftImage(n.poster, n.posterHeaders, Modifier.fillMaxSize(), alpha = 0.55f)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(scrim.copy(alpha = 0.55f), scrim.copy(alpha = 0.85f)))))
                Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(76.dp).width(52.dp).clip(RoundedCornerShape(8.dp)).background(c.control)) {
                        RemoteImage(n.poster, n.posterHeaders, null, Modifier.fillMaxSize(), ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 7.dp, vertical = 1.dp)) {
                                FText(n.episode?.let { "EP $it" } ?: "NEW", style = Fluent.type.caption.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
                            }
                            FText("New episode", style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                        }
                        FText(n.title, style = Fluent.type.bodyStrong, maxLines = 1)
                        FText(n.episode?.let { "Episode $it aired" } ?: n.text, style = Fluent.type.caption, color = c.text, maxLines = 1)
                        if (more > 0) FText("+$more more", style = Fluent.type.caption, color = c.accentText, maxLines = 1)
                    }
                    Box(Modifier.size(28.dp).align(Alignment.Top)) {
                        IconButton(Icons.Close, { UpdateNotices.popup = emptyList() }, tooltip = "Close", size = 28.dp, iconSize = 10.dp)
                    }
                }
                Box(Modifier.fillMaxSize().hairBorder(c.strokeStrong.copy(alpha = 0.4f), shape))
            }
        }
    }
}
