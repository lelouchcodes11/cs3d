package com.lagradost.desktop.player

import com.lagradost.cloudstream3.app
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/**
 * A subtitle file (SRT, WebVTT or ASS) put into another language with the public Google Translate endpoint (the one the picture translator,
 * [ScreenText], uses). Only the words change: every cue keeps its timing, an ASS line keeps its style and position. Lines go in batches of a few
 * thousand characters; a batch that does not come back line for line is redone cue by cue. The result is kept on disk, so a second time is instant.
 */
object SubtitleTranslate {
    private const val BATCH_CHARS = 3500
    /** A line break inside a cue, while its lines travel as one line */
    private const val BREAK = " || "

    private fun cacheDir() = File(AndroidRuntime.dataDir, "cache/subtitle-translations").apply { mkdirs() }

    private fun key(source: String, to: String): String =
        MessageDigest.getInstance("SHA-1").digest("$source|$to".toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    /** The translated file, or null with the reason (in [error]) */
    class Result(val file: File?, val error: String? = null)

    /** [raw]: the subtitle as downloaded; [sourceKey]: where it came from (its address), for the cache */
    suspend fun translate(raw: ByteArray, sourceKey: String, to: String, language: String? = null): Result {
        val clean = SubtitleFiles.normalize(raw, language) ?: return Result(null, "This subtitle has no lines that can be read")
        if (clean.extension !in setOf(".srt", ".vtt", ".ass")) return Result(null, "Only SRT, WebVTT and ASS subtitles can be translated")
        val out = File(cacheDir(), key(sourceKey, to) + clean.extension)
        if (out.length() > 0) return Result(out)
        val text = String(clean.bytes, Charsets.UTF_8)
        var totalSlots = 0
        var translatedSlots = 0
        val result = rewrite(text, clean.extension) { words ->
            totalSlots = words.size
            translateAll(words, to).also { got -> translatedSlots = got.count { it != null } }
        } ?: return Result(null, "This subtitle has no lines that can be read")
        if (translatedSlots == 0) return Result(null, "The translation service did not answer. Check the internet connection and try again.")
        // only cache when almost everything came back: a partial answer (rate limit / network drop) still plays now, and retrying later can fetch the rest
        val target = if (totalSlots > 0 && translatedSlots * 10 >= totalSlots * 9) {
            out
        } else {
            File.createTempFile("subtitle-partial-", clean.extension).apply { deleteOnExit() }
        }
        target.writeText(result, Charsets.UTF_8)
        return Result(target)
    }

    /** [text] with the words of every cue replaced by what [translate] gives for them (null: the original stays); null when it has no cues */
    internal suspend fun rewrite(text: String, extension: String, translate: suspend (List<String>) -> List<String?>): String? {
        val lines = text.replace("\r\n", "\n").split('\n').toMutableList()
        // where the words are, and how to put them back
        val slots = if (extension == ".ass") assSlots(lines) else cueSlots(lines)
        if (slots.isEmpty()) return null
        val translated = translate(slots.map { it.text })
        val done = slots.filterIndexed { i, s -> translated.getOrNull(i)?.let { s.put(lines, it); true } ?: false }
        // a translated cue of several lines was folded into its first line: the others are dropped (an untranslated one keeps them)
        val drop = dropped(done)
        return lines.filterIndexed { i, _ -> i !in drop }.joinToString("\n")
    }

    private class Slot(val text: String, val put: (MutableList<String>, String) -> Unit, val drop: List<Int> = emptyList())

    private fun dropped(slots: List<Slot>): Set<Int> = slots.flatMap { it.drop }.toSet()

    /** SRT / WebVTT: the lines after a timing line up to the blank line are one cue */
    private fun cueSlots(lines: List<String>): List<Slot> {
        val slots = ArrayList<Slot>()
        var i = 0
        while (i < lines.size) {
            if ("-->" in lines[i]) {
                val start = i + 1
                var end = start
                // a file without blank lines between cues: the cue ends where the next timing (and its number) begins; read as one cue,
                // the next cue's number and timing were dropped from the translated file and the two cues ran together
                while (end < lines.size && lines[end].isNotBlank() && "-->" !in lines[end]) end++
                if (end < lines.size && "-->" in lines[end] && end - 1 > start && lines[end - 1].trim().let { n -> n.isNotEmpty() && n.all { it.isDigit() } }) end--
                if (end > start) {
                    val words = lines.subList(start, end).joinToString(BREAK) { it.trim() }
                    if (words.any { it.isLetter() }) {
                        slots += Slot(words, { l, t -> l[start] = t.split(BREAK.trim()).joinToString("\n") { it.trim() } }, (start + 1 until end).toList())
                    }
                }
                i = end
            } else i++
        }
        return slots
    }

    private val assTag = Regex("""\{[^}]*\}""")

    /** ASS: the text field of every Dialogue line (the tenth); a leading tag block (position, alignment) stays, tags inside the words go */
    private fun assSlots(lines: List<String>): List<Slot> {
        val slots = ArrayList<Slot>()
        lines.forEachIndexed { i, line ->
            if (!line.trimStart().startsWith("Dialogue:", ignoreCase = true)) return@forEachIndexed
            val parts = line.split(',', limit = 10)
            if (parts.size < 10) return@forEachIndexed
            val field = parts[9]
            val lead = Regex("""^(\{[^}]*\})+""").find(field)?.value.orEmpty()
            // drawings (\p1) are pictures, not words
            if ("\\p1" in lead || "\\p2" in lead) return@forEachIndexed
            val words = field.removePrefix(lead).replace(assTag, "").replace("\\N", BREAK).replace("\\n", BREAK).replace("\\h", " ").trim()
            if (!words.any { it.isLetter() }) return@forEachIndexed
            val head = parts.take(9).joinToString(",")
            slots += Slot(words, { l, t -> l[i] = head + "," + lead + t.split(BREAK.trim()).joinToString("\\N") { it.trim() } })
        }
        return slots
    }

    /** One answer per text (null where the service did not answer: the original stays) */
    private suspend fun translateAll(texts: List<String>, to: String): List<String?> = coroutineScope {
        val batches = ArrayList<IntRange>()
        var from = 0
        var size = 0
        texts.forEachIndexed { i, t ->
            if (size > 0 && size + t.length > BATCH_CHARS) { batches += from until i; from = i; size = 0 }
            size += t.length + 1
        }
        if (from < texts.size) batches += from until texts.size
        val gate = Semaphore(4)
        val out = arrayOfNulls<String>(texts.size)
        batches.map { range ->
            async {
                val part = texts.subList(range.first, range.last + 1)
                val got = gate.withPermit { call(part.joinToString("\n"), to) }?.split('\n')
                if (got != null && got.size == part.size) got.forEachIndexed { k, s -> out[range.first + k] = s }
                // the lines did not come back one for one: each cue alone (not when the service did not answer at all, which would repeat the failure for every cue)
                else if (got != null) part.forEachIndexed { k, s -> out[range.first + k] = gate.withPermit { call(s, to) } }
            }
        }.awaitAll()
        out.toList()
    }

    /**
     * Two of Google's free interfaces. translate.googleapis.com (gtx) answers a "Sorry..." page to whole networks it finds busy (the owner's,
     * 2026-11-20: every subtitle came back untranslated); clients5 (the Chrome extension's) answered the same request from there. Each is tried
     * in turn; the one that worked is asked first next time.
     */
    @Volatile private var chromeFirst = true

    private suspend fun call(text: String, to: String): String? {
        repeat(3) { attempt ->
            for (chrome in if (chromeFirst) listOf(true, false) else listOf(false, true)) {
                val got = runCatching { if (chrome) viaChrome(text, to) else viaGtx(text, to) }.getOrNull()
                if (!got.isNullOrBlank()) { chromeFirst = chrome; return got }
            }
            delay(600L * (attempt + 1))
        }
        return null
    }

    private suspend fun viaGtx(text: String, to: String): String = parse(
        app.post(
            "https://translate.googleapis.com/translate_a/single",
            params = mapOf("client" to "gtx", "sl" to "auto", "tl" to to, "dt" to "t"),
            data = mapOf("q" to text),
            timeout = 30,
        ).text
    )

    /** [["translation","en"]] for sl=auto (a plain ["translation"] otherwise) */
    private suspend fun viaChrome(text: String, to: String): String {
        val body = app.post(
            "https://clients5.google.com/translate_a/t",
            params = mapOf("client" to "dict-chrome-ex", "sl" to "auto", "tl" to to),
            data = mapOf("q" to text),
            timeout = 30,
        ).text
        val first = org.json.JSONArray(body).get(0)
        return (if (first is org.json.JSONArray) first.optString(0) else first.toString()).trim('\n', ' ')
    }

    /** The answer's pieces joined (each piece is a sentence and keeps its own line breaks) */
    private fun parse(body: String): String {
        val segments = org.json.JSONArray(body).getJSONArray(0)
        return buildString { for (i in 0 until segments.length()) append(segments.getJSONArray(i).optString(0).takeIf { it != "null" }.orEmpty()) }.trim('\n', ' ')
    }
}
