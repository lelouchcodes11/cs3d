package com.lagradost.desktop.ui.screens.search

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import com.lagradost.desktop.ui.Toasts
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import com.lagradost.desktop.ui.fluent.shimmer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKeys
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.search.SEARCH_HISTORY_KEY
import com.lagradost.cloudstream3.ui.search.SearchHistoryItem
import com.lagradost.cloudstream3.ui.search.SearchViewModel
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.CheckBox
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.PosterSkeleton
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.SectionHeader
import com.lagradost.desktop.ui.fluent.Shelf
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.screens.common.PosterGrid
import com.lagradost.desktop.ui.screens.home.openCard
import com.lagradost.desktop.ui.shell.ShellState
import com.lagradost.desktop.ui.shell.TopBarHeight
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

private val gutter = 36.dp

private fun typeName(t: TvType): String = when (t) {
    TvType.Movie -> "Movies"
    TvType.TvSeries -> "TV series"
    TvType.Anime -> "Anime"
    TvType.AnimeMovie -> "Anime movies"
    TvType.OVA -> "OVA"
    TvType.Cartoon -> "Cartoons"
    TvType.AsianDrama -> "Asian dramas"
    TvType.Documentary -> "Documentaries"
    TvType.Live -> "Live streams"
    TvType.Torrent -> "Torrents"
    TvType.NSFW -> "NSFW"
    TvType.Others -> "Others"
    TvType.Music -> "Music"
    TvType.AudioBook -> "Audiobooks"
    TvType.CustomMedia -> "Media"
    TvType.Audio -> "Audio"
    TvType.Podcast -> "Podcasts"
    TvType.Video -> "Videos"
}

/** Same provider selection as the Android search: preferred media types, language, chosen types and providers */
private fun activeProviders(types: List<TvType>, selectedApis: Set<String>): Set<String> {
    val ctx = DesktopBootstrap.activityOrNull() ?: return selectedApis
    val default = enumValues<TvType>().sorted().filter { it != TvType.NSFW }.map { it.ordinal.toString() }.toSet()
    val preferredTypes = (androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        .getStringSet(ctx.getString(R.string.prefer_media_type_key), default)
        ?.ifEmpty { default } ?: default).mapNotNull { it.toIntOrNull() }
    val settings = ctx.getApiSettings()
    val notFiltered = selectedApis.filter { settings.contains(it) }
        .map { name -> name to getApiFromNameNull(name)?.supportedTypes }
        .filter { (_, t) -> t?.any { preferredTypes.contains(it.ordinal) } == true }
    return notFiltered.filter { (_, t) -> t?.any { types.contains(it) } == true }
        .ifEmpty { notFiltered }.map { it.first }.toSet()
}

@Composable
fun SearchScreen(route: Route.Search) {
    val c = Fluent.colors
    val vm = appVm<SearchViewModel>()
    val scope = rememberCoroutineScope()
    var types by remember { mutableStateOf(DataStoreHelper.searchPreferenceTags) }
    var apis by remember { mutableStateOf(DataStoreHelper.searchPreferenceProviders.toSet()) }
    var merged by remember { mutableStateOf(false) }
    val query = route.query?.trim().orEmpty()

    fun run() {
        if (query.length > 1) {
            // from the Home page: only the extension chosen there (the provider filters of the Search page do not apply)
            if (route.only != null) vm.searchAndCancel(query, setOf(route.only))
            else vm.searchAndCancel(query, activeProviders(types, apis))
        }
    }
    LaunchedEffect(route.nonce) {
        ShellState.searchText = query
        vm.updateHistory()
        if (query.isNotEmpty()) run() else vm.clearSearch()
    }


    val current by vm.currentSearch.observeAsState()
    val response by vm.searchResponse.observeAsState()
    val history by vm.currentHistory.observeAsState()
    val progress by vm.progress.observeAsState()
    val loading = response is Resource.Loading
    val pending = progress?.pending.orEmpty()
    val total = progress?.total ?: 0
    // a search made while extensions are still loading finds nothing in the ones that are not there yet: when more of them have loaded
    // and the page is still empty, ask again (every extension that loads raises the event, hence the wait)
    var loadedEpoch by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val onLoaded: (Boolean) -> Unit = { loadedEpoch++ }
        com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent += onLoaded
        onDispose { com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent -= onLoaded }
    }
    LaunchedEffect(loadedEpoch) {
        if (loadedEpoch == 0 || query.length <= 1) return@LaunchedEffect
        kotlinx.coroutines.delay(2_000)
        if (response !is Resource.Loading && current.orEmpty().values.none { it.list.isNotEmpty() }) run()
    }
    val validTypes = remember(route.nonce) {
        DesktopBootstrap.activityOrNull()?.let { runCatching { it.filterProviderByPreferredMedia().flatMap { api -> api.supportedTypes }.distinct().filter { t -> t != TvType.Torrent }.sorted() }.getOrNull() }.orEmpty()
    }
    // rows that were already shown do not fade in again when they scroll back into view
    val seen = remember(route.nonce) { HashSet<String>() }
    val listState = remember(route.nonce) { androidx.compose.foundation.lazy.LazyListState() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = com.lagradost.desktop.ui.fluent.Appearance.posterSize.width * (if (maxWidth >= 1008.dp) 1.05f else 0.92f)
        if (query.isEmpty()) {
            HistoryPage(history.orEmpty(), vm, validTypes, types) { types = it; DataStoreHelper.searchPreferenceTags = it }
            return@BoxWithConstraints
        }
        // every extension shows up as soon as it answers; the ones without results are left out
        val results = current.orEmpty().filterValues { it.list.isNotEmpty() }
        val searched = (total - pending.size).coerceAtLeast(0)
        val header: @Composable () -> Unit = {
            ResultsHeader(query, loading, searched, total, route.only, { Navigator.search(query) }, types, validTypes, merged, { types = it; DataStoreHelper.searchPreferenceTags = it; run() }, { merged = it }, { chooseProviders(apis) { apis = it; DataStoreHelper.searchPreferenceProviders = it.toList(); run() } })
        }

        val mergedList = if (merged) interleave(results.values.map { it.list }) else emptyList()
        if (merged && mergedList.isNotEmpty()) {
            PosterGrid(mergedList, showType = true, header = {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
            })
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = TopBarHeight + 8.dp, bottom = 32.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
                item(key = "header") { header() }
                results.entries.forEach { (name, list) ->
                    item(key = "p-$name") {
                        val fresh = remember { name !in seen }
                        val appear = remember { androidx.compose.animation.core.Animatable(if (fresh) 0f else 1f) }
                        LaunchedEffect(Unit) {
                            seen += name
                            if (fresh) appear.animateTo(1f, androidx.compose.animation.core.tween(260))
                        }
                        Column(Modifier.padding(bottom = 30.dp).graphicsLayer { alpha = appear.value }) {
                            com.lagradost.desktop.ui.fluent.RichSectionHeader(
                                name, Modifier.padding(horizontal = gutter),
                                subtitle = "${list.list.size}${if (list.hasNext) "+" else ""} result${if (list.list.size == 1) "" else "s"}",
                                onSeeAll = { Navigator.go(Route.Section("$query · $name", list.list)) },
                            )
                            Box(Modifier.height(12.dp))
                            val state = rememberLazyListState()
                            if (list.hasNext) {
                                LaunchedEffect(state, list.list.size) {
                                    snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                                        if (last >= list.list.size - 3 && list.hasNext) scope.launch { vm.expandAndReturn(name) }
                                    }
                                }
                            }
                            Shelf(list.list, cardWidth, gutter = gutter, spacing = 14.dp, state = state, key = { it.url }) { card ->
                                PosterCard(card, { openCard(card) }, cardWidth)
                            }
                        }
                    }
                }
                // a placeholder for the extensions that are still searching (a few of them, the rest is counted)
                if (pending.isNotEmpty()) {
                    items(pending.take(3), key = { "sk-$it" }) { name -> SkeletonRow(name, cardWidth) }
                    if (pending.size > 3) item(key = "more") {
                        FText("${pending.size - 3} more extension${if (pending.size - 3 == 1) "" else "s"} still searching…", Modifier.padding(horizontal = gutter), color = Fluent.colors.textTertiary)
                    }
                } else if (!loading && results.isEmpty()) {
                    item(key = "none") { NoResults(query) }
                }
            }
        }
    }
}

/** Results of several extensions as one list, best matches first (the first of each extension, then the second of each, ...) */
private fun interleave(lists: List<List<com.lagradost.cloudstream3.SearchResponse>>): List<com.lagradost.cloudstream3.SearchResponse> {
    val out = ArrayList<com.lagradost.cloudstream3.SearchResponse>()
    var index = 0
    while (true) {
        var added = 0
        for (sub in lists) if (sub.size > index) { out.add(sub[index]); added++ }
        if (added == 0) break
        index++
    }
    return out.distinctBy { it.url }
}

@Composable
private fun ResultsHeader(
    query: String,
    loading: Boolean,
    searched: Int,
    total: Int,
    only: String?,
    onSearchAll: () -> Unit,
    types: List<TvType>,
    validTypes: List<TvType>,
    merged: Boolean,
    onTypes: (List<TvType>) -> Unit,
    onMerged: (Boolean) -> Unit,
    onProviders: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 22.dp)) {
        val subtitle = when {
            only != null -> "Searching $only"
            loading && total > 0 -> "Searched $searched of $total extensions"
            loading -> "Searching…"
            else -> "Results from your extensions"
        }
        com.lagradost.desktop.ui.fluent.PageHeader("“$query”", subtitle = subtitle) {
            if (only == null) {
                com.lagradost.desktop.ui.fluent.PillTabs(listOf("By extension", "All results"), if (merged) 1 else 0, { onMerged(it == 1) })
                Button("Extensions", onProviders, icon = Icons.Filter, height = 36.dp)
            }
        }
        Box(Modifier.height(14.dp))
        // a slot of fixed height, so nothing below moves when the search ends
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (loading) {
                val shown by androidx.compose.animation.core.animateFloatAsState(if (total > 0) (searched.toFloat() / total).coerceIn(0.04f, 1f) else 0.04f, androidx.compose.animation.core.tween(300))
                ProgressBar(shown, Modifier.fillMaxWidth())
            }
        }
        Box(Modifier.height(14.dp))
        if (only != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip("Only in $only", true, {})
                Button("Search all extensions", onSearchAll, icon = Icons.Search, height = 36.dp)
            }
            return
        }
        androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(validTypes, key = { it.name }) { t ->
                Chip(typeName(t), t in types, onClick = {
                    val next = if (t in types) types - t else types + t
                    onTypes(next)
                })
            }
        }
    }
}

private fun chooseProviders(selected: Set<String>, onDone: (Set<String>) -> Unit) {
    val ctx = DesktopBootstrap.activityOrNull() ?: return
    val all = runCatching { ctx.filterProviderByPreferredMedia().map { it.name }.sorted() }.getOrNull().orEmpty()
    val working = androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(selected.filter { it in all }) }
    Overlays.show(
        Overlays.Dialog(
            title = "Search extensions",
            primary = "Apply",
            close = "Cancel",
            onPrimary = { onDone(working.toSet()) },
            width = 480.dp,
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Select all", { working.clear(); working.addAll(all) }, kind = ButtonKind.Subtle)
                    Button("Select none", { working.clear() }, kind = ButtonKind.Subtle)
                }
                Box(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(all, key = { it }) { name ->
                        CheckBox(name in working, { on -> if (on) working.add(name) else working.remove(name) }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = name)
                    }
                }
            }
        },
    )
}

@Composable
private fun SkeletonRow(cardWidth: androidx.compose.ui.unit.Dp) {
    Column(Modifier.padding(bottom = 30.dp, start = gutter)) {
        Box(Modifier.size(220.dp, 24.dp).clip(RoundedCornerShape(FluentShapes.control)).shimmer())
        Box(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(8) { PosterSkeleton(cardWidth) } }
    }
}

/** The placeholder of an extension that is still searching */
@Composable
private fun SkeletonRow(name: String, cardWidth: androidx.compose.ui.unit.Dp) {
    Column(Modifier.padding(bottom = 30.dp)) {
        Row(Modifier.padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FText(name, style = Fluent.type.subtitle.copy(fontSize = 21.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Fluent.colors.textTertiary, maxLines = 1)
            FText("searching…", style = Fluent.type.caption, color = Fluent.colors.textTertiary)
        }
        Box(Modifier.height(12.dp))
        Row(Modifier.padding(start = gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(8) { PosterSkeleton(cardWidth) } }
    }
}

@Composable
private fun NoResults(query: String) {
    com.lagradost.desktop.ui.fluent.EmptyState(Icons.Search, "No results for “$query”", "Check the spelling, pick more extensions, or try fewer filters.")
}

private val landingWidth = 680.dp

/** The Search page before anything is typed: one search box, the kinds of title to look for, the last searches */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HistoryPage(history: List<SearchHistoryItem>, vm: SearchViewModel, validTypes: List<TvType>, types: List<TvType>, onTypes: (List<TvType>) -> Unit) {
    val c = Fluent.colors
    var text by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = TopBarHeight + 64.dp, bottom = 48.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "field") {
            Column(Modifier.widthIn(max = landingWidth).fillMaxWidth()) {
                SearchField(text, { text = it }, focus) { q ->
                    val query = q.trim()
                    if (query.isNotEmpty()) { ShellState.searchText = query; Navigator.search(query) }
                }
                if (validTypes.isNotEmpty()) {
                    Box(Modifier.height(16.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        validTypes.forEach { t ->
                            Chip(typeName(t), t in types, onClick = { onTypes(if (t in types) types - t else types + t) })
                        }
                    }
                }
                Box(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText("Recent", Modifier.weight(1f), style = Fluent.type.bodyStrong, color = c.textSecondary)
                    if (history.isNotEmpty()) Button("Clear", {
                        Overlays.message("Clear search history?", "All recent searches will be removed.", primary = "Clear", onPrimary = {
                            removeKeys("$currentAccount/$SEARCH_HISTORY_KEY")
                            vm.updateHistory()
                        })
                    }, kind = ButtonKind.Subtle)
                }
                Box(Modifier.height(6.dp))
                if (history.isEmpty()) FText("Your searches show up here.", color = c.textTertiary, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
        items(history.take(8), key = { it.key }) { item ->
            Box(Modifier.widthIn(max = landingWidth).fillMaxWidth()) {
                HistoryRow(item, onOpen = { ShellState.searchText = item.searchText; Navigator.search(item.searchText) }, onRemove = {
                    removeKey("$currentAccount/$SEARCH_HISTORY_KEY", item.key)
                    vm.updateHistory()
                })
            }
        }
    }
}

/** The search box of the Search page: flat, no glow */
@Composable
private fun SearchField(text: String, onText: (String) -> Unit, focus: androidx.compose.ui.focus.FocusRequester, onSubmit: (String) -> Unit) {
    val c = Fluent.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val border by androidx.compose.animation.animateColorAsState(if (focused) c.accent else c.stroke, com.lagradost.desktop.ui.fluent.FluentMotion.tweenStd(140))
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .clip(shape).background(c.card, shape).border(if (focused) 1.5.dp else androidx.compose.ui.unit.Dp.Hairline, border, shape)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Search, size = 18.dp, tint = if (focused) c.accentText else c.textSecondary)
        Box(Modifier.width(14.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) FText("Search movies, series, anime…", style = Fluent.type.bodyLarge, color = c.textTertiary, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                text, onText,
                Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && (e.key == androidx.compose.ui.input.key.Key.Enter || e.key == androidx.compose.ui.input.key.Key.NumPadEnter)) { onSubmit(text); true } else false },
                singleLine = true,
                textStyle = Fluent.type.bodyLarge.copy(color = c.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            )
        }
        // always there (invisible while empty), so the box does not change when typing starts
        Box(Modifier.graphicsLayer { alpha = if (text.isEmpty()) 0f else 1f }) {
            com.lagradost.desktop.ui.fluent.PillButton("Search", null, primary = true, onClick = { if (text.isNotEmpty()) onSubmit(text) }, height = 40.dp)
        }
    }
}

/** One earlier search: the remove button sits in a reserved slot, hovering only changes colours */
@Composable
private fun HistoryRow(item: SearchHistoryItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(shape).background(if (hovered) c.cardHover else Color.Transparent, shape)
            .hoverable(source).fluentClickable(source, true, shape, Role.Button, onOpen).padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.History, size = 15.dp, tint = c.textTertiary)
        Box(Modifier.width(14.dp))
        FText(item.searchText, Modifier.weight(1f), maxLines = 1)
        Box(Modifier.size(32.dp).graphicsLayer { alpha = if (hovered) 1f else 0f }) {
            IconButton(Icons.Close, onRemove, size = 32.dp, iconSize = 10.dp, tooltip = "Remove")
        }
    }
}
