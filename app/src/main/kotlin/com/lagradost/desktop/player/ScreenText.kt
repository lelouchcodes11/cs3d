package com.lagradost.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.app
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The writing that is part of the picture (a game window with its stats, a sign, a letter, a title card) is not in the subtitles. T (or
 * Subtitles > Translate on-screen text) pauses, takes the picture from the player, lets Windows read the text on it (the OCR that is part of
 * Windows 10 / 11, nothing is sent anywhere for that) and puts it over the picture translated, each piece where the original is.
 *
 * Only the translation leaves the PC (the text, not the picture), to the public Google Translate endpoint, like the phone app's translations.
 */
object ScreenText {
    /** One piece of writing: where it is on the picture (fractions 0..1 of the picture) and the text before and after */
    class Block(val original: String, val text: String, val x: Float, val y: Float, val w: Float, val h: Float, val lines: Int)

    /** Where the picture is inside the player window (fractions 0..1: left, top, right, bottom) */
    class Picture(val l: Float, val t: Float, val r: Float, val b: Float)

    sealed interface State {
        object Idle : State
        class Working(val step: String) : State
        class Showing(val blocks: List<Block>, val picture: Picture, val atMs: Long, val from: String, val to: String) : State

        /** [openLanguages]: the fix is in the Windows language settings; [autoClose]: only a remark that goes away by itself */
        class Problem(val title: String, val detail: String, val openLanguages: Boolean = false, val autoClose: Boolean = false) : State
    }

    var state: State by mutableStateOf(State.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    // ---- settings ------------------------------------------------------------------------------------------------------

    /** (name, Windows OCR language tag); "profile" lets Windows use the languages of the user */
    val sources: List<Pair<String, String>> = listOf(
        "Japanese" to "ja", "Korean" to "ko", "Chinese (simplified)" to "zh-Hans-CN", "Chinese (traditional)" to "zh-Hant-TW",
        "English" to "en-US", "Whatever Windows reads for me" to "profile",
    )

    /** (name, Google Translate language code) */
    val targets: List<Pair<String, String>> = listOf(
        "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de", "Portuguese" to "pt", "Italian" to "it", "Dutch" to "nl",
        "Russian" to "ru", "Polish" to "pl", "Turkish" to "tr", "Arabic" to "ar", "Hindi" to "hi", "Indonesian" to "id", "Vietnamese" to "vi",
        "Thai" to "th", "Korean" to "ko", "Japanese" to "ja", "Chinese (simplified)" to "zh-CN",
    )

    /** Changes with every choice: what shows the current names reads it and draws again */
    var version by mutableIntStateOf(0)
        private set

    private val prefs get() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    fun from(): String = prefs.getString("screen_text_from", null)?.takeIf { f -> sources.any { it.second == f } } ?: "ja"
    fun to(): String = prefs.getString("screen_text_to", null)?.takeIf { f -> targets.any { it.second == f } } ?: "en"
    fun fromName(): String = sources.first { it.second == from() }.first
    fun toName(): String = targets.first { it.second == to() }.first

    fun setFrom(tag: String) { prefs.edit().putString("screen_text_from", tag).apply(); version++ }
    fun setTo(code: String) { prefs.edit().putString("screen_text_to", code).apply(); version++ }

    // ---- running -------------------------------------------------------------------------------------------------------

    /** Takes the picture that is on the screen now and puts the translated writing over it; [positionMs]: where the video is, so that a seek puts it away */
    fun start(positionMs: Long) {
        job?.cancel()
        state = State.Working("Reading the picture")
        job = scope.launch {
            try {
                state = withTimeout(60_000) { read(positionMs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state = State.Problem("Could not translate the picture", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        state = State.Idle
    }

    private suspend fun read(positionMs: Long): State {
        val player = MpvPlayer.active ?: return State.Problem("Nothing is playing", "Start a video first.", autoClose = true)
        // the pause settles: the frame is the one the viewer sees
        delay(150)
        val dir = File(System.getProperty("java.io.tmpdir"), "cloudstream-desktop").apply { mkdirs() }
        val png = File(dir, "screen-text-${System.nanoTime()}.png")
        try {
            if (!player.captureFrame(png)) return State.Problem("Could not take the picture", "The player did not hand over the frame. Try again in a moment.", autoClose = true)
            val picture = player.pictureRect() ?: Picture(0f, 0f, 1f, 1f)
            val from = from()
            val to = to()
            state = State.Working("Reading the text")
            val ocr = recognise(png, from)
            ocr.error?.let { return problemFor(it, ocr.available, from) }
            val lines = ocr.lines.map { it.copy(t = clean(it.t)) }.filter { worthKeeping(it) }
            if (lines.isEmpty()) return State.Problem("No writing found", "Nothing readable in ${fromName()} on this picture.", autoClose = true)
            val groups = merge(lines)
            state = State.Working("Translating")
            val translated = translate(groups.map { it.text }, to)
            if (translated.all { it == null }) {
                return State.Problem("Could not translate", "The translation service did not answer. Check the internet connection and try again.")
            }
            val blocks = groups.mapIndexedNotNull { i, g ->
                val t = translated[i] ?: g.text
                // a line that came back as it was (English subtitles, names, numbers) is not worth a box
                if (t.equals(g.text, ignoreCase = true) || t.filter { it.isLetterOrDigit() }.equals(g.text.filter { it.isLetterOrDigit() }, ignoreCase = true)) null
                else Block(g.text, t, g.x / ocr.w, g.y / ocr.h, g.w / ocr.w, g.h / ocr.h, g.lines)
            }
            if (blocks.isEmpty()) return State.Problem("Nothing to translate", "The writing on this picture is already in ${toName()} (or only names and numbers).", autoClose = true)
            return State.Showing(blocks, picture, positionMs, from, to)
        } finally {
            runCatching { png.delete() }
        }
    }

    private fun problemFor(error: String, available: String, from: String): State {
        return if (error == "no-engine") {
            val name = sources.firstOrNull { it.second == from }?.first ?: from
            State.Problem(
                "Windows can't read $name yet",
                "Windows reads the writing on a picture with its own OCR, one language pack per language. Add it once: Settings > Time & language > Language & region > Add a language > $name > tick \"Optical character recognition\" (it does not have to become your display language, no restart needed). " +
                    "Installed for OCR now: ${available.ifBlank { "none" }}.",
                openLanguages = true,
            )
        } else {
            State.Problem("Windows could not read the picture", error)
        }
    }

    // ---- the picture -> text ---------------------------------------------------------------------------------------------

    class OcrLine(val t: String, val x: Float, val y: Float, val w: Float, val h: Float) {
        fun copy(t: String = this.t) = OcrLine(t, x, y, w, h)
    }

    class Ocr(val error: String?, val available: String, val w: Float, val h: Float, val lines: List<OcrLine>)

    private val script: File by lazy {
        val dir = File(System.getProperty("java.io.tmpdir"), "cloudstream-desktop").apply { mkdirs() }
        val bytes = ScreenText::class.java.getResourceAsStream("/ocr.ps1")!!.use { it.readBytes() }
        File(dir, "ocr-${bytes.contentHashCode().toUInt().toString(16)}.ps1").also { f -> if (!f.exists() || f.length() != bytes.size.toLong()) f.writeBytes(bytes) }
    }

    /** Windows' own OCR through PowerShell (the Windows.Media.Ocr classes are WinRT; ~0.7 s for a 1080p picture) */
    private suspend fun recognise(png: File, from: String): Ocr = withContext(Dispatchers.IO) {
        val langs = if (from == "profile") "profile" else from
        val powershell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        val p = ProcessBuilder(
            powershell.path, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.path, "-Image", png.absolutePath, "-Langs", langs,
        ).redirectErrorStream(false).start()
        try {
            p.outputStream.close()
            val out = CompletableFuture.supplyAsync { p.inputStream.readBytes() }
            val err = CompletableFuture.supplyAsync { runCatching { p.errorStream.readBytes() }.getOrDefault(ByteArray(0)) }
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                throw IllegalStateException("Windows did not answer in time")
            }
            val text = String(out.get(5, TimeUnit.SECONDS), Charsets.UTF_8).trim().trimStart('\uFEFF')
            if (text.isBlank()) throw IllegalStateException("Windows OCR gave no answer " + String(err.get(2, TimeUnit.SECONDS), Charsets.UTF_8).take(300))
            parse(text)
        } finally {
            if (p.isAlive) p.destroyForcibly()
        }
    }

    internal fun parse(json: String): Ocr {
        val o = JSONObject(json)
        if (o.has("error")) return Ocr(o.optString("error"), o.optString("available"), 0f, 0f, emptyList())
        val arr = o.optJSONArray("lines") ?: JSONArray()
        val lines = (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            OcrLine(l.optString("t"), l.optDouble("x").toFloat(), l.optDouble("y").toFloat(), l.optDouble("w").toFloat(), l.optDouble("h").toFloat())
        }
        return Ocr(null, "", o.optDouble("w", 1.0).toFloat().coerceAtLeast(1f), o.optDouble("h", 1.0).toFloat().coerceAtLeast(1f), lines)
    }

    private const val CJK = "\\u3040-\\u30ff\\u3400-\\u9fff\\uff00-\\uffef\\uac00-\\ud7af"
    private val cjkGap = Regex("(?<=[$CJK]) +(?=[$CJK])")

    /** Windows puts a space between the letters of Japanese / Chinese lines */
    internal fun clean(t: String): String = cjkGap.replace(t.trim(), "").replace(Regex("\\s{2,}"), " ")

    private fun isCjk(c: Char) = c in '\u3040'..'\u30ff' || c in '\u3400'..'\u9fff' || c in '\uac00'..'\ud7af'

    /** Noise of the engine (a stray stroke of a drawing read as a letter) is not worth a request */
    private fun worthKeeping(l: OcrLine): Boolean {
        val letters = l.t.count { it.isLetterOrDigit() }
        return letters >= 2 || l.t.any { isCjk(it) }
    }

    // ---- lines -> pieces ---------------------------------------------------------------------------------------------------

    class Group(val text: String, val x: Float, val y: Float, val w: Float, val h: Float, val lines: Int)

    /** Lines that are stacked under each other (same left edge or same centre, the same size) are one sentence */
    internal fun merge(lines: List<OcrLine>): List<Group> {
        val sorted = lines.sortedWith(compareBy({ it.y }, { it.x }))
        val out = ArrayList<MutableList<OcrLine>>()
        for (l in sorted) {
            val target = out.lastOrNull { g ->
                val last = g.last()
                val gap = l.y - (last.y + last.h)
                val ratio = l.h / last.h.coerceAtLeast(1f)
                val left = Math.abs(l.x - g.first().x) < last.h * 1.2f
                val centre = Math.abs((l.x + l.w / 2) - (last.x + last.w / 2)) < last.h * 1.2f
                gap < last.h * 0.7f && gap > -last.h * 0.5f && ratio in 0.7f..1.4f && (left || centre)
            }
            if (target != null) target += l else out += mutableListOf(l)
        }
        return out.map { g ->
            val x1 = g.minOf { it.x }
            val y1 = g.minOf { it.y }
            val x2 = g.maxOf { it.x + it.w }
            val y2 = g.maxOf { it.y + it.h }
            val joined = g.map { it.t }.reduce { a, b -> if (a.lastOrNull()?.let(::isCjk) == true && b.firstOrNull()?.let(::isCjk) == true) a + b else "$a $b" }
            Group(joined, x1, y1, x2 - x1, y2 - y1, g.size)
        }
    }

    // ---- translating ---------------------------------------------------------------------------------------------------------

    /** One answer per text; null where the service did not answer (the original stays) */
    private suspend fun translate(texts: List<String>, to: String): List<String?> = coroutineScope {
        val gate = Semaphore(6)
        texts.map { t -> async { gate.withPermit { translateOne(t, to) } } }.awaitAll()
    }

    private suspend fun translateOne(text: String, to: String): String? {
        // numbers and signs stay as they are
        if (text.none { it.isLetter() }) return text
        repeat(2) { attempt ->
            val got = runCatching {
                val body = app.get(
                    "https://translate.googleapis.com/translate_a/single",
                    params = mapOf("client" to "gtx", "sl" to "auto", "tl" to to, "dt" to "t", "q" to text),
                    timeout = 15,
                ).text
                parseTranslation(body)
            }.getOrNull()
            if (!got.isNullOrBlank()) return got
            if (attempt == 0) delay(400)
        }
        return null
    }

    internal fun parseTranslation(body: String): String {
        val segments = JSONArray(body).getJSONArray(0)
        return buildString { for (i in 0 until segments.length()) append(segments.getJSONArray(i).optString(0).takeIf { it != "null" }.orEmpty()) }.trim()
    }
}
