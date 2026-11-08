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
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.ProgressRing
import kotlinx.coroutines.delay

/**
 * A small chip in the bottom right corner while a hidden page works on a site's Cloudflare check: "Cloudflare check · <site>", a ring and a stop
 * button. It was a bar in the middle of the window after 5 s, which the owner found in the way every time a source had a check ("that pausing
 * popup"): it now waits 10 s (the checks that pass by themselves are done by then), sits out of the way, and never shows over the player or the
 * setup. Stop ends the page, the extension's request goes on without the clearance, and the site is left alone for two minutes.
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
    val route = com.lagradost.desktop.core.Navigator.current.route
    val quietHere = route is com.lagradost.desktop.core.Route.Player || route is com.lagradost.desktop.core.Route.Setup
    val waiting = if (quietHere) emptyList() else solves.toList().filter { now - it.startedAt > 10_000 }
    // the text stays while the chip fades away
    val hosts = remember { arrayOf("") }
    if (waiting.isNotEmpty()) hosts[0] = waiting.map { it.host.removePrefix("www.") }.distinct().joinToString(", ")
    Box(Modifier.fillMaxSize().padding(end = 24.dp, bottom = 24.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            waiting.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .widthIn(max = 460.dp)
                    .shadow(12.dp, shape, clip = false)
                    .background(c.flyout, shape)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(size = 14.dp, strokeWidth = 2.dp)
                Box(Modifier.width(10.dp))
                FText(com.lagradost.desktop.i18n.Tr.f("Passing the Cloudflare check of {0}…", hosts[0]), style = Fluent.type.caption, color = c.textSecondary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.width(6.dp))
                com.lagradost.desktop.ui.fluent.IconButton(com.lagradost.desktop.ui.fluent.Icons.Close, { CloudflareGate.cancelSolves() }, tooltip = "Stop", size = 26.dp, iconSize = 11.dp)
            }
        }
    }
}
