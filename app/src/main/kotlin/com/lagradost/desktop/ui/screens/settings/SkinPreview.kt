package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.ThemePreset

/**
 * A miniature of Netflix's interface for the Settings gallery: the bar (the name in red, the links), the billboard with its two buttons and a row of cards, drawn with
 * the theme's colours and the shapes of the interface.
 */
@Composable
internal fun SkinPreview(p: ThemePreset) {
    val accent = p.accent ?: Color.White
    val ink = p.tint
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Block(24.dp, 5.dp, accent, 1.dp)
            Box(Modifier.size(2.dp))
            repeat(3) { i -> Block(9.dp, 3.dp, if (i == 0) Color.White.copy(alpha = 0.9f) else ink.copy(alpha = 0.4f), 1.dp) }
            Box(Modifier.weight(1f))
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.5.dp)).background(ink.copy(alpha = 0.45f)))
        }
        // the billboard: the picture's colour rising out of the page, the text low at the left
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.5f), p.bg))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Block(28.dp, 4.dp, Color.White.copy(alpha = 0.9f), 1.dp)
                Block(40.dp, 2.dp, ink.copy(alpha = 0.45f), 1.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Block(18.dp, 7.dp, Color.White, 1.dp)
                    Block(18.dp, 7.dp, Color(0x886D6D6E), 1.dp)
                }
            }
        }
        // a row of cards climbing into the billboard's foot
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(6) { Box(Modifier.size(14.dp, 20.dp).clip(RoundedCornerShape(1.5.dp)).background(Brush.verticalGradient(listOf(p.flyout, p.layer)))) }
        }
    }
}

@Composable
private fun Block(w: Dp, h: Dp, color: Color, radius: Dp) = Box(Modifier.size(w, h).clip(RoundedCornerShape(radius)).background(color))
