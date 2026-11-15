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

    @Test
    fun channelSurvivesTheTripThroughLinkData() {
        val c = Channels.parseM3u(playlist).first[1]
        assertEquals(c, IptvChannel.fromJson(c.toJson()))
    }
}
