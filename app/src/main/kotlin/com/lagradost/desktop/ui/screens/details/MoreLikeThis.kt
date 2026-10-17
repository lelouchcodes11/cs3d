package com.lagradost.desktop.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.desktop.tmdb.TmdbCard
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.MenuEntry
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.RichSectionHeader
import com.lagradost.desktop.ui.fluent.reveal
import com.lagradost.desktop.ui.fluent.typeLabel

/**
 * The "More like this" tab of a title page: a heading that says what the list is, then the titles as a grid of posters, each with its year and
 * first genre under the name, arriving one after the other in a wave from the top left. It was a bare block of posters without a word on it.
 */
@Composable
internal fun MoreLikeThis(
    title: String,
    cards: List<SearchResponse>,
    pageWidth: Dp,
    gutter: Dp,
    open: (SearchResponse) -> Unit,
    menu: (SearchResponse) -> (() -> List<MenuEntry>),
) {
    val minWidth = Appearance.posterSize.width
    val room = pageWidth - gutter * 2
    val perRow = ((room + 16.dp) / (minWidth + 16.dp)).toInt().coerceAtLeast(2)
    // the posters share the width of the page: no ragged space at the right of the grid
    val w = ((room - 16.dp * (perRow - 1)) / perRow).coerceAtLeast(minWidth * 0.8f)
    val c = Fluent.colors
    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.reveal("more-head/$title")) {
            RichSectionHeader(if (title.isBlank()) "More like this" else "More like $title")
            FText(
                "${cards.size} titles that people who watch this also watch, in the order of how close they are. Opening one looks for it in your extensions.",
                style = Fluent.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = 2.dp),
            )
        }
        cards.chunked(perRow).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEachIndexed { i, card ->
                    // a wave: the row and the place in the row together decide when a poster comes
                    Box(Modifier.reveal("more/" + card.url, (r + i + 1).coerceAtMost(14) * 45)) { PosterCard(card, { open(card) }, w, subtitle = subtitleOf(card), menu = menu(card)) }
                }
            }
        }
    }
}

/** "2014 · Sci-Fi" for a title that TMDB knows, else the kind of title */
private fun subtitleOf(card: SearchResponse): String? =
    if (card is TmdbCard) listOfNotNull(card.year?.toString(), card.genres.firstOrNull()).joinToString(" · ").ifBlank { null } else typeLabel(card.type)
