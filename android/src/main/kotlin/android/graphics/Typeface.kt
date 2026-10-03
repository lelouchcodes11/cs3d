package android.graphics

import android.content.res.AssetManager
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import java.io.File

class Typeface private constructor(
    @JvmField val skia: org.jetbrains.skia.Typeface,
    private val style: Int,
    private val weight: Int,
    @JvmField val familyName: String?,
) {
    fun getStyle(): Int = style
    fun isBold(): Boolean = (style and BOLD) != 0
    fun isItalic(): Boolean = (style and ITALIC) != 0
    fun getWeight(): Int = weight
    fun getSystemFontFamilyName(): String? = familyName

    override fun equals(other: Any?): Boolean =
        other is Typeface && other.style == style && other.weight == weight && other.familyName == familyName && other.skia.uniqueId == skia.uniqueId

    override fun hashCode(): Int = 31 * (31 * style + weight) + (familyName?.hashCode() ?: 0)

    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        private val fallbackFamilies = arrayOf("Segoe UI", "Arial", "Helvetica Neue", "Helvetica", "DejaVu Sans", "Roboto", "Noto Sans")

        private fun skiaStyle(style: Int, weight: Int = if ((style and BOLD) != 0) 700 else 400): FontStyle =
            FontStyle(weight, 5, if ((style and ITALIC) != 0) FontSlant.ITALIC else FontSlant.UPRIGHT)

        private fun match(family: String?, style: Int, weight: Int): org.jetbrains.skia.Typeface {
            val mgr = FontMgr.default
            val fs = skiaStyle(style, weight)
            val mapped = when (family?.lowercase()) {
                null, "", "sans-serif", "sans", "roboto", "google-sans", "product-sans" -> null
                "serif" -> "Times New Roman"
                "monospace" -> "Consolas"
                "sans-serif-condensed" -> "Arial Narrow"
                "sans-serif-medium" -> null
                else -> family
            }
            if (mapped != null) mgr.matchFamilyStyle(mapped, fs)?.let { return it }
            if (family?.lowercase() == "monospace") {
                for (f in arrayOf("Cascadia Mono", "Courier New", "DejaVu Sans Mono", "Menlo")) mgr.matchFamilyStyle(f, fs)?.let { return it }
            }
            if (family?.lowercase() == "serif") {
                for (f in arrayOf("Georgia", "DejaVu Serif", "Times")) mgr.matchFamilyStyle(f, fs)?.let { return it }
            }
            for (f in fallbackFamilies) mgr.matchFamilyStyle(f, fs)?.let { return it }
            return mgr.legacyMakeTypeface("", fs) ?: mgr.matchFamilyStyle(null, fs)
            ?: throw IllegalStateException("No fonts available")
        }

        private fun make(family: String?, style: Int, weight: Int = if ((style and BOLD) != 0) 700 else 400): Typeface =
            Typeface(match(family, style, weight), style, weight, family)

        @JvmField
        val DEFAULT: Typeface = make("sans-serif", NORMAL)

        @JvmField
        val DEFAULT_BOLD: Typeface = make("sans-serif", BOLD)

        @JvmField
        val SANS_SERIF: Typeface = make("sans-serif", NORMAL)

        @JvmField
        val SERIF: Typeface = make("serif", NORMAL)

        @JvmField
        val MONOSPACE: Typeface = make("monospace", NORMAL)

        @JvmStatic
        fun create(familyName: String?, style: Int): Typeface = make(familyName, style)

        @JvmStatic
        fun create(family: Typeface?, style: Int): Typeface {
            if (family == null) return defaultFromStyle(style)
            if (family.style == style) return family
            // Fonts loaded from files keep their face; emulate bold/italic by matching the family
            val name = family.familyName ?: family.skia.familyName
            return Typeface(match(name, style, if ((style and BOLD) != 0) 700 else family.weight), style, family.weight, name)
        }

        @JvmStatic
        fun create(family: Typeface?, weight: Int, italic: Boolean): Typeface {
            val style = (if (weight >= 600) BOLD else 0) or (if (italic) ITALIC else 0)
            val name = family?.familyName ?: family?.skia?.familyName
            return Typeface(match(name, style, weight), style, weight, name)
        }

        @JvmStatic
        fun defaultFromStyle(style: Int): Typeface = when (style) {
            BOLD -> DEFAULT_BOLD
            NORMAL -> DEFAULT
            else -> make("sans-serif", style)
        }

        @JvmStatic
        fun createFromAsset(mgr: AssetManager, path: String): Typeface {
            val bytes = mgr.open(path).use { it.readBytes() }
            return fromBytes(bytes, path)
        }

        @JvmStatic
        fun createFromFile(file: File?): Typeface {
            if (file == null) return DEFAULT
            return fromBytes(file.readBytes(), file.name)
        }

        @JvmStatic
        fun createFromFile(path: String?): Typeface = createFromFile(path?.let { File(it) })

        @JvmStatic
        fun fromBytes(bytes: ByteArray, name: String?): Typeface {
            val tf = FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) ?: return DEFAULT
            val style = (if (tf.isBold) BOLD else 0) or (if (tf.isItalic) ITALIC else 0)
            return Typeface(tf, style, tf.fontStyle.weight, tf.familyName.ifBlank { name })
        }
    }
}
