package com.lagradost.desktop.ui.fluent

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A colour theme: the accent, the tinted dark surfaces and the two soft glows behind the pages. [Classic] is the plain Windows look
 * (neutral greys, the Windows accent); the others are deliberately more colourful.
 */
@Immutable
class ThemePreset(
    val id: String,
    val name: String,
    val accent: Color?,
    /** the page itself (in the floating bar and dock looks this is all that is behind the cards) */
    val bg: Color,
    /** pages with a frame of their own (the rail looks), settings panes */
    val layer: Color,
    /** menus, dialogs and the open rail */
    val flyout: Color,
    /** the colour the cards, controls and lines are made of (white with little alpha on [Classic]) */
    val tint: Color,
    val glowA: Color?,
    val glowB: Color?,
)

object Themes {
    val classic = ThemePreset("classic", "Classic", null, Color(0xFF0E0F13), Color(0xFF15161B), Color(0xFF1D1F25), Color.White, null, null)

    // The pages are clearly coloured, not "almost black with a hint": a theme is the first thing a viewer sees of the app, and a hue that
    // is a few steps off black was lost on a normal screen (the themes looked like they did nothing).
    val all: List<ThemePreset> = listOf(
        ThemePreset("midnight", "Midnight", Color(0xFF8B7BFF), Color(0xFF0D0D2B), Color(0xFF14143A), Color(0xFF1F1F4F), Color(0xFFC9C2FF), Color(0xFF6D5BFF), Color(0xFFFF4FA3)),
        ThemePreset("crimson", "Crimson", Color(0xFFFF3B5C), Color(0xFF190A10), Color(0xFF25101A), Color(0xFF341724), Color(0xFFFFC2CC), Color(0xFFE11D48), Color(0xFFFF8A3D)),
        ThemePreset("ocean", "Ocean", Color(0xFF22D3EE), Color(0xFF071C2A), Color(0xFF0C2A3D), Color(0xFF133A54), Color(0xFFB8ECFF), Color(0xFF0EA5E9), Color(0xFF2DD4BF)),
        ThemePreset("matcha", "Matcha", Color(0xFFA3E635), Color(0xFF0B1A10), Color(0xFF112619), Color(0xFF1B3826), Color(0xFFD4F5B0), Color(0xFF84CC16), Color(0xFF14B8A6)),
        ThemePreset("sunset", "Sunset", Color(0xFFFF7A45), Color(0xFF1C0D0C), Color(0xFF2B1514), Color(0xFF3C1E1B), Color(0xFFFFD0B8), Color(0xFFFF6B35), Color(0xFFE11D74)),
        ThemePreset("gold", "Cinema gold", Color(0xFFFFC53D), Color(0xFF17120A), Color(0xFF221B0E), Color(0xFF312714), Color(0xFFFFE7B0), Color(0xFFF59E0B), Color(0xFFEF4444)),
        ThemePreset("dracula", "Dracula", Color(0xFFFF79C6), Color(0xFF21222C), Color(0xFF282A36), Color(0xFF343746), Color(0xFFD6C7FF), Color(0xFFBD93F9), Color(0xFFFF79C6)),
        ThemePreset("nord", "Nord", Color(0xFF88C0D0), Color(0xFF242933), Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFFD8DEE9), Color(0xFF5E81AC), Color(0xFF88C0D0)),
        ThemePreset("tokyo", "Tokyo Night", Color(0xFF7AA2F7), Color(0xFF13141C), Color(0xFF1A1B26), Color(0xFF24283B), Color(0xFFC0CAF5), Color(0xFF7AA2F7), Color(0xFFBB9AF7)),
        ThemePreset("mocha", "Mocha", Color(0xFFCBA6F7), Color(0xFF181825), Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFFCDD6F4), Color(0xFFCBA6F7), Color(0xFFF5C2E7)),
        ThemePreset("rose", "Rosé", Color(0xFFEBBCBA), Color(0xFF191724), Color(0xFF1F1D2E), Color(0xFF26233A), Color(0xFFE0DEF4), Color(0xFFC4A7E7), Color(0xFFEBBCBA)),
        ThemePreset("synthwave", "Synthwave", Color(0xFFFF2E97), Color(0xFF140A26), Color(0xFF1E1038), Color(0xFF2E1A52), Color(0xFFF5C6FF), Color(0xFFFF2E97), Color(0xFF00E5FF)),
        ThemePreset("gruvbox", "Gruvbox", Color(0xFFFABD2F), Color(0xFF1D2021), Color(0xFF282828), Color(0xFF3C3836), Color(0xFFEBDBB2), Color(0xFFD79921), Color(0xFFFE8019)),
        ThemePreset("graphite", "Graphite", Color(0xFF94A3B8), Color(0xFF15171A), Color(0xFF1C1F23), Color(0xFF272B31), Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFF94A3B8)),
    )

    fun byId(id: String?): ThemePreset = if (id == null) classic else all.firstOrNull { it.id == id } ?: classic

    /** The first start (nothing saved) and "Reset to default" get Classic: the plain Windows colours (the other themes are a choice in Settings > Appearance) */
    const val DEFAULT_ID = "classic"
}

/**
 * The colours of a theme: tinted surfaces, brighter secondary text; the accent stays the one in [FluentColors]. A dark theme replaces the
 * dark surfaces; in the light mode the theme only washes the page with its hue (the dark surfaces would be wrong there).
 */
internal fun FluentColors.themed(p: ThemePreset): FluentColors {
    if (p.id == "classic") return this
    if (!dark) {
        val hue = p.glowA ?: p.accent ?: return this
        return withSurfaces(lerp(bg, hue, 0.10f), lerp(bgPane, hue, 0.12f), lerp(layer, hue, 0.06f), lerp(flyout, hue, 0.05f))
    }
    val t = p.tint
    return FluentColors(
        dark = true, accent = accent, accentHover = accentHover, accentPressed = accentPressed, onAccent = onAccent, accentText = accentText,
        bg = p.bg, bgPane = p.bg, layer = p.layer,
        card = t.copy(alpha = 0.085f), cardHover = t.copy(alpha = 0.14f), cardPressed = t.copy(alpha = 0.055f),
        control = t.copy(alpha = 0.11f), controlHover = t.copy(alpha = 0.17f), controlPressed = t.copy(alpha = 0.07f), controlDisabled = t.copy(alpha = 0.05f),
        subtleHover = t.copy(alpha = 0.10f), subtlePressed = t.copy(alpha = 0.06f),
        flyout = p.flyout, stroke = t.copy(alpha = 0.16f), strokeStrong = t.copy(alpha = 0.34f), divider = t.copy(alpha = 0.12f),
        text = Color.White, textSecondary = Color(0xE0FFFFFF), textTertiary = Color(0xA3FFFFFF), textDisabled = Color(0x66FFFFFF),
        success = success, caution = caution, critical = critical, scrim = scrim, accent2 = accent2,
    )
}

private fun FluentColors.withSurfaces(bg: Color, bgPane: Color, layer: Color, flyout: Color) = FluentColors(
    dark = dark, accent = accent, accentHover = accentHover, accentPressed = accentPressed, onAccent = onAccent, accentText = accentText,
    bg = bg, bgPane = bgPane, layer = layer, card = card, cardHover = cardHover, cardPressed = cardPressed,
    control = control, controlHover = controlHover, controlPressed = controlPressed, controlDisabled = controlDisabled,
    subtleHover = subtleHover, subtlePressed = subtlePressed, flyout = flyout, stroke = stroke, strokeStrong = strokeStrong, divider = divider,
    text = text, textSecondary = textSecondary, textTertiary = textTertiary, textDisabled = textDisabled,
    success = success, caution = caution, critical = critical, scrim = scrim, accent2 = accent2,
)
