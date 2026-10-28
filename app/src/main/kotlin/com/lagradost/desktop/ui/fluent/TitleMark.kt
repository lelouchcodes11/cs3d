package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.lagradost.desktop.ui.components.RemoteImage
import kotlinx.coroutines.delay

/**
 * The title of a banner: its logo when there is one, else (or while the logo has not come after a moment, or never does) its name as text.
 * A logo that is still coming, or is a broken link, used to leave a blank above the facts, as if the title was missing.
 */
@Composable
fun TitleMark(
    logo: String?, headers: Map<String, String>?, title: String, logoHeight: Dp, logoMaxWidth: Dp,
    modifier: Modifier = Modifier, centered: Boolean = false, text: @Composable () -> Unit,
) {
    var loaded by remember(logo) { mutableStateOf(false) }
    var waited by remember(logo) { mutableStateOf(logo == null) }
    LaunchedEffect(logo) { if (logo != null) { delay(1400); waited = true } }
    Box(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        if (logo != null) RemoteImage(
            logo, headers, title, Modifier.height(logoHeight).widthIn(max = logoMaxWidth), ContentScale.Fit,
            alignment = if (centered) Alignment.Center else Alignment.CenterStart, onSize = { _, _ -> loaded = true },
        )
        if (!loaded && waited) text()
    }
}
