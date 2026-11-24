package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.txt
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryPageSortTest {
    private fun item(name: String) = SyncAPI.LibraryItem(
        name, "https://example.invalid/$name", name, null, null, null, null, "Local", null, null, null, null, null,
    )

    private fun page(vararg names: String) = SyncAPI.Page(txt("Watching"), names.map { item(it) })

    @Test fun alphabeticalIgnoresCase() {
        val p = page("xxxHOLiC", "Zom 100", "akira", "Bleach")
        p.sort(ListSorting.AlphabeticalA)
        assertEquals(listOf("akira", "Bleach", "xxxHOLiC", "Zom 100"), p.items.map { it.name })
        p.sort(ListSorting.AlphabeticalZ)
        assertEquals(listOf("Zom 100", "xxxHOLiC", "Bleach", "akira"), p.items.map { it.name })
    }
}
