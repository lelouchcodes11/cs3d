package com.lagradost.cloudstream3.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Repository links as people paste them, and plugin lists with an entry the app cannot read */
class RepositoryParsingTest {
    private val raw = "https://raw.githubusercontent.com/user/repo/builds/repo.json"

    @Test
    fun plainAddressesStayAsTheyAre() {
        assertEquals(raw, RepositoryManager.repoLinkToUrl(raw))
        assertEquals(raw, RepositoryManager.repoLinkToUrl("  $raw  "))
        assertEquals("http://example.org/repo.json", RepositoryManager.repoLinkToUrl("http://example.org/repo.json"))
    }

    @Test
    fun deepLinksGiveTheRepositoryAddress() {
        assertEquals(raw, RepositoryManager.repoLinkToUrl("cloudstreamrepo://raw.githubusercontent.com/user/repo/builds/repo.json"))
        assertEquals(raw, RepositoryManager.repoLinkToUrl("cloudstreamrepo://$raw"))
        assertEquals(raw, RepositoryManager.repoLinkToUrl("https://cs.repo/?raw.githubusercontent.com/user/repo/builds/repo.json"))
        assertEquals(raw, RepositoryManager.repoLinkToUrl("https://cs.repo/?$raw"))
    }

    @Test
    fun shortCodesAreNotLinks() {
        assertNull(RepositoryManager.repoLinkToUrl("megarepo"))
        assertNull(RepositoryManager.repoLinkToUrl("!abc"))
    }

    private fun entry(name: String, authors: String = "[\"someone\"]") = """
        {"url":"https://example.org/$name.cs3","status":1,"version":3,"apiVersion":1,"name":"$name","internalName":"$name",
         "authors":$authors,"description":null,"repositoryUrl":"https://example.org","tvTypes":["Movie"],"language":"en",
         "iconUrl":null,"fileSize":1234,"fileHash":null,"extraField":true}
    """.trimIndent()

    @Test
    fun oneBrokenEntryKeepsTheOthers() {
        val list = RepositoryManager.parsePluginList("[${entry("One")}, ${entry("Broken", "null")}, null, ${entry("Two")}]")
        assertEquals(listOf("One", "Two"), list.map { it.internalName })
        assertEquals(3, list[0].version)
        assertEquals(listOf("someone"), list[1].authors)
    }

    @Test
    fun notAListIsAnError() {
        assertFailsWith<Exception> { RepositoryManager.parsePluginList("<html>rate limited</html>") }
        assertFailsWith<Exception> { RepositoryManager.parsePluginList("{\"name\":\"repo\"}") }
    }
}
