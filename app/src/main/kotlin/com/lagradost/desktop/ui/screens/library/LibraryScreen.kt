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
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.runtime.AndroidRuntime
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

/** What the library shows: everything, only anime, or only films and series */
private enum class Kind(val label: String) { All("All"), Anime("Anime"), Other("Movies & TV") }

private const val KIND_KEY = "desktop_library_kind"

private fun savedKind(): Kind = runCatching {
    Kind.valueOf(PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).getString(KIND_KEY, null) ?: "All")
}.getOrDefault(Kind.All)

private fun saveKind(kind: Kind) {
    runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).edit().putString(KIND_KEY, kind.name).apply() }
}

private fun SearchResponse.isAnimeTitle() = type == TvType.Anime || type == TvType.AnimeMovie || type == TvType.OVA

private fun List<SearchResponse>.ofKind(kind: Kind) = when (kind) {
    Kind.All -> this
    Kind.Anime -> filter { it.isAnimeTitle() }
    Kind.Other -> filter { !it.isAnimeTitle() }
}

/**
 * A title of an AniList / MyAnimeList / Simkl list is no extension's page. It used to open only when some installed extension happened to claim
 * the address (StreamPlay's anime provider claims anilist.co; without it the page said "This provider does not exist"). Now: an extension that
 * claims the address opens it as before, and when none does the extensions are searched for the title, as Android does.
 */
private fun openLibraryItem(card: SearchResponse) {
    if (card is SyncAPI.LibraryItem && !opensDirectly(card)) Navigator.search(card.name) else openCard(card)
}

private fun opensDirectly(card: SyncAPI.LibraryItem): Boolean {
    if (APIHolder.getApiFromNameNull(card.apiName) != null) return true
    // an extension without an address of its own would "claim" every address
    return APIHolder.getApiFromUrlNull(card.url)?.mainUrl?.isNotBlank() == true
}

@Composable
fun LibraryScreen() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val vm = appVm<LibraryViewModel>()
    val pages by vm.pages.observeAsState()
    val pageIndex by vm.currentPage.observeAsState()
    val apiName by vm.currentApiName.observeAsState()
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(savedKind()) }

    LaunchedEffect(Unit) { vm.reloadPages(false) }

    val res = pages
    val titles = (res as? Resource.Success)?.value
    val index = (pageIndex ?: 0).coerceIn(0, ((titles?.size ?: 1) - 1).coerceAtLeast(0))
    val everything = titles?.getOrNull(index)?.items.orEmpty()
    val items = everything.ofKind(kind)
    val apis = vm.availableApiNames
    // how many titles of each kind the whole list has (the switch shows them; one without any anime hides the switch's Anime count as 0)
    val allTitles = titles?.flatMap { it.items }.orEmpty()
    val animeCount = allTitles.count { it.isAnimeTitle() }

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
                onOpen = ::openLibraryItem,
                menu = { card ->
                    val sync = card as? SyncAPI.LibraryItem
                    buildList<com.lagradost.desktop.ui.fluent.MenuEntry> {
                        add(MenuItem("Open", Icons.Play) { openLibraryItem(card) })
                        if (sync != null) add(MenuItem("Search all extensions", Icons.Search) { Navigator.search(sync.name) })
                        if (sync != null && sync.url.startsWith("http")) add(MenuItem("Open on ${sync.apiName}", Icons.Globe) { DesktopPlatform.openExternalBrowser(sync.url) })
                    }
                },
                header = {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(bottom = 10.dp)) {
                            com.lagradost.desktop.ui.fluent.PageHeader("Library", subtitle = "${allTitles.size} titles${if (animeCount > 0 && animeCount < allTitles.size) " · $animeCount anime" else ""}${apiName?.takeIf { apis.size > 1 }?.let { " · $it" } ?: ""}")
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
                            // anime only / films and series only (shown when the list has both kinds), then the statuses (Watching, Completed ...)
                            val mixed = animeCount > 0 && animeCount < allTitles.size
                            if (mixed || kind != Kind.All) {
                                com.lagradost.desktop.ui.fluent.PillTabs(
                                    Kind.entries.map { it.label }, kind.ordinal, { kind = Kind.entries[it]; saveKind(kind) },
                                    counts = listOf(allTitles.size, animeCount, allTitles.size - animeCount),
                                )
                                Box(Modifier.height(10.dp))
                            }
                            if (titles != null && titles.isNotEmpty()) {
                                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    com.lagradost.desktop.ui.fluent.PillTabs(titles.map { it.title.asStringNull(ctx) ?: "" }, index, { vm.switchPage(it) }, counts = titles.map { it.items.ofKind(kind).size })
                                }
                                Box(Modifier.height(8.dp))
                            }
                            if (items.isEmpty()) {
                                if (everything.isNotEmpty()) {
                                    com.lagradost.desktop.ui.fluent.EmptyState(Icons.Library, if (kind == Kind.Anime) "No anime in this list" else "No films or series in this list", "The other titles of this list are under “All”.") {
                                        Button("Show all", { kind = Kind.All; saveKind(kind) }, kind = ButtonKind.Accent, height = 36.dp)
                                    }
                                } else com.lagradost.desktop.ui.fluent.EmptyState(Icons.Library, "Nothing here yet", "Use “Add to library” on a title to collect it in this list.") {
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
