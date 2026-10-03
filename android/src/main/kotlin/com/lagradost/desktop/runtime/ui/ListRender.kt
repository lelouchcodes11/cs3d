package com.lagradost.desktop.runtime.ui

import android.view.View
import android.widget.AbsListView
import android.widget.ListView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * A group whose children change during layout (lists add and recycle item views while laying out):
 * the children are subcomposed in the measure pass, after the native layout, so new items show in
 * the same frame. [childModifier] adds per child behaviour (list item clicks).
 */
@Composable
internal fun SubcomposedGroupNode(group: android.view.ViewGroup, modifier: Modifier, childModifier: (View) -> Modifier = { Modifier }) {
    SubcomposeLayout(modifier.then(groupClip(group))) { c ->
        val kids = group.children.toList()
        val measured = ArrayList<Pair<View, Placeable>>(kids.size)
        for (child in kids) {
            if (child.getVisibility() == View.GONE) continue
            val m = subcompose(child) { ViewNode(child, Modifier.zIndex(child.getZ()).then(childModifier(child))) }.firstOrNull() ?: continue
            measured.add(child to m.measure(Constraints.fixed(maxOf(0, child.getWidth()), maxOf(0, child.getHeight()))))
        }
        layout(c.maxWidth, c.maxHeight) {
            val sx = group.getScrollX()
            val sy = group.getScrollY()
            for ((v, p) in measured) p.place(v.getLeft() - sx, v.getTop() - sy)
        }
    }
}

/** Mouse wheel / drag scrolling forwarded to a native scroll function returning the consumed px */
@Composable
private fun nativeScroll(key: Any, vertical: Boolean, onScrolling: (Boolean) -> Unit, scrollBy: (Int) -> Int): Modifier {
    val remainder = remember(key) { FloatArray(1) }
    val state = rememberScrollableState { delta ->
        val wanted = remainder[0] + delta
        val step = wanted.roundToInt()
        remainder[0] = wanted - step
        val consumed = scrollBy(step)
        if (consumed != step) {
            remainder[0] = 0f
            consumed.toFloat()
        } else delta
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { onScrolling(it) }
    }
    return Modifier.scrollable(state, if (vertical) Orientation.Vertical else Orientation.Horizontal, reverseDirection = true)
}

// ------------------------------------------------------------------------------------------------
// ListView / GridView

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AbsListNode(view: AbsListView, modifier: Modifier) {
    val scroll = nativeScroll(view, vertical = true, onScrolling = view::onUserScrollState) { view.trackMotionScroll(it) }
    val dividers = if (view is ListView) Modifier.drawWithContent {
        drawContent()
        view.renderVersion.intValue
        withAndroidCanvas { view.drawDividers(it) }
    } else Modifier
    SubcomposedGroupNode(view, modifier.then(scroll).then(dividers)) { child ->
        val position = view.positionOf(child)
        val adapterEnabled = if (view is ListView) {
            val h = view.getHeaderViewsCount()
            val n = view.getAdapter()?.getCount() ?: 0
            position in h until h + n && view.getAdapter()?.isEnabled(position - h) != false
        } else view.getAdapter()?.isEnabled(position) != false
        if (position < 0 || !view.isEnabled() || !adapterEnabled) Modifier
        else Modifier.combinedClickable(
            onClick = { view.performItemClick(child, position, view.getItemIdAtPosition(position)) },
            onLongClick = if (view.mOnItemLongClickListener != null) {
                { view.mOnItemLongClickListener?.onItemLongClick(view, child, position, view.getItemIdAtPosition(position)) }
            } else null,
        )
    }
}

@Composable
internal fun ListViewNode(view: ListView, modifier: Modifier) = AbsListNode(view, modifier)

@Composable
internal fun GridViewNode(view: android.widget.GridView, modifier: Modifier) = AbsListNode(view, modifier)

// ------------------------------------------------------------------------------------------------
// RecyclerView

@Composable
internal fun RecyclerViewNode(view: RecyclerView, modifier: Modifier) {
    val lm = view.getLayoutManager()
    val vertical = lm?.canScrollVertically() == true
    val horizontal = lm?.canScrollHorizontally() == true
    val scroll = if (vertical || horizontal) {
        nativeScroll(view, vertical, onScrolling = view::onUserScrollState) { step ->
            if (vertical) view.scrollByInternal(0, step)[1] else view.scrollByInternal(step, 0)[0]
        }
    } else Modifier
    val decorations = Modifier.drawWithContent {
        view.renderVersion.intValue
        withAndroidCanvas { view.drawDecorations(it, over = false) }
        drawContent()
        withAndroidCanvas { view.drawDecorations(it, over = true) }
    }
    SubcomposedGroupNode(view, modifier.then(scroll).then(decorations))
}
