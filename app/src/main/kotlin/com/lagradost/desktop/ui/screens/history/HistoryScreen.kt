package com.lagradost.desktop.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.EmptyState
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.PageHeader
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.screens.common.PosterGrid
import com.lagradost.desktop.ui.screens.home.openCard

/** Everything that was started and not finished (what Home shows as "Continue watching"), with a way to take titles off the list */
@Composable
fun HistoryScreen() {
    val vm = appVm<HomeViewModel>()
    LaunchedEffect(Unit) { vm.reloadStored() }
    val list by vm.resumeWatching.observeAsState()
    val all = list.orEmpty()
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val items = remember(all, trimmed) {
        if (trimmed.isEmpty()) all else all.filter { it.name.contains(trimmed, ignoreCase = true) || it.apiName.contains(trimmed, ignoreCase = true) }
    }
    PosterGrid(
        items = items,
        subtitle = { card -> com.lagradost.desktop.ui.screens.home.resumeSubtitle(card) },
        menu = { card ->
            val r = card as? DataStoreHelper.ResumeWatchingResult
            listOf(
                MenuItem("Resume", Icons.Play) { openCard(card) },
                MenuItem("Open title page", Icons.Info) { Navigator.openDetails(card) },
                MenuItem("Remove from history", Icons.Delete, destructive = true) {
                    r?.let { DataStoreHelper.removeLastWatched(it.parentId); vm.reloadStored() }
                },
            )
        },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 10.dp)) {
                    val countText = when {
                        all.isEmpty() -> null
                        trimmed.isNotEmpty() -> "${items.size} of ${all.size} ${if (all.size == 1) "title" else "titles"}"
                        else -> "${all.size} ${if (all.size == 1) "title" else "titles"}"
                    }
                    PageHeader(
                        "History", icon = Icons.History, tint = androidx.compose.ui.graphics.Color(0xFF9B7BFF), subtitle = countText,
                        trailing = {
                            if (all.size >= 6) TextBox(query, { query = it }, Modifier.widthIn(min = 200.dp, max = 260.dp), placeholder = "Filter history", leadingIcon = Icons.Search)
                            Button("Stats", { Navigator.go(com.lagradost.desktop.core.Route.Stats) }, kind = ButtonKind.Standard, icon = Icons.Chart)
                            if (all.isNotEmpty()) Button("Clear history", {
                                Overlays.message("Clear the history?", "Every title is taken off this list and off Continue watching. Your library and favourites stay.", primary = "Clear", onPrimary = {
                                    DataStoreHelper.deleteAllResumeStateIds()
                                    query = ""
                                    vm.reloadStored()
                                })
                            }, kind = ButtonKind.Standard, icon = Icons.Delete)
                        },
                    )
                    Box(Modifier.height(10.dp))
                    if (all.isEmpty()) EmptyState(Icons.History, "Your history is empty", "Titles you start watching show up here so you can pick them up again.") {
                        Button("Browse titles", { Navigator.goTab(Tab.Home) }, kind = ButtonKind.Accent, icon = Icons.Home, height = 36.dp)
                    } else if (items.isEmpty()) EmptyState(Icons.Search, "No matches for “$trimmed”", "Try another title name or clear the filter.") {
                        Button("Clear filter", { query = "" }, kind = ButtonKind.Accent, height = 36.dp)
                    }
                }
            }
        },
    )
}
