package com.lagradost.desktop.ui.screens.schedule

import com.lagradost.desktop.ui.fluent.hoverEdge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.notify.AiringSchedule
import com.lagradost.desktop.notify.AniListProgress
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
import kotlinx.coroutines.delay

/**
 * The "Notifications" tab: on top the next episodes of what you follow (bell, library, AniList "watching") airing this week as one card with a
 * live countdown and arrows to the others; below, the new episodes of the titles with the bell, each with how far you are (watched / out / total).
 */
internal fun LazyListScope.notifications() {
    item(key = "upcoming") { UpcomingCard() }
    if (UpdateNotices.items.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                Icons.Bell, "No new episodes yet",
                "Turn on the bell on a title page and the new episodes of that title are listed here. They are looked for every few hours while the app is open.",
            ) { Button("Check now", { UpdateNotices.checkNow() }, kind = ButtonKind.Accent, icon = Icons.Refresh, height = 36.dp) }
        }
        return
    }
    item(key = "newHead") { SectionLabel("New episodes", "${UpdateNotices.items.size}") }
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

/** "1d 21h 04m", "3h 12m 09s" under an hour away the seconds count too */
private fun countdown(ms: Long): String {
    if (ms <= 0) return "Airing now"
    val s = ms / 1000
    val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        d > 0 -> "${d}d ${h}h ${"%02d".format(m)}m"
        h > 0 -> "${h}h ${"%02d".format(m)}m ${"%02d".format(sec)}s"
        else -> "${m}m ${"%02d".format(sec)}s"
    }
}

@Composable
private fun SectionLabel(text: String, count: String? = null) {
    val c = Fluent.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FText(com.lagradost.desktop.i18n.Tr.upper(text), style = Fluent.type.caption.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold), color = c.textSecondary, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(c.stroke))
        if (count != null) Pill(count, c.control, c.textSecondary)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        FText(text, style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

/** A thin rounded bar, [part] of it filled */
@Composable
private fun Meter(part: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val c = Fluent.colors
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(c.control)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(part.coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(color))
    }
}

/**
 * The next episode of what you follow, with a countdown that ticks. The line under the card fills as the airing comes closer (over the week),
 * the arrows step through the other upcoming ones. Nothing when none of your titles airs this week.
 */
@Composable
private fun UpcomingCard() {
    val list by produceState<List<AiringSchedule.Airing>>(emptyList()) {
        value = runCatching { AiringSchedule.upcoming(7) }.getOrDefault(emptyList())
            .filter { (it.inList || System.getProperty("cloudstream.upcomingall") != null) && it.airingAt * 1000L > System.currentTimeMillis() - 30 * 60_000L }
            .distinctBy { it.mediaId }
    }
    if (list.isEmpty()) return
    var index by remember { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val i = index.coerceIn(0, list.lastIndex)
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Up next", "${i + 1} / ${list.size}")
        AnimatedContent(list[i], transitionSpec = { fadeIn(FluentMotion.tweenStd(220)) togetherWith fadeOut(FluentMotion.tweenStd(160)) }, label = "upcoming") { a ->
            val left = a.airingAt * 1000L - now
            val progress = AniListProgress.known[a.mediaId]
            val source = rememberInteraction()
            val hovered by source.collectIsHoveredAsState()
            val zoom by animateFloatAsState(if (hovered) 1.06f else 1f, FluentMotion.tweenStd(400), label = "upZoom")
            Box(Modifier.fillMaxWidth().height(170.dp).clip(shape).background(c.card, shape).hoverable(source)
                .fluentClickable(source, true, shape, Role.Button) { Navigator.go(Route.Search(a.title)) }) {
                val scrim = if (c.dark) Color.Black else Color.White
                SoftImage(a.cover, null, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }, alpha = if (hovered) 1f else 0.85f)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(scrim.copy(alpha = 0.92f), scrim.copy(alpha = 0.7f), scrim.copy(alpha = if (hovered) 0.15f else 0.35f)))))
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(138.dp).width(96.dp).clip(RoundedCornerShape(FluentShapes.small)).background(c.control)) {
                        RemoteImage(a.cover, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("UPCOMING · EP ${a.episode}", c.accent, Color.White)
                            a.format?.let { Pill(it.replace('_', ' '), c.control, c.textSecondary) }
                        }
                        FText(a.title, style = Fluent.type.subtitle, maxLines = 1)
                        val whenText = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · hh:mm a")
                            .format(java.time.Instant.ofEpochSecond(a.airingAt).atZone(java.time.ZoneId.systemDefault()))
                        FText(if (progress != null) "Watched ${progress.seen}${progress.total?.let { " of $it" } ?: ""}  ·  airs $whenText" else "Airs $whenText",
                            style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                        Box(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FText("Airing in", color = c.textSecondary, maxLines = 1)
                            FText(countdown(left), style = Fluent.type.bodyLarge.copy(fontWeight = FontWeight.Bold), color = c.accentText, maxLines = 1)
                        }
                    }
                    if (list.size > 1) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(Icons.ChevronUp, { index = (i - 1 + list.size) % list.size }, tooltip = "Previous", kind = ButtonKind.Standard, size = 34.dp, iconSize = 12.dp)
                        IconButton(Icons.ChevronDown, { index = (i + 1) % list.size }, tooltip = "Next", kind = ButtonKind.Standard, size = 34.dp, iconSize = 12.dp)
                    }
                }
                Box(Modifier.fillMaxSize().hairBorder(if (hovered) c.hoverEdge else c.stroke, shape))
            }
        }
        // the "symbolic line": how close the airing is, over the week it can be away
        val week = 7L * 86400_000L
        Meter(1f - ((list[i].airingAt * 1000L - now).toFloat() / week), c.accent, Modifier.fillMaxWidth(), 3.dp)
    }
}

/**
 * A new episode: the poster as the card's background (it comes forward and slowly zooms under the pointer), the title, the episode that came
 * out, how many you have watched of how many and how far behind you are, with a bar of it.
 */
@Composable
private fun NoticeCard(n: UpdateNotices.Notice) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1.01f else 1f, FluentMotion.tweenStd(160), label = "noticeLift")
    val zoom by animateFloatAsState(if (hovered) 1.08f else 1f, FluentMotion.tweenStd(500), label = "noticeZoom")
    val reveal by animateFloatAsState(if (hovered) 1f else 0f, FluentMotion.tweenStd(260), label = "noticeReveal")
    val shape = RoundedCornerShape(FluentShapes.card)
    val edge by animateColorAsState(if (hovered) c.hoverEdge else c.stroke, FluentMotion.tweenStd(160), label = "noticeEdge")
    val p = remember(n.key, AniListProgress.known.size) { UpdateNotices.progress(n) }
    Box(
        Modifier.fillMaxWidth().height(128.dp).graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(shape).background(c.card, shape)
            .hoverable(source)
            .fluentClickable(source, true, shape, Role.Button) {
                UpdateNotices.markRead(n.key)
                Navigator.go(Route.Details(n.url, n.apiName, n.title, n.poster))
            },
    ) {
        // the poster as a backdrop; a scrim of the page's own colour keeps the text readable and thins out under the pointer
        val scrim = if (c.dark) Color.Black else Color.White
        val base = if (n.read) 0.45f else 0.8f
        SoftImage(n.poster, n.posterHeaders, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }, alpha = base + (1f - base) * reveal)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            scrim.copy(alpha = (if (n.read) 0.92f else 0.85f) - 0.08f * reveal), scrim.copy(alpha = 0.62f - 0.2f * reveal), scrim.copy(alpha = 0.3f - 0.25f * reveal),
        ))))
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(104.dp).width(70.dp).clip(RoundedCornerShape(FluentShapes.small)).background(c.control)) {
                RemoteImage(n.poster, n.posterHeaders, null, Modifier.fillMaxSize(), ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    n.episode?.let { Pill("EP $it", if (n.read) c.control else c.accent, if (n.read) c.textSecondary else Color.White) }
                    p.behind?.let { Pill("$it behind", Color(0x33FF6B6B), Color(0xFFFF8A8A)) }
                    if (p.behind == null && p.seen != null && p.out != null && p.seen >= p.out) Pill("Caught up", Color(0x3337C871), Color(0xFF4CD787))
                }
                FText(n.title, style = Fluent.type.bodyStrong, maxLines = 1)
                val watched = p.seen?.let { s -> "Watched $s" + (p.total ?: p.out)?.let { " of $it" }.orEmpty() }
                FText(listOfNotNull(n.episode?.let { "Episode $it aired" } ?: n.text, watched).joinToString("  ·  "), color = c.text, maxLines = 1)
                val of = p.total ?: p.out
                if (p.seen != null && of != null && of > 0) Meter(p.seen.toFloat() / of, c.accent, Modifier.width(220.dp).padding(vertical = 2.dp))
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
