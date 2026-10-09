package com.lagradost.desktop.stremio

import com.lagradost.desktop.torrent.TorrentEngine
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The add-on protocol (addresses, manifests, streams) and the torrent file choice: no network, no engine */
class StremioProtocolTest {

    @Test
    fun manifestAddressesAreNormalised() {
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", StremioClient.normalizeManifestUrl("stremio://v3-cinemeta.strem.io/manifest.json"))
        assertEquals("https://torrentio.strem.fun/manifest.json", StremioClient.normalizeManifestUrl("torrentio.strem.fun"))
        assertEquals("https://x.io/providers=yts|sort=seeders/manifest.json", StremioClient.normalizeManifestUrl("https://x.io/providers=yts|sort=seeders/"))
        assertEquals("https://x.io/manifest.json?token=a", StremioClient.normalizeManifestUrl("https://x.io?token=a"))
        assertEquals("http://127.0.0.1:7000/manifest.json", StremioClient.normalizeManifestUrl(" http://127.0.0.1:7000/manifest.json "))
    }

    @Test
    fun resourceAddressesFollowTheSdk() {
        val m = "https://x.io/cfg/manifest.json?k=v"
        assertEquals("https://x.io/cfg", StremioClient.baseOf(m))
        assertEquals("https://x.io/cfg/stream/series/tt0903747%3A1%3A2.json?k=v", StremioClient.resourceUrl(m, "stream", "series", "tt0903747:1:2"))
        assertEquals("https://x.io/cfg/catalog/movie/top/search=big%20buck.json?k=v", StremioClient.resourceUrl(m, "catalog", "movie", "top", mapOf("search" to "big buck")))
        assertEquals("https://x.io/catalog/movie/top/genre=Sci-Fi&skip=100.json", StremioClient.resourceUrl("https://x.io/manifest.json", "catalog", "movie", "top", linkedMapOf("genre" to "Sci-Fi", "skip" to "100")))
    }

    private val manifest = """
        {"id":"a.b","name":"Test","version":"1.2.3","description":"Streams torrents","types":["movie","series"],"idPrefixes":["tt"],
         "resources":["catalog",{"name":"stream","types":["series"],"idPrefixes":["tt","kitsu"]},{"name":"subtitles"}],
         "catalogs":[{"type":"movie","id":"top","name":"Popular","extra":[{"name":"search","isRequired":true},{"name":"genre","options":["Action","Drama"]},{"name":"skip"}]},
                     {"type":"series","id":"new","name":"New","extraSupported":["skip"]}],
         "behaviorHints":{"configurable":true,"p2p":true}}
    """.trimIndent()

    @Test
    fun manifestIsReadIncludingBothResourceShapes() {
        val m = StremioManifest.parse(manifest)
        assertEquals("Test", m.name)
        assertTrue(m.providesStreams && m.providesSubtitles && m.providesCatalogs && !m.providesMeta)
        assertTrue(m.configurable && m.torrents)
        val popular = m.catalogs.first { it.id == "top" }
        assertTrue(popular.supportsSearch && popular.supportsSkip && popular.needsExtra)
        assertEquals(listOf("Action", "Drama"), popular.genres)
        val fresh = m.catalogs.first { it.id == "new" }
        assertTrue(fresh.supportsSkip && !fresh.needsExtra)
        // a resource with its own types and prefixes narrows the add-on's; one without takes the add-on's
        assertTrue(m.provides("stream", "series", "kitsu:12"))
        assertFalse(m.provides("stream", "movie", "tt1"))
        assertTrue(m.provides("subtitles", "movie", "tt1"))
        assertFalse(m.provides("subtitles", "movie", "kitsu:1"))
        assertFalse(m.provides("meta", "movie", "tt1"))
    }

    @Test
    fun streamsOfAllKindsAreRead() {
        val json = JSONObject(
            """{"streams":[
              {"name":"Torrentio 4k","title":"Movie.2160p","infoHash":"ABCDEF0123456789ABCDEF0123456789ABCDEF01","fileIdx":2,"sources":["tracker:udp://t.example:80/announce","dht:abc"],"behaviorHints":{"filename":"Movie.mkv"}},
              {"name":"Direct","url":"https://cdn.example/v.m3u8","behaviorHints":{"proxyHeaders":{"request":{"Referer":"https://r.example/"}},"notWebReady":true}},
              {"ytId":"dQw4w9WgXcQ","name":"Trailer"},
              {"externalUrl":"https://site.example/watch","name":"Web"}]}""",
        )
        val s = StremioClient.parseStreams(json)
        assertEquals(4, s.size)
        assertEquals("abcdef0123456789abcdef0123456789abcdef01", s[0].infoHash)
        assertEquals(2, s[0].fileIdx)
        assertEquals(listOf("udp://t.example:80/announce"), s[0].trackers)
        assertEquals("Movie.mkv", s[0].filename)
        assertEquals("https://r.example/", s[1].headers["Referer"])
        assertTrue(s[1].notWebReady)
        assertEquals("dQw4w9WgXcQ", s[2].ytId)
        assertEquals("https://site.example/watch", s[3].externalUrl)
        assertNull(s[3].url)
    }

    @Test
    fun magnetAndQualityHelpers() {
        val magnet = StremioStreams.magnetOf("ABCDEF0123456789ABCDEF0123456789ABCDEF01", "A Film (2010).mkv", listOf("udp://t.example:80/announce"))
        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&dn=A%20Film%20%282010%29.mkv&tr=udp%3A%2F%2Ft.example%3A80%2Fannounce"))
        assertTrue(StremioStreams.magnetOf("a".repeat(40), null, emptyList()).contains("&tr=udp%3A%2F%2Ftracker.opentrackr.org"))
        assertEquals(2160, StremioStreams.qualityOf("Movie 4k HDR"))
        assertEquals(1080, StremioStreams.qualityOf("Show.S01E01.1080p.WEB"))
        assertEquals(720, StremioStreams.qualityOf("720p BluRay"))
        assertEquals(0, StremioStreams.qualityOf("Some Video"))
    }

    @Test
    fun metaWithEpisodesIsRead() {
        val j = JSONObject(
            """{"meta":{"id":"tt0903747","type":"series","name":"Breaking Bad","releaseInfo":"2008-2013","imdbRating":"9.5","genres":["Crime"],
               "videos":[{"id":"tt0903747:1:1","title":"Pilot","season":1,"episode":1,"released":"2008-01-20T00:00:00.000Z"},{"id":"tt0903747:0:1","name":"Special","season":0,"episode":1}]}}""",
        )
        val m = StremioClient.parseMeta(j.getJSONObject("meta"))
        assertEquals(2008, m.year)
        assertEquals(9.5, m.rating)
        assertEquals("tt0903747", m.imdbId)
        assertEquals(2, m.videos.size)
        assertEquals(0, m.videos[1].season)
        assertEquals("Special", m.videos[1].title)
    }

    private fun files(vararg f: Pair<String, Long>) = f.mapIndexed { i, (p, n) -> TorrentEngine.TFile(i + 1, p, n) }

    @Test
    fun theVideoOfATorrentIsChosen() {
        val pack = files("Show S01/Show.S01E01.mkv" to 900L, "Show S01/Show.S01E02.mkv" to 950L, "Show S01/Show.S01E10.mkv" to 800L, "Show S01/readme.txt" to 1L, "Show S01/Subs/e02.srt" to 2L)
        // the add-on named the file (0-based index): that one
        assertEquals(3, TorrentEngine.pickFile(pack, JSONObject().put("fileIdx", 2))?.id)
        // no number: the episode asked for, and E1 must not match E10
        assertEquals(2, TorrentEngine.pickFile(pack, JSONObject().put("season", 1).put("episode", 2))?.id)
        assertEquals(1, TorrentEngine.pickFile(pack, JSONObject().put("season", 1).put("episode", 1))?.id)
        // a file name hint
        assertEquals(3, TorrentEngine.pickFile(pack, JSONObject().put("filename", "Show.S01E10.mkv"))?.id)
        // nothing to go by: the biggest video
        assertEquals(2, TorrentEngine.pickFile(pack, null)?.id)
        // a number that is not a video falls back to the biggest video
        assertNotNull(TorrentEngine.pickFile(pack, JSONObject().put("fileIdx", 3)))
        assertEquals(2, TorrentEngine.pickFile(pack, JSONObject().put("fileIdx", 3))?.id)
    }
}
