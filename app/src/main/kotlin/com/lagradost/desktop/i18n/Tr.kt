package com.lagradost.desktop.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The words of the desktop screens in the language of the app (Settings > General > App language).
 *
 * The text of the Fluent screens is written in English in the code. [t] looks the English text up in the catalog of the language
 * (`i18n/<language>.tsv` next to the other resources: English text, a tab, the translation) and gives the English text back when there is no
 * translation. The place all of that text passes through is `FText`, so a screen needs no change to be translated: add its lines to the catalogs.
 *
 * Text with numbers or names in it is a template: `"Search {0} sources"` matches "Search 12 sources", and the translation puts the pieces
 * where its grammar wants them (`{1} {0}`), with plural forms where the number matters (`{0,plural,one{# başlık}other{# başlık}}`,
 * Arabic has six: zero, one, two, few, many, other). The pieces that were cut out are looked up on their own, so "3 · Series" is made of two
 * translated pieces. A line made of several parts joined by " · ", " • " or " - " is translated part by part.
 *
 * The strings that were shown without a translation are kept ([misses]) for the dev server's `/i18n` page.
 */
object Tr {
    private var version by mutableIntStateOf(0)

    @Volatile private var loadedFor: String? = null
    @Volatile private var catalog: Catalog? = null
    @Volatile private var language: String = "en"

    private val rtlLanguages = setOf("ar", "arz", "apc", "ars", "fa", "he", "iw", "ur", "ckb", "ps", "sd", "yi", "dv", "ug")

    /** the language files that serve a dialect */
    private val families = mapOf("arz" to "ar", "apc" to "ar", "ars" to "ar", "acm" to "ar")

    /** strings that were shown as they are in a language that has a catalog (dev tool) */
    private val missed = ConcurrentHashMap.newKeySet<String>()
    val misses: List<String> get() = missed.sorted()

    private fun ensureLoaded() {
        val want = Locale.getDefault().language.ifBlank { "en" }
        if (loadedFor == want) return
        synchronized(this) {
            if (loadedFor == want) return
            language = want
            catalog = if (want == "en") null else Catalog.load(families[want] ?: want)
            missed.clear()
            loadedFor = want
        }
    }

    /** The app language changed ([java.util.Locale.setDefault] was called): everything that shows text is drawn again */
    fun localeChanged() {
        loadedFor = null
        ensureLoaded()
        version++
    }

    /** The English [text] of a screen in the language of the app */
    fun t(text: String): String {
        version // a composable that calls this is drawn again when the language changes
        if (text.length < 2) return text
        ensureLoaded()
        val c = catalog ?: return text
        val out = c.translate(text)
        if (out == text && missed.size < 4000 && text.any { it.isLetter() } && text.length <= 240) missed += text
        return out
    }

    /** [t] with the pieces filled in, for text that is made in the code: `Tr.f("Search {0} sources", n)` */
    fun f(template: String, vararg args: Any?): String {
        version
        ensureLoaded()
        val c = catalog
        val parts = c?.templateValues?.get(template)
        val strings = args.map { it.toString() }
        if (c != null && parts != null) return Catalog.render(parts, strings, c.language)
        var s = template
        strings.forEachIndexed { i, a -> s = s.replace("{$i}", a) }
        return t(s)
    }

    /** True for a language that is written from right to left: the screens are laid out the other way round */
    val rtl: Boolean
        get() {
            version
            ensureLoaded()
            return language in rtlLanguages
        }

    /** The language code of the app ("en", "tr", "ar" ...) */
    val code: String get() { ensureLoaded(); return language }

    /** Capital letters of the language: a Turkish "i" becomes "İ" */
    fun upper(text: String): String = text.uppercase(Locale.getDefault())

    fun lower(text: String): String = text.lowercase(Locale.getDefault())

    // -------------------------------------------------------------------------------------------------

    internal sealed interface Part
    internal class Lit(val text: String) : Part
    internal class Arg(val index: Int) : Part
    internal class Plural(val index: Int, val forms: Map<String, List<Part>>) : Part

    /** [labels]: the number of each placeholder of the English text, in the order they stand in it */
    internal class Template(val regex: Regex, val labels: IntArray, val literalLength: Int, val value: List<Part>)

    /** The English texts of one language and their translations */
    internal class Catalog(val language: String, val exact: Map<String, String>, val templates: List<Template>, val templateValues: Map<String, List<Part>>) {
        private val cache = ConcurrentHashMap<String, String>()

        fun translate(text: String): String {
            cache[text]?.let { return it }
            val out = lookup(text, 0)
            if (cache.size > 6000) cache.clear()
            cache[text] = out
            return out
        }

        private fun lookup(text: String, depth: Int): String {
            exact[text]?.let { return it }
            val trimmed = text.trim()
            if (trimmed != text && trimmed.isNotEmpty()) {
                val inner = lookup(trimmed, depth)
                if (inner != trimmed) return text.substringBefore(trimmed) + inner + text.substringAfterLast(trimmed)
            }
            // "Loading…" and "Loading..." are the same line
            if (text.endsWith("...")) exact[text.dropLast(3) + "…"]?.let { return it }
            for (t in templates) {
                val m = t.regex.matchEntire(text) ?: continue
                val args = Array(t.labels.maxOrNull()?.plus(1) ?: 0) { "" }
                t.labels.forEachIndexed { i, label -> args[label] = m.groupValues[i + 1].let { piece -> if (depth < 2) lookup(piece, depth + 1) else piece } }
                return render(t.value, args.toList(), language)
            }
            // several parts of one line
            for (sep in SEPARATORS) {
                if (!text.contains(sep)) continue
                val parts = text.split(sep)
                val done = parts.map { if (it.isBlank()) it else lookup(it, depth + 1) }
                if (done != parts) return done.joinToString(sep)
            }
            return text
        }

        companion object {
            /** the places where a line that is made of several texts is cut (one per line, parts of a meta line, "label: description") */
            private val SEPARATORS = listOf("\n", " · ", " • ", " - ", ": ")
            private val PLACEHOLDER = Regex("""\{(\d+)}""")
            private val PLURAL_HEAD = Regex("""\{(\d+),plural,""")

            /** The catalog of [language] from `i18n/strings.tsv` (null: that language has no column) */
            fun load(language: String): Catalog? {
                val stream = Tr::class.java.classLoader.getResourceAsStream("i18n/strings.tsv") ?: return null
                val catalog = stream.use { parse(language, it.readBytes().toString(Charsets.UTF_8)) }
                return catalog.takeIf { it.exact.isNotEmpty() || it.templates.isNotEmpty() }
            }

            /**
             * The table of all languages: the first line is `en`, a tab and the language codes ("en	ar	tr"), every other line the English text and its
             * translation in each language (a tab between, an empty cell = not translated yet); `#` starts a comment; `\n`, `\t` and `\\` are escapes
             */
            fun parse(language: String, text: String): Catalog {
                val exact = HashMap<String, String>()
                val templates = ArrayList<Template>()
                val values = HashMap<String, List<Part>>()
                var column = -1
                for (raw in text.lineSequence()) {
                    val line = raw.trimEnd('\r')
                    if (line.isBlank() || line.startsWith("#")) continue
                    val cells = line.split('\t')
                    if (column < 0) {
                        // the header
                        column = cells.indexOf(language)
                        if (column < 0) return Catalog(language, exact, templates, values)
                        continue
                    }
                    val key = unescape(cells[0])
                    val value = unescape(cells.getOrNull(column).orEmpty())
                    if (key.isEmpty() || value.isEmpty()) continue
                    val found = PLACEHOLDER.findAll(key).toList()
                    if (found.isEmpty()) { exact[key] = value; continue }
                    // a key with hardly any words of its own ("E{0}", "{0} ep") is for numbers: it must not take any text that happens to start with an E
                    val letters = PLACEHOLDER.replace(key, "").count { it.isLetter() }
                    val startsWithAWord = Regex("""\p{L}{2,}""").containsMatchIn(key.substring(0, found.first().range.first))
                    val capture = if (letters < 4 && !startsWithAWord) """([0-9][0-9 .,/+\-]*?)""" else "(.+?)"
                    val pattern = StringBuilder("^")
                    var from = 0
                    var literal = 0
                    for (m in found) {
                        val lit = key.substring(from, m.range.first)
                        if (lit.isNotEmpty()) pattern.append(Regex.escape(lit))
                        literal += lit.length
                        pattern.append(capture)
                        from = m.range.last + 1
                    }
                    val tail = key.substring(from)
                    if (tail.isNotEmpty()) pattern.append(Regex.escape(tail))
                    literal += tail.length
                    pattern.append('$')
                    val parsed = parseParts(value, IntArray(1), -1)
                    templates += Template(Regex(pattern.toString(), RegexOption.DOT_MATCHES_ALL), IntArray(found.size) { found[it].groupValues[1].toInt() }, literal, parsed)
                    values[key] = parsed
                }
                templates.sortByDescending { it.literalLength }
                return Catalog(language, exact, templates, values)
            }

            fun unescape(s: String): String {
                if (!s.contains('\\')) return s
                val sb = StringBuilder()
                var i = 0
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\' && i + 1 < s.length) {
                        when (s[i + 1]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            '\\' -> sb.append('\\')
                            else -> sb.append(s[i + 1])
                        }
                        i += 2
                    } else { sb.append(c); i++ }
                }
                return sb.toString()
            }

            /** `text {0} text {1,plural,one{# a}other{# b}}`: [at] is where the reading is; inside a plural form `#` is its number */
            private fun parseParts(s: String, at: IntArray, pluralArg: Int): List<Part> {
                val out = ArrayList<Part>()
                val lit = StringBuilder()
                fun flush() { if (lit.isNotEmpty()) { out += Lit(lit.toString()); lit.setLength(0) } }
                while (at[0] < s.length) {
                    val c = s[at[0]]
                    if (c == '}' && pluralArg >= 0) break
                    if (c == '#' && pluralArg >= 0) { flush(); out += Arg(pluralArg); at[0]++; continue }
                    if (c == '{') {
                        val simple = PLACEHOLDER.matchAt(s, at[0])
                        if (simple != null) {
                            flush(); out += Arg(simple.groupValues[1].toInt()); at[0] = simple.range.last + 1; continue
                        }
                        val head = PLURAL_HEAD.matchAt(s, at[0])
                        if (head != null) {
                            flush()
                            val index = head.groupValues[1].toInt()
                            at[0] = head.range.last + 1
                            val forms = LinkedHashMap<String, List<Part>>()
                            while (at[0] < s.length && s[at[0]] != '}') {
                                val nameStart = at[0]
                                while (at[0] < s.length && s[at[0]] != '{' && s[at[0]] != '}') at[0]++
                                val name = s.substring(nameStart, at[0]).trim()
                                if (at[0] >= s.length || s[at[0]] != '{') break
                                at[0]++ // {
                                forms[name] = parseParts(s, at, index)
                                at[0]++ // }
                            }
                            at[0]++ // the closing brace of the plural
                            out += Plural(index, forms)
                            continue
                        }
                    }
                    lit.append(c); at[0]++
                }
                flush()
                return out
            }

            fun render(parts: List<Part>, args: List<String>, language: String): String {
                val sb = StringBuilder()
                for (p in parts) when (p) {
                    is Lit -> sb.append(p.text)
                    is Arg -> sb.append(args.getOrElse(p.index) { "" })
                    is Plural -> {
                        val n = args.getOrNull(p.index)?.replace(",", "")?.replace(" ", "")?.toLongOrNull()
                        val chosen = p.forms[if (n == null) "other" else category(language, n)] ?: p.forms["other"] ?: emptyList()
                        sb.append(render(chosen, args, language))
                    }
                }
                return sb.toString()
            }
        }
    }

    /** The CLDR plural category of a whole number (Arabic has six, Turkish and English two) */
    fun category(language: String, n: Long): String = when (language) {
        "ar" -> when {
            n == 0L -> "zero"
            n == 1L -> "one"
            n == 2L -> "two"
            n % 100 in 3..10 -> "few"
            n % 100 in 11..99 -> "many"
            else -> "other"
        }
        else -> if (n == 1L) "one" else "other"
    }

    /** Parses a catalog (tests) */
    internal fun parseCatalog(language: String, text: String): Catalog = Catalog.parse(language, text)
}
