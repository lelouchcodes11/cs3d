package com.lagradost.desktop.ui.fluent

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A colour theme: flat surface palette (bg, layer, flyout, card/border tint) and solid accent colour.
 * [Classic] is the default untouched look; the others replace the surface palette with clean flat themes.
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
    val glowA: Color? = null,
    val glowB: Color? = null,
    val accent2: Color? = accent,
    val subtitle: String = "",
    /** the complete interface that goes with this theme (the Netflix theme of Settings > Appearance > Themes); [Skin.None] for an ordinary colour theme */
    val skin: Skin = Skin.None,
) {
    /** a streaming-service look: its own page colours and layout */
    val special: Boolean get() = skin != Skin.None
}

object Themes {
    val classic = ThemePreset(
        "classic", "Classic (Default)", null,
        Color(0xFF0E0F13), Color(0xFF15161B), Color(0xFF1D1F25), Color.White,
        null, null, null, "Default dark look",
    )

    val all: List<ThemePreset> = listOf(
        ThemePreset(
            "slate", "Slate", Color(0xFF3B82F6),
            Color(0xFF111318), Color(0xFF171A21), Color(0xFF1F242D), Color(0xFFCBD5E1),
            subtitle = "Cool slate & blue",
        ),
        ThemePreset(
            "graphite", "Graphite", Color(0xFFA1A1AA),
            Color(0xFF141416), Color(0xFF1B1B1E), Color(0xFF242428), Color(0xFFD4D4D8),
            subtitle = "Neutral carbon & zinc",
        ),
        ThemePreset(
            "onedark", "One Dark", Color(0xFF61AFEF),
            Color(0xFF1E222A), Color(0xFF232731), Color(0xFF282C34), Color(0xFFABB2BF),
            subtitle = "Atom dark grey & sky",
        ),
        ThemePreset(
            "tokyo", "Tokyo Night", Color(0xFF7AA2F7),
            Color(0xFF16161E), Color(0xFF1A1B26), Color(0xFF24283B), Color(0xFFC0CAF5),
            subtitle = "Deep navy & soft blue",
        ),
        ThemePreset(
            "mocha", "Catppuccin Mocha", Color(0xFFCBA6F7),
            Color(0xFF11111B), Color(0xFF181825), Color(0xFF1E1E2E), Color(0xFFCDD6F4),
            subtitle = "Warm charcoal & mauve",
        ),
        ThemePreset(
            "macchiato", "Catppuccin Macchiato", Color(0xFF8AADF4),
            Color(0xFF181926), Color(0xFF1E2030), Color(0xFF24273A), Color(0xFFCAD3F5),
            subtitle = "Soft indigo & cornflower",
        ),
        ThemePreset(
            "nord", "Nord", Color(0xFF88C0D0),
            Color(0xFF242933), Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFFD8DEE9),
            subtitle = "Arctic slate & frost",
        ),
        ThemePreset(
            "dracula", "Dracula", Color(0xFFBD93F9),
            Color(0xFF191A21), Color(0xFF21222C), Color(0xFF282A36), Color(0xFFF8F8F2),
            subtitle = "Dark slate & purple",
        ),
        ThemePreset(
            "gruvbox", "Gruvbox", Color(0xFFD79921),
            Color(0xFF1D2021), Color(0xFF282828), Color(0xFF32302F), Color(0xFFEBDBB2),
            subtitle = "Warm retro earth & amber",
        ),
        ThemePreset(
            "rose", "Rosé Pine", Color(0xFFEBBCBA),
            Color(0xFF191724), Color(0xFF1F1D2E), Color(0xFF26233A), Color(0xFFE0DEF4),
            subtitle = "Muted pine & soft rose",
        ),
        ThemePreset(
            "everforest", "Everforest", Color(0xFFA7C080),
            Color(0xFF1E2326), Color(0xFF272E33), Color(0xFF2E383C), Color(0xFFD3C6AA),
            subtitle = " Woodland green & sage",
        ),
        ThemePreset(
            "kanagawa", "Kanagawa", Color(0xFF7E9CD8),
            Color(0xFF16161D), Color(0xFF1F1F28), Color(0xFF2A2A37), Color(0xFFDCD7BA),
            subtitle = "Sumi ink & wave blue",
        ),
        ThemePreset(
            "ayu", "Ayu Dark", Color(0xFFE6B450),
            Color(0xFF0B0E14), Color(0xFF0F131A), Color(0xFF151A24), Color(0xFFBFBDB6),
            subtitle = "Deep midnight & warm gold",
        ),
        ThemePreset(
            "monokai", "Monokai", Color(0xFFFFD866),
            Color(0xFF19181A), Color(0xFF221F22), Color(0xFF2D2A2E), Color(0xFFFCFCFA),
            subtitle = "Warm charcoal & yellow",
        ),
        ThemePreset(
            "midnight", "Midnight", Color(0xFF6366F1),
            Color(0xFF0C0D18), Color(0xFF121424), Color(0xFF191C30), Color(0xFFC7D2FE),
            subtitle = "Dark indigo & violet",
        ),
        ThemePreset(
            "crimson", "Crimson", Color(0xFFE11D48),
            Color(0xFF140A0D), Color(0xFF1C0E13), Color(0xFF26141A), Color(0xFFFECDD3),
            subtitle = "Dark burgundy & crimson",
        ),
        ThemePreset(
            "ocean", "Ocean", Color(0xFF0EA5E9),
            Color(0xFF08131C), Color(0xFF0D1D2A), Color(0xFF13283A), Color(0xFFBAE6FD),
            subtitle = "Deep navy & ocean blue",
        ),
        ThemePreset(
            "emerald", "Emerald", Color(0xFF10B981),
            Color(0xFF081410), Color(0xFF0D1F18), Color(0xFF132B22), Color(0xFFA7F3D0),
            subtitle = "Dark forest & emerald",
        ),
        ThemePreset(
            "espresso", "Espresso", Color(0xFFD97706),
            Color(0xFF14110F), Color(0xFF1C1714), Color(0xFF26201C), Color(0xFFFDE68A),
            subtitle = "Dark roast & warm amber",
        ),
    )

    /**
     * The Netflix look as a complete interface: its page colours here (near-black pages with one signature red), its layout in [Skins] (navigation,
     * billboard, rows, cards, shapes). No name or mark of the service is drawn.
     */
    val specials: List<ThemePreset> = listOf(
        ThemePreset(
            "netflix", "Netflix", Color(0xFFE50914),
            Color(0xFF141414), Color(0xFF1A1A1A), Color(0xFF242424), Color(0xFFE5E5E5),
            subtitle = "Cinema black & red", skin = Skin.Netflix,
        ),
    )

    fun byId(id: String?): ThemePreset = if (id == null) classic else all.firstOrNull { it.id == id } ?: specials.firstOrNull { it.id == id } ?: classic

    /** The first start (nothing saved) and "Reset to default" get Classic: the plain Windows colours (the other themes are a choice in Settings > Appearance) */
    const val DEFAULT_ID = "classic"
}

/**
 * The colours of a theme: flat surfaces and subtle card/control/border fills scaled by [tintStrength].
 * [Classic] returns [this] untouched.
 */
internal fun FluentColors.themed(p: ThemePreset, tintStrength: SurfaceTint = SurfaceTint.Balanced): FluentColors {
    if (p.id == "classic") return this
    val m = tintStrength.multiplier
    if (!dark) {
        val hue = p.accent ?: return this
        return withSurfaces(lerp(bg, hue, 0.08f * m), lerp(bgPane, hue, 0.10f * m), lerp(layer, hue, 0.05f * m), lerp(flyout, hue, 0.04f * m))
    }
    val t = p.tint
    return FluentColors(
        dark = true, accent = accent, accentHover = accentHover, accentPressed = accentPressed, onAccent = onAccent, accentText = accentText,
        bg = p.bg, bgPane = p.bg, layer = p.layer,
        card = t.copy(alpha = (0.075f * m).coerceIn(0.04f, 0.18f)),
        cardHover = t.copy(alpha = (0.125f * m).coerceIn(0.08f, 0.24f)),
        cardPressed = t.copy(alpha = (0.05f * m).coerceIn(0.03f, 0.12f)),
        control = t.copy(alpha = (0.10f * m).coerceIn(0.06f, 0.20f)),
        controlHover = t.copy(alpha = (0.155f * m).coerceIn(0.10f, 0.26f)),
        controlPressed = t.copy(alpha = (0.065f * m).coerceIn(0.04f, 0.14f)),
        controlDisabled = t.copy(alpha = 0.045f),
        subtleHover = t.copy(alpha = (0.09f * m).coerceIn(0.05f, 0.18f)),
        subtlePressed = t.copy(alpha = (0.055f * m).coerceIn(0.03f, 0.12f)),
        flyout = p.flyout,
        stroke = t.copy(alpha = (0.14f * m).coerceIn(0.08f, 0.28f)),
        strokeStrong = t.copy(alpha = (0.28f * m).coerceIn(0.18f, 0.45f)),
        divider = t.copy(alpha = (0.11f * m).coerceIn(0.06f, 0.22f)),
        text = Color.White, textSecondary = Color(0xCCFFFFFF), textTertiary = Color(0x8FFFFFFF), textDisabled = Color(0x5DFFFFFF),
        success = success, caution = caution, critical = critical, scrim = scrim,
        accent2 = accent2, button = button, button2 = button2, onButton = onButton,
    )
}

private fun FluentColors.withSurfaces(bg: Color, bgPane: Color, layer: Color, flyout: Color) = FluentColors(
    dark = dark, accent = accent, accentHover = accentHover, accentPressed = accentPressed, onAccent = onAccent, accentText = accentText,
    bg = bg, bgPane = bgPane, layer = layer, card = card, cardHover = cardHover, cardPressed = cardPressed,
    control = control, controlHover = controlHover, controlPressed = controlPressed, controlDisabled = controlDisabled,
    subtleHover = subtleHover, subtlePressed = subtlePressed, flyout = flyout, stroke = stroke, strokeStrong = strokeStrong, divider = divider,
    text = text, textSecondary = textSecondary, textTertiary = textTertiary, textDisabled = textDisabled,
    success = success, caution = caution, critical = critical, scrim = scrim,
    accent2 = accent2, button = button, button2 = button2, onButton = onButton,
)
