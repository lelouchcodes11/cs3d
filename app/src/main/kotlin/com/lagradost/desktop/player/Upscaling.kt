package com.lagradost.desktop.player

import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.fluent.Upscaler
import java.io.File

/**
 * What mpv is told for an [Upscaler] mode: its own scaling filters and, for the shader modes, the chain of user shaders (`glsl-shaders`).
 * The shaders are in resources/shaders and are unpacked into the data folder once:
 *  - Anime4K (bloc97, MIT) in the chains of its own "fast" and "HQ" modes A, B and C (headers kept in the files),
 *  - FidelityFX Super Resolution 1.0 and Contrast Adaptive Sharpening (AMD, MIT; mpv ports by agyild, headers kept).
 */
internal object Upscaling {
    private const val CLAMP = "Anime4K_Clamp_Highlights.glsl"
    private val AUTO_DOWNSCALE = listOf("Anime4K_AutoDownscalePre_x2.glsl", "Anime4K_AutoDownscalePre_x4.glsl")

    private fun chain(mode: Upscaler): List<String> = when (mode) {
        Upscaler.Off, Upscaler.Sharp, Upscaler.Quality -> emptyList()
        // FSR enlarges (1.2x and more), CAS sharpens a picture that is shown at its own size: each is idle where the other works
        Upscaler.Fsr -> listOf("FSR.glsl", "CAS.glsl")
        Upscaler.Anime4kA -> listOf(CLAMP, "Anime4K_Restore_CNN_M.glsl", "Anime4K_Upscale_CNN_x2_M.glsl") + AUTO_DOWNSCALE + "Anime4K_Upscale_CNN_x2_S.glsl"
        Upscaler.Anime4kB -> listOf(CLAMP, "Anime4K_Restore_CNN_Soft_M.glsl", "Anime4K_Upscale_CNN_x2_M.glsl") + AUTO_DOWNSCALE + "Anime4K_Upscale_CNN_x2_S.glsl"
        Upscaler.Anime4kC -> listOf(CLAMP, "Anime4K_Upscale_Denoise_CNN_x2_M.glsl") + AUTO_DOWNSCALE + "Anime4K_Upscale_CNN_x2_S.glsl"
        Upscaler.Anime4kAHq -> listOf(CLAMP, "Anime4K_Restore_CNN_VL.glsl", "Anime4K_Upscale_CNN_x2_VL.glsl") + AUTO_DOWNSCALE + "Anime4K_Upscale_CNN_x2_M.glsl"
    }

    /** The shader chain as mpv's option value (";" separates on Windows); "" for none, or when the files can not be unpacked */
    fun shaders(mode: Upscaler): String = runCatching {
        val names = chain(mode)
        if (names.isEmpty()) return@runCatching ""
        val dir = File(AndroidRuntime.dataDir, "shaders").also { it.mkdirs() }
        names.joinToString(";") { name ->
            val file = File(dir, name)
            if (!file.isFile || file.length() == 0L) {
                val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("shaders/$name")
                    ?: Upscaling::class.java.classLoader?.getResourceAsStream("shaders/$name")
                    ?: Upscaling::class.java.getResourceAsStream("/shaders/$name")
                    ?: error("Shader resource shaders/$name not found")
                stream.use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            file.absolutePath.replace('\\', '/')
        }
    }.getOrDefault("")

    /**
     * mpv's option / property values of [mode]. [Upscaler.Off] is mpv's own defaults (bilinear, no sigmoid), so switching back to it in the
     * player puts everything back as it was. The other modes are mpv's "gpu-hq" scaling around the chosen filter.
     */
    fun settings(mode: Upscaler): List<Pair<String, String>> {
        val off = mode == Upscaler.Off
        return listOf(
            "scale" to mode.scaler,
            "cscale" to if (off) "bilinear" else "spline36",
            "dscale" to if (off) "bilinear" else "mitchell",
            "correct-downscaling" to if (off) "no" else "yes",
            "sigmoid-upscaling" to if (off) "no" else "yes",
            "glsl-shaders" to shaders(mode),
        )
    }
}
