package com.lagradost.desktop.ui.screens.common

import com.lagradost.desktop.ui.fluent.smoothWheel
import com.lagradost.desktop.ui.fluent.touchScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.lagradost.desktop.ui.fluent.reveal
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.MenuEntry
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.screens.home.openCard
import com.lagradost.desktop.ui.shell.TopBarHeight

/** Responsive poster grid: columns fill the width, cards keep a 2:3 poster */
@Composable
fun PosterGrid(
    items: List<SearchResponse>,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    showType: Boolean = false,
    onEnd: (() -> Unit)? = null,
    menu: ((SearchResponse) -> List<MenuEntry>)? = null,
    header: (LazyGridScope.() -> Unit)? = null,
    /** What a click on a card does; null opens its title page */
    onOpen: ((SearchResponse) -> Unit)? = null,
    /** After the last card (a "loading more" line) */
    footer: (LazyGridScope.() -> Unit)? = null,
) {
    if (onEnd != null) {
        LaunchedEffect(state, items.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                if (last >= state.layoutInfo.totalItemsCount - 6) onEnd()
            }
        }
    }
    // a repeated key throws while measuring and blanks the window: show each card once
    val unique = androidx.compose.runtime.remember(items) { items.distinctBy { it.url + it.apiName } }
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
    FluentScrollbar(state, TopBarHeight)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(com.lagradost.desktop.ui.fluent.Appearance.posterSize.width),
        state = state,
        modifier = Modifier.fillMaxSize().touchScroll(state).smoothWheel(state),
        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = TopBarHeight + 20.dp, bottom = 40.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(unique, key = { _, it -> it.url + it.apiName }) { i, card ->
            // the first posters of a page arrive one after the other (once per poster and run of the app)
            Box(Modifier.reveal("grid/" + card.url + card.apiName, (i % 8) * 40 + (i / 8).coerceAtMost(3) * 40, rise = 18.dp)) {
                PosterCard(card, { if (onOpen != null) onOpen(card) else openCard(card) }, width = null, showType = showType, menu = menu?.let { m -> { m(card) } })
            }
        }
        footer?.invoke(this)
    }
    }
}

/** "See all": one list in a full page grid; when the extension has more pages they are asked for as the end of the list comes into view */
@Composable
fun SectionPage(route: Route.Section) {
    var items by androidx.compose.runtime.remember(route) { androidx.compose.runtime.mutableStateOf(route.items) }
    var hasMore by androidx.compose.runtime.remember(route) { androidx.compose.runtime.mutableStateOf(route.more != null) }
    var loading by androidx.compose.runtime.remember(route) { androidx.compose.runtime.mutableStateOf(false) }
    // pages that brought nothing new in a row (an extension that repeats itself, a request that was refused): the list is complete
    var empty by androidx.compose.runtime.remember(route) { androidx.compose.runtime.mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val loadMore: () -> Unit = {
        val more = route.more
        if (more != null && hasMore && !loading) {
            loading = true
            scope.launch {
                try {
                    val (list, next) = more()
                    val known = items.mapTo(HashSet()) { it.url + it.apiName }
                    val grew = list.any { (it.url + it.apiName) !in known }
                    if (grew) { items = list; empty = 0 } else { empty++; kotlinx.coroutines.delay(600) }
                    if (!next || empty >= 3) hasMore = false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    hasMore = false
                } finally {
                    loading = false
                }
            }
        }
    }
    PosterGrid(
        items, onEnd = if (route.more != null) loadMore else null,
        header = {
            item(span = { GridItemSpan(maxLineSpan) }) {
                com.lagradost.desktop.ui.fluent.PageHeader(route.title, Modifier.padding(bottom = 8.dp), subtitle = "${items.size}${if (hasMore) "+" else ""} titles")
            }
        },
        footer = {
            if (hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    com.lagradost.desktop.ui.fluent.ProgressRing(size = 28.dp)
                }
            }
        },
    )
}
