package com.lagradost.desktop.ui.screens.home

import com.lagradost.desktop.ui.fluent.fadeIntoPage
import com.lagradost.desktop.ui.fluent.reveal
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
import androidx.compose.foundation.lazy.LazyColumn
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
        val heroHeight = (maxHeight * 0.8f).coerceIn(440.dp, 840.dp)
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 } }
        TopBarOverlay(scrolled)
        val cardWidth = com.lagradost.desktop.ui.fluent.Appearance.posterSize.width * (if (maxWidth >= 1008.dp) 1.05f else 0.92f)
        val space = com.lagradost.desktop.ui.fluent.Appearance.space(38.dp)

        FluentScrollbar(listState, com.lagradost.desktop.ui.shell.TopBarHeight)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().smoothWheel(listState), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
            item(key = "hero") {
                val res = preview
                val items = (res as? Resource.Success)?.value?.second.orEmpty()
                if (!com.lagradost.desktop.ui.fluent.Appearance.homeBanner) {
                    Box(Modifier.height(com.lagradost.desktop.ui.shell.TopBarHeight + 8.dp))
                } else if (items.isNotEmpty()) {
                    HomeHero(items, heroHeight, compact = maxWidth < 900.dp, scroll = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE })
                } else if (res == null || res is Resource.Loading) {
                    HeroSkeleton(heroHeight)
                } else Box(Modifier.height(72.dp))
            }
            // the other looks keep their "Browse" header with the customise and reload buttons
            if (com.lagradost.desktop.ui.fluent.Appearance.navPosition.let { it != com.lagradost.desktop.ui.fluent.NavPosition.Floating && it != com.lagradost.desktop.ui.fluent.NavPosition.Dock }) item(key = "header") {
                HomeHeader(vm, apiName, rowNames)
            }
            val resumeList = resume.orEmpty()
            if (resumeList.isNotEmpty() && com.lagradost.desktop.ui.fluent.Appearance.homeContinue) {
                item(key = "resume") {
                    Column(Modifier.reveal("home/$apiName/resume", 60).padding(bottom = space)) {
                        com.lagradost.desktop.ui.fluent.RichSectionHeader("Continue watching", Modifier.padding(horizontal = gutter), onSeeAll = { Navigator.go(Route.History) })
                        Box(Modifier.height(12.dp))
                        // plain posters like every other row: the bar along the bottom is how far you got, the line below what is next
                        Shelf(resumeList, cardWidth, gutter = gutter, spacing = 14.dp, key = { (it.id ?: it.url.hashCode()).toString() + it.name }) { card ->
                            val r = card as? DataStoreHelper.ResumeWatchingResult
                            PosterCard(
                                card, { openCard(card) }, cardWidth,
                                subtitle = resumeSubtitle(card),
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
                    Column(Modifier.reveal("home/$apiName/bookmarks", 120).padding(bottom = space)) {
                        BookmarksHeader(vm, statusTypes)
                        Box(Modifier.height(12.dp))
                        Shelf(bm.second, cardWidth, gutter = gutter, spacing = 14.dp, key = { it.url }) { card ->
                            PosterCard(card, { openCard(card) }, cardWidth)
                        }
                    }
                }
            }
            when (val res = page) {
                null, is Resource.Loading -> items(3, key = { "sk$it" }) { SkeletonRow(cardWidth) }
                is Resource.Failure -> item(key = "error") { ErrorRow(res.errorString, vm) }
                is Resource.Success -> {
                    val rows = HomeLayout.rowsFor(apiName.orEmpty(), res.value.entries.filter { it.value.list.list.isNotEmpty() }.map { it.key to it.value })
                    if (rows.isEmpty()) item(key = "empty") { EmptyHome() }
                    rows.forEachIndexed { rowIndex, (name, row) ->
                        item(key = "row-$name") {
                            Column(Modifier.reveal("home/$apiName/row-$name", 60 + rowIndex.coerceAtMost(4) * 70).padding(bottom = space)) {
                                val chart = chartSize(name)
                                val ranked = chart != null && !row.list.isHorizontalImages
                                com.lagradost.desktop.ui.fluent.RichSectionHeader(
                                    name, Modifier.padding(horizontal = gutter),
                                    onSeeAll = { Navigator.go(Route.Section(name, row.list.list)) },
                                )
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
                                        PosterCard(card, { openCard(card) }, w, landscape = landscape)
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
                val inHeader = com.lagradost.desktop.ui.fluent.Appearance.navPosition.let { it != com.lagradost.desktop.ui.fluent.NavPosition.Floating && it != com.lagradost.desktop.ui.fluent.NavPosition.Dock }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 40.dp), contentAlignment = Alignment.Center) {
                    if (customise != null && !inHeader) Button("Customise Home rows", customise, kind = ButtonKind.Subtle, icon = Icons.Edit, height = 34.dp)
                }
            }
        }
    }
}

/** "S1 · E3 · 40 min left" (a film: "26 min left") */
private fun resumeSubtitle(card: SearchResponse): String? {
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
            IconButton(Icons.Refresh, { vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = true, fromUI = false) }, tooltip = "Reload home page", kind = ButtonKind.Standard, size = 36.dp)
        }
    }
}

@Composable
private fun BookmarksHeader(vm: HomeViewModel, statusTypes: Pair<Set<WatchType>, Set<WatchType>>?) {
    val ctx = DesktopBootstrap.activityOrNull()
    val selected = statusTypes?.first.orEmpty()
    val types = statusTypes?.second?.sortedBy { it.internalId }.orEmpty()
    com.lagradost.desktop.ui.fluent.RichSectionHeader(
        "Your library", Modifier.padding(horizontal = gutter),
        trailing = {
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
        },
    )
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

@Composable
private fun ErrorRow(message: String, vm: HomeViewModel) {
    com.lagradost.desktop.ui.fluent.EmptyState(Icons.Warning, "The home page could not be loaded", message) {
        Button("Try again", { vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = true) }, kind = ButtonKind.Accent, icon = Icons.Refresh, height = 36.dp)
    }
}

@Composable
private fun EmptyHome() {
    com.lagradost.desktop.ui.fluent.EmptyState(Icons.Extensions, "Nothing to show yet", "Install an extension repository, then pick a provider at the top right.") {
        Button("Open Extensions", { Navigator.goTab(com.lagradost.desktop.core.Tab.Extensions) }, kind = ButtonKind.Accent, icon = Icons.Extensions, height = 36.dp)
    }
}

// -------------------------------------------------------------------------------------------
// Hero
// -------------------------------------------------------------------------------------------

@Composable
private fun HeroSkeleton(height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).shimmer()) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(90.dp, 22.dp).clip(RoundedCornerShape(50)).background(Fluent.colors.card))
            Box(Modifier.size(420.dp, 52.dp).clip(RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.control)).background(Fluent.colors.card))
            Box(Modifier.size(520.dp, 18.dp).clip(RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.control)).background(Fluent.colors.card))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(130.dp, 44.dp).clip(RoundedCornerShape(50)).background(Fluent.colors.card))
                Box(Modifier.size(150.dp, 44.dp).clip(RoundedCornerShape(50)).background(Fluent.colors.card))
            }
        }
    }
}

@Composable
private fun HomeHero(items: List<LoadResponse>, height: Dp, compact: Boolean, scroll: () -> Float) {
    var index by remember(items.size) { mutableStateOf(0) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    // the shown item's thumbnail fills while it is on screen; hovering the hero pauses the rotation
    // in steps of a few frames a second, not one per frame: an animation that runs all the time makes the whole window redraw 60 times a second
    // (a third of a core and the GPU for a thin line), and the pages are redrawn as a whole
    val progress = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(index, hovered, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        if (hovered) return@LaunchedEffect
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
            if (art.containsKey(item.url) || (item.backgroundPosterUrl != null && item.logoUrl != null)) continue
            runCatching { com.lagradost.desktop.tmdb.Tmdb.info(item.name, item.year, item.type?.isMovieType() == true, item.syncData.values) }.getOrNull()?.let { art[item.url] = it }
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
                    items.take(8).forEachIndexed { i, _ ->
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
                    count = items.size.coerceAtMost(7), selected = index, progress = { if (hovered) 1f else progress.floatValue },
                    image = { i -> val it = items[i]; (it.backgroundPosterUrl ?: art[it.url]?.backdrop ?: it.posterUrl) to it.posterHeaders },
                    onSelect = { i -> progress.floatValue = 0f; index = i },
                    label = { i -> items[i].name },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = gutter, bottom = 34.dp).graphicsLayer { alpha = (1f - scroll() / (height.toPx() * 0.4f)).coerceIn(0f, 1f) },
                )
            }
        }
    }
}

private const val HERO_MS = 9000
private const val HERO_TICK_MS = 120

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

/** Backdrop of the hero: still artwork, text scrims, and a fade into the page at the bottom (any backdrop style) */
@Composable
private fun HeroBackdrop(item: LoadResponse, tmdbBackdrop: String? = null, scroll: () -> Float = { 0f }) {
    val zoom: androidx.compose.runtime.State<Float>? = null
    Box(
        Modifier.fillMaxSize().fadeIntoPage(),
    ) {
        val backdrop = item.backgroundPosterUrl ?: tmdbBackdrop
        // the picture trails the page a little (parallax): it is shifted down by a part of the scroll, under the fade at the bottom
        Box(Modifier.fillMaxSize().graphicsLayer { val s = zoom?.value ?: 1f; scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.4f); translationY = scroll().coerceAtMost(size.height) * 0.3f }) {
            if (backdrop != null) {
                RemoteImage(backdrop, item.posterHeaders, null, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                com.lagradost.desktop.ui.components.SoftImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize(), alpha = 0.6f)
            }
        }
        if (backdrop == null) {
            // sharp poster on the right when the provider has no backdrop
            Box(Modifier.fillMaxSize().padding(end = 96.dp, top = 84.dp, bottom = 150.dp), contentAlignment = Alignment.CenterEnd) {
                val shape = RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.card)
                RemoteImage(item.posterUrl, item.posterHeaders, null, Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(shape), ContentScale.Crop)
            }
        }
        // scrims: left for the text, top for the title bar
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Fluent.colors.bg.copy(alpha = 0.95f), 0.38f to Fluent.colors.bg.copy(alpha = 0.7f), 0.7f to Fluent.colors.bg.copy(alpha = 0.2f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
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
            if (logo != null) {
                RemoteImage(logo, item.posterHeaders, item.name, Modifier.height(if (tight) 72.dp else if (compact) 92.dp else 128.dp).widthIn(max = 520.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
            } else {
                FText(item.name, style = (if (compact) Fluent.type.title else Fluent.type.titleLarge).copy(fontSize = if (compact) 36.sp else 56.sp, lineHeight = if (compact) 42.sp else 62.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 16f)), color = Color.White, maxLines = if (tight || !showPlot) 1 else 2)
            }
            // one line: score, age rating, what it is
            val tags = buildList {
                item.year?.let { add(it.toString()) }
                item.duration?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min") }
                item.tags?.take(3)?.forEach { add(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                item.score?.let { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.lagradost.desktop.ui.fluent.Icon(Icons.StarFilled, size = 15.dp, tint = Color(0xFFFFC53D))
                        FText(s.toString(10, 1), style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1)
                    }
                }
                item.contentRating?.takeIf { it.isNotBlank() }?.let { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                if (tags.isNotEmpty()) FText(tags.joinToString("  •  "), style = Fluent.type.bodyStrong, color = Color(0xE6FFFFFF), maxLines = 1)
            }
            if (showPlot) item.plot?.takeIf { it.isNotBlank() }?.let { FText(stripHtml(it), style = Fluent.type.bodyLarge.copy(lineHeight = 26.sp), color = Color(0xCCFFFFFF), maxLines = if (compact) 2 else 3) }
            Box(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.lagradost.desktop.ui.fluent.PillButton("Watch now", Icons.Play, primary = true, onClick = { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl, START_ACTION_RESUME_LATEST)) }, height = 50.dp)
                com.lagradost.desktop.ui.fluent.GlassCircleButton(Icons.Info, "More info", { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl)) }, size = 50.dp)
            }
        }
    }
}

internal fun stripHtml(html: String): String =
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()
