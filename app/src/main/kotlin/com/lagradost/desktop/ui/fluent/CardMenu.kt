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
 * Right click on a catalog title: the card lifts out of the shelf to the middle of the window over a dimmed background and the few things
 * that can be done with it (play, open, library, favourites) come in beside or under it, as in Nuvio.
 */
@Composable
fun CatalogCardMenu(card: SearchResponse, content: @Composable () -> Unit) {
    // the place of the card, asked only when it is right-clicked (no state: nothing is redrawn while the shelf scrolls)
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier
            .onGloballyPositioned { place[0] = it }
            .onSecondaryClick {
                val from = place[0]?.takeIf { c -> c.isAttached }?.boundsInRoot()
                Overlays.show(Overlays.Dialog(title = null, close = null, fullscreen = true) { dismiss -> PosterActions(card, from, dismiss) })
            },
    ) { content() }
}

private val PanelWidth = 304.dp

@Composable
private fun PosterActions(card: SearchResponse, from: Rect?, dismiss: () -> Unit) {
    val c = Fluent.colors
    // read when it opens: what the title is in the library now
    val status = remember(card) { CardLibrary.status(card) }
    val favourite = remember(card) { CardLibrary.isFavourite(card) }
    val lift = remember { Animatable(0f) }
    val panel = remember { Animatable(0f) }
    var target by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(target != null) {
        if (target == null) return@LaunchedEffect
        lift.animateTo(1f, FluentMotion.springSoft())
    }
    LaunchedEffect(Unit) { delay(110); panel.animateTo(1f, tween(FluentMotion.ms(240), easing = FluentMotion.enter)) }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } }, contentAlignment = Alignment.Center) {
        // beside each other in a window that is wide and not tall, one above the other when there is height for a big poster
        val panelHeight = 330.dp
        val stacked = maxHeight >= 260.dp + 78.dp + panelHeight + 48.dp
        val posterHeight: Dp = if (stacked) (maxHeight - 78.dp - panelHeight - 48.dp).coerceIn(260.dp, 400.dp) else (maxHeight * 0.74f).coerceIn(220.dp, 440.dp)
        val posterWidth = posterHeight * (2f / 3f)

        val poster = @Composable {
            // the slot is measured without the lift, the poster inside it travels from the card to the slot
            Box(Modifier.size(posterWidth, posterHeight).onGloballyPositioned { target = it.boundsInRoot() }) {
                Box(
                    Modifier
                        .size(posterWidth, posterHeight)
                        .graphicsLayer {
                            val t = target
                            if (t == null) { alpha = 0f; return@graphicsLayer }
                            val p = lift.value
                            val f = from
                            if (f != null && f.width > 0f) {
                                val s0 = f.width / t.width
                                scaleX = lerp(s0, 1f, p); scaleY = scaleX
                                translationX = lerp(f.center.x - t.center.x, 0f, p)
                                translationY = lerp(f.center.y - t.center.y, 0f, p)
                            } else {
                                scaleX = lerp(0.9f, 1f, p); scaleY = scaleX
                                alpha = p
                            }
                        }
                        .shadow(36.dp, RoundedCornerShape(FluentShapes.card), clip = false)
                        .clip(RoundedCornerShape(FluentShapes.card))
                        .hairBorder(Color(0x44FFFFFF), RoundedCornerShape(FluentShapes.card))
                        .pointerInput(Unit) { detectTapGestures { } },
                ) { PosterImage(card, Modifier.fillMaxSize()) }
            }
        }
        val actions = @Composable {
            Column(
                Modifier.graphicsLayer { alpha = panel.value; translationY = (1f - panel.value) * 14.dp.toPx() },
                horizontalAlignment = if (stacked) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                // what it is
                val sub = listOfNotNull(CardLibrary.yearOf(card)?.toString(), card.type?.name, card.apiName.takeIf { it.isNotBlank() }).joinToString("  ·  ")
                FText(card.name, Modifier.width(PanelWidth), style = Fluent.type.subtitle.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2, textAlign = if (stacked) TextAlign.Center else TextAlign.Start)
                if (sub.isNotBlank()) FText(sub, Modifier.width(PanelWidth), style = Fluent.type.caption, color = Color(0xB3FFFFFF), maxLines = 1, textAlign = if (stacked) TextAlign.Center else TextAlign.Start)
                Box(Modifier.height(14.dp))
                ActionPanel(card, status, favourite, dismiss)
            }
        }
        if (stacked) Column(horizontalAlignment = Alignment.CenterHorizontally) { poster(); Box(Modifier.height(18.dp)); actions() }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) { poster(); actions() }
    }
}

@Composable
private fun ActionPanel(card: SearchResponse, status: WatchType, favourite: Boolean, close: () -> Unit) {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val saved = status != WatchType.NONE
    val shape = RoundedCornerShape(FluentShapes.overlay)
    Column(
        Modifier
            .width(PanelWidth)
            .shadow(32.dp, shape, clip = false)
            .background(c.flyout, shape)
            .hairBorder(if (c.dark) Color(0x66757575) else Color(0x33000000), shape)
            .clip(shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 6.dp),
    ) {
        CardMenuRow("Play", Icons.Play) { close(); Navigator.openDetails(card, START_ACTION_RESUME_LATEST, null) }
        CardMenuRow("Open title page", Icons.Info) { close(); Navigator.openDetails(card) }
        Box(Modifier.padding(vertical = 4.dp)) { Divider() }
        // the library: one click on a list
        FText(
            if (saved) "In your library: ${ctx.getString(status.stringRes)}" else "Add to library",
            Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.textSecondary, maxLines = 1,
        )
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchType.entries.filter { it != WatchType.NONE }.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { t -> Box(Modifier.weight(1f)) { StatusChip(ctx.getString(t.stringRes), t == status) { close(); if (t != status) CardLibrary.setStatus(card, t) } } }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        Box(Modifier.height(4.dp))
        if (saved) CardMenuRow("Remove from library", Icons.Delete, destructive = true) { close(); CardLibrary.setStatus(card, WatchType.NONE) }
        Box(Modifier.padding(vertical = 4.dp)) { Divider() }
        CardMenuRow(if (favourite) "Remove from favourites" else "Add to favourites", if (favourite) Icons.FavoriteFilled else Icons.Favorite) { close(); CardLibrary.toggleFavourite(card) }
    }
}

@Composable
private fun CardMenuRow(text: String, icon: String, destructive: Boolean = false, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.row)
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            .height(34.dp)
            .background(if (hovered) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { Icon(icon, size = 16.dp, tint = if (destructive) c.critical else c.text) }
        Box(Modifier.width(12.dp))
        FText(text, Modifier.weight(1f), color = if (destructive) c.critical else c.text, maxLines = 1)
    }
}

/** One list of the library; the one the title is on is lit */
@Composable
private fun StatusChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = FluentShapes.pill
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(if (selected || hovered) c.controlHover else c.control, shape)
            .hairBorder(if (selected) c.selectedEdge else c.stroke, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) { Icon(Icons.Check, size = 12.dp, tint = c.text); Box(Modifier.width(6.dp)) }
        FText(text, style = Fluent.type.caption, color = c.text, maxLines = 1)
    }
}
