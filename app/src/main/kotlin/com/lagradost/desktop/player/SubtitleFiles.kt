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

    private fun lastCueEnd(text: String, extension: String): Long = runCatching {
        val m = if (extension == ".ass") assEnd.findAll(text).lastOrNull() else endTime.findAll(text).lastOrNull()
        m?.let { clock(it.groupValues[1]) } ?: -1L
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

    /** Where the first cue starts (cues are in order in nearly every file; the earliest of the first few is taken) */
    internal fun firstCueStart(text: String, extension: String): Long = runCatching {
        (if (extension == ".ass") assStart else startTime).findAll(text).take(5).map { clock(it.groupValues[1]) }.filter { it >= 0 }.minOrNull() ?: -1L
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

    fun normalize(raw: ByteArray): Clean? {
        var data = raw
        if (data.size > 4 && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte() && data[2].toInt() == 3) data = unzip(data) ?: return null
        else if (data.size > 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) data = gunzip(data) ?: return null
        if (data.isEmpty() || data.size > MAX_BYTES) return null
        val text = decode(data).trimStart('﻿')
        val head = text.trimStart()
        val (extension, cues) = when {
            head.startsWith("WEBVTT") -> ".vtt" to vttTime.findAll(text).count()
            head.contains("[Script Info]", true) || assDialogue.containsMatchIn(text) -> ".ass" to assDialogue.findAll(text).count()
            srtTime.containsMatchIn(text) -> ".srt" to srtTime.findAll(text).count()
            vttTime.containsMatchIn(text) -> ".vtt" to vttTime.findAll(text).count()
            ttmlCue.containsMatchIn(text) -> ".ttml" to ttmlCue.findAll(text).count()
            microDvd.containsMatchIn(text) -> ".sub" to microDvd.findAll(text).count()
            else -> return null
        }
        if (cues == 0) return null
        return Clean(text.toByteArray(Charsets.UTF_8), extension, cues, lastCueEnd(text, extension), timingProfile(text, extension), firstCueStart(text, extension))
    }

    private fun unzip(data: ByteArray): ByteArray? = runCatching {
        val wanted = listOf("srt", "vtt", "ass", "ssa", "ttml", "sub", "txt", "xml")
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            val found = ArrayList<Pair<String, ByteArray>>()
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                if (ext !in wanted) continue
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) { val n = zip.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > MAX_BYTES) break }
                found += ext to out.toByteArray()
            }
            // the first file, the usual subtitle formats before a plain text one
            (found.firstOrNull { it.first in wanted.take(5) } ?: found.firstOrNull())?.second
        }
    }.getOrNull()

    private fun gunzip(data: ByteArray): ByteArray? = runCatching { GZIPInputStream(ByteArrayInputStream(data)).use { it.readNBytes(MAX_BYTES + 1) } }.getOrNull()

    /** Byte order mark first, then UTF-16 without one (every other byte empty), then UTF-8 when it is valid, else the Windows code page */
    private fun decode(d: ByteArray): String {
        // the character set the viewer chose (Settings > Player > Subtitle encoding): used as it is, nothing guessed
        SubtitleEncoding.charset()?.let { forced ->
            val from = if (forced.name().startsWith("UTF-8") && d.size >= 3 && d[0] == 0xEF.toByte() && d[1] == 0xBB.toByte() && d[2] == 0xBF.toByte()) 3 else 0
            return String(d, from, d.size - from, forced)
        }
        fun bytes(from: Int) = ByteBuffer.wrap(d, from, d.size - from)
        return when {
            d.size >= 3 && d[0] == 0xEF.toByte() && d[1] == 0xBB.toByte() && d[2] == 0xBF.toByte() -> String(d, 3, d.size - 3, Charsets.UTF_8)
            d.size >= 2 && d[0] == 0xFF.toByte() && d[1] == 0xFE.toByte() -> Charsets.UTF_16LE.decode(bytes(2)).toString()
            d.size >= 2 && d[0] == 0xFE.toByte() && d[1] == 0xFF.toByte() -> Charsets.UTF_16BE.decode(bytes(2)).toString()
            looksUtf16(d, evenNulls = false) -> Charsets.UTF_16LE.decode(bytes(0)).toString()
            looksUtf16(d, evenNulls = true) -> Charsets.UTF_16BE.decode(bytes(0)).toString()
            else -> strictUtf8(d) ?: String(d, Charset.forName("windows-1252"))
        }
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
