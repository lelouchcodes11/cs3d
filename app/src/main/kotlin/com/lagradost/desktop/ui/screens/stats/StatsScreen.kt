package com.lagradost.desktop.ui.screens.stats

import com.lagradost.desktop.ui.fluent.hairBorder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.stats.WatchLog
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.EmptyState
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.PageHeader
import com.lagradost.desktop.ui.fluent.smoothWheel
import com.lagradost.desktop.ui.shell.TopBarHeight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private fun duration(ms: Long): String {
    val m = ms / 60_000L
    return when {
        m < 1 -> if (ms > 0) "< 1 min" else "0 min"
        m < 60 -> "$m min"
        m < 100 * 60 -> "${m / 60} h ${m % 60} min"
        else -> "${m / 60} h"
    }
}

private fun langName(tag: String?): String? {
    val t = tag?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji(t) ?: runCatching { Locale.forLanguageTag(t).displayLanguage.takeIf { it.isNotBlank() } }.getOrNull() ?: t
}

private class Totals(
    val allMs: Long, val loggedMs: Long, val todayMs: Long, val weekMs: Long, val monthMs: Long, val activeDays: Int, val streak: Int, val titles: Int,
    val byType: List<Pair<String, Long>>, val genres: List<Pair<String, Long>>, val studios: List<Pair<String, Long>>, val people: List<Pair<String, Long>>,
    val providers: List<Pair<String, Long>>, val audio: List<Pair<String, Long>>, val subs: List<Pair<String, Long>>, val top: List<Pair<WatchLog.Title, Long>>,
    val rewatched: List<WatchLog.Title>, val weekdays: LongArray, val perDay: Map<LocalDate, Long>,
)

private fun compute(): Totals {
    val snap = WatchLog.snapshot()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val perDay = HashMap<LocalDate, Long>()
    val perTitle = HashMap<String, Long>()
    val weekdays = LongArray(7)
    for ((day, map) in snap.days) {
        val d = runCatching { LocalDate.parse(day) }.getOrNull() ?: continue
        val sum = map.values.sum()
        perDay[d] = sum
        weekdays[d.dayOfWeek.value - 1] += sum
        for ((k, ms) in map) perTitle[k] = (perTitle[k] ?: 0L) + ms
    }
    val logged = perDay.values.sum()
    fun inLast(days: Long) = perDay.filterKeys { !it.isAfter(today) && it.isAfter(today.minusDays(days)) }.values.sum()
    // consecutive days with something watched, ending today (or yesterday: the day is not over)
    var streak = 0
    var d = if ((perDay[today] ?: 0L) > 0) today else today.minusDays(1)
    while ((perDay[d] ?: 0L) > 0) { streak++; d = d.minusDays(1) }

    fun weighted(select: (WatchLog.Title) -> List<String>): List<Pair<String, Long>> {
        val m = HashMap<String, Long>()
        for ((k, ms) in perTitle) {
            val t = snap.titles[k] ?: continue
            for (v in select(t)) if (v.isNotBlank()) m[v] = (m[v] ?: 0L) + ms
        }
        return m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val legacy = runCatching { DataStoreHelper.getAllViewPositions().sumOf { it.position.coerceAtLeast(0L) } }.getOrDefault(0L)
    return Totals(
        allMs = maxOf(logged, legacy), loggedMs = logged, todayMs = perDay[today] ?: 0L, weekMs = inLast(7), monthMs = inLast(30),
        activeDays = perDay.count { it.value > 0 }, streak = streak, titles = perTitle.size,
        byType = weighted { listOf(it.type) }, genres = weighted { it.genres }, studios = weighted { it.studios }, people = weighted { it.people },
        providers = weighted { listOf(it.api) },
        audio = weighted { listOfNotNull(langName(it.audioLang)) }, subs = weighted { listOfNotNull(langName(it.subLang)) },
        top = perTitle.entries.sortedByDescending { it.value }.mapNotNull { e -> snap.titles[e.key]?.let { it to e.value } }.take(10),
        rewatched = snap.titles.values.filter { it.rewatches > 0 }.sortedByDescending { it.rewatches }.take(8),
        weekdays = weekdays, perDay = perDay,
    )
}

@Composable
fun StatsScreen() {
    var version by remember { mutableStateOf(0) }
    val t by produceState<Totals?>(null, version) { value = runCatching { compute() }.getOrNull() }
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        FluentScrollbar(state, TopBarHeight)
        val data = t
        LazyColumn(
            state = state, modifier = Modifier.fillMaxSize().smoothWheel(state),
            contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = TopBarHeight + 20.dp, bottom = 40.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "head") {
                PageHeader(
                    "Stats", subtitle = "What you watched, in this app",
                    trailing = {
                        if (data != null && data.loggedMs > 0) Button("Reset", {
                            Overlays.message("Reset the stats?", "The diary of what you watched (this page) is cleared. Your library, history and tracker lists stay.", primary = "Reset", onPrimary = { WatchLog.reset(); version++ })
                        }, kind = ButtonKind.Standard, icon = Icons.Delete, height = 34.dp)
                    },
                )
            }
            if (data == null) return@LazyColumn
            if (data.allMs <= 0) {
                item(key = "empty") { EmptyState(Icons.Chart, "Nothing to count yet", "Watch something and its time, genres and studios show up here.") }
                return@LazyColumn
            }
            item(key = "tiles") {
                val wide = maxWidth >= 760.dp
                val tiles = listOf(
                    Triple("All time", duration(data.allMs), if (data.loggedMs < data.allMs) "includes what you watched before the diary" else "of video"),
                    Triple("Last 7 days", duration(data.weekMs), "${duration(data.todayMs)} today"),
                    Triple("Last 30 days", duration(data.monthMs), "${data.activeDays} days watched"),
                    Triple("Streak", "${data.streak} ${if (data.streak == 1) "day" else "days"}", "${data.titles} titles in the diary"),
                )
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { tiles.forEach { (a, b, c) -> Box(Modifier.weight(1f)) { Tile(a, b, c) } } }
                else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { tiles.chunked(2).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { r.forEach { (a, b, c) -> Box(Modifier.weight(1f)) { Tile(a, b, c) } } } } }
            }
            item(key = "heat") { Card("Activity") { Heatmap(data.perDay, data.weekdays) } }
            item(key = "split") {
                val wide = maxWidth >= 900.dp
                val left = @Composable { Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (data.byType.isNotEmpty()) Card("By type") { Bars(data.byType) }
                    if (data.genres.isNotEmpty()) Card("Genres") { Bars(data.genres.take(8)) }
                    if (data.providers.isNotEmpty()) Card("Extensions") { Bars(data.providers.take(6)) }
                } }
                val right = @Composable { Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (data.top.isNotEmpty()) Card("Most watched") { data.top.forEachIndexed { i, (title, ms) -> TitleRow(i + 1, title, duration(ms)) } }
                    if (data.studios.isNotEmpty()) Card("Studios") { Bars(data.studios.take(8)) }
                    if (data.people.isNotEmpty()) Card("Cast you watch the most") { Bars(data.people.take(8)) }
                    if (data.audio.isNotEmpty() || data.subs.isNotEmpty()) Card("Languages") {
                        if (data.audio.isNotEmpty()) { FText("Audio", style = Fluent.type.caption, color = Fluent.colors.textTertiary); Bars(data.audio.take(5)) }
                        if (data.subs.isNotEmpty()) { FText("Subtitles", style = Fluent.type.caption, color = Fluent.colors.textTertiary, modifier = Modifier.padding(top = 8.dp)); Bars(data.subs.take(5)) }
                    }
                    if (data.rewatched.isNotEmpty()) Card("Watched again") { data.rewatched.forEach { r -> TitleRow(null, r, "${r.rewatches}× again") } }
                } }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.weight(1f)) { left() }
                    Box(Modifier.weight(1f)) { right() }
                } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { left(); right() }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FText(label, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
        FText(value, style = Fluent.type.title, maxLines = 1)
        FText(sub, style = Fluent.type.caption, color = c.textTertiary, maxLines = 2)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FText(title, style = Fluent.type.bodyStrong, maxLines = 1)
        content()
    }
}

/** Horizontal bars: the longest is the full width, the time sits at the right */
@Composable
private fun Bars(entries: List<Pair<String, Long>>) {
    val c = Fluent.colors
    val max = (entries.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        entries.forEach { (name, ms) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(name, Modifier.weight(1f), maxLines = 1)
                    FText(duration(ms), style = Fluent.type.caption, color = c.textSecondary, maxLines = 1, softWrap = false)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.control)) {
                    Box(Modifier.fillMaxWidth((ms.toFloat() / max).coerceIn(0.02f, 1f)).height(6.dp).background(c.accent, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

@Composable
private fun TitleRow(rank: Int?, t: WatchLog.Title, detail: String) {
    val c = Fluent.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rank != null) FText("$rank", Modifier.width(18.dp), style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        Box(Modifier.height(54.dp).width(37.dp).clip(RoundedCornerShape(FluentShapes.small)).background(c.control)) {
            RemoteImage(t.poster, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            FText(t.name, maxLines = 1)
            FText(listOfNotNull(t.type.takeIf { it.isNotBlank() }, t.api.takeIf { it.isNotBlank() }).joinToString("  ·  "), style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
        FText(detail, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1, softWrap = false)
    }
}

/** 26 weeks, one column per week (Monday on top), darker accent = more time that day; below: which weekday has the most */
@Composable
private fun Heatmap(perDay: Map<LocalDate, Long>, weekdays: LongArray) {
    val c = Fluent.colors
    val today = LocalDate.now(ZoneId.systemDefault())
    val max = (perDay.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val accent = c.accent
    val empty = c.control
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // as many weeks as fit (a year at most)
    val weeks = ((maxWidth + 3.dp) / (15.dp + 3.dp)).toInt().coerceIn(12, 53)
    val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((weeks - 1).toLong())
    Canvas(Modifier.fillMaxWidth().height((15 + 3).dp * 7)) {
        val gap = 3.dp.toPx()
        val cell = 15.dp.toPx()
        for (w in 0 until weeks) for (d in 0 until 7) {
            val day = start.plusWeeks(w.toLong()).plusDays(d.toLong())
            if (day.isAfter(today)) continue
            val ms = perDay[day] ?: 0L
            val k = if (ms <= 0) 0f else 0.25f + 0.75f * (ms.toFloat() / max).coerceIn(0f, 1f)
            val color = if (ms <= 0) empty else accent.copy(alpha = k)
            drawRoundRect(color, Offset(w * (cell + gap), d * (cell + gap)), Size(cell, cell), CornerRadius(3.dp.toPx()))
        }
    }
    }
    val best = weekdays.indices.maxByOrNull { weekdays[it] }?.takeIf { weekdays[it] > 0 }
    if (best != null) {
        FText("You watch the most on ${DayOfWeek.of(best + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())}s", style = Fluent.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}
