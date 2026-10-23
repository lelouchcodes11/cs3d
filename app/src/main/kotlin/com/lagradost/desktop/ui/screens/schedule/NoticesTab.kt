package com.lagradost.desktop.ui.screens.schedule

import com.lagradost.desktop.ui.fluent.hoverEdge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.notify.UpdateNotices
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.components.SoftImage
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.EmptyState
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** The "Notifications" tab: the new episodes of the titles the viewer follows (the bell on a title page) */
internal fun LazyListScope.notifications() {
    if (UpdateNotices.items.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                Icons.Bell, "No new episodes yet",
                "Turn on the bell on a title page and the new episodes of that title are listed here. They are looked for every few hours while the app is open.",
            ) { Button("Check now", { UpdateNotices.checkNow() }, kind = ButtonKind.Accent, icon = Icons.Refresh, height = 36.dp) }
        }
        return
    }
    items(UpdateNotices.items.toList(), key = { it.key }) { n -> NoticeCard(n) }
}

private fun ago(time: Long): String {
    val m = ((System.currentTimeMillis() - time) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        m < 48 * 60 -> "yesterday"
        else -> "${m / (24 * 60)} days ago"
    }
}

/** A card with the poster as its soft background; it rises a little under the pointer and has the title, the episode and when it was found */
@Composable
private fun NoticeCard(n: UpdateNotices.Notice) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1.012f else 1f, FluentMotion.tweenStd(160), label = "noticeLift")
    val shape = RoundedCornerShape(FluentShapes.card)
    val edge by animateColorAsState(if (hovered) c.hoverEdge else c.stroke, FluentMotion.tweenStd(160), label = "noticeEdge")
    Box(
        Modifier.fillMaxWidth().height(112.dp).graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(shape).background(c.card, shape)
            .hoverable(source)
            .fluentClickable(source, true, shape, Role.Button) {
                UpdateNotices.markRead(n.key)
                Navigator.go(Route.Details(n.url, n.apiName, n.title, n.poster))
            },
    ) {
        // the poster as a soft backdrop; a scrim of the page's own colour keeps the text readable (c.card itself is a faint overlay, not a colour)
        val scrim = if (c.dark) Color.Black else Color.White
        SoftImage(n.poster, n.posterHeaders, Modifier.fillMaxSize(), alpha = if (n.read) 0.5f else 0.9f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(scrim.copy(alpha = if (n.read) 0.9f else 0.82f), scrim.copy(alpha = 0.6f), scrim.copy(alpha = 0.3f)))))
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(88.dp).width(60.dp).clip(RoundedCornerShape(FluentShapes.small)).background(c.control)) {
                RemoteImage(n.poster, n.posterHeaders, null, Modifier.fillMaxSize(), ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FText(n.title, style = Fluent.type.bodyStrong, maxLines = 1)
                FText(n.text, color = c.text, maxLines = 1)
                FText("${ago(n.time)}  ·  ${n.apiName}", style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
            }
            if (!n.read) Box(Modifier.size(9.dp).background(c.accent, CircleShape))
            Box(Modifier.size(34.dp)) {
                if (hovered) IconButton(Icons.Close, { UpdateNotices.remove(n.key) }, tooltip = "Remove", size = 32.dp, iconSize = 12.dp)
            }
        }
        Box(Modifier.fillMaxSize().hairBorder(edge, shape))
    }
}
