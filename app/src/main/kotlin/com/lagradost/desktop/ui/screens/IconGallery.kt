package com.lagradost.desktop.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon

/** Dev page (/nav?route=icons&q=E700): the glyphs of Segoe Fluent Icons from a start code point, with their hex */
@Composable
fun IconGalleryPage(startHex: String) {
    val start = startHex.toIntOrNull(16) ?: 0xE700
    val codes = (start until start + 160).toList()
    LazyVerticalGrid(
        GridCells.Adaptive(76.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 16.dp),
    ) {
        items(codes) { cp ->
            Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(String(Character.toChars(cp)), size = 28.dp)
                FText("%04X".format(cp), style = Fluent.type.caption, color = Fluent.colors.textSecondary)
            }
        }
    }
}
