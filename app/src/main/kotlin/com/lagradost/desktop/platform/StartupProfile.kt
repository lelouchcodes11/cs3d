package com.lagradost.desktop.platform

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * Start-up diagnostics. [mark] records milestones (milliseconds since the JVM started) in the log.
 * With `-Dcloudstream.profile=<seconds>` a sampler also looks at the UI thread and the busiest worker threads
 * every 50 ms and writes the most frequent frames to `logs/startup-profile.txt` in the data folder.
 */
object StartupProfile {
    private val jvmStart = runCatching { ManagementFactory.getRuntimeMXBean().startTime }.getOrDefault(System.currentTimeMillis())
    private val marks = ConcurrentLinkedQueue<String>()

    fun uptimeMs(): Long = System.currentTimeMillis() - jvmStart

    fun mark(name: String) {
        val line = "${uptimeMs()} ms  $name"
        marks.add(line)
        Log.i("Startup", line)
    }

    fun startSamplerIfRequested() {
        val seconds = System.getProperty("cloudstream.profile")?.toIntOrNull() ?: return
        thread(name = "cloudstream-startup-sampler", isDaemon = true) {
            val inclusive = HashMap<String, Int>()
            val leaf = HashMap<String, Int>()
            var samples = 0
            val end = System.currentTimeMillis() + seconds * 1000L
            while (System.currentTimeMillis() < end) {
                for ((t, stack) in Thread.getAllStackTraces()) {
                    if (t.name != "AWT-EventQueue-0") continue
                    if (stack.isEmpty()) continue
                    samples++
                    val seen = HashSet<String>()
                    for (f in stack) {
                        val c = f.className
                        if (c.startsWith("com.lagradost") || c.startsWith("androidx.compose") || c.startsWith("coil3") || c.startsWith("org.jetbrains.skiko") || c.startsWith("okhttp3")) {
                            val key = c.substringAfterLast('.') + "." + f.methodName
                            if (seen.add(key)) inclusive.merge(key, 1, Int::plus)
                        }
                    }
                    val top = stack.first { !it.className.startsWith("java.lang.ClassLoader") && !it.className.startsWith("jdk.internal.loader") && !it.className.startsWith("java.security.SecureClassLoader") }
                    leaf.merge(top.className.substringAfterLast('.') + "." + top.methodName, 1, Int::plus)
                }
                Thread.sleep(50)
            }
            runCatching {
                val text = buildString {
                    appendLine("Milestones:")
                    marks.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("UI thread samples: $samples (50 ms apart)")
                    appendLine("Most frequent leaf frames (class loading folded into its caller):")
                    leaf.entries.sortedByDescending { it.value }.take(25).forEach { appendLine("  ${it.value}  ${it.key}") }
                    appendLine()
                    appendLine("Inclusive (anywhere in the stack):")
                    inclusive.entries.sortedByDescending { it.value }.take(70).forEach { appendLine("  ${it.value}  ${it.key}") }
                }
                val dir = File(AndroidRuntime.dataDir, "logs").also { it.mkdirs() }
                File(dir, "startup-profile.txt").writeText(text)
            }
        }
    }
}
