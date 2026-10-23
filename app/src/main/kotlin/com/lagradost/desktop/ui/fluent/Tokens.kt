package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.desktop.platform.AccentPalette
import com.lagradost.desktop.platform.WinTheme
import kotlinx.coroutines.delay
import java.io.File

/** Design tokens of the app. Windows 11 Fluent look: see docs/PLAN.md section 2. */
@Immutable
class FluentColors(
    val dark: Boolean,
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val onAccent: Color,
    /** accent used for text, links and selection marks on normal surfaces */
    val accentText: Color,
    /** window backdrop */
    val bg: Color,
    /** navigation pane / secondary backdrop */
    val bgPane: Color,
    /** page layer drawn over the backdrop */
    val layer: Color,
    /** card on a layer */
    val card: Color,
    val cardHover: Color,
    val cardPressed: Color,
    /** standard button */
    val control: Color,
    val controlHover: Color,
    val controlPressed: Color,
    val controlDisabled: Color,
    /** transparent button (nav items, icon buttons) */
    val subtleHover: Color,
    val subtlePressed: Color,
    val flyout: Color,
    val stroke: Color,
    val strokeStrong: Color,
    val divider: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val success: Color,
    val caution: Color,
    val critical: Color,
    val scrim: Color,
    /** the second colour of the accent (kept equal to accent: no gradients) */
    val accent2: Color = accent,
    /** primary button, chip, and pill colour (follows [accent] unless overridden in Settings) */
    val button: Color = accent,
    /** secondary stop (kept equal to button: flat solid fill) */
    val button2: Color = button,
    /** foreground text/icon colour on top of [button] */
    val onButton: Color = onAccent,
)

/**
 * The edge of a surface the pointer is over, and of a selected one. Neutral on purpose: the accent colour is for state (progress, switches,
 * sliders, counts), while hover and selection are drawn the way the top bar and the tabs draw them, in white.
 */
val FluentColors.hoverEdge: Color get() = text.copy(alpha = 0.32f)
val FluentColors.selectedEdge: Color get() = text.copy(alpha = 0.7f)

/** Picks crisp dark or white text depending on the perceived luminance of [bg] */
fun contrastText(bg: Color): Color {
    val lum = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (lum > 0.56f) Color(0xFF090A0F) else Color.White
}

internal fun fluentColors(dark: Boolean, a: AccentPalette, btnOverride: Color? = null): FluentColors {
    val primary = if (dark) a.base else a.dark1
    val btn = btnOverride ?: primary
    val onAcc = if (dark) contrastText(primary) else Color.White
    val onBtn = contrastText(btn)
    return if (dark) FluentColors(
        dark = true,
        accent = a.base, accentHover = a.base.copy(alpha = 0.9f), accentPressed = a.base.copy(alpha = 0.8f),
        accent2 = a.base,
        onAccent = onAcc, accentText = a.light2,
        button = btn, button2 = btn, onButton = onBtn,
        bg = Color(0xFF0E0F13), bgPane = Color(0xFF0E0F13), layer = Color(0xFF15161B),
        card = Color(0x0EFFFFFF), cardHover = Color(0x18FFFFFF), cardPressed = Color(0x0AFFFFFF),
        control = Color(0x12FFFFFF), controlHover = Color(0x1CFFFFFF), controlPressed = Color(0x0BFFFFFF), controlDisabled = Color(0x0BFFFFFF),
        subtleHover = Color(0x12FFFFFF), subtlePressed = Color(0x0BFFFFFF),
        flyout = Color(0xFF1D1F25), stroke = Color(0x18FFFFFF), strokeStrong = Color(0x36FFFFFF), divider = Color(0x15FFFFFF),
        text = Color(0xFFFFFFFF), textSecondary = Color(0xCCFFFFFF), textTertiary = Color(0x8FFFFFFF), textDisabled = Color(0x5DFFFFFF),
        success = Color(0xFF6CCB5F), caution = Color(0xFFFCE100), critical = Color(0xFFFF99A4), scrim = Color(0x99000000),
    ) else FluentColors(
        dark = false,
        accent = a.dark1, accentHover = a.dark1.copy(alpha = 0.9f), accentPressed = a.dark1.copy(alpha = 0.8f),
        accent2 = a.dark1,
        onAccent = Color.White, accentText = a.dark2,
        button = btn, button2 = btn, onButton = onBtn,
        bg = Color(0xFFF3F3F3), bgPane = Color(0xFFEEEEEE), layer = Color(0xFFFAFAFA),
        card = Color(0xB3FFFFFF), cardHover = Color(0x80F9F9F9), cardPressed = Color(0x4DF9F9F9),
        control = Color(0xB3FFFFFF), controlHover = Color(0x80F9F9F9), controlPressed = Color(0x4DF9F9F9), controlDisabled = Color(0x4DF9F9F9),
        subtleHover = Color(0x09000000), subtlePressed = Color(0x06000000),
        flyout = Color(0xFFFCFCFC), stroke = Color(0x0F000000), strokeStrong = Color(0x72000000), divider = Color(0x0F000000),
        text = Color(0xE4000000), textSecondary = Color(0x9E000000), textTertiary = Color(0x72000000), textDisabled = Color(0x5C000000),
        success = Color(0xFF0F7B0F), caution = Color(0xFF9D5D00), critical = Color(0xFFC42B1C), scrim = Color(0x4D000000),
    )
}

@Immutable
class FluentType(
    val caption: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodyLarge: TextStyle,
    val subtitle: TextStyle,
    val title: TextStyle,
    val titleLarge: TextStyle,
    val display: TextStyle,
)

private fun style(size: TextUnit, line: TextUnit, weight: FontWeight, family: FontFamily): TextStyle =
    TextStyle(fontFamily = family, fontSize = size, lineHeight = line, fontWeight = weight)

// headings (page titles, row titles, tabs, the hero) are set in Manrope, a geometric sans bundled with the app; the text stays Segoe UI
private fun fluentType(family: FontFamily, heading: FontFamily = FluentFonts.heading) = FluentType(
    caption = style(12.sp, 16.sp, FontWeight.Normal, family),
    body = style(14.sp, 20.sp, FontWeight.Normal, family),
    bodyStrong = style(14.sp, 20.sp, FontWeight.SemiBold, family),
    bodyLarge = style(18.sp, 24.sp, FontWeight.Normal, family),
    subtitle = style(20.sp, 28.sp, FontWeight.SemiBold, heading),
    title = style(28.sp, 36.sp, FontWeight.Bold, heading),
    titleLarge = style(40.sp, 52.sp, FontWeight.Bold, heading),
    display = style(68.sp, 92.sp, FontWeight.Bold, heading),
)

/** Segoe UI (text) and Segoe Fluent Icons (glyphs) from the Windows font folder */
object FluentFonts {
    private val dir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
    private fun file(name: String): File? = File(dir, name).takeIf { it.isFile }

    val text: FontFamily by lazy {
        val fonts = listOfNotNull(
            file("segoeuil.ttf")?.let { Font(it, FontWeight.Light) },
            file("segoeuisl.ttf")?.let { Font(it, FontWeight(350)) },
            file("segoeui.ttf")?.let { Font(it, FontWeight.Normal) },
            file("seguisb.ttf")?.let { Font(it, FontWeight.SemiBold) },
            file("segoeuib.ttf")?.let { Font(it, FontWeight.Bold) },
            file("segoeuii.ttf")?.let { Font(it, FontWeight.Normal, FontStyle.Italic) },
        )
        if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
    }

    /** Manrope SemiBold and Bold from the app's resources (SIL Open Font License, fonts/Manrope-OFL.txt); the Segoe UI family when they are not found */
    val heading: FontFamily by lazy {
        runCatching {
            fun font(name: String, weight: FontWeight) = androidx.compose.ui.text.platform.Font(
                identity = name, weight = weight,
                data = FluentFonts::class.java.classLoader.getResourceAsStream("fonts/$name.ttf")!!.use { it.readBytes() },
            )
            FontFamily(font("Manrope-SemiBold", FontWeight.SemiBold), font("Manrope-Bold", FontWeight.Bold), font("Manrope-Bold", FontWeight.ExtraBold), font("Manrope-Bold", FontWeight.Black))
        }.getOrDefault(text)
    }

    val icons: FontFamily by lazy {
        val f = file("SegoeIcons.ttf") ?: file("segmdl2.ttf")
        if (f == null) FontFamily.Default else FontFamily(Font(f))
    }
}

/**
 * Corner radii, from the user's corner radius ([Appearance.cornerRadius]): read in composition, so a change redraws.
 *
 * One shape language: everything you press or type into (buttons, drop-downs, text boxes, chips, tags, tabs) is a [pill];
 * everything that holds content (cards, rows, menus, dialogs, posters) is a rounded rectangle from the radii below.
 */
object FluentShapes {
    /** the corner radius in use: the user's, or the one of the interface of a special theme (Skins) */
    private val radius: Float get() = Skins.spec?.cardRadius?.toFloat() ?: Appearance.cornerRadius.toFloat()
    val control: androidx.compose.ui.unit.Dp get() = (radius * 0.5f).dp
    val card: androidx.compose.ui.unit.Dp get() = radius.dp
    val overlay: androidx.compose.ui.unit.Dp get() = (radius * 1.2f).coerceAtMost(28f).dp
    val small: androidx.compose.ui.unit.Dp get() = (radius * 0.7f).dp

    /** a row inside a menu or list: the overlay radius less its 4 dp inset, so the curves are concentric */
    val row: androidx.compose.ui.unit.Dp get() = (overlay - 4.dp).coerceAtLeast(6.dp)

    /** buttons, drop-downs, text boxes, chips and tags */
    val pill: androidx.compose.ui.graphics.Shape get() = Skins.pill

    /** the box of a check box (a small square, not a pill) */
    val check: androidx.compose.ui.unit.Dp = 6.dp
}

val LocalFluentColors = compositionLocalOf<FluentColors> { error("FluentTheme missing") }
val LocalFluentType = compositionLocalOf<FluentType> { error("FluentTheme missing") }

object Fluent {
    val colors: FluentColors @Composable @ReadOnlyComposable get() = LocalFluentColors.current
    val type: FluentType @Composable @ReadOnlyComposable get() = LocalFluentType.current
}

enum class ThemeMode { System, Light, Dark }

/** In-app theme choice (persisted by the settings page); dark until the user chooses another (Settings > Appearance > App theme) */
object FluentSettings {
    var themeMode by mutableStateOf(ThemeMode.Dark)

    /** null = theme default / Windows accent */
    var accentOverride by mutableStateOf<Color?>(null)

    /** null = match accent / theme button colour */
    var buttonColorOverride by mutableStateOf<Color?>(null)

    private const val MODE_KEY = "desktop_theme_mode"
    private const val ACCENT_KEY = "desktop_accent_color"
    private const val BUTTON_COLOR_KEY = "desktop_button_color"

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    /** Reads the saved choice (call once the Android runtime exists) */
    fun load() {
        runCatching {
            val p = prefs()
            themeMode = ThemeMode.entries.getOrNull(p.getInt(MODE_KEY, ThemeMode.Dark.ordinal)) ?: ThemeMode.Dark
            accentOverride = if (p.contains(ACCENT_KEY)) Color(p.getInt(ACCENT_KEY, 0)) else null
            buttonColorOverride = if (p.contains(BUTTON_COLOR_KEY)) Color(p.getInt(BUTTON_COLOR_KEY, 0)) else null
        }
    }

    fun chooseMode(mode: ThemeMode) {
        themeMode = mode
        runCatching { prefs().edit().putInt(MODE_KEY, mode.ordinal).apply() }
    }

    fun chooseAccent(color: Color?) {
        accentOverride = color
        runCatching { prefs().edit().also { if (color == null) it.remove(ACCENT_KEY) else it.putInt(ACCENT_KEY, color.toArgb()) }.apply() }
    }

    fun chooseButtonColor(color: Color?) {
        buttonColorOverride = color
        runCatching { prefs().edit().also { if (color == null) it.remove(BUTTON_COLOR_KEY) else it.putInt(BUTTON_COLOR_KEY, color.toArgb()) }.apply() }
    }
}

@Composable
fun FluentTheme(content: @Composable () -> Unit) {
    // follow Windows while the app runs
    LaunchedEffect(Unit) {
        while (true) {
            WinTheme.refreshIfChanged()
            delay(3000)
        }
    }
    val revision = WinTheme.systemRevision
    val mode = FluentSettings.themeMode
    // the interface of a special theme (Netflix, Prime Video, ...) is a dark one whatever the app mode is: its bars and banners are made for a dark page
    val systemDark = remember(revision) { WinTheme.systemIsDark() }
    val dark = if (Skins.spec != null) true else when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val system = remember(revision) { WinTheme.systemAccent() }
    val preset = Skins.preset
    // the accent the user picked wins, then the theme's own, then the Windows one
    val override = FluentSettings.accentOverride ?: preset.accent
    val accent = if (override == null) system else AccentPalette(
        androidx.compose.ui.graphics.lerp(override, Color.White, 0.6f), androidx.compose.ui.graphics.lerp(override, Color.White, 0.35f),
        androidx.compose.ui.graphics.lerp(override, Color.White, 0.15f), override, androidx.compose.ui.graphics.lerp(override, Color.Black, 0.15f),
        androidx.compose.ui.graphics.lerp(override, Color.Black, 0.3f), androidx.compose.ui.graphics.lerp(override, Color.Black, 0.45f),
    )
    // "Pure black" is a look of the Classic theme: a colour theme brings its own page colour (with pure black under it the theme was nothing
    // but a different accent, and Black is the default look)
    val black = Appearance.backdrop == Backdrop.Black && preset.id == "classic"
    val btnOverride = FluentSettings.buttonColorOverride
    val tintStrength = Appearance.surfaceTint
    val colors = remember(dark, accent, black, preset, btnOverride, tintStrength) {
        fluentColors(dark, accent, btnOverride).themed(preset, tintStrength).let { if (dark && black) it.oled() else it }
    }
    val type = remember { fluentType(FluentFonts.text) }
    CompositionLocalProvider(LocalFluentColors provides colors, LocalFluentType provides type, content = content)
}

/** Everything but the window's caption buttons (which the title bar hit test places at the Windows scale) follows the UI scale */
@Composable
fun ScaledContent(content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    val scale = Appearance.uiScale
    val density = remember(base, scale) { if (scale == 1f) base else androidx.compose.ui.unit.Density(base.density * scale, base.fontScale) }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density, content = content)
}

/** Pure black surfaces (OLED) with the same text, button and accent colours */
private fun FluentColors.oled() = FluentColors(
    dark = dark, accent = accent, accentHover = accentHover, accentPressed = accentPressed, onAccent = onAccent, accentText = accentText,
    bg = Color.Black, bgPane = Color.Black, layer = Color(0xFF080808), card = Color(0x0AFFFFFF), cardHover = Color(0x12FFFFFF), cardPressed = Color(0x08FFFFFF),
    control = control, controlHover = controlHover, controlPressed = controlPressed, controlDisabled = controlDisabled,
    subtleHover = subtleHover, subtlePressed = subtlePressed, flyout = Color(0xFF151515), stroke = stroke, strokeStrong = strokeStrong, divider = divider,
    text = text, textSecondary = textSecondary, textTertiary = textTertiary, textDisabled = textDisabled,
    success = success, caution = caution, critical = critical, scrim = scrim,
    accent2 = accent2, button = button, button2 = button2, onButton = onButton,
)

