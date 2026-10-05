package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.providers.SubtitleCat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The markup is what subtitlecat.com sends (search page rows and a release page's language rows) */
class SubtitleCatTest {
    private val search = """
        <table class="table sub-table"><thead><tr><th colspan="2"><h2 class="sec-title">34 subtitles found</h2></th></tr></thead><tbody>
        <tr>
            <td><a href="subs/1296/Vikram%20%28Vikram%29%20%282022%29%20%5B1080p%5D.html">Vikram (Vikram) (2022) [1080p]</a> (translated from Spanish)</td>
            <td class="sub-table__stars">&nbsp;</td>
            <td class="sub-table__metric"><span class="sub-table__metric-value">156 KB</span></td>
        </tr>
        <tr>
            <td><a href="subs/1671/Vikram.2022.1080p.BluRay.x264.AAC5.1-%5BYTS.MX%5D.html">Vikram.2022.1080p.BluRay.x264.AAC5.1-[YTS.MX]</a></td>
        </tr>
        <tr><td>no link here</td></tr>
        </tbody></table>
    """.trimIndent()

    private val page = """
        <div class="sub-single">
          <span><img src="/assets/flags/za.png" alt="af" class="flag"></span>
          <span>Afrikaans</span>
          <span><button id="af" onclick="translate_from_server_folder('af', 'x-orig.srt', '/subs/1671/')" class="yellow-link">Translate</button></span>
        </div>
        <div class="sub-single">
          <span><img src="/assets/flags/gb.png" alt="en" class="flag"></span>
          <span>English</span>
          <span><a id="download_en"  onclick="log_download(1); show_voting('en');" href="/subs/1673/Vikram.2022.1080p.BluRay.x264.AAC5.1-[YTS.MX]-en.srt" class="green-link">Download</a>
            <span id="voting_en" style="display:none;"><a href="javascript:vote('en',1,+1)">up</a></span></span>
        </div>
        <div class="sub-single">
          <span><img src="/assets/flags/br.png" alt="pt-BR" class="flag"></span>
          <span>Portuguese (Brazil)</span>
          <span><a id="download_pt-BR" href="/subs/1673/Vikram-pt-BR.srt" class="green-link">Download</a></span>
        </div>
    """.trimIndent()

    @Test
    fun searchRowsGiveTheReleasePagesAndTheirNotes() {
        val found = SubtitleCat.parseSearch(search)
        assertEquals(2, found.size)
        assertEquals("Vikram (Vikram) (2022) [1080p]", found[0].title)
        assertEquals("(translated from Spanish)", found[0].note)
        assertEquals("https://www.subtitlecat.com/subs/1296/Vikram%20%28Vikram%29%20%282022%29%20%5B1080p%5D.html", found[0].pageUrl)
        assertEquals("", found[1].note)
    }

    @Test
    fun onlyRowsWithAFileAreKept() {
        val entries = SubtitleCat.parsePage(page)
        assertEquals(listOf("en", "pt-BR"), entries.map { it.code })
        assertEquals("English", entries[0].language)
        // spaces and brackets of the site's paths are escaped
        assertEquals("https://www.subtitlecat.com/subs/1673/Vikram.2022.1080p.BluRay.x264.AAC5.1-%5BYTS.MX%5D-en.srt", entries[0].fileUrl)
    }

    @Test
    fun languagePartDecidesTheMatch() {
        assertTrue(SubtitleCat.sameLanguage("en", "en"))
        assertTrue(SubtitleCat.sameLanguage("pt", "pt-BR"))
        assertTrue(SubtitleCat.sameLanguage("pt-BR", "pt"))
        assertTrue(!SubtitleCat.sameLanguage("en", "fr"))
        assertTrue(SubtitleCat.sameLanguage(null, "xx"))
    }

    @Test
    fun unrelatedUploadsAreNotOffered() {
        val q = SubtitleSearch(query = "Big Buck Bunny")
        assertTrue(SubtitleCat.isRelevant("Big.Buck.Bunny.2008.720p.BluRay", q))
        assertTrue(!SubtitleCat.isRelevant("Zoey Holloway BIG ASS big dick 720p", q))
        val ep = SubtitleSearch(query = "Dark", seasonNumber = 1, epNumber = 5)
        assertTrue(SubtitleCat.isRelevant("Dark.S01E05.1080p.WEB", ep))
        assertTrue(!SubtitleCat.isRelevant("Dark.S01E06.1080p.WEB", ep))
        assertTrue(!SubtitleCat.isRelevant("Dark Knight 2008", ep))
    }

    @Test
    fun searchTermsFollowTheKindOfTitle() {
        assertEquals("Vikram 2022", SubtitleCat.searchTerms(SubtitleSearch(query = "Vikram", year = 2022)))
        assertEquals("Dark S01E05", SubtitleCat.searchTerms(SubtitleSearch(query = "Dark", seasonNumber = 1, epNumber = 5, year = 2017)))
        assertEquals("Naruto", SubtitleCat.searchTerms(SubtitleSearch(query = " Naruto ")))
    }
}
