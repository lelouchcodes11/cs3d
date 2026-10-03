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
)

private fun fluentColors(dark: Boolean, a: AccentPalette): FluentColors =
    if (dark) FluentColors(
        dark = true,
        accent = a.light2, accentHover = a.light2.copy(alpha = 0.9f), accentPressed = a.light2.copy(alpha = 0.8f),
        onAccent = Color.Black, accentText = a.light3,
        bg = Color(0xFF202020), bgPane = Color(0xFF1C1C1C), layer = Color(0xFF272727),
        card = Color(0x0DFFFFFF), cardHover = Color(0x15FFFFFF), cardPressed = Color(0x0AFFFFFF),
        control = Color(0x0FFFFFFF), controlHover = Color(0x15FFFFFF), controlPressed = Color(0x08FFFFFF), controlDisabled = Color(0x0BFFFFFF),
        subtleHover = Color(0x0FFFFFFF), subtlePressed = Color(0x0AFFFFFF),
        flyout = Color(0xFF2C2C2C), stroke = Color(0x14FFFFFF), strokeStrong = Color(0x33FFFFFF), divider = Color(0x15FFFFFF),
        text = Color(0xFFFFFFFF), textSecondary = Color(0xC5FFFFFF), textTertiary = Color(0x87FFFFFF), textDisabled = Color(0x5DFFFFFF),
        success = Color(0xFF6CCB5F), caution = Color(0xFFFCE100), critical = Color(0xFFFF99A4), scrim = Color(0x99000000),
    ) else FluentColors(
        dark = false,
        accent = a.dark1, accentHover = a.dark1.copy(alpha = 0.9f), accentPressed = a.dark1.copy(alpha = 0.8f),
        onAccent = Color.White, accentText = a.dark2,
        bg = Color(0xFFF3F3F3), bgPane = Color(0xFFEEEEEE), layer = Color(0xFFFAFAFA),
        card = Color(0xB3FFFFFF), cardHover = Color(0x80F9F9F9), cardPressed = Color(0x4DF9F9F9),
        control = Color(0xB3FFFFFF), controlHover = Color(0x80F9F9F9), controlPressed = Color(0x4DF9F9F9), controlDisabled = Color(0x4DF9F9F9),
        subtleHover = Color(0x09000000), subtlePressed = Color(0x06000000),
        flyout = Color(0xFFFCFCFC), stroke = Color(0x0F000000), strokeStrong = Color(0x72000000), divider = Color(0x0F000000),
        text = Color(0xE4000000), textSecondary = Color(0x9E000000), textTertiary = Color(0x72000000), textDisabled = Color(0x5C000000),
        success = Color(0xFF0F7B0F), caution = Color(0xFF9D5D00), critical = Color(0xFFC42B1C), scrim = Color(0x4D000000),
    )

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

private fun fluentType(family: FontFamily) = FluentType(
    caption = style(12.sp, 16.sp, FontWeight.Normal, family),
    body = style(14.sp, 20.sp, FontWeight.Normal, family),
    bodyStrong = style(14.sp, 20.sp, FontWeight.SemiBold, family),
    bodyLarge = style(18.sp, 24.sp, FontWeight.Normal, family),
    subtitle = style(20.sp, 28.sp, FontWeight.SemiBold, family),
    title = style(28.sp, 36.sp, FontWeight.SemiBold, family),
    titleLarge = style(40.sp, 52.sp, FontWeight.SemiBold, family),
    display = style(68.sp, 92.sp, FontWeight.SemiBold, family),
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

    val icons: FontFamily by lazy {
        val f = file("SegoeIcons.ttf") ?: file("segmdl2.ttf")
        if (f == null) FontFamily.Default else FontFamily(Font(f))
    }
}

object FluentShapes {
    val control = 4.dp
    val card = 8.dp
    val overlay = 8.dp
}

val LocalFluentColors = compositionLocalOf<FluentColors> { error("FluentTheme missing") }
val LocalFluentType = compositionLocalOf<FluentType> { error("FluentTheme missing") }

object Fluent {
    val colors: FluentColors @Composable @ReadOnlyComposable get() = LocalFluentColors.current
    val type: FluentType @Composable @ReadOnlyComposable get() = LocalFluentType.current
}

enum class ThemeMode { System, Light, Dark }

/** In-app theme choice (persisted by the settings page) */
object FluentSettings {
    var themeMode by mutableStateOf(ThemeMode.System)

    /** null = Windows accent */
    var accentOverride by mutableStateOf<Color?>(null)

    private const val MODE_KEY = "desktop_theme_mode"
    private const val ACCENT_KEY = "desktop_accent_color"

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    /** Reads the saved choice (call once the Android runtime exists) */
    fun load() {
        runCatching {
            val p = prefs()
            themeMode = ThemeMode.entries.getOrNull(p.getInt(MODE_KEY, 0)) ?: ThemeMode.System
            accentOverride = if (p.contains(ACCENT_KEY)) Color(p.getInt(ACCENT_KEY, 0)) else null
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
    val dark = when (mode) {
        ThemeMode.System -> remember(revision) { WinTheme.systemIsDark() }
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val system = remember(revision) { WinTheme.systemAccent() }
    val override = FluentSettings.accentOverride
    val accent = if (override == null) system else AccentPalette(
        androidx.compose.ui.graphics.lerp(override, Color.White, 0.6f), androidx.compose.ui.graphics.lerp(override, Color.White, 0.35f),
        androidx.compose.ui.graphics.lerp(override, Color.White, 0.15f), override, androidx.compose.ui.graphics.lerp(override, Color.Black, 0.15f),
        androidx.compose.ui.graphics.lerp(override, Color.Black, 0.3f), androidx.compose.ui.graphics.lerp(override, Color.Black, 0.45f),
    )
    val colors = remember(dark, accent) { fluentColors(dark, accent) }
    val type = remember { fluentType(FluentFonts.text) }
    CompositionLocalProvider(LocalFluentColors provides colors, LocalFluentType provides type, content = content)
}
