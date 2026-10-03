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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
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

private val gutter = 24.dp

/** Opens a card: a continue-watching entry resumes its episode, anything else opens the title page */
fun openCard(card: SearchResponse) {
    if (card is DataStoreHelper.ResumeWatchingResult && !card.isFromDownload && card.id != null) {
        Navigator.openDetails(card, START_ACTION_LOAD_EP, card.id)
    } else Navigator.openDetails(card)
}

@Composable
fun HomeScreen() {
    val c = Fluent.colors
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
        val heroHeight = (maxHeight * 0.52f).coerceIn(340.dp, 560.dp)
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
        TopBarOverlay(scrolled)
        val cardWidth = if (maxWidth >= 1008.dp) 168.dp else 148.dp

        FluentScrollbar(listState, com.lagradost.desktop.ui.shell.TopBarHeight)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                val res = preview
                val items = (res as? Resource.Success)?.value?.second.orEmpty()
                if (items.isNotEmpty()) {
                    HomeHero(items, heroHeight)
                } else if (res == null || res is Resource.Loading) {
                    HeroSkeleton(heroHeight)
                } else Box(Modifier.height(60.dp))
            }
            item(key = "header") {
                HomeHeader(vm, apiName)
            }
            val resumeList = resume.orEmpty()
            if (resumeList.isNotEmpty()) {
                item(key = "resume") {
                    Column(Modifier.padding(bottom = 24.dp)) {
                        SectionHeader("Continue watching", Modifier.padding(horizontal = gutter))
                        Box(Modifier.height(8.dp))
                        Shelf(resumeList, cardWidth, key = { (it.id ?: it.url.hashCode()).toString() + it.name }) { card ->
                            PosterCard(
                                card, { openCard(card) }, cardWidth,
                                subtitle = resumeSubtitle(card),
                                menu = {
                                    listOf(
                                        MenuItem("Resume", Icons.Play) { openCard(card) },
                                        MenuItem("Open title page", Icons.Info) { Navigator.openDetails(card) },
                                        MenuItem("Remove from list", Icons.Delete, destructive = true) {
                                            (card as? DataStoreHelper.ResumeWatchingResult)?.let {
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
                    Column(Modifier.padding(bottom = 24.dp)) {
                        BookmarksHeader(vm, statusTypes)
                        Box(Modifier.height(8.dp))
                        Shelf(bm.second, cardWidth, key = { it.url }) { card ->
                            PosterCard(card, { openCard(card) }, cardWidth)
                        }
                    }
                }
            }
            when (val res = page) {
                null, is Resource.Loading -> items(3, key = { "sk$it" }) { SkeletonRow(cardWidth) }
                is Resource.Failure -> item(key = "error") { ErrorRow(res.errorString, vm) }
                is Resource.Success -> {
                    val rows = res.value.entries.filter { it.value.list.list.isNotEmpty() }
                    if (rows.isEmpty()) item(key = "empty") { EmptyHome() }
                    rows.forEach { (name, row) ->
                        item(key = "row-$name") {
                            Column(Modifier.padding(bottom = 24.dp)) {
                                SectionHeader(
                                    name, Modifier.padding(horizontal = gutter),
                                    onSeeAll = { Navigator.go(Route.Section(name, row.list.list)) },
                                )
                                Box(Modifier.height(8.dp))
                                val landscape = row.list.isHorizontalImages
                                val w = if (landscape) 280.dp else cardWidth
                                val state = rememberLazyListState()
                                if (row.hasNext) {
                                    LaunchedEffect(state, row.list.list.size) {
                                        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                                            if (last >= row.list.list.size - 3 && row.hasNext) vm.expand(name)
                                        }
                                    }
                                }
                                Shelf(row.list.list, w, state = state, key = { it.url }) { card ->
                                    PosterCard(card, { openCard(card) }, w, landscape = landscape)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "end") { Box(Modifier.height(32.dp)) }
        }
    }
}

private fun resumeSubtitle(card: SearchResponse): String? {
    val r = card as? DataStoreHelper.ResumeWatchingResult ?: return null
    val ep = r.episode
    val season = r.season
    val left = r.watchPos?.let { ((it.duration - it.position) / 60_000L).coerceAtLeast(0) }
    val label = when {
        ep != null && season != null -> "S$season · E$ep"
        ep != null -> "Episode $ep"
        else -> typeLabel(r.type)
    }
    return if (left != null && left > 0) "$label · ${left} min left" else label
}

// -------------------------------------------------------------------------------------------
// Header (provider switcher)
// -------------------------------------------------------------------------------------------

@Composable
private fun HomeHeader(vm: HomeViewModel, apiName: String?) {
    val page by vm.page.observeAsState()
    val names = remember(page, apiName) {
        val ctx = DesktopBootstrap.activityOrNull()
        val providers = runCatching { ctx?.filterProviderByPreferredMedia()?.map { it.name }?.sorted() }.getOrNull().orEmpty()
        listOf(APIRepository.randomApi.name) + providers
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FText("Browse", Modifier.weight(1f), style = Fluent.type.title)
        IconButton(Icons.Refresh, { vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = true, fromUI = false) }, tooltip = "Reload home page", kind = ButtonKind.Standard)
    }
}

@Composable
private fun BookmarksHeader(vm: HomeViewModel, statusTypes: Pair<Set<WatchType>, Set<WatchType>>?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FText("Bookmarks", style = Fluent.type.subtitle)
        Box(Modifier.width(8.dp))
        val ctx = DesktopBootstrap.activityOrNull()
        val selected = statusTypes?.first.orEmpty()
        statusTypes?.second?.sortedBy { it.internalId }?.forEach { type ->
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


// -------------------------------------------------------------------------------------------
// States
// -------------------------------------------------------------------------------------------

@Composable
private fun SkeletonRow(cardWidth: Dp) {
    Column(Modifier.padding(bottom = 24.dp, start = gutter)) {
        Box(Modifier.size(180.dp, 22.dp).clip(RoundedCornerShape(4.dp)).background(Fluent.colors.card))
        Box(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(8) { PosterSkeleton(cardWidth) } }
    }
}

@Composable
private fun ErrorRow(message: String, vm: HomeViewModel) {
    Column(Modifier.fillMaxWidth().padding(gutter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        com.lagradost.desktop.ui.fluent.Icon(Icons.Warning, size = 32.dp, tint = Fluent.colors.caution)
        FText("The home page could not be loaded", style = Fluent.type.subtitle)
        FText(message, color = Fluent.colors.textSecondary, maxLines = 4)
        Button("Try again", { vm.loadAndCancel(DataStoreHelper.currentHomePage, forceReload = true) }, kind = ButtonKind.Accent, icon = Icons.Refresh)
    }
}

@Composable
private fun EmptyHome() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.lagradost.desktop.ui.fluent.Icon(Icons.Extensions, size = 40.dp, tint = Fluent.colors.textTertiary)
        FText("Nothing to show yet", style = Fluent.type.subtitle)
        FText("Install an extension repository, then pick a provider above.", color = Fluent.colors.textSecondary)
        Button("Open Extensions", { Navigator.goTab(com.lagradost.desktop.core.Tab.Extensions) }, kind = ButtonKind.Accent, icon = Icons.Extensions)
    }
}

// -------------------------------------------------------------------------------------------
// Hero
// -------------------------------------------------------------------------------------------

@Composable
private fun HeroSkeleton(height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).background(Fluent.colors.card), contentAlignment = Alignment.Center) { ProgressRing() }
}

@Composable
private fun HomeHero(items: List<LoadResponse>, height: Dp) {
    var index by remember(items.size) { mutableStateOf(0) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(index, hovered, items.size) {
        if (!hovered && items.size > 1) {
            delay(9000)
            index = (index + 1) % items.size
        }
    }
    Box(Modifier.fillMaxWidth().height(height).hoverable(source)) {
        val current = items[index.coerceIn(0, items.lastIndex)]
        Crossfade(current, animationSpec = tween(600), label = "heroBackdrop") { item -> HeroBackdrop(item) }
        AnimatedContent(
            current,
            transitionSpec = { fadeIn(tween(300, delayMillis = 250)) togetherWith fadeOut(tween(180)) },
            label = "heroText",
        ) { item -> HeroText(item, height < 430.dp) }
        if (items.size > 1) {
            Row(Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { i, _ ->
                    val active = i == index
                    Box(
                        Modifier
                            .size(if (active) 22.dp else 8.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) Color.White else Color(0x66FFFFFF))
                            .clickableNoRipple { index = i },
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

@Composable
private fun HeroBackdrop(item: LoadResponse) {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        val backdrop = item.backgroundPosterUrl
        if (backdrop != null) {
            RemoteImage(backdrop, item.posterHeaders, null, Modifier.fillMaxSize(), ContentScale.Crop)
        } else {
            com.lagradost.desktop.ui.components.SoftImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize(), alpha = 0.55f)
            // sharp poster on the right when the provider has no backdrop
            Box(Modifier.fillMaxSize().padding(end = 64.dp, top = 72.dp, bottom = 40.dp), contentAlignment = Alignment.CenterEnd) {
                RemoteImage(item.posterUrl, item.posterHeaders, null, Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)), ContentScale.Crop)
            }
        }
        // scrims: left for text, top for the title bar, bottom to blend with the page
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE6101010), 0.55f to Color(0x99101010), 1f to Color.Transparent)))
        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to c.layer)))
    }
}

@Composable
private fun HeroText(item: LoadResponse, compact: Boolean) {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = if (compact) 44.dp else 56.dp).widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                typeLabel(item.type)?.let { Badge(it, accent = true) }
                item.contentRating?.let { Badge(it) }
            }
            val logo = item.logoUrl
            if (logo != null) {
                RemoteImage(logo, item.posterHeaders, item.name, Modifier.height(88.dp).widthIn(max = 380.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
            } else {
                FText(item.name, style = if (compact) Fluent.type.title else Fluent.type.titleLarge, color = Color.White, maxLines = 2)
            }
            val meta = buildList {
                item.score?.let { add("★ " + it.toString(10, 1)) }
                item.year?.let { add(it.toString()) }
                item.duration?.takeIf { it > 0 }?.let { add("$it min") }
                item.tags?.take(3)?.forEach { add(it) }
            }
            if (meta.isNotEmpty()) FText(meta.joinToString("  ·  "), color = Color(0xCCFFFFFF), maxLines = 1)
            item.plot?.takeIf { it.isNotBlank() }?.let { FText(stripHtml(it), style = Fluent.type.bodyLarge, color = Color(0xE6FFFFFF), maxLines = if (compact) 2 else 3) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button("Play", { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl, START_ACTION_RESUME_LATEST)) }, kind = ButtonKind.Accent, icon = Icons.Play, height = 36.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp))
                Button("More info", { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl)) }, icon = Icons.Info, height = 36.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp))
            }
        }
    }
}

internal fun stripHtml(html: String): String =
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()
