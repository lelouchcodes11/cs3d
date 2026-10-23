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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        drawRect(c.card)
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, if (c.dark) Color(0x14FFFFFF) else Color(0x14000000), Color.Transparent),
                startX = x - size.width * 0.3f, endX = x + size.width * 0.3f,
            ),
        )
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
    val pos = (this as? DataStoreHelper.ResumeWatchingResult)?.watchPos ?: return null
    if (pos.duration <= 0) return null
    return (pos.position.toFloat() / pos.duration.toFloat()).coerceIn(0f, 1f)
}

@Composable
fun PosterImage(item: SearchResponse, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val c = Fluent.colors
    Box(modifier.background(c.card), contentAlignment = Alignment.Center) {
        Icon(Icons.Video, size = 28.dp, tint = c.textDisabled)
        RemoteImage(item.posterUrl, item.posterHeaders, item.name, Modifier.fillMaxSize(), scale)
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
    val hovered by source.collectIsHoveredAsState()
    val zoomOn = Appearance.hoverZoom
    val pressed by source.collectIsPressedAsState()
    // the card rises on a spring when the pointer comes (a little overshoot) and gives way under a press
    val liftSpec = FluentMotion.springSoft<Float>()
    val lift by animateFloatAsState(if (pressed) 0.975f else if (hovered && zoomOn) 1.035f else 1f, liftSpec, label = "lift")
    val glow by animateFloatAsState(if (hovered) 1f else 0f, FluentMotion.tweenIn(220))
    val shape = RoundedCornerShape(FluentShapes.card)
    // a top-lit hairline gives the poster an edge (brighter under the pointer)
    val edge = depthEdge(if (hovered) 2.1f else 1f)
    val body = @Composable {
        Column(
            modifier
                .let { if (width != null) it.width(width) else it.fillMaxWidth() }
                // fluentClickable adds the hover tracking and the focus ring itself (they were added a second time here: two more nodes and a second focus collector per card)
                .fluentClickable(source, true, shape, Role.Button, onClick),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .clip(shape)
                    .hairBorder(edge, shape),
            ) {
                PosterImage(item, Modifier.fillMaxSize())
                // hover: a flat dim and a quiet play button (no gradient, shadow or accent)
                if (glow > 0.01f) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = glow }.background(Color(0x59000000)), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(44.dp).background(Color(0x33FFFFFF), CircleShape).hairBorder(Color(0x66FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Play, size = 16.dp, tint = Color.White)
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    qualityLabel(item.quality)?.let { Overlay(it) }
                }
                item.score?.let { s ->
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Overlay("★ " + s.toString(10, 1)) }
                }
                item.watchedFraction()?.let { f ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                        ProgressBar(f, height = 3.dp, color = c.accent)
                    }
                }
            }
            Box(Modifier.height(8.dp))
            // the title is quiet until the pointer is on the card
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
