package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
private fun fluentScrollbarStyle(): ScrollbarStyle {
    val c = Fluent.colors
    return ScrollbarStyle(
        minimalHeight = 36.dp, thickness = 6.dp, shape = RoundedCornerShape(3.dp), hoverDurationMillis = 200,
        unhoverColor = c.textSecondary.copy(alpha = 0.32f), hoverColor = c.textSecondary.copy(alpha = 0.75f),
    )
}

/** Thin overlay scrollbar at the right edge of a lazy list; [top] keeps it below a see-through top bar */
@Composable
fun BoxScope.FluentScrollbar(state: LazyListState, top: Dp = 0.dp) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state), Modifier.zIndex(10f).align(Alignment.CenterEnd).fillMaxHeight().padding(top = top, end = 2.dp),
        style = fluentScrollbarStyle(),
    )
}

@Composable
fun BoxScope.FluentScrollbar(state: LazyGridState, top: Dp = 0.dp) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state), Modifier.zIndex(10f).align(Alignment.CenterEnd).fillMaxHeight().padding(top = top, end = 2.dp),
        style = fluentScrollbarStyle(),
    )
}

@Composable
fun BoxScope.FluentScrollbar(state: ScrollState, top: Dp = 0.dp) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state), Modifier.zIndex(10f).align(Alignment.CenterEnd).fillMaxHeight().padding(top = top, end = 2.dp),
        style = fluentScrollbarStyle(),
    )
}
