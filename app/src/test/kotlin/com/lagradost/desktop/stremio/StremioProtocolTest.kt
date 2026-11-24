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
        // a slash after the manifest, and the add-on's settings page, are the same add-on
        assertEquals("https://x.io/cfg/manifest.json", StremioClient.normalizeManifestUrl("https://x.io/cfg/manifest.json/"))
        assertEquals("https://torrentio.strem.fun/providers=yts/manifest.json", StremioClient.normalizeManifestUrl("https://torrentio.strem.fun/providers=yts/configure"))
        assertEquals("https://torrentio.strem.fun/manifest.json", StremioClient.normalizeManifestUrl("https://torrentio.strem.fun/configure/"))
    }

    @Test
    fun runtimesInHoursAndMinutes() {
        assertEquals(142, StremioClient.runtimeMinutes("142 min"))
        assertEquals(142, StremioClient.runtimeMinutes("2h22min"))
        assertEquals(65, StremioClient.runtimeMinutes("1h 5m"))
        assertEquals(120, StremioClient.runtimeMinutes("2h"))
        assertEquals(45, StremioClient.runtimeMinutes("45"))
        assertNull(StremioClient.runtimeMinutes(null))
    }

    @Test
    fun resourceAddressesFollowTheSdk() {
        val m = "https://x.io/cfg/manifest.json?k=v"
        assertEquals("https://x.io/cfg", StremioClient.baseOf(m))
        assertEquals("https://x.io/cfg/stream/series/tt0903747%3A1%3A2.json?k=v", StremioClient.resourceUrl(m, "stream", "series", "tt0903747:1:2"))
        assertEquals("https://x.io/cfg/catalog/movie/top/search=big%20buck.json?k=v", StremioClient.resourceUrl(m, "catalog", "movie", "top", mapOf("search" to "big buck")))
        assertEquals("https://x.io/catalog/movie/top/genre=Sci-Fi&skip=100.json", StremioClient.resourceUrl("https://x.io/manifest.json", "catalog", "movie", "top", linkedMapOf("genre" to "Sci-Fi", "skip" to "100")))
    }

    @Test
    fun logAddressesHideConfigSegmentsAndSecrets() {
        assertEquals(
            "https://pengu.uk/…/manifest.json",
            StremioClient.safeUrl("https://pengu.uk/%7B%22auth_token%22%3A%22SECRET%22%7D/manifest.json"),
        )
        assertEquals(
            "https://torrentio.strem.fun/…/stream/movie/tt0133093.json",
            StremioClient.safeUrl("https://torrentio.strem.fun/realdebrid=MYKEY123/stream/movie/tt0133093.json?api_key=xyz"),
        )
        assertEquals(
            "https://v3-cinemeta.strem.io/manifest.json",
            StremioClient.safeUrl("https://v3-cinemeta.strem.io/manifest.json"),
        )
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
        // the stream's own trackers first, then every public one, each once
        val both = StremioStreams.magnetOf("a".repeat(40), null, listOf("udp://t.example:80/announce", "udp://tracker.opentrackr.org:1337/announce"))
        assertTrue(both.indexOf("t.example") < both.indexOf("open.stealth.si"))
        assertEquals(1, Regex("tracker\\.opentrackr\\.org").findAll(both).count())
        assertEquals(TorrentEngine.trackers.size + 1, Regex("&tr=").findAll(both).count())
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

    @Test
    fun torrentSeedTiers() {
        fun torrent(seeders: Int?) = kotlinx.coroutines.runBlocking {
            com.lagradost.cloudstream3.utils.newExtractorLink("Torrentio", "t", "magnet:?xt=urn:btih:" + "a".repeat(40), com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET) {
                extractorData = JSONObject().also { o -> seeders?.let { o.put("seeders", it) } }.toString()
            }
        }
        assertEquals(listOf(0, 0, 1, 2, 3, 4, 4), listOf(0, 2, 3, 10, 30, 100, 5000).map { TorrentEngine.seedTier(torrent(it)) })
        // no count from the add-on: unknown, not dead
        assertEquals(1, TorrentEngine.seedTier(torrent(null)))
        // a 1080p release with 500 seeders ranks above a 4K one with 5
        assertTrue(TorrentEngine.seedTier(torrent(500)) > TorrentEngine.seedTier(torrent(5)))
        // 480 and 500 seeders are the same step: quality decides between them
        assertEquals(TorrentEngine.seedTier(torrent(480)), TorrentEngine.seedTier(torrent(500)))
    }

    @Test
    fun torrentSpeedRule() {
        val gb = 1024L * 1024 * 1024
        // a 2 GB film, length unknown (100 min): needs ~350 KB/s; the 1-peer swarm measured here (120 KB/s) is too slow, 300 KB/s is not
        val film = TorrentEngine.neededBps(2 * gb, null, series = false)
        assertEquals(2 * gb / 6000, film)
        assertTrue(TorrentEngine.tooSlow(120L * 1024, film))
        assertFalse(TorrentEngine.tooSlow(300L * 1024, film))
        // the 7.1 GB 4K release needs ~1.2 MB/s
        assertTrue(TorrentEngine.tooSlow(500L * 1024, TorrentEngine.neededBps(7 * gb + gb / 10, null, series = false)))
        // a known length wins over the guess; a nonsense one (0, 20 h) does not
        assertEquals(gb / 1440, TorrentEngine.neededBps(gb, 24 * 60, series = true))
        assertEquals(gb / 2700, TorrentEngine.neededBps(gb, 0, series = true))
        assertEquals(gb / 6000, TorrentEngine.neededBps(gb, 20 * 3600, series = false))
        // a healthy swarm (Ubuntu: 25 MB in 30 s) plays even a 7 GB film
        assertFalse(TorrentEngine.tooSlow(25L * 1024 * 1024 / 30, TorrentEngine.neededBps(7 * gb, null, series = false)))
    }

    @Test
    fun aFileNumberThatPointsAtAnotherEpisodeIsNotTaken() {
        // the engine numbers files sorted by name; the add-on's number (3, i.e. id 4) counted in the torrent's own order and is E04 here
        val pack = files("BB/S01/BB.S01E01.mkv" to 900L, "BB/S01/BB.S01E02.mkv" to 950L, "BB/S01/BB.S01E03.mkv" to 940L, "BB/S01/BB.S01E04.mkv" to 930L)
        assertEquals(2, TorrentEngine.pickFile(pack, JSONObject().put("fileIdx", 3).put("season", 1).put("episode", 2))?.id)
        // a number that is the episode asked for is taken as it is
        assertEquals(4, TorrentEngine.pickFile(pack, JSONObject().put("fileIdx", 3).put("season", 1).put("episode", 4))?.id)
    }

    @Test
    fun seasonOneIsNotSeasonTen() {
        val pack = files("Show Season 1 Episode 2.mkv" to 900L, "Show Season 10 Episode 2.mkv" to 1000L)
        assertEquals(1, TorrentEngine.pickFile(pack, JSONObject().put("season", 1).put("episode", 2))?.id)
        assertEquals(2, TorrentEngine.pickFile(pack, JSONObject().put("season", 10).put("episode", 2))?.id)
    }

    @Test
    fun aPackOfSeveralSeasonsWithEpisodeOnlyNames() {
        // the season is in the folder only; season 1's file is the bigger one
        val pack = files("Show/Season 1/Show - 05.mkv" to 1000L, "Show/Season 2/Show - 05.mkv" to 900L, "Show/Season 2/Show - 06.mkv" to 950L)
        assertEquals(2, TorrentEngine.pickFile(pack, JSONObject().put("season", 2).put("episode", 5))?.id)
        assertEquals(1, TorrentEngine.pickFile(pack, JSONObject().put("season", 1).put("episode", 5))?.id)
        // the add-on's file number for such a name is taken (it named no "S02E05" to check against)
        assertEquals(2, TorrentEngine.pickFile(pack, JSONObject().put("fileIdx", 1).put("season", 2).put("episode", 5))?.id)
        // ... but not when its folder is another season
        assertEquals(2, TorrentEngine.pickFile(pack, JSONObject().put("fileIdx", 0).put("season", 2).put("episode", 5))?.id)
        // no season known: the episode alone, as before
        assertEquals(1, TorrentEngine.pickFile(pack, JSONObject().put("episode", 5))?.id)
        // a release whose own season count differs (only "S01E07" files, season 2 asked for) still gives its episode
        val other = files("Show 2nd/Show.S01E07.mkv" to 900L, "Show 2nd/Show.S01E08.mkv" to 950L)
        assertEquals(1, TorrentEngine.pickFile(other, JSONObject().put("season", 2).put("episode", 7))?.id)
    }

    @Test
    fun whichEpisodeAFileIs() {
        assertEquals(2, TorrentEngine.statedSeason("Show/Season 2/Show - 05.mkv"))
        assertEquals(3, TorrentEngine.statedSeason("Pack S01-S05/Show.S03E01.mkv"))
        assertNull(TorrentEngine.statedSeason("Batch/[Grp] Show - 07 (1080p).mkv"))
        // an anime batch's file is the next episode (the episode fetched ahead) when a season is known
        assertEquals(true, TorrentEngine.isEpisode("Batch/[Grp] Show - 07 (1080p).mkv", 1, 7))
        assertEquals(true, TorrentEngine.isEpisode("Show.S01E07.mkv", 1, 7))
        assertEquals(false, TorrentEngine.isEpisode("Show.S02E07.mkv", 1, 7))
        assertEquals(false, TorrentEngine.isEpisode("Season 2/Show - 07.mkv", 1, 7))
        assertNull(TorrentEngine.isEpisode("Film.mkv", null, null))
    }

    @Test
    fun subtitleFilesOfATorrent() {
        // a film with a Subs folder: every subtitle, by language, the second English one numbered
        val film = files("Film (2019)/Film.2019.1080p.mkv" to 2000L, "Film (2019)/Subs/2_English.srt" to 5L, "Film (2019)/Subs/3_English.srt" to 5L,
            "Film (2019)/Subs/4_French.srt" to 5L, "Film (2019)/Film.2019.en.forced.srt" to 5L, "Film (2019)/Sample/sample.mkv" to 20L)
        val f = TorrentEngine.subtitlesFor(film, film[0])
        assertEquals(listOf("English", "English 2", "French", "English (forced)"), f.map { it.name })
        assertEquals("French", f[2].language)
        // a season pack: only the episode's own (named after it, or in its folder)
        val pack = files("S01/Show.S01E01.720p.mkv" to 900L, "S01/Show.S01E02.720p.mkv" to 950L, "S01/Show.S01E01.720p.srt" to 3L,
            "S01/Subs/Show.S01E02.720p/2_English.srt" to 3L, "S01/Subs/Show.S01E02.720p/5_Spanish.srt" to 3L, "S01/Subs/Show.S01E12.720p/2_English.srt" to 3L)
        assertEquals(listOf("English", "Spanish"), TorrentEngine.subtitlesFor(pack, pack[1]).map { it.name })
        assertEquals(listOf("Show.S01E01.720p"), TorrentEngine.subtitlesFor(pack, pack[0]).map { it.name })
        // a release group is not a language
        assertNull(TorrentEngine.languageOf("Show.S01E01.720p.HDTV.x264-GRP.srt"))
        assertEquals("English", TorrentEngine.languageOf("Film.eng.srt"))
        assertEquals("Portuguese", TorrentEngine.languageOf("Film.pt.srt"))
    }

    @Test
    fun theHashOfATorrentSource() = kotlinx.coroutines.runBlocking {
        val h = "6BBAD06245A0D631B0A5E47C8CD6E5ABC9A70211"
        val named = com.lagradost.cloudstream3.utils.newExtractorLink("Torrentio", "x", "magnet:?xt=urn:btih:ffff", com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET) { extractorData = JSONObject().put("hash", h).toString() }
        assertEquals(h.lowercase(), TorrentEngine.hashOf(named))
        val magnet = com.lagradost.cloudstream3.utils.newExtractorLink("Nyaa", "x", "magnet:?xt=urn:btih:$h&dn=a", com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET)
        assertEquals(h.lowercase(), TorrentEngine.hashOf(magnet))
        assertNull(TorrentEngine.hashOf(com.lagradost.cloudstream3.utils.newExtractorLink("X", "x", "https://a.b/c.torrent")))
    }

    @Test
    fun aFarTooSlowTorrentIsLeftEarly() {
        // a 7 GB episode needs ~2.7 MB/s: 300 KB/s at 12 s is hopeless; a 600 MB one (~230 KB/s) at 150 KB/s is not, it is judged again at 20 s
        assertTrue(TorrentEngine.hopeless(300L * 1024, 2772L * 1024))
        assertFalse(TorrentEngine.hopeless(150L * 1024, 230L * 1024))
        assertTrue(TorrentEngine.tooSlow(100L * 1024, 230L * 1024))
    }

    @Test
    fun aTorrentTooBigToStreamIsTriedLast() = kotlinx.coroutines.runBlocking {
        fun t(name: String, episode: Boolean) = kotlinx.coroutines.runBlocking {
            com.lagradost.cloudstream3.utils.newExtractorLink("Torrentio", name, "magnet:?xt=urn:btih:6bbad06245a0d631b0a5e47c8cd6e5abc9a70211", com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET) {
                extractorData = JSONObject().put("hash", "6bbad06245a0d631b0a5e47c8cd6e5abc9a70211").also { if (episode) it.put("episode", 2) }.toString()
            }
        }
        assertTrue(TorrentEngine.tooHeavy(t("[Torrentio] 1080p · S01E02 BDRip 👤 40 💾 7.14 GB", episode = true)))
        assertFalse(TorrentEngine.tooHeavy(t("[Torrentio] 1080p · S01E02 x265 👤 40 💾 599 MB", episode = true)))
        // a 5 GB film is fine, a 60 GB remux is not
        assertFalse(TorrentEngine.tooHeavy(t("[Torrentio] 1080p · Film 👤 40 💾 5.1 GB", episode = false)))
        assertTrue(TorrentEngine.tooHeavy(t("[Torrentio] 4k · Film REMUX 👤 40 💾 60 GB", episode = false)))
    }
}
