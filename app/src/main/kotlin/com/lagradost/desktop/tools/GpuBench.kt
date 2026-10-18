package com.lagradost.desktop.tools

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.Score
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.Shelf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.io.File

/**
 * Dev tool: what a new row of posters costs the GPU the first time it is drawn. /gpubench?mode=row&n=12&reps=3 puts n fresh posters (new files, so
 * new textures) in a real Shelf of PosterCards over the window for a moment per repetition and reports the frame intervals while they appear.
 * mode=bare draws the same pictures as plain Image composables (the upload cost without any card around them).
 */
object GpuBench {
    private class Item(
        override val name: String,
        override val url: String,
        override var posterUrl: String?,
    ) : SearchResponse {
        override val apiName: String = "bench"
        override var type: TvType? = TvType.Movie
        override var posterHeaders: Map<String, String>? = null
        override var id: Int? = null
        override var quality: SearchQuality? = null
        override var score: Score? = null
    }

    class Trial(val mode: String, val cards: List<SearchResponse>, val files: List<File>, val round: Int)

    var trial by mutableStateOf<Trial?>(null)
    @Volatile var holdMs = 0L
    val commands = Channel<Pair<Trial, CompletableDeferred<List<Long>>>>(Channel.RENDEZVOUS)
    private val dir = File(System.getProperty("java.io.tmpdir"), "cs-gpubench").also { it.mkdirs() }
    private var counter = 0

    /** n JPEG posters (500 x 750) that differ from every earlier one */
    private fun makeFiles(n: Int): List<File> {
        val round = ++counter
        return List(n) { i ->
            val f = File(dir, "p${round}_$i.jpg")
            val img = java.awt.image.BufferedImage(500, 750, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            val rnd = java.util.Random((round * 1000 + i).toLong())
            g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(rnd.nextInt(0xFFFFFF)), 500f, 750f, java.awt.Color(rnd.nextInt(0xFFFFFF)))
            g.fillRect(0, 0, 500, 750)
            repeat(40) { g.color = java.awt.Color(rnd.nextInt(0xFFFFFF)); g.fillOval(rnd.nextInt(500), rnd.nextInt(750), 40 + rnd.nextInt(200), 40 + rnd.nextInt(200)) }
            g.dispose()
            javax.imageio.ImageIO.write(img, "jpg", f)
            f
        }
    }

    /** Runs [reps] trials one after the other; each answer is the list of frame intervals (ms) from the moment the row is put on the screen */
    suspend fun run(mode: String, n: Int, reps: Int): List<List<Long>> {
        val out = ArrayList<List<Long>>()
        for (r in 0 until reps) {
            val files = makeFiles(n)
            val cards = files.mapIndexed { i, f -> Item("Poster $i of a test row", "bench://$counter/$i", "file:///" + f.absolutePath.replace(java.io.File.separatorChar, (47).toChar())) as SearchResponse }
            val done = CompletableDeferred<List<Long>>()
            commands.send(Trial(mode, cards, files, counter) to done)
            out.add(done.await())
        }
        return out
    }
}

@Composable
fun GpuBenchLayer() {
    val t = GpuBench.trial
    LaunchedEffect(Unit) {
        for ((trial, done) in GpuBench.commands) {
            repeat(8) { withFrameNanos { } }
            val frames = ArrayList<Long>()
            var last = withFrameNanos { it }
            GpuBench.trial = trial
            repeat(50) { val now = withFrameNanos { it }; frames.add((now - last) / 1_000_000); last = now }
            done.complete(frames)
            if (GpuBench.holdMs > 0) kotlinx.coroutines.delay(GpuBench.holdMs)
            GpuBench.trial = null
        }
    }
    if (t == null) return
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (t.mode) {
            "bare" -> Row(Modifier.padding(24.dp)) {
                t.files.forEach { f ->
                    val bmp = androidx.compose.runtime.remember(f) { f.inputStream().use { loadImageBitmap(it) } }
                    Image(bmp, null, Modifier.width(190.dp).height(285.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
                }
            }
            else -> Column(Modifier.padding(top = 24.dp)) {
                Shelf(t.cards, 190.dp, key = { it.url }) { card -> PosterCard(card, {}, 190.dp) }
            }
        }
    }
}
