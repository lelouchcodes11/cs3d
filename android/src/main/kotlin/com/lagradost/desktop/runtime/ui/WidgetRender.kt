package com.lagradost.desktop.runtime.ui

import android.graphics.drawable.Drawable
import android.widget.AbsSpinner
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.SeekBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------------------
// ImageView

@Composable
internal fun ImageViewNode(view: ImageView, modifier: Modifier) {
    val drawModifier = Modifier.drawBehind {
        view.renderVersion.intValue
        val d = view.getDrawable() ?: return@drawBehind
        drawImage(view, d, size.width.toInt(), size.height.toInt())
    }
    Layout(content = {}, modifier = modifier.then(drawModifier)) { _, c -> layout(c.maxWidth, c.maxHeight) {} }
}

/** ImageView.configureBounds + onDraw on the compat canvas */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImage(view: ImageView, d: Drawable, width: Int, height: Int) {
    val pl = view.getPaddingLeft()
    val pt = view.getPaddingTop()
    val vw = width - pl - view.getPaddingRight()
    val vh = height - pt - view.getPaddingBottom()
    if (vw <= 0 || vh <= 0) return
    val dw = d.getIntrinsicWidth()
    val dh = d.getIntrinsicHeight()
    view.getImageTintList()?.let { d.setTintList(it) }
    d.setState(view.getDrawableState())
    drawIntoCanvas { canvas ->
        val c = android.graphics.Canvas.wrap(canvas.nativeCanvas, size.width.toInt(), size.height.toInt())
        c.save()
        if (view.getCropToPadding()) c.clipRect(pl, pt, pl + vw, pt + vh) else c.clipRect(0, 0, width, height)
        c.translate(pl.toFloat(), pt.toFloat())
        val type = view.getScaleType()
        try {
            if (dw <= 0 || dh <= 0 || type == ImageView.ScaleType.FIT_XY) {
                d.setBounds(0, 0, vw, vh)
                d.draw(c)
            } else {
                d.setBounds(0, 0, dw, dh)
                var scale: Float
                var dx = 0f
                var dy = 0f
                var sx = 1f
                var sy = 1f
                when (type) {
                    ImageView.ScaleType.CENTER -> {
                        dx = ((vw - dw) * 0.5f).roundToInt().toFloat()
                        dy = ((vh - dh) * 0.5f).roundToInt().toFloat()
                    }
                    ImageView.ScaleType.CENTER_CROP -> {
                        if (dw * vh > vw * dh) {
                            scale = vh.toFloat() / dh
                            dx = (vw - dw * scale) * 0.5f
                        } else {
                            scale = vw.toFloat() / dw
                            dy = (vh - dh * scale) * 0.5f
                        }
                        sx = scale
                        sy = scale
                    }
                    ImageView.ScaleType.CENTER_INSIDE -> {
                        scale = if (dw <= vw && dh <= vh) 1f else min(vw.toFloat() / dw, vh.toFloat() / dh)
                        dx = ((vw - dw * scale) * 0.5f).roundToInt().toFloat()
                        dy = ((vh - dh * scale) * 0.5f).roundToInt().toFloat()
                        sx = scale
                        sy = scale
                    }
                    ImageView.ScaleType.MATRIX -> {}
                    else -> {
                        // FIT_START / FIT_CENTER / FIT_END: Matrix.setRectToRect keeping the aspect ratio
                        scale = min(vw.toFloat() / dw, vh.toFloat() / dh)
                        sx = scale
                        sy = scale
                        val freeX = vw - dw * scale
                        val freeY = vh - dh * scale
                        when (type) {
                            ImageView.ScaleType.FIT_START -> {}
                            ImageView.ScaleType.FIT_END -> {
                                dx = freeX
                                dy = freeY
                            }
                            else -> {
                                dx = freeX / 2
                                dy = freeY / 2
                            }
                        }
                    }
                }
                c.translate(dx, dy)
                c.scale(sx, sy)
                d.draw(c)
            }
        } catch (t: Throwable) {
            android.util.Log.e("ViewRender", "image draw failed", t)
        }
        c.restore()
    }
}

// ------------------------------------------------------------------------------------------------
// ProgressBar / SeekBar / RatingBar

@Composable
internal fun ProgressBarNode(view: ProgressBar, modifier: Modifier) {
    val color = Color(
        (if (view.isIndeterminate()) view.getIndeterminateTintList() else view.getProgressTintList())
            .colorFor(view, ThemeBridge.colorPrimary)
    )
    val track = Color(view.getProgressBackgroundTintList().colorFor(view, (ThemeBridge.colorPrimary and 0x00FFFFFF) or 0x40000000))
    // A progressDrawable (layer-list with a clip/scale "progress" layer) is drawn like Android: level = progress
    val custom = view.getProgressDrawable()
    if (custom != null && !view.isIndeterminate()) {
        val draw = Modifier.drawBehind {
            view.renderVersion.intValue
            val range = max(1, view.getMax() - view.getMin())
            val level = ((view.getProgress() - view.getMin()).toFloat() / range * 10000).toInt().coerceIn(0, 10000)
            val progressLayer = (custom as? android.graphics.drawable.LayerDrawable)
                ?.findDrawableByLayerId(com.lagradost.desktop.runtime.res.FrameworkResources.ID_PROGRESS) ?: custom
            progressLayer.setLevel(level)
            view.getProgressTintList()?.let { progressLayer.setTintList(it) }
            drawAndroidDrawable(custom, size.width.toInt(), size.height.toInt())
        }
        PlacedContent(modifier, rects = { _, w, h -> view.paddedRect(w, h) }) { Box(Modifier.fillMaxSize().then(draw)) }
        return
    }
    val material = view as? com.google.android.material.progressindicator.BaseProgressIndicator
    val density = LocalDensity.current
    fun px(v: Int) = with(density) { v.toDp() }
    PlacedContent(modifier, rects = { _, w, h -> view.paddedRect(w, h) }) {
        if (view.isHorizontalStyle()) {
            val range = max(1, view.getMax() - view.getMin())
            val fraction = (view.getProgress() - view.getMin()).toFloat() / range
            val bar = if (material != null && material.trackThicknessPx > 0) {
                Modifier.fillMaxWidth().height(px(material.trackThicknessPx))
            } else Modifier.fillMaxWidth()
            val gap = material?.let { px(it.trackGapPx) } ?: 0.dp
            Box(contentAlignment = Alignment.Center) {
                if (view.isIndeterminate()) {
                    LinearProgressIndicator(modifier = bar, color = color, trackColor = track, gapSize = gap)
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = bar, color = color, trackColor = track, gapSize = gap, drawStopIndicator = {})
                }
            }
        } else {
            val stroke = material?.trackThicknessPx?.takeIf { it > 0 }?.let(::px) ?: 4.dp
            val inset = material?.indicatorInsetPx?.takeIf { it > 0 }?.let(::px) ?: if (material != null) 0.dp else 4.dp
            val gap = material?.let { px(it.trackGapPx) } ?: 0.dp
            val ring = Modifier.fillMaxSize().padding(inset)
            if (view.isIndeterminate()) {
                CircularProgressIndicator(modifier = ring, color = color, strokeWidth = stroke, trackColor = track, strokeCap = StrokeCap.Round, gapSize = gap)
            } else {
                val range = max(1, view.getMax() - view.getMin())
                CircularProgressIndicator(
                    progress = { (view.getProgress() - view.getMin()).toFloat() / range },
                    modifier = ring, color = color, trackColor = track, strokeWidth = stroke, strokeCap = StrokeCap.Round, gapSize = gap,
                )
            }
        }
    }
}

@Composable
internal fun SeekBarNode(view: SeekBar, modifier: Modifier) {
    var dragging by remember(view) { mutableStateOf(false) }
    val colorInt = view.getProgressTintList().colorFor(view, ThemeBridge.colorPrimary)
    val color = Color(colorInt)
    val thumb = Color(view.getThumbTintList().colorFor(view, colorInt))
    val inactive = Color(view.getProgressBackgroundTintList().colorFor(view, (colorInt and 0x00FFFFFF) or 0x40000000))
    val min = view.getMin()
    val maxV = max(min + 1, view.getMax())
    PlacedContent(modifier, rects = { _, w, h -> view.paddedRect(w, h) }) {
        Box(contentAlignment = Alignment.Center) {
            Slider(
                value = view.getProgress().toFloat().coerceIn(min.toFloat(), maxV.toFloat()),
                onValueChange = { v ->
                    if (!dragging) {
                        dragging = true
                        view.onUserProgress(0, 0)
                    }
                    view.onUserProgress(v.roundToInt(), 1)
                },
                onValueChangeFinished = {
                    if (dragging) {
                        dragging = false
                        view.onUserProgress(0, 2)
                    }
                },
                valueRange = min.toFloat()..maxV.toFloat(),
                enabled = view.isEnabled(),
                colors = SliderDefaults.colors(thumbColor = thumb, activeTrackColor = color, inactiveTrackColor = inactive),
            )
        }
    }
}

@Composable
internal fun RatingBarNode(view: RatingBar, modifier: Modifier) {
    val color = Color(view.getProgressTintList().colorFor(view, ThemeBridge.colorPrimary))
    val stars = view.getNumStars().coerceAtLeast(1)
    val rating = view.getRating()
    PlacedContent(modifier, rects = { _, w, h -> view.paddedRect(w, h) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until stars) {
                val filled = rating >= i + 1 - 0.25f
                BasicText(
                    if (filled) "★" else "☆",
                    style = TextStyle(color = color, fontSize = 22.sp),
                    modifier = if (view.isEnabled()) Modifier.clickable { view.setRating((i + 1).toFloat()) } else Modifier,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Spinner

@Composable
internal fun SpinnerNode(view: AbsSpinner, modifier: Modifier) {
    val adapter = view.getAdapter()
    val version = (adapter as? android.widget.BaseAdapter)?.dataVersion?.intValue ?: 0
    val selected = view.getSelectedItemPosition()
    var expanded by remember(view) { mutableStateOf(false) }
    val cache = remember(view, adapter) { HashMap<String, android.view.View>() }
    val arrowColor = Color(ThemeBridge.textColorSecondary)
    val clickable = if (view.isEnabled()) Modifier.clickable { expanded = true } else Modifier
    PlacedContent(
        modifier.then(clickable),
        rects = { i, w, h ->
            if (i == 0) view.paddedRect(w, h)
            else IntRect(max(0, w - view.getPaddingRight()), 0, w, h)
        },
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            val itemView = view.selectedItemView()
            @Suppress("UNUSED_EXPRESSION") version
            if (itemView != null && selected >= 0) AndroidViewHost(itemView)
        }
        Box(contentAlignment = Alignment.Center) {
            BasicText("▾", style = TextStyle(color = arrowColor, fontSize = 16.sp))
        }
    }
    if (expanded) {
        DropdownMenu(expanded = true, onDismissRequest = { expanded = false }) {
            if (adapter != null) {
                for (i in 0 until adapter.getCount()) {
                    val itemView = remember(adapter, i, version) {
                        adapter.getDropDownView(i, cache["d$i"], view).also { cache["d$i"] = it }
                    }
                    DropdownMenuItem(
                        text = { AndroidViewHost(itemView, Modifier.width(280.dp)) },
                        onClick = {
                            expanded = false
                            view.setSelectionInternal(i, true)
                        },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// WebView (Chromium through JCEF)

@Composable
internal fun WebViewNode(view: android.webkit.WebView, modifier: Modifier) {
    var component by remember(view) { mutableStateOf(view.uiComponent()) }
    LaunchedEffect(view) {
        while (component == null) {
            delay(100)
            component = view.uiComponent()
        }
    }
    PlacedContent(modifier, rects = { _, w, h -> view.paddedRect(w, h) }) {
        val c = component
        if (c != null) {
            SwingPanel(factory = { c }, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(ThemeBridge.colorPrimary), modifier = Modifier.size(36.dp))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// MpvSurfaceView: the latest rendered video frame, drawn at the view size (frames are rendered at it)

@Composable
internal fun MpvSurfaceViewNode(view: MpvSurfaceView, modifier: Modifier) {
    val draw = Modifier.drawBehind {
        view.frameVersion.intValue
        val image = view.frame ?: return@drawBehind
        drawIntoCanvas { c ->
            c.nativeCanvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(size.width, size.height))
        }
    }
    Layout(content = {}, modifier = modifier.then(draw)) { _, c -> layout(c.maxWidth, c.maxHeight) {} }
}

