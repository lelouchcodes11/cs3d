package com.lagradost.desktop.ui.screens.details

import com.lagradost.desktop.ui.fluent.smoothWheel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lagradost.desktop.ui.fluent.Chip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.lagradost.desktop.ui.fluent.shimmer
import com.lagradost.desktop.ui.fluent.glass
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.ACTION_CLICK_DEFAULT
import com.lagradost.cloudstream3.ui.result.ACTION_DOWNLOAD_EPISODE
import com.lagradost.cloudstream3.ui.result.ACTION_MARK_AS_WATCHED
import com.lagradost.cloudstream3.ui.result.ACTION_MARK_WATCHED_UP_TO_THIS_EPISODE
import com.lagradost.cloudstream3.ui.result.ACTION_PLAY_EPISODE_IN_PLAYER
import com.lagradost.cloudstream3.ui.result.ACTION_RELOAD_EPISODE
import com.lagradost.cloudstream3.ui.result.ACTION_SHOW_OPTIONS
import com.lagradost.cloudstream3.ui.result.AutoResume
import com.lagradost.cloudstream3.ui.result.EpisodeClickEvent
import com.lagradost.cloudstream3.ui.result.ResultData
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.desktop.ui.fluent.fadeIntoPage
import com.lagradost.cloudstream3.ui.result.getWatchProgress
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.SelectPopup
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.cloudstream3.ui.result.callback
import com.lagradost.cloudstream3.ui.result.getRealPosition
import com.lagradost.cloudstream3.ui.result.getTitle
import com.lagradost.cloudstream3.ui.result.getOptions
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.core.Entry
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.Badge
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.ContextMenuArea
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuEntry
import com.lagradost.desktop.ui.fluent.MenuFlyout
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.MenuSeparator
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.SectionHeader
import com.lagradost.desktop.ui.fluent.Shelf
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.Toasts
import kotlinx.coroutines.launch
import com.lagradost.desktop.ui.screens.home.openCard
import com.lagradost.desktop.ui.screens.home.stripHtml
import com.lagradost.desktop.ui.shell.TopBarOverlay

/** Where the text of the page starts: 36 dp, and after the floating dock when that floats over the artwork */
private val gutter: androidx.compose.ui.unit.Dp
    @Composable get() = 36.dp + com.lagradost.desktop.ui.shell.LocalDockStart.current

private fun UiText?.str(): String? = this?.let { runCatching { it.asStringNull(DesktopBootstrap.activity) }.getOrNull() }

@Composable
fun DetailsScreen(entry: Entry, route: Route.Details) {
    val ctx = DesktopBootstrap.activity
    val vm = remember(entry.id) { entry.vms.get<ResultViewModel2>() }
    val sync = remember(entry.id) { entry.vms.get<SyncViewModel>() }

    LaunchedEffect(entry.id) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val fillers = prefs.getBoolean(ctx.getString(R.string.show_fillers_key), false)
        val dub = if (ctx.getApiDubstatusSettings().contains(DubStatus.Dubbed)) DubStatus.Dubbed else DubStatus.Subbed
        val start = if (route.startAction != 0 && !entry.autoStartDone) AutoResume(season = null, episode = null, id = route.startValue, startAction = route.startAction) else null
        entry.autoStartDone = true
        vm.load(ctx, route.url, route.apiName, fillers, dub, start)
        sync.addFromUrl(route.url)
    }
    DisposableEffect(entry.id) {
        val listener = { _: Int? -> sync.updateUserData(); vm.reloadEpisodes() }
        ResultFragment.updateUIEvent += listener
        onDispose { ResultFragment.updateUIEvent -= listener; vm.clear() }
    }

    val page by vm.page.observeAsState()
    val popup by vm.selectPopup.observeAsState()
    val links by vm.loadedLinks.observeAsState()
    // the tracker ids of the title (AniList / MAL / Simkl): what the status card below and the player's episode reports use
    val loadedSyncData = (page as? Resource.Success)?.value?.syncData
    LaunchedEffect(loadedSyncData) {
        if (loadedSyncData != null && sync.addSyncs(loadedSyncData)) {
            sync.updateMetaAndUser()
            sync.updateSynced()
        }
    }

    // engine questions ("which source?", "which action?") are Fluent dialogs
    LaunchedEffect(popup) {
        val p = popup ?: return@LaunchedEffect
        showSelectPopup(p)
    }

    Box(Modifier.fillMaxSize()) {
        when (val res = page) {
            null, is Resource.Loading -> LoadingPage(route)
            is Resource.Failure -> FailurePage(route, res.errorString) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                vm.load(ctx, route.url, route.apiName, prefs.getBoolean(ctx.getString(R.string.show_fillers_key), false),
                    if (ctx.getApiDubstatusSettings().contains(DubStatus.Dubbed)) DubStatus.Dubbed else DubStatus.Subbed, null)
            }
            is Resource.Success -> DetailsContent(vm, sync, route, res.value)
        }
        links?.let { LinkLoadingCard(it.linksLoaded, it.subsLoaded, { vm.skipLoading() }, { vm.cancelLinks() }) }
    }
}

private fun showSelectPopup(p: SelectPopup) {
    val ctx = DesktopBootstrap.activity
    val title = p.getTitle(ctx)
    val options = p.getOptions(ctx)
    var done = false
    Overlays.show(
        Overlays.Dialog(
            title = title, close = "Cancel", width = 420.dp,
            onClose = { if (!done) { done = true; p.callback(null) } },
        ) { dismiss ->
            LazyColumn(Modifier.fillMaxWidth()) {
                items(options.size) { i ->
                    val source = rememberInteraction()
                    val hovered by source.collectIsHoveredAsState()
                    val shape = RoundedCornerShape(4.dp)
                    Box(
                        Modifier.fillMaxWidth().height(40.dp).clip(shape)
                            .background(if (hovered) Fluent.colors.subtleHover else Color.Transparent, shape)
                            .fluentClickable(source, true, shape, Role.Button) { if (!done) { done = true; dismiss(); p.callback(i) } }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { FText(options[i], maxLines = 1) }
                }
            }
        },
    )
}

// -------------------------------------------------------------------------------------------

@Composable
private fun LoadingPage(route: Route.Details) {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize()) {
        com.lagradost.desktop.ui.components.SoftImage(route.poster, null, Modifier.fillMaxSize(), alpha = 0.35f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), c.layer))))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(size = 48.dp)
            FText(route.name, style = Fluent.type.subtitle, maxLines = 2)
            FText("Loading details…", color = c.textSecondary)
        }
    }
}

@Composable
private fun FailurePage(route: Route.Details, message: String, retry: () -> Unit) {
    val c = Fluent.colors
    Column(
        Modifier.fillMaxSize().padding(gutter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Warning, size = 40.dp, tint = c.caution)
        Box(Modifier.height(12.dp))
        FText("Could not load “${route.name}”", style = Fluent.type.subtitle, maxLines = 2)
        Box(Modifier.height(8.dp))
        FText(message, color = c.textSecondary, maxLines = 5, modifier = Modifier.widthIn(max = 560.dp))
        Box(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Try again", retry, kind = ButtonKind.Accent, icon = Icons.Refresh)
            if (route.url.startsWith("http")) Button("Open in browser", { DesktopPlatform.openExternalBrowser(route.url) }, icon = Icons.OpenInNewWindow)
            Button("Back", { Navigator.back() })
        }
    }
}

@Composable
private fun LinkLoadingCard(links: Int, subs: Int, skip: () -> Unit, cancel: () -> Unit) {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize().padding(bottom = 36.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.glass(FluentShapes.overlay, elevation = 28.dp, strong = true).padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressRing(size = 26.dp, strokeWidth = 3.dp)
            Column {
                FText("Finding sources…", style = Fluent.type.bodyStrong)
                FText("$links link${if (links == 1) "" else "s"}  ·  $subs subtitle${if (subs == 1) "" else "s"}", style = Fluent.type.caption, color = c.textSecondary)
            }
            if (links > 0) Button("Play now", skip, kind = ButtonKind.Accent, icon = Icons.Play, height = 36.dp)
            Button("Cancel", cancel, height = 36.dp)
        }
    }
}

// -------------------------------------------------------------------------------------------

private enum class DetailsTab(val label: String) { Episodes("Episodes"), Related("Related"), More("More like this"), Cast("Cast & crew"), Gallery("Gallery"), Reviews("Reviews"), About("Details") }

/**
 * A related title from AniList or TMDB belongs to no extension. Opening one searches the extension this page came from (the sources the
 * person is using); the context menu offers the search in every extension. An extension's own recommendation opens as it always did.
 */
private fun openRelated(card: com.lagradost.cloudstream3.SearchResponse, apiName: String) {
    if (card !is com.lagradost.desktop.tmdb.TmdbCard) return openCard(card)
    Navigator.search(card.name, only = apiName.takeIf { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it) != null })
}

private fun relatedMenu(card: com.lagradost.cloudstream3.SearchResponse, apiName: String): () -> List<MenuEntry> = {
    if (card is com.lagradost.desktop.tmdb.TmdbCard) listOf(
        MenuItem("Search in $apiName", Icons.Search) { openRelated(card, apiName) },
        MenuItem("Search in all extensions", Icons.Search) { Navigator.search(card.name) },
    ) else listOf(
        MenuItem("Open", Icons.Play) { openCard(card) },
        MenuItem("Search in all extensions", Icons.Search) { Navigator.search(card.name) },
    )
}

private fun isWatched(ep: ResultEpisode): Boolean {
    val progress = if (ep.duration > 0) ep.position.toFloat() / ep.duration else 0f
    return ep.videoWatchState == com.lagradost.cloudstream3.ui.result.VideoWatchState.Watched || progress > 0.95f
}

@Composable
private fun DetailsContent(vm: ResultViewModel2, sync: SyncViewModel, route: Route.Details, d: ResultData) {
    val c = Fluent.colors
    val episodes by vm.episodes.observeAsState()
    val movie by vm.movie.observeAsState()
    val recommendations by vm.recommendations.observeAsState()
    val resume by vm.resumeWatching.observeAsState()
    val seasons by vm.seasonSelections.observeAsState()
    val dubs by vm.dubSubSelections.observeAsState()
    val ranges by vm.rangeSelections.observeAsState()
    val sorts by vm.sortSelections.observeAsState()
    val seasonIdx by vm.selectedSeasonIndex.observeAsState()
    val dubIdx by vm.selectedDubStatusIndex.observeAsState()
    val rangeIdx by vm.selectedRangeIndex.observeAsState()
    val sortIdx by vm.selectedSortingIndex.observeAsState()
    val watch by vm.watchStatus.observeAsState()
    val favorite by vm.favoriteStatus.observeAsState()
    val subscribed by vm.subscribeStatus.observeAsState()
    val count by vm.episodesCountText.observeAsState()
    val trailers by vm.trailers.observeAsState()
    // TMDB: artwork, cast with photos, reviews, collection and where it streams, found by the ids the extension gave or by name and year
    val isMovieTitle = movie != null
    val tmdb by androidx.compose.runtime.produceState<com.lagradost.desktop.tmdb.TmdbInfo?>(null, route.url, isMovieTitle) {
        value = runCatching { com.lagradost.desktop.tmdb.Tmdb.info(d.title, d.yearText.str()?.take(4)?.toIntOrNull(), isMovieTitle, d.syncData.values) }.getOrNull()
    }
    // the extension's own artwork wins; TMDB fills what it did not send
    val shownData = remember(d, tmdb) { tmdb?.let { t -> d.copy(logoUrl = d.logoUrl ?: t.logo, backgroundPosterUrl = d.backgroundPosterUrl ?: d.posterBackgroundImage ?: t.backdrop, posterImage = d.posterImage ?: t.poster) } ?: d }
    // Settings > Player > Show trailers also decides whether TMDB's trailer is offered
    val showTrailers = remember { PreferenceManager.getDefaultSharedPreferences(DesktopBootstrap.activity).getBoolean(DesktopBootstrap.activity.getString(R.string.show_trailers_key), true) }
    val tmdbTrailer = tmdb?.trailerKey?.takeIf { showTrailers }?.let { "https://www.youtube.com/watch?v=$it" }
    val tmdbLinks = tmdb?.let { t -> listOfNotNull(t.imdbId?.let { "IMDb" to "https://www.imdb.com/title/$it/" }, "TMDB" to "https://www.themoviedb.org/${if (t.isMovie) "movie" else "tv"}/${t.id}") }.orEmpty()
    // anime: its prequels, sequels and side stories (AniList), found by the AniList / MAL id of the page or by the title
    val isAnime = d.typeText.str()?.let { it.contains("anime", true) || it.contains("ova", true) } == true || d.syncData.keys.any { it == "anilist" || it == "mal" }
    val related by androidx.compose.runtime.produceState<List<RelatedTitle>>(emptyList(), route.url, isAnime) {
        value = if (isAnime && com.lagradost.desktop.ui.fluent.Appearance.tmdbEnabled) runCatching { AnimeRelations.load(d.title, d.syncData) }.getOrDefault(emptyList()) else emptyList()
    }
    val hideSpoilers = com.lagradost.desktop.ui.fluent.Appearance.hideSpoilers

    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val headerHeight = (maxHeight * 0.8f).coerceIn(520.dp, 840.dp)
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 } }
        TopBarOverlay(scrolled)
        FluentScrollbar(listState, com.lagradost.desktop.ui.shell.TopBarHeight)
        val cast = d.actors.orEmpty()
        val recs = recommendations.orEmpty().ifEmpty { tmdb?.similar.orEmpty() }
        val tmdbCast = tmdb?.cast.orEmpty()
        val isMovie = movie != null
        val tabs = buildList {
            if (!isMovie) add(DetailsTab.Episodes)
            if (related.isNotEmpty()) add(DetailsTab.Related)
            if (recs.isNotEmpty()) add(DetailsTab.More)
            if (cast.isNotEmpty() || tmdbCast.isNotEmpty() || d.actorsText.str() != null) add(DetailsTab.Cast)
            if ((tmdb?.backdrops?.size ?: 0) > 1) add(DetailsTab.Gallery)
            if (tmdb?.reviews?.isNotEmpty() == true) add(DetailsTab.Reviews)
            if (tmdb != null) add(DetailsTab.About)
        }
        var tab by remember(route.url) { mutableStateOf<DetailsTab?>(null) }
        val shown = tab?.takeIf { it in tabs } ?: tabs.firstOrNull()
        // episode cards per row from the room there is
        val columns = ((maxWidth - gutter * 2 + 18.dp) / (250.dp + 18.dp)).toInt().coerceIn(1, 6)
        val pageWidth = maxWidth

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().smoothWheel(listState), contentPadding = PaddingValues(bottom = 48.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
            item(key = "header") {
                Header(vm, shownData, route, wide, headerHeight, watch, favorite, subscribed, resume?.result ?: (movie as? Resource.Success)?.value?.second ?: (episodes as? Resource.Success)?.value?.firstOrNull(), resume?.progress?.progressLeft.str(), trailers.orEmpty().isNotEmpty() || tmdbTrailer != null, trailers?.firstOrNull()?.mirros?.firstOrNull()?.second ?: tmdbTrailer, tmdbLinks, tmdb,
                    scroll = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE })
            }
            item(key = "trackers") { TrackerCard(sync) }
            if (tabs.isNotEmpty()) item(key = "tabs") {
                Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 4.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.lagradost.desktop.ui.fluent.UnderlineTabs(tabs.map { it.label }, tabs.indexOf(shown).coerceAtLeast(0), { tab = tabs[it] }, Modifier.weight(1f))
                    if (shown == DetailsTab.Episodes) count.str()?.let { FText(it, color = c.textSecondary) }
                }
            }
            when (shown) {
                DetailsTab.Episodes -> {
                    val seasonList = seasons.orEmpty()
                    // the season picker has a line of its own: beside the other pickers it was squeezed out of view in a narrow window
                    if (seasonList.size >= 2) item(key = "ep-seasons") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.List, size = 16.dp, tint = c.accentText)
                            if (seasonList.size <= 6) {
                                Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    seasonList.forEachIndexed { i, (label, value) ->
                                        Chip(label.str() ?: "Season $value", i == (seasonIdx ?: 0), onClick = { vm.changeSeason(value) })
                                    }
                                }
                            } else {
                                ComboBox(seasonList.map { it.second }, seasonList.getOrNull(seasonIdx ?: 0)?.second, { s -> seasonList.firstOrNull { it.second == s }?.first.str() ?: "Season $s" }, { vm.changeSeason(it) }, minWidth = 190.dp, height = 36.dp)
                                FText("${seasonList.size} seasons", style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
                            }
                        }
                    }
                    item(key = "ep-tools") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f))
                            // spoilers: one click hides or shows the stills and descriptions of the episodes ahead
                            com.lagradost.desktop.ui.fluent.Chip(
                                if (hideSpoilers) "Spoilers hidden" else "Spoilers shown", hideSpoilers,
                                onClick = { com.lagradost.desktop.ui.fluent.Appearance.hideSpoilers = !hideSpoilers; com.lagradost.desktop.ui.fluent.Appearance.save() },
                                icon = if (hideSpoilers) Icons.Hide else Icons.Eye,
                            )
                            if (dubs.orEmpty().size > 1) ComboBox(dubs.orEmpty().map { it.second }, dubs.orEmpty().getOrNull(dubIdx ?: 0)?.second, { dubLabel(dubs.orEmpty(), it) }, { vm.changeDubStatus(it) }, minWidth = 100.dp)
                            if (ranges.orEmpty().size > 1) ComboBox(ranges.orEmpty().map { it.second }, ranges.orEmpty().getOrNull(rangeIdx ?: 0)?.second, { r -> ranges.orEmpty().firstOrNull { it.second == r }?.first.str() ?: "${r.startEpisode}-${r.endEpisode}" }, { vm.changeRange(it) }, minWidth = 120.dp)
                            if (sorts.orEmpty().size > 1) ComboBox(sorts.orEmpty().map { it.second }, sorts.orEmpty().getOrNull(sortIdx ?: 0)?.second, { s -> sorts.orEmpty().firstOrNull { it.second == s }?.first.str() ?: "Sort" }, { vm.setSort(it) }, icon = Icons.Sort, minWidth = 120.dp)
                        }
                    }
                    when (val res = episodes) {
                        null, is Resource.Loading -> item(key = "ep-loading") {
                            Row(Modifier.padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                repeat(columns) { Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(FluentShapes.card)).shimmer()) }
                            }
                        }
                        is Resource.Failure -> item(key = "ep-failure") {
                            com.lagradost.desktop.ui.fluent.EmptyState(Icons.Warning, "Episodes could not be loaded", res.errorString)
                        }
                        is Resource.Success -> {
                            if (res.value.isEmpty()) item(key = "ep-empty") {
                                com.lagradost.desktop.ui.fluent.EmptyState(Icons.Video, d.noEpisodesFoundText.str() ?: "No episodes found")
                            }
                            val rows = res.value.chunked(columns)
                            // spoilers: episodes up to current episode stay open; unviewed episodes after current episode are covered
                            val currentEpIdx = res.value.indexOfLast { isWatched(it) || it.position > 0 }.coerceAtLeast(0)
                            val openIds = if (!hideSpoilers) null else res.value.filterIndexed { i, ep -> i <= currentEpIdx || isWatched(ep) || ep.position > 0 }.map { it.id }.toSet()
                            items(rows.size, key = { i -> "eprow-" + (rows[i].firstOrNull()?.id ?: i) + "-" + i }) { i ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    rows[i].forEach { ep -> Box(Modifier.weight(1f)) { EpisodeCard(vm, ep, d.backgroundPosterUrl ?: d.posterImage, d.posterHeaders, spoiler = openIds != null && ep.id !in openIds) } }
                                    repeat(columns - rows[i].size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                DetailsTab.Related -> item(key = "related") {
                    val w = com.lagradost.desktop.ui.fluent.Appearance.posterSize.width
                    val perRow = ((pageWidth - gutter * 2 + 16.dp) / (w + 16.dp)).toInt().coerceAtLeast(2)
                    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        FText("Prequels, sequels and other titles of this story, from AniList. Opening one searches ${route.apiName}; right-click it to search all extensions.", style = Fluent.type.caption, color = c.textTertiary)
                        related.chunked(perRow).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { r -> PosterCard(r.card, { openRelated(r.card, route.apiName) }, w, subtitle = r.detail, menu = relatedMenu(r.card, route.apiName)) }
                            }
                        }
                    }
                }
                DetailsTab.More -> item(key = "recs") {
                    val w = com.lagradost.desktop.ui.fluent.Appearance.posterSize.width
                    val perRow = ((pageWidth - gutter * 2 + 16.dp) / (w + 16.dp)).toInt().coerceAtLeast(2)
                    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        recs.chunked(perRow).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { card -> PosterCard(card, { openRelated(card, route.apiName) }, w, menu = relatedMenu(card, route.apiName)) }
                            }
                        }
                    }
                }
                DetailsTab.Gallery -> tmdb?.let { t -> item(key = "gallery") { TmdbGallery(t, pageWidth) } }
                DetailsTab.About -> tmdb?.let { t -> item(key = "about") { TmdbStrip(t) } }
                DetailsTab.Reviews -> tmdb?.let { t -> item(key = "reviews") { TmdbReviews(t.reviews) } }
                DetailsTab.Cast -> item(key = "cast") {
                    Column(Modifier.padding(horizontal = gutter)) {
                        val perRow = ((pageWidth - gutter * 2) / 150.dp).toInt().coerceAtLeast(3)
                        // TMDB's cast has real photos and opens the person's page; the extension's list is the fallback
                        if (tmdbCast.isNotEmpty()) tmdbCast.chunked(perRow).forEach { row ->
                            Row(Modifier.padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                row.forEach { a -> CastCard(a.name, a.image, a.role) { Navigator.go(Route.Person(a.id, a.name, a.image)) } }
                            }
                        } else cast.take(60).chunked(perRow).forEach { row ->
                            Row(Modifier.padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                row.forEach { a -> CastCard(a.actor.name, a.actor.image, a.roleString) }
                            }
                        }
                        d.actorsText.str()?.let { text -> FText(text, color = c.textSecondary, modifier = Modifier.padding(vertical = 8.dp).widthIn(max = 900.dp)) }
                    }
                }
                null -> {}
            }
        }
    }
}

@Composable
private fun CastCard(name: String, image: String?, role: String?, onClick: (() -> Unit)? = null) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1.03f else 1f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(200))
    Column(Modifier.width(132.dp).hoverable(source).let { if (onClick != null) it.fluentClickable(source, true, RoundedCornerShape(FluentShapes.card), Role.Button, onClick) else it }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(c.layer)
                .border(androidx.compose.ui.unit.Dp.Hairline, if (hovered) c.strokeStrong else c.stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FText(name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), style = Fluent.type.subtitle, color = c.textTertiary)
            RemoteImage(image, null, name, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Box(Modifier.height(10.dp))
        FText(name, style = Fluent.type.bodyStrong, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        role?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.caption, color = c.textTertiary, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

private fun dubLabel(list: List<Pair<UiText?, DubStatus>>, status: DubStatus): String =
    list.firstOrNull { it.second == status }?.first.str() ?: status.name

@Composable
private fun Header(
    vm: ResultViewModel2,
    d: ResultData,
    route: Route.Details,
    wide: Boolean,
    height: androidx.compose.ui.unit.Dp,
    watch: WatchType?,
    favorite: Boolean?,
    subscribed: Boolean?,
    playEpisode: ResultEpisode?,
    resumeText: String?,
    hasTrailer: Boolean,
    trailerUrl: String?,
    links: List<Pair<String, String>>,
    tmdb: com.lagradost.desktop.tmdb.TmdbInfo?,
    scroll: () -> Float = { 0f },
) {
    val c = Fluent.colors
    val backdrop = d.backgroundPosterUrl ?: d.posterBackgroundImage
    val poster = d.posterImage
    com.lagradost.desktop.ui.shell.AmbientArtwork(backdrop ?: poster, d.posterHeaders)
    Box(Modifier.fillMaxWidth().height(height)) {
        // the artwork is the page: it runs edge to edge and fades out into the page at the bottom
        Box(
            Modifier.fillMaxSize().fadeIntoPage(),
        ) {
            // the picture trails the page a little (parallax), under the fade at the bottom
            if (backdrop != null) RemoteImage(backdrop, d.posterHeaders, null, Modifier.fillMaxSize().graphicsLayer { translationY = scroll().coerceAtMost(size.height) * 0.3f }, ContentScale.Crop, alignment = Alignment.TopCenter)
            else com.lagradost.desktop.ui.components.SoftImage(poster, d.posterHeaders, Modifier.fillMaxSize(), alpha = 0.6f)
            // no backdrop from the extension: the poster stands at the right, sharp, instead of only a blurred tint
            if (backdrop == null && poster != null && wide) {
                Box(Modifier.fillMaxSize().padding(end = 96.dp, top = 84.dp, bottom = 120.dp), contentAlignment = Alignment.CenterEnd) {
                    RemoteImage(poster, d.posterHeaders, d.title, Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(FluentShapes.card)), ContentScale.Crop)
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Fluent.colors.bg.copy(alpha = 0.94f), 0.45f to Fluent.colors.bg.copy(alpha = 0.55f), 0.8f to Fluent.colors.bg.copy(alpha = 0.1f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
        }

        // which extension this page comes from (it decides the sources), small, at the right
        d.apiName.str()?.takeIf { it.isNotBlank() }?.let { api ->
            Column(Modifier.align(Alignment.TopEnd).padding(top = com.lagradost.desktop.ui.shell.TopBarHeight + 20.dp, end = 36.dp), horizontalAlignment = Alignment.End) {
                FText("SOURCE", style = Fluent.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.4.sp), color = Color(0x99FFFFFF), maxLines = 1)
                FText(api, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1)
            }
        }

        // a series: the episode the Play button opens, as a card (its still, where you are in it)
        if (wide && playEpisode != null && !playEpisode.tvType.isMovieType()) {
            UpNextCard(vm, playEpisode, playEpisode.poster ?: backdrop ?: poster, d.posterHeaders, Modifier.align(Alignment.BottomEnd).padding(end = gutter, bottom = 34.dp).graphicsLayer { alpha = (1f - scroll() / (height.toPx() * 0.4f)).coerceIn(0f, 1f) })
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 30.dp).widthIn(max = 820.dp)
                .graphicsLayer { val s = scroll().coerceAtMost(height.toPx()); translationY = s * 0.18f; alpha = (1f - s / (height.toPx() * 0.55f)).coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (d.logoUrl != null) RemoteImage(d.logoUrl, d.posterHeaders, d.title, Modifier.height(132.dp).widthIn(max = 560.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
            else FText(d.title, style = Fluent.type.titleLarge.copy(fontSize = 54.sp, lineHeight = 60.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 16f)), color = Color.White, maxLines = 3)
            // line one: when, how long, what it is, the age rating
            val facts = listOfNotNull(d.yearText.str(), d.durationText.str()?.takeIf { it.isNotBlank() } ?: tmdb?.runtime?.let { if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min" }, d.typeText.str())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (facts.isNotEmpty()) FText(facts.joinToString("  •  "), style = Fluent.type.bodyStrong, color = Color(0xE6FFFFFF), maxLines = 1)
                (d.contentRatingText.str()?.takeIf { it.isNotBlank() } ?: tmdb?.certification)?.let { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                d.onGoingText.str()?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.bodyStrong, color = c.accentText, maxLines = 1) }
            }
            // line two: the scores (each says whose it is) and the genres
            val genres = d.tags.take(4)
            // the extension's own score only when it adds something: TMDB's is the same number most of the time
            val scoreText = d.ratingText.str()?.takeIf { it.isNotBlank() }?.substringBefore('/')?.removePrefix("★")?.trim()
                ?.takeIf { shown -> val own = shown.replace(',', '.').toFloatOrNull(); tmdb?.rating == null || own == null || kotlin.math.abs(own - tmdb.rating!!.toFloat()) >= 0.3f }
            if (tmdb?.rating != null || scoreText != null || genres.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                tmdb?.rating?.let { ScoreMark("%.1f".format(it), "TMDB") }
                scoreText?.let { ScoreMark(it, d.apiName.str()) }
                if (genres.isNotEmpty()) FText(genres.joinToString("  •  "), style = Fluent.type.bodyStrong, color = Color(0xCCFFFFFF), maxLines = 1)
            }
            d.nextAiringEpisode.str()?.let { ep -> FText("$ep ${d.nextAiringDate.str().orEmpty()}", color = c.accentText, maxLines = 1) }
            var expanded by remember { mutableStateOf(false) }
            val plot = stripHtml(d.plotText.str().orEmpty())
            if (plot.isNotBlank()) {
                FText(plot, style = Fluent.type.bodyLarge.copy(lineHeight = 26.sp), color = Color(0xD9FFFFFF), maxLines = if (expanded) 40 else 3, modifier = Modifier.widthIn(max = 780.dp).fluentClickable(rememberInteraction(), true, RoundedCornerShape(4.dp), Role.Button) { expanded = !expanded })
            }
            d.vpnText.str()?.let { FText(it, style = Fluent.type.caption, color = c.caution) }
            Box(Modifier.height(4.dp))
            ActionRow(vm, d, watch, favorite, subscribed, playEpisode, resumeText, hasTrailer, trailerUrl, links)
        }
    }
}

/** The episode the Play button opens, as a glass card at the right of the header: its still, "Continue" or "Up next", the number and name, and a line for how far you are */
@Composable
private fun UpNextCard(vm: ResultViewModel2, ep: ResultEpisode, image: String?, headers: Map<String, String>?, modifier: Modifier = Modifier) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val started = ep.getRealPosition() > 0
    val scale by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1.03f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 420f), label = "upNext")
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(330.dp)
            .clip(shape)
            .background(if (hovered) Color(0xE6141418) else Color(0xCC0B0B10), shape)
            .border(Dp.Hairline, Color(0x33FFFFFF), shape)
            .fluentClickable(source, true, shape, Role.Button) { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)) }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF15161A))) {
            RemoteImage(image, headers, null, Modifier.fillMaxSize(), ContentScale.Crop)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (hovered) 1f else 0f }.background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Play, size = 18.dp, tint = Color.White)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FText(if (started) "CONTINUE" else "UP NEXT", style = Fluent.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.2.sp), color = Color(0x99FFFFFF), maxLines = 1)
            FText((if (ep.season != null) "S${ep.season} · " else "") + "E${ep.episode}", style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1)
            ep.name?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.caption, color = Color(0xCCFFFFFF), maxLines = 1) }
            if (started) Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(ep.getWatchProgress().coerceIn(0f, 1f)).background(Color.White))
            }
        }
    }
}

/** A score with the name of who gave it: a star, the number, then the source in small letters */
@Composable
private fun ScoreMark(value: String, source: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.StarFilled, size = 15.dp, tint = Color(0xFFFFC53D))
        FText(value, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1)
        if (!source.isNullOrBlank()) FText(source, style = Fluent.type.caption, color = Color(0x99FFFFFF), maxLines = 1)
    }
}

@Composable
private fun ActionRow(
    vm: ResultViewModel2,
    d: ResultData,
    watch: WatchType?,
    favorite: Boolean?,
    subscribed: Boolean?,
    playEpisode: ResultEpisode?,
    resumeText: String?,
    hasTrailer: Boolean,
    trailerUrl: String?,
    links: List<Pair<String, String>>,
) {
    val ctx = DesktopBootstrap.activity
    var bookmarkOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (playEpisode != null) {
            val label = when {
                playEpisode.tvType.isMovieType() -> if (playEpisode.getRealPosition() > 0) "Resume" else "Play"
                else -> (if (playEpisode.getRealPosition() > 0) "Resume " else "Play ") + (if (playEpisode.season != null) "S${playEpisode.season} · " else "") + "E${playEpisode.episode}"
            }
            com.lagradost.desktop.ui.fluent.PillButton(label, Icons.Play, primary = true, onClick = { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, playEpisode)) }, height = 50.dp, modifier = Modifier.widthIn(min = 180.dp))
            // a film that was started: from the beginning instead (an episode has this in its ⋯ menu)
            if (playEpisode.tvType.isMovieType() && playEpisode.getRealPosition() > 0) {
                com.lagradost.desktop.ui.fluent.PillButton("Start over", Icons.Previous, primary = false, onClick = {
                    com.lagradost.desktop.ui.screens.player.StartOver.request(playEpisode.id)
                    vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, playEpisode))
                }, height = 50.dp)
            }
            resumeText?.takeIf { it.isNotBlank() }?.let { FText(it, color = Color(0xCCFFFFFF), style = Fluent.type.caption, maxLines = 2, modifier = Modifier.widthIn(max = 90.dp)) }
        }
        // the library: one round button, the list it is on is in its tooltip and menu
        Box {
            val saved = watch != null && watch != WatchType.NONE
            com.lagradost.desktop.ui.fluent.GlassCircleButton(if (saved) Icons.BookmarkFilled else Icons.Bookmark, if (saved) "In your library: ${watchLabel(watch)}" else "Add to library", { bookmarkOpen = true }, active = saved, size = 50.dp)
            if (bookmarkOpen) {
                MenuFlyout(
                    WatchType.entries.map { t -> MenuItem(ctx.getString(t.stringRes), checked = t == watch) { vm.updateWatchStatus(t, ctx) } },
                    onDismiss = { bookmarkOpen = false },
                )
            }
        }
        if (hasTrailer && trailerUrl != null) com.lagradost.desktop.ui.fluent.GlassCircleButton(Icons.Video, "Watch trailer", { playTrailer(trailerUrl) }, size = 50.dp)
        Box {
            com.lagradost.desktop.ui.fluent.GlassCircleButton(Icons.More, "More", { moreOpen = true }, size = 50.dp)
            if (moreOpen) {
                val url = d.url
                MenuFlyout(
                    buildList {
                        // the links of what Play opens are fetched again (an episode has this in its right-click / three dots menu too)
                        playEpisode?.let { ep -> add(MenuItem("Reload sources", Icons.Refresh) { vm.handleAction(EpisodeClickEvent(ACTION_RELOAD_EPISODE, ep)) }) }
                        favorite?.let { fav ->
                            add(MenuItem(if (fav) "Remove from favourites" else "Add to favourites", if (fav) Icons.FavoriteFilled else Icons.Favorite, checked = fav) {
                                vm.toggleFavoriteStatus(ctx) { new -> if (new != null) Toasts.show(if (new) "Added to favourites" else "Removed from favourites", false) }
                            })
                        }
                        subscribed?.let { sub ->
                            add(MenuItem(if (sub) "Unsubscribe from new episodes" else "Get notified about new episodes", Icons.Notification, checked = sub) {
                                vm.toggleSubscriptionStatus(ctx) { new -> if (new != null) Toasts.show(if (new) "You will be told about new episodes" else "Subscription removed", false) }
                            })
                        }
                        if (url.startsWith("http")) add(MenuItem("Open in browser", Icons.OpenInNewWindow) { DesktopPlatform.openExternalBrowser(url) })
                        add(MenuItem("Copy link", Icons.Copy) { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null); Toasts.show("Link copied", false) })
                        add(MenuItem("Search for “${d.title}”", Icons.Search) { Navigator.search(d.title) })
                        links.forEach { (name, link) -> add(MenuItem("Open on $name", Icons.OpenInNewWindow) { DesktopPlatform.openExternalBrowser(link) }) }
                    },
                    onDismiss = { moreOpen = false },
                )
            }
        }
    }
}

/** The trailer in the app's own player; the page in the browser when its video cannot be taken out of it */
private fun playTrailer(url: String) {
    Toasts.show("Loading the trailer…", false)
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val found = java.util.Collections.synchronizedList(ArrayList<com.lagradost.cloudstream3.utils.ExtractorLink>())
        kotlinx.coroutines.withTimeoutOrNull(20_000) { runCatching { com.lagradost.cloudstream3.utils.loadExtractor(url, null, {}, { found.add(it) }) } }
        if (found.isEmpty()) { DesktopPlatform.openExternalBrowser(url); return@launch }
        // up to 1080p (a 4K AV1 trailer asks a lot of the PC), H.264 first at each height
        val fit = found.filter { it.quality in 1..1080 }.ifEmpty { found }
        val best = fit.sortedWith(compareByDescending<com.lagradost.cloudstream3.utils.ExtractorLink> { it.quality }.thenByDescending { it.name.contains("H264") }).take(6)
        Navigator.go(Route.Player(com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator(best, emptyList()), 0, null))
    }
}

private fun watchLabel(w: WatchType?): String =
    if (w == null || w == WatchType.NONE) "Add to library" else DesktopBootstrap.activity.getString(w.stringRes)

// -------------------------------------------------------------------------------------------

/** One episode: a 16:9 still with its number, progress and a play disc on hover; title, length and synopsis under it */
@Composable
private fun EpisodeCard(vm: ResultViewModel2, ep: ResultEpisode, fallback: String?, fallbackHeaders: Map<String, String>?, spoiler: Boolean = false) {
    val c = Fluent.colors
    // an episode ahead of the viewer is covered until the eye on it is clicked
    var uncovered by remember(ep.id) { mutableStateOf(false) }
    val covered = spoiler && !uncovered
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val lift by androidx.compose.animation.core.animateFloatAsState(if (hovered && com.lagradost.desktop.ui.fluent.Appearance.hoverZoom) 1.03f else 1f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(220))
    val glow by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1f else 0f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(200))
    val shape = RoundedCornerShape(FluentShapes.card)
    val progress = if (ep.duration > 0) (ep.position.toFloat() / ep.duration).coerceIn(0f, 1f) else 0f
    val watched = ep.videoWatchState == com.lagradost.cloudstream3.ui.result.VideoWatchState.Watched || progress > 0.95f
    val baseMenu: () -> List<MenuEntry> = {
        listOf(
            MenuItem("Play", Icons.Play) { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)) },
            MenuItem("Start from beginning", Icons.Previous) {
                com.lagradost.desktop.ui.screens.player.StartOver.request(ep.id)
                vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep))
            },
            MenuItem("Choose how to play…", Icons.Settings) { vm.handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)) },
            MenuSeparator,
            MenuItem(if (watched) "Mark as unwatched" else "Mark as watched", Icons.Check) { vm.handleAction(EpisodeClickEvent(ACTION_MARK_AS_WATCHED, ep)) },
            MenuItem("Mark all up to here as watched", Icons.Accept) { vm.handleAction(EpisodeClickEvent(ACTION_MARK_WATCHED_UP_TO_THIS_EPISODE, ep)) },
            MenuItem("Reload sources", Icons.Refresh) { vm.handleAction(EpisodeClickEvent(ACTION_RELOAD_EPISODE, ep)) },
            MenuSeparator,
            MenuItem("Download", Icons.Download) { vm.handleAction(EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, ep)) },
        )
    }
    // an episode ahead of the viewer can be shown and hidden again from its menu (right click or the three dots)
    val menu: () -> List<MenuEntry> = {
        val items = baseMenu()
        if (!spoiler) items else buildList {
            addAll(items.take(3))
            add(MenuItem(if (uncovered) "Hide spoiler" else "Show spoiler", if (uncovered) Icons.Hide else Icons.Eye) { uncovered = !uncovered })
            addAll(items.drop(3))
        }
    }
    ContextMenuArea(menu) {
        Column(Modifier.fillMaxWidth().hoverable(source).fluentClickable(source, true, shape, Role.Button) { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)) }) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .clip(shape).background(c.card).border(androidx.compose.ui.unit.Dp.Hairline, if (hovered) c.strokeStrong else c.stroke, shape),
            ) {
                if (ep.poster.isNullOrBlank()) {
                    // no still: the show's artwork, soft, under a big episode number
                    com.lagradost.desktop.ui.components.SoftImage(fallback, fallbackHeaders, Modifier.matchParentSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }, alpha = 0.9f)
                    Box(Modifier.matchParentSize().background(Color(0x59000000)))
                    FText(ep.episode.toString(), Modifier.align(Alignment.Center), style = Fluent.type.display.copy(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black), color = Color(0xCCFFFFFF), maxLines = 1)
                } else if (covered) {
                    // the still of an episode you have not reached, as a soft smear (decoded tiny and stretched: a real blur filter on every covered card is redrawn on each frame)
                    com.lagradost.desktop.ui.components.SoftImage(ep.poster, null, Modifier.fillMaxSize(), tiny = true)
                } else RemoteImage(ep.poster, null, ep.name, Modifier.fillMaxSize(), ContentScale.Crop)
                if (covered) {
                    Box(Modifier.matchParentSize().background(Color(0x66000000)))
                    Row(
                        Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color(0x99000000)).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x40FFFFFF), RoundedCornerShape(50))
                            .fluentClickable(rememberInteraction(), true, RoundedCornerShape(50), Role.Button) { uncovered = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Eye, size = 14.dp, tint = Color.White)
                        FText("Spoiler · show", style = Fluent.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false)
                    }
                }
                Box(Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xB3000000), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp)) {
                    FText(if (ep.season != null) "S${ep.season} · E${ep.episode}" else "E${ep.episode}", style = Fluent.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false)
                }
                if (watched) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).background(c.success, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Check, size = 12.dp, tint = Color.Black) }
                if (glow > 0.01f && !covered) Box(Modifier.matchParentSize().graphicsLayer { alpha = glow }.background(Color(0x4D000000)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).background(Color(0x33FFFFFF), CircleShape).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x66FFFFFF), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Play, size = 18.dp, tint = Color.White) }
                }
                ep.runTime?.takeIf { it > 0 }?.let { rt ->
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color(0xB3000000), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp)) {
                        FText("$rt min", style = Fluent.type.caption, color = Color.White, maxLines = 1, softWrap = false)
                    }
                }
                if (progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x40FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(c.accent))
                }
            }
            Box(Modifier.height(10.dp))
            // the whole title (up to three lines, the rest in a tooltip), with the rating and filler mark under it and the menu beside it
            val title = if (covered) "Episode ${ep.episode}" else ep.name?.takeIf { it.isNotBlank() } ?: "Episode ${ep.episode}"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    com.lagradost.desktop.ui.fluent.Tooltip(title) {
                        FText(title, Modifier.fillMaxWidth(), style = Fluent.type.bodyStrong.copy(fontSize = 15.sp, lineHeight = 20.sp), maxLines = 3)
                    }
                    if (ep.isFiller == true || ep.score != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ep.isFiller == true) Badge("Filler")
                        ep.score?.let { FText("★ " + it.toString(10, 1), style = Fluent.type.caption, color = c.textTertiary, maxLines = 1) }
                    }
                }
                Box {
                    var open by remember { mutableStateOf(false) }
                    com.lagradost.desktop.ui.fluent.IconButton(Icons.More, { open = true }, tooltip = "More", size = 28.dp, iconSize = 14.dp)
                    if (open) MenuFlyout(menu(), onDismiss = { open = false })
                }
            }
            ep.description?.takeIf { it.isNotBlank() }?.let {
                if (covered) FText("Description hidden to avoid spoilers", style = Fluent.type.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = c.textTertiary, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                else FText(stripHtml(it), style = Fluent.type.caption.copy(lineHeight = 18.sp), color = c.textSecondary, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

// -------------------------------------------------------------------------------------------

/**
 * The title on the services the user is signed in to (AniList, MyAnimeList, Simkl): list status, score and watched episodes, saved to
 * all of them with "Save". Only there when the title is known to such a service (its page gave the id, or its address maps to one).
 */
@Composable
private fun TrackerCard(sync: SyncViewModel) {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    // the model changes the status object in place and posts the same object again: a plain observeAsState would see "no change"
    var version by remember(sync) { mutableStateOf(0) }
    DisposableEffect(sync) {
        val bump = androidx.lifecycle.Observer<Any?> { version++ }
        sync.userData.observeForever(bump)
        sync.synced.observeForever(bump)
        sync.metadata.observeForever(bump)
        onDispose {
            sync.userData.removeObserver(bump)
            sync.synced.removeObserver(bump)
            sync.metadata.removeObserver(bump)
        }
    }
    @Suppress("UNUSED_EXPRESSION") version
    val names = sync.synced.value.orEmpty().filter { it.isSynced && it.hasAccount }.map { it.name }
    if (names.isEmpty()) return
    val user = sync.userData.value
    val status = (user as? Resource.Success)?.value
    val total = status?.maxEpisodes ?: (sync.metadata.value as? Resource.Success)?.value?.totalEpisodes

    Row(
        Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 4.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            FText("Tracking on ${names.joinToString(" · ")}", style = Fluent.type.bodyStrong, color = c.textSecondary, maxLines = 1)
        }
        when {
            user is Resource.Loading -> ProgressRing(size = 22.dp, strokeWidth = 2.dp)
            status != null -> {
                val kinds = com.lagradost.cloudstream3.ui.SyncWatchType.entries
                ComboBox(kinds, status.status, { ctx.getString(it.stringRes) }, { sync.setStatus(it.internalId) }, icon = Icons.Bookmark, minWidth = 150.dp, height = 36.dp)
                ComboBox((0..10).toList(), status.score?.toInt(10) ?: 0, { if (it == 0) "No score" else "$it / 10" }, { sync.setScore(if (it == 0) null else com.lagradost.cloudstream3.Score.from(it, 10)) }, icon = Icons.Star, minWidth = 120.dp, height = 36.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Icons.ChevronLeftSmall, { sync.setEpisodesDelta(-1) }, tooltip = "One episode less", size = 32.dp, iconSize = 12.dp)
                    FText("${status.watchedEpisodes ?: 0}${total?.let { " / $it" } ?: ""} ep", Modifier.padding(horizontal = 6.dp), maxLines = 1, softWrap = false)
                    IconButton(Icons.ChevronRightSmall, { sync.setEpisodesDelta(1) }, tooltip = "One episode more", size = 32.dp, iconSize = 12.dp)
                }
                Button("Save", { sync.publishUserData() }, kind = ButtonKind.Accent, height = 36.dp)
            }
            else -> FText("Not found on these services", style = Fluent.type.caption, color = c.textTertiary)
        }
    }
}
