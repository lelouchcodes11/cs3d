package com.lagradost.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.tmdb.Tmdb
import com.lagradost.desktop.tmdb.TmdbCard
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.Toasts

/**
 * "Wrong match?" on a title page: when the cast, story or pictures that TMDB gave belong to another film (a remake, a namesake), the viewer
 * searches TMDB and picks the right entry, or says there is none. The choice is kept for the title and used everywhere its TMDB facts show.
 */
fun showMatchDialog(title: String, year: Int?, isMovie: Boolean) {
    val chosen = Tmdb.chosenMatch(title, isMovie)
    Overlays.show(
        Overlays.Dialog(
            title = "Choose the right title",
            secondary = if (chosen != null) "Find it automatically" else "No match",
            close = "Cancel",
            width = 520.dp,
            onSecondary = {
                if (chosen != null) Tmdb.chooseMatch(title, isMovie, null)
                else { Tmdb.chooseMatch(title, isMovie, null, none = true); Toasts.show("TMDB details are hidden for this title", false) }
            },
        ) { dismiss -> MatchBody(title, year, isMovie, dismiss) },
    )
}

@Composable
private fun MatchBody(title: String, year: Int?, isMovie: Boolean, dismiss: () -> Unit) {
    val c = Fluent.colors
    var query by remember { mutableStateOf(listOfNotNull(title, year?.toString()).joinToString(" ")) }
    var asked by remember { mutableStateOf(query) }
    var results by remember { mutableStateOf<List<TmdbCard>?>(null) }
    LaunchedEffect(asked) {
        results = null
        results = runCatching { Tmdb.searchCandidates(asked) }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextBox(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Title and year", leadingIcon = Icons.Search, onSubmit = { asked = query })
        val list = results
        when {
            list == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { ProgressRing(size = 24.dp) }
            list.isEmpty() -> FText("Nothing found. Try another spelling or leave out the year.", color = c.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(list, key = { it.url }) { card ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .fluentClickable(remember { MutableInteractionSource() }) {
                                Tmdb.chooseMatch(title, isMovie, card)
                                Toasts.show("Details now come from “${card.name}”", false)
                                dismiss()
                            }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteImage(card.posterUrl, modifier = Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(c.subtleHover))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            FText(card.name, maxLines = 1)
                            FText(listOfNotNull(card.year?.toString(), if (card.isMovie) "Film" else "Series").joinToString(" · "), color = c.textSecondary, style = Fluent.type.caption, maxLines = 1)
                            card.overview?.let { FText(it, color = c.textTertiary, style = Fluent.type.caption, maxLines = 2) }
                        }
                    }
                }
            }
        }
    }
}
