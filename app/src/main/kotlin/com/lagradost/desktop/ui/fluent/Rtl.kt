package com.lagradost.desktop.ui.fluent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * For a bar whose pill or line glides to the place of the chosen item: the places are measured from the left, so the bar is laid out left to
 * right in every language, and in a right-to-left language (Arabic) its items are put in the opposite order ([visual]). The bar then looks
 * mirrored and the glide needs no other change.
 */
@Composable
fun LeftToRight(content: @Composable (rtl: Boolean) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { content(rtl) }
}

/** The indices 0 until [count] in the order they are drawn from the left */
fun visual(count: Int, rtl: Boolean): List<Int> = if (rtl) (count - 1 downTo 0).toList() else (0 until count).toList()
