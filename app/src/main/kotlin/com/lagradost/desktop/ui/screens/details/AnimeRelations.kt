package com.lagradost.desktop.ui.screens.details

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.desktop.tmdb.TmdbCard
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One title related to an anime (its prequel, sequel, side story ...), as a card that opens a search for it in the extensions */
class RelatedTitle(val label: String, val detail: String, val card: TmdbCard, val order: Int)

/**
 * Prequels, sequels, side stories and spin-offs of an anime, from AniList (public GraphQL, no key). The AniList id comes from the
 * extension's page (AniList or MyAnimeList id) when it has one; otherwise AniList is searched by the title.
 */
object AnimeRelations {
    private val cache = ConcurrentHashMap<String, List<RelatedTitle>>()

    /** The relation types worth showing, in the order they are listed */
    private val labels = linkedMapOf(
        "PREQUEL" to "Prequel", "PARENT" to "Parent story", "SEQUEL" to "Sequel", "SIDE_STORY" to "Side story", "SPIN_OFF" to "Spin-off",
        "ALTERNATIVE" to "Alternative version", "SUMMARY" to "Summary", "COMPILATION" to "Compilation", "CONTAINS" to "Contains", "OTHER" to "Related",
    )

    private val formats = mapOf("TV" to "TV", "TV_SHORT" to "TV short", "MOVIE" to "Movie", "SPECIAL" to "Special", "OVA" to "OVA", "ONA" to "ONA", "MUSIC" to "Music")

    /** "Name (Dub)", "Name [1080p]", "Name (2019)" -> "Name": the words an extension adds do not exist on AniList */
    private fun clean(title: String): String =
        title.replace(Regex("""\s*[\(\[][^)\]]*[\)\]]"""), " ").replace(Regex("""\s+(dub|sub|dubbed|subbed|hindi|english)\b.*$""", RegexOption.IGNORE_CASE), "").trim().ifEmpty { title }

    suspend fun load(title: String, syncData: Map<String, String>): List<RelatedTitle> {
        val aniId = syncData["anilist"]?.toIntOrNull()
        val malId = syncData["mal"]?.toIntOrNull()
        val key = when {
            aniId != null -> "id:$aniId"
            malId != null -> "mal:$malId"
            else -> "q:" + clean(title).lowercase()
        }
        cache[key]?.let { return it }
        val args = when {
            aniId != null -> "id: $aniId"
            malId != null -> "idMal: $malId"
            else -> "search: \$search"
        }
        val query = (if (aniId == null && malId == null) "query(\$search: String)" else "query") +
            "{ Media($args, type: ANIME) { id relations { edges { relationType(version: 2) node { id type format status seasonYear title { romaji english } coverImage { extraLarge large } averageScore genres } } } } }"
        val body = mutableMapOf<String, Any>("query" to query)
        if (aniId == null && malId == null) body["variables"] = mapOf("search" to clean(title))
        val json = JSONObject(app.post("https://graphql.anilist.co", json = body, timeout = 20).text)
        val edges = json.optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("relations")?.optJSONArray("edges")
        val list = buildList {
            if (edges != null) for (i in 0 until edges.length()) {
                val e = edges.getJSONObject(i)
                val node = e.optJSONObject("node") ?: continue
                if (node.optString("type") != "ANIME") continue
                val type = e.optString("relationType")
                val label = labels[type] ?: continue
                val t = node.optJSONObject("title")
                val name = (t?.optString("english")?.takeIf { it.isNotBlank() && it != "null" } ?: t?.optString("romaji"))?.takeIf { it.isNotBlank() } ?: continue
                val cover = node.optJSONObject("coverImage")?.let { it.optString("extraLarge").takeIf { s -> s.isNotBlank() } ?: it.optString("large") }?.takeIf { it.isNotBlank() }
                val format = formats[node.optString("format")]
                val year = node.optInt("seasonYear").takeIf { it > 0 }
                val genres = node.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.getString(it) } }.orEmpty()
                add(
                    RelatedTitle(
                        label, listOfNotNull(label, format, year?.toString()).joinToString(" · "),
                        TmdbCard(
                            name = name, url = "anilist:" + node.optInt("id"), type = if (node.optString("format") == "MOVIE") TvType.AnimeMovie else TvType.Anime,
                            posterUrl = cover, year = year, genres = genres, score = Score.from100(node.optInt("averageScore").takeIf { it > 0 }), isMovie = false,
                        ),
                        labels.keys.indexOf(type),
                    ),
                )
            }
        }.sortedBy { it.order }
        cache[key] = list
        return list
    }
}
