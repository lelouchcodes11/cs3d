package com.lagradost.desktop.ui.screens.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.player.MpvPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Automatic subtitle timing, made cheap: nothing is downloaded or decoded a second time. While the video plays, a small audio filter in mpv
 * (voice band pass + loudness meter, @ssync) reports how loud the voices are; ten times a second the app notes that loudness and whether a
 * subtitle line is on screen, keyed by the position in the video. From 20 s of playing on (every 5 s) it tries every shift of the lines
 * from -12 s to +12 s, takes the one where lines and voices line up best, moves the subtitle delay by it and takes the filter away again.
 * A result it is not sure of changes nothing. Off by default: the player menu (Subtitles > Delay and size) runs it once or for every video.
 */
object SubtitleSync {
    sealed interface State {
        data object Idle : State
        data class Listening(val seconds: Int) : State
        data class Done(val shiftMs: Long) : State
        data class Failed(val reason: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    private const val PREF = "desktop_sub_autosync"
    private const val LABEL = "ssync"
    private const val BIN_MS = 100
    private const val MAX_SHIFT_BINS = 120 // 12 s
    // it decides as soon as the match is clear: from 20 s of playing on (checked every 5 s, with stricter rules while it has little),
    // and keeps listening up to 3 minutes of playing when the sound is hard to read (music, noise); then it gives up and changes nothing
    private const val FIRST_TRY_BINS = 200
    private const val TRY_EVERY_BINS = 50
    private const val RELAXED_BINS = 900
    private const val MAX_BINS = 1800
    private const val MIN_LINES = 6
    private const val GIVE_UP_MS = 8 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    /** The video and subtitle a sync already ran for (the automatic one runs once per pair) */
    private var doneFor: String? = null

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)
    var automatic: Boolean
        get() = runCatching { prefs().getBoolean(PREF, false) }.getOrDefault(false)
        set(v) { runCatching { prefs().edit().putBoolean(PREF, v).apply() } }

    /** Called about once a second by the player screen: starts a sync for a new video + subtitle when the automatic sync is on */
    fun tick(s: PlayerSession) {
        if (!automatic || job?.isActive == true) return
        val key = key() ?: return
        if (key != doneFor) start(s)
    }

    private fun key(): String? {
        val p = MpvPlayer.active ?: return null
        val sid = p.getMpvPropertyString("sid")?.takeIf { it != "no" && it.isNotBlank() } ?: return null
        val path = p.getMpvPropertyString("path") ?: return null
        return "$path|$sid"
    }

    /** Listens and moves the subtitle delay once (the player menu's button; the automatic sync uses it too) */
    fun start(s: PlayerSession) {
        job?.cancel()
        val player = MpvPlayer.active ?: run { state = State.Failed("Nothing is playing"); return }
        val key = key() ?: run { state = State.Failed("Turn on subtitles first"); return }
        doneFor = key
        state = State.Listening(0)
        job = scope.launch {
            // voices sit between about 300 Hz and 3 kHz: music and effects outside it count less; the meter averages ~5 audio frames
            val added = player.mpvCommandResult("af", "add", "@$LABEL:lavfi=[highpass=f=300,lowpass=f=3000,astats=metadata=1:reset=5:measure_perchannel=none:measure_overall=RMS_level]") >= 0
            if (!added) { state = State.Failed("The player could not listen to the sound"); return@launch }
            try {
                val loud = HashMap<Int, Float>()
                val text = HashMap<Int, Boolean>()
                var lineStarts = 0
                var wasText = false
                var shift: Int? = null
                var nextTry = FIRST_TRY_BINS
                val began = System.currentTimeMillis()
                while (isActive) {
                    delay(BIN_MS.toLong())
                    if (MpvPlayer.active !== player || key() != key) { state = State.Idle; return@launch }
                    if (System.currentTimeMillis() - began > GIVE_UP_MS) { state = State.Failed("Not enough talking to match the lines"); return@launch }
                    if (player.getMpvPropertyString("pause") == "yes" || player.getMpvPropertyString("seeking") == "yes") continue
                    val pos = player.getMpvPropertyString("time-pos")?.toDoubleOrNull() ?: continue
                    val rms = player.getMpvPropertyString("af-metadata/$LABEL")?.let { Regex("Overall\\.RMS_level\":\"(-?[0-9.]+|-inf)").find(it)?.groupValues?.get(1) }
                    val db = rms?.toFloatOrNull()?.coerceAtLeast(-90f) ?: -90f
                    val on = !player.getMpvPropertyString("sub-text").isNullOrBlank()
                    if (on && !wasText) lineStarts++
                    wasText = on
                    // the filter hears the sound one audio buffer before the speakers play it (measured: 0.4 s off at 1x, 0.8 s at 2x with
                    // audio-buffer=0.4): the loudness belongs that much later in the video than the position read now
                    val ahead = (player.getMpvPropertyString("audio-buffer")?.toDoubleOrNull() ?: 0.2) * (player.getMpvPropertyString("speed")?.toDoubleOrNull() ?: 1.0)
                    loud[((pos + ahead) * 1000 / BIN_MS).toInt()] = db
                    text[(pos * 1000 / BIN_MS).toInt()] = on
                    state = State.Listening(loud.size * BIN_MS / 1000)
                    if (loud.size >= nextTry && lineStarts >= MIN_LINES) {
                        nextTry = loud.size + TRY_EVERY_BINS
                        // with little sound only a very clear match counts; from 90 s on the normal rules
                        val early = loud.size < RELAXED_BINS
                        val found = bestShift(loud, text, minPeak = if (early) 3f else 2.5f, minCorrelation = if (early) 0.3f else 0.2f, minLead = if (early) 1.6f else 1.3f)
                        if (found != null) { shift = found; break }
                    }
                    if (loud.size >= MAX_BINS) break
                }
                if (shift == null) { state = State.Failed("Could not match the lines to the voices"); return@launch }
                // lines that show `shift` bins after the voice are late by that much: the delay goes down by it
                val ms = shift!! * BIN_MS.toLong()
                java.awt.EventQueue.invokeLater { s.setSubtitleDelay(s.subtitleDelayMs - ms) }
                state = State.Done(-ms)
                android.util.Log.i("SubtitleSync", "moved the subtitles by ${-ms} ms (${loud.size} samples, $lineStarts lines)")
            } finally {
                runCatching { player.mpvCommandResult("af", "remove", "@$LABEL") }
            }
        }
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
        is State.Listening -> "Listening… ${st.seconds} s"
        is State.Done -> if (st.shiftMs == 0L) "Already in time" else "Moved by ${if (st.shiftMs > 0) "+" else ""}${"%.1f".format(st.shiftMs / 1000.0)} s"
        is State.Failed -> st.reason
        State.Idle -> null
    }

}
