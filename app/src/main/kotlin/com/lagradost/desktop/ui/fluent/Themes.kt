package com.lagradost.desktop.ui.fluent

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A colour theme: the accent, the tinted dark surfaces and the two soft glows behind the pages. [Classic] is the plain Windows look
 * (neutral greys, the Windows accent); the others are deliberately more colourful.
 */
@Immutable
class ThemePreset(
    val id: String,
    val name: String,
    val accent: Color?,
    val bg: Color,
    val layer: Color,
    val flyout: Color,
    /** the colour the cards, controls and lines are made of (white with little alpha on [Classic]) */
    val tint: Color,
    val glowA: Color?,
    val glowB: Color?,
)

object Themes {
    val classic = ThemePreset("classic", "Classic", null, Color(0xFF0E0F13), Color(0xFF15161B), Color(0xFF1D1F25), Color.White, null, null)

    val all: List<ThemePreset> = listOf(
        ThemePreset("midnight", "Midnight", Color(0xFF8B7BFF), Color(0xFF0A0A18), Color(0xFF10102A), Color(0xFF1A1A38), Color(0xFFC9C2FF), Color(0xFF6D5BFF), Color(0xFFFF4FA3)),
        ThemePreset("crimson", "Crimson", Color(0xFFFF3B5C), Color(0xFF0D0508), Color(0xFF170A10), Color(0xFF241018), Color(0xFFFFC2CC), Color(0xFFE11D48), Color(0xFFFF8A3D)),
        ThemePreset("ocean", "Ocean", Color(0xFF22D3EE), Color(0xFF06121A), Color(0xFF0B1C28), Color(0xFF12293A), Color(0xFFB8ECFF), Color(0xFF0EA5E9), Color(0xFF2DD4BF)),
        ThemePreset("matcha", "Matcha", Color(0xFFA3E635), Color(0xFF0A130E), Color(0xFF101D15), Color(0xFF182B1F), Color(0xFFD4F5B0), Color(0xFF84CC16), Color(0xFF14B8A6)),
        ThemePreset("sunset", "Sunset", Color(0xFFFF7A45), Color(0xFF130A0B), Color(0xFF1E1013), Color(0xFF2B181C), Color(0xFFFFD0B8), Color(0xFFFF6B35), Color(0xFFE11D74)),
        ThemePreset("dracula", "Dracula", Color(0xFFFF79C6), Color(0xFF1C1D26), Color(0xFF242634), Color(0xFF30334A), Color(0xFFFFC7E8), Color(0xFFBD93F9), Color(0xFFFF79C6)),
        ThemePreset("nord", "Nord", Color(0xFF88C0D0), Color(0xFF232832), Color(0xFF2B313D), Color(0xFF363E4D), Color(0xFFD8E6EE), Color(0xFF5E81AC), Color(0xFF88C0D0)),
        ThemePreset("gold", "Cinema gold", Color(0xFFFFC53D), Color(0xFF110E07), Color(0xFF1B160B), Color(0xFF282011), Color(0xFFFFE7B0), Color(0xFFF59E0B), Color(0xFFEF4444)),
    )

    fun byId(id: String?): ThemePreset = if (id == null) classic else all.firstOrNull { it.id == id } ?: classic

    /** The first start (nothing saved) and "Reset to default" get Classic: the plain Windows colours (the other themes are a choice in Settings > Appearance) */
    const val DEFAULT_ID = "classic"
}

/** The colours of a dark theme: tinted surfaces, brighter secondary text; the accent stays the one in [FluentColors] */
internal fun FluentColors.themed(p: ThemePreset): FluentColors {
    if (!dark || p.id == "classic") return this
    val t = p.tint
    return FluentColors(
        dark = true, accent = accent, accentHover = accentHover, accentPressed = accentPressed, onAccent = onAccent, accentText = accentText,
        bg = p.bg, bgPane = p.bg, layer = p.layer,
        card = t.copy(alpha = 0.075f), cardHover = t.copy(alpha = 0.13f), cardPressed = t.copy(alpha = 0.05f),
        control = t.copy(alpha = 0.10f), controlHover = t.copy(alpha = 0.16f), controlPressed = t.copy(alpha = 0.06f), controlDisabled = t.copy(alpha = 0.05f),
        subtleHover = t.copy(alpha = 0.10f), subtlePressed = t.copy(alpha = 0.06f),
        flyout = p.flyout, stroke = t.copy(alpha = 0.15f), strokeStrong = t.copy(alpha = 0.34f), divider = t.copy(alpha = 0.12f),
        text = Color.White, textSecondary = Color(0xE0FFFFFF), textTertiary = Color(0xA3FFFFFF), textDisabled = Color(0x66FFFFFF),
        success = success, caution = caution, critical = critical, scrim = scrim, accent2 = accent2,
    )
}
