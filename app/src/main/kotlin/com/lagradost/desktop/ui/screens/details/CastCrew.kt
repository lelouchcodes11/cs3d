package com.lagradost.desktop.ui.screens.details

import com.lagradost.desktop.ui.fluent.hairBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ActorData
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.tmdb.Tmdb
import com.lagradost.desktop.tmdb.TmdbCrew
import com.lagradost.desktop.tmdb.TmdbInfo
import com.lagradost.desktop.tmdb.TmdbPerson
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.EmptyState
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.PageHeader
import com.lagradost.desktop.ui.fluent.Shelf
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.smoothWheel
import com.lagradost.desktop.ui.shell.TopBarHeight

/** One person as a card: [badge] is the small label over the photo for the crew ("DIRECTOR"), [role] what they played or did */
internal class CastEntry(val id: Int?, val name: String, val image: String?, val role: String?, val badge: String? = null)

private val CastTileWidth = 140.dp

/** The headings of the crew on the cast and crew pages, in the order they are shown (the group of [TmdbCrew], its title, the label on a card) */
private class CrewHeading(val group: String, val title: String, val badge: String)

private val CrewHeadings = listOf(
    CrewHeading("Directors", "Directors", "DIRECTOR"), CrewHeading("Creators", "Created by", "CREATOR"), CrewHeading("Writers", "Writers", "WRITER"),
    CrewHeading("Producers", "Producers", "PRODUCER"), CrewHeading("Music", "Music", "MUSIC"), CrewHeading("Cinematography", "Cinematography", "CAMERA"),
    CrewHeading("Editing", "Editing", "EDITOR"), CrewHeading("Design and animation", "Design and animation", "DESIGN"),
)

/** The cast and crew of a title or of one of its seasons */
private class Credits(val cast: List<TmdbPerson>, val crew: List<TmdbCrew>, val creators: List<Pair<Int, String>>)

private fun TmdbInfo.allCredits() = Credits(cast, crew, if (isMovie) emptyList() else directors)

/** Credits of [season] (null: the whole title); the title's own when TMDB has none for that season */
private suspend fun creditsOf(info: TmdbInfo, season: Int?): Credits {
    if (season == null || info.isMovie) return info.allCredits()
    val s = Tmdb.seasonCredits(info.id, season) ?: return info.allCredits()
    // a season has no creators of its own: the series' ones stay
    return Credits(s.first, s.second, info.directors)
}

private fun seasonLabel(n: Int) = if (n < 0) "All seasons" else if (n == 0) "Specials" else "Season $n"

private fun badgeOf(job: String, heading: CrewHeading) = if (heading.group == "Producers" && job.contains("Executive", true)) "EXEC PRODUCER" else heading.badge

private fun Credits.entries(heading: CrewHeading): List<CastEntry> {
    val people = crew.filter { it.group == heading.group }.map { CastEntry(it.id, it.name, it.image, it.job, badgeOf(it.job, heading)) }
    return if (heading.group == "Creators") people + creators.filter { (id, _) -> people.none { it.id == id } }.map { (id, name) -> CastEntry(id, name, null, "Creator", heading.badge) } else people
}

private fun Credits.castEntries() = cast.map { CastEntry(it.id, it.name, it.image, it.role) }

private fun openPerson(p: CastEntry) { p.id?.let { Navigator.go(Route.Person(it, p.name, p.image)) } }

/**
 * The Cast & crew tab: the title, a season picker and "View all" over a row of cast cards, then the crew (directors, creators, writers, producers, each card tagged with its job) in a second row;
 * "View all" (or the last card of a row) opens the whole list. Without TMDB the extension's cast is the one row.
 */
@Composable
fun CastAndCrewTab(info: TmdbInfo?, extensionCast: List<ActorData>, actorsText: String?, mediaTitle: String, seasonNumbers: List<Int>) {
    val c = Fluent.colors
    var season by remember(info?.id) { mutableStateOf<Int?>(null) }
    val credits by produceState(info?.allCredits(), info?.id, season) { info?.let { value = creditsOf(it, season) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val shown = credits
        val cast = shown?.castEntries() ?: extensionCast.take(60).map { CastEntry(null, it.actor.name, it.actor.image, it.roleString) }
        // the crew in one row, like Ayu's app: directors, creators, writers and producers, each card tagged with what the person did; one card
        // per person (a director who also wrote it has both jobs under the name)
        val crewRow = shown?.let { cr ->
            listOf(1, 0, 2, 3).flatMap { cr.entries(CrewHeadings[it]).take(4) }.groupBy { it.id ?: it.name }.values
                .map { same -> CastEntry(same[0].id, same[0].name, same.firstNotNullOfOrNull { it.image }, same.mapNotNull { it.role }.distinct().take(2).joinToString(", "), same[0].badge) }
        }.orEmpty()
        val total = cast.size + (shown?.crew?.size ?: 0)
        val canOpenAll = info != null && total > 8
        val openAll = { info?.let { Navigator.go(Route.FullCast(mediaTitle, it.id, it.isMovie, seasonNumbers, season)) }; Unit }
        // header
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FText(if (season != null) (if (season == 0) "Specials cast & crew" else "Season $season cast & crew") else "Cast & crew", style = Fluent.type.subtitle)
            if (info != null && seasonNumbers.size >= 2) ComboBox(
                listOf(-1) + seasonNumbers, season ?: -1, { seasonLabel(it) }, { season = it.takeIf { n -> n >= 0 } }, minWidth = 150.dp, height = 32.dp,
            )
            Box(Modifier.weight(1f))
            if (canOpenAll) Button("View all", openAll, kind = ButtonKind.Subtle, icon = Icons.ChevronRightSmall)
        }
        if (cast.isNotEmpty()) PeopleShelf(cast.take(14), if (canOpenAll) openAll else null, cast.size)
        else if (shown == null && info != null) FText("Loading…", color = c.textSecondary)
        if (crewRow.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                FText("Directors, creators & producers", style = Fluent.type.bodyStrong.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                if (canOpenAll && crewRow.size > 6) Button("View all", openAll, kind = ButtonKind.Subtle, icon = Icons.ChevronRightSmall)
            }
            PeopleShelf(crewRow.take(14), if (canOpenAll && crewRow.size > 6) openAll else null, crewRow.size)
        }
        actorsText?.let { text -> FText(text, color = c.textSecondary, modifier = Modifier.padding(vertical = 8.dp).widthIn(max = 900.dp)) }
    }
}

private object ViewAllMarker

/** A row of person cards that scrolls (wheel, arrows, or held and dragged); a last card opens the whole list when [openAll] is given */
@Composable
private fun PeopleShelf(people: List<CastEntry>, openAll: (() -> Unit)?, count: Int) {
    val items: List<Any> = if (openAll != null) people + ViewAllMarker else people
    Shelf(items, CastTileWidth, gutter = 0.dp, spacing = 16.dp) { item ->
        if (item is CastEntry) CastCard(item.name, item.image, item.role, item.badge, item.id?.let { { openPerson(item) } })
        else ViewAllCard(count) { openAll?.invoke() }
    }
}

/** The last card of a row: a tile of the same shape that says the whole list is behind it */
@Composable
private fun ViewAllCard(count: Int, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(Modifier.width(CastTileWidth).hoverable(source).fluentClickable(source, true, shape, Role.Button, onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(if (hovered) c.cardHover else c.card)
                .hairBorder(if (hovered) c.accent else c.stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(44.dp).background(c.accent.copy(alpha = if (hovered) 0.3f else 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.ChevronRightSmall, size = 16.dp, tint = c.accentText)
                }
                FText("View all", style = Fluent.type.bodyStrong)
                FText("$count people", style = Fluent.type.caption, color = c.textTertiary)
            }
        }
    }
}

/** A person: a portrait photo in a rounded rectangle (a badge over it for the crew), the name under it and what they played or did */
@Composable
internal fun CastCard(name: String, image: String?, role: String?, badge: String? = null, onClick: (() -> Unit)? = null) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1.03f else 1f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(200))
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(Modifier.width(CastTileWidth).hoverable(source).let { if (onClick != null) it.fluentClickable(source, true, shape, Role.Button, onClick) else it }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(c.layer)
                .hairBorder(if (hovered) c.strokeStrong else c.stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            FText(name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), style = Fluent.type.subtitle, color = c.textTertiary)
            RemoteImage(image, null, name, Modifier.fillMaxSize(), ContentScale.Crop)
            if (badge != null) Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xAD000000), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
            ) { FText(badge, style = Fluent.type.caption, color = if (badge == "DIRECTOR" || badge == "CREATOR") Color(0xFFFFB74D) else c.accentText, maxLines = 1, softWrap = false) }
        }
        Box(Modifier.height(8.dp))
        FText(name, style = Fluent.type.bodyStrong, maxLines = 2)
        role?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.caption, color = c.textTertiary, maxLines = 2) }
    }
}

/** The whole list on a page of its own: a search box, the season picker, then every heading with all its people */
@Composable
fun FullCastScreen(route: Route.FullCast) {
    val c = Fluent.colors
    val info by produceState<TmdbInfo?>(null, route.tmdbId) { value = Tmdb.byId(route.tmdbId, route.isMovie) }
    var season by remember { mutableStateOf(route.initialSeason) }
    var query by remember { mutableStateOf("") }
    val credits by produceState<Credits?>(null, info, season) { value = info?.let { creditsOf(it, season) } }
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val perRow = ((maxWidth - 72.dp + 18.dp) / (CastTileWidth + 18.dp)).toInt().coerceAtLeast(2)
        FluentScrollbar(listState, TopBarHeight)
        LazyColumn(
            Modifier.fillMaxSize().smoothWheel(listState), state = listState,
            contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = TopBarHeight + 20.dp, bottom = 40.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current),
        ) {
            item { PageHeader("Cast & crew", Modifier.padding(bottom = 12.dp), subtitle = route.title) }
            item {
                Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextBox(query, { query = it }, Modifier.width(320.dp), placeholder = "Search by name or role", leadingIcon = Icons.Search)
                    if (route.seasons.size >= 2) ComboBox(listOf(-1) + route.seasons, season ?: -1, { seasonLabel(it) }, { season = it.takeIf { n -> n >= 0 } }, minWidth = 170.dp, height = 32.dp)
                }
            }
            val cr = credits
            if (cr == null) item { FText("Loading…", color = c.textSecondary) }
            else {
                fun matches(p: CastEntry) = query.isBlank() || p.name.contains(query.trim(), true) || p.role?.contains(query.trim(), true) == true
                val sections = buildList {
                    add("Cast" to cr.castEntries())
                    CrewHeadings.forEach { h -> add(h.title to cr.entries(h)) }
                }.map { (t, people) -> t to people.filter(::matches) }.filter { it.second.isNotEmpty() }
                if (sections.isEmpty()) item { EmptyState(Icons.Search, "No one found", "Nothing in the cast or crew matches that.") }
                sections.forEach { (title, people) ->
                    item(key = "h-$title") {
                        Row(Modifier.padding(top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FText(title, style = Fluent.type.subtitle)
                            FText(people.size.toString(), style = Fluent.type.caption, color = c.textTertiary)
                        }
                    }
                    val rows = people.chunked(perRow)
                    rows.forEachIndexed { i, row ->
                        item(key = "r-$title-$i") {
                            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                row.forEach { p -> CastCard(p.name, p.image, p.role, if (title == "Cast") null else CrewHeadings.firstOrNull { it.title == title }?.badge, p.id?.let { { openPerson(p) } }) }
                            }
                        }
                    }
                }
            }
        }
    }
}
