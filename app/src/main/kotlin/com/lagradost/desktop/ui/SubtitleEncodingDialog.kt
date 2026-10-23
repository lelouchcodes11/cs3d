package com.lagradost.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.player.MpvPlayer
import com.lagradost.desktop.player.SubtitleEncoding
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/**
 * The phone's "Subtitle encoding" list: Automatic or the character set the subtitle files are read with, a check mark on the chosen one,
 * Apply and Cancel. Apply loads the subtitle that is on again with the new set (and keeps it for every video).
 */
fun showSubtitleEncodingDialog(onApplied: () -> Unit = {}) {
    val entries = SubtitleEncoding.entries()
    var picked by mutableStateOf(SubtitleEncoding.value())
    Overlays.show(
        Overlays.Dialog(
            title = "Subtitle encoding",
            primary = "Apply",
            close = "Cancel",
            width = 520.dp,
            onPrimary = {
                if (picked != SubtitleEncoding.value()) {
                    SubtitleEncoding.set(picked)
                    com.lagradost.desktop.core.ioTask { MpvPlayer.active?.subtitleEncodingChanged() }
                    onApplied()
                }
            },
        ) {
            val c = Fluent.colors
            Column {
                FText(
                    "Automatic is right for most files. When the letters of a subtitle show as squares or strange signs, pick the set it was made in (for an old Japanese file Shift JIS, for a Russian one Windows-1251 ...).",
                    style = Fluent.type.caption, color = c.textSecondary, maxLines = 4,
                )
                Box(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(entries) { (name, value) ->
                        val source = rememberInteraction()
                        val hovered by source.collectIsHoveredAsState()
                        val shape = RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.row)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(if (hovered) c.subtleHover else Color.Transparent, shape)
                                .fluentClickable(source, true, shape, Role.RadioButton) { picked = value }.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (picked == value) Icon(Icons.Check, size = 16.dp, tint = c.accentText)
                            }
                            FText(name, Modifier.weight(1f), color = if (picked == value) c.text else c.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        },
    )
}
