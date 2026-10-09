package com.lagradost.cloudstream3.utils.videoskip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.isMovie
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.app
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://theintrodb.org/docs */
class TheIntroDBSkip : SkipAPI() {
    override val name = "TheIntroDB"
    override val supportedTypes = setOf(
        TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.OVA,
        TvType.AnimeMovie, TvType.Movie, TvType.AsianDrama
    )

    val mainUrl = "https://api.theintrodb.org"

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        val isMovie = data.isMovie()
        val isAnime = data is com.lagradost.cloudstream3.AnimeLoadResponse
            || data.type in setOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)
            || data.syncData.containsKey("mal")
            || data.syncData.containsKey("anilist")
            || data.syncData.containsKey("kitsu")
            || data.tags?.any { it.contains("anime", ignoreCase = true) } == true
            || data.apiName.contains("anime", ignoreCase = true)
        val idSuffix: String? =
            data.getTMDbId()?.let { tmdbId -> "tmdb_id=$tmdbId" }
                ?: data.getImdbId()?.let { imdbId -> "imdb_id=$imdbId" }
                ?: try {
                    com.lagradost.desktop.tmdb.Tmdb.info(
                        title = data.name,
                        year = data.year,
                        isMovie = isMovie,
                        hints = data.syncData.values
                    )?.let { info ->
                        if (isAnime && info.genres.none { it.equals("Animation", ignoreCase = true) }) {
                            null
                        } else if (info.id > 0) {
                            "tmdb_id=${info.id}"
                        } else {
                            info.imdbId?.let { "imdb_id=$it" }
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
        if (idSuffix == null) return null

        val url = if (isMovie) {
            "$mainUrl/v2/media?$idSuffix"
        } else {
            val season = episode.season ?: 1
            "$mainUrl/v2/media?$idSuffix&season=$season&episode=${episode.episode}"
        }
        val root = runCatching { app.get(url).parsed<Root>() }.getOrNull() ?: return null
        return arrayOf(
            root.intro to SkipType.Intro,
            root.credits to SkipType.Credits,
            root.recap to SkipType.Recap,
            root.preview to SkipType.Preview
        ).map { (list, type) ->
            list.map { stamp ->
                SkipStamp(
                    type,
                    stamp.startMs ?: 0L,
                    stamp.endMs ?: episodeDurationMs
                )
            }
        }.flatten()
    }

    @Serializable
    data class Root(
        @JsonProperty("tmdb_id") @SerialName("tmdb_id") val tmdbId: Long,
        @JsonProperty("type") @SerialName("type") val type: String,
        @JsonProperty("intro") @SerialName("intro") val intro: List<Stamp> = emptyList(),
        @JsonProperty("recap") @SerialName("recap") val recap: List<Stamp> = emptyList(),
        @JsonProperty("credits") @SerialName("credits") val credits: List<Stamp> = emptyList(),
        @JsonProperty("preview") @SerialName("preview") val preview: List<Stamp> = emptyList(),
    )

    @Serializable
    data class Stamp(
        @JsonProperty("start_ms") @SerialName("start_ms") val startMs: Long?,
        @JsonProperty("end_ms") @SerialName("end_ms") val endMs: Long?,
    )
}
