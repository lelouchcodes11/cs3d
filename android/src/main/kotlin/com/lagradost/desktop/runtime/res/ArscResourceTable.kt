package com.lagradost.desktop.runtime.res

import android.content.res.Configuration
import android.util.TypedValue
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Resource table parsed from an APK/.cs3 resources.arsc, with files served from the archive.
 * Supports the value types used by extensions: strings, colors, dimensions, integers, booleans,
 * arrays, plurals, styles, references and file based resources (layouts, drawables, xml).
 */
class ArscResourceTable private constructor(private val archive: File) : ResourceTable {
    private class Entry(val config: Config, val value: ResValue)

    /** Resource configuration (subset used for selection) */
    private class Config(
        val language: String,
        val country: String,
        val night: Int, // 0 any, 1 notnight, 2 night
        val sdk: Int,
        val density: Int,
        val orientation: Int,
        val specificity: Int,
    )

    private val entries = HashMap<Int, MutableList<Entry>>()
    private val names = HashMap<Int, String>()
    private val ids = HashMap<String, Int>()
    private var packageName = "app"
    private val files: Map<String, ByteArray>

    companion object {
        private const val RES_STRING_POOL_TYPE = 0x0001
        private const val RES_TABLE_TYPE = 0x0002
        private const val RES_TABLE_PACKAGE_TYPE = 0x0200
        private const val RES_TABLE_TYPE_TYPE = 0x0201
        private const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
        private const val NO_ENTRY = -1
        private const val FLAG_COMPLEX = 0x0001
        private const val FLAG_COMPACT = 0x0008
        private const val TYPE_FLAG_SPARSE = 0x01
        private const val TYPE_FLAG_OFFSET16 = 0x02
        private const val ATTR_OTHER = 0x01000004
        private const val ATTR_ZERO = 0x01000005
        private const val ATTR_ONE = 0x01000006
        private const val ATTR_TWO = 0x01000007
        private const val ATTR_FEW = 0x01000008
        private const val ATTR_MANY = 0x01000009

        @JvmStatic
        fun load(file: File): ArscResourceTable? {
            val t = ArscResourceTable(file)
            return if (t.entries.isEmpty() && t.files.isEmpty()) null else t
        }
    }

    init {
        val map = HashMap<String, ByteArray>()
        var arsc: ByteArray? = null
        ZipFile(archive).use { zip ->
            for (e in zip.entries()) {
                if (e.isDirectory) continue
                val n = e.name
                if (n == "resources.arsc") arsc = zip.getInputStream(e).use { it.readBytes() }
                else if (n.startsWith("res/") || n.startsWith("assets/")) map[n] = zip.getInputStream(e).use { it.readBytes() }
            }
        }
        files = map
        arsc?.let { parse(it) }
    }

    private fun parse(data: ByteArray) {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if ((buf.getShort(0).toInt() and 0xffff) != RES_TABLE_TYPE) return
        val headerSize = buf.getShort(2).toInt() and 0xffff
        var globalStrings: AXmlResourceParser.StringPool? = null
        var p = headerSize
        while (p < data.size) {
            val type = buf.getShort(p).toInt() and 0xffff
            val size = buf.getInt(p + 4)
            if (size <= 0) break
            when (type) {
                RES_STRING_POOL_TYPE -> globalStrings = AXmlResourceParser.StringPool(buf, p)
                RES_TABLE_PACKAGE_TYPE -> parsePackage(buf, p, size, globalStrings)
            }
            p += size
        }
    }

    private fun parsePackage(buf: ByteBuffer, start: Int, size: Int, strings: AXmlResourceParser.StringPool?) {
        val headerSize = buf.getShort(start + 2).toInt() and 0xffff
        val pkgId = buf.getInt(start + 8)
        val nameChars = CharArray(128) { buf.getChar(start + 12 + it * 2) }
        packageName = String(nameChars).substringBefore('\u0000')
        val typeStringsOffset = buf.getInt(start + 12 + 256)
        val keyStringsOffset = buf.getInt(start + 12 + 256 + 8)
        val typeStrings = AXmlResourceParser.StringPool(buf, start + typeStringsOffset)
        val keyStrings = AXmlResourceParser.StringPool(buf, start + keyStringsOffset)
        var p = start + headerSize
        val end = start + size
        while (p < end) {
            val type = buf.getShort(p).toInt() and 0xffff
            val chunkSize = buf.getInt(p + 4)
            if (chunkSize <= 0) break
            if (type == RES_TABLE_TYPE_TYPE) parseType(buf, p, pkgId, typeStrings, keyStrings, strings)
            p += chunkSize
        }
    }

    private fun readConfig(buf: ByteBuffer, p: Int): Config {
        val size = buf.getInt(p)
        fun byteAt(off: Int): Int = if (off < size) buf.get(p + off).toInt() and 0xff else 0
        fun shortAt(off: Int): Int = if (off + 1 < size) buf.getShort(p + off).toInt() and 0xffff else 0
        val lang = if (byteAt(8) != 0) String(charArrayOf(byteAt(8).toChar(), byteAt(9).toChar())) else ""
        val country = if (byteAt(10) != 0) String(charArrayOf(byteAt(10).toChar(), byteAt(11).toChar())) else ""
        val orientation = byteAt(12)
        val density = shortAt(14)
        val sdk = shortAt(24)
        // screenConfig: screenLayout(28) uiMode(29) smallestScreenWidthDp(30)
        val uiMode = byteAt(29)
        val night = when (uiMode and 0x30) {
            0x10 -> 1
            0x20 -> 2
            else -> 0
        }
        var specificity = 0
        if (lang.isNotEmpty()) specificity += 100
        if (country.isNotEmpty()) specificity += 50
        if (night != 0) specificity += 20
        if (orientation != 0) specificity += 10
        if (sdk != 0) specificity += 1
        return Config(lang, country, night, sdk, density, orientation, specificity)
    }

    private fun parseType(
        buf: ByteBuffer, start: Int, pkgId: Int,
        typeStrings: AXmlResourceParser.StringPool, keyStrings: AXmlResourceParser.StringPool, strings: AXmlResourceParser.StringPool?
    ) {
        val headerSize = buf.getShort(start + 2).toInt() and 0xffff
        val typeId = buf.get(start + 8).toInt() and 0xff
        val flags = buf.get(start + 9).toInt() and 0xff
        val entryCount = buf.getInt(start + 12)
        val entriesStart = buf.getInt(start + 16)
        val config = readConfig(buf, start + 20)
        val typeName = typeStrings.get(typeId - 1) ?: return
        val offsetsStart = start + headerSize
        val sparse = (flags and TYPE_FLAG_SPARSE) != 0
        val offset16 = (flags and TYPE_FLAG_OFFSET16) != 0
        for (i in 0 until entryCount) {
            val entryIndex: Int
            val offset: Int
            if (sparse) {
                entryIndex = buf.getShort(offsetsStart + i * 4).toInt() and 0xffff
                offset = (buf.getShort(offsetsStart + i * 4 + 2).toInt() and 0xffff) * 4
            } else if (offset16) {
                entryIndex = i
                val o = buf.getShort(offsetsStart + i * 2).toInt() and 0xffff
                if (o == 0xffff) continue
                offset = o * 4
            } else {
                entryIndex = i
                offset = buf.getInt(offsetsStart + i * 4)
                if (offset == NO_ENTRY) continue
            }
            val e = start + entriesStart + offset
            val resId = (pkgId shl 24) or (typeId shl 16) or entryIndex
            val entrySize = buf.getShort(e).toInt() and 0xffff
            val entryFlags = buf.getShort(e + 2).toInt() and 0xffff
            val value: ResValue?
            val keyIndex: Int
            if ((entryFlags and FLAG_COMPACT) != 0) {
                keyIndex = entrySize
                val dataType = (entryFlags shr 8) and 0xff
                value = convert(typeName, dataType, buf.getInt(e + 4), strings)
            } else {
                keyIndex = buf.getInt(e + 4)
                value = if ((entryFlags and FLAG_COMPLEX) != 0) {
                    val count = buf.getInt(e + 12)
                    var m = e + entrySize
                    val items = ArrayList<Pair<Int, ResValue?>>()
                    repeat(count) {
                        val nameId = buf.getInt(m)
                        val dataType = buf.get(m + 7).toInt() and 0xff
                        val data = buf.getInt(m + 8)
                        items.add(nameId to convert(typeName, dataType, data, strings))
                        m += 12
                    }
                    complex(typeName, items)
                } else {
                    val dataType = buf.get(e + entrySize + 3).toInt() and 0xff
                    val data = buf.getInt(e + entrySize + 4)
                    convert(typeName, dataType, data, strings)
                }
            }
            val key = keyStrings.get(keyIndex) ?: continue
            names[resId] = "$packageName:$typeName/$key"
            ids["$typeName/$key"] = resId
            if (value != null) entries.getOrPut(resId) { ArrayList() }.add(Entry(config, value))
        }
    }

    private fun complex(typeName: String, items: List<Pair<Int, ResValue?>>): ResValue = when (typeName) {
        "plurals" -> {
            val map = LinkedHashMap<String, CharSequence>()
            for ((k, v) in items) {
                val q = when (k) {
                    ATTR_ZERO -> "zero"
                    ATTR_ONE -> "one"
                    ATTR_TWO -> "two"
                    ATTR_FEW -> "few"
                    ATTR_MANY -> "many"
                    else -> "other"
                }
                (v?.value as? CharSequence)?.let { map[q] = it }
            }
            ResValue(ResValue.Kind.PLURALS, map)
        }
        "array", "string-array", "integer-array", "array-string" -> {
            val values = items.sortedBy { it.first and 0xffff }.map { it.second }
            if (values.all { it?.kind == ResValue.Kind.INTEGER }) ResValue(ResValue.Kind.INT_ARRAY, values.map { it!!.value as Int }.toIntArray())
            else ResValue(ResValue.Kind.STRING_ARRAY, values.map { v ->
                when (v?.kind) {
                    ResValue.Kind.STRING -> v.value as CharSequence
                    null -> null
                    else -> v.value.toString()
                }
            }.toTypedArray())
        }
        else -> ResValue(ResValue.Kind.STYLE, items.filter { it.second != null }.associate { it.first to it.second!! })
    }

    private fun convert(typeName: String, dataType: Int, data: Int, strings: AXmlResourceParser.StringPool?): ResValue? = when (dataType) {
        TypedValue.TYPE_NULL -> null
        TypedValue.TYPE_REFERENCE, 0x07 -> if (data == 0) null else ResValue.reference(data)
        TypedValue.TYPE_ATTRIBUTE, 0x08 -> ResValue(ResValue.Kind.ATTRIBUTE, data)
        TypedValue.TYPE_STRING -> {
            val s = strings?.get(data) ?: ""
            if (s.startsWith("res/") && typeName != "string") ResValue.file(s) else ResValue.string(s)
        }
        TypedValue.TYPE_FLOAT -> ResValue(ResValue.Kind.FLOAT, java.lang.Float.intBitsToFloat(data))
        TypedValue.TYPE_DIMENSION -> ResValue.dimen(TypedValue.complexToFloat(data), data and TypedValue.COMPLEX_UNIT_MASK)
        TypedValue.TYPE_FRACTION -> ResValue(ResValue.Kind.FRACTION, TypedValue.complexToFloat(data))
        TypedValue.TYPE_INT_BOOLEAN -> ResValue.bool(data != 0)
        in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> ResValue.color(data)
        in TypedValue.TYPE_FIRST_INT..TypedValue.TYPE_LAST_INT -> if (typeName == "id") ResValue(ResValue.Kind.ID, data) else ResValue.integer(data)
        else -> null
    }

    override fun get(id: Int, config: Configuration): ResValue? {
        val list = entries[id] ?: return null
        if (list.size == 1) return list[0].value
        val lang = config.locale?.language ?: ""
        val country = config.locale?.country ?: ""
        val night = if (config.isNightModeActive) 2 else 1
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val candidates = list.filter { e ->
            val c = e.config
            (c.language.isEmpty() || c.language == lang) &&
                    (c.country.isEmpty() || c.country == country) &&
                    (c.night == 0 || c.night == night) &&
                    (c.sdk <= 34) &&
                    (c.orientation == 0 || (c.orientation == 2) == landscape)
        }
        return (candidates.maxByOrNull { it.config.specificity } ?: list.minByOrNull { it.config.specificity })?.value
    }

    override fun getIdentifier(type: String, name: String): Int = ids["$type/$name"] ?: 0
    override fun getResourceName(id: Int): String? = names[id]
    override fun getPackageName(): String = packageName

    override fun openFile(path: String): InputStream {
        val bytes = files[path] ?: throw FileNotFoundException(path)
        return ByteArrayInputStream(bytes)
    }

    override fun isBinaryXml(path: String): Boolean = path.endsWith(".xml")

    @Suppress("UNCHECKED_CAST")
    override fun getStyle(styleId: Int, config: Configuration): Map<Int, ResValue> {
        val v = get(styleId, config) ?: return emptyMap()
        return if (v.kind == ResValue.Kind.STYLE) v.value as Map<Int, ResValue> else emptyMap()
    }

    override fun openAsset(name: String): InputStream {
        val bytes = files["assets/${name.removePrefix("/")}"] ?: throw FileNotFoundException(name)
        return ByteArrayInputStream(bytes)
    }

    override fun listAssets(path: String): Array<String> {
        val prefix = "assets/" + path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        return files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().toTypedArray()
    }
}
