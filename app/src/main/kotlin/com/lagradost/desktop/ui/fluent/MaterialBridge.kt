package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.theme.LocalSharedInfiniteTransition

/**
 * The Material 3 theme of what is still written in Material (engine dialogs, the PIN dialog, custom
 * preference widgets of the old settings): colours, fonts and shapes come from the Fluent tokens so
 * those pieces look like the rest of the Windows app.
 */
@Composable
fun FluentMaterialTheme(content: @Composable () -> Unit) {
    val c = Fluent.colors
    val family = FluentFonts.text
    val scheme = remember(c) {
        if (c.dark) darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accent, onPrimaryContainer = c.onAccent,
            secondary = c.accentText, onSecondary = c.onAccent, tertiary = c.accentText,
            background = c.bg, onBackground = c.text, surface = c.flyout, onSurface = c.text,
            surfaceVariant = c.layer, onSurfaceVariant = c.textSecondary, surfaceTint = c.accent,
            surfaceContainerLowest = c.bgPane, surfaceContainerLow = c.bg, surfaceContainer = c.layer,
            surfaceContainerHigh = c.flyout, surfaceContainerHighest = c.flyout,
            outline = c.strokeStrong, outlineVariant = c.divider, error = c.critical,
        ) else lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accent, onPrimaryContainer = c.onAccent,
            secondary = c.accentText, onSecondary = c.onAccent, tertiary = c.accentText,
            background = c.bg, onBackground = c.text, surface = c.flyout, onSurface = c.text,
            surfaceVariant = c.layer, onSurfaceVariant = c.textSecondary, surfaceTint = c.accent,
            surfaceContainerLowest = c.bgPane, surfaceContainerLow = c.bg, surfaceContainer = c.layer,
            surfaceContainerHigh = c.flyout, surfaceContainerHighest = c.flyout,
            outline = c.strokeStrong, outlineVariant = c.divider, error = c.critical,
        )
    }
    val typography = remember(family) {
        val b = Typography()
        Typography(
            displayLarge = b.displayLarge.copy(fontFamily = family), displayMedium = b.displayMedium.copy(fontFamily = family), displaySmall = b.displaySmall.copy(fontFamily = family),
            headlineLarge = b.headlineLarge.copy(fontFamily = family), headlineMedium = b.headlineMedium.copy(fontFamily = family), headlineSmall = b.headlineSmall.copy(fontFamily = family),
            titleLarge = b.titleLarge.copy(fontFamily = family), titleMedium = b.titleMedium.copy(fontFamily = family), titleSmall = b.titleSmall.copy(fontFamily = family),
            bodyLarge = b.bodyLarge.copy(fontFamily = family), bodyMedium = b.bodyMedium.copy(fontFamily = family), bodySmall = b.bodySmall.copy(fontFamily = family),
            labelLarge = b.labelLarge.copy(fontFamily = family), labelMedium = b.labelMedium.copy(fontFamily = family), labelSmall = b.labelSmall.copy(fontFamily = family),
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(8.dp),
    )
    val transition = rememberInfiniteTransition(label = "GlobalSharedTransition")
    CompositionLocalProvider(LocalSharedInfiniteTransition provides transition) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
