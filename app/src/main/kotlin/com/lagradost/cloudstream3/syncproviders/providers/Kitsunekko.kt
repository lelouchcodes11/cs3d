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
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * Kitsunekko (kitsunekko.net): Japanese subtitles of anime, a plain directory listing with no account. Its folders are named after the
 * romaji title; the files in them are mostly one .srt / .ass per episode (archives are left out, a person cannot use them here).
 * Only offered for Japanese (or "all languages").
 */
class Kitsunekko : SubtitleAPI() {
    override val name = "Kitsunekko"
    override val idPrefix = "kitsunekko"
    override val requiresLogin = false

    companion object {
        const val HOST = "https://kitsunekko.net"
        private const val TAG = "Kitsunekko"
        private const val ROOT = "subtitles/japanese/"
        private const val MAX_FOLDERS = 3
        private const val MAX_FILES = 24
        private const val LIST_MS = 6L * 60 * 60 * 1000

        private val folderRegex = Regex("""href="/dirlist\.php\?dir=subtitles%2Fjapanese%2F([^"%]+(?:%[0-9A-Fa-f]{2}[^"%]*)*?)%2F"""")
        private val fileRegex = Regex("""href="(subtitles/japanese/[^"]+\.(?:srt|ass|ssa|vtt))"""", RegexOption.IGNORE_CASE)

        private fun words(s: String): List<String> = s.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }

        /** The folders whose name has every word of the query (or most of them, for long titles), the closest first */
        fun matchFolders(folders: List<String>, query: String): List<String> {
            val wanted = words(query).filter { it.length > 1 }
            if (wanted.isEmpty()) return emptyList()
            return folders.map { f ->
                val have = words(f).toSet()
                val hit = wanted.count { it in have }
                val score = hit.toDouble() / wanted.size - (have.size - hit).coerceAtLeast(0) * 0.02
                f to score
            }.filter { (_, score) -> score >= if (wanted.size <= 2) 0.99 else 0.66 }.sortedByDescending { it.second }.map { it.first }
        }

        /** Does a file name carry the episode number: "... 03.srt", "E03", "- 3 -", "episode 3" */
        fun hasEpisode(file: String, episode: Int): Boolean {
            val name = file.substringBeforeLast('.')
            val n = Regex("""(?<![\p{L}\p{N}])(?:e|ep|episode|#)?\s*0*$episode(?![\p{N}])""", RegexOption.IGNORE_CASE)
            return n.containsMatchIn(name.replace(Regex("""\d{3,4}p|[xh]\.?26[45]|\bv\d\b""", RegexOption.IGNORE_CASE), " "))
        }
    }

    @Volatile private var folders: Pair<Long, List<String>>? = null

    private suspend fun folderList(): List<String> {
        folders?.let { (at, list) -> if (System.currentTimeMillis() - at < LIST_MS) return list }
        val html = get("$HOST/dirlist.php?dir=" + java.net.URLEncoder.encode(ROOT, "UTF-8"))
        val list = folderRegex.findAll(html).map { URLDecoder.decode(it.groupValues[1].replace("+", " "), "UTF-8") }.distinct().toList()
        folders = System.currentTimeMillis() to list
        return list
    }

    override suspend fun search(auth: AuthData?, query: SubtitleSearch): List<SubtitleEntity>? {
        val lang = query.lang
        if (query.query.isBlank()) return null
        if (!lang.isNullOrBlank() && lang != AllLanguagesName && !lang.lowercase().startsWith("ja")) return null
        val all = runCatching { folderList() }.getOrElse { Log.w(TAG, "folders: ${it.message}"); return null }
        val hits = matchFolders(all, query.query).take(MAX_FOLDERS)
        if (hits.isEmpty()) return emptyList()
        val out = ArrayList<SubtitleEntity>()
        for (folder in hits) {
            val html = runCatching { get("$HOST/dirlist.php?dir=" + java.net.URLEncoder.encode("$ROOT$folder/", "UTF-8")) }.getOrNull() ?: continue
            var files = fileRegex.findAll(html).map { it.groupValues[1] }.toList()
            val episode = query.epNumber ?: 0
            if (episode > 0) {
                val matching = files.filter { hasEpisode(it.substringAfterLast('/'), episode) }
                // a folder with a single file per film or a pack: nothing matched means show them all rather than none
                files = matching.ifEmpty { if (files.size <= 3) files else emptyList() }
            }
            for (path in files.take(MAX_FILES)) {
                val file = path.substringAfterLast('/')
                out += SubtitleEntity(
                    idPrefix = idPrefix, name = "$folder · $file", lang = "ja",
                    data = HOST + "/" + path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
                    source = name, type = TvType.Anime, epNumber = query.epNumber, seasonNumber = query.seasonNumber, year = query.year,
                    headers = mapOf("referer" to "$HOST/"),
                )
            }
        }
        return out
    }

    override suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? = subtitle.data

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val response = app.get(url, referer = "$HOST/", timeout = 20)
        if (response.code !in 200..299) error("HTTP ${response.code}")
        response.text
    }
}
