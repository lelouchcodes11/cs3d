package com.lagradost.desktop.iptv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** M3U playlists as IPTV services and Kodi write them: names, groups, logos, headers, ClearKey keys and the guide address */
class IptvParseTest {
    private val playlist = """
        #EXTM3U url-tvg="https://epg.example/guide.xml.gz" x-tvg-url="https://b.example/g.xml"
        #EXTINF:-1 tvg-id="news.uk" tvg-logo="https://l.example/news.png" group-title="News, World",News 24, Live
        #EXTVLCOPT:http-user-agent=OTT Navigator/1.7
        #EXTVLCOPT:http-referrer=https://site.example/
        https://cdn.example/news/index.m3u8
        #EXTINF:-1 group-title="Sports",Sport One HD
        #KODIPROP:inputstream.adaptive.license_type=clearkey
        #KODIPROP:inputstream.adaptive.license_key=0123abcd:89efcdab
        #EXTHTTP:{"cookie":"hdntl=abc"}
        https://cdn.example/sport/manifest.mpd
        #EXTINF:-1,Plain Channel
        http://plain.example/stream.ts|User-Agent=Mozilla%2F5.0&Referer=https://r.example/
    """.trimIndent()

    @Test
    fun channelsGroupsAndHeaders() {
        val (channels, guides) = Channels.parseM3u(playlist)
        assertEquals(listOf("https://epg.example/guide.xml.gz", "https://b.example/g.xml"), guides)
        assertEquals(3, channels.size)

        val news = channels[0]
        assertEquals("News 24, Live", news.name)
        assertEquals("News, World", news.group)
        assertEquals("news.uk", news.tvgId)
        assertEquals("https://l.example/news.png", news.logo)
        assertEquals(mapOf("User-Agent" to "OTT Navigator/1.7", "Referer" to "https://site.example/"), news.headers)
        assertNull(news.licenseKey)

        val sport = channels[1]
        assertEquals("Sport One HD", sport.name)
        assertEquals("0123abcd:89efcdab", sport.licenseKey)
        assertEquals(mapOf("Cookie" to "hdntl=abc"), sport.headers)

        val plain = channels[2]
        assertEquals("Plain Channel", plain.name)
        assertEquals("Channels", plain.group)
        assertEquals("http://plain.example/stream.ts", plain.url)
        assertEquals(mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://r.example/"), plain.headers)
    }

    /** JioTV-style lists put the key and the agent BEFORE #EXTINF; they were dropped when #EXTINF came */
    @Test
    fun optionsBeforeExtinfBelongToTheNextAddress() {
        val list = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=https://keys.example/ck?id=7
            #EXTVLCOPT:http-user-agent=plaYtv/7.1.3
            #EXTINF:-1 tvg-id="1" group-title="Movies",Star Gold
            https://jio.example/gold/index.mpd
            #EXTINF:-1 group-title="Movies",Plain After
            https://plain.example/after.m3u8
        """.trimIndent()
        val (channels, _) = Channels.parseM3u(list)
        assertEquals(2, channels.size)
        assertEquals("https://keys.example/ck?id=7", channels[0].licenseUrl)
        assertEquals(mapOf("User-Agent" to "plaYtv/7.1.3"), channels[0].headers)
        // nothing carries over to the next channel
        assertNull(channels[1].licenseUrl)
        assertEquals(emptyMap(), channels[1].headers)
    }

    @Test
    fun channelSurvivesTheTripThroughLinkData() {
        val c = Channels.parseM3u(playlist).first[1]
        assertEquals(c, IptvChannel.fromJson(c.toJson()))
    }

    @Test
    fun quotesPercentSignsAndRelativeAddresses() {
        val list = """
            #EXTM3U
            #EXTINF:-1 tvg-name="News, 24" group-title="UK",BBC "HD"
            ch1/index.m3u8|User-Agent=Foo 100%
            #EXTINF:-1,Second
            /live/two.m3u8
        """.trimIndent()
        val (channels, _) = Channels.parseM3u(list, "https://host.example/lists/tv.m3u")
        assertEquals(2, channels.size)
        assertEquals("BBC \"HD\"", channels[0].name)
        assertEquals("UK", channels[0].group)
        assertEquals("https://host.example/lists/ch1/index.m3u8", channels[0].url)
        assertEquals("Foo 100%", channels[0].headers["User-Agent"])
        assertEquals("https://host.example/live/two.m3u8", channels[1].url)
    }

    @Test
    fun extgrpGroupsAndKodiStreamHeaders() {
        val list = """
            #EXTM3U
            #EXTINF:-1,News One
            #EXTGRP:News
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Mozilla%2F5.0&Referer=https://r.example/
            https://cdn.example/one.mpd
            #EXTINF:-1 group-title="Sports",Sport One
            #EXTGRP:Ignored
            https://cdn.example/two.m3u8
            #EXTINF:-1,No Group
            https://cdn.example/three.m3u8
        """.trimIndent()
        val (channels, _) = Channels.parseM3u(list)
        assertEquals(listOf("News", "Sports", "Channels"), channels.map { it.group })
        assertEquals(mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://r.example/"), channels[0].headers)
        assertEquals(emptyMap(), channels[1].headers)
    }

    @Test
    fun bareAddressesWithoutExtinfAreChannels() {
        val (channels, _) = Channels.parseM3u("#EXTM3U\nhttps://cdn.example/a.m3u8\r\nhttp://cdn.example/b.ts\nnot an address\n")
        assertEquals(listOf("https://cdn.example/a.m3u8", "http://cdn.example/b.ts"), channels.map { it.url })
        assertEquals(listOf("Channel 1", "Channel 2"), channels.map { it.name })
    }

    @Test
    fun exthttpWithANumberKeepsItsHeaders() {
        val (channels, _) = Channels.parseM3u("#EXTM3U\n#EXTINF:-1,One\n#EXTHTTP:{\"cookie\":\"a=b\",\"x-id\":42}\nhttps://cdn.example/a.m3u8\n")
        assertEquals(mapOf("Cookie" to "a=b", "x-id" to "42"), channels[0].headers)
    }

    @Test
    fun playlistsAreNamedByPublisherAndList() {
        assertEquals("iptv-org · isl", IptvPlaylists.defaultName("https://iptv-org.github.io/iptv/languages/isl.m3u", false))
        assertEquals("Free-TV · playlist2", IptvPlaylists.defaultName("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist2.m3u8", false))
        assertEquals("tv.example.com", IptvPlaylists.defaultName("http://tv.example.com/get.php?username=a&password=b", true))
        assertEquals("provider.example", IptvPlaylists.defaultName("https://www.provider.example/playlist.m3u", false))
        assertEquals("host.example · my channels", IptvPlaylists.defaultName("https://host.example/my%20channels.m3u", false))
    }
}
