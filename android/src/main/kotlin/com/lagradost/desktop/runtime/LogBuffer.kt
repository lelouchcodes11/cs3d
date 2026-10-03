package com.lagradost.desktop.runtime

import java.io.File
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory ring buffer of log lines, the desktop equivalent of logcat.
 * Lines are formatted like `logcat -v threadtime` so the upstream logcat parser can read them.
 */
object LogBuffer {
    private const val MAX_LINES = 5000
    private val lines = ConcurrentLinkedDeque<String>()
    private val size = AtomicInteger(0)
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val pid = ProcessHandle.current().pid()

    @Volatile
    var echo: PrintStream? = System.out

    @Volatile
    var logFile: File? = null

    fun priorityChar(priority: Int): Char = when (priority) {
        2 -> 'V'
        3 -> 'D'
        4 -> 'I'
        5 -> 'W'
        6 -> 'E'
        7 -> 'F'
        else -> 'I'
    }

    fun add(priority: Int, tag: String?, msg: String?) {
        val time = synchronized(format) { format.format(Date()) }
        val tid = Thread.currentThread().threadId()
        val prefix = "$time ${pid.toString().padStart(5)} ${tid.toString().padStart(5)} ${priorityChar(priority)} ${tag ?: "null"}: "
        val text = msg ?: "null"
        for (part in text.split('\n')) {
            val line = prefix + part
            lines.addLast(line)
            if (size.incrementAndGet() > MAX_LINES) {
                lines.pollFirst()
                size.decrementAndGet()
            }
            echo?.println(line)
        }
    }

    fun snapshot(): List<String> = lines.toList()

    fun clear() {
        lines.clear()
        size.set(0)
    }
}
