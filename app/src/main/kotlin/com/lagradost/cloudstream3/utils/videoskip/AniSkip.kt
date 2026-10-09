package com.lagradost.cloudstream3.utils.videoskip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// taken from https://github.com/saikou-app/saikou/blob/3803f8a7a59b826ca193664d46af3a22bbc989f7/app/src/main/java/ani/saikou/others/AniSkip.kt
// the following is GPLv3 code https://github.com/saikou-app/saikou/blob/main/LICENSE.md
class AniSkip : SkipAPI() {
    override val name: String = "AniSkip"
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Anime, TvType.OVA, TvType.AnimeMovie, TvType.TvSeries, TvType.Movie
    )

    data class AnimeMeta(
        val malId: Int,
        val aniId: Int?,
        val episodes: Int?,
        val seasonMalIds: Map<Int, Int> = emptyMap(),
        val seasonEpCounts: Map<Int, Int> = emptyMap()
    )

    companion object {
        private val metaCache = ConcurrentHashMap<String, AnimeMeta>()

        fun cleanTitle(title: String): String {
            return title
                .replace(Regex("""\s*[\(\[][^)\]]*[\)\]]"""), " ")
                .replace(Regex("""\s+(dub|sub|dubbed|subbed|uncut|censored|uncensored|hindi|english|multi|4k|1080p|720p)\b.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { title }
        }

        fun extractSeasonNumber(title: String): Int? {
            val lower = title.lowercase()
            val match = Regex("""(?:season|series|part)\s*(\d+)""").find(lower)
                ?: Regex("""(\d+)(?:st|nd|rd|th)\s*season""").find(lower)
                ?: Regex("""\bseason\s*(iv|iii|ii|i)\b""").find(lower)
            if (match != null) {
                val numStr = match.groupValues[1]
                return when (numStr) {
                    "i" -> 1
                    "ii" -> 2
                    "iii" -> 3
                    "iv" -> 4
                    else -> numStr.toIntOrNull()
                }
            }
            return null
        }
    }

    private suspend fun resolveFromAniList(aniId: Int?, title: String?, season: Int?): AnimeMeta? {
        val cacheKey = when {
            aniId != null -> "id:$aniId"
            !title.isNullOrBlank() -> "title:${cleanTitle(title).lowercase()}:${season ?: 1}"
            else -> return null
        }
        metaCache[cacheKey]?.let { return it }

        val relationsFragment = """
          relations {
            edges {
              relationType(version: 2)
              node {
                id
                idMal
                format
                episodes
                title { romaji english userPreferred }
                relations {
                  edges {
                    relationType(version: 2)
                    node {
                      id
                      idMal
                      format
                      episodes
                      title { romaji english userPreferred }
                      relations {
                        edges {
                          relationType(version: 2)
                          node {
                            id
                            idMal
                            format
                            episodes
                            title { romaji english userPreferred }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        """.trimIndent()

        val query = if (aniId != null) {
            """
            query {
              Media(id: $aniId, type: ANIME) {
                id
                idMal
                episodes
                title { romaji english userPreferred }
                $relationsFragment
              }
            }
            """.trimIndent()
        } else {
            """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 10) {
                media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  idMal
                  format
                  episodes
                  title { romaji english userPreferred }
                  $relationsFragment
                }
              }
            }
            """.trimIndent()
        }

        val body = mutableMapOf<String, Any>("query" to query)
        if (aniId == null && title != null) {
            body["variables"] = mapOf("search" to cleanTitle(title))
        }

        val json = runCatching {
            JSONObject(app.post("https://graphql.anilist.co", json = body, timeout = 15).text)
        }.getOrNull() ?: return null

        val seasonMals = mutableMapOf<Int, Int>()
        val seasonEps = mutableMapOf<Int, Int>()

        fun recordSeason(s: Int, mal: Int, epCount: Int) {
            if (mal > 0 && !seasonMals.containsKey(s)) {
                seasonMals[s] = mal
            }
            if (epCount > 0 && !seasonEps.containsKey(s)) {
                seasonEps[s] = epCount
            }
        }

        fun parseNodeSequels(node: JSONObject, currentSeason: Int) {
            val edges = node.optJSONObject("relations")?.optJSONArray("edges") ?: return
            for (i in 0 until edges.length()) {
                val edge = edges.getJSONObject(i)
                if (edge.optString("relationType") == "SEQUEL") {
                    val nextNode = edge.optJSONObject("node") ?: continue
                    val nextMal = nextNode.optInt("idMal", 0)
                    val nextEps = nextNode.optInt("episodes", 0)
                    val nextSeason = currentSeason + 1
                    recordSeason(nextSeason, nextMal, nextEps)
                    parseNodeSequels(nextNode, nextSeason)
                }
            }
        }

        var primaryNode: JSONObject? = null
        if (aniId != null) {
            primaryNode = json.optJSONObject("data")?.optJSONObject("Media")
        } else {
            val mediaArray = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
            if (mediaArray != null && mediaArray.length() > 0) {
                for (i in 0 until mediaArray.length()) {
                    val m = mediaArray.getJSONObject(i)
                    val tObj = m.optJSONObject("title")
                    val eng = tObj?.optString("english").orEmpty()
                    val rom = tObj?.optString("romaji").orEmpty()
                    val user = tObj?.optString("userPreferred").orEmpty()
                    val fullT = "$eng $rom $user"
                    val detectedSeason = extractSeasonNumber(fullT)
                    val mMal = m.optInt("idMal", 0)
                    val mEps = m.optInt("episodes", 0)
                    if (detectedSeason != null) {
                        recordSeason(detectedSeason, mMal, mEps)
                    } else if (primaryNode == null) {
                        primaryNode = m
                    }
                }
                if (primaryNode == null) {
                    primaryNode = mediaArray.getJSONObject(0)
                }
            }
        }

        if (primaryNode == null) return null
        val idMal = primaryNode.optInt("idMal", 0).takeIf { it > 0 } ?: return null
        val id = primaryNode.optInt("id", 0).takeIf { it > 0 }
        val eps = primaryNode.optInt("episodes", 0).takeIf { it > 0 }

        recordSeason(1, idMal, eps ?: 0)
        parseNodeSequels(primaryNode, 1)

        // If target season MAL ID is still unresolved and season > 1, query AniList directly for that season
        if (season != null && season > 1 && !seasonMals.containsKey(season) && title != null) {
            val seasonBody = mapOf(
                "query" to "query (\$search: String) { Page(page: 1, perPage: 3) { media(search: \$search, type: ANIME, sort: SEARCH_MATCH) { id idMal episodes } } }",
                "variables" to mapOf("search" to "${cleanTitle(title)} Season $season")
            )
            val seasonJson = runCatching {
                JSONObject(app.post("https://graphql.anilist.co", json = seasonBody, timeout = 10).text)
            }.getOrNull()
            val seasonArr = seasonJson?.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
            if (seasonArr != null && seasonArr.length() > 0) {
                val sObj = seasonArr.getJSONObject(0)
                val sMal = sObj.optInt("idMal", 0)
                val sEps = sObj.optInt("episodes", 0)
                if (sMal > 0) recordSeason(season, sMal, sEps)
            }
        }

        val meta = AnimeMeta(
            malId = seasonMals[1] ?: idMal,
            aniId = id,
            episodes = seasonEps[1] ?: eps,
            seasonMalIds = seasonMals,
            seasonEpCounts = seasonEps
        )
        metaCache[cacheKey] = meta
        return meta
    }

    private suspend fun fetchStamps(malId: Int, episodeNumber: Int, episodeDurationMs: Long): List<SkipStamp>? {
        val durationSec = if (episodeDurationMs > 0) episodeDurationMs / 1000L else 0L
        val lengthParam = if (durationSec > 0) "&episodeLength=$durationSec" else ""
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap$lengthParam"
        val response = runCatching { app.get(url).parsed<AniSkipResponse>() }.getOrNull() ?: return null
        if (!response.found || response.results.isNullOrEmpty()) return null

        return response.results.mapNotNull { stamp ->
            val skipType = when (stamp.skipType) {
                "op" -> SkipType.Opening
                "ed" -> SkipType.Ending
                "recap" -> SkipType.Recap
                "mixed-ed" -> SkipType.MixedEnding
                "mixed-op" -> SkipType.MixedOpening
                else -> null
            } ?: return@mapNotNull null
            val end = (stamp.interval.endTime * 1000.0).toLong()
            val start = (stamp.interval.startTime * 1000.0).toLong()
            SkipStamp(
                type = skipType,
                startMs = start,
                endMs = end,
            )
        }
    }

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        val isExplicitAnime = data is AnimeLoadResponse
            || data.type in setOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)
            || data.syncData.containsKey("mal")
            || data.syncData.containsKey("anilist")
            || data.syncData.containsKey("kitsu")
            || data.tags?.any { it.contains("anime", ignoreCase = true) } == true
            || data.apiName.contains("anime", ignoreCase = true)

        if (!isExplicitAnime) return null

        val directMalId = data.getMalId()?.toIntOrNull() ?: data.syncData["mal"]?.toIntOrNull()
        val aniId = data.getAniListId()?.toIntOrNull() ?: data.syncData["anilist"]?.toIntOrNull()
        val season = episode.season ?: 1
        val epNum = episode.episode ?: 1

        // If direct MAL ID is present and we're on Season 1, try it first
        if (directMalId != null && (season <= 1 || episode.season == null)) {
            val directStamps = fetchStamps(directMalId, epNum, episodeDurationMs)
                ?: if (episodeDurationMs > 0) fetchStamps(directMalId, epNum, 0L) else null
            if (!directStamps.isNullOrEmpty()) return directStamps
        }

        // Resolve meta via AniList GraphQL (by AniList ID or anime title)
        val meta = resolveFromAniList(aniId, data.name, episode.season)
            ?: (if (directMalId != null) AnimeMeta(directMalId, null, null) else null)
            ?: return null

        // Determine target MAL ID for the season
        val targetMalId = if (season > 1 && meta.seasonMalIds.containsKey(season)) {
            meta.seasonMalIds[season]!!
        } else {
            directMalId ?: meta.malId
        }

        // 1. Try with given episode number
        var stamps = fetchStamps(targetMalId, epNum, episodeDurationMs)
            ?: if (episodeDurationMs > 0) fetchStamps(targetMalId, epNum, 0L) else null
        if (!stamps.isNullOrEmpty()) return stamps

        // 2. Try handling absolute vs season numbering offset:
        // Case A: epNum is absolute (e.g. Ep 38, but season is 3)
        if (season > 1) {
            val prevEpisodes = (1 until season).sumOf { meta.seasonEpCounts[it] ?: 0 }
            if (prevEpisodes > 0 && epNum > prevEpisodes) {
                val relativeEp = epNum - prevEpisodes
                stamps = fetchStamps(targetMalId, relativeEp, episodeDurationMs)
                    ?: if (episodeDurationMs > 0) fetchStamps(targetMalId, relativeEp, 0L) else null
                if (!stamps.isNullOrEmpty()) return stamps
            }
        }

        // Case B: epNum is absolute across the whole show, but season was 1 or null (e.g. epNum = 38, season = 1)
        if (season <= 1 && meta.seasonEpCounts.isNotEmpty()) {
            var accumulated = 0
            for (s in 1..10) {
                val count = meta.seasonEpCounts[s] ?: break
                if (epNum > accumulated && epNum <= accumulated + count) {
                    val sMalId = meta.seasonMalIds[s]
                    val relEp = epNum - accumulated
                    if (sMalId != null) {
                        stamps = fetchStamps(sMalId, relEp, episodeDurationMs)
                            ?: if (episodeDurationMs > 0) fetchStamps(sMalId, relEp, 0L) else null
                        if (!stamps.isNullOrEmpty()) return stamps
                    }
                    break
                }
                accumulated += count
            }
        }

        // Case C: epNum is season-relative (e.g. Season 2 Ep 1), but MAL only has one continuous entry (e.g. Naruto Shippuden / Black Clover)
        if (season > 1 && (meta.seasonMalIds.size <= 1 || targetMalId == meta.malId)) {
            val prevEpisodes = (1 until season).sumOf { meta.seasonEpCounts[it] ?: 0 }
            if (prevEpisodes > 0) {
                val absoluteEp = prevEpisodes + epNum
                stamps = fetchStamps(meta.malId, absoluteEp, episodeDurationMs)
                    ?: if (episodeDurationMs > 0) fetchStamps(meta.malId, absoluteEp, 0L) else null
                if (!stamps.isNullOrEmpty()) return stamps
            }
        }

        return null
    }

    @Serializable
    data class AniSkipResponse(
        @JsonProperty("found") @SerialName("found") val found: Boolean,
        @JsonProperty("results") @SerialName("results") val results: List<Stamp>?,
        @JsonProperty("message") @SerialName("message") val message: String?,
        @JsonProperty("statusCode") @SerialName("statusCode") val statusCode: Int,
    )

    @Serializable
    data class Stamp(
        @JsonProperty("interval") @SerialName("interval") val interval: AniSkipInterval,
        @JsonProperty("skipType") @SerialName("skipType") val skipType: String,
        @JsonProperty("skipId") @SerialName("skipId") val skipId: String,
        @JsonProperty("episodeLength") @SerialName("episodeLength") val episodeLength: Double,
    )

    @Serializable
    data class AniSkipInterval(
        @JsonProperty("startTime") @SerialName("startTime") val startTime: Double,
        @JsonProperty("endTime") @SerialName("endTime") val endTime: Double,
    )
}
