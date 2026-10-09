package com.lagradost.desktop.ui.screens.home

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

private val gutter = 36.dp

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

    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.68f).coerceIn(380.dp, 720.dp)
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 } }
        TopBarOverlay(scrolled)
        val cardWidth = com.lagradost.desktop.ui.fluent.Appearance.posterSize.width * (if (maxWidth >= 1008.dp) 1.05f else 0.92f)
        val space = com.lagradost.desktop.ui.fluent.Appearance.space(30.dp)

        FluentScrollbar(listState, com.lagradost.desktop.ui.shell.TopBarHeight)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
            item(key = "hero") {
                val res = preview
                val items = (res as? Resource.Success)?.value?.second.orEmpty()
                if (!com.lagradost.desktop.ui.fluent.Appearance.homeBanner) {
                    Box(Modifier.height(com.lagradost.desktop.ui.shell.TopBarHeight + 8.dp))
                } else if (items.isNotEmpty()) {
                    HomeHero(items, heroHeight, compact = maxWidth < 900.dp)
                } else if (res == null || res is Resource.Loading) {
                    HeroSkeleton(heroHeight)
                } else Box(Modifier.height(72.dp))
            }
            item(key = "header") {
                HomeHeader(vm, apiName, (page as? Resource.Success)?.value?.entries?.filter { it.value.list.list.isNotEmpty() }?.map { it.key }.orEmpty())
            }
            val resumeList = resume.orEmpty()
            if (resumeList.isNotEmpty() && com.lagradost.desktop.ui.fluent.Appearance.homeContinue) {
                item(key = "resume") {
                    Column(Modifier.padding(bottom = space)) {
                        com.lagradost.desktop.ui.fluent.RichSectionHeader("Continue watching", Modifier.padding(horizontal = gutter), subtitle = "Pick up where you left off", onSeeAll = { Navigator.go(Route.History) })
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
                    Column(Modifier.padding(bottom = space)) {
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
                    rows.forEach { (name, row) ->
                        item(key = "row-$name") {
                            Column(Modifier.padding(bottom = space)) {
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
            item(key = "end") { Box(Modifier.height(40.dp)) }
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
private fun HomeHero(items: List<LoadResponse>, height: Dp, compact: Boolean) {
    var index by remember(items.size) { mutableStateOf(0) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    // the shown item's thumbnail fills while it is on screen; hovering the hero pauses the rotation
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(index, hovered, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        if (hovered) return@LaunchedEffect
        progress.animateTo(1f, tween(((1f - progress.value) * HERO_MS).toInt().coerceAtLeast(1), easing = androidx.compose.animation.core.LinearEasing))
        progress.snapTo(0f)
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
        Crossfade(current, animationSpec = com.lagradost.desktop.ui.fluent.FluentMotion.tweenStd(900), label = "heroBackdrop") { item -> HeroBackdrop(item, art[item.url]?.backdrop) }
        AnimatedContent(
            current,
            transitionSpec = {
                (fadeIn(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(420)) + androidx.compose.animation.slideInHorizontally(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(520)) { -it / 14 }) togetherWith
                    fadeOut(com.lagradost.desktop.ui.fluent.FluentMotion.tweenOut(160))
            },
            label = "heroText",
        ) { item -> HeroText(item, compact, height, art[item.url]?.logo) }
        if (items.size > 1 && !compact) {
            com.lagradost.desktop.ui.fluent.FeaturedStrip(
                count = items.size.coerceAtMost(6), selected = index.coerceAtMost(5),
                progress = { if (hovered) 1f else progress.value },
                image = { i -> (items[i].backgroundPosterUrl ?: items[i].posterUrl) to items[i].posterHeaders },
                onSelect = { i -> if (i != index) { scope.launch { progress.snapTo(0f) }; index = i } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = gutter, bottom = 34.dp),
            )
        } else if (items.size > 1) {
            Row(Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { i, _ ->
                    val w by androidx.compose.animation.core.animateDpAsState(if (i == index) 26.dp else 8.dp, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(320), label = "dot")
                    Box(Modifier.size(w, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (i == index) Fluent.colors.accent else Color(0x55FFFFFF)).clickableNoRipple { index = i })
                }
            }
        }
    }
}

private const val HERO_MS = 9000

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

/** Backdrop of the hero: still artwork, text scrims, and a fade into the page at the bottom (any backdrop style) */
@Composable
private fun HeroBackdrop(item: LoadResponse, tmdbBackdrop: String? = null) {
    val zoom: androidx.compose.runtime.State<Float>? = null
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.6f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            },
    ) {
        val backdrop = item.backgroundPosterUrl ?: tmdbBackdrop
        Box(Modifier.fillMaxSize().graphicsLayer { val s = zoom?.value ?: 1f; scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.4f) }) {
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
    // the thumbnail strip is at the right: on a wide window the text can go lower, beside it
    val wide = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width / androidx.compose.ui.platform.LocalDensity.current.density >= 1150f
    val bottomPad = if (compact) 52.dp else if (wide) 64.dp else 132.dp
    // what is left below the top bar: the block never reaches under it (full 346 dp, without the synopsis 254, short 214)
    val room = height - com.lagradost.desktop.ui.shell.TopBarHeight - bottomPad - 8.dp
    val showPlot = room >= 346.dp
    val showMeta = room >= 230.dp
    val tight = room < 230.dp
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = bottomPad).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                typeLabel(item.type)?.let { com.lagradost.desktop.ui.fluent.ArtChip(it, accent = true) }
                item.contentRating?.let { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                item.year?.let { com.lagradost.desktop.ui.fluent.ArtChip(it.toString()) }
                item.score?.let { com.lagradost.desktop.ui.fluent.ArtChip("★ " + it.toString(10, 1)) }
            }
            val logo = item.logoUrl ?: tmdbLogo
            if (logo != null) {
                RemoteImage(logo, item.posterHeaders, item.name, Modifier.height(if (tight) 64.dp else if (compact) 80.dp else 110.dp).widthIn(max = 460.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
            } else {
                FText(item.name, style = (if (compact) Fluent.type.title else Fluent.type.titleLarge).copy(fontSize = if (compact) 34.sp else 52.sp, lineHeight = if (compact) 40.sp else 60.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 16f)), color = Color.White, maxLines = if (tight || !showPlot) 1 else 2)
            }
            val meta = buildList {
                item.duration?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min") }
                item.tags?.take(4)?.forEach { add(it) }
            }
            if (showMeta && meta.isNotEmpty()) FText(meta.joinToString("   •   "), style = Fluent.type.bodyStrong, color = Color(0xD9FFFFFF), maxLines = 1)
            if (showPlot) item.plot?.takeIf { it.isNotBlank() }?.let { FText(stripHtml(it), style = Fluent.type.bodyLarge.copy(lineHeight = 26.sp), color = Color(0xCCFFFFFF), maxLines = if (compact) 2 else 3) }
            Box(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.lagradost.desktop.ui.fluent.PillButton("Play", Icons.Play, primary = true, onClick = { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl, START_ACTION_RESUME_LATEST)) }, height = 48.dp)
                com.lagradost.desktop.ui.fluent.PillButton("More info", Icons.Info, primary = false, onClick = { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl)) }, height = 48.dp)
            }
        }
    }
}

internal fun stripHtml(html: String): String =
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()
