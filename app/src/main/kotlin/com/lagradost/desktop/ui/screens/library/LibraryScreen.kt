package com.lagradost.desktop.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.glass
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.library.LibraryViewModel
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.TabBar
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.screens.common.PosterGrid
import com.lagradost.desktop.ui.screens.home.openCard
import com.lagradost.desktop.ui.shell.TopBarHeight

@Composable
fun LibraryScreen() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val vm = appVm<LibraryViewModel>()
    val pages by vm.pages.observeAsState()
    val pageIndex by vm.currentPage.observeAsState()
    val apiName by vm.currentApiName.observeAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.reloadPages(false) }

    val res = pages
    val titles = (res as? Resource.Success)?.value
    val index = (pageIndex ?: 0).coerceIn(0, ((titles?.size ?: 1) - 1).coerceAtLeast(0))
    val items = titles?.getOrNull(index)?.items.orEmpty()
    val apis = vm.availableApiNames

    Box(Modifier.fillMaxSize()) {
        when {
            res == null || res is Resource.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProgressRing() }
            res is Resource.Failure -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Warning, size = 40.dp, tint = c.caution)
                Box(Modifier.height(12.dp))
                FText("The library could not be loaded", style = Fluent.type.subtitle)
                FText(res.errorString, color = c.textSecondary, maxLines = 3)
                Box(Modifier.height(12.dp))
                Button("Try again", { vm.reloadPages(true) }, kind = ButtonKind.Accent, icon = Icons.Refresh)
            }
            else -> PosterGrid(
                items = items,
                menu = { card -> listOf(MenuItem("Open", Icons.Play) { openCard(card) }) },
                header = {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(bottom = 10.dp)) {
                            com.lagradost.desktop.ui.fluent.PageHeader("Library", subtitle = "${titles?.sumOf { it.items.size } ?: 0} titles${apiName?.takeIf { apis.size > 1 }?.let { " · $it" } ?: ""}")
                            Box(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth().glass(FluentShapes.overlay).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextBox(query, { query = it; vm.currentSortingMethod?.let { m -> vm.sort(ListSorting.Query, it.ifBlank { null }) } }, Modifier.weight(1f).widthIn(max = 420.dp), placeholder = "Search your library", leadingIcon = Icons.Search)
                                Box(Modifier.weight(0.01f))
                                if (vm.sortingMethods.size > 1) ComboBox(
                                    vm.sortingMethods, vm.currentSortingMethod, { ctx.getString(it.stringRes) },
                                    { vm.sort(it, query.ifBlank { null }) }, icon = Icons.Sort, minWidth = 150.dp,
                                )
                                if (apis.size > 1) ComboBox(apis, apiName?.takeIf { it in apis } ?: apis.firstOrNull(), { it }, { vm.switchList(it) }, icon = Icons.Cloud, minWidth = 140.dp)
                                IconButton(Icons.Refresh, { vm.reloadPages(true) }, tooltip = "Refresh", kind = ButtonKind.Standard)
                            }
                            Box(Modifier.height(18.dp))
                            if (titles != null && titles.isNotEmpty()) {
                                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    com.lagradost.desktop.ui.fluent.PillTabs(titles.map { it.title.asStringNull(ctx) ?: "" }, index, { vm.switchPage(it) }, counts = titles.map { it.items.size })
                                }
                                Box(Modifier.height(8.dp))
                            }
                            if (items.isEmpty()) {
                                com.lagradost.desktop.ui.fluent.EmptyState(Icons.Library, "Nothing here yet", "Use “Add to library” on a title to collect it in this list.") {
                                    Button("Browse titles", { Navigator.goTab(Tab.Home) }, kind = ButtonKind.Accent, icon = Icons.Home, height = 36.dp)
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
