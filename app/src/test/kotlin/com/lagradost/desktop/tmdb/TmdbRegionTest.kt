package com.lagradost.desktop.tmdb

import com.lagradost.desktop.ui.fluent.Appearance
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The country of the Local lists and the streaming services comes from the PC, not from the app language: the language setting makes the JVM's
 * default locale a bare "en", which has no country, and every installed copy that had picked English showed the United States.
 */
class TmdbRegionTest {
    private val original = Locale.getDefault()
    private val savedRegion = Appearance.tmdbRegion

    @BeforeTest
    fun setUp() { Appearance.tmdbRegion = "" }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(original)
        Appearance.tmdbRegion = savedRegion
    }

    @Test
    fun appLanguageDoesNotChangeTheRegion() {
        val before = Tmdb.region
        for (tag in listOf("en", "hi", "pt-BR", "fr")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertEquals(before, Tmdb.region, "region after the app language was set to $tag")
        }
        assertTrue(before.length == 2 && before.all { it in 'A'..'Z' }, "a two letter country code, was '$before'")
    }

    @Test
    fun windowsRegionIsRead() {
        if (!System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")) return
        val value = runCatching {
            com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, "Control Panel\\International\\Geo", "Name",
            )
        }.getOrNull()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 } ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        assertEquals(value, Tmdb.region)
    }

    @Test
    fun theChoiceInSettingsWins() {
        Appearance.tmdbRegion = "JP"
        Locale.setDefault(Locale.forLanguageTag("en"))
        assertEquals("JP", Tmdb.region)
    }
}
