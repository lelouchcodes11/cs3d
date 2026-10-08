package com.lagradost.desktop.platform

import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File

/** A short list of the extension updates that were installed (by the automatic check at start or by Update Plugins), newest first */
object PluginUpdateLog {
    class Entry(val at: Long, val name: String, val version: Int)

    private const val MAX = 200
    private val file: File get() = File(AndroidRuntime.dataDir, "files/extension_updates.tsv")

    @Synchronized
    fun record(name: String, version: Int) {
        runCatching {
            val f = file.also { it.parentFile?.mkdirs() }
            val old = if (f.isFile) f.readLines().take(MAX - 1) else emptyList()
            f.writeText((listOf("${System.currentTimeMillis()}\t${name.replace('\t', ' ')}\t$version") + old).joinToString("\n"))
        }
    }

    @Synchronized
    fun all(): List<Entry> = runCatching {
        if (!file.isFile) return@runCatching emptyList()
        file.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) null else Entry(p[0].toLongOrNull() ?: return@mapNotNull null, p[1], p[2].toIntOrNull() ?: 0)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear() { runCatching { file.delete() } }
}
