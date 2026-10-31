package com.lagradost.desktop.isolate

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What crosses the pipe between the app and the sandbox: the page of a title with its related titles (an interface in the library) must come back whole */
class IsolationSerdeTest {
    private class Example : MainAPI() {
        override var name = "Example"
        override var mainUrl = "https://example.test"
    }

    private val api = Example()

    @Test
    fun aTitlePageWithRelatedTitlesComesBack() = runBlocking {
        val related = api.newMovieSearchResponse("Related film", "https://example.test/related", TvType.Movie) { posterUrl = "https://example.test/p.jpg" }
        val page = api.newTvSeriesLoadResponse("A series", "https://example.test/series", TvType.TvSeries, listOf(api.newEpisode("https://example.test/e1") { name = "One"; season = 1; episode = 1 })) {
            recommendations = listOf(related)
        }

        val node = IsolationSerde.write(page, LoadResponse::class.java)
        val back = IsolationSerde.read(node, LoadResponse::class.java)

        assertNotNull(back, "the page was read")
        assertTrue(back is TvSeriesLoadResponse)
        assertEquals("A series", back.name)
        assertEquals(1, back.episodes.size)
        val recommendations = back.recommendations
        assertNotNull(recommendations, "the related titles were read")
        assertEquals(1, recommendations.size)
        assertEquals("Related film", recommendations[0].name)
        assertEquals("https://example.test/related", recommendations[0].url)
    }

    @Test
    fun aTitlePageWithoutRelatedTitlesStaysWithout() = runBlocking {
        val page = api.newTvSeriesLoadResponse("Plain", "https://example.test/plain", TvType.TvSeries, listOf(api.newEpisode("https://example.test/p1") { episode = 1 }))
        val back = IsolationSerde.read(IsolationSerde.write(page, LoadResponse::class.java), LoadResponse::class.java)
        assertNotNull(back)
        assertNull(back.recommendations)
    }

    // a series page computes "the latest episode per dub status" with a getter (an immutable one-entry map); it is written as a property and must not be read back into that map
    @Test
    fun aSeriesPageWhoseEpisodesHaveNoNumbersComesBack() = runBlocking {
        val noNumbers = api.newTvSeriesLoadResponse("Numberless", "https://example.test/n", TvType.TvSeries, listOf(api.newEpisode("https://example.test/n1") { name = "Pilot" }))
        val back = IsolationSerde.read(IsolationSerde.write(noNumbers, LoadResponse::class.java), LoadResponse::class.java)
        assertNotNull(back, "a series page whose episodes have no season or number was read")
        assertEquals("Numberless", back.name)
    }

    @Test
    fun aSeriesPageWithSeasonsAndNumbersComesBack() = runBlocking {
        val episodes = (1..3).flatMap { s -> (1..4).map { e -> api.newEpisode("https://example.test/s${s}e$e") { name = "S${s}E$e"; season = s; episode = e } } }
        val page = api.newTvSeriesLoadResponse("Seasons", "https://example.test/seasons", TvType.TvSeries, episodes) { plot = "p"; seasonNames = listOf(com.lagradost.cloudstream3.SeasonData(1, "One")) }
        val back = IsolationSerde.read(IsolationSerde.write(page, LoadResponse::class.java), LoadResponse::class.java)
        assertNotNull(back, "a series page with seasons was read")
        assertTrue(back is TvSeriesLoadResponse)
        assertEquals(12, back.episodes.size)
    }

    @Test
    fun aSearchResultWithAnUnknownClassNameIsRefused() = runBlocking {
        val node = IsolationSerde.write(api.newMovieSearchResponse("x", "https://example.test/x", TvType.Movie), SearchResponse::class.java)
        (node as com.fasterxml.jackson.databind.node.ObjectNode).put("cls", "java.lang.ProcessBuilder")
        assertNull(IsolationSerde.read(node, SearchResponse::class.java), "a class outside the library is never built")
    }
}
