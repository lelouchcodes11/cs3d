package com.lagradost.desktop.platform

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import java.awt.EventQueue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.concurrent.thread

/**
 * Notices when the UI thread stops answering (the window then looks empty and does not react).
 * - every freeze of 0.7 s or more is appended to `logs/jank.txt` (duration and where the UI thread was stuck), so what makes the app
 *   "hangy" can be found without a profiler;
 * - a freeze of 8 s or more writes every thread's stack to `logs/hang-<time>.txt` in the data folder.
 */
object HangWatchdog {
    @Volatile
    private var lastAnswer = System.currentTimeMillis()

    @Volatile
    private var edt: Thread? = null

    // -Dcloudstream.jank=<ms> lowers it while hunting for stutters that are too short to be called a freeze
    private val JANK_MS = System.getProperty("cloudstream.jank")?.toLongOrNull() ?: 700L
    private const val MAX_JANK_FILE = 400_000L

    fun start() {
        thread(name = "cloudstream-hang-watchdog", isDaemon = true) {
            var reported = false
            var jankSince = 0L
            var firstStack = ""
            var lastStack = ""
            while (true) {
                EventQueue.invokeLater {
                    edt = Thread.currentThread()
                    lastAnswer = System.currentTimeMillis()
                }
                Thread.sleep(200)
                val now = System.currentTimeMillis()
                val stuck = now - lastAnswer
                if (stuck >= JANK_MS) {
                    if (jankSince == 0L) jankSince = lastAnswer
                    val s = summary(Thread.getAllStackTraces().keys.firstOrNull { it.name.startsWith("AWT-EventQueue") } ?: edt)
                    if (firstStack.isEmpty()) firstStack = s
                    lastStack = s
                } else if (jankSince != 0L && stuck < 300) {
                    logJank(now - jankSince, firstStack, lastStack)
                    jankSince = 0L
                    firstStack = ""
                    lastStack = ""
                }
                if (stuck > 8000 && !reported) {
                    reported = true
                    dump(stuck)
                } else if (stuck < 3000) reported = false
            }
        }
    }

    /** The first frames of the UI thread that are not JDK class loading plumbing */
    private fun summary(t: Thread?): String = runCatching {
        val frames = t?.stackTrace.orEmpty().filter {
            !it.className.startsWith("java.lang.ClassLoader") && !it.className.startsWith("jdk.internal.loader") &&
                !it.className.startsWith("java.security.SecureClassLoader")
        }
        frames.take(40).joinToString("\n    at ")
    }.getOrDefault("")

    private fun logJank(durationMs: Long, first: String, last: String) {
        runCatching {
            if (!AndroidRuntime.isInitialized) return
            val dir = File(AndroidRuntime.dataDir, "logs").also { it.mkdirs() }
            val file = File(dir, "jank.txt")
            if (file.length() > MAX_JANK_FILE) return
            file.appendText(buildString {
                appendLine("${SimpleDateFormat("HH:mm:ss").format(Date())}  UI thread frozen for $durationMs ms")
                appendLine("  first seen at:\n    at $first")
                if (last != first) appendLine("  last seen at:\n    at $last")
                appendLine()
            })
        }
    }

    private fun dump(stuckMs: Long) {
        runCatching {
            val text = buildString {
                appendLine("The UI thread did not answer for $stuckMs ms (${Date()})")
                for ((t, stack) in Thread.getAllStackTraces()) {
                    appendLine()
                    appendLine("\"${t.name}\" ${t.state}")
                    stack.take(40).forEach { appendLine("\tat $it") }
                }
            }
            Log.e("HangWatchdog", text.lineSequence().take(60).joinToString("\n"))
            if (AndroidRuntime.isInitialized) {
                val dir = File(AndroidRuntime.dataDir, "logs").also { it.mkdirs() }
                File(dir, "hang-" + SimpleDateFormat("yyyyMMdd-HHmmss").format(Date()) + ".txt").writeText(text)
            }
        }
    }
}
