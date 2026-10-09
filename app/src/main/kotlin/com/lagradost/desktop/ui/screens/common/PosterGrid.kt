package com.lagradost.desktop.ui.screens.common

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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = TopBarHeight + 20.dp, bottom = 40.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        header?.invoke(this)
        items(unique, key = { it.url + it.apiName }) { card ->
            PosterCard(card, { if (onOpen != null) onOpen(card) else openCard(card) }, width = null, showType = showType, menu = menu?.let { m -> { m(card) } })
        }
    }
    }
}

/** "See all": one list in a full page grid */
@Composable
fun SectionPage(route: Route.Section) {
    PosterGrid(route.items, header = {
        item(span = { GridItemSpan(maxLineSpan) }) {
            com.lagradost.desktop.ui.fluent.PageHeader(route.title, Modifier.padding(bottom = 8.dp), subtitle = "${route.items.size} titles")
        }
    })
}
