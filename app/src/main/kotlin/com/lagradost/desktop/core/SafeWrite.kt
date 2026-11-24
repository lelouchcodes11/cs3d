package com.lagradost.desktop.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [text] so that the file is either the old one or the new one, never half of each: a crash, a full disk or the app being closed in the middle of
 * the write used to leave a cut-off JSON file, and the next start read it as empty and wrote over the saved data.
 */
fun File.writeTextAtomic(text: String) {
    parentFile?.mkdirs()
    // each write has its own temporary file: two writers at once (a timer and the shutdown) cannot mix their bytes
    val tmp = File(parentFile, "$name.${System.nanoTime()}.tmp")
    try {
        tmp.writeText(text)
        try {
            Files.move(tmp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(tmp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        tmp.delete()
    }
}

/** A file that could not be read is kept next to the original (name.bad) before anything writes over it, so the data can still be recovered */
fun File.keepUnreadable() {
    runCatching { if (isFile && length() > 0) Files.copy(toPath(), File(parentFile, "$name.bad").toPath(), StandardCopyOption.REPLACE_EXISTING) }
}
