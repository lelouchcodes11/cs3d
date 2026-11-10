package com.lagradost.desktop

import java.io.File

/**
 * crash.log: every uncaught exception is added at the end, one report after another. It was rewritten per crash from each crashing thread at once,
 * so when several threads failed together (a full heap takes down a handful of them in the same second) the reports overwrote each other into one
 * garbled text, and an earlier crash was lost to a later stray one.
 */
object CrashLog {
    /** The file is cut back to its newest [KEEP_BYTES] once it grows past [MAX_BYTES] */
    const val MAX_BYTES = 512 * 1024L
    const val KEEP_BYTES = 256 * 1024

    private const val SEPARATOR = "\n----------------------------------------\n\n"

    @Synchronized
    fun append(file: File, report: String) {
        if (file.isFile && file.length() > MAX_BYTES) trim(file)
        java.io.FileOutputStream(file, true).use { out ->
            if (file.length() > 0) out.write(SEPARATOR.toByteArray())
            out.write(report.toByteArray())
        }
    }

    /** Keeps the newest reports: the tail of the file, from the first report that starts inside it */
    private fun trim(file: File) {
        val bytes = file.readBytes()
        val tail = String(bytes, (bytes.size - KEEP_BYTES).coerceAtLeast(0), minOf(KEEP_BYTES, bytes.size))
        val start = tail.indexOf(SEPARATOR.trimStart('\n')).let { if (it < 0) 0 else it + SEPARATOR.trimStart('\n').length }
        file.writeText(tail.substring(start))
    }
}
