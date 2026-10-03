package com.google.android.material.imageview

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.lagradost.desktop.runtime.ui.ViewAttributes

/**
 * ImageView clipped to a Material shape. cornerSize in the shapeAppearance / shapeAppearanceOverlay
 * style is either a dimension or a percent of the view height (50% is a circle on a square).
 */
open class ShapeableImageView : AppCompatImageView {
    /** Fraction of the height, or < 0 when [cornerPx] is an absolute radius. Order: topStart, topEnd, bottomEnd, bottomStart. */
    private val cornerFraction = FloatArray(4) { -1f }
    private val cornerPx = FloatArray(4)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        applyShape(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        applyShape(attrs)
    }

    private fun applyShape(attrs: AttributeSet?) {
        if (attrs == null) return
        val element = ViewAttributes.reader(this, attrs)
        val base = element.resourceId("shapeAppearance")
        val overlay = element.resourceId("shapeAppearanceOverlay")
        if (base != 0) readCorners(ViewAttributes.styleReader(this, base))
        if (overlay != 0) readCorners(ViewAttributes.styleReader(this, overlay))
        readCorners(ViewAttributes.reader(this, attrs))
        if (hasCornerShape()) setClipToOutline(true)
    }

    private fun readCorners(r: ViewAttributes.Reader) {
        cornerOf(r, "cornerSize")?.let { all ->
            for (i in 0 until 4) setCorner(i, all)
        }
        cornerOf(r, "cornerSizeTopLeft")?.let { setCorner(0, it) }
        cornerOf(r, "cornerSizeTopStart")?.let { setCorner(0, it) }
        cornerOf(r, "cornerSizeTopRight")?.let { setCorner(1, it) }
        cornerOf(r, "cornerSizeTopEnd")?.let { setCorner(1, it) }
        cornerOf(r, "cornerSizeBottomRight")?.let { setCorner(2, it) }
        cornerOf(r, "cornerSizeBottomEnd")?.let { setCorner(2, it) }
        cornerOf(r, "cornerSizeBottomLeft")?.let { setCorner(3, it) }
        cornerOf(r, "cornerSizeBottomStart")?.let { setCorner(3, it) }
    }

    private fun setCorner(index: Int, corner: Pair<Float, Float>) {
        cornerFraction[index] = corner.first
        cornerPx[index] = corner.second
    }

    /** fraction (>= 0) or px (fraction < 0). "50%" is half the view height, which circles a square. */
    private fun cornerOf(r: ViewAttributes.Reader, name: String): Pair<Float, Float>? {
        r.dim(name)?.let { return -1f to it.toFloat() }
        val text = r.text(name)?.toString()?.trim() ?: return null
        if (text.endsWith("%")) {
            val percent = text.removeSuffix("%").trim().toFloatOrNull() ?: return null
            return (percent / 100f) to 0f
        }
        return null
    }

    fun hasCornerShape(): Boolean = cornerFraction.any { it >= 0f } || cornerPx.any { it > 0f }

    /** Radius in px for corner [index] inside a shape of [heightPx]. */
    fun cornerRadius(index: Int, heightPx: Float): Float {
        val fraction = cornerFraction.getOrElse(index) { -1f }
        return if (fraction >= 0f) fraction * heightPx else cornerPx.getOrElse(index) { 0f }
    }
}
