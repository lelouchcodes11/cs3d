package com.lagradost.desktop.player

import android.util.Log
import androidx.media3.ui.CaptionStyleCompat
import com.lagradost.cloudstream3.ui.player.CustomDecoder
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitleFont
import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File

/**
 * The subtitle look of the settings (SaveCaptionStyle: font, size, colours, edge, background, position) as mpv properties.
 * mpv draws the subtitles itself, so this is the whole bridge between the style pages and what is on the picture.
 */
object SubtitleStyler {
    private const val TAG = "SubtitleStyler"

    /** mpv's own default size (55) for the engine's default 25 */
    private const val SIZE_FACTOR = 2.2

    private fun argb(color: Int) = String.format("#%02X%02X%02X%02X", (color ushr 24) and 0xFF, (color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)

    private fun num(v: Double) = String.format(java.util.Locale.ROOT, "%.2f", v)

    /** The fonts of the settings, as files libass can scan (written once, next to the other caches) */
    val fontsDir: File by lazy { File(AndroidRuntime.dataDir, "cache/subtitle-fonts").also { it.mkdirs() } }

    private val families = java.util.concurrent.ConcurrentHashMap<SubtitleFont, String>()

    /** Writes the packaged fonts to [fontsDir]; their family names are what mpv's `sub-font` asks for */
    fun prepareFonts() {
        for (font in SubtitleFont.entries) {
            if (font.resource == 0 || families.containsKey(font)) continue
            runCatching {
                val entry = AndroidRuntime.context.resources.getResourceEntryName(font.resource)
                val loader = SubtitleStyler::class.java.classLoader
                val ext = listOf("ttf", "otf").firstOrNull { loader.getResource("android-res/font/$entry.$it") != null } ?: return@runCatching
                val file = File(fontsDir, "$entry.$ext")
                if (!file.isFile || file.length() == 0L) loader.getResourceAsStream("android-res/font/$entry.$ext")?.use { input -> file.outputStream().use { input.copyTo(it) } }
                families[font] = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file).family
            }.onFailure { Log.w(TAG, "font ${font.label}: ${it.message}") }
        }
    }

    /** What mpv gets for [style], in the order it is set */
    fun properties(style: SaveCaptionStyle): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        // ---- font
        val custom = style.font == SubtitleFont.Custom && style.typefaceFilePath != null
        val family = when {
            custom -> runCatching {
                val file = File(style.typefaceFilePath!!)
                val copy = File(fontsDir, "custom-font.${file.extension.ifBlank { "ttf" }}")
                file.copyTo(copy, overwrite = true)
                java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, copy).family
            }.getOrNull()
            style.font != null -> families[style.font]
            else -> null
        }
        out += "sub-font" to (family ?: "sans-serif")
        out += "sub-font-size" to num((style.fixedTextSize ?: 25f) * SIZE_FACTOR)
        out += "sub-color" to argb(style.foregroundColor)
        out += "sub-bold" to if (style.bold) "yes" else "no"
        out += "sub-italic" to if (style.italic) "yes" else "no"

        // ---- edge: outline, shadow or none (raised and depressed are a short shadow)
        val edge = argb(style.edgeColor)
        val outline = if (style.edgeSize == null || style.edgeSize <= 0f) 3.0 else 1.0 + style.edgeSize / 10.0
        when (style.edgeType) {
            CaptionStyleCompat.EDGE_TYPE_NONE -> { out += "sub-border-size" to "0"; out += "sub-shadow-offset" to "0" }
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> { out += "sub-border-size" to "0"; out += "sub-shadow-offset" to num(2.0 + (style.edgeSize ?: 0f) / 10.0); out += "sub-shadow-color" to edge }
            CaptionStyleCompat.EDGE_TYPE_RAISED, CaptionStyleCompat.EDGE_TYPE_DEPRESSED -> { out += "sub-border-size" to "0"; out += "sub-shadow-offset" to "1.2"; out += "sub-shadow-color" to edge }
            else -> { out += "sub-border-size" to num(outline); out += "sub-border-color" to edge; out += "sub-shadow-offset" to "0" }
        }

        // ---- background box behind each line (a transparent colour means none)
        val boxed = (style.backgroundColor ushr 24) != 0
        out += "sub-border-style" to if (boxed) "background-box" else "outline-and-shadow"
        out += "sub-back-color" to argb(style.backgroundColor)

        // ---- position: SSA alignment numbers (1-3 bottom, 4-6 middle, 7-9 top) and the distance from the edge
        val alignment = style.alignment ?: CustomDecoder.SSA_ALIGNMENT_BOTTOM_CENTER
        out += "sub-align-x" to when (alignment) { 1, 4, 7 -> "left"; 3, 6, 9 -> "right"; else -> "center" }
        out += "sub-align-y" to when (alignment) { in 7..9 -> "top"; in 4..6 -> "center"; else -> "bottom" }
        out += "sub-margin-y" to (style.elevation + 2).toString()
        // inside the picture, not in the black bars around it where the controls cover it
        out += "sub-use-margins" to "no"
        // [Knocking on door], (laughs) and the like
        out += "sub-filter-sdh" to if (style.removeCaptions) "yes" else "no"
        return out
    }

    fun apply(player: MpvPlayer, style: SaveCaptionStyle) {
        prepareFonts()
        for ((name, value) in properties(style)) player.setMpvProperty(name, value)
    }
}
