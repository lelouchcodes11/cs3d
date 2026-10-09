package com.lagradost.desktop.player

import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File

/** The Anime4K shaders (bloc97, MIT, headers kept in the files) for mpv's `glsl-shaders`: restore, then enlarge x2 where the picture is scaled up */
internal object Anime4K {
    private val files = listOf("Anime4K_Restore_CNN_M.glsl", "Anime4K_Upscale_CNN_x2_M.glsl")

    /** The shader chain as mpv's option value (the files are unpacked into the data folder once), or null when they can not be unpacked */
    fun option(): String? = runCatching {
        val dir = File(AndroidRuntime.dataDir, "shaders").also { it.mkdirs() }
        files.joinToString(";") { name ->
            val file = File(dir, name)
            if (!file.isFile || file.length() == 0L) {
                val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("shaders/$name")
                    ?: Anime4K::class.java.classLoader?.getResourceAsStream("shaders/$name")
                    ?: Anime4K::class.java.getResourceAsStream("/shaders/$name")
                    ?: error("Shader resource shaders/$name not found")
                stream.use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            file.absolutePath.replace('\\', '/')
        }
    }.getOrNull()
}
