package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.Motion
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.skottie.Animation
import java.util.concurrent.ConcurrentHashMap

/** The flat background colors behind a character */
val avatarBackgrounds = listOf(
    Color(0xFF3D8BFD), // blue
    Color(0xFFE5484D), // red
    Color(0xFFFFC53D), // yellow
    Color(0xFF30C48D), // green
    Color(0xFF8E5BF5), // purple
    Color(0xFFFF8A3D), // orange
    Color(0xFFFF5CA8), // pink
    Color(0xFF19B5C4), // teal
    Color(0xFF5B6478), // slate
    Color(0xFF1B1E2B), // midnight
)

/**
 * Animated profile pictures. The characters of [avatarArt] are drawn by code (`AvatarArt.kt`): sharp at every size, nothing to load, each with an idle
 * loop of its own (blinks, floats, twinkles). The "Scene clips" are short anime / series clips played with Skia's Codec from animated WebP resources in
 * `resources/avatars`.
 */
class AnimatedAvatar(val id: String, val label: String, val group: String, val bg: Int)

/** The groups of the picker, in order */
val animatedAvatarGroups = listOf("Heroes", "Creatures", "Spooky & Space", "Scene clips")

/** [bg] is an index of [avatarBackgrounds]: the disc the picture looks best on */
private val drawnAvatars = listOf(
    AnimatedAvatar("ninja", "Shadow Ninja", "Heroes", 5),
    AnimatedAvatar("samurai", "Ronin", "Heroes", 7),
    AnimatedAvatar("astronaut", "Astronaut", "Heroes", 9),
    AnimatedAvatar("wizard", "Wizard", "Heroes", 6),
    AnimatedAvatar("cyber", "Cyber Runner", "Heroes", 8),
    AnimatedAvatar("pirate", "Pirate", "Heroes", 0),
    AnimatedAvatar("knight", "Knight", "Heroes", 4),
    AnimatedAvatar("hacker", "Hacker", "Heroes", 9),
    AnimatedAvatar("dj", "DJ", "Heroes", 4),
    AnimatedAvatar("vampire", "Vampire", "Heroes", 1),
    AnimatedAvatar("cat", "Tabby Cat", "Creatures", 0),
    AnimatedAvatar("fox", "Fox", "Creatures", 8),
    AnimatedAvatar("panda", "Panda", "Creatures", 3),
    AnimatedAvatar("bunny", "Bunny", "Creatures", 6),
    AnimatedAvatar("penguin", "Penguin", "Creatures", 7),
    AnimatedAvatar("dragon", "Little Dragon", "Creatures", 5),
    AnimatedAvatar("owl", "Owl", "Creatures", 2),
    AnimatedAvatar("frog", "Frog", "Creatures", 4),
    AnimatedAvatar("ghost", "Ghost", "Spooky & Space", 9),
    AnimatedAvatar("alien", "Alien", "Spooky & Space", 4),
    AnimatedAvatar("robot", "Robot", "Spooky & Space", 0),
    AnimatedAvatar("skull", "Skull", "Spooky & Space", 8),
    AnimatedAvatar("oni", "Oni", "Spooky & Space", 9),
    AnimatedAvatar("monster", "Fuzzy Monster", "Spooky & Space", 7),
    AnimatedAvatar("invader", "Pixel Invader", "Spooky & Space", 9),
)

private val clipAvatars = listOf(
    // Anime Legends
    AnimatedAvatar("gojo", "Gojo Satoru", "Anime Legends", 0),
    AnimatedAvatar("sukuna", "Ryomen Sukuna", "Anime Legends", 1),
    AnimatedAvatar("luffy", "Monkey D. Luffy", "Anime Legends", 2),
    AnimatedAvatar("zoro", "Roronoa Zoro", "Anime Legends", 3),
    AnimatedAvatar("levi", "Levi Ackerman", "Anime Legends", 8),
    AnimatedAvatar("eren", "Eren Yeager", "Anime Legends", 1),
    AnimatedAvatar("itachi", "Itachi Uchiha", "Anime Legends", 1),
    AnimatedAvatar("kakashi", "Kakashi Hatake", "Anime Legends", 0),
    AnimatedAvatar("naruto", "Naruto Uzumaki", "Anime Legends", 5),
    AnimatedAvatar("goku", "Son Goku", "Anime Legends", 2),
    AnimatedAvatar("tanjiro", "Tanjiro Kamado", "Anime Legends", 3),
    AnimatedAvatar("nezuko", "Nezuko Kamado", "Anime Legends", 6),
    AnimatedAvatar("denji", "Chainsaw Man", "Anime Legends", 5),
    AnimatedAvatar("killua", "Killua Zoldyck", "Anime Legends", 0),

    // Netflix & Series
    AnimatedAvatar("jinx", "Jinx", "Netflix & Series", 4),
    AnimatedAvatar("vi", "Vi", "Netflix & Series", 6),
    AnimatedAvatar("eleven", "Eleven", "Netflix & Series", 1),
    AnimatedAvatar("steve", "Steve Harrington", "Netflix & Series", 0),
    AnimatedAvatar("david", "David Martinez", "Netflix & Series", 3),
    AnimatedAvatar("lucy", "Lucy", "Netflix & Series", 4),
    AnimatedAvatar("wednesday", "Wednesday Addams", "Netflix & Series", 9),
    AnimatedAvatar("squidguard", "Squid Game Guard", "Netflix & Series", 6),
    AnimatedAvatar("geralt", "Geralt of Rivia", "Netflix & Series", 8),
    AnimatedAvatar("walter", "Walter White", "Netflix & Series", 3),
    AnimatedAvatar("homelander", "Homelander", "Netflix & Series", 0),

    // Movies & Comics
    AnimatedAvatar("spiderman", "Spider-Man", "Movies & Comics", 0),
    AnimatedAvatar("batman", "Batman", "Movies & Comics", 9),
    AnimatedAvatar("joker", "The Joker", "Movies & Comics", 4),
    AnimatedAvatar("deadpool", "Deadpool", "Movies & Comics", 1),
    AnimatedAvatar("ironman", "Iron Man", "Movies & Comics", 5),
    AnimatedAvatar("vader", "Darth Vader", "Movies & Comics", 1),
)

val animatedAvatars: List<AnimatedAvatar> = drawnAvatars + clipAvatars.map { AnimatedAvatar(it.id, it.label, "Scene clips", it.bg) }

fun animatedAvatar(id: String): AnimatedAvatar? = animatedAvatars.firstOrNull { it.id == id }

/** A profile that has made no choice gets one of the drawn characters, by its number (the ninja for the first) */
fun defaultAnimatedAvatar(keyIndex: Int): AnimatedAvatar {
    val picks = listOf("ninja", "astronaut", "cat", "robot", "wizard", "ghost", "alien", "samurai")
    val id = picks[Math.floorMod(keyIndex, picks.size)]
    return animatedAvatar(id) ?: animatedAvatars.first()
}

/** One loaded animation clip */
private sealed interface Clip {
    val seconds: Float
    fun draw(canvas: org.jetbrains.skia.Canvas, size: Float, at: Float, zoom: Float)
}

private class CodecClip(
    val frames: List<Image>,
    val durationsMs: List<Int>,
    override val seconds: Float,
) : Clip {
    private fun frameAt(at: Float): Image? {
        if (frames.isEmpty()) return null
        if (frames.size == 1) return frames[0]
        val totalMs = (seconds * 1000).toInt().coerceAtLeast(1)
        val currentMs = ((at * 1000).toLong() % totalMs).toInt()
        var elapsed = 0
        for (i in frames.indices) {
            elapsed += durationsMs.getOrElse(i) { 70 }
            if (currentMs < elapsed) return frames[i]
        }
        return frames.last()
    }

    override fun draw(canvas: org.jetbrains.skia.Canvas, size: Float, at: Float, zoom: Float) {
        val frame = frameAt(at) ?: return
        val inset = size * (1f - zoom) / 2f
        val w = frame.width.toFloat()
        val h = frame.height.toFloat()
        canvas.drawImageRect(
            frame,
            Rect.makeWH(w, h),
            Rect.makeXYWH(inset, inset, size * zoom, size * zoom),
        )
    }
}

private class SkottieClip(val animation: Animation) : Clip {
    override val seconds: Float = animation.duration.coerceAtLeast(0.5f)
    override fun draw(canvas: org.jetbrains.skia.Canvas, size: Float, at: Float, zoom: Float) {
        val inset = size * (1f - zoom) / 2f
        animation.seekFrameTime(at.coerceIn(0f, seconds))
        animation.render(canvas, Rect.makeXYWH(inset, inset, size * zoom, size * zoom))
    }
}

private object Clips {
    private val loaded = ConcurrentHashMap<String, Any>()
    private const val NONE = "none"

    /** The clip with this id, loaded the first time it is asked for */
    fun get(id: String): Clip? {
        val found = loaded.getOrPut(id) {
            runCatching {
                val cl = Clips::class.java.classLoader
                val webpStream = cl.getResourceAsStream("avatars/$id.webp")
                    ?: cl.getResourceAsStream("avatars/$id.gif")
                if (webpStream != null) {
                    val bytes = webpStream.use { it.readBytes() }
                    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
                    val count = codec.frameCount.coerceAtLeast(1)
                    val frames = ArrayList<Image>(count)
                    val durations = ArrayList<Int>(count)
                    var totalMs = 0
                    for (i in 0 until count) {
                        val bmp = Bitmap()
                        bmp.allocPixels(codec.imageInfo)
                        codec.readPixels(bmp, i)
                        bmp.setImmutable()
                        val img = Image.makeFromBitmap(bmp)
                        frames.add(img)
                        val dur = if (count > 1) codec.getFrameInfo(i).duration.coerceAtLeast(30) else 1000
                        durations.add(dur)
                        totalMs += dur
                    }
                    CodecClip(frames, durations, (totalMs / 1000f).coerceAtLeast(0.5f))
                } else {
                    val jsonStream = cl.getResourceAsStream("avatars/$id.json")
                    if (jsonStream != null) {
                        val text = jsonStream.use { String(it.readBytes(), Charsets.UTF_8) }
                        SkottieClip(Animation.makeFromString(text))
                    } else {
                        NONE
                    }
                }
            }.getOrDefault(NONE)
        }
        return found as? Clip
    }
}

/**
 * Where an animated picture is in its animation, seconds (0 = at rest, the first frame). With [playing] it loops for as long as it is true.
 * Otherwise it plays smoothly every while, and only while the window has focus and motion is on:
 * 3 seconds every 8 to 15, at 30 frames a second.
 */
@Composable
private fun rememberClipTime(seconds: Float?, animated: Boolean, playing: Boolean, seed: Int): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val focused = LocalWindowInfo.current.isWindowFocused
    val allowed = seconds != null && focused && Appearance.motion != Motion.Off && Appearance.animatedProfiles && (animated || playing)
    LaunchedEffect(allowed, playing, seconds) {
        time.floatValue = 0f
        val len = seconds ?: return@LaunchedEffect
        if (!allowed) return@LaunchedEffect
        val rnd = java.util.Random(seed * 7919L + System.nanoTime())
        if (!playing) delay(1200L + rnd.nextInt(4000))
        while (true) {
            val start = withFrameNanos { it }
            var last = 0f
            while (true) {
                val k = (withFrameNanos { it } - start) / 1e9f
                if (!playing && k >= len) break
                val frameTime = if (len > 0f) k % len else 0f
                if (playing || k - last >= 1f / 30f) {
                    time.floatValue = frameTime
                    last = k
                }
                withFrameNanos { }
            }
            time.floatValue = 0f
            if (!playing) delay(8_000L + rnd.nextInt(8_000))
        }
    }
    return time
}

/** The picture of [id] on its disc [background], filling its box; [size] is the box's side */
@Composable
fun AnimatedAvatarView(id: String, size: Dp, background: Color, modifier: Modifier = Modifier, animated: Boolean = true, playing: Boolean = false, seed: Int = id.hashCode()) {
    val art = remember(id) { avatarArt[id] }
    val clip = remember(id) { if (art != null) null else Clips.get(id) }
    val time = rememberClipTime(if (art != null) ART_LOOP else clip?.seconds, animated, playing, seed)
    Canvas(modifier.size(size)) {
        drawRect(background)
        if (art != null) { drawAvatarArt(art, time.value); return@Canvas }
        val c = clip ?: return@Canvas
        drawIntoCanvas { canvas ->
            val px = this.size.minDimension
            c.draw(canvas.nativeCanvas, px, time.value, 1.0f)
        }
    }
}
