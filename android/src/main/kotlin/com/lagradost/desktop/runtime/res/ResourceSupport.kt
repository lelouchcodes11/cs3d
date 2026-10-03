package com.lagradost.desktop.runtime.res

import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import com.lagradost.desktop.runtime.ui.ThemeBridge
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

object ResourceSupport {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    // ------------------------------------------------------------------ plurals (CLDR cardinal rules subset)
    @JvmStatic
    fun pluralRule(locale: Locale, n: Int): String {
        val lang = locale.language
        return when (lang) {
            "ja", "zh", "ko", "vi", "th", "id", "in", "ms", "my", "lo", "km" -> "other"
            "fr", "pt" -> if (n == 0 || n == 1) "one" else if (n != 0 && n % 1000000 == 0) "many" else "other"
            "ru", "uk", "be", "hr", "sr", "bs" -> {
                val m10 = n % 10
                val m100 = n % 100
                when {
                    m10 == 1 && m100 != 11 -> "one"
                    m10 in 2..4 && m100 !in 12..14 -> "few"
                    else -> "many"
                }
            }
            "pl" -> {
                val m10 = n % 10
                val m100 = n % 100
                when {
                    n == 1 -> "one"
                    m10 in 2..4 && m100 !in 12..14 -> "few"
                    else -> "many"
                }
            }
            "cs", "sk" -> when (n) {
                1 -> "one"
                in 2..4 -> "few"
                else -> "other"
            }
            "ar", "arz", "ars", "apc" -> {
                val m100 = n % 100
                when {
                    n == 0 -> "zero"
                    n == 1 -> "one"
                    n == 2 -> "two"
                    m100 in 3..10 -> "few"
                    m100 in 11..99 -> "many"
                    else -> "other"
                }
            }
            "he", "iw" -> when (n) {
                1 -> "one"
                2 -> "two"
                else -> "other"
            }
            "ro" -> when {
                n == 1 -> "one"
                n == 0 || n % 100 in 1..19 -> "few"
                else -> "other"
            }
            "lt" -> {
                val m10 = n % 10
                val m100 = n % 100
                when {
                    m10 == 1 && m100 !in 11..19 -> "one"
                    m10 in 2..9 && m100 !in 11..19 -> "few"
                    else -> "other"
                }
            }
            "lv" -> {
                val m10 = n % 10
                val m100 = n % 100
                when {
                    m10 == 0 || m100 in 11..19 -> "zero"
                    m10 == 1 && m100 != 11 -> "one"
                    else -> "other"
                }
            }
            "hi", "bn", "fa", "gu", "kn", "mr", "am", "zu" -> if (n == 0 || n == 1) "one" else "other"
            else -> if (n == 1) "one" else "other"
        }
    }

    // ------------------------------------------------------------------ xml parsers

    @JvmStatic
    @Throws(java.io.IOException::class)
    fun openXmlParser(res: Resources, path: String): XmlResourceParser {
        val table = res.table ?: throw java.io.FileNotFoundException(path)
        val stream = table.openFile(path)
        return if (table.isBinaryXml(path)) {
            stream.use { AXmlResourceParser(it.readBytes()) }.also { it.resources = res }
        } else {
            TextXmlResourceParser(stream).also { it.resources = res }
        }
    }

    // ------------------------------------------------------------------ typed values

    @JvmStatic
    fun toTypedValue(res: Resources, v: ResValue, out: TypedValue) {
        out.string = null
        out.resourceId = 0
        when (v.kind) {
            ResValue.Kind.STRING -> {
                out.type = TypedValue.TYPE_STRING; out.string = v.value as CharSequence
            }
            ResValue.Kind.COLOR -> {
                out.type = TypedValue.TYPE_INT_COLOR_ARGB8; out.data = v.value as Int
            }
            ResValue.Kind.INTEGER, ResValue.Kind.ID -> {
                out.type = TypedValue.TYPE_INT_DEC; out.data = v.value as Int
            }
            ResValue.Kind.BOOL -> {
                out.type = TypedValue.TYPE_INT_BOOLEAN; out.data = if (v.value as Boolean) -1 else 0
            }
            ResValue.Kind.FLOAT -> {
                out.type = TypedValue.TYPE_FLOAT; out.data = java.lang.Float.floatToIntBits(v.value as Float)
            }
            ResValue.Kind.DIMEN -> {
                out.type = TypedValue.TYPE_DIMENSION; out.data = floatToComplex(v.value as Float, v.unit)
            }
            ResValue.Kind.FILE -> {
                out.type = TypedValue.TYPE_STRING; out.string = v.value as String
            }
            ResValue.Kind.REFERENCE -> {
                out.type = TypedValue.TYPE_REFERENCE; out.data = v.value as Int; out.resourceId = v.value
            }
            ResValue.Kind.ATTRIBUTE -> {
                out.type = TypedValue.TYPE_ATTRIBUTE; out.data = v.value as Int
            }
            else -> {
                out.type = TypedValue.TYPE_NULL; out.data = 0
            }
        }
    }

    /** Encode a float + unit as TypedValue complex data */
    @JvmStatic
    fun floatToComplex(value: Float, unit: Int): Int {
        // Same algorithm as android.util.TypedValue.floatToComplex (radix 23p0/16p7/8p15/0p23)
        fun create(mantissa: Int, radix: Int): Int =
            ((mantissa and TypedValue.COMPLEX_MANTISSA_MASK) shl TypedValue.COMPLEX_MANTISSA_SHIFT) or (radix shl TypedValue.COMPLEX_RADIX_SHIFT)
        val abs = kotlin.math.abs(value)
        val complex = when {
            value == value.toInt().toFloat() -> create(value.toInt(), 0)
            abs < 1f -> create(kotlin.math.round(value * (1 shl 23)).toInt(), 3)
            abs < (1 shl 8).toFloat() -> create(kotlin.math.round(value * (1 shl 15)).toInt(), 2)
            abs < (1 shl 16).toFloat() -> create(kotlin.math.round(value * (1 shl 7)).toInt(), 1)
            else -> create(kotlin.math.round(value).toInt(), 0)
        }
        return complex or (unit and TypedValue.COMPLEX_UNIT_MASK)
    }

    /** Parse a textual attribute value ("#fff", "@color/x", "16dp", "?attr/y", "12") */
    @JvmStatic
    fun parseTypedValue(res: Resources, raw: String?): TypedValue {
        val tv = TypedValue()
        if (raw == null) {
            tv.type = TypedValue.TYPE_NULL
            return tv
        }
        val s = raw.trim()
        when {
            s.startsWith("#") -> {
                tv.type = TypedValue.TYPE_INT_COLOR_ARGB8
                tv.data = parseColor(s)
            }
            s.startsWith("@") -> {
                val id = resolveReference(res, s)
                tv.type = TypedValue.TYPE_REFERENCE
                tv.data = id
                tv.resourceId = id
            }
            s.startsWith("?") -> {
                tv.type = TypedValue.TYPE_ATTRIBUTE
                tv.data = resolveAttributeName(res, s)
            }
            s == "true" || s == "false" -> {
                tv.type = TypedValue.TYPE_INT_BOOLEAN
                tv.data = if (s == "true") -1 else 0
            }
            parseDimension(s) != null -> {
                val (value, unit) = parseDimension(s)!!
                tv.type = TypedValue.TYPE_DIMENSION
                tv.data = floatToComplex(value, unit)
            }
            s.toIntOrNull() != null -> {
                tv.type = TypedValue.TYPE_INT_DEC
                tv.data = s.toInt()
            }
            s.startsWith("0x") && s.substring(2).toLongOrNull(16) != null -> {
                tv.type = TypedValue.TYPE_INT_HEX
                tv.data = s.substring(2).toLong(16).toInt()
            }
            s.toFloatOrNull() != null -> {
                tv.type = TypedValue.TYPE_FLOAT
                tv.data = java.lang.Float.floatToIntBits(s.toFloat())
            }
            else -> {
                tv.type = TypedValue.TYPE_STRING
                tv.string = raw
            }
        }
        return tv
    }

    @JvmStatic
    fun parseColor(s: String): Int {
        val hex = s.removePrefix("#")
        return when (hex.length) {
            3 -> (0xff shl 24) or (hexDigit(hex[0]) * 17 shl 16) or (hexDigit(hex[1]) * 17 shl 8) or (hexDigit(hex[2]) * 17)
            4 -> (hexDigit(hex[0]) * 17 shl 24) or (hexDigit(hex[1]) * 17 shl 16) or (hexDigit(hex[2]) * 17 shl 8) or (hexDigit(hex[3]) * 17)
            6 -> (0xff shl 24) or hex.toInt(16)
            8 -> hex.toLong(16).toInt()
            else -> Color.parseColor(s)
        }
    }

    private fun hexDigit(c: Char): Int = Character.digit(c, 16)

    @JvmStatic
    fun parseDimension(s: String): Pair<Float, Int>? {
        val m = Regex("^(-?[0-9]*\\.?[0-9]+)\\s*(dp|dip|sp|px|pt|in|mm)$").find(s) ?: return null
        val unit = when (m.groupValues[2]) {
            "dp", "dip" -> TypedValue.COMPLEX_UNIT_DIP
            "sp" -> TypedValue.COMPLEX_UNIT_SP
            "pt" -> TypedValue.COMPLEX_UNIT_PT
            "in" -> TypedValue.COMPLEX_UNIT_IN
            "mm" -> TypedValue.COMPLEX_UNIT_MM
            else -> TypedValue.COMPLEX_UNIT_PX
        }
        return m.groupValues[1].toFloat() to unit
    }

    private val attrIdCache = java.util.concurrent.ConcurrentHashMap<ResourceTable, java.util.concurrent.ConcurrentHashMap<String, Int>>()

    /** App attr id from the merged resource table. Zero is not cached: a theme built before the table exists must not stick. */
    @JvmStatic
    fun attrId(res: Resources, name: String): Int {
        val table = res.table ?: return 0
        attrIdCache[table]?.get(name)?.let { return it }
        val id = res.getIdentifier(name, "attr", null)
        if (id != 0) attrIdCache.computeIfAbsent(table) { java.util.concurrent.ConcurrentHashMap() }[name] = id
        return id
    }

    /** "@color/x", "@android:color/x", "@+id/x", "@12345" -> id */
    @JvmStatic
    fun resolveReference(res: Resources, s: String): Int {
        val ref = s.removePrefix("@").removePrefix("+")
        ref.toIntOrNull()?.let { return it }
        if (ref == "null") return 0
        return res.getIdentifier(ref, null, null)
    }

    @JvmStatic
    fun resolveAttributeName(res: Resources, s: String): Int {
        val ref = s.removePrefix("?")
        ref.toIntOrNull()?.let { return it }
        val name = ref.substringAfterLast('/').substringAfterLast(':')
        val framework = ref.startsWith("android:")
        return when (name) {
            "textColorPrimary" -> FrameworkResources.ATTR_TEXT_COLOR_PRIMARY
            "textColorSecondary" -> FrameworkResources.ATTR_TEXT_COLOR_SECONDARY
            "colorBackground", "windowBackground" -> FrameworkResources.ATTR_COLOR_BACKGROUND
            "colorPrimary" -> if (framework) FrameworkResources.ATTR_COLOR_PRIMARY else attrId(res, "colorPrimary")
            "colorAccent", "colorSecondary" -> FrameworkResources.ATTR_COLOR_ACCENT
            "colorPrimaryDark" -> FrameworkResources.ATTR_COLOR_PRIMARY_DARK
            "colorOnPrimary" -> attrId(res, "colorOnPrimary")
            "colorSurface" -> attrId(res, "colorSurface")
            "colorOnSurface" -> attrId(res, "colorOnSurface")
            "selectableItemBackground" -> FrameworkResources.ATTR_SELECTABLE_ITEM_BACKGROUND
            "selectableItemBackgroundBorderless" -> FrameworkResources.ATTR_SELECTABLE_ITEM_BACKGROUND_BORDERLESS
            else -> res.getIdentifier(name, "attr", null).takeIf { it != 0 || !framework } ?: FrameworkResources.attrId(name)
        }
    }

    /** Resolve a typed value to a color int, following references and theme attributes */
    @JvmStatic
    fun colorOf(res: Resources, tv: TypedValue, theme: Resources.Theme?): Int? {
        return when (tv.type) {
            in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT, TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> tv.data
            TypedValue.TYPE_REFERENCE -> try {
                res.getColorStateList(tv.data, theme).defaultColor
            } catch (e: Exception) {
                null
            }
            TypedValue.TYPE_ATTRIBUTE -> {
                val t = TypedValue()
                if ((theme ?: res.newTheme()).resolveAttribute(tv.data, t, true)) colorOf(res, t, theme) else null
            }
            TypedValue.TYPE_STRING -> tv.string?.toString()?.let { s -> if (s.startsWith("#")) parseColor(s) else null }
            else -> null
        }
    }

    @JvmStatic
    fun colorStateListOf(res: Resources, tv: TypedValue, theme: Resources.Theme?): ColorStateList? {
        if (tv.type == TypedValue.TYPE_REFERENCE) {
            return try {
                res.getColorStateList(tv.data, theme)
            } catch (e: Exception) {
                null
            }
        }
        return colorOf(res, tv, theme)?.let { ColorStateList.valueOf(it) }
    }

    @JvmStatic
    fun dimensionOf(res: Resources, tv: TypedValue): Float? = when (tv.type) {
        TypedValue.TYPE_DIMENSION -> TypedValue.complexToDimension(tv.data, res.displayMetrics)
        TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> tv.data.toFloat()
        TypedValue.TYPE_FLOAT -> java.lang.Float.intBitsToFloat(tv.data)
        TypedValue.TYPE_REFERENCE -> try {
            res.getDimension(tv.data)
        } catch (e: Exception) {
            null
        }
        TypedValue.TYPE_STRING -> tv.string?.toString()?.let { s -> parseDimension(s)?.let { TypedValue.applyDimension(it.second, it.first, res.displayMetrics) } }
        else -> null
    }

    private const val TOOLS_NS = "http://schemas.android.com/tools"

    /** tools: attributes are design-time only (aapt strips them), never runtime values */
    @JvmStatic
    fun isToolsAttribute(set: AttributeSet, index: Int): Boolean {
        if (set.getAttributeName(index).startsWith("tools:")) return true
        val parser = (set as? org.xmlpull.v1.XmlPullParser) ?: (set as? android.util.Xml.XmlPullAttributes)?.mParser ?: return false
        return runCatching { parser.getAttributeNamespace(index) == TOOLS_NS }.getOrDefault(false)
    }

    /** Typed value of an attribute of an AttributeSet, by local name (any namespace except tools) */
    @JvmStatic
    fun attr(res: Resources, set: AttributeSet, name: String): TypedValue? {
        if (set is AXmlResourceParser) {
            val i = set.findByName(name)
            if (i < 0) return null
            return set.getTypedValue(i)
        }
        var raw = set.getAttributeValue(ANDROID_NS, name)
        if (raw == null) {
            for (i in 0 until set.attributeCount) {
                if (isToolsAttribute(set, i)) continue
                if (set.getAttributeName(i) == name || set.getAttributeName(i).substringAfter(':') == name) {
                    raw = set.getAttributeValue(i)
                    break
                }
            }
        }
        return raw?.let { parseTypedValue(res, it) }
    }

    private val GRAVITY = mapOf(
        "top" to 0x30, "bottom" to 0x50, "left" to 0x03, "right" to 0x05, "center_vertical" to 0x10, "fill_vertical" to 0x70,
        "center_horizontal" to 0x01, "fill_horizontal" to 0x07, "center" to 0x11, "fill" to 0x77, "clip_vertical" to 0x80,
        "clip_horizontal" to 0x08, "start" to 0x00800003, "end" to 0x00800005,
    )
    private val LAYOUT_SIZE = mapOf("match_parent" to -1, "fill_parent" to -1, "wrap_content" to -2)

    /** Enum and flag values of framework attributes (from the platform's attrs.xml) */
    private val FRAMEWORK_ENUMS: Map<String, Map<String, Int>> = mapOf(
        "orientation" to mapOf("horizontal" to 0, "vertical" to 1),
        "visibility" to mapOf("visible" to 0, "invisible" to 1, "gone" to 2),
        "gravity" to GRAVITY, "layout_gravity" to GRAVITY, "foregroundGravity" to GRAVITY,
        "layout_width" to LAYOUT_SIZE, "layout_height" to LAYOUT_SIZE,
        "textStyle" to mapOf("normal" to 0, "bold" to 1, "italic" to 2),
        "typeface" to mapOf("normal" to 0, "sans" to 1, "serif" to 2, "monospace" to 3),
        "ellipsize" to mapOf("none" to 0, "start" to 1, "middle" to 2, "end" to 3, "marquee" to 4),
        "scaleType" to mapOf("matrix" to 0, "fitXY" to 1, "fitStart" to 2, "fitCenter" to 3, "fitEnd" to 4, "center" to 5, "centerCrop" to 6, "centerInside" to 7),
        "textAlignment" to mapOf("inherit" to 0, "gravity" to 1, "textStart" to 2, "textEnd" to 3, "center" to 4, "viewStart" to 5, "viewEnd" to 6),
        "layoutDirection" to mapOf("ltr" to 0, "rtl" to 1, "inherit" to 2, "locale" to 3),
        "textDirection" to mapOf("inherit" to 0, "firstStrong" to 1, "anyRtl" to 2, "ltr" to 3, "rtl" to 4, "locale" to 5),
        "importantForAccessibility" to mapOf("auto" to 0, "yes" to 1, "no" to 2, "noHideDescendants" to 4),
        "descendantFocusability" to mapOf("beforeDescendants" to 0, "afterDescendants" to 1, "blocksDescendants" to 2),
        "choiceMode" to mapOf("none" to 0, "singleChoice" to 1, "multipleChoice" to 2, "multipleChoiceModal" to 3),
        "scrollbars" to mapOf("none" to 0, "horizontal" to 0x100, "vertical" to 0x200),
        "overScrollMode" to mapOf("always" to 0, "ifContentScrolls" to 1, "never" to 2),
        "focusable" to mapOf("false" to 0, "true" to 1, "auto" to 16),
        "tintMode" to mapOf("src_over" to 3, "src_in" to 5, "src_atop" to 9, "multiply" to 14, "screen" to 15, "add" to 16),
        "backgroundTintMode" to mapOf("src_over" to 3, "src_in" to 5, "src_atop" to 9, "multiply" to 14, "screen" to 15, "add" to 16),
        "stretchMode" to mapOf("none" to 0, "spacingWidth" to 1, "columnWidth" to 2, "spacingWidthUniform" to 3),
        "numColumns" to mapOf("auto_fit" to -1),
        "imeOptions" to mapOf("normal" to 0, "actionUnspecified" to 0, "actionNone" to 1, "actionGo" to 2, "actionSearch" to 3, "actionSend" to 4, "actionNext" to 5, "actionDone" to 6, "actionPrevious" to 7, "flagNoExtractUi" to 0x10000000),
    )

    /** Numeric value of an enum / flag name of the attribute [attrId] ("match_parent", "packed", "top|start") */
    @JvmStatic
    fun enumOrFlagValue(res: Resources, attrId: Int, value: CharSequence?): Int? {
        val s = value?.toString()?.trim() ?: return null
        if (s.isEmpty()) return null
        val c = s[0]
        if (c.isDigit() || c == '-' || c == '#' || c == '@' || c == '?') return null
        val framework = FrameworkResources.attrName(attrId)
        val table: Map<String, Int> = if (framework != null) {
            FRAMEWORK_ENUMS[framework] ?: return null
        } else {
            val app = (com.lagradost.desktop.runtime.AndroidRuntime.applicationContext?.resources?.table ?: res.table) as? IndexedResourceTable ?: return null
            val name = app.nameOf(attrId)?.takeIf { it.startsWith("attr/") }?.removePrefix("attr/") ?: return null
            app.attrValues(name) ?: return null
        }
        var result = 0
        for (part in s.split('|')) result = result or (table[part.trim()] ?: return null)
        return result
    }

    @JvmStatic
    fun attributeFromSet(res: Resources, set: AttributeSet, attrId: Int): TypedValue? {
        if (set is AXmlResourceParser) {
            val i = set.findByResourceId(attrId)
            if (i >= 0) return set.getTypedValue(i)
        }
        // by name: framework attributes, the app's attributes (code libraries), then the set's own resources
        val name = FrameworkResources.attrName(attrId)
            ?: (com.lagradost.desktop.runtime.AndroidRuntime.applicationContext?.resources?.table as? IndexedResourceTable)?.nameOf(attrId)?.takeIf { it.startsWith("attr/") }?.removePrefix("attr/")
            ?: try {
                res.getResourceEntryName(attrId)
            } catch (e: Exception) {
                return null
            }
        return attr(res, set, name)
    }

    // ------------------------------------------------------------------ themes

    @JvmStatic
    fun applyDefaultTheme(theme: Resources.Theme) = ThemeBridge.populate(theme)

    @JvmStatic
    fun applyStyle(theme: Resources.Theme, resId: Int, force: Boolean) {
        val res = theme.resources
        val table = res.table ?: return
        val indexed = table as? IndexedResourceTable
        for ((attr, v) in table.getStyle(resId, res.configuration)) {
            val tv = TypedValue()
            toTypedValue(res, v, tv)
            theme.putAttribute(attr, tv)
            // the same attribute under the ids compat and extension code resolve names to
            val name = indexed?.nameOf(attr)?.takeIf { it.startsWith("attr/") }?.removePrefix("attr/") ?: continue
            val alias = resolveAttributeName(res, name)
            if (alias != 0 && alias != attr) theme.putAttribute(alias, tv)
        }
    }

    // ------------------------------------------------------------------ drawables, colors, fonts

    @JvmStatic
    fun loadColorStateList(res: Resources, path: String, theme: Resources.Theme?): ColorStateList? {
        if (!path.endsWith(".xml")) return null
        return try {
            openXmlParser(res, path).use { parser -> DrawableInflater.inflateColorStateList(res, parser, theme) }
        } catch (t: Throwable) {
            android.util.Log.w("Resources", "Failed to load color state list $path: ${t.message}")
            null
        }
    }

    @JvmStatic
    fun loadDrawable(res: Resources, id: Int, path: String, theme: Resources.Theme?): Drawable? {
        if (path.startsWith("@android:")) return DrawableInflater.frameworkDrawable(path)
        return try {
            if (path.endsWith(".xml")) {
                openXmlParser(res, path).use { parser -> DrawableInflater.inflate(res, parser, theme) }
            } else {
                val table = res.table ?: return null
                val bytes = table.openFile(path).use { it.readBytes() }
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                android.graphics.drawable.BitmapDrawable(res, bitmap)
            }
        } catch (t: Throwable) {
            android.util.Log.w("Resources", "Failed to load drawable $path: ${t.message}")
            null
        }
    }

    @JvmStatic
    fun loadFont(res: Resources, path: String): Typeface? {
        val table = res.table ?: return null
        return try {
            if (path.endsWith(".xml")) {
                // font family xml: use the first font entry
                openXmlParser(res, path).use { parser ->
                    var t = parser.next()
                    while (t != XmlPullParser.END_DOCUMENT) {
                        if (t == XmlPullParser.START_TAG && parser.name == "font") {
                            val fontRef = attr(res, parser, "font")
                            if (fontRef?.type == TypedValue.TYPE_REFERENCE) return res.getFont(fontRef.data)
                        }
                        t = parser.next()
                    }
                    null
                }
            } else {
                Typeface.fromBytes(table.openFile(path).use { it.readBytes() }, File(path).nameWithoutExtension)
            }
        } catch (t: Throwable) {
            null
        }
    }

    @JvmStatic
    fun loadApkResources(path: String): ResourceTable? = ArscResourceTable.load(File(path))

    /** The Resources an AttributeSet originates from (extension layouts), if known */
    @JvmStatic
    fun resourcesOf(set: AttributeSet?): Resources? = when (set) {
        is AXmlResourceParser -> set.resources
        is TextXmlResourceParser -> set.resources
        else -> null
    }
}

/** XmlResourceParser over plain text XML (application resources) */
class TextXmlResourceParser(input: InputStream) : KXmlParser(), XmlResourceParser {
    /** Resources this parser was opened from */
    @JvmField
    var resources: Resources? = null

    init {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(InputStreamReader(input, Charsets.UTF_8))
    }

    override fun getAttributeValue(namespace: String?, name: String?): String? {
        val v = super.getAttributeValue(namespace, name)
        if (v != null || namespace == null) return v
        // any namespace (app vs res-auto), but never tools: (design-time only)
        for (i in 0 until attributeCount) {
            if (getAttributeName(i) == name && getAttributeNamespace(i) != "http://schemas.android.com/tools") return getAttributeValue(i)
        }
        return null
    }

    override fun getAttributeResourceValue(namespace: String?, attribute: String?, defaultValue: Int): Int =
        resolveResource(getAttributeValue(namespace, attribute), defaultValue)

    override fun getAttributeResourceValue(index: Int, defaultValue: Int): Int =
        resolveResource(getAttributeValue(index), defaultValue)

    private fun resolveResource(raw: String?, defaultValue: Int): Int {
        if (raw.isNullOrEmpty()) return defaultValue
        if (raw[0] == '@') {
            val id = resources?.getIdentifier(raw.removePrefix("@").removePrefix("+"), null, null) ?: 0
            return if (id != 0) id else defaultValue
        }
        return raw.toIntOrNull() ?: defaultValue
    }

    override fun close() {}
}
