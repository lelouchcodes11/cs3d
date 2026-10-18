package com.lagradost.desktop.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalMpvTest {
    private val exe = File("C:\\Tools\\mpv\\mpv.exe")

    private fun link(url: String, type: ExtractorLinkType = ExtractorLinkType.VIDEO, referer: String = "", headers: Map<String, String> = emptyMap()): ExtractorLink =
        runBlocking {
            newExtractorLink("test", "Test", url, type) {
                this.referer = referer
                this.headers = headers
            }
        }

    @Test
    fun linkGoesFirstAndTheHeadersGoToMpvItself() {
        val command = ExternalPlayers.mpvCommand(
            exe, link("https://cdn.example.invalid/v.m3u8", ExtractorLinkType.M3U8, headers = mapOf("User-Agent" to "UA/1", "Referer" to "https://site.invalid/", "Cookie" to "a=b", "Origin" to "https://site.invalid")),
            "Show - Episode 3",
        )
        assertEquals(exe.absolutePath, command[0])
        assertEquals("https://cdn.example.invalid/v.m3u8", command[1])
        assertTrue("--user-agent=UA/1" in command)
        assertTrue("--referrer=https://site.invalid/" in command)
        assertTrue("--http-header-fields-append=Cookie: a=b" in command)
        assertTrue("--http-header-fields-append=Origin: https://site.invalid" in command)
        // the user agent and the referer have options of their own: not sent twice as header fields
        assertFalse(command.any { it.startsWith("--http-header-fields-append=User-Agent") || it.startsWith("--http-header-fields-append=Referer") })
        assertTrue("--force-media-title=Show - Episode 3" in command)
        assertTrue("--no-terminal" in command)
        assertTrue("--ytdl=no" in command)
    }

    @Test
    fun theRefererOfTheLinkIsUsedWhenThereIsNoRefererHeader() {
        val command = ExternalPlayers.mpvCommand(exe, link("https://cdn.example.invalid/v.mp4", referer = "https://embed.invalid/"), null)
        assertTrue("--referrer=https://embed.invalid/" in command)
        assertFalse(command.any { it.startsWith("--force-media-title") })
    }

    @Test
    fun subtitlesAndTheStartPositionAreGiven() {
        val subs = listOf(File("C:\\Temp\\a.srt"), File("C:\\Temp\\b.vtt"))
        val command = ExternalPlayers.mpvCommand(exe, link("https://cdn.example.invalid/v.mp4"), "T", subs, 125.7)
        assertTrue("--sub-file=${subs[0].absolutePath}" in command)
        assertTrue("--sub-file=${subs[1].absolutePath}" in command)
        assertTrue("--start=125" in command)
        // the first seconds are not worth a jump
        assertFalse(ExternalPlayers.mpvCommand(exe, link("https://cdn.example.invalid/v.mp4"), "T", emptyList(), 3.0).any { it.startsWith("--start") })
    }

    @Test
    fun quotesInTheTitleAreReplaced() {
        val command = ExternalPlayers.mpvCommand(exe, link("https://cdn.example.invalid/v.mp4"), "He said \"hi\"")
        assertTrue("--force-media-title=He said 'hi'" in command)
    }

    @Test
    fun mpvPlaysFilesHlsAndDash() {
        assertTrue(ExternalPlayers.mpvCanPlay(link("https://x.invalid/v.mp4")))
        assertTrue(ExternalPlayers.mpvCanPlay(link("https://x.invalid/v.m3u8", ExtractorLinkType.M3U8)))
        assertTrue(ExternalPlayers.mpvCanPlay(link("https://x.invalid/v.mpd", ExtractorLinkType.DASH)))
        assertFalse(ExternalPlayers.mpvCanPlay(link("magnet:?xt=urn:btih:abc", ExtractorLinkType.MAGNET)))
    }
}
