package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.CustomDecoder
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitleFont
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.applyStyleEvent
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.defaultSubtitleStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.saveStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.subtitleStyleState
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesScreen
import com.lagradost.desktop.player.SubtitleStyler
import com.lagradost.desktop.ui.LegacyContent
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.mihon.presentation.settings.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The subtitle look (font, size, colours, edge, background, position): a live preview and the engine's own
 * preference list, so keys and defaults stay the ones of upstream. Changes are saved at once and sent to the
 * playing video, which is also where they show up.
 */
@Composable
fun SubtitleStyleEditor(modifier: Modifier = Modifier, initial: SaveCaptionStyle = remember { subtitleStyleState.value }) {
    // [initial] is what it was when this page or dialog opened: "Undo changes" goes back to it
    LegacyContent {
        val context = LocalContext.current
        var style by subtitleStyleState
        // saved when it changes, sent to mpv a moment after the last change (a slider drag is many changes)
        LaunchedEffect(style) {
            context.saveStyle(style)
            delay(100)
            withContext(Dispatchers.IO) { applyStyleEvent.invoke(style) }
        }

        // what mpv cannot do is not offered
        val hidden = setOf(
            stringResource(R.string.uppercase_all_subtitles), stringResource(R.string.background_radius),
            stringResource(R.string.subs_window_color), stringResource(R.string.subtitles_remove_bloat),
        )
        val prefs = SubtitlesScreen.getPreferences().mapNotNull { p ->
            when (p) {
                is Preference.PreferenceGroup -> p.copy(preferenceItems = p.preferenceItems.filter { it.title !in hidden }).takeIf { it.preferenceItems.isNotEmpty() }
                is Preference.PreferenceItem.CustomPreference -> null // the Android preview, replaced by SubtitlePreview
                else -> p
            }
        }

        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubtitlePreview(style)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button("Undo changes", { style = initial }, kind = ButtonKind.Subtle, icon = Icons.Back, enabled = style != initial)
                Button("Reset to default", { style = defaultSubtitleStyle }, kind = ButtonKind.Subtle, icon = Icons.Refresh, enabled = style != defaultSubtitleStyle)
            }
            FluentPreferenceList(prefs)
        }
    }
}

/** A sample line over a dark picture, drawn the way the style will look in the player */
@Composable
fun SubtitlePreview(style: SaveCaptionStyle, modifier: Modifier = Modifier) {
    val alignment = style.alignment ?: CustomDecoder.SSA_ALIGNMENT_BOTTOM_CENTER
    val vertical = when (alignment) { in 7..9 -> Alignment.Top; in 4..6 -> Alignment.CenterVertically; else -> Alignment.Bottom }
    val horizontal = when (alignment) { 1, 4, 7 -> Alignment.Start; 3, 6, 9 -> Alignment.End; else -> Alignment.CenterHorizontally }
    Box(
        modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B3A55), Color(0xFF6B5B73), Color(0xFFC08A60)))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp).padding(bottom = (style.elevation / 6).dp),
            verticalArrangement = when (vertical) { Alignment.Top -> Arrangement.Top; Alignment.Bottom -> Arrangement.Bottom; else -> Arrangement.Center },
            horizontalAlignment = horizontal,
        ) {
            val text = "Subtitles look like this.\nSecond line of text."
            val family = remember(style.font, style.typefaceFilePath) { previewFont(style) }
            val size = ((style.fixedTextSize ?: com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE) * 0.62f).sp
            val align = when (horizontal) { Alignment.Start -> TextAlign.Start; Alignment.End -> TextAlign.End; else -> TextAlign.Center }
            val base = TextStyle(
                fontSize = size, fontFamily = family, fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            )
            val edge = Color(style.edgeColor)
            val boxed = (style.backgroundColor ushr 24) != 0
            Box(if (boxed) Modifier.background(Color(style.backgroundColor), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier) {
                when (style.edgeType) {
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE -> {
                        val width = (1.5f + (style.edgeSize ?: 0f) / 10f) * 2f
                        FText(text, textAlign = align, style = base.copy(color = edge, drawStyle = Stroke(width = width, join = androidx.compose.ui.graphics.StrokeJoin.Round)), color = edge)
                        FText(text, textAlign = align, style = base.copy(color = Color(style.foregroundColor)), color = Color(style.foregroundColor))
                    }
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, CaptionStyleCompat.EDGE_TYPE_RAISED, CaptionStyleCompat.EDGE_TYPE_DEPRESSED ->
                        FText(text, textAlign = align, style = base.copy(color = Color(style.foregroundColor), shadow = Shadow(edge, Offset(2f, 2f), 2f)), color = Color(style.foregroundColor))
                    else -> FText(text, textAlign = align, style = base.copy(color = Color(style.foregroundColor)), color = Color(style.foregroundColor))
                }
            }
        }
    }
}

private fun previewFont(style: SaveCaptionStyle): FontFamily? = runCatching {
    SubtitleStyler.prepareFonts()
    val file = when {
        style.font == SubtitleFont.Custom -> style.typefaceFilePath?.let { java.io.File(it) }
        style.font != null -> SubtitleStyler.fontsDir.listFiles()?.firstOrNull { f -> f.nameWithoutExtension == com.lagradost.desktop.runtime.AndroidRuntime.context.resources.getResourceEntryName(style.font.resource) }
        else -> null
    } ?: return@runCatching null
    FontFamily(androidx.compose.ui.text.platform.Font(file))
}.getOrNull()
