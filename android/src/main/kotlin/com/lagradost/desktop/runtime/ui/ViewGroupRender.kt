package com.lagradost.desktop.runtime.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Clipping of a view group: children are clipped to the group's bounds (clipChildren) and, for
 * groups with padding, to the padded area (clipToPadding), like ViewGroup.dispatchDraw.
 */
internal fun groupClip(group: ViewGroup): Modifier {
    if (!group.getClipChildren()) return Modifier
    val padded = group.getClipToPadding() &&
        (group.getPaddingLeft() != 0 || group.getPaddingTop() != 0 || group.getPaddingRight() != 0 || group.getPaddingBottom() != 0)
    if (!padded) return Modifier.clipToBounds()
    return Modifier.clipToBounds().drawWithContent {
        clipRect(
            group.getPaddingLeft().toFloat(), group.getPaddingTop().toFloat(),
            size.width - group.getPaddingRight(), size.height - group.getPaddingBottom(),
        ) { this@drawWithContent.drawContent() }
    }
}

/** What a framework group draws itself: card background, LinearLayout dividers, custom onDraw */
private fun groupDrawing(group: ViewGroup): Modifier = when {
    group is androidx.cardview.widget.CardView -> Modifier.drawBehind {
        group.renderVersion.intValue
        val bg = group.getCardBackgroundColor()?.colorFor(group, ThemeBridge.surface) ?: group.getBackground().solidColor() ?: ThemeBridge.surface
        drawRoundRect(Color(bg), cornerRadius = CornerRadius(group.getRadius(), group.getRadius()))
    }
    group.hasCustomDraw -> Modifier.drawBehind {
        group.renderVersion.intValue
        withAndroidCanvas { group.draw(it) }
    }
    group is LinearLayout && group.getDividerDrawable() != null -> Modifier.drawWithContent {
        drawContent()
        group.renderVersion.intValue
        withAndroidCanvas { group.drawDividers(it) }
    }
    else -> Modifier
}

/**
 * A view group: its children's nodes placed at their frames (minus the group's scroll offset),
 * sized to their laid out size.
 */
@Composable
internal fun GroupNode(group: ViewGroup, modifier: Modifier, content: @Composable () -> Unit = { ChildNodes(group) }) {
    Layout(content = content, modifier = modifier.then(groupDrawing(group)).then(groupClip(group))) { ms, c ->
        val placeables = ms.map { m ->
            val v = m.view
            if (v != null) m.measure(Constraints.fixed(max(0, v.getWidth()), max(0, v.getHeight())))
            else m.measure(Constraints.fixed(c.maxWidth, c.maxHeight))
        }
        layout(c.maxWidth, c.maxHeight) {
            val sx = group.getScrollX()
            val sy = group.getScrollY()
            for (i in ms.indices) {
                val v = ms[i].view
                if (v != null) placeables[i].place(v.getLeft() - sx, v.getTop() - sy) else placeables[i].place(0, 0)
            }
        }
    }
}

/**
 * ScrollView / NestedScrollView / HorizontalScrollView: the content scrolls natively (View.scrollTo),
 * mouse wheel and drags scroll it through Compose's scrollable.
 */
@Composable
internal fun ScrollContainerNode(view: FrameLayout, vertical: Boolean, modifier: Modifier) {
    val remainder = remember(view) { FloatArray(1) }
    val state = rememberScrollableState { delta ->
        val before = if (vertical) view.getScrollY() else view.getScrollX()
        val wanted = remainder[0] + delta
        val step = wanted.roundToInt()
        remainder[0] = wanted - step
        if (vertical) (view as ScrollView).onUserScroll(before + step) else (view as HorizontalScrollView).onUserScroll(before + step)
        val after = if (vertical) view.getScrollY() else view.getScrollX()
        if (after == before && step != 0) {
            remainder[0] = 0f
            0f
        } else delta
    }
    GroupNode(
        view,
        modifier.scrollable(state, if (vertical) Orientation.Vertical else Orientation.Horizontal, reverseDirection = true),
    )
}
