package com.lagradost.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.runtime.web.CloudflareGate
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.hairBorder
import kotlinx.coroutines.delay

/**
 * "Passing a Cloudflare check for <site>…" with a Cancel button, at the bottom of the window while a hidden page works on a site's check (the
 * request of an extension waits for it, up to a minute). It shows after a moment, so the checks that take a second never flash it. Cancel stops
 * the page, the extension's request goes on without the clearance, and the site is left alone for two minutes.
 */
@Composable
fun CloudflareSolveBar() {
    val c = Fluent.colors
    val solves = CloudflareGate.solves
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(solves.size) {
        while (solves.isNotEmpty()) {
            now = System.currentTimeMillis()
            delay(400)
        }
    }
    val waiting = solves.toList().filter { now - it.startedAt > 1500 }
    // the text stays while the bar slides away
    val hosts = remember { arrayOf("") }
    if (waiting.isNotEmpty()) hosts[0] = waiting.map { it.host }.distinct().joinToString(", ")
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            waiting.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val shape = RoundedCornerShape(FluentShapes.card)
            Row(
                Modifier
                    .widthIn(max = 560.dp)
                    .shadow(18.dp, shape, clip = false)
                    .background(c.flyout, shape)
                    .hairBorder(c.strokeStrong.copy(alpha = 0.4f), shape)
                    .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(size = 18.dp, strokeWidth = 2.dp)
                Box(Modifier.width(12.dp))
                FText("Passing the Cloudflare check of ${hosts[0]}…", maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.width(14.dp))
                Button("Cancel", { CloudflareGate.cancelSolves() })
            }
        }
    }
}
