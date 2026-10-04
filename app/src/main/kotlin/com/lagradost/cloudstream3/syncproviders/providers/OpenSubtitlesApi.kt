package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromCodeToLangTagIETF
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromCodeToOpenSubtitlesTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OpenSubtitlesApi : SubtitleAPI() {
    override val name = "OpenSubtitles"
    override val idPrefix = "opensubtitles"

    override val icon = R.drawable.open_subtitles_icon
    override val hasInApp = true
    override val inAppLoginRequirement = AuthLoginRequirement(
        password = true,
        username = true,
    )

    override val createAccountUrl = "https://www.opensubtitles.com/en/users/sign_up"

    companion object {
        const val API_KEY = "uyBLgFD17MgrYmA0gSXoKllMJBelOYj2"
        const val HOST = "https://api.opensubtitles.com/api/v1"
        const val TAG = "OPENSUBS"
        const val COOLDOWN_DURATION: Long = 1000L * 30L // CoolDown if 429 error code in ms
        var currentCoolDown: Long = 0L
        const val userAgent = "Cloudstream3 v0.2"
        val headers = mapOf("user-agent" to userAgent, "Api-Key" to API_KEY)
    }

    private fun canDoRequest(): Boolean {
        return unixTimeMS > currentCoolDown
    }

    private fun throwIfCantDoRequest() {
        if (!canDoRequest()) {
            throw ErrorLoadingException("Too many requests wait for ${(currentCoolDown - unixTimeMS) / 1000L}s")
        }
    }

    private fun throwGotTooManyRequests() {
        currentCoolDown = unixTimeMS + COOLDOWN_DURATION
        throw ErrorLoadingException("Too many requests")
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        return login(parseJson<AuthLoginResponse>(token.payload ?: return null))
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = parseJson<AuthLoginResponse>(token?.payload ?: return null)
        val username = user.username ?: return null
        return AuthUser(
            id = username.hashCode(),
            name = username
        )
    }

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val username = form.username ?: return null
        val password = form.password ?: return null

        val response = app.post(
            url = "$HOST/login",
            headers = mapOf(
                "Content-Type" to "application/json",
            ) + headers,
            json = mapOf(
                "username" to username,
                "password" to password
            ),
        ).parsed<OAuthToken>()

        return AuthToken(
            accessToken = response.token
                ?: throw ErrorLoadingException("Invalid password or username"),
            /// JWT token is valid 24 hours after successfully authentication of user
            accessTokenLifetime = APIHolder.unixTime + 60 * 60 * 24,
            payload = form.toJson()
        )
    }

    /**
     * Fetch subtitles using token authenticated on previous method (see authorize).
     * Returns list of Subtitles which user can select to download (see load).
     */
    override suspend fun search(
        auth : AuthData?,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): List<AbstractSubtitleEntities.SubtitleEntity>? {
        // www.opensubtitles.com is blocked at the ISP in some countries (a court-order page in India, for the search as well as for files):
        // the older anonymous interface then answers the search
        if (!canDoRequest() || OpenSubtitlesLegacy.comLooksBlocked()) return legacySearch(query)
        val langOpenSubTag = fromCodeToOpenSubtitlesTag(query.lang) ?: query.lang ?: ""

        val imdbId = query.imdbId?.replace("tt", "")?.toInt() ?: 0
        val queryText = query.query
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0
        val epQuery = if (epNum > 0) "&episode_number=$epNum" else ""
        val seasonQuery = if (seasonNum > 0) "&season_number=$seasonNum" else ""
        val yearQuery = if (yearNum > 0) "&year=$yearNum" else ""

        val searchQueryUrl = when (imdbId > 0) {
            //Use imdb_id to search if its valid
            true -> "$HOST/subtitles?imdb_id=$imdbId&languages=${langOpenSubTag}$yearQuery$epQuery$seasonQuery"
            false -> "$HOST/subtitles?query=${queryText}&languages=${langOpenSubTag}$yearQuery$epQuery$seasonQuery"
        }

        val req = try {
            app.get(
                url = searchQueryUrl,
                headers = mapOf(
                    Pair("Content-Type", "application/json")
                ) + headers,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "search failed: ${t.message}")
            return legacySearch(query)
        }
        Log.i(TAG, "searchQueryUrl => ${searchQueryUrl}")
        val head = req.text.trimStart().take(300)
        val webPage = head.startsWith("<")
        Log.i(TAG, "Search Req => ${if (webPage) "a web page, not a result list: ${head.take(120)}" else req.text.take(300)}")
        if (webPage) OpenSubtitlesLegacy.markComBlocked()
        if (!req.isSuccessful || webPage) {
            if (req.code == 429)
                throwGotTooManyRequests()
            return legacySearch(query)
        }

        val results = mutableListOf<AbstractSubtitleEntities.SubtitleEntity>()

        AppUtils.tryParseJson<Results>(req.text)?.let {
            it.data?.forEach { item ->
                val attr = item.attributes ?: return@forEach
                val featureDetails = attr.featDetails
                //Use filename as name, if its valid
                val filename = attr.files?.firstNotNullOfOrNull { subfile ->
                    subfile.fileName
                }
                //Use any valid name/title in hierarchy
                val name = filename ?: featureDetails?.movieName ?: featureDetails?.title
                ?: featureDetails?.parentTitle ?: attr.release ?: query.query
                val langTagIETF = fromCodeToLangTagIETF(attr.language) ?: ""
                val resEpNum = featureDetails?.episodeNumber ?: query.epNumber
                val resSeasonNum = featureDetails?.seasonNumber ?: query.seasonNumber
                val year = featureDetails?.year ?: query.year
                val type = if ((resSeasonNum ?: 0) > 0) TvType.TvSeries else TvType.Movie
                val isHearingImpaired = attr.hearingImpaired ?: false
                //Log.i(TAG, "Result id/name => ${item.id} / $name")
                item.attributes?.files?.forEach { file ->
                    // file id, then what the older interface (the fallback of getResources) searches by: IMDb number and file name
                    val imdbOfTitle = if ((resSeasonNum ?: 0) > 0) (featureDetails?.parentImdbId ?: featureDetails?.imdbId) else (featureDetails?.imdbId ?: featureDetails?.parentImdbId)
                    val resultData = "${file.fileId ?: ""}|${imdbOfTitle ?: ""}|${file.fileName ?: ""}"
                    //Log.i(TAG, "Result file => ${file.fileId} / ${file.fileName}")
                    results.add(
                        AbstractSubtitleEntities.SubtitleEntity(
                            idPrefix = this.idPrefix,
                            name = name,
                            lang = langTagIETF,
                            data = resultData,
                            type = type,
                            source = this.name,
                            epNumber = resEpNum,
                            seasonNumber = resSeasonNum,
                            year = year,
                            isHearingImpaired = isHearingImpaired
                        )
                    )
                }
            }
        }
        // nothing from the new API: the older database may know the title
        if (results.isEmpty()) return legacySearch(query)
        return results
    }

    /** The search through the older anonymous interface; the result's data carries the file link so that no second search is needed */
    private suspend fun legacySearch(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity> {
        val imdb = query.imdbId?.takeIf { it.isNotBlank() }
        return OpenSubtitlesLegacy.search(query.query, query.lang ?: "en", imdb, query.seasonNumber, query.epNumber)
            .sortedByDescending { it.downloads?.toLongOrNull() ?: 0L }
            .take(40)
            .map { r ->
                val season = r.season?.toIntOrNull()?.takeIf { it > 0 } ?: query.seasonNumber
                val episode = r.episode?.toIntOrNull()?.takeIf { it > 0 } ?: query.epNumber
                AbstractSubtitleEntities.SubtitleEntity(
                    idPrefix = this.idPrefix,
                    name = r.subFileName ?: r.releaseName ?: r.movieName ?: query.query,
                    lang = fromCodeToLangTagIETF(r.language) ?: query.lang ?: "",
                    data = "|${r.imdb ?: ""}|${r.subFileName ?: ""}|${r.downloadLink ?: ""}",
                    type = if ((season ?: 0) > 0) TvType.TvSeries else TvType.Movie,
                    source = this.name,
                    epNumber = episode,
                    seasonNumber = season,
                    year = r.year?.toIntOrNull()?.takeIf { it > 0 } ?: query.year,
                    isHearingImpaired = r.hearingImpaired == "1",
                )
            }
    }

    /**
     * Process data returned from search.
     * Returns string url for the subtitle file.
     */
    override suspend fun load(
        auth : AuthData?,
        subtitle: AbstractSubtitleEntities.SubtitleEntity
    ): String? {
        if (auth == null) return null
        throwIfCantDoRequest()

        val req = app.post(
            url = "$HOST/download",
            headers = mapOf(
                Pair(
                    "Authorization",
                    "Bearer ${auth.token.accessToken ?: throw ErrorLoadingException("No access token active in current session")}"
                ),
                Pair("Content-Type", "application/json"),
                Pair("Accept", "*/*")
            ) + headers,
            data = mapOf(
                Pair("file_id", subtitle.data.substringBefore('|'))
            )
        )
        Log.i(TAG, "Request result  => (${req.code}) ${req.text}")
        //Log.i(TAG, "Request headers => ${req.headers}")
        if (req.isSuccessful) {
            AppUtils.tryParseJson<ResultDownloadLink>(req.text)?.let {
                val link = it.link ?: ""
                Log.i(TAG, "Request load link => $link")
                return link
            }
        } else {
            if (req.code == 429)
                throwGotTooManyRequests()
        }
        return null
    }

    /**
     * The subtitle file itself, not a link for the player to open: www.opensubtitles.com answers the file of some titles with a web page in some
     * countries (a court-order page in India), so the download is made here, and the older anonymous interface is the way around it.
     */
    override suspend fun com.lagradost.cloudstream3.subtitles.SubtitleResource.getResources(
        auth: AuthData?,
        subtitle: AbstractSubtitleEntities.SubtitleEntity
    ) {
        val parts = (subtitle.data + "|||").split('|')
        val fileId = parts[0]
        val imdb = parts[1]
        val fileName = parts[2]
        val legacyLink = parts[3]
        // a result of the older interface: its file is one request away
        if (legacyLink.isNotBlank()) {
            OpenSubtitlesLegacy.downloadResult(legacyLink, fileName.ifBlank { subtitle.name })?.let { addFile(it); return }
        }
        if (fileId.isNotBlank() && !OpenSubtitlesLegacy.comLooksBlocked()) {
            val link = try { load(auth, subtitle) } catch (t: Throwable) { Log.w(TAG, "download link: ${t.message}"); null }
            if (link != null) {
                val fetched = OpenSubtitlesLegacy.fetch(link)
                if (fetched.bytes != null) {
                    addFile(OpenSubtitlesLegacy.write(fetched.bytes, fileName.ifBlank { subtitle.name }))
                    return
                }
                if (fetched.webPage) OpenSubtitlesLegacy.markComBlocked()
            }
        }
        OpenSubtitlesLegacy.download(subtitle.name, fileName.ifBlank { null }, subtitle.lang, imdb.ifBlank { null }, subtitle.seasonNumber, subtitle.epNumber)?.let { addFile(it) }
    }

    @Serializable
    data class OAuthToken(
        @JsonProperty("token") @SerialName("token") var token: String? = null,
        @JsonProperty("status") @SerialName("status") var status: Int? = null,
    )

    @Serializable
    data class Results(
        @JsonProperty("data") @SerialName("data") var data: List<ResultData>? = listOf(),
    )

    @Serializable
    data class ResultData(
        @JsonProperty("id") @SerialName("id") var id: String? = null,
        @JsonProperty("type") @SerialName("type") var type: String? = null,
        @JsonProperty("attributes") @SerialName("attributes") var attributes: ResultAttributes? = ResultAttributes(),
    )

    @Serializable
    data class ResultAttributes(
        @JsonProperty("subtitle_id") @SerialName("subtitle_id") var subtitleId: String? = null,
        @JsonProperty("language") @SerialName("language") var language: String? = null,
        @JsonProperty("release") @SerialName("release") var release: String? = null,
        @JsonProperty("url") @SerialName("url") var url: String? = null,
        @JsonProperty("files") @SerialName("files") var files: List<ResultFiles>? = listOf(),
        @JsonProperty("feature_details") @SerialName("feature_details") var featDetails: ResultFeatureDetails? = ResultFeatureDetails(),
        @JsonProperty("hearing_impaired") @SerialName("hearing_impaired") var hearingImpaired: Boolean? = null,
    )

    @Serializable
    data class ResultFiles(
        @JsonProperty("file_id") @SerialName("file_id") var fileId: Int? = null,
        @JsonProperty("file_name") @SerialName("file_name") var fileName: String? = null,
    )

    @Serializable
    data class ResultDownloadLink(
        @JsonProperty("link") @SerialName("link") var link: String? = null,
        @JsonProperty("file_name") @SerialName("file_name") var fileName: String? = null,
        @JsonProperty("requests") @SerialName("requests") var requests: Int? = null,
        @JsonProperty("remaining") @SerialName("remaining") var remaining: Int? = null,
        @JsonProperty("message") @SerialName("message") var message: String? = null,
        @JsonProperty("reset_time") @SerialName("reset_time") var resetTime: String? = null,
        @JsonProperty("reset_time_utc") @SerialName("reset_time_utc") var resetTimeUtc: String? = null,
    )

    @Serializable
    data class ResultFeatureDetails(
        @JsonProperty("year") @SerialName("year") var year: Int? = null,
        @JsonProperty("title") @SerialName("title") var title: String? = null,
        @JsonProperty("movie_name") @SerialName("movie_name") var movieName: String? = null,
        @JsonProperty("imdb_id") @SerialName("imdb_id") var imdbId: Int? = null,
        @JsonProperty("tmdb_id") @SerialName("tmdb_id") var tmdbId: Int? = null,
        @JsonProperty("season_number") @SerialName("season_number") var seasonNumber: Int? = null,
        @JsonProperty("episode_number") @SerialName("episode_number") var episodeNumber: Int? = null,
        @JsonProperty("parent_imdb_id") @SerialName("parent_imdb_id") var parentImdbId: Int? = null,
        @JsonProperty("parent_title") @SerialName("parent_title") var parentTitle: String? = null,
        @JsonProperty("parent_tmdb_id") @SerialName("parent_tmdb_id") var parentTmdbId: Int? = null,
        @JsonProperty("parent_feature_id") @SerialName("parent_feature_id") var parentFeatureId: Int? = null,
    )
}
