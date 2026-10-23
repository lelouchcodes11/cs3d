package com.lagradost.desktop.ui.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lagradost.desktop.platform.AccentPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The colour themes: Netflix's palette and what it does to the colour set */
class ThemesTest {
    /** what FluentTheme builds from a colour: the accent the theme or the user chose */
    private fun palette(o: Color) = AccentPalette(
        lerp(o, Color.White, 0.6f), lerp(o, Color.White, 0.35f), lerp(o, Color.White, 0.15f), o,
        lerp(o, Color.Black, 0.15f), lerp(o, Color.Black, 0.3f), lerp(o, Color.Black, 0.45f),
    )

    private fun colors(p: ThemePreset, accent: Color? = p.accent, button: Color? = null) = fluentColors(true, palette(accent!!), button).themed(p)

    @Test
    fun netflixIsFindableById() {
        assertSame(Themes.specials.single(), Themes.byId("netflix"))
        assertTrue(Themes.specials.all { it.special })
        assertTrue(Themes.all.none { it.special })
    }

    @Test
    fun idsAreUniqueAcrossAllThemes() {
        val ids = listOf(Themes.classic.id) + Themes.all.map { it.id } + Themes.specials.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun unknownIdIsClassic() {
        assertEquals("classic", Themes.byId("nope").id)
    }

    @Test
    fun netflixIsNearBlackWithItsRed() {
        val c = colors(Themes.byId("netflix"))
        assertEquals(Color(0xFF141414), c.bg)
        assertEquals(Color(0xFFE50914), c.accent)
        assertEquals(Color(0xFFE50914), c.button)
        // white on the red
        assertEquals(Color.White, c.onButton)
    }
}
