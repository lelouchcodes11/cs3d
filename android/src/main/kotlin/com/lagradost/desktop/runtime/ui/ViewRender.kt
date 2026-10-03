package com.lagradost.desktop.runtime.ui

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.AbsSpinner
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.zIndex
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlin.math.max

/*
 * Renders android.view.View trees with Compose.
 *
 * Views measure and lay out themselves like on Android (View.measure -> onMeasure,
 * View.layout -> onLayout, see LayoutScheduler); every view is one Compose node that is given the
 * view's laid out size and placed at its frame, so Compose only draws, scrolls and handles input.
 * One Android px is one Compose px (see DisplaySync).
 */

// ------------------------------------------------------------------------------------------------
// MeasureSpec <-> Constraints

internal fun widthSpec(c: Constraints): Int = when {
    c.hasFixedWidth -> MeasureSpec.makeMeasureSpec(c.maxWidth, MeasureSpec.EXACTLY)
    c.hasBoundedWidth -> MeasureSpec.makeMeasureSpec(c.maxWidth, MeasureSpec.AT_MOST)
    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
}

internal fun heightSpec(c: Constraints): Int = when {
    c.hasFixedHeight -> MeasureSpec.makeMeasureSpec(c.maxHeight, MeasureSpec.EXACTLY)
    c.hasBoundedHeight -> MeasureSpec.makeMeasureSpec(c.maxHeight, MeasureSpec.AT_MOST)
    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
}

internal fun specMode(spec: Int) = MeasureSpec.getMode(spec)
internal fun specSize(spec: Int) = MeasureSpec.getSize(spec)
internal fun childSpec(spec: Int, padding: Int, dimension: Int): Int = ViewGroup.getChildMeasureSpec(spec, padding, dimension)

internal val Measurable.view: View? get() = layoutId as? View

internal fun View.lp(): ViewGroup.LayoutParams =
    layoutParamsOrNull() ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

internal val ViewGroup.LayoutParams.leftMargin get() = (this as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
internal val ViewGroup.LayoutParams.topMargin get() = (this as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
internal val ViewGroup.LayoutParams.rightMargin get() = (this as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0
internal val ViewGroup.LayoutParams.bottomMargin get() = (this as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

/** Traversal counters for the dev tools */
object LayoutReporter {
    @Volatile
    var reports = 0

    @Volatile
    var flushed = 0
}

/** Window position (px) of every hosted root view (View.getLocationInWindow, DevServer /find) */
object HostPositions {
    val positions = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, androidx.compose.ui.geometry.Offset>())

    /** Right-click at window px: long-press the view under the pointer, or the row that owns it. */
    fun longPressAt(x: Float, y: Float): Boolean {
        val hits = ArrayList<View>()
        for ((root, origin) in positions.entries.toList()) {
            collect(root, origin.x, origin.y, x, y, hits)
        }
        // Smallest view is the one actually under the pointer. A full-window ComposeView sits
        // above the Android tree and would otherwise hide every row.
        val ordered = hits.sortedBy { it.getWidth().toLong() * it.getHeight() }
        for (start in ordered) {
            var target: View? = start
            while (target != null) {
                if (target.isLongClickable() && target.isEnabled() && target.isShown()) {
                    return target.performLongClick()
                }
                target = target.getParent() as? View
            }
        }
        return false
    }

    private fun collect(v: View, left: Float, top: Float, x: Float, y: Float, out: MutableList<View>) {
        if (v.getVisibility() != View.VISIBLE) return
        if (x < left || y < top || x >= left + v.getWidth() || y >= top + v.getHeight()) return
        out.add(v)
        if (v is ViewGroup) {
            val sx = v.getScrollX()
            val sy = v.getScrollY()
            for (i in 0 until v.getChildCount()) {
                val c = v.getChildAt(i) ?: continue
                collect(c, left + c.getLeft() - sx, top + c.getTop() - sy, x, y, out)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Host

/**
 * Shows an Android view tree, like a window: the root is attached while shown, measured against
 * the available space with its own LayoutParams (ViewRootImpl.getRootMeasureSpec) and laid out at
 * (0, 0) whenever a layout is requested in the tree.
 */
@Composable
fun AndroidViewHost(view: View, modifier: Modifier = Modifier) {
    val request = remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        view.hostLayoutRequest = request
        view.dispatchAttachedToWindow()
        onDispose {
            view.dispatchDetachedFromWindow()
            if (view.hostLayoutRequest === request) view.hostLayoutRequest = null
            LayoutScheduler.onHostDisposed(view)
        }
    }
    val tracked = modifier.then(Modifier.onGloballyPositioned { HostPositions.positions[view] = it.positionInWindow() })
    Layout(content = { ViewNode(view) }, modifier = tracked) { measurables, constraints ->
        request.intValue // a layout request in the tree measures again
        val lp = view.lp()
        val hm = lp.leftMargin + lp.rightMargin
        val vm = lp.topMargin + lp.bottomMargin
        val wSpec = childSpec(widthSpec(constraints), hm, lp.width)
        val hSpec = childSpec(heightSpec(constraints), vm, lp.height)
        Snapshot.withoutReadObservation {
            LayoutReporter.reports++
            LayoutScheduler.performTraversal(view, wSpec, hSpec)
        }
        val w = max(0, view.getWidth())
        val h = max(0, view.getHeight())
        val p = measurables.firstOrNull()?.measure(Constraints.fixed(w, h))
        layout((w + hm).coerceIn(constraints.minWidth, max(constraints.minWidth, constraints.maxWidth)), (h + vm).coerceIn(constraints.minHeight, max(constraints.minHeight, constraints.maxHeight))) {
            p?.place(lp.leftMargin, lp.topMargin)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Nodes

/** Sizes a view's node to the view's frame; told by the view when layout changes its frame */
private class ViewFrameElement(val view: View) : ModifierNodeElement<ViewFrameNode>() {
    override fun create() = ViewFrameNode(view)
    override fun update(node: ViewFrameNode) = node.attachTo(view)
    override fun equals(other: Any?) = other is ViewFrameElement && other.view === view
    override fun hashCode() = System.identityHashCode(view)
}

private class ViewFrameNode(private var view: View) : Modifier.Node(), LayoutModifierNode, ViewNodeHandle {
    override fun onAttach() {
        view.desktopNode = this
    }

    override fun onDetach() {
        if (view.desktopNode === this) view.desktopNode = null
    }

    fun attachTo(v: View) {
        if (v === view) return
        if (view.desktopNode === this) view.desktopNode = null
        view = v
        if (isAttached) {
            v.desktopNode = this
            invalidateMeasurement()
        }
    }

    override fun invalidateNodeMeasurement() {
        if (isAttached) invalidateMeasurement()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val w = max(0, view.getWidth())
        val h = max(0, view.getHeight())
        val p = measurable.measure(Constraints.fixed(w, h))
        return layout(w, h) { p.place(0, 0) }
    }
}

/** Composes [view]; GONE views emit nothing (they are not laid out either) */
@Composable
fun ViewNode(view: View, modifier: Modifier = Modifier) {
    if (view.getVisibility() == View.GONE) return
    val m = modifier.layoutId(view).then(viewModifier(view)).then(ViewFrameElement(view))
    when (view) {
        is MpvSurfaceView -> MpvSurfaceViewNode(view, m)
        is WebView -> WebViewNode(view, m)
        is EditText -> EditTextNode(view, m)
        is com.google.android.material.chip.Chip -> TextViewNode(view, m)
        is CompoundButton -> CompoundButtonNode(view, m)
        is CheckedTextView -> CheckedTextNode(view, m)
        is TextView -> TextViewNode(view, m)
        is ImageView -> ImageViewNode(view, m)
        is RatingBar -> RatingBarNode(view, m)
        is SeekBar -> SeekBarNode(view, m)
        is ProgressBar -> ProgressBarNode(view, m)
        is AbsSpinner -> SpinnerNode(view, m)
        is GridView -> GridViewNode(view, m)
        is ListView -> ListViewNode(view, m)
        is androidx.recyclerview.widget.RecyclerView -> RecyclerViewNode(view, m)
        is ScrollView -> ScrollContainerNode(view, vertical = true, m)
        is HorizontalScrollView -> ScrollContainerNode(view, vertical = false, m)
        is androidx.compose.ui.platform.ComposeView -> ComposeViewNode(view, m)
        is ViewGroup -> GroupNode(view, m)
        else -> PlainViewNode(view, m)
    }
}

/** Compose content stored on a ComposeView, in the view's own context. */
@Composable
private fun ComposeViewNode(view: androidx.compose.ui.platform.ComposeView, modifier: Modifier) {
    val version = view.contentVersion
    Box(modifier) {
        key(version) {
            val ctx = view.getContext()
            val backDispatcherOwner = ctx as? androidx.activity.OnBackPressedDispatcherOwner
            CompositionLocalProvider(
                LocalContext provides ctx,
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides backDispatcherOwner
            ) {
                view.getContent()?.invoke()
            }
        }
    }
}

/** The children of a ViewGroup, in order, keyed by identity */
@Composable
internal fun ChildNodes(group: ViewGroup) {
    for (child in group.children.toList()) {
        key(System.identityHashCode(child), child) {
            ViewNode(child, Modifier.zIndex(child.getZ()))
        }
    }
}

/**
 * Leaf content placed at rects of the view (px): child i of [content] is measured to the size of
 * rects(i) and placed at its position.
 */
@Composable
internal fun PlacedContent(modifier: Modifier, rects: (Int, Int, Int) -> IntRect, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { ms, c ->
        val w = c.maxWidth
        val h = c.maxHeight
        val placed = ms.mapIndexed { i, m ->
            val r = rects(i, w, h)
            m.measure(Constraints.fixed(max(0, r.width), max(0, r.height))) to r
        }
        layout(w, h) { for ((p, r) in placed) p.place(r.left, r.top) }
    }
}

/** The padded area of a view */
internal fun View.paddedRect(w: Int, h: Int) =
    IntRect(getPaddingLeft(), getPaddingTop(), max(getPaddingLeft(), w - getPaddingRight()), max(getPaddingTop(), h - getPaddingBottom()))

// ------------------------------------------------------------------------------------------------
// Common view behaviour: transforms, background, clipping, input

/** Rounded outline of a view's background (used for clipToOutline and hover highlights) */
internal fun outlineShape(view: View): Shape {
    if (view is com.google.android.material.imageview.ShapeableImageView && view.hasCornerShape()) {
        fun corner(index: Int): CornerSize = object : CornerSize {
            override fun toPx(shapeSize: Size, density: Density): Float = view.cornerRadius(index, shapeSize.height)
        }
        return RoundedCornerShape(corner(0), corner(1), corner(2), corner(3))
    }
    if (view is androidx.cardview.widget.CardView) {
        val r = view.getRadius()
        if (r > 0f) return RoundedCornerShape(r)
    }
    val bg = view.getBackground() ?: return RectangleShape
    return drawableShape(bg) ?: RectangleShape
}

internal fun drawableShape(d: Drawable): Shape? = when (d) {
    is GradientDrawable -> {
        val radii = d.getCornerRadii()
        when {
            radii != null && radii.size >= 8 -> RoundedCornerShape(radii[0], radii[2], radii[4], radii[6])
            d.getCornerRadius() > 0f -> RoundedCornerShape(d.getCornerRadius())
            d.getShape() == GradientDrawable.OVAL -> RoundedCornerShape(50)
            else -> null
        }
    }
    is com.google.android.material.chip.ChipDrawable -> RoundedCornerShape(d.getChipCornerRadius())
    is RippleDrawable -> (0 until d.getNumberOfLayers()).firstNotNullOfOrNull { i -> d.getDrawable(i)?.let(::drawableShape) }
    is LayerDrawable -> (0 until d.getNumberOfLayers()).firstNotNullOfOrNull { i -> d.getDrawable(i)?.let(::drawableShape) }
    is StateListDrawable -> drawableShape(d.getCurrent().takeIf { it !== d } ?: return null)
    is InsetDrawable -> d.getDrawable()?.let(::drawableShape)
    else -> null
}

/** Paints an Android drawable filling [w] x [h] px of the draw scope */
fun DrawScope.drawAndroidDrawable(d: Drawable, w: Int, h: Int, left: Int = 0, top: Int = 0) {
    if (w <= 0 || h <= 0) return
    drawIntoCanvas { canvas ->
        val c = android.graphics.Canvas.wrap(canvas.nativeCanvas, size.width.toInt(), size.height.toInt())
        d.setBounds(left, top, left + w, top + h)
        try {
            d.draw(c)
        } catch (t: Throwable) {
            android.util.Log.e("ViewRender", "drawable $d failed to draw", t)
        }
    }
}

/** Runs [block] with a compat canvas over the draw scope */
internal fun DrawScope.withAndroidCanvas(block: (android.graphics.Canvas) -> Unit) {
    drawIntoCanvas { canvas ->
        val c = android.graphics.Canvas.wrap(canvas.nativeCanvas, size.width.toInt(), size.height.toInt())
        try {
            block(c)
        } catch (t: Throwable) {
            android.util.Log.e("ViewRender", "custom drawing failed", t)
        }
    }
}

/** true when a view reacts to pointer input */
private fun View.isInteractive(): Boolean =
    isClickable() || isLongClickable() || mOnTouchListener != null || overridesTouch

private val touchOverrides = java.util.concurrent.ConcurrentHashMap<Class<*>, Boolean>()

/** Custom (extension) view classes that implement onTouchEvent themselves */
private val View.overridesTouch: Boolean
    get() = touchOverrides.getOrPut(javaClass) {
        var c: Class<*>? = javaClass
        var custom = false
        while (c != null && c != View::class.java) {
            val n = c.name
            if (!n.startsWith("android.") && !n.startsWith("androidx.") && !n.startsWith("com.google.android.material.")) {
                if (c.declaredMethods.any { (it.name == "onTouchEvent" || it.name == "dispatchTouchEvent") && it.parameterCount == 1 }) {
                    custom = true
                    break
                }
            }
            c = c.superclass
        }
        custom
    }

@Composable
private fun viewModifier(view: View): Modifier {
    val visible = view.getVisibility() == View.VISIBLE
    val interaction = remember(view) { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focusRequester = remember(view) { FocusRequester() }
    val request = view.focusRequest.intValue
    LaunchedEffect(request) {
        if (request > 0) runCatching { focusRequester.requestFocus() }
    }

    val interactive = visible && view.isInteractive() && view !is EditText
    val clip = view.getClipToOutline() || view is androidx.cardview.widget.CardView

    var m: Modifier = Modifier.graphicsLayer {
        view.renderVersion.intValue
        alpha = if (view.getVisibility() == View.VISIBLE) view.getAlpha() else 0f
        translationX = view.getTranslationX()
        translationY = view.getTranslationY()
        rotationZ = view.getRotation()
        scaleX = view.getScaleX()
        scaleY = view.getScaleY()
        val elevation = view.getElevation() + view.getTranslationZ() +
            ((view as? androidx.cardview.widget.CardView)?.getCardElevation() ?: 0f)
        if (elevation > 0f) {
            shadowElevation = elevation
            this.shape = outlineShape(view)
        }
        if (clip) {
            this.shape = outlineShape(view)
            this.clip = true
        }
    }

    // Background (custom drawn views paint their own background in View.draw)
    if (!view.hasCustomDraw && view !is androidx.cardview.widget.CardView) {
        m = m.drawBehind {
            view.renderVersion.intValue
            val bg = view.getBackground()
            if (bg != null) drawAndroidDrawable(bg, size.width.toInt(), size.height.toInt())
        }
    }
    // Foreground drawable and hover/pressed highlight
    m = m.drawWithContent {
        drawContent()
        view.renderVersion.intValue
        view.getForeground()?.let { drawAndroidDrawable(it, size.width.toInt(), size.height.toInt()) }
        // Like a mouse on Android: only a ripple (selectable) background or a button shows hover/press
        if (interactive && view.isEnabled() && !view.hasCustomDraw && (view !is CompoundButton || view is com.google.android.material.chip.Chip) && view.showsTouchFeedback()) {
            val alpha = when {
                view.isPressed() -> 0.14f
                hovered -> 0.08f
                else -> 0f
            }
            if (alpha > 0f) {
                val color = Color(ThemeBridge.textColorPrimary).copy(alpha = alpha)
                when (val outline = outlineShape(view).createOutline(size, layoutDirection, this)) {
                    is androidx.compose.ui.graphics.Outline.Rounded -> drawRoundRect(
                        color, cornerRadius = CornerRadius(outline.roundRect.topLeftCornerRadius.x, outline.roundRect.topLeftCornerRadius.y)
                    )
                    else -> drawRect(color)
                }
            }
        }
    }

    if (view is androidx.media3.ui.PlayerView) m = m.then(desktopPlayerModifier(view))
    if (interactive) {
        m = m.hoverable(interaction).pointerInput(view) { detectAndroidTouches(view) }
    }
    if (visible && (view.isFocusable() || view.isClickable()) && view !is EditText) {
        // touch mode: like Android, only focusableInTouchMode views take focus while using the mouse
        val canFocus = !com.lagradost.desktop.runtime.TouchMode.inTouchMode || view.isFocusableInTouchMode()
        m = m.focusRequester(focusRequester)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged { view.dispatchFocusChanged(it.isFocused) }
            .onKeyEvent { event -> dispatchKey(view, event) }
            .focusable(view.isEnabled())
    } else if (view is ViewGroup) {
        m = m.focusRequester(focusRequester)
    }
    return m
}

/** Android like touch dispatch: DOWN/MOVE/UP to the view, long press after the platform timeout */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectAndroidTouches(view: View) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
        val downTime = System.currentTimeMillis()
        val awtButton = currentEvent.awtEventOrNull?.button ?: 0
        val secondary = currentEvent.button == PointerButton.Secondary ||
            currentEvent.buttons.isSecondaryPressed ||
            awtButton == java.awt.event.MouseEvent.BUTTON3
        if (secondary) {
            // Right click is the desktop long press. The view under the pointer is often a
            // label inside the row that owns the long-click listener.
            var target: View? = view
            while (target != null && !(target.isLongClickable() && target.isEnabled())) {
                target = target.getParent() as? View
            }
            if (target != null) {
                down.consume()
                target.performLongClick()
            }
            return@awaitEachGesture
        }
        fun event(action: Int, x: Float, y: Float) =
            MotionEvent.obtain(downTime, System.currentTimeMillis(), action, x, y, 0)

        val handled = view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, down.position.x, down.position.y))
        if (!handled) return@awaitEachGesture
        down.consume()
        var longPressed = false
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        var last = down.position
        var finished = false
        while (!finished) {
            val elapsed = System.currentTimeMillis() - downTime
            val remaining = longPressTimeout - elapsed
            val pointerEvent = if (!longPressed && view.isLongClickable() && remaining > 0) {
                withTimeoutOrNull(remaining) { awaitPointerEvent() }
            } else awaitPointerEvent()
            if (pointerEvent == null) {
                // long press timeout
                longPressed = view.performLongClick()
                if (longPressed) view.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, last.x, last.y))
                continue
            }
            val change = pointerEvent.changes.firstOrNull { it.id == down.id } ?: pointerEvent.changes.first()
            last = change.position
            when {
                !change.pressed -> {
                    change.consume()
                    if (!longPressed) {
                        val inside = change.position.x >= 0 && change.position.y >= 0 &&
                            change.position.x <= size.width && change.position.y <= size.height
                        view.dispatchTouchEvent(event(if (inside) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL, change.position.x, change.position.y))
                    }
                    finished = true
                }
                pointerEvent.type == PointerEventType.Move -> {
                    if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero && !longPressed) {
                        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, change.position.x, change.position.y))
                    }
                }
                change.isConsumed -> {
                    if (!longPressed) view.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, change.position.x, change.position.y))
                    finished = true
                }
            }
        }
    }
}

/** Compose key -> Android KeyEvent for the view (Enter/Space click like DPAD_CENTER) */
internal fun dispatchKey(view: View, event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    val e = androidKeyEvent(event) ?: return false
    return view.dispatchKeyEvent(e)
}

/** Compose key event as an Android KeyEvent ([keyCode] replaces the mapped code), null for keys Android has no code for */
fun androidKeyEvent(event: androidx.compose.ui.input.key.KeyEvent, keyCode: Int? = null): KeyEvent? {
    val code = keyCode ?: androidKeyCode(event.key)
    if (code == KeyEvent.KEYCODE_UNKNOWN) return null
    val action = when (event.type) {
        KeyEventType.KeyDown -> KeyEvent.ACTION_DOWN
        KeyEventType.KeyUp -> KeyEvent.ACTION_UP
        else -> return null
    }
    var meta = 0
    if (event.isShiftPressed) meta = meta or KeyEvent.META_SHIFT_ON
    if (event.isCtrlPressed) meta = meta or KeyEvent.META_CTRL_ON
    if (event.isAltPressed) meta = meta or KeyEvent.META_ALT_ON
    val now = System.currentTimeMillis()
    return KeyEvent(now, now, action, code, 0, meta)
}

internal fun androidKeyCode(key: Key): Int = when (key) {
    Key.Enter -> KeyEvent.KEYCODE_ENTER
    Key.NumPadEnter -> KeyEvent.KEYCODE_NUMPAD_ENTER
    Key.Spacebar -> KeyEvent.KEYCODE_SPACE
    Key.Tab -> KeyEvent.KEYCODE_TAB
    Key.Escape -> KeyEvent.KEYCODE_ESCAPE
    Key.Backspace -> 67 // KEYCODE_DEL
    Key.Delete -> 112 // KEYCODE_FORWARD_DEL
    Key.DirectionUp -> KeyEvent.KEYCODE_DPAD_UP
    Key.DirectionDown -> KeyEvent.KEYCODE_DPAD_DOWN
    Key.DirectionLeft -> KeyEvent.KEYCODE_DPAD_LEFT
    Key.DirectionRight -> KeyEvent.KEYCODE_DPAD_RIGHT
    Key.PageUp -> 92
    Key.PageDown -> 93
    Key.MoveHome -> 122
    Key.MoveEnd -> 123
    Key.MediaPlayPause -> 85
    Key.MediaNext -> 87
    Key.MediaPrevious -> 88
    else -> {
        val k = key.keyCode
        // java.awt.event.KeyEvent codes are packed in the high bits of Compose key codes
        val awt = (k shr 32).toInt()
        when (awt) {
            in java.awt.event.KeyEvent.VK_A..java.awt.event.KeyEvent.VK_Z -> KeyEvent.KEYCODE_A + (awt - java.awt.event.KeyEvent.VK_A)
            in java.awt.event.KeyEvent.VK_0..java.awt.event.KeyEvent.VK_9 -> KeyEvent.KEYCODE_0 + (awt - java.awt.event.KeyEvent.VK_0)
            in java.awt.event.KeyEvent.VK_F1..java.awt.event.KeyEvent.VK_F12 -> KeyEvent.KEYCODE_F1 + (awt - java.awt.event.KeyEvent.VK_F1)
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Plain and custom drawn views

/** A View that is not a known widget: drawn with View.draw on the compat canvas when it draws itself */
@Composable
internal fun PlainViewNode(view: View, modifier: Modifier) {
    val drawModifier = if (view.hasCustomDraw) Modifier.drawBehind {
        view.renderVersion.intValue
        withAndroidCanvas { view.draw(it) }
    } else Modifier
    Layout(content = {}, modifier = modifier.then(drawModifier)) { _, c -> layout(c.maxWidth, c.maxHeight) {} }
}

// ------------------------------------------------------------------------------------------------
// Gravity

/** Offset of [size] in [space] for the horizontal part of [gravity] (START/END are LTR) */
internal fun horizontalOffset(gravity: Int, space: Int, size: Int): Int {
    val free = space - size
    // START (0x800003) and END (0x800005) reduce to LEFT (3) and RIGHT (5) under the 0x7 mask
    return when (gravity and 0x07) {
        android.view.Gravity.CENTER_HORIZONTAL -> free / 2
        android.view.Gravity.RIGHT -> free
        else -> 0
    }
}

internal fun verticalOffset(gravity: Int, space: Int, size: Int): Int {
    val free = space - size
    return when (gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK) {
        android.view.Gravity.CENTER_VERTICAL -> free / 2
        android.view.Gravity.BOTTOM -> free
        else -> 0
    }
}

/** Resolved color of a ColorStateList for a view's current drawable state */
internal fun android.content.res.ColorStateList?.colorFor(view: View, default: Int): Int =
    this?.getColorForState(view.getDrawableState(), this.defaultColor) ?: default

/** Solid color of a simple background drawable, if any */
internal fun Drawable?.solidColor(): Int? = when (this) {
    is ColorDrawable -> getColor()
    else -> null
}

/**
 * Keeps the Android display metrics and configuration equal to the Compose density and the window
 * size (1 android px = 1 compose px; the window is the "screen" extensions size their UI against).
 */
object DisplaySync {
    fun sync(density: Float, fontScale: Float, widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return
        val dm = AndroidRuntime.displayMetrics
        if (dm.density == density && dm.scaledDensity == density * fontScale && dm.widthPixels == widthPx && dm.heightPixels == heightPx) return
        dm.density = density
        dm.scaledDensity = density * fontScale
        dm.densityDpi = (160 * density).toInt()
        dm.xdpi = 160 * density
        dm.ydpi = 160 * density
        dm.widthPixels = widthPx
        dm.heightPixels = heightPx
        val config = android.content.res.Configuration.current()
        config.densityDpi = dm.densityDpi
        config.fontScale = fontScale
        config.screenWidthDp = (widthPx / density).toInt()
        config.screenHeightDp = (heightPx / density).toInt()
        config.smallestScreenWidthDp = minOf(config.screenWidthDp, config.screenHeightDp)
        config.orientation = if (widthPx >= heightPx) android.content.res.Configuration.ORIENTATION_LANDSCAPE
        else android.content.res.Configuration.ORIENTATION_PORTRAIT
    }
}

/** Views whose background or foreground reacts to hover/press (ripples, selectable items) or buttons */
internal fun View.showsTouchFeedback(): Boolean {
    if (this is android.widget.Button) return true
    fun reacts(d: android.graphics.drawable.Drawable?): Boolean = when (d) {
        null -> false
        is android.graphics.drawable.RippleDrawable -> true
        is android.graphics.drawable.StateListDrawable -> true
        is android.graphics.drawable.LayerDrawable -> (0 until d.getNumberOfLayers()).any { reacts(d.getDrawable(it)) }
        is android.graphics.drawable.InsetDrawable -> reacts(d.getDrawable())
        else -> false
    }
    return reacts(getBackground()) || reacts(getForeground())
}

private val blankCursor: androidx.compose.ui.input.pointer.PointerIcon by lazy {
    val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    androidx.compose.ui.input.pointer.PointerIcon(java.awt.Toolkit.getDefaultToolkit().createCustomCursor(image, java.awt.Point(0, 0), "blank"))
}

/** Mouse like a desktop video player: see [DesktopPlayerInput]. Events are observed, not consumed. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun desktopPlayerModifier(view: androidx.media3.ui.PlayerView): Modifier {
    var hideCursor by remember(view) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(view) {
        while (true) {
            kotlinx.coroutines.delay(250)
            hideCursor = view.desktopInput?.isControllerShowing() == false
        }
    }
    return Modifier
        .pointerHoverIcon(if (hideCursor) blankCursor else androidx.compose.ui.input.pointer.PointerIcon.Default)
        .pointerInput(view) {
            var lastMove = 0L
            var lastPress = 0L
            var lastPressPosition = androidx.compose.ui.geometry.Offset.Zero
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val input = view.desktopInput ?: continue
                    val change = event.changes.firstOrNull() ?: continue
                    val now = System.currentTimeMillis()
                    when (event.type) {
                        PointerEventType.Move -> if (now - lastMove > 150) {
                            lastMove = now
                            input.onPointerMoved()
                        }
                        PointerEventType.Scroll -> {
                            val dy = change.scrollDelta.y
                            if (dy != 0f) input.onWheel(if (dy < 0f) 1 else -1)
                        }
                        PointerEventType.Press -> if (event.button == PointerButton.Primary) {
                            val close = (change.position - lastPressPosition).getDistance() < 24f
                            if (now - lastPress < 400 && close) {
                                lastPress = 0L
                                input.onDoubleClick()
                            } else {
                                lastPress = now
                                lastPressPosition = change.position
                            }
                        }
                    }
                }
            }
        }
}
