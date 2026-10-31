package com.lagradost.desktop.isolate

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mapper

/**
 * What crosses the pipe between the app and the sandbox: results of an extension (a search result, the page of a title, a link) as JSON with the name of
 * the library class that describes them. An extension may define a class of its own (a subclass of a library class, or one that implements the library's
 * interface): the name sent is that of the nearest class of the library, and the extension's extra fields are dropped on the way.
 *
 * The sandbox is untrusted, so the app never builds a class from a name it did not expect: [read] accepts only a class of the library's own package that is
 * a kind of what was asked for.
 */
object IsolationSerde {
    private const val LIBRARY = "com.lagradost.cloudstream3."

    /**
     * For reading: a getter that returns a map is not filled in. A series page has `getLatestEpisodes()`, which is written as a property and answers with an immutable
     * one-entry map; Jackson would call the getter and put the written entry into that map, which fails when the entry is empty (a series whose episodes have no
     * season or number, as the Netflix source of CNC Verse lists them: "unable to load details"). Such a property has nothing to be read back into: it is computed.
     */
    private val readMapper = mapper.rebuild().configure(com.fasterxml.jackson.databind.MapperFeature.USE_GETTERS_AS_SETTERS, false).build()

    /** The library class that stands for [value]: its own class, or the closest library class above it, or [fallback] for a plain implementation of an interface */
    fun libraryName(value: Any, fallback: Class<*>): String {
        var c: Class<*>? = value.javaClass
        while (c != null && c != Any::class.java) {
            if (c.name.startsWith(LIBRARY)) return c.name
            c = c.superclass
        }
        return fallback.name
    }

    fun write(value: Any, fallback: Class<*>): JsonNode {
        val tree = mapper.valueToTree<JsonNode>(value)
        // the page of a title lists related titles as SearchResponse (an interface): each is written with the name of its class, as a result of a search is
        if (value is LoadResponse && tree is ObjectNode) {
            value.recommendations?.let { list -> tree.set<JsonNode>("recommendations", mapper.createArrayNode().also { a -> list.forEach { a.add(write(it, SearchResponse::class.java)) } }) }
        }
        return mapper.createObjectNode().put("cls", libraryName(value, fallback)).also { it.set<JsonNode>("json", tree) }
    }

    /** The object that [write] made, as a [expected] (null when the class name is not an acceptable one) */
    fun <T : Any> read(node: JsonNode, expected: Class<T>): T? {
        val name = node.get("cls")?.asText() ?: return null
        val json = node.get("json") ?: return null
        if (!name.startsWith(LIBRARY)) return null
        val clazz = runCatching { Class.forName(name, false, IsolationSerde::class.java.classLoader) }.getOrNull() ?: return null
        if (!expected.isAssignableFrom(clazz)) return null
        // the related titles of a page are read one by one (see [write])
        var related: List<SearchResponse>? = null
        var tree = json
        if (LoadResponse::class.java.isAssignableFrom(clazz) && json is ObjectNode && json.get("recommendations")?.isArray == true) {
            related = json.get("recommendations").mapNotNull { read(it, SearchResponse::class.java) }
            tree = json.deepCopy<ObjectNode>().also { it.remove("recommendations") }
        }
        @Suppress("UNCHECKED_CAST")
        return runCatching { readMapper.treeToValue(tree, clazz).also { if (related != null && it is LoadResponse) it.recommendations = related } as T }.onFailure { android.util.Log.w("IsolationSerde", "a ${clazz.simpleName} from the sandbox could not be read: ${it.message?.take(400)}") }.getOrNull()
    }

    /** [expected] read from a node that was made by `mapper.valueToTree` of exactly that class (links, subtitles) */
    fun <T : Any> plain(node: JsonNode, expected: Class<T>): T? = runCatching { mapper.treeToValue(node, expected) }.getOrNull()
}
