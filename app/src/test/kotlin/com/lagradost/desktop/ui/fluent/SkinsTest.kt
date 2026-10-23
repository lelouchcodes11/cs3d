package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.screens.home.ageRating
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Netflix interface of the special themes (Skins) */
class SkinsTest {
    private val pages = setOf("home", "search", "library", "schedule", "downloads", "settings")

    @AfterTest
    fun restore() {
        Appearance.theme = Themes.DEFAULT_ID
        Appearance.palette = Themes.DEFAULT_ID
        Appearance.skinLayout = true
        Appearance.cornerRadius = 12
        Skins.inPlayer = false
    }

    @Test
    fun thePlayerKeepsTheDefaultThemeWhateverTheThemeIs() {
        Appearance.cornerRadius = 12
        Appearance.theme = "netflix"
        Appearance.palette = "slate"
        assertEquals("netflix", Skins.preset.id)
        assertEquals(Skin.Netflix, Skins.active)
        Skins.inPlayer = true
        // the Default theme in the palette of Settings > Colors: its colours, no interface of a service, the user's shapes
        assertEquals("slate", Skins.preset.id)
        assertEquals(Skin.None, Skins.active)
        assertNull(Skins.spec)
        assertEquals(12.dp, FluentShapes.card)
        assertEquals(RoundedCornerShape(percent = 50), FluentShapes.pill)
        Skins.inPlayer = false
        assertEquals(Skin.Netflix, Skins.active)
    }

    @Test
    fun netflixIsTheOnlySpecialTheme() {
        assertEquals(listOf("netflix"), Themes.specials.map { it.id })
        assertEquals(Skin.Netflix, Themes.byId("netflix").skin)
        assertTrue(Themes.byId("netflix").special)
        // the other ids of the first version are no themes any more: they fall back to Classic
        for (gone in listOf("prime", "hotstar", "crunchyroll")) assertEquals("classic", Themes.byId(gone).id)
        assertNull(Skins.specOf(Skin.None))
    }

    @Test
    fun theLabelsCoverEveryPageAndTheLinksAreRealPages() {
        val spec = Skins.specOf(Skin.Netflix)!!
        assertEquals(pages, spec.labels.keys)
        assertTrue(spec.links.isNotEmpty() && spec.links.all { it in pages })
        assertEquals(spec.links.size, spec.links.toSet().size)
        assertTrue("home" in spec.links)
    }

    @Test
    fun theSkinFollowsTheThemeAndTheSwitch() {
        Appearance.theme = "netflix"
        Appearance.skinLayout = true
        assertEquals(Skin.Netflix, Skins.active)
        Appearance.skinLayout = false
        assertEquals(Skin.None, Skins.active)
        assertNull(Skins.spec)
        Appearance.skinLayout = true
        Appearance.theme = "slate"
        assertEquals(Skin.None, Skins.active)
    }

    @Test
    fun shapesComeFromTheSkinWhileItIsOn() {
        Appearance.cornerRadius = 12
        Appearance.theme = "classic"
        assertEquals(12.dp, FluentShapes.card)
        assertEquals(RoundedCornerShape(percent = 50), FluentShapes.pill)
        Appearance.theme = "netflix"
        assertEquals(6.dp, FluentShapes.card)
        assertEquals(RoundedCornerShape(4.dp), FluentShapes.pill)
        Appearance.skinLayout = false
        assertEquals(12.dp, FluentShapes.card)
    }

    @Test
    fun pagesAreTitledTheWayTheInterfaceCallsThem() {
        Appearance.theme = "classic"
        assertEquals("Library", Skins.pageTitle("Library"))
        Appearance.theme = "netflix"
        assertEquals("My List", Skins.pageTitle("Library"))
        assertEquals("New & Popular", Skins.pageTitle("Schedule"))
        assertEquals("Settings", Skins.pageTitle("Settings"))
    }

    @Test
    fun aMatchIsTheScoreTimesTen() {
        assertNull(matchText(null))
    }

    @Test
    fun anAgeRatingIsOneAndASiteNameIsNot() {
        for (ok in listOf("PG-13", "TV-MA", "R", "16+", "U/A 13+", "18+", "G", "TV14", "tv-ma")) assertNotNull(ok.ageRating(), ok)
        for (junk in listOf("IMDB", "TMDB", "", "  ", "Rated by IMDB", "Netflix")) assertNull(junk.ageRating(), junk)
        assertNull((null as String?).ageRating())
    }
}
