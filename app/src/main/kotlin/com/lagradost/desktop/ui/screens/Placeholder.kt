package com.lagradost.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon

/** A page of the native UI that is not built yet (docs/PLAN.md lists the work package) */
@Composable
fun ComingSoonPage(title: String, glyph: String) {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(glyph, size = 40.dp, tint = c.textTertiary)
            FText(title, style = Fluent.type.title)
            FText("This page is being rebuilt as a native Windows page.", color = c.textSecondary)
        }
    }
}
