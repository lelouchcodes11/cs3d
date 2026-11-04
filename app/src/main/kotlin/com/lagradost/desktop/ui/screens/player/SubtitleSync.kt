package com.lagradost.desktop.ui.screens.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.player.MpvPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Automatic subtitle timing, entirely in the background: the chosen subtitle file's line times are matched against the next ~2.5 minutes of
 * the video's sound, decoded ahead by a hidden second mpv (AudioAhead; a few seconds on a good connection). The playing video and its sound
 * are never touched (an earlier version put a voice filter into the playing sound while it listened, and the sound came out thin and
 * modulated). It tries every shift of the lines from -12 s to +12 s, takes the one where lines and voices line up best and moves the subtitle
 * delay by it; a result it is not sure of changes nothing. Subtitles inside the video file and live streams are left alone (the first are
 * almost always in time, the second have nothing ahead to read). Off by default: the player menu (Subtitles > Delay and size) runs it once
 * or for every video.
 */
object SubtitleSync {
    sealed interface State {
        data object Idle : State
        data object Reading : State
        data class Done(val shiftMs: Long) : State
        data class Failed(val reason: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    private const val PREF = "desktop_sub_autosync"
    private const val BIN_MS = 100
    private const val MAX_SHIFT_BINS = 120 // 12 s
    /** How much sound is read ahead of the position (seconds of video) */
    private const val AHEAD_SECONDS = 150.0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    /** The video and subtitle a sync already ran for (the automatic one runs once per pair) */
    private var doneFor: String? = null

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)
    private var automaticState by mutableStateOf(runCatching { prefs().getBoolean(PREF, false) }.getOrDefault(false))

    /** Sync every video (not live streams): one setting, shown in the player's Subtitles menu and in Settings > Player > Subtitles, both follow it at once */
    var automatic: Boolean
        get() = automaticState
        set(v) {
            automaticState = v
            runCatching { prefs().edit().putBoolean(PREF, v).apply() }
        }

    /** Called about once a second by the player screen: starts a sync for a new video + subtitle when the automatic sync is on */
    fun tick(s: PlayerSession) {
        if (job?.isActive == true) return
        val key = key()
        // another video or subtitle: what the menu said about the last one ("Moved by -2.0 s") no longer applies
        if (key != doneFor && state != State.Idle) state = State.Idle
        if (!automatic || key == null) return
        if (key != doneFor) start(s)
    }

    private fun key(): String? {
        val p = MpvPlayer.active ?: return null
        val sid = p.getMpvPropertyString("sid")?.takeIf { it != "no" && it.isNotBlank() } ?: return null
        val path = p.getMpvPropertyString("path") ?: return null
        return "$path|$sid"
    }

    /** Reads ahead and moves the subtitle delay once (the player menu's button; the automatic sync uses it too) */
    fun start(s: PlayerSession) {
        job?.cancel()
        val player = MpvPlayer.active ?: run { state = State.Failed("Nothing is playing"); return }
        val key = key() ?: run { state = State.Failed("Turn on subtitles first"); return }
        doneFor = key
        state = State.Reading
        job = scope.launch {
            val (shift, why) = runCatching { readAhead(player, s.subtitleDelayMs) }
                .onFailure { android.util.Log.w("SubtitleSync", "reading ahead failed: ${it.message}") }
                .getOrElse { null to "Could not read the video ahead" }
            if (!isActive) return@launch
            if (shift == null) { state = State.Failed(why ?: "Could not match the lines to the voices"); return@launch }
            // lines that show `shift` bins after the voice are late by that much: the delay goes down by it
            val ms = shift * BIN_MS.toLong()
            state = State.Done(-ms)
            // and a short note at the top of the picture, as when a subtitle loads
            val note = "Subtitles auto-synced · " + label()
            java.awt.EventQueue.invokeLater {
                s.setSubtitleDelay(s.subtitleDelayMs - ms)
                s.showSubtitleNote(note)
            }
            android.util.Log.i("SubtitleSync", "moved the subtitles by ${-ms} ms")
        }
    }

    /** The shift found from the subtitle file and the sound decoded ahead, or null and the reason it could not tell */
    private suspend fun readAhead(player: MpvPlayer, delayMs: Long): Pair<Int?, String?> {
        val duration = player.getMpvPropertyString("duration")?.toDoubleOrNull()
        if (player.getMpvPropertyString("seekable") == "no" || duration == null) return null to "Not for live streams"
        val from = player.getMpvPropertyString("time-pos")?.toDoubleOrNull() ?: return null to "Nothing is playing"
        val length = minOf(AHEAD_SECONDS, duration - from)
        if (length < 40.0) return null to "Too close to the end"
        val cues = loadCues(player) ?: return null to "Works with subtitle files only"
        if (cues.size < 10) return null to "Could not match the lines to the voices"
        val context = kotlinx.coroutines.currentCoroutineContext()
        val loud = com.lagradost.desktop.player.AudioAhead.read(player, from, length, 45_000) { !context.isActive }?.takeIf { it.size >= 300 }
            ?: return null to "Could not read the video ahead"
        // the lines as they show now (file time + the current delay), over the stretch that was read and the shifts around it
        val d = delayMs / 1000.0
        val first = (from * 10).toInt() - MAX_SHIFT_BINS - 5
        val last = ((from + length) * 10).toInt() + MAX_SHIFT_BINS + 5
        val text = HashMap<Int, Boolean>()
        for (b in first..last) text[b] = false
        for ((start, end) in cues) {
            val a = Math.round((start + d) * 10).toInt().coerceAtLeast(first)
            val z = Math.round((end + d) * 10).toInt().coerceAtMost(last + 1)
            for (b in a until z) text[b] = true
        }
        return bestShift(loud, text) to null
    }

    /** Start and end (seconds) of every line of the chosen subtitle when it is a file mpv loaded (a path or an address), else null */
    private suspend fun loadCues(player: MpvPlayer): List<Pair<Double, Double>>? {
        val sid = player.getMpvPropertyString("sid") ?: return null
        val count = player.getMpvPropertyString("track-list/count")?.toIntOrNull() ?: return null
        val source = (0 until count).firstNotNullOfOrNull { i ->
            if (player.getMpvPropertyString("track-list/$i/type") == "sub" && player.getMpvPropertyString("track-list/$i/id") == sid) player.getMpvPropertyString("track-list/$i/external-filename") else null
        } ?: return null
        val text = when {
            source.startsWith("http://") || source.startsWith("https://") -> com.lagradost.cloudstream3.app.get(source).text
            source.startsWith("memory://") -> source.removePrefix("memory://")
            else -> java.io.File(source.removePrefix("file:///").removePrefix("file://")).takeIf { it.isFile }?.readText(Charsets.ISO_8859_1)
        } ?: return null
        return parseCues(text)
    }

    /** The times of SRT / WebVTT lines (00:01:02,345 --> 00:01:04,000) or, when there are none, of ASS Dialogue lines */
    internal fun parseCues(text: String): List<Pair<Double, Double>> {
        fun seconds(h: String?, m: String, s: String, f: String) = (h?.toIntOrNull() ?: 0) * 3600.0 + m.toInt() * 60 + s.toInt() + f.toInt() / Math.pow(10.0, f.length.toDouble())
        val out = ArrayList<Pair<Double, Double>>()
        val srt = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
        for (m in srt.findAll(text)) {
            val g = m.groupValues
            out += seconds(g[1].ifEmpty { null }, g[2], g[3], g[4]) to seconds(g[5].ifEmpty { null }, g[6], g[7], g[8])
        }
        if (out.isEmpty()) {
            val ass = Regex("""^Dialogue:\s*[^,]*,(\d+):(\d{2}):(\d{2})\.(\d{1,3}),(\d+):(\d{2}):(\d{2})\.(\d{1,3})""", RegexOption.MULTILINE)
            for (m in ass.findAll(text)) {
                val g = m.groupValues
                out += seconds(g[1], g[2], g[3], g[4]) to seconds(g[5], g[6], g[7], g[8])
            }
        }
        return out.filter { it.second > it.first }.sortedBy { it.first }
    }

    fun cancel() {
        job?.cancel()
        state = State.Idle
    }

    /**
     * The shift (in bins) of the subtitle lines that lines them up best with the voices, or null when no shift stands out. Loudness is turned
     * into "louder than usual" (z-score) and line-on-screen into +1/-1; the score of a shift is their mean product over the samples both have.
     */
    internal fun bestShift(loud: Map<Int, Float>, text: Map<Int, Boolean>, minPeak: Float = 2.5f, minCorrelation: Float = 0.2f, minLead: Float = 1.3f): Int? {
        if (loud.size < 100) return null
        val values = loud.values
        val mean = values.average().toFloat()
        val sd = sqrt(values.sumOf { ((it - mean) * (it - mean)).toDouble() } / values.size).toFloat().takeIf { it > 0.5f } ?: return null
        val z = loud.mapValues { (it.value - mean) / sd }
        val textShare = text.values.count { it }.toFloat() / text.size
        if (textShare < 0.05f || textShare > 0.95f) return null
        val scores = FloatArray(2 * MAX_SHIFT_BINS + 1)
        for (k in -MAX_SHIFT_BINS..MAX_SHIFT_BINS) {
            var sum = 0f
            var n = 0
            for ((bin, zv) in z) {
                val t = text[bin + k] ?: continue
                sum += zv * (if (t) 1f - textShare else -textShare)
                n++
            }
            scores[k + MAX_SHIFT_BINS] = if (n >= maxOf(100, loud.size / 2)) sum / n else Float.NEGATIVE_INFINITY
        }
        val finite = scores.filter { it.isFinite() }
        if (finite.size < 30) return null
        val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: return null
        val best = scores[bestIndex]
        val m = finite.average().toFloat()
        val spread = sqrt(finite.sumOf { ((it - m) * (it - m)).toDouble() } / finite.size).toFloat()
        // the best shift has to stand well clear of the others (a flat or noisy score line means the sound does not tell)
        if (spread <= 0f || (best - m) / spread < minPeak) return null
        // and lines and voices have to really go together there (correlation): the best of many poor matches is still a poor match (pure noise found one)
        val correlation = best / sqrt(textShare * (1f - textShare))
        if (correlation < minCorrelation) return null
        // and no other shift (more than 3 s away) may come close: a second match almost as good means the sound does not tell which is right
        val runnerUp = scores.indices.filter { abs(it - bestIndex) > 30 && scores[it].isFinite() }.maxOfOrNull { scores[it] } ?: m
        if (best - m < minLead * (runnerUp - m)) return null
        val k = bestIndex - MAX_SHIFT_BINS
        // under 200 ms is within what the measurement can tell: leave it
        return if (abs(k) < 2) 0 else k
    }

    fun label(): String? = when (val st = state) {
        State.Reading -> "Reading ahead…"
        is State.Done -> if (st.shiftMs == 0L) "Already in time" else "Moved by ${if (st.shiftMs > 0) "+" else ""}${"%.1f".format(st.shiftMs / 1000.0)} s"
        is State.Failed -> st.reason
        State.Idle -> null
    }
}
