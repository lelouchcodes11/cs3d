package com.lagradost.desktop.player

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * What a downloaded "subtitle" really is. Sources send files that mpv does not take as they are: a zip or gzip with the subtitle inside, UTF-16
 * or a Windows code page, a WebVTT body behind an .srt name, an error page, a file without a single cue. Those loaded "fine" and then showed
 * nothing (or failed), so the viewer had to try one after the other. Here every file is unpacked, turned into UTF-8, named by what it is and
 * checked for cues before mpv sees it; one that is nothing usable gives null, so that it can be reported (and left out of the list) at once.
 */
object SubtitleFiles {
    /** [lastCueMs]: where the last cue ends (-1 unknown), to tell a file made for another cut of the film */
    class Clean(val bytes: ByteArray, val extension: String, val cues: Int, val lastCueMs: Long = -1L, val profile: IntArray = IntArray(0), val firstCueMs: Long = -1L)

    private val endTime = Regex("""-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})""")
    private val assEnd = Regex("""(?m)^\s*Dialogue:\s*[^,]*,\s*[\d:.]+\s*,\s*(\d+:\d{2}:\d{2}[.:]\d{1,3})""")

    private fun clock(t: String): Long {
        val parts = t.replace(',', '.').split(':')
        val sec = parts.last().toDoubleOrNull() ?: return -1L
        var ms = (sec * 1000).toLong()
        var unit = 60_000L
        for (i in parts.size - 2 downTo 0) {
            ms += (parts[i].toLongOrNull() ?: 0L) * unit
            unit *= 60
        }
        return ms
    }

    // the latest end of all cues, not the end of the last line: ASS files are often sorted by style (songs and signs at the bottom) and SRTs
    // carry a credit line at the end timed at 0:00, and such a file was taken for one "made for another cut" (it seemed to end early)
    private fun lastCueEnd(text: String, extension: String): Long = runCatching {
        (if (extension == ".ass") assEnd else endTime).findAll(text).map { clock(it.groupValues[1]) }.filter { it >= 0 }.maxOrNull() ?: -1L
    }.getOrDefault(-1L)

    private val startTime = Regex("""((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->""")
    private val assStart = Regex("""(?m)^\s*Dialogue:\s*[^,]*,\s*(\d+:\d{2}:\d{2}[.:]\d{1,3})""")

    const val PROFILE_BINS = 150
    const val PROFILE_BIN_MS = 120_000L

    /** How many cues start in every two minutes of the film (up to 5 h): the shape of the talk. One timing of one cut of the film has one shape */
    internal fun timingProfile(text: String, extension: String): IntArray {
        val bins = IntArray(PROFILE_BINS)
        runCatching {
            for (m in (if (extension == ".ass") assStart else startTime).findAll(text)) {
                val ms = clock(m.groupValues[1])
                if (ms >= 0) bins[(ms / PROFILE_BIN_MS).toInt().coerceIn(0, PROFILE_BINS - 1)]++
            }
        }
        return bins
    }

    /**
     * Where the first cue starts. SRT / WebVTT cues are in order in nearly every file: the earliest of the first few. An ASS file is often sorted
     * by style (songs and signs first, from all over the film): the earliest of all its lines
     */
    internal fun firstCueStart(text: String, extension: String): Long = runCatching {
        val starts = (if (extension == ".ass") assStart else startTime).findAll(text).map { clock(it.groupValues[1]) }
        (if (extension == ".ass") starts else starts.take(5)).filter { it >= 0 }.minOrNull() ?: -1L
    }.getOrDefault(-1L)

    /** 0 (the same talk at the same times) to 1 (nothing in common): half the summed difference of the two shapes, each as a share of its own cues */
    fun timingDistance(a: IntArray, b: IntArray): Double {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 1.0
        val sa = a.sum().toDouble()
        val sb = b.sum().toDouble()
        if (sa <= 0 || sb <= 0) return 1.0
        var d = 0.0
        for (i in a.indices) d += Math.abs(a[i] / sa - b[i] / sb)
        return d / 2
    }

    private const val MAX_BYTES = 8 * 1024 * 1024

    private val srtTime = Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}""")
    private val vttTime = Regex("""(?:\d{1,2}:)?\d{2}:\d{2}\.\d{3}\s*-->\s*(?:\d{1,2}:)?\d{2}:\d{2}\.\d{3}""")
    private val assDialogue = Regex("""(?m)^\s*Dialogue:""")
    private val microDvd = Regex("""(?m)^\{\d+\}\{\d+\}""")
    private val ttmlCue = Regex("""<p\s[^>]*begin=""")

    fun normalize(raw: ByteArray, language: String? = null): Clean? {
        var data = raw
        // a zip: the first of its files that is a usable subtitle (a zip made on a Mac starts with "__MACOSX/._Film.srt", which is no subtitle,
        // and one of several languages has the wanted one first)
        if (data.size > 4 && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte() && data[2].toInt() == 3) return unzip(data, language).firstNotNullOfOrNull { normalizeText(it, language) }
        else if (data.size > 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) data = gunzip(data) ?: return null
        return normalizeText(data, language)
    }

    /** "01:02,500 --> 01:04,000": SRT timings without hours (some writers leave them out), which the player's SRT reader does not take */
    private val shortSrtTiming = Regex("""(?m)^([ \t]*)((?:[0-9]{1,2}:)?[0-9]{1,2}:[0-9]{2},[0-9]{1,3})([ \t]*-->[ \t]*)((?:[0-9]{1,2}:)?[0-9]{1,2}:[0-9]{2},[0-9]{1,3})""")

    /** Every SRT timing with hours ("01:02,500" -> "00:01:02,500"); a file whose timings all have them is returned as it is */
    internal fun padSrtHours(text: String): String {
        fun full(t: String): String {
            val parts = t.split(':')
            return if (parts.size == 3) t else "00:" + parts[0].padStart(2, '0') + ":" + parts[1]
        }
        if (shortSrtTiming.findAll(text).none { it.groupValues[2].count { c -> c == ':' } < 2 || it.groupValues[4].count { c -> c == ':' } < 2 }) return text
        return shortSrtTiming.replace(text) { m -> m.groupValues[1] + full(m.groupValues[2]) + m.groupValues[3] + full(m.groupValues[4]) }
    }

    private fun normalizeText(data: ByteArray, language: String?): Clean? {
        if (data.isEmpty() || data.size > MAX_BYTES) return null
        var text = decode(data, language).trimStart('﻿')
        val head = text.trimStart()
        val ass = head.contains("[Script Info]", true) || assDialogue.containsMatchIn(text)
        if (!head.startsWith("WEBVTT") && !ass) text = padSrtHours(text)
        val (extension, cues) = when {
            head.startsWith("WEBVTT") -> ".vtt" to vttTime.findAll(text).count()
            ass -> ".ass" to assDialogue.findAll(text).count()
            srtTime.containsMatchIn(text) -> ".srt" to srtTime.findAll(text).count()
            // WebVTT cues without the "WEBVTT" line: the player recognises a WebVTT file by that line only, so it is put in
            vttTime.containsMatchIn(text) -> { text = "WEBVTT\n\n" + text.trimStart(); ".vtt" to vttTime.findAll(text).count() }
            ttmlCue.containsMatchIn(text) -> ".ttml" to ttmlCue.findAll(text).count()
            microDvd.containsMatchIn(text) -> ".sub" to microDvd.findAll(text).count()
            else -> return null
        }
        if (cues == 0) return null
        return Clean(text.toByteArray(Charsets.UTF_8), extension, cues, lastCueEnd(text, extension), timingProfile(text, extension), firstCueStart(text, extension))
    }

    /**
     * The files of a zip that may be the subtitle, in the order to try them: the usual subtitle formats before plain text, those named for
     * [language] ("Film.en.srt", "English.srt") first. A Mac's "__MACOSX/._Film.srt" (file details, not a subtitle) is left out.
     */
    private fun unzip(data: ByteArray, language: String? = null): List<ByteArray> = runCatching {
        val wanted = listOf("srt", "vtt", "ass", "ssa", "ttml", "sub", "txt", "xml")
        val tokens = languageTokens(language)
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            val found = ArrayList<Triple<String, String, ByteArray>>()
            while (true) {
                // a cut-off zip: the files read so far are still tried
                val entry = (try { zip.nextEntry } catch (_: Exception) { null }) ?: break
                if (entry.isDirectory) continue
                val base = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (entry.name.startsWith("__MACOSX/") || base.startsWith("._")) continue
                val ext = base.substringAfterLast('.', "").lowercase()
                if (ext !in wanted) continue
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                val cut = try {
                    while (true) { val n = zip.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > MAX_BYTES) break }
                    false
                } catch (_: Exception) { true }
                if (cut) break
                found += Triple(ext, base, out.toByteArray())
            }
            found.withIndex().sortedWith(compareBy(
                { if (it.value.first in wanted.take(5)) 0 else 1 },
                { if (tokens.isNotEmpty() && nameTokens(it.value.second).any { t -> t in tokens }) 0 else 1 },
                { it.index },
            )).map { it.value.third }
        }
    }.getOrDefault(emptyList())

    private fun nameTokens(name: String): List<String> = name.lowercase().split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }

    /** How a file of [language] ("en", "pt-BR", "English") may be marked in its name: the code, the three-letter code, the English name */
    private fun languageTokens(language: String?): Set<String> {
        val first = language?.let { nameTokens(it).firstOrNull() } ?: return emptySet()
        val out = hashSetOf(first)
        runCatching {
            // a name ("english"): its code
            val code = if (first.length in 2..3) first
            else java.util.Locale.getISOLanguages().firstOrNull { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH).equals(first, true) }
            if (code != null) {
                val locale = java.util.Locale.forLanguageTag(code)
                out += code
                runCatching { locale.isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { out += it.lowercase() }
                locale.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase().takeIf { it.isNotEmpty() && it != code }?.let { out += it }
            }
        }
        return out
    }

    private val notSubtitle = setOf("nfo", "jpg", "jpeg", "png", "gif", "webp", "bmp", "url", "htm", "html", "exe", "md", "diz", "pdf", "db", "ini", "lnk", "rtf", "doc", "docx", "sfv", "md5", "nzb", "torrent")
    private val sxe = Regex("""s([0-9]{1,2})[ ._-]?e([0-9]{1,4})(?![0-9])""")
    private val nxn = Regex("""(?<![0-9])([0-9]{1,2})x([0-9]{1,3})(?![0-9])""")
    private val epOnly = Regex("""(?:^|[^a-z0-9])(?:e|ep|episode)[ ._-]?([0-9]{1,4})(?![0-9])""")

    /** 2: named for this episode (S01E05, 1x05), 1: "E05", 0: not told, -1 / -2: named for another episode */
    internal fun episodeFit(name: String, season: Int?, episode: Int?): Int {
        if (episode == null || episode <= 0) return 0
        val n = name.lowercase()
        (sxe.find(n) ?: nxn.find(n))?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e = m.groupValues[2].toIntOrNull()
            return if (e == episode && (season == null || season <= 0 || s == season)) 2 else -2
        }
        epOnly.find(n)?.let { m -> return if (m.groupValues[1].toIntOrNull() == episode) 1 else -1 }
        return 0
    }

    /**
     * The order to offer the files of one subtitle download in (indices into [names]). A zip from SubDL / SubSource holds a whole season,
     * other languages, an .nfo or a picture, and the first file of it was the one selected: the first episode's subtitle on the fifth episode,
     * or an .nfo that does not load. Files that are no subtitle are left out (all are kept when nothing else is there); the one of [episode]
     * and of [language] comes first.
     */
    fun downloadOrder(names: List<String?>, language: String?, season: Int?, episode: Int?): List<Int> {
        if (names.size < 2) return names.indices.toList()
        val tokens = languageTokens(language)
        fun junk(name: String?): Boolean {
            if (name == null) return false
            if (name.endsWith('/') || name.endsWith('\\')) return true
            val base = name.substringAfterLast('/').substringAfterLast('\\')
            return name.startsWith("__MACOSX/") || base.startsWith("._") || base.substringAfterLast('.', "").lowercase() in notSubtitle
        }
        val kept = names.indices.filter { !junk(names[it]) }.ifEmpty { names.indices.toList() }
        return kept.sortedWith(compareBy(
            { -episodeFit(names[it].orEmpty(), season, episode) },
            { if (tokens.isNotEmpty() && nameTokens(names[it].orEmpty()).any { t -> t in tokens }) 0 else 1 },
            { if (names[it].orEmpty().substringAfterLast('.', "").lowercase() in setOf("txt", "xml")) 1 else 0 },
            { it },
        ))
    }

    private fun gunzip(data: ByteArray): ByteArray? = runCatching { GZIPInputStream(ByteArrayInputStream(data)).use { it.readNBytes(MAX_BYTES + 1) } }.getOrNull()

    /** Byte order mark first, then UTF-16 without one (every other byte empty), then UTF-8 when it is valid, else the Windows code page */
    private fun decode(d: ByteArray, language: String? = null): String {
        fun bytes(from: Int) = ByteBuffer.wrap(d, from, d.size - from)
        // a byte order mark says what the file is, whatever is chosen: with a code page chosen for old files (Arabic, Cyrillic ...), a UTF-8 or
        // UTF-16 file with a mark was read in that code page and showed only rubbish
        when {
            d.size >= 3 && d[0] == 0xEF.toByte() && d[1] == 0xBB.toByte() && d[2] == 0xBF.toByte() -> return String(d, 3, d.size - 3, Charsets.UTF_8)
            d.size >= 2 && d[0] == 0xFF.toByte() && d[1] == 0xFE.toByte() -> return Charsets.UTF_16LE.decode(bytes(2)).toString()
            d.size >= 2 && d[0] == 0xFE.toByte() && d[1] == 0xFF.toByte() -> return Charsets.UTF_16BE.decode(bytes(2)).toString()
        }
        // the character set the viewer chose (Settings > Player > Subtitle encoding): used as it is, nothing guessed
        SubtitleEncoding.charset()?.let { forced -> return String(d, forced) }
        return when {
            looksUtf16(d, evenNulls = false) -> Charsets.UTF_16LE.decode(bytes(0)).toString()
            looksUtf16(d, evenNulls = true) -> Charsets.UTF_16BE.decode(bytes(0)).toString()
            else -> strictUtf8(d) ?: String(d, legacyCharset(language))
        }
    }

    /**
     * The code page an old (not UTF-8) file in [language] (a code such as "ar", "pt-BR", or a name such as "Arabic") was most likely made in:
     * read as Windows-1252, Arabic, Russian, Greek, Chinese ... files showed only rubbish until the encoding was picked by hand
     */
    internal fun legacyCharset(language: String?): Charset {
        val l = language?.trim()?.lowercase().orEmpty()
        val code = l.substringBefore('-').substringBefore('_')
        fun has(vararg names: String) = names.any { l.startsWith(it) }
        val name = when {
            code in setOf("ar", "ara", "fa", "per", "fas", "ur", "urd") || has("arabic", "persian", "farsi", "urdu") -> "windows-1256"
            code in setOf("ru", "rus", "uk", "ukr", "bg", "bul", "sr", "srp", "mk", "mac", "mkd", "be", "bel") ||
                has("russian", "ukrainian", "bulgarian", "serbian", "macedonian", "belarusian") -> "windows-1251"
            code in setOf("el", "gre", "ell") || has("greek") -> "windows-1253"
            code in setOf("tr", "tur") || has("turkish") -> "windows-1254"
            code in setOf("he", "heb", "iw") || has("hebrew") -> "windows-1255"
            code in setOf("pl", "pol", "cs", "cze", "ces", "sk", "slo", "slk", "hu", "hun", "ro", "rum", "ron", "hr", "hrv", "sl", "slv", "bs", "bos", "sq", "alb", "sqi") ||
                has("polish", "czech", "slovak", "hungarian", "romanian", "croatian", "slovenian", "bosnian", "albanian") -> "windows-1250"
            code in setOf("et", "est", "lv", "lav", "lt", "lit") || has("estonian", "latvian", "lithuanian") -> "windows-1257"
            code in setOf("vi", "vie") || has("vietnamese") -> "windows-1258"
            code in setOf("th", "tha") || has("thai") -> "x-windows-874"
            code in setOf("ja", "jpn") || has("japanese") -> "windows-31j"
            code in setOf("ko", "kor") || has("korean") -> "x-windows-949"
            // traditional Chinese (Taiwan, Hong Kong) is Big5, simplified GBK; OpenSubtitles' older codes: "zht" / "zt" traditional, "zhe" / "ze" bilingual;
            // its names: "Chinese (traditional)"; other sites: "Chinese (Taiwan)", "Cantonese"
            code in setOf("zht", "zt", "yue") -> "Big5"
            code in setOf("zh", "chi", "zho", "zhe", "ze") || has("chinese", "cantonese", "mandarin") ->
                if (Regex("tw|hk|hant|traditional|taiwan|hong ?kong|cantonese").containsMatchIn(l)) "Big5" else "GBK"
            else -> "windows-1252"
        }
        return runCatching { Charset.forName(name) }.getOrDefault(Charset.forName("windows-1252"))
    }

    private fun looksUtf16(d: ByteArray, evenNulls: Boolean): Boolean {
        val n = minOf(d.size, 400) / 2
        if (n < 20) return false
        var nulls = 0
        for (i in 0 until n) if (d[i * 2 + (if (evenNulls) 0 else 1)].toInt() == 0) nulls++
        return nulls > n * 0.6
    }

    private fun strictUtf8(d: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(d)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
