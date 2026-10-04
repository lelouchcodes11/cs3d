package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.io.File
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * OpenSubtitles through its older, anonymous interface (rest.opensubtitles.org and dl.opensubtitles.org), as the way around the
 * download block of www.opensubtitles.com: for some titles and some countries (India, for new Indian releases) the file behind the
 * link of the new API is replaced by an HTML "court order" page, while the same subtitle is served by the older host.
 */
object OpenSubtitlesLegacy {
    private const val TAG = "OPENSUBS-LEGACY"
    private const val REST = "https://rest.opensubtitles.org/search"

    /** The interface's documented user agent for tests; it is the one that is accepted without registration */
    private const val USER_AGENT = "TemporaryUserAgent"
    private const val BLOCK_REMEMBERED_MS = 30L * 60L * 1000L

    @Volatile
    private var blockedAt = 0L

    /** The new API's files were answered with a web page a short while ago: the older host is asked first from now on */
    fun comLooksBlocked(): Boolean = unixTimeMS - blockedAt < BLOCK_REMEMBERED_MS

    fun markComBlocked() {
        blockedAt = unixTimeMS
    }

    /**
     * A client that resolves names with the system's DNS. The app's own client uses DNS over HTTPS (a setting), and the public resolvers
     * of such services answer for these two legacy hosts with an address that is not usable (blocked in India), while the system answers.
     */
    private val systemDnsClient by lazy { okhttp3.OkHttpClient.Builder().dns(okhttp3.Dns.SYSTEM).proxy(java.net.Proxy.NO_PROXY).connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build() }

    private suspend fun getBytes(url: String, userAgent: String): Pair<Int, ByteArray> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = okhttp3.Request.Builder().url(url).header("User-Agent", userAgent).build()
        systemDnsClient.newCall(request).execute().use { it.code to it.body.bytes() }
    }

    class Fetched(val bytes: ByteArray?, val webPage: Boolean)

    /** The content behind a download link; [Fetched.webPage] when the answer was an HTML page and not a subtitle */
    suspend fun fetch(link: String, headers: Map<String, String> = emptyMap()): Fetched = try {
        val response = app.get(link, headers = headers + ("user-agent" to OpenSubtitlesApi.userAgent), timeout = 20)
        val bytes = response.okhttpResponse.body.bytes()
        val head = String(bytes, 0, minOf(bytes.size, 300), Charsets.UTF_8).trimStart()
        val page = response.okhttpResponse.header("Content-Type")?.contains("html", true) == true || head.startsWith("<!") || head.startsWith("<html", true) || head.startsWith("<meta", true)
        if (!response.isSuccessful || bytes.isEmpty() || page) {
            Log.w(TAG, "download answered ${response.code}, ${bytes.size} bytes${if (page) " (a web page, not a subtitle)" else ""}")
            Fetched(null, page)
        } else Fetched(bytes, false)
    } catch (t: Throwable) {
        Log.w(TAG, "download failed: ${t.message}")
        Fetched(null, false)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Result(
        @JsonProperty("SubFileName") val subFileName: String? = null,
        @JsonProperty("SubDownloadLink") val downloadLink: String? = null,
        @JsonProperty("ISO639") val language: String? = null,
        @JsonProperty("SubDownloadsCnt") val downloads: String? = null,
        @JsonProperty("SubFormat") val format: String? = null,
        @JsonProperty("MovieName") val movieName: String? = null,
        @JsonProperty("MovieReleaseName") val releaseName: String? = null,
        @JsonProperty("IDMovieImdb") val imdb: String? = null,
        @JsonProperty("SeriesSeason") val season: String? = null,
        @JsonProperty("SeriesEpisode") val episode: String? = null,
        @JsonProperty("SubHearingImpaired") val hearingImpaired: String? = null,
        @JsonProperty("MovieYear") val year: String? = null,
    )

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    /** Release names ("Name.2026.WEBRip.x264.srt") as a search text */
    private fun searchText(name: String): String =
        name.replace(Regex("""\.(srt|vtt|ass|ssa|sub|txt)$""", RegexOption.IGNORE_CASE), "").replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim().lowercase()

    /** The interface's search: by IMDb number when the title has one the older database knows, else by name; only the language asked for */
    suspend fun search(name: String, lang: String, imdb: String?, season: Int?, episode: Int?): List<Result> {
        val language2 = lang.substringBefore('-').lowercase().ifBlank { "en" }
        // the interface wants ISO 639-2 codes ("eng"); anything else (like "all") is answered with a broken redirect
        val language3 = com.lagradost.cloudstream3.utils.SubtitleHelper.languages.firstOrNull { it.ISO_639_1 == language2 }?.ISO_639_2_B?.takeIf { it.isNotBlank() } ?: "eng"
        val series = (season ?: 0) > 0 && (episode ?: 0) > 0
        fun pathOf(byImdb: Boolean) = buildString {
            if (series) append("/episode-").append(episode)
            if (byImdb) append("/imdbid-").append(imdb!!.removePrefix("tt").toLong()) else append("/query-").append(enc(searchText(name)))
            if (series) append("/season-").append(season)
        }
        suspend fun search(path: String): List<Result> {
            val found = runCatching {
                val (code, bytes) = getBytes("$REST$path/sublanguageid-$language3", USER_AGENT)
                if (code != 200) Log.w(TAG, "search answered $code")
                tryParseJson<List<Result>>(String(bytes, Charsets.UTF_8)).orEmpty()
            }.getOrElse { Log.w(TAG, "search failed: ${it.stackTraceToString().take(700)}"); emptyList() }
                .filter { it.downloadLink != null && (it.language ?: "").lowercase() == language2 }
            Log.i(TAG, "search $path: ${found.size} result(s) in $language2")
            return found
        }
        val hasImdb = imdb?.removePrefix("tt")?.toLongOrNull()?.let { it > 0 } == true
        return (if (hasImdb) search(pathOf(true)) else emptyList()).ifEmpty { search(pathOf(false)) }
    }

    /** A result's file (gzip) downloaded and unpacked into a temporary file */
    suspend fun downloadResult(link: String, name: String): File? {
        val gz = runCatching { getBytes(link, USER_AGENT).second }.onFailure { Log.w(TAG, "download failed: ${it.message}") }.getOrNull() ?: return null
        val text = runCatching { GZIPInputStream(gz.inputStream()).use { it.readBytes() } }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        Log.i(TAG, "downloaded '$name' (${text.size} bytes)")
        return write(text, name)
    }

    /**
     * Finds the subtitle (the same file when the name matches, else the most downloaded one of the language) and returns it unpacked
     * in a temporary file. The parts of the search path have to be in alphabetical order.
     */
    suspend fun download(name: String, fileName: String?, lang: String, imdb: String?, season: Int?, episode: Int?): File? {
        val results = search(name, lang, imdb, season, episode)
        val pick = results.firstOrNull { fileName != null && it.subFileName.equals(fileName, true) }
            ?: results.maxByOrNull { it.downloads?.toLongOrNull() ?: 0L } ?: return null
        return downloadResult(pick.downloadLink!!, pick.subFileName ?: name)
    }

    /** A subtitle's bytes as a temporary file with a proper extension (mpv decides by the extension and the content) */
    fun write(bytes: ByteArray, name: String): File {
        val ext = Regex("""\.(srt|vtt|ass|ssa|sub)$""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.lowercase() ?: "srt"
        return File.createTempFile("opensubtitles-", ".$ext").apply { writeBytes(bytes) }
    }
}
