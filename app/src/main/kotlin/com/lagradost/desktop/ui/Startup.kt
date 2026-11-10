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

    /** The main pages are being prepared off screen under the loader (see ScreenWarmup in AppShell): the loader may stay this much longer for it */
    @Volatile
    var warming = false
    const val WARM_MS = 3_000L

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
    val logo = remember { runCatching { BitmapPainter(Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")!!.use { loadImageBitmap(it) }, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium) }.getOrNull() }
    val leave = remember { Animatable(0f) } // 0 -> 1: the loader fades away, the app settles in
    var gone by remember { mutableStateOf(false) }
    var showLine by remember { mutableStateOf(false) }
    // the line and the stage text fade in (they popped in at once)
    val lineAlpha by androidx.compose.animation.core.animateFloatAsState(if (showLine) 1f else 0f, tween(240, easing = LinearEasing))
    // the last time a frame took long (the "hand" of a busy start): the app is shown once the window has been smooth for a moment
    var lastJank by remember { mutableStateOf(System.currentTimeMillis()) }
    var frames by remember { mutableStateOf(0) }
    // the loader's own pauses (a frozen line is what a slow start looks like): logged when the app is revealed
    val pauses = remember { StringBuilder() }
    var pauseCount by remember { mutableStateOf(0) }
    // System.currentTimeMillis() of the last frame over 50 ms (read by the reveal below; not state: nothing redraws for it)
    val lastPauseAt = remember { longArrayOf(System.currentTimeMillis()) }
    // the app is drawn under the (opaque) loader from the moment the start has settled: see the reveal
    var drawApp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val first = androidx.compose.runtime.withFrameNanos { it }
        var previous = first
        var lastPause = first
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            frames++
            val gapMs = (now - previous) / 1_000_000
            if (gapMs > 90) lastJank = System.currentTimeMillis()
            if (gapMs > 50) {
                lastPause = now
                lastPauseAt[0] = System.currentTimeMillis()
                pauseCount++
                if (pauses.length < 400) pauses.append((now - first) / 1_000_000).append(':').append(gapMs).append("ms(").append(Startup.stage.ifEmpty { "-" }.take(9)).append(if (showLine) ",line" else "").append(if (leave.value > 0f) ",fade" else if (drawApp) ",draw" else "").append(") ")
            }
            // The moving line only when the start is slow (1.2 s) and the window has been calm for a moment: the first ~0.8 s after the window opens are its own first draws
            // and the app being built under the loader (pauses of 0.1-0.5 s, measured), and a line that freezes there is what looks like a hang.
            // Until then the logo stands alone, as in the native splash before it; a quick start never shows the line.
            if (!showLine && now - first >= 1_200_000_000L && now - lastPause >= 300_000_000L) showLine = true
            previous = now
        }
    }
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        delay(350)
        while (System.currentTimeMillis() - started < Startup.MAX_MS + (if (Startup.warming) Startup.WARM_MS else 0L)) {
            val pm = com.lagradost.cloudstream3.plugins.PluginManager
            val setup = com.lagradost.desktop.core.Navigator.current.route is com.lagradost.desktop.core.Route.Setup
            val now = System.currentTimeMillis()
            val stage = when {
                setup -> null
                !pm.loadedLocalPlugins -> "Loading extensions"
                !pm.loadedOnlinePlugins && now - started < Startup.ONLINE_WAIT_MS -> "Updating extensions"
                // the pages people open first are composed under the loader, so their first visit does not stall
                Startup.warming -> "Getting ready"
                // smooth for 0.5 s (and a few frames drawn): nothing heavy is running on the UI thread any more
                // (not on a weak PC: its frames may never be calm, and a loader that stays for seconds looks like a hang; the app shows itself instead)
                !com.lagradost.desktop.ui.fluent.Appearance.light && (now - lastJank < 500 || frames < 8) -> "Getting ready"
                else -> null
            }
            if (stage == null) break
            if (stage != Startup.stage) android.util.Log.i("Startup", "waiting for: $stage (${System.currentTimeMillis() - started} ms)")
            Startup.stage = stage
            delay(100)
        }
        android.util.Log.i("Startup", "settled after ${System.currentTimeMillis() - started} ms (local=${com.lagradost.cloudstream3.plugins.PluginManager.loadedLocalPlugins} online=${com.lagradost.cloudstream3.plugins.PluginManager.loadedOnlinePlugins} home=${homeSettled()})")
        Startup.stage = ""
        // The app's first draw happens under the still loader, not in the fade: Home's pictures and shapes going to the graphics card for the
        // first time paused the fade 3-5 times (60-300 ms, measured with fast extensions). The loader waits until those frames are done (0.7 s at most).
        drawApp = true
        delay(180)
        val drawnBy = System.currentTimeMillis() + 700
        while (System.currentTimeMillis() < drawnBy && System.currentTimeMillis() - lastPauseAt[0] < 200) delay(30)
        leave.animateTo(1f, tween(FluentMotion.ms(360).coerceAtLeast(1), easing = FluentMotion.standard))
        gone = true
        android.util.Log.i("Startup", "loader: $frames frames, $pauseCount pauses over 50 ms: $pauses")
        Startup.revealed.complete(Unit)
    }
    Box(Modifier.fillMaxSize()) {
        // The app is not drawn while the loader is up (only from the moment the start has settled, under the opaque loader, for its first draw); when the reveal begins it is drawn as it is (no alpha, no scale: a whole-window layer that
        // fades in and grows a little was two off-screen passes per frame and soft text, which a weak graphics chip showed as stutter and flicker)
        // and the loader, a plain colour with the logo, fades away over it
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (drawApp || leave.value > 0f) 1f else 0f }) { content() }
        if (!gone) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - leave.value; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }.background(c.bg),
                contentAlignment = Alignment.Center,
            ) {
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
                            .graphicsLayer { alpha = lineAlpha }
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
                    FText(Startup.stage, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = lineAlpha })
                }
            }
        }
    }
}
