package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays

/** The keys of the player (see `PlayerContent.onKey`), for the "Keyboard shortcuts" window of the player (F1) and of Settings → About */
object Shortcuts {
    val player: List<Pair<String, String>> = listOf(
        "Space  ·  K" to "Play or pause",
        "←  /  →" to "Back or forward 5 s (Shift: 30 s)",
        "J  /  L" to "Back or forward 10 s",
        "Ctrl + ←  /  →" to "Previous or next episode (also P  /  N)",
        "↑  /  ↓  ·  mouse wheel" to "Volume",
        "M" to "Mute",
        "F  ·  F11  ·  double click" to "Full screen",
        "Esc" to "Leave full screen or picture in picture, close the episode list, go back",
        "I" to "Picture in picture",
        "S" to "Next subtitle track (or off)",
        "A" to "Next audio track",
        "Z" to "Picture size: fit, fill, zoom",
        "E" to "Episode list",
        "," to "Slower (0.25x steps)",
        "." to "Faster (0.25x steps)",
        "Home" to "Back to the start",
        "0 – 9" to "Jump to 0 % … 90 % of the video",
        "F1" to "This list",
    )

    fun show() {
        Overlays.show(
            Overlays.Dialog(title = "Keyboard shortcuts", close = "Close", width = 560.dp) {
                val c = Fluent.colors
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((keys, what) in player) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(210.dp)) {
                                Box(Modifier.background(c.control, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    FText(keys, style = Fluent.type.caption, maxLines = 1)
                                }
                            }
                            FText(what, color = c.textSecondary, style = Fluent.type.body)
                        }
                    }
                }
            },
        )
    }
}
