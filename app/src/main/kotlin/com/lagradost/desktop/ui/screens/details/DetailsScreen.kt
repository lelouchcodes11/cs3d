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

private val gutter = 24.dp

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
            is Resource.Success -> DetailsContent(vm, route, res.value)
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
    Box(Modifier.fillMaxSize().padding(bottom = 32.dp), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(FluentShapes.card)
        Row(
            Modifier.background(c.flyout, shape).border(androidx.compose.ui.unit.Dp.Hairline, c.strokeStrong.copy(alpha = 0.4f), shape).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressRing(size = 24.dp, strokeWidth = 3.dp)
            Column {
                FText("Finding sources…", style = Fluent.type.bodyStrong)
                FText("$links link${if (links == 1) "" else "s"}  ·  $subs subtitle${if (subs == 1) "" else "s"}", style = Fluent.type.caption, color = c.textSecondary)
            }
            if (links > 0) Button("Play now", skip, kind = ButtonKind.Accent)
            Button("Cancel", cancel)
        }
    }
}

// -------------------------------------------------------------------------------------------

@Composable
private fun DetailsContent(vm: ResultViewModel2, route: Route.Details, d: ResultData) {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
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
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 } }
        TopBarOverlay(scrolled)
        FluentScrollbar(listState, com.lagradost.desktop.ui.shell.TopBarHeight)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "header") {
                Header(vm, d, route, wide, watch, favorite, subscribed, resume?.result ?: (movie as? Resource.Success)?.value?.second ?: (episodes as? Resource.Success)?.value?.firstOrNull(), resume?.progress?.progressLeft.str(), trailers.orEmpty().isNotEmpty(), trailers?.firstOrNull()?.mirros?.firstOrNull()?.second)
            }
            val cast = d.actors.orEmpty()
            val isMovie = movie != null
            if (!isMovie) {
                item(key = "episodes-head") {
                    Column(Modifier.padding(horizontal = gutter).padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FText("Episodes", style = Fluent.type.subtitle)
                            count.str()?.let { FText(it, color = c.textSecondary) }
                            Box(Modifier.weight(1f))
                            if (dubs.orEmpty().size > 1) ComboBox(dubs.orEmpty().map { it.second }, dubs.orEmpty().getOrNull(dubIdx ?: 0)?.second, { dubLabel(dubs.orEmpty(), it) }, { vm.changeDubStatus(it) }, minWidth = 100.dp)
                            if (seasons.orEmpty().size > 1) ComboBox(seasons.orEmpty().map { it.second }, seasons.orEmpty().getOrNull(seasonIdx ?: 0)?.second, { s -> seasons.orEmpty().firstOrNull { it.second == s }?.first.str() ?: "Season $s" }, { vm.changeSeason(it) }, minWidth = 140.dp)
                            if (ranges.orEmpty().size > 1) ComboBox(ranges.orEmpty().map { it.second }, ranges.orEmpty().getOrNull(rangeIdx ?: 0)?.second, { r -> ranges.orEmpty().firstOrNull { it.second == r }?.first.str() ?: "${r.startEpisode}-${r.endEpisode}" }, { vm.changeRange(it) }, minWidth = 120.dp)
                            if (sorts.orEmpty().size > 1) ComboBox(sorts.orEmpty().map { it.second }, sorts.orEmpty().getOrNull(sortIdx ?: 0)?.second, { s -> sorts.orEmpty().firstOrNull { it.second == s }?.first.str() ?: "Sort" }, { vm.setSort(it) }, icon = Icons.Sort, minWidth = 120.dp)
                        }
                        Box(Modifier.height(12.dp))
                    }
                }
                when (val res = episodes) {
                    null, is Resource.Loading -> item(key = "ep-loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { ProgressRing() }
                    }
                    is Resource.Failure -> item(key = "ep-failure") {
                        FText("Episodes could not be loaded: ${res.errorString}", color = c.textSecondary, modifier = Modifier.padding(gutter))
                    }
                    is Resource.Success -> {
                        if (res.value.isEmpty()) item(key = "ep-empty") {
                            FText(d.noEpisodesFoundText.str() ?: "No episodes found.", color = c.textSecondary, modifier = Modifier.padding(horizontal = gutter, vertical = 16.dp))
                        }
                        items(res.value, key = { it.id.toString() + "-" + it.index }) { ep ->
                            EpisodeRow(vm, ep, wide)
                        }
                    }
                }
            }
            if (cast.isNotEmpty()) item(key = "cast") {
                Column(Modifier.padding(top = 28.dp)) {
                    SectionHeader("Cast", Modifier.padding(horizontal = gutter))
                    Box(Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(cast.take(40)) { a ->
                            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(80.dp).clip(CircleShape).background(c.card)) {
                                    RemoteImage(a.actor.image, null, a.actor.name, Modifier.fillMaxSize(), ContentScale.Crop)
                                }
                                Box(Modifier.height(6.dp))
                                FText(a.actor.name, style = Fluent.type.caption, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                a.roleString?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1) }
                            }
                        }
                    }
                }
            }
            d.actorsText.str()?.let { text ->
                item(key = "cast-text") { FText(text, color = c.textSecondary, modifier = Modifier.padding(horizontal = gutter, vertical = 16.dp).widthIn(max = 900.dp)) }
            }
            val recs = recommendations.orEmpty()
            if (recs.isNotEmpty()) item(key = "recs") {
                Column(Modifier.padding(top = 28.dp)) {
                    SectionHeader("More like this", Modifier.padding(horizontal = gutter))
                    Box(Modifier.height(8.dp))
                    Shelf(recs, 148.dp, key = { it.url }) { card -> PosterCard(card, { openCard(card) }, 148.dp) }
                }
            }
        }
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
    watch: WatchType?,
    favorite: Boolean?,
    subscribed: Boolean?,
    playEpisode: ResultEpisode?,
    resumeText: String?,
    hasTrailer: Boolean,
    trailerUrl: String?,
) {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val backdrop = d.backgroundPosterUrl ?: d.posterBackgroundImage
    val poster = d.posterImage
    Box(Modifier.fillMaxWidth().height(if (wide) 500.dp else 460.dp).background(Color(0xFF101010))) {
        if (backdrop != null) RemoteImage(backdrop, d.posterHeaders, null, Modifier.fillMaxSize(), ContentScale.Crop, alignment = Alignment.TopCenter)
        else com.lagradost.desktop.ui.components.SoftImage(poster, d.posterHeaders, Modifier.fillMaxSize(), alpha = 0.5f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF2101010), 0.6f to Color(0xA6101010), 1f to Color(0x33101010))))
        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to c.layer)))

        Row(Modifier.align(Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom) {
            if (wide && poster != null) {
                Box(Modifier.width(200.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(FluentShapes.card)).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x33FFFFFF), RoundedCornerShape(FluentShapes.card)).background(c.card)) {
                    RemoteImage(poster, d.posterHeaders, d.title, Modifier.fillMaxSize(), ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    d.typeText.str()?.let { Badge(it, accent = true) }
                    d.apiName.str()?.let { Badge(it) }
                    d.contentRatingText.str()?.takeIf { it.isNotBlank() }?.let { Badge(it) }
                    d.onGoingText.str()?.let { Badge(it) }
                }
                if (d.logoUrl != null) RemoteImage(d.logoUrl, d.posterHeaders, d.title, Modifier.height(80.dp).widthIn(max = 420.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
                else FText(d.title, style = Fluent.type.titleLarge, color = Color.White, maxLines = 3)
                val meta = listOfNotNull(d.ratingText.str(), d.yearText.str(), d.durationText.str()) + d.tags.take(4)
                if (meta.isNotEmpty()) FText(meta.joinToString("  ·  "), color = Color(0xCCFFFFFF), maxLines = 2)
                d.nextAiringEpisode.str()?.let { ep -> FText("$ep ${d.nextAiringDate.str().orEmpty()}", color = c.accentText, maxLines = 1) }
                var expanded by remember { mutableStateOf(false) }
                val plot = stripHtml(d.plotText.str().orEmpty())
                if (plot.isNotBlank()) {
                    FText(plot, style = Fluent.type.bodyLarge, color = Color(0xE6FFFFFF), maxLines = if (expanded) 40 else 3, modifier = Modifier.widthIn(max = 760.dp).fluentClickable(rememberInteraction(), true, RoundedCornerShape(4.dp), Role.Button) { expanded = !expanded })
                }
                d.vpnText.str()?.let { FText(it, style = Fluent.type.caption, color = c.caution) }
                ActionRow(vm, d, route, watch, favorite, subscribed, playEpisode, resumeText, hasTrailer, trailerUrl)
            }
        }
    }
}

@Composable
private fun ActionRow(
    vm: ResultViewModel2,
    d: ResultData,
    route: Route.Details,
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (playEpisode != null) {
            val label = when {
                playEpisode.tvType.isMovieType() -> if (playEpisode.getRealPosition() > 0) "Resume" else "Play"
                else -> (if (playEpisode.getRealPosition() > 0) "Resume " else "Play ") + (if (playEpisode.season != null) "S${playEpisode.season} · " else "") + "E${playEpisode.episode}"
            }
            Button(label, { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, playEpisode)) }, kind = ButtonKind.Accent, icon = Icons.Play, height = 36.dp, contentPadding = PaddingValues(horizontal = 20.dp))
            resumeText?.takeIf { it.isNotBlank() }?.let { FText(it, color = Color(0xCCFFFFFF), style = Fluent.type.caption) }
        }
        Box {
            Button(watchLabel(watch), { bookmarkOpen = true }, icon = if (watch != null && watch != WatchType.NONE) Icons.BookmarkFilled else Icons.Bookmark, height = 36.dp)
            if (bookmarkOpen) {
                MenuFlyout(
                    WatchType.entries.map { t -> MenuItem(ctx.getString(t.stringRes), checked = t == watch) { vm.updateWatchStatus(t, ctx) } },
                    onDismiss = { bookmarkOpen = false },
                )
            }
        }
        favorite?.let { fav ->
            IconButton(if (fav) Icons.FavoriteFilled else Icons.Favorite, {
                vm.toggleFavoriteStatus(ctx) { new -> if (new != null) Toasts.show(if (new) "Added to favourites" else "Removed from favourites", false) }
            }, tooltip = if (fav) "Remove from favourites" else "Add to favourites", kind = ButtonKind.Standard, size = 36.dp, tint = if (fav) Fluent.colors.critical else null)
        }
        subscribed?.let { sub ->
            IconButton(Icons.Notification, {
                vm.toggleSubscriptionStatus(ctx) { new -> if (new != null) Toasts.show(if (new) "You will be told about new episodes" else "Subscription removed", false) }
            }, tooltip = if (sub) "Unsubscribe from new episodes" else "Get notified about new episodes", kind = if (sub) ButtonKind.Accent else ButtonKind.Standard, size = 36.dp)
        }
        if (hasTrailer && trailerUrl != null) IconButton(Icons.Video, { DesktopPlatform.openExternalBrowser(trailerUrl) }, tooltip = "Watch trailer", kind = ButtonKind.Standard, size = 36.dp)
        Box {
            IconButton(Icons.More, { moreOpen = true }, tooltip = "More", kind = ButtonKind.Standard, size = 36.dp)
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

@Composable
private fun EpisodeRow(vm: ResultViewModel2, ep: ResultEpisode, wide: Boolean) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    val progress = if (ep.duration > 0) (ep.position.toFloat() / ep.duration).coerceIn(0f, 1f) else 0f
    val watched = ep.videoWatchState == com.lagradost.cloudstream3.ui.result.VideoWatchState.Watched || progress > 0.95f
    val menu: () -> List<MenuEntry> = {
        listOf(
            MenuItem("Play", Icons.Play) { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)) },
            MenuItem("Choose how to play…", Icons.Settings) { vm.handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)) },
            MenuSeparator,
            MenuItem(if (watched) "Mark as unwatched" else "Mark as watched", Icons.Check) { vm.handleAction(EpisodeClickEvent(ACTION_MARK_AS_WATCHED, ep)) },
            MenuItem("Mark all up to here as watched", Icons.Accept) { vm.handleAction(EpisodeClickEvent(ACTION_MARK_WATCHED_UP_TO_THIS_EPISODE, ep)) },
            MenuItem("Reload sources", Icons.Refresh) { vm.handleAction(EpisodeClickEvent(ACTION_RELOAD_EPISODE, ep)) },
            MenuSeparator,
            MenuItem("Download", Icons.Download) { vm.handleAction(EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, ep)) },
        )
    }
    ContextMenuArea(menu, Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (hovered) c.cardHover else Color.Transparent, shape)
                .fluentClickable(source, true, shape, Role.Button) { vm.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.width(if (wide) 192.dp else 128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(c.card)) {
                RemoteImage(ep.poster, null, ep.name, Modifier.fillMaxSize(), ContentScale.Crop)
                if (hovered) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).background(Color(0xB3000000), CircleShape).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x55FFFFFF), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Play, size = 16.dp, tint = Color.White) }
                }
                if (progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) { ProgressBar(progress, height = 3.dp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FText("${ep.episode}.", color = c.textSecondary, style = Fluent.type.bodyStrong)
                    FText(ep.name ?: "Episode ${ep.episode}", Modifier.weight(1f, fill = false), style = Fluent.type.bodyStrong, maxLines = 1)
                    if (ep.isFiller == true) Badge("Filler")
                    if (watched) Icon(Icons.Check, size = 14.dp, tint = c.success)
                }
                val meta = listOfNotNull(ep.season?.let { "Season $it" }, ep.runTime?.takeIf { it > 0 }?.let { "$it min" }, ep.score?.let { "★ " + it.toString(10, 1) })
                if (meta.isNotEmpty()) FText(meta.joinToString("  ·  "), style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
                ep.description?.takeIf { it.isNotBlank() }?.let { FText(stripHtml(it), style = Fluent.type.caption, color = c.textSecondary, maxLines = 2) }
            }
        }
    }
}
