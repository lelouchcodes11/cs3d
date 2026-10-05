package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Log
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Subtitle Cat (subtitlecat.com): no account. A search lists release pages; each page lists the languages it has as ready .srt files
 * (the others only offer a server-side "Translate" button, which is no file and is skipped).
 */
class SubtitleCat : SubtitleAPI() {
    override val name = "Subtitle Cat"
    override val idPrefix = "subtitlecat"
    override val requiresLogin = false

    /** A release page found by a search */
    data class Release(val title: String, val note: String, val pageUrl: String)

    /** One ready subtitle file of a release page */
    data class Entry(val code: String, val language: String, val fileUrl: String)

    companion object {
        const val HOST = "https://www.subtitlecat.com"
        private const val TAG = "SubtitleCat"

        /** Release pages shown per search (each costs one more request) */
        private const val MAX_PAGES = 6

        /** Files kept per release page when every language is asked for */
        private const val MAX_FILES_PER_PAGE = 12

        /** The rows of a search page: release page link, its title and the "(translated from ...)" note */
        fun parseSearch(html: String): List<Release> = Jsoup.parse(html, HOST).select("table.sub-table tbody tr").mapNotNull { row ->
            val a = row.selectFirst("td a[href^=subs/], td a[href^=/subs/]") ?: return@mapNotNull null
            val href = a.attr("href").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val td = a.parent()
            val note = td?.ownText()?.trim().orEmpty()
            Release(a.text().trim(), note, absolute(href))
        }

        /** The ready files of a release page: a language row with a download link (rows with only a Translate button are no file) */
        fun parsePage(html: String): List<Entry> = Jsoup.parse(html, HOST).select("div.sub-single").mapNotNull { row ->
            val link = row.selectFirst("a[id^=download_][href]") ?: return@mapNotNull null
            val href = link.attr("href").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val code = link.id().removePrefix("download_").ifBlank { row.selectFirst("img.flag")?.attr("alt").orEmpty() }
            val language = row.select("span").firstOrNull { it.children().isEmpty() && it.text().isNotBlank() }?.text()?.trim().orEmpty()
            Entry(code, language, absolute(href))
        }

        /** Site paths come with spaces and brackets unescaped */
        fun absolute(href: String): String {
            val base = if (href.startsWith("http", ignoreCase = true)) href else if (href.startsWith("/")) HOST + href else "$HOST/$href"
            return base.replace(" ", "%20").replace("[", "%5B").replace("]", "%5D")
        }

        /** "en" matches "en", "pt" matches "pt-BR": the language part is what counts */
        fun sameLanguage(wanted: String?, code: String): Boolean {
            if (wanted.isNullOrBlank() || wanted == AllLanguagesName) return true
            val w = wanted.lowercase().replace('_', '-')
            val c = code.lowercase().replace('_', '-')
            return w == c || w.substringBefore('-') == c.substringBefore('-')
        }

        private fun words(text: String): List<String> = text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }

        /**
         * The site's search is loose (a search for a title also returns unrelated uploads, some of them adult), so a release is only offered when its
         * name has every word of the title, and for an episode its SxxEyy.
         */
        fun isRelevant(releaseTitle: String, query: SubtitleSearch): Boolean {
            val name = words(releaseTitle)
            val wanted = words(query.query)
            if (wanted.isEmpty() || !wanted.all { it in name }) return false
            val season = query.seasonNumber ?: 0
            val episode = query.epNumber ?: 0
            if (season > 0 && episode > 0) {
                val joined = releaseTitle.lowercase()
                return joined.contains("s%02de%02d".format(season, episode)) || joined.contains("%dx%02d".format(season, episode))
            }
            return true
        }

        /** What to type in the site's search box: the title, the year of a film, or the SxxEyy of an episode */
        fun searchTerms(query: SubtitleSearch): String {
            val title = query.query.trim()
            val season = query.seasonNumber ?: 0
            val episode = query.epNumber ?: 0
            return when {
                season > 0 && episode > 0 -> "$title S%02dE%02d".format(season, episode)
                query.year != null && query.year!! > 0 -> "$title ${query.year}"
                else -> title
            }
        }
    }

    override suspend fun search(auth: AuthData?, query: SubtitleSearch): List<SubtitleEntity>? {
        if (query.query.isBlank()) return null
        val type = if ((query.seasonNumber ?: 0) > 0) TvType.TvSeries else TvType.Movie
        val releases = runCatching {
            // a series title often has no year in the site's names: fall back to the plain title when the first try finds nothing
            val first = parseSearch(searchPage(searchTerms(query)))
            if (first.isNotEmpty() || searchTerms(query) == query.query.trim()) first else parseSearch(searchPage(query.query.trim()))
        }.getOrElse { Log.w(TAG, "search failed: ${it.message}"); return null }.filter { isRelevant(it.title, query) }.take(MAX_PAGES)
        if (releases.isEmpty()) return emptyList()

        val pages = coroutineScope {
            releases.map { release ->
                async(Dispatchers.IO) { release to runCatching { parsePage(get(release.pageUrl)) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }
        val wantAll = query.lang.isNullOrBlank() || query.lang == AllLanguagesName
        return pages.flatMap { (release, entries) ->
            entries.filter { sameLanguage(query.lang, it.code) }.take(if (wantAll) MAX_FILES_PER_PAGE else Int.MAX_VALUE).map { entry ->
                SubtitleEntity(
                    idPrefix = idPrefix,
                    name = listOf(release.title, release.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" ") + if (wantAll) " · ${entry.language}" else "",
                    lang = entry.code,
                    data = entry.fileUrl,
                    source = name,
                    type = type,
                    epNumber = query.epNumber,
                    seasonNumber = query.seasonNumber,
                    year = query.year,
                    headers = mapOf("referer" to "$HOST/"),
                )
            }
        }
    }

    override suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? = subtitle.data

    private suspend fun searchPage(terms: String): String = get("$HOST/index.php?search=" + java.net.URLEncoder.encode(terms, "UTF-8"))

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val response = app.get(url, referer = "$HOST/", timeout = 20)
        if (response.code !in 200..299) error("HTTP ${response.code}")
        response.text
    }
}
