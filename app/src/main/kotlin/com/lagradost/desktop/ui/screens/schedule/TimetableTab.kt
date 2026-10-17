package com.lagradost.desktop.ui.screens.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.notify.AiringSchedule
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Badge
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.EmptyState
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.primaryBorder
import com.lagradost.desktop.ui.fluent.primaryContent
import com.lagradost.desktop.ui.fluent.primaryFill
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.shimmer
import com.lagradost.desktop.ui.shell.TopBarHeight
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val longDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

private val TimeColumn = 92.dp
private val RailColumn = 26.dp
private val CardMin = 330.dp

/** What the timetable lists: the titles of the library, the ones many people follow, or everything that airs */
internal enum class Show(val label: String, val glyph: String) {
    Mine("My list", Icons.Library), Popular("Popular", Icons.Star), All("Everything", Icons.Globe);

    fun keeps(a: AiringSchedule.Airing): Boolean = when (this) {
        Mine -> a.inList
        // the shorts and the little-known ONAs are most of a busy day: people who follow, or many of them, are what "popular" means
        Popular -> a.inList || (a.popularity >= 4000 && a.format != "TV_SHORT")
        All -> true
    }
}

/** What the timetable page remembers while it is open: the loaded week, the day and the filter that are chosen, the clock */
internal class TimetableState(val first: LocalDate, val zone: ZoneId) {
    var selected by mutableStateOf(0)
    internal var show by mutableStateOf<Show?>(null)
    var refresh by mutableStateOf(0)
    var loaded by mutableStateOf<Result<List<AiringSchedule.Airing>>?>(null)
    var nowMs by mutableLongStateOf(System.currentTimeMillis())
    var showAired by mutableStateOf(false)

    /** The filter in use: the viewer's choice, else the library's titles when any of them airs this week, else everything */
    internal fun activeShow(): Show = show ?: if (loaded?.getOrNull().orEmpty().any { it.inList }) Show.Mine else Show.All
}

@Composable
internal fun rememberTimetable(): TimetableState {
    val zone = remember { ZoneId.systemDefault() }
    val state = remember { TimetableState(LocalDate.now(zone), zone) }
    LaunchedEffect(state.refresh) {
        state.loaded = null
        state.loaded = runCatching { AiringSchedule.timetable(state.first, 7, force = state.refresh > 0) }
    }
    // the clock moves: "in 12 min" and the now line stay right while the page is open
    LaunchedEffect(Unit) { while (true) { delay(30_000); state.nowMs = System.currentTimeMillis() } }
    return state
}

/**
 * The anime timetable: seven day buttons (today and the six days after it, with how many episodes each has) that stay at the top while the
 * page scrolls, and under them the chosen day as a timeline, hour by hour, in the viewer's own time. Today has a "now" line, what is still to
 * come under it and what already aired above it (folded away). AniList's airing schedule; the titles of the library are marked.
 */
internal fun LazyListScope.timetable(t: TimetableState, width: Dp, list: LazyListState) {
    val result = t.loaded
    val show = t.activeShow()
    val shown = result?.getOrNull().orEmpty().filter { show.keeps(it) }
        .sortedWith(compareBy<AiringSchedule.Airing>({ it.airingAt }, { -it.popularity }))
    val byDay = shown.groupBy { Instant.ofEpochSecond(it.airingAt).atZone(t.zone).toLocalDate() }

    stickyHeader(key = "days") { StickyDays(t, list) { d -> if (result == null) null else byDay[d]?.size ?: 0 } }

    val day = t.first.plusDays(t.selected.toLong())
    val list = byDay[day].orEmpty()
    when {
        result == null -> {
            item(key = "loading") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(FluentShapes.card)).shimmer()) }
                }
            }
        }
        result.isFailure -> item(key = "failed") {
            EmptyState(Icons.Warning, "The timetable could not be loaded", result.exceptionOrNull()?.message ?: "Check the connection and try again.") {
                Button("Try again", { t.refresh++ }, kind = ButtonKind.Accent, icon = Icons.Refresh, height = 36.dp)
            }
        }
        else -> {
            item(key = "dayhead") { DayHeader(t, day, list.size, show) }
            if (show == Show.Mine && result.getOrNull().orEmpty().none { it.inList }) {
                item(key = "nolist") {
                    FText("Nothing in your library airs this week. \"My list\" needs titles you saved, subscribed to or favourited.", color = Fluent.colors.textSecondary)
                }
            }
            if (list.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Calendar, if (show == Show.Mine) "Nothing from your list airs ${dayWord(day, t.first)}" else "Nothing is scheduled ${dayWord(day, t.first)}",
                        if (show != Show.All) "Switch to \"Everything\" to see every show." else "Try another day.",
                    )
                }
            } else timeline(t, list, day == t.first, width)
        }
    }
}

private fun dayWord(day: LocalDate, first: LocalDate): String = when (day) {
    first -> "today"
    first.plusDays(1) -> "tomorrow"
    else -> "on " + day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

private fun dayTitle(day: LocalDate, first: LocalDate): String = when (day) {
    first -> "Today"
    first.plusDays(1) -> "Tomorrow"
    else -> day.format(longDay)
}

// ---------------------------------------------------------------------------------------------------------------------------------
// the seven days

/**
 * The day buttons; they stay at the top while the day below them scrolls. A lazy list sticks a header at the very top of its window, which is
 * behind the floating bar of the app, so once the header is stuck it takes the bar's height as room above it.
 */
@Composable
private fun StickyDays(t: TimetableState, list: LazyListState, count: (LocalDate) -> Int?) {
    val bg = Fluent.colors.bg
    // the header is item 2 (title, tabs, days): scrolled past its own place = stuck
    val stuck by remember { derivedStateOf { list.firstVisibleItemIndex > 2 || (list.firstVisibleItemIndex == 2 && list.firstVisibleItemScrollOffset > 0) } }
    val top by animateDpAsState(if (stuck) TopBarHeight + 6.dp else 0.dp, FluentMotion.tweenStd(160), label = "daysTop")
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to bg, 0.86f to bg, 1f to bg.copy(alpha = 0f))).padding(top = top, bottom = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until 7) {
                val day = t.first.plusDays(i.toLong())
                Box(Modifier.weight(1f)) { DayButton(day, i == 0, i == t.selected, count(day)) { t.selected = i } }
            }
        }
    }
}

@Composable
private fun DayButton(day: LocalDate, today: Boolean, on: Boolean, count: Int?, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    val lift by animateFloatAsState(if (hovered && !on) 1.03f else 1f, FluentMotion.tweenStd(140), label = "dayLift")
    val fg = if (on) c.primaryContent() else c.text
    val soft = if (on) c.primaryContent().copy(alpha = 0.7f) else c.textSecondary
    Column(
        Modifier.fillMaxWidth().height(82.dp).graphicsLayer { scaleX = lift; scaleY = lift }
            .background(if (on) c.primaryFill(hovered, false) else SolidColor(if (hovered) c.cardHover else c.card), shape)
            .hairBorder(if (on) c.primaryBorder(hovered) else c.stroke, shape)
            .hoverable(source)
            .fluentClickable(source, true, shape, Role.Tab, onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        FText(
            if (today) "TODAY" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            style = Fluent.type.caption.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold), color = soft, maxLines = 1, softWrap = false,
        )
        FText(day.dayOfMonth.toString(), style = Fluent.type.title.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
        FText(
            when (count) { null -> " "; 0 -> "none"; 1 -> "1 episode"; else -> "$count episodes" },
            style = Fluent.type.caption, color = soft, maxLines = 1, softWrap = false,
        )
    }
}

@Composable
private fun DayHeader(t: TimetableState, day: LocalDate, count: Int, show: Show) {
    val c = Fluent.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            FText(dayTitle(day, t.first), style = Fluent.type.subtitle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            val offset = t.zone.rules.getOffset(Instant.now()).id.let { if (it == "Z") "UTC" else "UTC$it" }
            FText(
                (if (day == t.first) day.format(longDay) + "  ·  " else "") + "$count ${if (count == 1) "episode" else "episodes"}  ·  your time ($offset)",
                style = Fluent.type.caption, color = c.textTertiary, maxLines = 1,
            )
        }
        Show.entries.forEach { s -> Chip(s.label, show == s, { t.show = s }, icon = s.glyph) }
        IconButton(Icons.Refresh, { t.refresh++ }, tooltip = "Reload the timetable", size = 32.dp, iconSize = 14.dp)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// one day, hour by hour

private fun LazyListScope.timeline(t: TimetableState, list: List<AiringSchedule.Airing>, today: Boolean, width: Dp) {
    val nowSec = t.nowMs / 1000L
    val aired = if (today) list.filter { it.airingAt <= nowSec } else emptyList()
    val coming = if (today) list.filter { it.airingAt > nowSec } else list
    val perRow = ((width - TimeColumn - RailColumn + 12.dp) / (CardMin + 12.dp)).toInt().coerceAtLeast(1)
    if (aired.isNotEmpty()) {
        item(key = "fold") { AiredFold(aired.size, t.showAired) { t.showAired = !t.showAired } }
        if (t.showAired) slots(aired).forEach { (time, items) -> item(key = "aired-$time") { Slot(time, items, true, nowSec, perRow, last = false) } }
    }
    if (today) item(key = "now") { NowLine(t.nowMs) }
    val rest = slots(coming)
    if (coming.isEmpty() && today) {
        item(key = "done") {
            FText("That was the last episode of today.", color = Fluent.colors.textSecondary, modifier = Modifier.padding(start = TimeColumn + RailColumn, top = 6.dp, bottom = 10.dp))
        }
    }
    rest.forEachIndexed { i, (time, items) -> item(key = "slot-$time") { Slot(time, items, false, nowSec, perRow, last = i == rest.lastIndex) } }
}

/** The episodes of one moment (the same minute) together, in time order */
private fun slots(list: List<AiringSchedule.Airing>): List<Pair<Long, List<AiringSchedule.Airing>>> =
    list.groupBy { it.airingAt / 60L }.toSortedMap().map { (minute, items) -> minute * 60L to items.sortedByDescending { it.popularity } }

@Composable
private fun AiredFold(count: Int, open: Boolean, toggle: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(start = TimeColumn + RailColumn).padding(bottom = 10.dp).clip(shape).background(if (hovered) c.cardHover else c.card, shape).hairBorder(c.stroke, shape)
            .hoverable(source).fluentClickable(source, true, shape, Role.Button, toggle).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Check, size = 13.dp, tint = c.textSecondary)
        FText(if (open) "Hide the $count that aired earlier today" else "$count aired earlier today", color = c.textSecondary, maxLines = 1, softWrap = false)
        Icon(if (open) Icons.ChevronUpSmall else Icons.ChevronDownSmall, size = 10.dp, tint = c.textTertiary)
    }
}

@Composable
private fun NowLine(nowMs: Long) {
    val c = Fluent.colors
    val time = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).format(timeFormat)
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(TimeColumn)) {
            FText("NOW", style = Fluent.type.caption.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold), color = c.accentText, maxLines = 1)
            FText(time, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
        Box(Modifier.width(RailColumn), contentAlignment = Alignment.Center) { Box(Modifier.size(11.dp).background(c.accent, CircleShape)) }
        Box(Modifier.weight(1f).height(2.dp).background(c.accent.copy(alpha = 0.55f), RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun Slot(time: Long, items: List<AiringSchedule.Airing>, aired: Boolean, nowSec: Long, perRow: Int, last: Boolean) {
    val c = Fluent.colors
    val label = Instant.ofEpochSecond(time).atZone(ZoneId.systemDefault()).format(timeFormat)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(TimeColumn).padding(top = 2.dp)) {
            FText(label, style = Fluent.type.bodyStrong, color = if (aired) c.textSecondary else c.text, maxLines = 1, softWrap = false)
            if (!aired) FText(countdown(time - nowSec), style = Fluent.type.caption, color = c.accentText, maxLines = 1, softWrap = false)
        }
        // the rail: a dot at the time, a line down to the next one
        val line = c.stroke
        val dot = if (aired) c.textTertiary else c.text
        Box(
            Modifier.width(RailColumn).fillMaxHeight().drawBehind {
                val x = size.width / 2
                if (!last) drawLine(line, Offset(x, 14.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(dot, 4.5.dp.toPx(), Offset(x, 9.dp.toPx()))
            },
        )
        Column(Modifier.weight(1f).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { a -> Box(Modifier.weight(1f)) { EpisodeCard(a, aired) } }
                    repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun countdown(seconds: Long): String {
    if (seconds <= 0) return "now"
    val m = seconds / 60
    return when {
        m < 1 -> "in under a minute"
        m < 60 -> "in $m min"
        m < 24 * 60 -> "in ${m / 60} h" + if (m % 60 != 0L) " ${m % 60} min" else ""
        else -> "in ${m / (24 * 60)} d ${(m % (24 * 60)) / 60} h"
    }
}

private fun formatName(format: String?): String? = when (format) {
    "MOVIE" -> "Movie"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "SPECIAL" -> "Special"
    "TV_SHORT" -> "Short"
    else -> null
}

@Composable
private fun EpisodeCard(a: AiringSchedule.Airing, aired: Boolean) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    val edge by animateColorAsState(if (hovered) c.accent.copy(alpha = 0.55f) else c.stroke, FluentMotion.tweenStd(140), label = "epEdge")
    val lift by animateFloatAsState(if (hovered) 1.012f else 1f, FluentMotion.tweenStd(140), label = "epLift")
    Row(
        Modifier.fillMaxWidth().height(92.dp).graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(shape).background(if (hovered) c.cardHover else c.card, shape)
            .hairBorder(edge, shape)
            .hoverable(source)
            .fluentClickable(source, true, shape, Role.Button) { Navigator.search(a.title) }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.height(72.dp).width(50.dp).clip(RoundedCornerShape(FluentShapes.small)).background(c.control).alpha(if (aired) 0.6f else 1f)) {
            RemoteImage(a.cover, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FText(a.title, style = Fluent.type.bodyStrong, color = if (aired) c.textSecondary else c.text, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FText("Episode ${a.episode}", color = c.textSecondary, maxLines = 1, softWrap = false)
                formatName(a.format)?.let { FText(it, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1, softWrap = false) }
                a.score?.let { FText("★ ${"%.1f".format(it / 10.0)}", style = Fluent.type.caption, color = c.textTertiary, maxLines = 1, softWrap = false) }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (a.inList) Badge("In your list", accent = true)
            if (a.episode == 1) Badge("Premiere")
        }
    }
}
