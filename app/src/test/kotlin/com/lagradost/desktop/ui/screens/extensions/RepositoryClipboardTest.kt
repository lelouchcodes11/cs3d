package com.lagradost.desktop.ui.screens.extensions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the "Add repository" box takes from the clipboard */
class RepositoryClipboardTest {
    @Test
    fun repositoryAddressesAreTaken() {
        assertTrue(looksLikeRepository("https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json"))
        assertTrue(looksLikeRepository("https://example.org/some/list.json"))
        assertTrue(looksLikeRepository("https://github.com/someone/cloudstream-extensions"))
        assertTrue(looksLikeRepository("cloudstreamrepo://phisher98"))
    }

    @Test
    fun shortCodesAreTaken() {
        assertTrue(looksLikeRepository("phisher"))
        assertTrue(looksLikeRepository("my-repo_1.0"))
    }

    @Test
    fun otherTextIsNot() {
        assertFalse(looksLikeRepository(""))
        assertFalse(looksLikeRepository("hello world"))
        assertFalse(looksLikeRepository("https://www.youtube.com/watch?v=abc"))
        assertFalse(looksLikeRepository("https://github.com/mohamedyassine-benmiled/e-windows"))
        assertFalse(looksLikeRepository("a very long sentence that somebody copied for another purpose"))
        assertFalse(looksLikeRepository("ab"))
    }
}
