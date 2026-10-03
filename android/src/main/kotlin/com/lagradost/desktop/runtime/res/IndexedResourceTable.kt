package com.lagradost.desktop.runtime.res

import android.content.res.Configuration
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resource table for the application's resources: the upstream Android res/ folder merged over the
 * Android libraries' resources (Material, AppCompat ...), packaged as plain files under [root] with
 * an index (id type name [path], value files per folder) generated at build time. Values are parsed
 * lazily per folder and resolved through the folders matching the configuration like Android
 * (locale, screen size, orientation, night mode, API level).
 */
class IndexedResourceTable(
    private val loader: ClassLoader,
    private val root: String = "android-res",
    private val packageName: String = "com.lagradost.cloudstream3",
) : ResourceTable {
    private class Entry(val type: String, val name: String, val path: String?)

    private val entries = HashMap<Int, Entry>()
    private val ids = HashMap<String, Int>()
    private val folders = ConcurrentHashMap<String, Map<String, Element>>()
    private val valueFiles = HashMap<String, List<String>>()
    private val valueFolders: List<String>
    private val cache = ConcurrentHashMap<Long, ResValue>()
    private val styleCache = ConcurrentHashMap<Long, Map<Int, ResValue>>()

    init {
        val index = loader.getResourceAsStream("$root/index.txt") ?: throw FileNotFoundException("$root/index.txt")
        index.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split(' ')
                when (parts[0]) {
                    "#values" -> valueFiles[parts[1]] = parts.drop(2)
                    "#fwattr" -> FrameworkResources.attrId(parts[1])
                    else -> {
                        val id = parts[0].toLong(16).toInt()
                        val e = Entry(parts[1], parts[2], parts.getOrNull(3))
                        entries[id] = e
                        ids["${e.type}/${e.name}"] = id
                    }
                }
            }
        }
        valueFolders = if (valueFiles.isNotEmpty()) valueFiles.keys.toList() else listOf("values")
    }

    // ------------------------------------------------------------------ configuration matching

    /** Qualifiers of a values folder, null when one of them can't match this platform */
    private class Qualifiers(
        val language: String?, val region: String?, val night: Boolean?, val version: Int,
        val orientation: Int, val sw: Int, val w: Int, val h: Int,
    )

    private val qualifierCache = ConcurrentHashMap<String, Any>()

    private fun qualifiers(folder: String): Qualifiers? {
        val cached = qualifierCache.getOrPut(folder) { parseQualifiers(folder) ?: Unit }
        return cached as? Qualifiers
    }

    private fun parseQualifiers(folder: String): Qualifiers? {
        var language: String? = null
        var region: String? = null
        var night: Boolean? = null
        var version = 0
        var orientation = 0
        var sw = 0
        var w = 0
        var h = 0
        val parts = folder.split('-').drop(1)
        var i = 0
        while (i < parts.size) {
            val q = parts[i]
            when {
                q.startsWith("b+") -> {
                    val sub = q.removePrefix("b+").split('+')
                    language = sub[0]
                    region = sub.getOrNull(1)
                }
                q.matches(Regex("[a-z]{2,3}")) && language == null && q !in setOf("land", "port", "night", "notnight", "ldltr", "ldrtl", "car", "desk", "watch", "television", "appliance", "vrheadset") -> {
                    language = q
                    if (i + 1 < parts.size && parts[i + 1].matches(Regex("r[A-Z]{2}"))) {
                        region = parts[i + 1].drop(1)
                        i++
                    }
                }
                q == "night" -> night = true
                q == "notnight" -> night = false
                q == "land" -> orientation = Configuration.ORIENTATION_LANDSCAPE
                q == "port" -> orientation = Configuration.ORIENTATION_PORTRAIT
                q == "ldltr" -> {}
                q.matches(Regex("v\\d+")) -> version = q.drop(1).toInt()
                q.matches(Regex("sw\\d+dp")) -> sw = q.drop(2).dropLast(2).toInt()
                q.matches(Regex("w\\d+dp")) -> w = q.drop(1).dropLast(2).toInt()
                q.matches(Regex("h\\d+dp")) -> h = q.drop(1).dropLast(2).toInt()
                q in setOf("normal", "long", "notlong", "notround", "nokeys", "finger", "keyshidden") -> {}
                else -> return null // television, watch, car, xlarge, ldrtl, mcc, round ... never match here
            }
            i++
        }
        return Qualifiers(language, region, night, version, orientation, sw, w, h)
    }

    private fun matches(q: Qualifiers, config: Configuration): Boolean {
        val locale = config.locale
        if (q.language != null) {
            val lang = locale?.language ?: return false
            val aliases = when (lang) {
                "he", "iw" -> setOf("iw", "he")
                "id", "in" -> setOf("in", "id")
                "yi", "ji" -> setOf("ji", "yi")
                else -> setOf(lang)
            }
            if (q.language !in aliases) return false
            if (q.region != null && q.region != locale.country) return false
        }
        if (q.night != null && q.night != config.isNightModeActive()) return false
        if (q.version > 34) return false
        if (q.orientation != 0 && q.orientation != config.orientation) return false
        if (q.sw > 0 && config.smallestScreenWidthDp < q.sw) return false
        if (q.w > 0 && config.screenWidthDp < q.w) return false
        if (q.h > 0 && config.screenHeightDp < q.h) return false
        return true
    }

    private val orderCache = ConcurrentHashMap<String, List<String>>()

    /** The values folders matching [config], best first (Android's qualifier precedence) */
    private fun foldersFor(config: Configuration): List<String> {
        val key = "${config.locale?.toLanguageTag()}|${config.isNightModeActive()}|${config.orientation}|${config.smallestScreenWidthDp}|${config.screenWidthDp}|${config.screenHeightDp}"
        return orderCache.getOrPut(key) {
            valueFolders.mapNotNull { f -> qualifiers(f)?.takeIf { matches(it, config) }?.let { f to it } }
                .sortedWith(
                    compareByDescending<Pair<String, Qualifiers>> { if (it.second.language != null) 1 else 0 }
                        .thenByDescending { if (it.second.region != null) 1 else 0 }
                        .thenByDescending { it.second.sw }
                        .thenByDescending { it.second.w }
                        .thenByDescending { it.second.h }
                        .thenByDescending { if (it.second.orientation != 0) 1 else 0 }
                        .thenByDescending { if (it.second.night != null) 1 else 0 }
                        .thenByDescending { it.second.version }
                ).map { it.first }
        }
    }

    private fun folder(name: String): Map<String, Element> = folders.getOrPut(name) {
        val map = HashMap<String, Element>()
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        val list = valueFiles[name] ?: listOf("strings.xml", "arrays.xml", "colors.xml", "dimens.xml", "styles.xml", "attrs.xml")
        // later files override earlier ones: the libraries are listed before the app
        for (file in list) {
            val stream = loader.getResourceAsStream("$root/$name/$file") ?: continue
            try {
                val doc = stream.use { dbf.newDocumentBuilder().parse(it) }
                val nodes = doc.documentElement.childNodes
                for (i in 0 until nodes.length) {
                    val e = nodes.item(i) as? Element ?: continue
                    val type = when (e.tagName) {
                        "string" -> "string"
                        "plurals" -> "plurals"
                        "string-array", "integer-array", "array" -> "array"
                        "color" -> "color"
                        "dimen" -> "dimen"
                        "integer" -> "integer"
                        "bool" -> "bool"
                        "fraction" -> "fraction"
                        "style" -> "style"
                        "drawable" -> "drawable"
                        "item" -> e.getAttribute("type").takeIf { it.isNotEmpty() } ?: continue
                        else -> continue
                    }
                    map["$type/${e.getAttribute("name").replace('.', '_')}"] = e
                }
            } catch (t: Throwable) {
                android.util.Log.w("Resources", "Failed to parse $name/$file: ${t.message}")
            }
        }
        map
    }

    private fun element(type: String, name: String, config: Configuration): Element? {
        for (f in foldersFor(config)) {
            folder(f)["$type/$name"]?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------------ values

    override fun get(id: Int, config: Configuration): ResValue? {
        val e = entries[id] ?: return null
        val key = (id.toLong() shl 20) xor configKey(config)
        cache[key]?.let { return it }
        val v = resolve(e, config) ?: return null
        cache[key] = v
        return v
    }

    private fun configKey(config: Configuration): Long =
        ((config.locale?.toLanguageTag()?.hashCode()?.toLong() ?: 0L) * 31 + (if (config.isNightModeActive()) 1 else 0)) * 31 +
            config.orientation * 7 + config.smallestScreenWidthDp * 131L + config.screenWidthDp * 17L

    private fun resolve(e: Entry, config: Configuration): ResValue? {
        if (e.path != null && e.type != "string") {
            // a values item can shadow a file of the same name only in the libraries (colors, drawables)
            return ResValue.file(e.path)
        }
        return when (e.type) {
            "string" -> element("string", e.name, config)?.let { ResValue.string(resolveText(textOf(it), config)) }
            "plurals" -> element("plurals", e.name, config)?.let { el ->
                val map = LinkedHashMap<String, CharSequence>()
                val items = el.getElementsByTagName("item")
                for (i in 0 until items.length) {
                    val item = items.item(i) as Element
                    map[item.getAttribute("quantity")] = resolveText(textOf(item), config)
                }
                ResValue(ResValue.Kind.PLURALS, map)
            }
            "array" -> element("array", e.name, config)?.let { el ->
                val items = el.getElementsByTagName("item")
                val values = (0 until items.length).map { resolveText(textOf(items.item(it) as Element), config) }
                if (el.tagName == "integer-array") ResValue(ResValue.Kind.INT_ARRAY, values.map { it.toString().trim().toIntOrNull() ?: 0 }.toIntArray())
                else ResValue(ResValue.Kind.STRING_ARRAY, values.map { it as CharSequence }.toTypedArray())
            }
            "color", "dimen", "integer", "fraction", "drawable" -> element(e.type, e.name, config)?.let { parseValue(it.textContent.trim()) }
            "bool" -> element("bool", e.name, config)?.let { parseValue(it.textContent.trim()).let { v -> if (v.kind == ResValue.Kind.REFERENCE) v else ResValue.bool(it.textContent.trim() == "true") } }
            "id", "attr", "style" -> ResValue(ResValue.Kind.ID, ids["${e.type}/${e.name}"] ?: 0)
            else -> null
        }
    }

    /** A value written in XML: references (also to the framework), theme attributes, colors, dimensions */
    fun parseValue(raw: String): ResValue {
        if (raw.startsWith("@")) {
            if (raw == "@null" || raw == "@empty") return ResValue.reference(0)
            val body = raw.removePrefix("@").removePrefix("+").removePrefix("*")
            if (body.startsWith("android:")) {
                val ref = body.removePrefix("android:")
                val id = FrameworkResources.INSTANCE.getIdentifier(ref.substringBefore('/'), ref.substringAfter('/'))
                return if (id != 0) ResValue.reference(id) else ResValue.string(raw)
            }
            val ref = body.substringAfter(':')
            val id = ids[ref.substringBefore('/') + "/" + ref.substringAfter('/').replace('.', '_')] ?: 0
            return if (id != 0) ResValue.reference(id) else ResValue.string(raw)
        }
        if (raw.startsWith("?")) {
            val body = raw.removePrefix("?")
            val name = body.substringAfterLast('/').substringAfterLast(':')
            val id = if (body.startsWith("android:")) FrameworkResources.attrId(name) else ids["attr/$name"] ?: 0
            return ResValue(ResValue.Kind.ATTRIBUTE, id)
        }
        if (raw.startsWith("#")) return ResValue.color(ResourceSupport.parseColor(raw))
        ResourceSupport.parseDimension(raw)?.let { return ResValue.dimen(it.first, it.second) }
        raw.toIntOrNull()?.let { return ResValue.integer(it) }
        if (raw == "true" || raw == "false") return ResValue.bool(raw == "true")
        return ResValue.string(raw)
    }

    // ------------------------------------------------------------------ styles

    /** Id of the attribute an item of a style names ("android:padding", "cornerRadius") */
    fun attrIdOf(itemName: String): Int =
        if (itemName.startsWith("android:")) FrameworkResources.attrId(itemName.removePrefix("android:"))
        else ids["attr/" + itemName.substringAfter(':')] ?: 0

    /**
     * The items of a style with those of its parents (explicit parent="..." or the name prefix before
     * the last dot), keyed by attribute id
     */
    override fun getStyle(styleId: Int, config: Configuration): Map<Int, ResValue> {
        val e = entries[styleId] ?: return emptyMap()
        if (e.type != "style") return emptyMap()
        val key = (styleId.toLong() shl 20) xor configKey(config)
        return styleCache.getOrPut(key) { styleItems(e.name, config, HashSet()) }
    }

    private fun styleItems(name: String, config: Configuration, seen: MutableSet<String>): Map<Int, ResValue> {
        if (!seen.add(name)) return emptyMap()
        val el = element("style", name, config) ?: return emptyMap()
        val result = LinkedHashMap<Int, ResValue>()
        val parent = when {
            el.hasAttribute("parent") -> el.getAttribute("parent").takeIf { it.isNotEmpty() }
                ?.removePrefix("@style/")?.removePrefix("@android:style/")?.removePrefix("android:")?.takeIf { !el.getAttribute("parent").contains("android:") }
            el.getAttribute("name").contains('.') -> el.getAttribute("name").substringBeforeLast('.')
            else -> null
        }
        if (parent != null) result.putAll(styleItems(parent.replace('.', '_'), config, seen))
        val items = el.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val attr = attrIdOf(item.getAttribute("name"))
            if (attr == 0) continue
            result[attr] = parseValue(item.textContent.trim())
        }
        return result
    }

    // ------------------------------------------------------------------ attribute formats

    /** Enum / flag values of every attribute declared in the default values (top level or in styleables) */
    private val attrFormats: Map<String, Map<String, Int>> by lazy {
        val result = HashMap<String, Map<String, Int>>()
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        for (file in valueFiles["values"] ?: emptyList()) {
            val stream = loader.getResourceAsStream("$root/values/$file") ?: continue
            try {
                val doc = stream.use { dbf.newDocumentBuilder().parse(it) }
                val attrs = doc.getElementsByTagName("attr")
                for (i in 0 until attrs.length) {
                    val a = attrs.item(i) as Element
                    val values = LinkedHashMap<String, Int>()
                    val kids = a.childNodes
                    for (j in 0 until kids.length) {
                        val k = kids.item(j) as? Element ?: continue
                        if (k.tagName != "enum" && k.tagName != "flag") continue
                        val v = k.getAttribute("value").trim()
                        values[k.getAttribute("name")] = (if (v.startsWith("0x") || v.startsWith("0X")) java.lang.Long.parseLong(v.substring(2), 16) else v.toLong()).toInt()
                    }
                    if (values.isNotEmpty()) result[a.getAttribute("name").substringAfter(':')] = values
                }
            } catch (t: Throwable) {
                android.util.Log.w("Resources", "Failed to read attributes of values/$file: ${t.message}")
            }
        }
        result
    }

    /** name -> value of the enums / flags of an attribute */
    fun attrValues(name: String): Map<String, Int>? = attrFormats[name]

    // ------------------------------------------------------------------ strings

    /** "@string/x" references inside string/array items */
    private fun resolveText(text: String, config: Configuration): String {
        if (text.startsWith("@string/")) {
            val name = text.removePrefix("@string/")
            return element("string", name, config)?.let { resolveText(textOf(it), config) } ?: text
        }
        return text
    }

    /** Inner text of a string element with aapt processing (quotes, escapes, whitespace) */
    private fun textOf(e: Element): String {
        val raw = StringBuilder()
        fun collect(n: Node) {
            when (n.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> raw.append(n.nodeValue)
                Node.ELEMENT_NODE -> {
                    val children = n.childNodes
                    for (i in 0 until children.length) collect(children.item(i))
                }
            }
        }
        val children = e.childNodes
        for (i in 0 until children.length) collect(children.item(i))
        return aapt(raw.toString())
    }

    private fun aapt(s: String): String {
        val out = StringBuilder(s.length)
        var inQuotes = false
        var lastWasSpace = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> {
                    val n = s[i + 1]
                    i++
                    when (n) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> if (i + 4 < s.length) {
                            out.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> out.append(n)
                    }
                    lastWasSpace = false
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c.isWhitespace() -> {
                    if (!lastWasSpace) out.append(' ')
                    lastWasSpace = true
                }
                else -> {
                    out.append(c)
                    lastWasSpace = false
                }
            }
            i++
        }
        return out.toString().trim()
    }

    override fun getIdentifier(type: String, name: String): Int = ids["$type/${name.replace('.', '_')}"] ?: 0

    override fun getResourceName(id: Int): String? = entries[id]?.let { "$packageName:${it.type}/${it.name}" }

    override fun getPackageName(): String = packageName

    override fun openFile(path: String): InputStream =
        loader.getResourceAsStream("$root/$path") ?: throw FileNotFoundException(path)

    override fun isBinaryXml(path: String): Boolean = false

    /** Resource name for an id without package, e.g. "string/app_name" */
    fun nameOf(id: Int): String? = entries[id]?.let { "${it.type}/${it.name}" }
}
