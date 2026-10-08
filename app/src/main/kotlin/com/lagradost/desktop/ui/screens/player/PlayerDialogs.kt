package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.desktop.ui.fluent.Overlays

// The player's choices (source, quality, audio, subtitles, speed ...) are in its menu (PlayerMenu.kt); the subtitle style editor is
// the one dialog: it needs the room of a preview and many options.

/** Font, size, colours, edge, background and position of the subtitles, with the same preview and options as the settings page */
fun openSubtitleStyleDialog(s: PlayerSession) {
    val opened = com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.subtitleStyleState.value
    val wasPlaying = s.status == CSPlayerLoading.IsPlaying
    s.pause()
    Overlays.show(
        Overlays.Dialog(title = "Subtitle style", close = "Done", width = 680.dp, onClose = { if (wasPlaying) s.play() }) {
            Box(Modifier.fillMaxWidth().height(520.dp)) {
                val scroll = androidx.compose.foundation.rememberScrollState()
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 12.dp)) {
                    com.lagradost.desktop.ui.screens.settings.SubtitleStyleEditor(initial = opened)
                }
            }
        },
    )
}
