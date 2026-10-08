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

    /** The fonts of the settings, as files libass can scan (written once, next to the other caches; libass reads every file here) */
    val fontsDir: File by lazy { File(AndroidRuntime.dataDir, "cache/subtitle-fonts").also { it.mkdirs() } }

    private val families = java.util.concurrent.ConcurrentHashMap<SubtitleFont, String>()
    private val bundledFiles = java.util.concurrent.ConcurrentHashMap<SubtitleFont, File>()

    /** Packaged font files of a font resource: one file, or the members of a font family XML (Google Sans: regular, italic, bold, bold italic) */
    private fun resourceFonts(entry: String): List<String> {
        val loader = SubtitleStyler::class.java.classLoader
        listOf("ttf", "otf").firstOrNull { loader.getResource("android-res/font/$entry.$it") != null }?.let { return listOf("$entry.$it") }
        val xml = loader.getResourceAsStream("android-res/font/$entry.xml")?.use { String(it.readBytes()) } ?: return emptyList()
        val members = Regex("""<font\b[^>]*>""").findAll(xml).map { tag ->
            val style = Regex("""fontStyle="(\w+)"""").find(tag.value)?.groupValues?.get(1) ?: "normal"
            val weight = Regex("""fontWeight="(\d+)"""").find(tag.value)?.groupValues?.get(1)?.toIntOrNull() ?: 400
            val name = Regex("""font="@font/(\w+)"""").find(tag.value)?.groupValues?.get(1)
            Triple(name, style, weight)
        }.filter { it.first != null }.toList()
        // regular first: its family name is the one asked for
        return members.filter { it.third == 400 || it.third == 700 }.sortedBy { (if (it.third == 400) 0 else 2) + (if (it.second == "normal") 0 else 1) }
            .mapNotNull { m -> listOf("ttf", "otf").firstOrNull { loader.getResource("android-res/font/${m.first}.$it") != null }?.let { "${m.first}.$it" } }
    }

    /** Writes the packaged fonts to [fontsDir]; their family names are what mpv's `sub-font` asks for */
    fun prepareFonts() {
        for (font in SubtitleFont.entries) {
            if (font.resource == 0 || families.containsKey(font)) continue
            runCatching {
                val entry = AndroidRuntime.context.resources.getResourceEntryName(font.resource)
                val loader = SubtitleStyler::class.java.classLoader
                val files = resourceFonts(entry).map { name ->
                    File(fontsDir, name).also { file ->
                        if (!file.isFile || file.length() == 0L) loader.getResourceAsStream("android-res/font/$name")?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                }
                val main = files.firstOrNull() ?: return@runCatching
                bundledFiles[font] = main
                families[font] = familyOf(main) ?: return@runCatching
            }.onFailure { Log.w(TAG, "font ${font.label}: ${it.message}") }
        }
    }

    /** The font file the style draws with (for previews): a packaged font, a Windows font or the user's own file */
    fun fontFileOf(style: SaveCaptionStyle): File? {
        prepareFonts()
        return when {
            style.font == SubtitleFont.Custom -> style.typefaceFilePath?.let(::File)?.takeIf { it.isFile }
            style.font != null -> bundledFiles[style.font]
            else -> null
        }
    }

    // ---- fonts of Windows: libass finds them by name, nothing is copied

    private val windowsFontDirs: List<File> by lazy {
        listOfNotNull(
            File(System.getenv("WINDIR") ?: "C:/Windows", "Fonts"),
            System.getenv("LOCALAPPDATA")?.let { File(File(File(File(it, "Microsoft"), "Windows"), "Fonts").path) },
        )
    }

    private fun isWindowsFont(file: File): Boolean = windowsFontDirs.any { file.absoluteFile.parentFile?.equals(it.absoluteFile) == true }

    /** Fonts of Windows that read well as subtitles (and are not packaged already): (label, file), only the ones this PC has */
    val windowsFonts: List<Pair<String, File>> by lazy {
        val dir = windowsFontDirs.first()
        listOf(
            "Arial" to "arial.ttf", "Arial Black" to "ariblk.ttf", "Bahnschrift" to "bahnschrift.ttf", "Calibri" to "calibri.ttf", "Cambria" to "cambria.ttc",
            "Candara" to "Candara.ttf", "Constantia" to "constan.ttf", "Corbel" to "corbel.ttf", "Franklin Gothic" to "framd.ttf", "Georgia" to "georgia.ttf",
            "Impact" to "impact.ttf", "Segoe Print" to "segoepr.ttf", "Segoe UI" to "segoeui.ttf", "Segoe UI Semibold" to "seguisb.ttf",
            "Tahoma" to "tahoma.ttf",
        ).map { (label, name) -> label to File(dir, name) }.filter { it.second.isFile }
    }

    /** A font file's family name (the `name` table, record 1): TTF, OTF (also CFF outlines, which Java cannot open) and the first font of a TTC */
    fun familyOf(file: File): String? = runCatching {
        java.io.RandomAccessFile(file, "r").use { f ->
            fun u16(at: Long): Int { f.seek(at); return f.readUnsignedShort() }
            fun u32(at: Long): Long { f.seek(at); return f.readInt().toLong() and 0xFFFFFFFFL }
            fun tagAt(at: Long): String { f.seek(at); return String(ByteArray(4).also { f.readFully(it) }, Charsets.ISO_8859_1) }
            val font = if (tagAt(0) == "ttcf") u32(12) else 0L
            val tables = u16(font + 4)
            var name = -1L
            for (i in 0 until tables) {
                val rec = font + 12 + i * 16L
                if (tagAt(rec) == "name") name = u32(rec + 8)
            }
            if (name < 0) return@use null
            val count = u16(name + 2)
            val strings = name + u16(name + 4)
            var best: String? = null
            var bestScore = -1
            for (i in 0 until count) {
                val rec = name + 6 + i * 12L
                if (u16(rec + 6) != 1) continue
                val platform = u16(rec)
                val language = u16(rec + 4)
                val length = u16(rec + 8)
                val offset = u16(rec + 10)
                val bytes = ByteArray(length).also { f.seek(strings + offset); f.readFully(it) }
                val text = if (platform == 3 || platform == 0) String(bytes, Charsets.UTF_16BE) else String(bytes, Charsets.ISO_8859_1)
                val score = when { platform == 3 && language == 0x409 -> 3; platform == 3 -> 2; else -> 1 }
                if (score > bestScore && text.isNotBlank()) { best = text.trim(); bestScore = score }
            }
            best
        }
    }.getOrNull() ?: runCatching { java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file).family }.getOrNull()

    /**
     * A font file of the user's (not one of Windows'): copied into [fontsDir] under a name of its own content, so a newer choice never has to
     * overwrite a file the player still holds open (that copy failed and the default font was drawn). Older copies are removed when they can be.
     * Returns the copy and whether it is new to the player.
     */
    private fun customCopy(file: File): Pair<File, Boolean>? = runCatching {
        val bytes = file.readBytes()
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)
        val copy = File(fontsDir, "custom-$hash.${file.extension.ifBlank { "ttf" }.lowercase()}")
        val created = !copy.isFile
        if (created) copy.writeBytes(bytes)
        fontsDir.listFiles()?.filter { it.name.startsWith("custom") && it != copy }?.forEach { it.delete() }
        copy to created
    }.onFailure { Log.w(TAG, "custom font ${file.name}: ${it.message}") }.getOrNull()

    /** A font file was added to [fontsDir] after the player read it: libass reads that folder only when a subtitle track starts */
    @Volatile private var fontsChanged = false

    /** The family mpv asks libass for */
    private fun familyFor(style: SaveCaptionStyle): String? = when {
        style.font == SubtitleFont.Custom -> style.typefaceFilePath?.let(::File)?.takeIf { it.isFile }?.let { file ->
            if (isWindowsFont(file)) familyOf(file)
            else customCopy(file)?.let { (copy, created) -> if (created) fontsChanged = true; familyOf(copy) }
        }
        style.font != null -> families[style.font]
        else -> null
    }

    /** What mpv gets for [style], in the order it is set */
    fun properties(style: SaveCaptionStyle): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        // ---- font
        val family = familyFor(style)
        // one renderer for every kind of subtitle: SRT / WebVTT already follow the style, but ASS / SSA (files and embedded tracks) bring their own
        // font, size, outline and position, which is why some lines were bigger, outlined or elsewhere; "force" makes them follow the style's font, size,
        // colours and edge, the style overrides below add what "force" leaves alone (weight, slant and where the line sits)
        // the renderer setting (Settings > Subtitles): by default a styled subtitle (ASS, an SRT with <font> colours or {\an8}) keeps its own fonts,
        // colours and positions and plain ones follow this style (mpv's "scale"); "universal" drops every tag and the file's styles ("strip"),
        // so every text subtitle is drawn with this one style
        val universal = com.lagradost.desktop.ui.fluent.Appearance.subtitleUniversal
        out += "sub-ass-override" to if (universal) "strip" else "scale"
        val alignment = style.alignment ?: CustomDecoder.SSA_ALIGNMENT_BOTTOM_CENTER
        out += "sub-ass-style-overrides" to if (universal) "Bold=${if (style.bold) -1 else 0},Italic=${if (style.italic) -1 else 0},Alignment=$alignment" else ""
        out += "sub-font" to (family ?: "sans-serif")
        out += "sub-font-size" to num((style.fixedTextSize ?: com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE) * SIZE_FACTOR)
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
        // libass has one "back" colour: it is the box and also the shadow, and mpv's sub-shadow-color is the same setting. Setting the transparent
        // background here used to wipe out the shadow colour set above, so the drop shadow (the default) was drawn invisible
        out += "sub-back-color" to if (boxed) argb(style.backgroundColor) else edge

        // ---- position: SSA alignment numbers (1-3 bottom, 4-6 middle, 7-9 top) and the distance from the edge
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
        // a font file the subtitle renderer has not read yet: the track starts again, which makes libass read the fonts folder
        if (fontsChanged) {
            fontsChanged = false
            val sid = player.getMpvPropertyString("sid")
            if (sid != null && sid != "no" && sid != "false") {
                player.setMpvProperty("sid", "no")
                player.setMpvProperty("sid", sid)
            }
        }
    }
}
