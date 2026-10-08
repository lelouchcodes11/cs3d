package com.lagradost.desktop.ui.screens.details

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
import com.lagradost.desktop.ui.screens.home.openCard
import com.lagradost.desktop.ui.screens.home.stripHtml
import com.lagradost.desktop.ui.shell.TopBarOverlay

private val gutter = 36.dp

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

private enum class DetailsTab(val label: String) { Episodes("Episodes"), More("More like this"), Cast("Cast & crew") }

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

    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val headerHeight = (maxHeight * 0.66f).coerceIn(460.dp, 700.dp)
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 } }
        TopBarOverlay(scrolled)
        FluentScrollbar(listState, com.lagradost.desktop.ui.shell.TopBarHeight)
        val cast = d.actors.orEmpty()
        val recs = recommendations.orEmpty()
        val isMovie = movie != null
        val tabs = buildList {
            if (!isMovie) add(DetailsTab.Episodes)
            if (recs.isNotEmpty()) add(DetailsTab.More)
            if (cast.isNotEmpty() || d.actorsText.str() != null) add(DetailsTab.Cast)
        }
        var tab by remember(route.url) { mutableStateOf<DetailsTab?>(null) }
        val shown = tab?.takeIf { it in tabs } ?: tabs.firstOrNull()
        // episode cards per row from the room there is
        val columns = ((maxWidth - gutter * 2 + 18.dp) / (250.dp + 18.dp)).toInt().coerceIn(1, 6)
        val pageWidth = maxWidth

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
            item(key = "header") {
                Header(vm, d, route, wide, headerHeight, watch, favorite, subscribed, resume?.result ?: (movie as? Resource.Success)?.value?.second ?: (episodes as? Resource.Success)?.value?.firstOrNull(), resume?.progress?.progressLeft.str(), trailers.orEmpty().isNotEmpty(), trailers?.firstOrNull()?.mirros?.firstOrNull()?.second)
            }
            item(key = "trackers") { TrackerCard(sync) }
            if (tabs.isNotEmpty()) item(key = "tabs") {
                Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 4.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.lagradost.desktop.ui.fluent.PillTabs(
                        tabs.map { it.label }, tabs.indexOf(shown).coerceAtLeast(0), { tab = tabs[it] },
                        counts = tabs.map { t -> when (t) { DetailsTab.More -> recs.size; DetailsTab.Cast -> cast.size.takeIf { it > 0 }; else -> null } },
                    )
                    Box(Modifier.weight(1f))
                    if (shown == DetailsTab.Episodes) count.str()?.let { FText(it, color = c.textSecondary) }
                }
            }
            when (shown) {
                DetailsTab.Episodes -> {
                    item(key = "ep-tools") {
                        val seasonList = seasons.orEmpty()
                        Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (seasonList.size in 2..10) {
                                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    seasonList.forEachIndexed { i, (label, value) ->
                                        Chip(label.str() ?: "Season $value", i == (seasonIdx ?: 0), onClick = { vm.changeSeason(value) })
                                    }
                                }
                            } else {
                                if (seasonList.size > 10) ComboBox(seasonList.map { it.second }, seasonList.getOrNull(seasonIdx ?: 0)?.second, { s -> seasonList.firstOrNull { it.second == s }?.first.str() ?: "Season $s" }, { vm.changeSeason(it) }, minWidth = 160.dp)
                                Box(Modifier.weight(1f))
                            }
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
                            items(rows.size, key = { i -> "eprow-" + (rows[i].firstOrNull()?.id ?: i) + "-" + i }) { i ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    rows[i].forEach { ep -> Box(Modifier.weight(1f)) { EpisodeCard(vm, ep, d.backgroundPosterUrl ?: d.posterImage, d.posterHeaders) } }
                                    repeat(columns - rows[i].size) { Box(Modifier.weight(1f)) }
                                }
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
                                row.forEach { card -> PosterCard(card, { openCard(card) }, w) }
                            }
                        }
                    }
                }
                DetailsTab.Cast -> item(key = "cast") {
                    Column(Modifier.padding(horizontal = gutter)) {
                        val perRow = ((pageWidth - gutter * 2) / 150.dp).toInt().coerceAtLeast(3)
                        cast.take(60).chunked(perRow).forEach { row ->
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
private fun CastCard(name: String, image: String?, role: String?) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1.03f else 1f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(200))
    Column(Modifier.width(132.dp).hoverable(source), horizontalAlignment = Alignment.CenterHorizontally) {
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
) {
    val c = Fluent.colors
    val backdrop = d.backgroundPosterUrl ?: d.posterBackgroundImage
    val poster = d.posterImage
    com.lagradost.desktop.ui.shell.AmbientArtwork(backdrop ?: poster, d.posterHeaders)
    Box(Modifier.fillMaxWidth().height(height)) {
        // the artwork fades out into the page at the bottom, whatever the backdrop style (still: no slow zoom)
        val zoom: androidx.compose.runtime.State<Float>? = null
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { val s = zoom?.value ?: 1f; scaleX = s; scaleY = s }) {
                if (backdrop != null) RemoteImage(backdrop, d.posterHeaders, null, Modifier.fillMaxSize(), ContentScale.Crop, alignment = Alignment.TopCenter)
                else com.lagradost.desktop.ui.components.SoftImage(poster, d.posterHeaders, Modifier.fillMaxSize(), alpha = 0.6f)
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF20B0B0E), 0.5f to Color(0x990B0B0E), 1f to Color(0x140B0B0E))))
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
        }

        Row(Modifier.align(Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
            if (wide && poster != null) {
                val shape = RoundedCornerShape(FluentShapes.card)
                Box(Modifier.width(230.dp).aspectRatio(2f / 3f).clip(shape).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x33FFFFFF), shape).background(c.card)) {
                    RemoteImage(poster, d.posterHeaders, d.title, Modifier.fillMaxSize(), ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    d.typeText.str()?.let { com.lagradost.desktop.ui.fluent.ArtChip(it, accent = true) }
                    d.apiName.str()?.let { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                    d.contentRatingText.str()?.takeIf { it.isNotBlank() }?.let { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                    d.onGoingText.str()?.let { com.lagradost.desktop.ui.fluent.ArtChip(it) }
                }
                if (d.logoUrl != null) RemoteImage(d.logoUrl, d.posterHeaders, d.title, Modifier.height(96.dp).widthIn(max = 460.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
                else FText(d.title, style = Fluent.type.titleLarge.copy(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 16f)), color = Color.White, maxLines = 3)
                val meta = listOfNotNull(d.ratingText.str(), d.yearText.str(), d.durationText.str()) + d.tags.take(4)
                if (meta.isNotEmpty()) FText(meta.joinToString("   •   "), style = Fluent.type.bodyStrong, color = Color(0xD9FFFFFF), maxLines = 2)
                d.nextAiringEpisode.str()?.let { ep -> FText("$ep ${d.nextAiringDate.str().orEmpty()}", color = c.accentText, maxLines = 1) }
                var expanded by remember { mutableStateOf(false) }
                val plot = stripHtml(d.plotText.str().orEmpty())
                if (plot.isNotBlank()) {
                    FText(plot, style = Fluent.type.bodyLarge.copy(lineHeight = 26.sp), color = Color(0xD9FFFFFF), maxLines = if (expanded) 40 else 3, modifier = Modifier.widthIn(max = 780.dp).fluentClickable(rememberInteraction(), true, RoundedCornerShape(4.dp), Role.Button) { expanded = !expanded })
                }
                d.vpnText.str()?.let { FText(it, style = Fluent.type.caption, color = c.caution) }
                Box(Modifier.height(4.dp))
                ActionRow(vm, d, watch, favorite, subscribed, playEpisode, resumeText, hasTrailer, trailerUrl)
            }
        }
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
            Column {
                com.lagradost.desktop.ui.fluent.PillButton(label, Icons.Play, primary = true, onClick = { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, playEpisode)) }, height = 48.dp)
            }
            // a film that was started: from the beginning instead (an episode has this in its ⋯ menu)
            if (playEpisode.tvType.isMovieType() && playEpisode.getRealPosition() > 0) {
                com.lagradost.desktop.ui.fluent.PillButton("Start over", Icons.Previous, primary = false, onClick = {
                    com.lagradost.desktop.ui.screens.player.StartOver.request(playEpisode.id)
                    vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, playEpisode))
                }, height = 48.dp)
            }
            resumeText?.takeIf { it.isNotBlank() }?.let { FText(it, color = Color(0xCCFFFFFF), style = Fluent.type.caption, maxLines = 2, modifier = Modifier.widthIn(max = 90.dp)) }
        }
        Box {
            com.lagradost.desktop.ui.fluent.PillButton(watchLabel(watch), if (watch != null && watch != WatchType.NONE) Icons.BookmarkFilled else Icons.Bookmark, primary = false, onClick = { bookmarkOpen = true }, height = 48.dp)
            if (bookmarkOpen) {
                MenuFlyout(
                    WatchType.entries.map { t -> MenuItem(ctx.getString(t.stringRes), checked = t == watch) { vm.updateWatchStatus(t, ctx) } },
                    onDismiss = { bookmarkOpen = false },
                )
            }
        }
        favorite?.let { fav ->
            com.lagradost.desktop.ui.fluent.GlassCircleButton(if (fav) Icons.FavoriteFilled else Icons.Favorite, if (fav) "Remove from favourites" else "Add to favourites", {
                vm.toggleFavoriteStatus(ctx) { new -> if (new != null) Toasts.show(if (new) "Added to favourites" else "Removed from favourites", false) }
            }, active = fav, size = 48.dp)
        }
        subscribed?.let { sub ->
            com.lagradost.desktop.ui.fluent.GlassCircleButton(Icons.Notification, if (sub) "Unsubscribe from new episodes" else "Get notified about new episodes", {
                vm.toggleSubscriptionStatus(ctx) { new -> if (new != null) Toasts.show(if (new) "You will be told about new episodes" else "Subscription removed", false) }
            }, active = sub, size = 48.dp)
        }
        if (hasTrailer && trailerUrl != null) com.lagradost.desktop.ui.fluent.GlassCircleButton(Icons.Video, "Watch trailer", { DesktopPlatform.openExternalBrowser(trailerUrl) }, size = 48.dp)
        Box {
            com.lagradost.desktop.ui.fluent.GlassCircleButton(Icons.More, "More", { moreOpen = true }, size = 48.dp)
            if (moreOpen) {
                val url = d.url
                MenuFlyout(
                    buildList {
                        if (url.startsWith("http")) add(MenuItem("Open in browser", Icons.OpenInNewWindow) { DesktopPlatform.openExternalBrowser(url) })
                        add(MenuItem("Copy link", Icons.Copy) { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null); Toasts.show("Link copied", false) })
                        add(MenuItem("Search for “${d.title}”", Icons.Search) { Navigator.search(d.title) })
                    },
                    onDismiss = { moreOpen = false },
                )
            }
        }
    }
}

private fun watchLabel(w: WatchType?): String =
    if (w == null || w == WatchType.NONE) "Add to library" else DesktopBootstrap.activity.getString(w.stringRes)

// -------------------------------------------------------------------------------------------

/** One episode: a 16:9 still with its number, progress and a play disc on hover; title, length and synopsis under it */
@Composable
private fun EpisodeCard(vm: ResultViewModel2, ep: ResultEpisode, fallback: String?, fallbackHeaders: Map<String, String>?) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val lift by androidx.compose.animation.core.animateFloatAsState(if (hovered && com.lagradost.desktop.ui.fluent.Appearance.hoverZoom) 1.03f else 1f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(220))
    val glow by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1f else 0f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(200))
    val shape = RoundedCornerShape(FluentShapes.card)
    val progress = if (ep.duration > 0) (ep.position.toFloat() / ep.duration).coerceIn(0f, 1f) else 0f
    val watched = ep.videoWatchState == com.lagradost.cloudstream3.ui.result.VideoWatchState.Watched || progress > 0.95f
    val menu: () -> List<MenuEntry> = {
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
                } else RemoteImage(ep.poster, null, ep.name, Modifier.fillMaxSize(), ContentScale.Crop)
                Box(Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xB3000000), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp)) {
                    FText(if (ep.season != null) "S${ep.season} · E${ep.episode}" else "E${ep.episode}", style = Fluent.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false)
                }
                if (watched) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).background(c.success, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Check, size = 12.dp, tint = Color.Black) }
                if (glow > 0.01f) Box(Modifier.matchParentSize().graphicsLayer { alpha = glow }.background(Color(0x4D000000)), contentAlignment = Alignment.Center) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FText(ep.name ?: "Episode ${ep.episode}", Modifier.weight(1f, fill = false), style = Fluent.type.bodyStrong.copy(fontSize = 15.sp), maxLines = 1)
                if (ep.isFiller == true) Badge("Filler")
                ep.score?.let { FText("★ " + it.toString(10, 1), style = Fluent.type.caption, color = c.textTertiary, maxLines = 1) }
                Box(Modifier.weight(1f))
                Box {
                    var open by remember { mutableStateOf(false) }
                    com.lagradost.desktop.ui.fluent.IconButton(Icons.More, { open = true }, tooltip = "More", size = 28.dp, iconSize = 14.dp)
                    if (open) MenuFlyout(menu(), onDismiss = { open = false })
                }
            }
            ep.description?.takeIf { it.isNotBlank() }?.let { FText(stripHtml(it), style = Fluent.type.caption.copy(lineHeight = 18.sp), color = c.textSecondary, maxLines = 2, modifier = Modifier.padding(top = 3.dp)) }
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
        Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 22.dp).glass(FluentShapes.overlay).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            FText("Tracking", style = Fluent.type.bodyStrong)
            FText(names.joinToString(" · "), style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
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
