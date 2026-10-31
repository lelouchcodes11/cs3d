package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.ui.components.RemoteImage
import kotlinx.coroutines.launch

/** Shimmering placeholder while content loads */
@Composable
fun Modifier.shimmer(): Modifier {
    val c = Fluent.colors
    val phase = shimmerPhase()
    return this.drawBehind {
        val x = (phase * 2f - 0.5f) * size.width
        // Light mode: a grey block with a white glint (a white card on the pale page left the skeleton of a loading banner invisible)
        // dark: a lit block you can see on the black page (the card tint, 4 % white, was close to invisible on it)
        // a flat block that breathes (no band of light sweeping over it)
        val breath = 0.5f + 0.5f * kotlin.math.sin(phase * 2f * Math.PI.toFloat())
        drawRect(if (c.dark) Color.White.copy(alpha = 0.07f + 0.05f * breath) else Color(0xFFE6E6EC).copy(alpha = 0.7f + 0.3f * breath))
    }
}

@Composable
private fun shimmerPhase(): Float {
    val t = rememberInfiniteTransition()
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)))
    return phase
}

fun qualityLabel(q: SearchQuality?): String? = when (q) {
    null -> null
    SearchQuality.FourK -> "4K"
    SearchQuality.BlueRay -> "BluRay"
    SearchQuality.WebRip -> "WEB"
    SearchQuality.HdCam -> "HD CAM"
    SearchQuality.CamRip -> "CAM"
    else -> q.name.uppercase()
}

fun typeLabel(t: TvType?): String? = when (t) {
    null -> null
    TvType.Movie, TvType.AnimeMovie -> "Movie"
    TvType.TvSeries -> "Series"
    TvType.Anime -> "Anime"
    TvType.OVA -> "OVA"
    TvType.Cartoon -> "Cartoon"
    TvType.AsianDrama -> "Drama"
    TvType.Documentary -> "Documentary"
    TvType.Live -> "Live"
    TvType.Torrent -> "Torrent"
    else -> null
}

/** Fraction watched (0..1) of a continue-watching entry, null for ordinary results */
fun SearchResponse.watchedFraction(): Float? {
    // a library entry of AniList / MyAnimeList: how many episodes of the whole are done (nothing for a title not started or finished)
    (this as? com.lagradost.cloudstream3.syncproviders.SyncAPI.LibraryItem)?.let { li ->
        val total = li.episodesTotal ?: return null
        val done = li.episodesCompleted ?: return null
        return if (total > 0 && done in 1 until total) done.toFloat() / total else null
    }
    val pos = (this as? DataStoreHelper.ResumeWatchingResult)?.watchPos ?: return null
    if (pos.duration <= 0) return null
    return (pos.position.toFloat() / pos.duration.toFloat()).coerceIn(0f, 1f)
}

@Composable
fun PosterImage(item: SearchResponse, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val c = Fluent.colors
    // until the picture is there (a slow network, a title with none) the card says what it is, instead of a dark box with a camera in it
    var loaded by androidx.compose.runtime.remember(item.posterUrl) { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier.background(c.card), contentAlignment = Alignment.Center) {
        if (!loaded) Column(Modifier.padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Video, size = 24.dp, tint = c.textDisabled)
            if (item.name.isNotBlank()) FText(item.name, style = Fluent.type.caption, color = c.textTertiary, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        RemoteImage(item.posterUrl, item.posterHeaders, item.name, Modifier.fillMaxSize(), scale, onSize = { _, _ -> loaded = true })
    }
}

/** Poster (2:3) or landscape (16:9) card with title below. Hover lifts the card and shows play. */
@Composable
fun PosterCard(
    item: SearchResponse,
    onClick: () -> Unit,
    width: Dp?,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    subtitle: String? = null,
    showType: Boolean = false,
    menu: (() -> List<MenuEntry>)? = null,
    /** Play on the card's own button (the interface of a special theme shows one); the title page opens when null */
    onPlay: (() -> Unit)? = null,
) {
    // the interface of a special theme (Netflix) has its own card
    if (Skins.spec != null) {
        SkinPosterCard(item, onClick, width, modifier, landscape, subtitle, showType, onPlay, menu)
        return
    }
    val c = Fluent.colors
    val source = rememberInteraction()
    val pointerOn by source.collectIsHoveredAsState()
    // not while the page is moving under the pointer (see ScrollActivity); only the card under the pointer re-composes when that changes
    val hovered by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { pointerOn && !ScrollActivity.active } }
    val zoomOn = Appearance.hoverZoomOn
    val pressed by source.collectIsPressedAsState()
    // the card rises on a spring when the pointer comes (a little overshoot) and gives way under a press
    val liftSpec = FluentMotion.springSoft<Float>()
    val lift by animateFloatAsState(if (pressed) 0.975f else if (hovered && zoomOn) 1.05f else 1f, liftSpec, label = "lift")
    // the info that comes up on the card (title, play / add / more, score, year): a tween, so it never overshoots
    val info by animateFloatAsState(if (hovered) 1f else 0f, FluentMotion.tweenIn(200), label = "cardInfo")
    val shape = RoundedCornerShape(FluentShapes.card)
    // a flat hairline gives the poster an edge on the black page (brighter under the pointer)
    val edge = Color.White.copy(alpha = if (hovered) 0.28f else 0.1f)
    // the list a title is on is read only while the pointer is on it (a database read per card otherwise)
    // only a title of an extension can be put on the library here (an AniList entry is already on a list, a TMDB title belongs to no extension)
    val owned = item.apiName.isNotBlank() && item.url.isNotBlank() && item !is com.lagradost.desktop.tmdb.TmdbCard && com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(item.apiName) != null
    val savedInLibrary = androidx.compose.runtime.remember(item.url, hovered) {
        hovered && owned && runCatching { CardLibrary.status(item) != com.lagradost.cloudstream3.ui.WatchType.NONE }.getOrDefault(false)
    }
    // the mark follows a click at once (the library answers a moment later)
    var flipped by androidx.compose.runtime.remember(item.url, hovered) { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    val saved = flipped ?: savedInLibrary
    val body = @Composable {
        Column(
            modifier
                .let { if (width != null) it.width(width) else it.fillMaxWidth() }
                // the card under the pointer is drawn above its neighbours: it grows over them
                .zIndex(if (hovered) 2f else 0f)
                // fluentClickable adds the hover tracking and the focus ring itself (they were added a second time here: two more nodes and a second focus collector per card)
                .fluentClickable(source, true, shape, Role.Button, onClick),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .let { if (info > 0.05f) it.shadow((16 * info).dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else it.lightShadow(shape, 4.dp) }
                    .clip(shape)
                    .hairBorder(edge, shape),
            ) {
                PosterImage(item, Modifier.fillMaxSize())
                // what the title is, at a glance: quality, sub / dub, episodes (they give way to the info panel)
                Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    qualityLabel(item.quality)?.let { Overlay(it) }
                    dubLabel(item)?.let { Overlay(it) }
                }
                item.score?.let { s ->
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Overlay("★ " + s.toString(10, 1)) }
                }
                episodeCount(item)?.let { n ->
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = if (item.watchedFraction() != null) 10.dp else 6.dp)) { Overlay(if (n == 1) "1 ep" else "$n eps") }
                }
                if (info > 0.01f) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = info }) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xB3000000))))
                        CardInfoPanel(item, width, subtitle, owned, saved, onPlay ?: onClick, onClick) {
                            flipped = !saved
                            runCatching { CardLibrary.setStatus(item, if (saved) com.lagradost.cloudstream3.ui.WatchType.NONE else com.lagradost.cloudstream3.ui.WatchType.PLANTOWATCH) }
                        }
                    }
                }
                item.watchedFraction()?.let { f ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                        ProgressBar(f, height = 3.dp, color = c.text)
                    }
                }
            }
            Box(Modifier.height(8.dp))
            // the title stays under the card; under the pointer it is a little brighter
            if (item.name.isNotBlank()) FText(item.name, style = Fluent.type.body, color = if (hovered) c.text else c.textSecondary, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
            val sub = subtitle ?: if (showType) typeLabel(item.type) else null
            if (sub != null) FText(sub, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
    // a title of an extension gets the library menu unless the page that shows it brings its own; a TMDB title belongs to no extension (no library entry)
    if (menu != null) ContextMenuArea(menu) { body() }
    else if (item !is com.lagradost.desktop.tmdb.TmdbCard && item.url.isNotBlank() && item.apiName.isNotBlank()) CatalogCardMenu(item) { body() }
    else body()
}

@Composable
private fun Overlay(text: String) {
    Box(Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        FText(text, style = Fluent.type.caption, color = Color.White, maxLines = 1, softWrap = false)
    }
}

/** "DUB", "SUB" or "SUB · DUB" for an anime card whose extension says which it has */
private fun dubLabel(item: SearchResponse): String? {
    val a = item as? com.lagradost.cloudstream3.AnimeSearchResponse ?: return null
    val set = a.dubStatus?.filter { it != com.lagradost.cloudstream3.DubStatus.None }?.toSet().orEmpty()
    val dub = com.lagradost.cloudstream3.DubStatus.Dubbed in set
    val sub = com.lagradost.cloudstream3.DubStatus.Subbed in set
    return when { dub && sub -> "SUB · DUB"; dub -> "DUB"; sub -> "SUB"; else -> null }
}

/** How many episodes the extension says a series has (the most of the sub and dub counts for an anime) */
private fun episodeCount(item: SearchResponse): Int? = when (item) {
    is com.lagradost.cloudstream3.AnimeSearchResponse -> item.episodes.values.maxOrNull()
    is com.lagradost.cloudstream3.TvSeriesSearchResponse -> item.episodes
    else -> null
}?.takeIf { it > 0 }

/**
 * What comes up on a poster under the pointer: the round buttons (play, add to the library, more), the title and the facts. The same
 * calls as the card's right-click menu ([CardLibrary]), so a title added here is the one the library and the title page show.
 */
@Composable
private fun CardInfoPanel(item: SearchResponse, width: Dp?, subtitle: String?, owned: Boolean, saved: Boolean, onPlay: () -> Unit, onOpen: () -> Unit, onToggle: () -> Unit) {
    val narrow = width != null && width < 150.dp
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (narrow) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SkinCircleButton(Icons.Play, "Play", onPlay, size = 32.dp, filled = true) }
            else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SkinCircleButton(Icons.Play, "Play", onPlay, size = 32.dp, filled = true)
                if (owned) SkinCircleButton(if (saved) Icons.Check else Icons.Add, if (saved) "Remove from your library" else "Add to your library", onToggle, size = 32.dp)
                Box(Modifier.weight(1f))
                SkinCircleButton(Icons.ChevronDown, "More info", onOpen, size = 32.dp)
            }
        }
    }
}

@Composable
fun PosterSkeleton(width: Dp, landscape: Boolean = false) {
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(FluentShapes.card)).shimmer())
        Box(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(FluentShapes.control)).shimmer())
        Box(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(FluentShapes.control)).shimmer())
    }
}

/** Section header with optional "See all" */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FText(title, Modifier.weight(1f), style = Fluent.type.subtitle, maxLines = 1)
        trailing?.invoke()
        if (onSeeAll != null) Button("See all", onSeeAll, kind = ButtonKind.Subtle, icon = null)
    }
}

/** Horizontal scroller with hover chevrons (and wheel + Shift/horizontal scroll through LazyRow) */
@Composable
fun <T> Shelf(
    items: List<T>,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    gutter: Dp = 24.dp,
    spacing: Dp = 12.dp,
    key: ((T) -> Any)? = null,
    state: LazyListState = rememberLazyListState(),
    content: @Composable (T) -> Unit,
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()
    // a repeated key (two cards with the same address from one source) throws while measuring and blanks the window
    val shown = androidx.compose.runtime.remember(items, key) {
        if (key == null) items else HashSet<Any>().let { seen -> items.filter { seen.add(key(it)) } }
    }
    // a list keeps the item it is showing in place: a title that comes in at the front (the film you have just watched in Continue watching) would
    // wait out of sight at the left of a row that was scrolled. When the first item changes the row goes back to its start.
    val firstKey = shown.firstOrNull()?.let { if (key != null) key(it) else it }
    val lastFirst = androidx.compose.runtime.remember { arrayOf(firstKey) }
    androidx.compose.runtime.LaunchedEffect(firstKey) {
        if (lastFirst[0] == firstKey) return@LaunchedEffect
        lastFirst[0] = firstKey
        if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) state.animateScrollToItem(0)
    }
    var width by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    // the row can be held and dragged with the mouse (a touch screen does that by itself, a mouse only had the wheel and the arrows); a short
    // movement is still a click on the card, and the row carries on a little after the button is let go
    val dragState = androidx.compose.foundation.gestures.rememberDraggableState { delta -> state.dispatchRawDelta(-delta) }
    val drag = Modifier.draggable(
        dragState, androidx.compose.foundation.gestures.Orientation.Horizontal,
        onDragStopped = { velocity ->
            scope.launch {
                var last = 0f
                androidx.compose.animation.core.AnimationState(0f, -velocity).animateDecay(androidx.compose.animation.core.exponentialDecay(frictionMultiplier = 1.4f)) {
                    val step = value - last
                    last = value
                    if ((step > 0f && !state.canScrollForward) || (step < 0f && !state.canScrollBackward)) cancelAnimation() else state.dispatchRawDelta(step)
                }
            }
        },
    )
    Box(modifier.fillMaxWidth().hoverable(source).onSizeChanged { width = it.width }) {
        LazyRow(
            // a row with no gutter (chips) is cut at its own edge: the cut end fades into the page instead of showing half a chip
            modifier = (if (gutter == 0.dp) Modifier.scrollEdgeFade(state) else Modifier).touchScroll(state, androidx.compose.foundation.gestures.Orientation.Horizontal).then(drag),
            state = state,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            // the first cards of a shelf arrive one after the other (fade and rise, once per card and run of the app); rows without a gutter are chips and stay still
            if (key != null) itemsIndexed(shown, key = { _, item -> key(item) }) { i, item -> ShelfCard(gutter != 0.dp, key(item), i) { content(item) } }
            else itemsIndexed(shown) { i, item -> ShelfCard(gutter != 0.dp, item as Any? ?: i, i) { content(item) } }
        }
        val canBack = state.canScrollBackward
        val canForward = state.canScrollForward
        // a special theme's arrows are tall strips over the row's edge (a row of chips, which has no gutter, keeps the round ones)
        val strips = Skins.spec != null && gutter != 0.dp
        if (hovered && canBack) {
            if (strips) SkinShelfArrow(Icons.ChevronLeft, Modifier.align(Alignment.CenterStart)) { scope.launch { state.animateScrollBy(-width * 0.8f) } }
            else ChevronButton(Icons.ChevronLeft, Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { scope.launch { state.animateScrollBy(-width * 0.8f) } }
        }
        if (hovered && canForward) {
            if (strips) SkinShelfArrow(Icons.ChevronRight, Modifier.align(Alignment.CenterEnd)) { scope.launch { state.animateScrollBy(width * 0.8f) } }
            else ChevronButton(Icons.ChevronRight, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { scope.launch { state.animateScrollBy(width * 0.8f) } }
        }
    }
}

@Composable
private fun ShelfCard(reveals: Boolean, key: Any, index: Int, content: @Composable () -> Unit) {
    if (!reveals || index > 12) content() else Box(Modifier.reveal("shelf/" + key, index * 45, rise = 18.dp)) { content() }
}

@Composable
fun ChevronButton(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier
            .size(36.dp)
            .background(if (hovered) c.flyout else c.flyout.copy(alpha = 0.9f), CircleShape)
            .hairBorder(c.strokeStrong.copy(alpha = 0.5f), CircleShape)
            .fluentClickable(source, true, CircleShape, Role.Button, onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, size = 14.dp, tint = c.text) }
}

/** Plural display name of a content type (filters, settings, setup) */
fun tvTypeName(t: TvType): String = when (t) {
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
    TvType.CustomMedia -> "Custom media"
    TvType.Audio -> "Audio"
    TvType.Podcast -> "Podcasts"
    TvType.Video -> "Videos"
}
