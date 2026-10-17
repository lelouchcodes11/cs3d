package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.desktop.player.ScreenText
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import kotlinx.coroutines.delay

/**
 * The translated writing of the picture (T): every piece where the original stands on the picture, in a dark box so that it reads on any
 * background; a pill at the top says what it is. Boxes do not take clicks (the picture below still plays and pauses); the problem card does.
 */
@Composable
internal fun ScreenTextLayer(s: PlayerSession) {
    when (val st = ScreenText.state) {
        ScreenText.State.Idle -> {}
        is ScreenText.State.Working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.padding(top = 56.dp).clip(RoundedCornerShape(50)).background(PlayerSurface, RoundedCornerShape(50)).border(1.dp, PlayerSurfaceBorder, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProgressRing(size = 16.dp, strokeWidth = 2.dp, color = Color.White)
                FText(st.step + "…", color = Color.White, maxLines = 1, softWrap = false)
            }
        }
        is ScreenText.State.Showing -> Showing(st)
        is ScreenText.State.Problem -> Problem(st)
    }
}

@Composable
private fun Showing(st: ScreenText.State.Showing) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pictureLeft = maxWidth * st.picture.l
        val pictureTop = maxHeight * st.picture.t
        val pictureW = maxWidth * (st.picture.r - st.picture.l)
        val pictureH = maxHeight * (st.picture.b - st.picture.t)
        for (b in st.blocks) {
            // the size of the original letters (one line of the piece), a little smaller: the translation is longer
            val lineH = pictureH * (b.h / b.lines)
            val fontSize = (lineH.value * 0.62f).coerceIn(11f, 30f)
            val boxW = (pictureW * b.w * 1.3f).coerceIn(150.dp, pictureW - 12.dp)
            val x = (pictureLeft + pictureW * b.x).coerceIn(pictureLeft + 6.dp, (pictureLeft + pictureW - boxW - 6.dp).coerceAtLeast(pictureLeft + 6.dp))
            val y = (pictureTop + pictureH * b.y).coerceIn(pictureTop + 4.dp, (pictureTop + pictureH - 30.dp).coerceAtLeast(pictureTop + 4.dp))
            val shape = RoundedCornerShape(6.dp)
            Box(
                Modifier.offset(x, y).widthIn(min = (pictureW * b.w).coerceAtMost(boxW), max = boxW)
                    .background(Color(0xEB0D0D10), shape).border(1.dp, Color(0x33FFFFFF), shape).padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                FText(b.text, style = Fluent.type.body.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.25f).sp), color = Color.White)
            }
        }
        // what it is, and how to put it away
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.padding(top = 18.dp).clip(RoundedCornerShape(50)).background(PlayerSurface, RoundedCornerShape(50)).border(1.dp, PlayerSurfaceBorder, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Language, size = 14.dp, tint = Color.White)
                FText("${st.blocks.size} translated · ${ScreenText.sources.firstOrNull { it.second == st.from }?.first?.substringBefore(" (") ?: "?"} → ${ScreenText.targets.firstOrNull { it.second == st.to }?.first?.substringBefore(" (") ?: st.to} · T or play to put away", color = Color(0xB3FFFFFF), style = Fluent.type.caption, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun Problem(st: ScreenText.State.Problem) {
    if (st.autoClose) LaunchedEffect(st) { delay(3500); if (ScreenText.state === st) ScreenText.clear() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            Modifier.widthIn(max = 460.dp).clip(shape).background(PlayerSurface, shape).border(1.dp, PlayerSurfaceBorder, shape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FText(st.title, style = Fluent.type.bodyStrong, color = Color.White)
            FText(st.detail, style = Fluent.type.caption, color = Color(0xB3FFFFFF), maxLines = 12)
            if (!st.autoClose) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.openLanguages) PillButton("Open Windows language settings") {
                        runCatching { ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", "ms-settings:regionlanguage").start() }
                    }
                    PillButton("Close") { ScreenText.clear() }
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape).background(if (hovered) Color(0x33FFFFFF) else Color(0x1FFFFFFF), shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FText(text, color = Color.White, maxLines = 1, softWrap = false)
    }
}
