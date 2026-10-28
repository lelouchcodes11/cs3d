package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A page of shelves composes a row only when it comes into view (Compose for Desktop has no look-ahead for lazy lists): the first scroll over a page
 * composed, measured and started the pictures of every row that entered, 8 or more cards each, in the frames of the scroll itself (a frame of 70 to
 * 110 ms had the window's thread about half busy in the card composition of the entering row). The list is made taller than the window by [extra]
 * and cut off at the window's edge, so the rows of the next screenful are composed, laid out and loading their pictures while the page is idle; the
 * same [extra] is added to the list's bottom padding ([lazyPadding]), so the end of the list stays where it was. Nothing of it is drawn or reachable
 * by the pointer. The value is read while measuring, so it changing does not recompose the page. -Dcloudstream.composeahead=false turns it off.
 */
fun Modifier.composeAhead(extra: State<Dp>?): Modifier = if (extra == null) this else this.clipToBounds().layout { measurable, constraints ->
    val e = if (constraints.hasBoundedHeight) extra.value.roundToPx() else 0
    val taller = Constraints(constraints.minWidth, constraints.maxWidth, constraints.minHeight + e, if (e > 0) constraints.maxHeight + e else constraints.maxHeight)
    val placeable = measurable.measure(taller)
    layout(placeable.width, (placeable.height - e).coerceAtLeast(0)) { placeable.place(0, 0) }
}

/** The list's padding with the look-ahead added to its bottom (read while measuring) */
fun lazyPadding(extra: State<Dp>?, top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = top
    override fun calculateBottomPadding(): Dp = bottom + (extra?.value ?: 0.dp)
}

/** How far below the window a page composes ahead: nothing at first, [step] and then twice [step] once the page has been open for a moment. Null when off (weak PC, the switch) */
@Composable
fun rememberComposeAhead(step: Dp = 450.dp): State<Dp>? {
    if (System.getProperty("cloudstream.composeahead") == "false" || com.lagradost.desktop.platform.LowEnd.detected) return null
    val ahead = remember { mutableStateOf(0.dp) }
    LaunchedEffect(Unit) {
        // after the page's first frames and its pictures, in two steps so that the extra composition does not come as one long frame
        delay(1500)
        ahead.value = step
        delay(600)
        ahead.value = step * 2
    }
    return ahead
}
