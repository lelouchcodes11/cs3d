package com.lagradost.cloudstream3.utils.videoskip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class IntroDbSkip : SkipAPI() {
    override val name = "IntroDb"

    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime,
        TvType.OVA,
        TvType.AnimeMovie,
        TvType.Cartoon,
        TvType.Movie,
    )

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        val isMovie = data.type == TvType.Movie || data.type == TvType.AnimeMovie
        val season = if (isMovie) null else (episode.season ?: 1)
        val epNum = if (isMovie) null else (episode.episode ?: 1)

        val isAnime = data is com.lagradost.cloudstream3.AnimeLoadResponse
            || data.type in setOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)
            || data.syncData.containsKey("mal")
            || data.syncData.containsKey("anilist")
            || data.syncData.containsKey("kitsu")
            || data.tags?.any { it.contains("anime", ignoreCase = true) } == true
            || data.apiName.contains("anime", ignoreCase = true)
        val imdbId = data.getImdbId()
            ?: runCatching {
                com.lagradost.desktop.tmdb.Tmdb.info(
                    title = data.name,
                    year = data.year,
                    isMovie = isMovie,
                    hints = data.syncData.values + listOfNotNull(data.getTMDbId()?.let { "tmdb:$it" })
                )?.let { info ->
                    if (isAnime && info.genres.none { it.equals("Animation", ignoreCase = true) }) {
                        null
                    } else {
                        info.imdbId
                    }
                }
            }.getOrNull()
            ?: return null

        val url = if (isMovie) {
            "https://api.introdb.app/segments?imdb_id=$imdbId&is_movie=true"
        } else {
            "https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$epNum"
        }
        val response = runCatching { app.get(url).parsed<IntroDbResponse>() }.getOrNull() ?: return null

        return listOfNotNull(
            response.intro?.let {
                val start = it.startMs ?: it.startSec?.let { s -> (s * 1000.0).toLong() } ?: return@let null
                val end = it.endMs ?: it.endSec?.let { s -> (s * 1000.0).toLong() } ?: return@let null
                SkipStamp(
                    type = SkipType.Opening,
                    startMs = start,
                    endMs = end
                )
            },
            response.recap?.let {
                val start = it.startMs ?: it.startSec?.let { s -> (s * 1000.0).toLong() } ?: return@let null
                val end = it.endMs ?: it.endSec?.let { s -> (s * 1000.0).toLong() } ?: return@let null
                SkipStamp(
                    type = SkipType.Recap,
                    startMs = start,
                    endMs = end
                )
            },
            response.outro?.let {
                val start = it.startMs ?: it.startSec?.let { s -> (s * 1000.0).toLong() } ?: return@let null
                val end = it.endMs ?: it.endSec?.let { s -> (s * 1000.0).toLong() } ?: return@let null
                SkipStamp(
                    type = SkipType.Ending,
                    startMs = start,
                    endMs = end
                )
            }
        )
    }

    @Serializable
    data class IntroDbResponse(
        @JsonProperty("imdb_id") @SerialName("imdb_id") val imdbId: String?,
        @JsonProperty("season") @SerialName("season") val season: Int?,
        @JsonProperty("episode") @SerialName("episode") val episode: Int?,
        @JsonProperty("intro") @SerialName("intro") val intro: Segment?,
        @JsonProperty("recap") @SerialName("recap") val recap: Segment?,
        @JsonProperty("outro") @SerialName("outro") val outro: Segment?,
    )

    @Serializable
    data class Segment(
        @JsonProperty("start_sec") @SerialName("start_sec") val startSec: Double?,
        @JsonProperty("end_sec") @SerialName("end_sec") val endSec: Double?,
        @JsonProperty("start_ms") @SerialName("start_ms") val startMs: Long?,
        @JsonProperty("end_ms") @SerialName("end_ms") val endMs: Long?,
        @JsonProperty("confidence") @SerialName("confidence") val confidence: Double?,
        @JsonProperty("submission_count") @SerialName("submission_count") val submissionCount: Int?,
        @JsonProperty("updated_at") @SerialName("updated_at") val updatedAt: String?,
    )
}
