package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.ui.Toasts
import kotlinx.coroutines.delay

/**
 * What the right-click menu of a title in a catalog does to the library without opening the title page: the same calls the title page's
 * bookmark button makes (so a title added here is the one the page shows as added, and the library lists it), and the favourites.
 */
object CardLibrary {
    private val viewModel by lazy { ResultViewModel2() }

    internal fun yearOf(card: SearchResponse): Int? = when (card) {
        is MovieSearchResponse -> card.year
        is TvSeriesSearchResponse -> card.year
        is AnimeSearchResponse -> card.year
        is DataStoreHelper.LibrarySearchResponse -> card.year
        else -> null
    }

    /** The title as the page would load it (same id as the page computes, so the bookmark is the page's bookmark) */
    fun response(card: SearchResponse) = ResultViewModel2.LoadResponseFromSearch(
        name = card.name, url = card.url, apiName = card.apiName, type = card.type ?: TvType.Others, posterUrl = card.posterUrl,
        year = yearOf(card), score = card.score, posterHeaders = card.posterHeaders, id = card.id,
    )

    fun status(card: SearchResponse): WatchType = DataStoreHelper.getResultWatchState(response(card).getId())

    fun isFavourite(card: SearchResponse): Boolean = DataStoreHelper.getFavoritesData(response(card).getId()) != null

    /** The same for a title whose page data is loaded already (the banner of Home): the list it is on, and putting it on one or taking it off */
    fun statusOf(load: com.lagradost.cloudstream3.LoadResponse): WatchType = DataStoreHelper.getResultWatchState(load.getId())

    fun setStatus(load: com.lagradost.cloudstream3.LoadResponse, status: WatchType) {
        val ctx = DesktopBootstrap.activity
        val name = load.name
        viewModel.updateWatchStatus(status, ctx, load) { changed ->
            if (changed) Toasts.show(if (status == WatchType.NONE) "Removed “$name” from your library" else "“$name” is now in ${ctx.getString(status.stringRes)}", false)
        }
    }

    /** [WatchType.NONE] takes the title off the library */
    fun setStatus(card: SearchResponse, status: WatchType) {
        val ctx = DesktopBootstrap.activity
        val name = card.name
        viewModel.updateWatchStatus(status, ctx, response(card)) { changed ->
            if (changed) Toasts.show(if (status == WatchType.NONE) "Removed “$name” from your library" else "“$name” is now in ${ctx.getString(status.stringRes)}", false)
        }
    }

    fun toggleFavourite(card: SearchResponse) {
        val r = response(card)
        val id = r.getId()
        if (DataStoreHelper.getFavoritesData(id) != null) {
            DataStoreHelper.removeFavoritesData(id)
            Toasts.show("Removed “${card.name}” from your favourites", false)
        } else {
            val now = System.currentTimeMillis()
            DataStoreHelper.setFavoritesData(id, DataStoreHelper.FavoritesData(now, id, now, r.name, r.url, r.apiName, r.type, r.posterUrl, r.year, r.syncData, plot = r.plot, score = r.score, tags = r.tags))
            Toasts.show("Added “${card.name}” to your favourites", false)
        }
        MainActivity.reloadLibraryEvent(true)
    }
}

/**
 * Right click on a catalog title: a small menu at the pointer, like any desktop app's. Play and the title page first, then the library lists (the
 * one the title is on is ticked) and the favourites. The same calls the title page's buttons make.
 */
@Composable
fun CatalogCardMenu(card: SearchResponse, content: @Composable () -> Unit) {
    ContextMenuArea({ cardEntries(card) }) { content() }
}

/** Read when the menu opens: what the title is in the library now */
private fun cardEntries(card: SearchResponse): List<MenuEntry> {
    val ctx = DesktopBootstrap.activity
    val status = CardLibrary.status(card)
    val favourite = CardLibrary.isFavourite(card)
    val lists = WatchType.entries.filter { it != WatchType.NONE }
    return buildList {
        add(MenuItem("Play", Icons.Play) { Navigator.openDetails(card, START_ACTION_RESUME_LATEST, null) })
        add(MenuItem("Open title page", Icons.Info) { Navigator.openDetails(card) })
        add(MenuSeparator)
        // the library: one click puts the title on a list; the list it is on is ticked, and clicking it again takes the title off
        for (t in lists) add(MenuItem(if (t == status) "In ${ctx.getString(t.stringRes)}: remove" else "Add to ${ctx.getString(t.stringRes)}", Icons.Library, checked = t == status) { CardLibrary.setStatus(card, if (t == status) WatchType.NONE else t) })
        add(MenuSeparator)
        add(
            MenuItem(
                if (favourite) "Remove from favourites" else "Add to favourites",
                if (favourite) Icons.FavoriteFilled else Icons.Favorite,
            ) { CardLibrary.toggleFavourite(card) },
        )
    }
}
