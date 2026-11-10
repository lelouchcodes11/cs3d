package com.lagradost.desktop.ui.screens.home

import com.lagradost.desktop.ui.fluent.shown
import com.lagradost.desktop.ui.fluent.fadeIntoPage
import com.lagradost.desktop.ui.fluent.reveal
import com.lagradost.desktop.ui.fluent.composeAhead
import com.lagradost.desktop.ui.fluent.followKeyboardFocus
import com.lagradost.desktop.ui.fluent.smoothWheel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.lagradost.desktop.ui.fluent.TouchLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import com.lagradost.desktop.ui.fluent.focusRing
import com.lagradost.desktop.ui.fluent.fluentClickable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.watchedFraction
import com.lagradost.desktop.ui.fluent.shimmer
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.result.START_ACTION_LOAD_EP
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.Badge
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.artScrim
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.PosterSkeleton
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.SectionHeader
import com.lagradost.desktop.ui.fluent.Shelf
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.typeLabel
import com.lagradost.desktop.ui.shell.TopBarOverlay
import kotlinx.coroutines.delay
import com.lagradost.desktop.ui.screens.explore.discoverSections
import kotlinx.coroutines.ensureActive

/** Where the text of the page starts: 36 dp, and after the floating dock when that floats over the artwork */
private val gutter: Dp
    @Composable get() = 36.dp + com.lagradost.desktop.ui.shell.LocalDockStart.current

/** Opens a card: a continue-watching entry resumes its episode, anything else opens the title page */
fun openCard(card: SearchResponse) {
    // a title from TMDB or the Explore lists belongs to no extension: the extensions are searched for it
    if (card is com.lagradost.desktop.tmdb.TmdbCard) { Navigator.search(card.name); return }
    if (card is DataStoreHelper.ResumeWatchingResult && !card.isFromDownload && card.id != null) {
        Navigator.openDetails(card, START_ACTION_LOAD_EP, card.id)
    } else Navigator.openDetails(card)
}

/**
 * Only a row that says it is a short chart ("Top 10 in India", "Top 5 today") gets big rank numbers. Matching "top", "popular", "hot" ...
 * anywhere in a name numbered nearly every row of StreamPlay and CineStream ("Hotstar", "Top Rated", "Popular on Netflix"), which are long lists.
 */
private val chartName = Regex("""\btop[\s-]*(\d{1,2}|five|ten|twenty)\b""", RegexOption.IGNORE_CASE)

private fun chartSize(name: String): Int? {
    val word = chartName.find(name)?.groupValues?.get(1)?.lowercase() ?: return null
    return when (word) { "five" -> 5; "ten" -> 10; "twenty" -> 20; else -> word.toIntOrNull() }?.takeIf { it in 3..20 }
}

/** The play button of a card: the title page opens and starts the episode or film at once (a continue-watching entry resumes it, a TMDB title is searched for) */
fun playCard(card: SearchResponse) {
    if (card is com.lagradost.desktop.tmdb.TmdbCard || (card is DataStoreHelper.ResumeWatchingResult && !card.isFromDownload && card.id != null)) { openCard(card); return }
    Navigator.go(Route.Details(card.url, card.apiName, card.name, card.posterUrl, START_ACTION_RESUME_LATEST))
}

@Composable
fun HomeScreen() {
    val vm = appVm<HomeViewModel>()
    LaunchedEffect(Unit) {
        vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = false)
        vm.reloadStored()
    }
    val page by vm.page.observeAsState()
    val preview by vm.preview.observeAsState()
    val resume by vm.resumeWatching.observeAsState()
    val bookmarks by vm.bookmarks.observeAsState()
    val statusTypes by vm.availableWatchStatusTypes.observeAsState()
    val apiName by vm.apiName.observeAsState()
    // the page of the last visit is on screen because the fresh one could not be loaded

    // customise and reload live in the top bar of the floating-dock look (no "Browse" header on Home)
    val rowNames = (page as? Resource.Success)?.value?.entries?.filter { it.value.list.list.isNotEmpty() }?.map { it.key }.orEmpty()
    androidx.compose.runtime.DisposableEffect(apiName, rowNames) {
        val name = apiName
        com.lagradost.desktop.ui.shell.ShellState.homeActions = com.lagradost.desktop.ui.shell.HomeActions(
            customise = if (rowNames.isNotEmpty() && !name.isNullOrBlank()) ({ HomeLayout.showDialog(name, rowNames) }) else null,
            reload = { vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = true, fromUI = false) },
        )
        onDispose { com.lagradost.desktop.ui.shell.ShellState.homeActions = null }
    }

    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // the interface of a special theme (Netflix) has its own billboard, row titles and spacing
        val skin = com.lagradost.desktop.ui.fluent.Skins.spec
        val heroHeight = if (skin != null) skinHeroHeight(maxHeight) else (maxHeight * 0.8f).coerceIn(440.dp, 840.dp)
        // Netflix lays its first row over the foot of the banner
        val overlap = if (skin != null) SkinRowOverlap else 0.dp
        fun Modifier.climb(): Modifier = if (overlap == 0.dp) this else this.layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            val up = overlap.roundToPx().coerceAtMost(p.height)
            layout(p.width, p.height - up) { p.place(0, -up) }
        }
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 } }
        TopBarOverlay(scrolled)
        val cardWidth = com.lagradost.desktop.ui.fluent.Appearance.posterSize.width * (if (maxWidth >= 1008.dp) 1.05f else 0.92f)
        val space = com.lagradost.desktop.ui.fluent.Appearance.space(38.dp)

        FluentScrollbar(listState, com.lagradost.desktop.ui.shell.TopBarHeight)
        // the rows of the next screenful are composed while the page is idle (see composeAhead), not in the frames of the first scroll
        val ahead = com.lagradost.desktop.ui.fluent.rememberComposeAhead()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().followKeyboardFocus(listState).composeAhead(ahead).smoothWheel(listState), contentPadding = com.lagradost.desktop.ui.fluent.lazyPadding(ahead, bottom = com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
            item(key = "hero") {
                val res = preview
                val items = (res as? Resource.Success)?.value?.second.orEmpty()
                if (!com.lagradost.desktop.ui.fluent.Appearance.homeBanner) {
                    Box(Modifier.height(com.lagradost.desktop.ui.shell.TopBarHeight + 8.dp))
                } else if (items.isNotEmpty()) {
                    val heroScroll = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE }
                    if (skin != null) SkinHero(skin, items, heroHeight, maxWidth, heroScroll)
                    else HomeHero(items, heroHeight, compact = maxWidth < 900.dp, width = maxWidth, scroll = heroScroll)
                } else if ((res == null || res is Resource.Loading) && page !is Resource.Failure) {
                    HeroSkeleton(heroHeight)
                } else Box(Modifier.height(72.dp))
            }
            // the other looks keep their "Browse" header with the customise and reload buttons
            if (skin == null && com.lagradost.desktop.ui.fluent.Appearance.navPosition.let { it != com.lagradost.desktop.ui.fluent.NavPosition.Floating && it != com.lagradost.desktop.ui.fluent.NavPosition.Dock }) item(key = "header") {
                HomeHeader(vm, apiName, rowNames)
            }
            val resumeList = resume.orEmpty()
            val resumeShown = resumeList.isNotEmpty() && com.lagradost.desktop.ui.fluent.Appearance.homeContinue
            val bookmarksShown = bookmarks?.second?.isNotEmpty() == true
            if (resumeShown) {
                item(key = "resume") {
                    Column(Modifier.let { if (resumeShown) it.climb() else it }.reveal("home/$apiName/resume", 60).padding(bottom = space)) {
                        if (skin != null) com.lagradost.desktop.ui.fluent.SkinRowHeader(skin, "Continue watching", Modifier.padding(horizontal = gutter), onSeeAll = { Navigator.go(Route.History) })
                        else com.lagradost.desktop.ui.fluent.RichSectionHeader("Continue watching", Modifier.padding(horizontal = gutter), onSeeAll = { Navigator.go(Route.History) })
                        Box(Modifier.height(12.dp))
                        // wide cards: the still of the episode, what is next and how long is left, the source it was watched with, the bar along the bottom is how far you got
                        val resumeWidth = (cardWidth * 1.75f).coerceIn(250.dp, 340.dp)
                        Shelf(resumeList, resumeWidth, gutter = gutter, spacing = 14.dp, key = { (it.id ?: it.url.hashCode()).toString() + it.name }) { card ->
                            val r = card as? DataStoreHelper.ResumeWatchingResult
                            ResumeCard(
                                card, resumeWidth, resumeSubtitle(card),
                                remove = { r?.let { DataStoreHelper.removeLastWatched(it.parentId); vm.reloadStored() } },
                                menu = {
                                    listOf(
                                        MenuItem("Resume", Icons.Play) { openCard(card) },
                                        MenuItem("Open title page", Icons.Info) { Navigator.openDetails(card) },
                                        MenuItem("Remove from list", Icons.Delete, destructive = true) {
                                            r?.let {
                                                DataStoreHelper.removeLastWatched(it.parentId)
                                                vm.reloadStored()
                                            }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            val bm = bookmarks
            if (bm != null && bm.second.isNotEmpty()) {
                item(key = "bookmarks") {
                    Column(Modifier.let { if (!resumeShown) it.climb() else it }.reveal("home/$apiName/bookmarks", 120).padding(bottom = space)) {
                        BookmarksHeader(vm, statusTypes, skin)
                        Box(Modifier.height(12.dp))
                        Shelf(bm.second, cardWidth, gutter = gutter, spacing = 14.dp, key = { it.url }) { card ->
                            PosterCard(card, { openCard(card) }, cardWidth, onPlay = { playCard(card) })
                        }
                    }
                }
            }
            when (val res = page) {
                null, is Resource.Loading -> items(3, key = { "sk$it" }) { SkeletonRow(cardWidth) }
                is Resource.Failure -> {
                    item(key = "error") {
                        val problems = com.lagradost.desktop.ui.components.ProblemText
                        // a source that only needs setting up (Ultima before its sections are chosen) was announced as "could not be reached", with a
                        // "Try again" that could never help: it says what to do and opens its settings page
                        val setup = problems.asksForSettings(res.errorString)
                        com.lagradost.desktop.ui.fluent.NoticeBar(
                            if (setup) "${apiName ?: "This source"} has nothing to show yet. ${res.errorString}"
                            else "${apiName ?: "This source"} could not be reached. ${problems.of(res.errorString).text}",
                            Modifier.padding(horizontal = gutter).padding(bottom = 22.dp),
                            action = if (setup) "Open its settings" else "Try again",
                            onAction = {
                                if (!setup || !problems.openProviderSettings(apiName)) vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = true)
                            },
                        )
                    }
                    // not an empty page: what is trending meanwhile
                    discoverSections(cardWidth)
                }
                is Resource.Success -> {
                    val rows = HomeLayout.rowsFor(apiName.orEmpty(), res.value.entries.filter { it.value.list.list.isNotEmpty() }.map { it.key to it.value })
                    if (rows.isEmpty()) {
                        item(key = "empty") {
                            // extensions that are not loaded because the sandbox cannot start: say that, not "install an extension"
                            val held = com.lagradost.desktop.isolate.Isolation.blockedNames()
                            if (held.isNotEmpty()) com.lagradost.desktop.ui.fluent.NoticeBar(
                                com.lagradost.desktop.i18n.Tr.f("{0} extensions are not loaded because the sandbox could not start: {1}", held.size, com.lagradost.desktop.isolate.IsolatedHost.startFailure() ?: "?"),
                                Modifier.padding(horizontal = gutter).padding(bottom = 22.dp),
                                action = "Open Settings", onAction = { Navigator.goTab(com.lagradost.desktop.core.Tab.Settings) },
                            ) else com.lagradost.desktop.ui.fluent.NoticeBar(
                                // no extension chosen at all reads "from None" otherwise
                                if (apiName.isNullOrBlank() || apiName.equals("none", ignoreCase = true)) "No extension is chosen yet. Install one in Extensions to get catalogues here, or pick a source at the top right."
                                else "Nothing to show from $apiName yet. Pick another source at the top right, or install an extension.",
                                Modifier.padding(horizontal = gutter).padding(bottom = 22.dp),
                                action = "Open Extensions", onAction = { Navigator.goTab(com.lagradost.desktop.core.Tab.Extensions) },
                            )
                        }
                        discoverSections(cardWidth)
                    }
                    rows.forEachIndexed { rowIndex, (name, row) ->
                        item(key = "row-$name") {
                            Column(Modifier.let { if (rowIndex == 0 && !resumeShown && !bookmarksShown) it.climb() else it }.reveal("home/$apiName/row-$name", 60 + rowIndex.coerceAtMost(4) * 70).padding(bottom = space)) {
                                val chart = chartSize(name)
                                val ranked = chart != null && !row.list.isHorizontalImages
                                val seeAll: () -> Unit = {
                                        // the row's own pages go on in the full list: the next page is asked for as the list is scrolled to its end
                                        val more: (suspend () -> Pair<List<SearchResponse>, Boolean>)? = if (!row.hasNext) null else ({
                                            val expanded = vm.expandAndReturn(name)
                                            // null: the row is being extended by the shelf at this moment; the list so far, asked again shortly
                                            (expanded?.list?.list ?: row.list.list).toList() to (expanded?.hasNext ?: true)
                                        })
                                        Navigator.go(Route.Section(name, row.list.list.toList(), more))
                                }
                                if (skin != null) com.lagradost.desktop.ui.fluent.SkinRowHeader(skin, name, Modifier.padding(horizontal = gutter), onSeeAll = seeAll)
                                else com.lagradost.desktop.ui.fluent.RichSectionHeader(name, Modifier.padding(horizontal = gutter), onSeeAll = seeAll)
                                Box(Modifier.height(12.dp))
                                val landscape = row.list.isHorizontalImages
                                val w = if (landscape) (cardWidth * 1.6f).coerceIn(230.dp, 300.dp) else cardWidth
                                val state = rememberLazyListState()
                                if (row.hasNext) {
                                    LaunchedEffect(state, row.list.list.size) {
                                        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                                            if (last >= row.list.list.size - 3 && row.hasNext) vm.expand(name)
                                        }
                                    }
                                }
                                if (ranked) {
                                    val shown = row.list.list.take(chart ?: 10)
                                    Shelf(shown, w, gutter = gutter, spacing = 6.dp, state = state, key = { it.url }) { card ->
                                        com.lagradost.desktop.ui.fluent.RankedPoster(shown.indexOf(card) + 1, w) {
                                            PosterCard(card, { openCard(card) }, null)
                                        }
                                    }
                                } else {
                                    Shelf(row.list.list, w, gutter = gutter, spacing = 14.dp, state = state, key = { it.url }) { card ->
                                        PosterCard(card, { openCard(card) }, w, landscape = landscape, onPlay = { playCard(card) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "end") {
                // choosing and ordering the rows: at the foot of the page, not in the top bar (the other looks have it in their Browse header)
                val customise = com.lagradost.desktop.ui.shell.ShellState.homeActions?.customise
                val inHeader = skin == null && com.lagradost.desktop.ui.fluent.Appearance.navPosition.let { it != com.lagradost.desktop.ui.fluent.NavPosition.Floating && it != com.lagradost.desktop.ui.fluent.NavPosition.Dock }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 40.dp), contentAlignment = Alignment.Center) {
                    if (customise != null && !inHeader) Button("Customise Home rows", customise, kind = ButtonKind.Subtle, icon = Icons.Edit, height = 34.dp)
                }
            }
        }
    }
}

/** "S1 · E3 · 40 min left" (a film: "26 min left") */
internal fun resumeSubtitle(card: SearchResponse): String? {
    val r = card as? DataStoreHelper.ResumeWatchingResult ?: return typeLabel(card.type)
    val parts = ArrayList<String>()
    val ep = r.episode
    val season = r.season
    // a film has episode 0 and no season
    if (ep != null && (ep > 0 || season != null)) parts += if (season != null) "S$season · E$ep" else "E$ep"
    resumeLeft(card)?.let { parts += it }
    return parts.joinToString(" · ").ifEmpty { typeLabel(r.type) }
}

private fun resumeLeft(card: SearchResponse): String? {
    val r = card as? DataStoreHelper.ResumeWatchingResult ?: return null
    val left = r.watchPos?.let { ((it.duration - it.position) / 60_000L).coerceAtLeast(0) } ?: return null
    if (left <= 0) return null
    return if (left >= 60) "${left / 60} h ${left % 60} min left" else "$left min left"
}

// -------------------------------------------------------------------------------------------
// Header (provider switcher)
// -------------------------------------------------------------------------------------------

@Composable
private fun HomeHeader(vm: HomeViewModel, apiName: String?, rowNames: List<String>) {
    Row(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.lagradost.desktop.ui.fluent.PageHeader(
            "Browse", Modifier.weight(1f),
            subtitle = apiName?.takeIf { it.isNotBlank() && it != "NONE" }?.let { "From $it" },
        ) {
            if (rowNames.isNotEmpty() && !apiName.isNullOrBlank()) IconButton(Icons.Edit, { HomeLayout.showDialog(apiName, rowNames) }, tooltip = "Customise Home: choose and order the rows", kind = ButtonKind.Standard, size = 36.dp)
            IconButton(Icons.Refresh, {
                // a check window (captcha) of an extension and a hidden Cloudflare check that hold the page up are closed first, then the page loads again
                com.lagradost.desktop.runtime.web.CloudflareGate.cancelSolves()
                com.lagradost.desktop.isolate.RemoteBrowserHost.cancelWindows()
                com.lagradost.desktop.ui.DesktopUiHost.cancelPageDialogs()
                vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = true, fromUI = false)
            }, tooltip = "Reload home page", kind = ButtonKind.Standard, size = 36.dp)
        }
    }
}

@Composable
private fun BookmarksHeader(vm: HomeViewModel, statusTypes: Pair<Set<WatchType>, Set<WatchType>>?, skin: com.lagradost.desktop.ui.fluent.SkinSpec? = null) {
    val ctx = DesktopBootstrap.activityOrNull()
    val selected = statusTypes?.first.orEmpty()
    val types = statusTypes?.second?.sortedBy { it.internalId }.orEmpty()
    val trailing: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                types.forEach { type ->
                    Chip(
                        ctx?.getString(type.stringRes) ?: type.name, type in selected,
                        onClick = {
                            val next = if (type in selected) selected - type else selected + type
                            vm.loadStoredData(if (next.isEmpty()) setOf(type) else next)
                        },
                    )
                }
            }
    }
    if (skin != null) com.lagradost.desktop.ui.fluent.SkinRowHeader(skin, "Your library", Modifier.padding(horizontal = gutter), trailing = trailing)
    else com.lagradost.desktop.ui.fluent.RichSectionHeader("Your library", Modifier.padding(horizontal = gutter), trailing = trailing)
}

// -------------------------------------------------------------------------------------------
// States
// -------------------------------------------------------------------------------------------

@Composable
private fun SkeletonRow(cardWidth: Dp) {
    Column(Modifier.padding(bottom = 30.dp, start = gutter)) {
        Box(Modifier.size(220.dp, 24.dp).clip(RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.control)).shimmer())
        Box(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(8) { PosterSkeleton(cardWidth) } }
    }
}

// -------------------------------------------------------------------------------------------
// Hero
// -------------------------------------------------------------------------------------------

@Composable
private fun HeroSkeleton(height: Dp) {
    // a dim lift of the page colour that fades out at the foot (a full-width shimmering slab was a big grey block on the black page), and the shapes of the text on it
    Box(Modifier.fillMaxWidth().height(height).background(Fluent.colors.card.copy(alpha = 0.4f))) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(90.dp, 22.dp).clip(RoundedCornerShape(50)).shimmer())
            Box(Modifier.size(420.dp, 52.dp).clip(RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.control)).shimmer())
            Box(Modifier.size(520.dp, 18.dp).clip(RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.control)).shimmer())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(130.dp, 44.dp).clip(RoundedCornerShape(50)).shimmer())
                Box(Modifier.size(150.dp, 44.dp).clip(RoundedCornerShape(50)).shimmer())
            }
        }
    }
}

@Composable
private fun HomeHero(all: List<LoadResponse>, height: Dp, compact: Boolean, width: Dp, scroll: () -> Float) {
    // as many titles as the strip has room for (the slices stay clear of the buttons at the left), at most the eight the page loads
    val cap = if (compact) 8 else (((width - gutter * 2 - 300.dp - 168.dp) / 62.dp).toInt() + 1).coerceIn(3, 8)
    val items = remember(all, all.size, cap) { all.take(cap) }
    var index by remember { mutableStateOf(0) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    // the shown item's thumbnail fills while it is on screen; hovering the hero pauses the rotation
    // in steps of a few frames a second, not one per frame: an animation that runs all the time makes the whole window redraw 60 times a second
    // (a third of a core and the GPU for a thin line), and the pages are redrawn as a whole
    val progress = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // and while the window is not the active one (minimised, another app in front): it carries on where it stopped
    val active = com.lagradost.desktop.DesktopLifecycle.windowActive
    LaunchedEffect(index, hovered, items.size, active) {
        if (items.size <= 1) return@LaunchedEffect
        if (hovered || !active) return@LaunchedEffect
        while (progress.floatValue < 1f) {
            kotlinx.coroutines.delay(HERO_TICK_MS.toLong())
            progress.floatValue = (progress.floatValue + HERO_TICK_MS / HERO_MS.toFloat()).coerceAtMost(1f)
        }
        progress.floatValue = 0f
        index = (index + 1) % items.size
    }
    val current = items[index.coerceIn(0, items.lastIndex)]
    // artwork TMDB has for slides the extension sent no backdrop or logo for: this slide and the next one are looked up
    val art = remember(items) { androidx.compose.runtime.mutableStateMapOf<String, com.lagradost.desktop.tmdb.TmdbInfo>() }
    LaunchedEffect(index, items) {
        for (item in listOf(current, items[(index + 1) % items.size])) {
            if (art.containsKey(item.url)) continue
            val found = try {
                com.lagradost.desktop.tmdb.Tmdb.info(item.name, item.year, item.type?.isMovieType() == true, item.syncData.values)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) { null }
            ensureActive()
            // only the title itself: a first search result that is another show would put the wrong picture on the billboard
            if (found != null && com.lagradost.desktop.tmdb.Tmdb.sameTitle(item.name, found.title)) art[item.url] = found
        }
    }
    com.lagradost.desktop.ui.shell.AmbientArtwork(current.backgroundPosterUrl ?: current.posterUrl, current.posterHeaders)
    Box(Modifier.fillMaxWidth().height(height).hoverable(source)) {
        Crossfade(current, animationSpec = com.lagradost.desktop.ui.fluent.FluentMotion.tweenStd(900), label = "heroBackdrop") { item -> HeroBackdrop(item, art[item.url]?.backdrop, scroll) }
        AnimatedContent(
            current,
            // the text leaves a little slower than the page and fades as it goes under the bar
            modifier = Modifier.graphicsLayer { val s = scroll().coerceAtMost(height.toPx()); translationY = s * 0.18f; alpha = (1f - s / (height.toPx() * 0.5f)).coerceIn(0f, 1f) },
            transitionSpec = {
                (fadeIn(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(420)) + androidx.compose.animation.slideInHorizontally(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(520)) { -it / 14 }) togetherWith
                    fadeOut(com.lagradost.desktop.ui.fluent.FluentMotion.tweenOut(160))
            },
            label = "heroText",
        ) { item -> HeroText(item, compact, height, art[item.url]?.logo) }
        if (items.size > 1) {
            if (compact) {
                // slim segments at the right: the shown one fills while it is on screen (hovering the banner holds it)
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    items.forEachIndexed { i, _ ->
                        val w by androidx.compose.animation.core.animateDpAsState(if (i == index) 34.dp else 18.dp, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(320), label = "seg")
                        Box(Modifier.size(w, 14.dp).clickableNoRipple { progress.floatValue = 0f; index = i }, contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x4DFFFFFF))) {
                                if (i == index) Box(Modifier.fillMaxHeight().fillMaxWidth(if (hovered) 1f else progress.floatValue).background(Color.White))
                            }
                        }
                    }
                }
            } else {
                // the next titles as a strip: the shown one is open, the rest are slices of their artwork
                com.lagradost.desktop.ui.fluent.FeaturedStrip(
                    count = items.size, selected = index, progress = { if (hovered) 1f else progress.floatValue },
                    image = { i -> val it = items[i]; (it.backgroundPosterUrl ?: art[it.url]?.backdrop ?: it.posterUrl) to it.posterHeaders },
                    onSelect = { i -> progress.floatValue = 0f; index = i },
                    label = { i -> items[i].name },
                    // the hero's text is at the left: the strip takes what is right of it
                    maxWidth = (width * 0.56f).coerceAtMost(760.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = gutter, bottom = 34.dp).graphicsLayer { alpha = (1f - scroll() / (height.toPx() * 0.4f)).coerceIn(0f, 1f) },
                )
            }
        }
    }
}

// how long a banner slide stays: each change redraws the whole window for about a second (0.9 s fade), the main cost of an idle Home (measured:
// about 8-10 % of a core at 9 s, 2 % with the slides held; 15 s: 7.7 %, A/B on one build), so 15 s; -Dcloudstream.heroms=<ms> for A/B
internal val HERO_MS = System.getProperty("cloudstream.heroms")?.toIntOrNull()?.coerceAtLeast(2000) ?: 15_000
internal const val HERO_TICK_MS = 120

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

/** Backdrop of the hero: still artwork, text scrims, and a fade into the page at the bottom (any backdrop style) */
@Composable
internal fun HeroBackdrop(item: LoadResponse, tmdbBackdrop: String? = null, scroll: () -> Float = { 0f }, scrims: Boolean = true, fadeStart: Float = 0.6f) {
    val zoom: androidx.compose.runtime.State<Float>? = null
    Box(
        Modifier.fillMaxSize().fadeIntoPage(fadeStart),
    ) {
        // the extension's own picture, unless it is small (a 640 px still stretched over a 1920 px window is a smear) and TMDB has a wide one of the same title
        val own = item.backgroundPosterUrl
        var ownWidth by remember(own) { mutableStateOf<Int?>(null) }
        val smear = own != null && tmdbBackdrop != null && (ownWidth ?: Int.MAX_VALUE) < 1100
        val backdrop = if (smear) tmdbBackdrop else (own ?: tmdbBackdrop)
        val sizeSeen: ((Int, Int) -> Unit)? = if (backdrop == own) { w, _ -> ownWidth = w } else null
        // the picture trails the page a little (parallax): it is shifted down by a part of the scroll, under the fade at the bottom
        Box(Modifier.fillMaxSize().graphicsLayer { val s = zoom?.value ?: 1f; scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.4f); translationY = scroll().coerceAtMost(size.height) * 0.3f }) {
            // colour at once, never a black page while the wide picture is on its way: the poster, decoded tiny
            com.lagradost.desktop.ui.components.SoftImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize(), alpha = 0.55f)
            if (backdrop != null) RemoteImage(backdrop, if (backdrop == own) item.posterHeaders else null, null, Modifier.fillMaxSize(), ContentScale.Crop, onSize = sizeSeen)
        }
        if (backdrop == null) {
            // sharp poster on the right when the provider has no backdrop
            Box(Modifier.fillMaxSize().padding(end = 96.dp, top = 84.dp, bottom = 150.dp), contentAlignment = Alignment.CenterEnd) {
                val shape = RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.card)
                // a landscape poster (a live event, a wide key art) stays landscape; only a portrait one is shown tall (cropping it to 2:3 cut its text off)
                var ratio by remember(item.posterUrl) { mutableStateOf<Float?>(null) }
                val landscape = (ratio ?: 0f) > 1.15f
                RemoteImage(
                    item.posterUrl, item.posterHeaders, null,
                    if (landscape) Modifier.fillMaxWidth(0.46f).aspectRatio((ratio ?: 1.78f).coerceIn(1.15f, 2.4f)).clip(shape) else Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(shape),
                    if (landscape) ContentScale.Fit else ContentScale.Crop,
                    onSize = { w, h -> if (h > 0) ratio = w.toFloat() / h },
                )
            }
        }
        // scrims: left for the text, top for the title bar
        if (scrims) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Fluent.colors.artScrim.copy(alpha = 0.95f), 0.38f to Fluent.colors.artScrim.copy(alpha = 0.7f), 0.7f to Fluent.colors.artScrim.copy(alpha = 0.2f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
        }
    }
}

@Composable
private fun HeroText(item: LoadResponse, compact: Boolean, height: Dp, tmdbLogo: String? = null) {
    val c = Fluent.colors
    val bottomPad = if (compact) 56.dp else 84.dp
    // what is left below the top bar: the block never reaches under it
    val room = height - com.lagradost.desktop.ui.shell.TopBarHeight - bottomPad - 8.dp
    val showPlot = room >= 300.dp
    val tight = room < 230.dp
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = bottomPad).widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val logo = item.logoUrl ?: tmdbLogo
            com.lagradost.desktop.ui.fluent.TitleMark(logo, item.posterHeaders, item.name, if (tight) 60.dp else if (compact) 76.dp else 104.dp, 460.dp) {
                FText(item.name, style = (if (compact) Fluent.type.title else Fluent.type.titleLarge).copy(fontSize = if (compact) 30.sp else 46.sp, lineHeight = if (compact) 36.sp else 52.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 16f)), color = Color.White, maxLines = if (tight || !showPlot) 1 else 2)
            }
            // one line: score, age rating, what it is
            val chips = buildList {
                com.lagradost.desktop.ui.fluent.typeLabel(item.type)?.let { add(it) }
                // a series: how many episodes it has, and for an anime whether it has sub and dub
                val episodes = when (item) {
                    is com.lagradost.cloudstream3.AnimeLoadResponse -> item.episodes.values.maxOfOrNull { it.size }
                    is com.lagradost.cloudstream3.TvSeriesLoadResponse -> item.episodes.size
                    else -> null
                }?.takeIf { it > 1 }
                episodes?.let { add("$it episodes") }
                (item as? com.lagradost.cloudstream3.AnimeLoadResponse)?.episodes?.keys?.filter { it != com.lagradost.cloudstream3.DubStatus.None }?.let { keys ->
                    val dub = com.lagradost.cloudstream3.DubStatus.Dubbed in keys
                    val sub = com.lagradost.cloudstream3.DubStatus.Subbed in keys
                    (if (dub && sub) "SUB · DUB" else if (dub) "DUB" else if (sub) "SUB" else null)?.let { add(it) }
                }
            }
            val tags = buildList {
                item.year?.let { add(it.toString()) }
                item.duration?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min") }
                item.tags?.take(3)?.forEach { add(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                item.score.shown()?.let { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.lagradost.desktop.ui.fluent.Icon(Icons.StarFilled, size = 15.dp, tint = Color(0xFFFFC53D))
                        FText(s.toString(10, 1), style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1)
                    }
                }
                item.contentRating?.takeIf { it.isNotBlank() }?.let { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                chips.forEach { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                if (tags.isNotEmpty()) FText(tags.joinToString("  •  "), style = Fluent.type.bodyStrong, color = Color(0xE6FFFFFF), maxLines = 1)
            }
            if (showPlot) item.plot?.takeIf { it.isNotBlank() }?.let { FText(stripHtml(it).replace("[more]", "").trim(), style = Fluent.type.bodyLarge.copy(lineHeight = 26.sp), color = Color(0xCCFFFFFF), maxLines = 2) }
            Box(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.lagradost.desktop.ui.fluent.PillButton("Watch now", Icons.Play, primary = true, onArt = true, onClick = { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl, START_ACTION_RESUME_LATEST)) }, height = 50.dp)
                // add to / take off the library without leaving the page (the mark follows a click at once, the library answers a moment later)
                var saved by remember(item.url) { mutableStateOf(runCatching { com.lagradost.desktop.ui.fluent.CardLibrary.statusOf(item) != com.lagradost.cloudstream3.ui.WatchType.NONE }.getOrDefault(false)) }
                com.lagradost.desktop.ui.fluent.GlassCircleButton(
                    if (saved) Icons.Check else Icons.Add, if (saved) "Remove from your library" else "Add to your library",
                    {
                        val now = !saved
                        saved = now
                        runCatching { com.lagradost.desktop.ui.fluent.CardLibrary.setStatus(item, if (now) com.lagradost.cloudstream3.ui.WatchType.PLANTOWATCH else com.lagradost.cloudstream3.ui.WatchType.NONE) }
                    },
                    size = 50.dp,
                )
                com.lagradost.desktop.ui.fluent.GlassCircleButton(Icons.Info, "More info", { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl)) }, size = 50.dp)
            }
        }
    }
}

internal fun stripHtml(html: String): String =
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()

// -------------------------------------------------------------------------------------------
// Continue watching card
// -------------------------------------------------------------------------------------------

/** TMDB's wide picture by title for the Continue watching cards that have no still of their own ("" = looked up, none found) */
private val resumeBackdrops = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * One entry of Continue watching as a 16:9 card: the still of the episode (the poster of the title when the extension sent none),
 * the title and what is next at the bottom, the source it was watched with at the top right, how far you got as a bar.
 */
@Composable
private fun ResumeCard(card: SearchResponse, width: Dp, subtitle: String?, menu: () -> List<MenuItem>, remove: (() -> Unit)? = null) {
    val c = Fluent.colors
    val r = card as? DataStoreHelper.ResumeWatchingResult
    val source = com.lagradost.desktop.ui.fluent.rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.card)
    val lift by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1.03f else 1f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenStd(160), label = "resumeLift")
    val stored = com.lagradost.desktop.stats.ResumeExtras.thumbOf(r?.id)
    // nothing stored (a title watched before stills were kept): TMDB's wide picture of the title instead of the tall poster cropped to a strip
    val backdrop by androidx.compose.runtime.produceState<String?>(resumeBackdrops[card.name]?.ifEmpty { null }, card.name, stored) {
        if (stored != null || !com.lagradost.desktop.tmdb.Tmdb.enabled || resumeBackdrops.containsKey(card.name)) return@produceState
        // a lookup that is cut off (the page was left) says nothing: only an answer is kept
        val info = try {
            com.lagradost.desktop.tmdb.Tmdb.info(card.name, null, card.type?.isMovieType() == true, emptyList())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            return@produceState
        }
        // Tmdb swallows a cancellation inside its request and answers null: look again whether this coroutine is still wanted
        ensureActive()
        value = info?.takeIf { com.lagradost.desktop.tmdb.Tmdb.sameTitle(card.name, it.title) }?.backdrop
        resumeBackdrops[card.name] = value ?: ""
    }
    val thumb = stored ?: backdrop ?: card.posterUrl
    val sourceName = com.lagradost.desktop.stats.ResumeExtras.sourceOf(r?.parentId)
    val fraction = card.watchedFraction()
    com.lagradost.desktop.ui.fluent.ContextMenuArea(menu) {
        Column(Modifier.width(width).hoverable(source).fluentClickable(source, true, shape, androidx.compose.ui.semantics.Role.Button) { openCard(card) }) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer { scaleX = lift; scaleY = lift }.clip(shape).background(c.card)) {
                // colour at once: the poster, decoded tiny; the wide picture crossfades over it when it has come
                if (thumb != card.posterUrl) com.lagradost.desktop.ui.components.SoftImage(card.posterUrl, card.posterHeaders, Modifier.fillMaxSize(), alpha = 0.8f)
                RemoteImage(thumb, if (thumb == card.posterUrl) card.posterHeaders else null, null, Modifier.fillMaxSize(), ContentScale.Crop, alignment = if (thumb == card.posterUrl) Alignment.TopCenter else Alignment.Center)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000))))
                // a quiet play disc on hover
                if (hovered) Box(Modifier.align(Alignment.Center).size(46.dp).background(Color(0x66000000), CircleShape), contentAlignment = Alignment.Center) {
                    com.lagradost.desktop.ui.fluent.Icon(Icons.Play, size = 18.dp, tint = Color.White)
                }
                // under the pointer: a round button to take the title off the list (the source it was watched with is shown otherwise)
                if (hovered && remove != null) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        com.lagradost.desktop.ui.fluent.SkinCircleButton(Icons.Close, "Remove from Continue watching", remove, size = 30.dp)
                    }
                } else sourceName?.let { name ->
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).widthIn(max = width * 0.6f).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        FText(name, style = Fluent.type.caption, color = Color.White, maxLines = 1, softWrap = false)
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = if (fraction != null) 12.dp else 10.dp)) {
                    FText(card.name, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1)
                    if (!subtitle.isNullOrBlank()) FText(subtitle, style = Fluent.type.caption, color = Color(0xCCFFFFFF), maxLines = 1)
                }
                fraction?.let { f ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) { com.lagradost.desktop.ui.fluent.ProgressBar(f, height = 3.dp, color = Color.White.copy(alpha = 0.92f)) }
                }
            }
        }
    }
}
