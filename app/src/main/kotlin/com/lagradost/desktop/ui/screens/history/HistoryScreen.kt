package com.lagradost.desktop.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.lagradost.desktop.ui.screens.common.PosterGrid
import com.lagradost.desktop.ui.screens.home.openCard

/** Everything that was started and not finished (what Home shows as "Continue watching"), with a way to take titles off the list */
@Composable
fun HistoryScreen() {
    val vm = appVm<HomeViewModel>()
    LaunchedEffect(Unit) { vm.reloadStored() }
    val list by vm.resumeWatching.observeAsState()
    val items = list.orEmpty()
    PosterGrid(
        items = items,
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
                    PageHeader(
                        "History", subtitle = if (items.isEmpty()) "Nothing started yet" else "${items.size} ${if (items.size == 1) "title" else "titles"} you started and have not finished",
                        trailing = {
                            if (items.isNotEmpty()) Button("Clear history", {
                                Overlays.message("Clear the history?", "Every title is taken off this list and off Continue watching. Your library and favourites stay.", primary = "Clear", onPrimary = {
                                    DataStoreHelper.deleteAllResumeStateIds()
                                    vm.reloadStored()
                                })
                            }, kind = ButtonKind.Standard, icon = Icons.Delete)
                        },
                    )
                    Box(Modifier.height(10.dp))
                    if (items.isEmpty()) EmptyState(Icons.History, "Your history is empty", "Titles you start watching show up here so you can pick them up again.") {
                        Button("Browse titles", { Navigator.goTab(Tab.Home) }, kind = ButtonKind.Accent, icon = Icons.Home, height = 36.dp)
                    }
                }
            }
        },
    )
}
