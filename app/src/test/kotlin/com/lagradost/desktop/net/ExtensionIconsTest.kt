package com.lagradost.desktop.net

import kotlin.test.Test
import kotlin.test.assertEquals

/** The icon address of an extension: the favicon service is asked for the site's real icon, not a stretched 16 px one */
class ExtensionIconsTest {
    @Test
    fun googleFaviconBecomesTheRealIconOfTheSite() {
        assertEquals(
            "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://nimegami.id&size=256",
            ExtensionIcons.sharper("https://www.google.com/s2/favicons?domain=nimegami.id&sz=%size%"),
        )
        assertEquals(
            "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://example.org&size=256",
            ExtensionIcons.sharper("https://www.google.com/s2/favicons?sz=64&domain_url=https%3A%2F%2Fexample.org%2Fpath"),
        )
    }

    @Test
    fun otherAddressesOnlyGetTheirSizeFilledIn() {
        assertEquals("https://cdn.example.org/logo.png", ExtensionIcons.sharper("https://cdn.example.org/logo.png"))
        assertEquals("https://icons.example.org/x?s=128", ExtensionIcons.sharper("https://icons.example.org/x?s=%size%"))
    }
}
