package com.lagradost.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * The app's one loading screen. The native splash (only the logo, see src/main/splash) shows while Java starts; the window then
 * opens with the same logo in the same place and a thin line under it, and stays like that until the app has settled: the
 * extensions are loaded, their updates are checked and Home has its content (never longer than
 * [MAX_MS]). The app is composed underneath the whole time, so it is ready when it is revealed.
 */
object Startup {
    /** The app is on screen (dialogs that wait for the start, such as the profile picker, come after this) */
    val revealed = CompletableDeferred<Unit>()

    /** What the start is waiting for, shown under the line */
    var stage by mutableStateOf("")

    const val MAX_MS = 3_000L

    /** The extension update (a background job that can take a minute) is waited for this long, no longer */
    const val ONLINE_WAIT_MS = 1_200L
    const val LOGO_DP = 88
}

private fun homeSettled(): Boolean = runCatching {
    val vm = com.lagradost.desktop.core.AppVms.get<com.lagradost.cloudstream3.ui.home.HomeViewModel>()
    val api = vm.apiName.value
    // no extension chosen for Home: nothing to wait for
    if (api.isNullOrBlank() || api == "NONE") return@runCatching true
    val page = vm.page.value
    page is com.lagradost.cloudstream3.mvvm.Resource.Success || page is com.lagradost.cloudstream3.mvvm.Resource.Failure
}.getOrDefault(true)

@Composable
fun StartupReveal(content: @Composable () -> Unit) {
    if (Startup.revealed.isCompleted) {
        content()
        return
    }
    val c = Fluent.colors
    val logo = remember { runCatching { BitmapPainter(Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")!!.use { loadImageBitmap(it) }) }.getOrNull() }
    val leave = remember { Animatable(0f) } // 0 -> 1: the loader fades away, the app settles in
    var gone by remember { mutableStateOf(false) }
    var showLine by remember { mutableStateOf(false) }
    // the last time a frame took long (the "hand" of a busy start): the app is shown once the window has been smooth for a moment
    var lastJank by remember { mutableStateOf(System.currentTimeMillis()) }
    var frames by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var previous = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            frames++
            if ((now - previous) / 1_000_000 > 90) lastJank = System.currentTimeMillis()
            previous = now
        }
    }
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        delay(350)
        showLine = true
        while (System.currentTimeMillis() - started < Startup.MAX_MS) {
            val pm = com.lagradost.cloudstream3.plugins.PluginManager
            val setup = com.lagradost.desktop.core.Navigator.current.route is com.lagradost.desktop.core.Route.Setup
            val now = System.currentTimeMillis()
            val stage = when {
                setup -> null
                !pm.loadedLocalPlugins -> "Loading extensions"
                !pm.loadedOnlinePlugins && now - started < Startup.ONLINE_WAIT_MS -> "Updating extensions"
                // smooth for 0.6 s (and a few frames drawn): nothing heavy is running on the UI thread any more
                now - lastJank < 600 || frames < 8 -> "Getting ready"
                else -> null
            }
            if (stage == null) break
            if (stage != Startup.stage) android.util.Log.i("Startup", "waiting for: $stage (${System.currentTimeMillis() - started} ms)")
            Startup.stage = stage
            delay(100)
        }
        android.util.Log.i("Startup", "settled after ${System.currentTimeMillis() - started} ms (local=${com.lagradost.cloudstream3.plugins.PluginManager.loadedLocalPlugins} online=${com.lagradost.cloudstream3.plugins.PluginManager.loadedOnlinePlugins} home=${homeSettled()})")
        Startup.stage = ""
        // a moment for Home to lay out its content under the loader
        delay(300)
        leave.animateTo(1f, tween(FluentMotion.ms(480).coerceAtLeast(1), easing = FluentMotion.standard))
        gone = true
        Startup.revealed.complete(Unit)
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val l = leave.value
                alpha = l
                val s = 0.985f + 0.015f * l
                scaleX = s; scaleY = s
            },
        ) { content() }
        if (!gone) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - leave.value }.background(c.bg), contentAlignment = Alignment.Center) {
                val accent = c.accent
                val track = c.text.copy(alpha = 0.10f)
                val motion = rememberInfiniteTransition()
                val phase by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // the logo sits exactly where the native splash showed it
                    if (logo != null) Image(logo, "CloudStream", Modifier.size(Startup.LOGO_DP.dp))
                    Box(Modifier.height(28.dp))
                    // one slim indeterminate line
                    Box(
                        Modifier.width(132.dp).height(3.dp)
                            .graphicsLayer { alpha = if (showLine) 1f else 0f }
                            .drawBehind {
                                val r = CornerRadius(size.height / 2)
                                drawRoundRect(track, cornerRadius = r)
                                val w = size.width * 0.32f
                                val x = (size.width + w) * FluentMotion.standard.transform(phase) - w
                                val left = x.coerceAtLeast(0f)
                                val right = (x + w).coerceAtMost(size.width)
                                if (right > left) drawRoundRect(accent, Offset(left, 0f), Size(right - left, size.height), r)
                            },
                    )
                    Box(Modifier.height(12.dp))
                    FText(Startup.stage, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = if (showLine) 1f else 0f })
                }
            }
        }
    }
}
