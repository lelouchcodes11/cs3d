@file:JvmName("DrawablesKt")

package android.graphics.drawable

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import java.io.InputStream

open class ColorDrawable : Drawable {
    private var baseColor: Int = 0
    private var useColor: Int = 0
    private var colorFilter: ColorFilter? = null
    private var alpha = 255

    constructor() : this(0)
    constructor(color: Int) {
        setColor(color)
    }

    open fun getColor(): Int = useColor
    open fun setColor(color: Int) {
        baseColor = color
        updateColor()
    }

    private fun updateColor() {
        val baseAlpha = baseColor ushr 24
        val useAlpha = baseAlpha * (alpha + (alpha shr 7)) shr 8
        val newColor = (baseColor shl 8 ushr 8) or (useAlpha shl 24)
        if (newColor != useColor) {
            useColor = newColor
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        if (useColor ushr 24 == 0 && mTintList == null) return
        val p = Paint()
        p.setColor(useColor)
        p.setColorFilter(colorFilter ?: tintFilter())
        canvas.drawRect(getBounds(), p)
    }

    override fun getAlpha(): Int = alpha
    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        updateColor()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getColorFilter(): ColorFilter? = colorFilter

    override fun getOpacity(): Int = when (useColor ushr 24) {
        255 -> PixelFormat.OPAQUE
        0 -> PixelFormat.TRANSPARENT
        else -> PixelFormat.TRANSLUCENT
    }

    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable(): Drawable = ColorDrawable(baseColor)
        override fun getChangingConfigurations(): Int = 0
    }
}

open class GradientDrawable : Drawable {
    enum class Orientation { TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR }

    companion object {
        const val RECTANGLE = 0
        const val OVAL = 1
        const val LINE = 2
        const val RING = 3
        const val LINEAR_GRADIENT = 0
        const val RADIAL_GRADIENT = 1
        const val SWEEP_GRADIENT = 2
    }

    private var shape = RECTANGLE
    private var orientation = Orientation.TOP_BOTTOM
    private var colors: IntArray? = null
    private var solidColors: ColorStateList? = null
    private var cornerRadius = 0f
    private var cornerRadii: FloatArray? = null
    private var strokeWidth = -1
    private var strokeColors: ColorStateList? = null
    private var strokeDashWidth = 0f
    private var strokeDashGap = 0f
    private var gradientType = LINEAR_GRADIENT
    private var gradientRadius = 0.5f
    private var gradientCenterX = 0.5f
    private var gradientCenterY = 0.5f
    private var alpha = 255
    private var colorFilter: ColorFilter? = null
    private var width = -1
    private var height = -1
    private var useLevel = false
    private var padding: Rect? = null

    constructor()
    constructor(orientation: Orientation?, colors: IntArray?) {
        this.orientation = orientation ?: Orientation.TOP_BOTTOM
        this.colors = colors
    }

    open fun setShape(shape: Int) {
        this.shape = shape
        invalidateSelf()
    }

    open fun getShape(): Int = shape
    open fun setOrientation(orientation: Orientation?) {
        this.orientation = orientation ?: Orientation.TOP_BOTTOM
        invalidateSelf()
    }

    open fun getOrientation(): Orientation = orientation
    open fun setColors(colors: IntArray?) {
        this.colors = colors
        solidColors = null
        invalidateSelf()
    }

    open fun setColors(colors: IntArray?, offsets: FloatArray?) = setColors(colors)
    open fun getColors(): IntArray? = colors?.clone()
    open fun setColor(argb: Int) {
        solidColors = ColorStateList.valueOf(argb)
        colors = null
        invalidateSelf()
    }

    open fun setColor(colorStateList: ColorStateList?) {
        solidColors = colorStateList
        colors = null
        invalidateSelf()
    }

    open fun getColor(): ColorStateList? = solidColors
    open fun setCornerRadius(radius: Float) {
        cornerRadius = radius
        cornerRadii = null
        invalidateSelf()
    }

    open fun getCornerRadius(): Float = cornerRadius
    open fun setCornerRadii(radii: FloatArray?) {
        cornerRadii = radii
        invalidateSelf()
    }

    open fun getCornerRadii(): FloatArray? = cornerRadii?.clone()
    open fun setStroke(width: Int, color: Int) = setStroke(width, color, 0f, 0f)
    open fun setStroke(width: Int, colorStateList: ColorStateList?) = setStroke(width, colorStateList, 0f, 0f)
    open fun setStroke(width: Int, color: Int, dashWidth: Float, dashGap: Float) =
        setStroke(width, ColorStateList.valueOf(color), dashWidth, dashGap)

    open fun setStroke(width: Int, colorStateList: ColorStateList?, dashWidth: Float, dashGap: Float) {
        strokeWidth = width
        strokeColors = colorStateList
        strokeDashWidth = dashWidth
        strokeDashGap = dashGap
        invalidateSelf()
    }

    open fun setGradientType(gradient: Int) {
        gradientType = gradient
        invalidateSelf()
    }

    open fun getGradientType(): Int = gradientType
    open fun setGradientRadius(gradientRadius: Float) {
        this.gradientRadius = gradientRadius
        invalidateSelf()
    }

    open fun getGradientRadius(): Float = gradientRadius
    open fun setGradientCenter(x: Float, y: Float) {
        gradientCenterX = x
        gradientCenterY = y
        invalidateSelf()
    }

    open fun setUseLevel(useLevel: Boolean) {
        this.useLevel = useLevel
    }

    open fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    open fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        padding = Rect(left, top, right, bottom)
    }

    override fun getPadding(padding: Rect): Boolean {
        val p = this.padding ?: return super.getPadding(padding)
        padding.set(p)
        return true
    }

    override fun getIntrinsicWidth(): Int = width
    override fun getIntrinsicHeight(): Int = height
    override fun getAlpha(): Int = alpha
    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getColorFilter(): ColorFilter? = colorFilter
    override fun isStateful(): Boolean = solidColors?.isStateful == true || strokeColors?.isStateful == true || super.isStateful()
    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun modulateAlpha(color: Int): Int {
        val a = (color ushr 24) * (alpha + (alpha shr 7)) shr 8
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun buildShape(r: RectF): Path {
        val path = Path()
        when (shape) {
            OVAL -> path.addOval(r, Path.Direction.CW)
            LINE -> {
                val y = r.centerY()
                path.moveTo(r.left, y)
                path.lineTo(r.right, y)
            }
            RING -> {
                val cx = r.centerX()
                val cy = r.centerY()
                val outer = kotlin.math.min(r.width(), r.height()) / 2
                val inner = outer / 3
                path.addCircle(cx, cy, outer, Path.Direction.CW)
                path.addCircle(cx, cy, inner, Path.Direction.CCW)
                path.setFillType(Path.FillType.EVEN_ODD)
            }
            else -> {
                val radii = cornerRadii
                if (radii != null) path.addRoundRect(r, radii, Path.Direction.CW)
                else if (cornerRadius > 0) path.addRoundRect(r, cornerRadius, cornerRadius, Path.Direction.CW)
                else path.addRect(r, Path.Direction.CW)
            }
        }
        return path
    }

    override fun draw(canvas: Canvas) {
        val b = getBounds()
        if (b.isEmpty) return
        val half = if (strokeWidth > 0) strokeWidth / 2f else 0f
        val rect = RectF(b.left + half, b.top + half, b.right - half, b.bottom - half)
        val path = buildShape(rect)
        val filter = colorFilter ?: tintFilter()

        if (shape != LINE) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.setStyle(Paint.Style.FILL)
            fill.setColorFilter(filter)
            val gradientColors = colors
            if (gradientColors != null && gradientColors.size >= 2) {
                val c = gradientColors.map { modulateAlpha(it) }.toIntArray()
                fill.setShader(gradientShader(rect, c))
                fill.setColor(Color.WHITE)
                canvas.drawPath(path, fill)
            } else {
                val solid = solidColors ?: gradientColors?.firstOrNull()?.let { ColorStateList.valueOf(it) }
                if (solid != null) {
                    fill.setColor(modulateAlpha(solid.getColorForState(getState(), solid.defaultColor)))
                    canvas.drawPath(path, fill)
                }
            }
        }
        val sc = strokeColors
        if (strokeWidth > 0 && sc != null) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
            stroke.setStyle(Paint.Style.STROKE)
            stroke.setStrokeWidth(strokeWidth.toFloat())
            stroke.setColor(modulateAlpha(sc.getColorForState(getState(), sc.defaultColor)))
            stroke.setColorFilter(filter)
            if (strokeDashWidth > 0) {
                stroke.skia.pathEffect = org.jetbrains.skia.PathEffect.makeDash(floatArrayOf(strokeDashWidth, strokeDashGap), 0f)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun gradientShader(r: RectF, c: IntArray): Shader = when (gradientType) {
        RADIAL_GRADIENT -> RadialGradient(
            r.left + r.width() * gradientCenterX, r.top + r.height() * gradientCenterY,
            if (gradientRadius <= 1f) gradientRadius * kotlin.math.min(r.width(), r.height()) else gradientRadius,
            c, null, Shader.TileMode.CLAMP
        )
        SWEEP_GRADIENT -> SweepGradient(r.centerX(), r.centerY(), c, null)
        else -> {
            val (x0, y0, x1, y1) = when (orientation) {
                Orientation.TOP_BOTTOM -> floatArrayOf(r.left, r.top, r.left, r.bottom)
                Orientation.TR_BL -> floatArrayOf(r.right, r.top, r.left, r.bottom)
                Orientation.RIGHT_LEFT -> floatArrayOf(r.right, r.top, r.left, r.top)
                Orientation.BR_TL -> floatArrayOf(r.right, r.bottom, r.left, r.top)
                Orientation.BOTTOM_TOP -> floatArrayOf(r.left, r.bottom, r.left, r.top)
                Orientation.BL_TR -> floatArrayOf(r.left, r.bottom, r.right, r.top)
                Orientation.LEFT_RIGHT -> floatArrayOf(r.left, r.top, r.right, r.top)
                Orientation.TL_BR -> floatArrayOf(r.left, r.top, r.right, r.bottom)
            }
            LinearGradient(x0, y0, x1, y1, c, null, Shader.TileMode.CLAMP)
        }
    }

    override fun getConstantState(): ConstantState {
        val snapshot = this
        return object : ConstantState() {
            override fun newDrawable(): Drawable = GradientDrawable(snapshot.orientation, snapshot.colors?.clone()).also {
                it.shape = snapshot.shape
                it.solidColors = snapshot.solidColors
                it.cornerRadius = snapshot.cornerRadius
                it.cornerRadii = snapshot.cornerRadii?.clone()
                it.strokeWidth = snapshot.strokeWidth
                it.strokeColors = snapshot.strokeColors
                it.strokeDashWidth = snapshot.strokeDashWidth
                it.strokeDashGap = snapshot.strokeDashGap
                it.gradientType = snapshot.gradientType
                it.gradientRadius = snapshot.gradientRadius
                it.alpha = snapshot.alpha
                it.width = snapshot.width
                it.height = snapshot.height
            }

            override fun getChangingConfigurations(): Int = 0
        }
    }

    override fun mutate(): Drawable = this
}

open class BitmapDrawable : Drawable {
    private var mBitmap: Bitmap? = null
    open val bitmap: Bitmap? get() = mBitmap
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private var gravity = android.view.Gravity.FILL
    private var tileX: Shader.TileMode? = null
    private var tileY: Shader.TileMode? = null

    @Deprecated("")
    constructor()

    @Deprecated("")
    constructor(bitmap: Bitmap?) {
        this.mBitmap = bitmap
    }

    constructor(res: Resources?) {}
    constructor(res: Resources?, bitmap: Bitmap?) {
        this.mBitmap = bitmap
    }

    constructor(res: Resources?, filepath: String?) {
        mBitmap = BitmapFactory.decodeFile(filepath)
    }

    constructor(res: Resources?, `is`: InputStream?) {
        mBitmap = BitmapFactory.decodeStream(`is`)
    }

    fun getPaint(): Paint = paint
    open fun setBitmap(bitmap: Bitmap?) {
        this.mBitmap = bitmap
        invalidateSelf()
    }

    open fun getGravity(): Int = gravity
    open fun setGravity(gravity: Int) {
        this.gravity = gravity
        invalidateSelf()
    }

    open fun setAntiAlias(aa: Boolean) = paint.setAntiAlias(aa)
    override fun setFilterBitmap(filter: Boolean) = paint.setFilterBitmap(filter)
    override fun isFilterBitmap(): Boolean = paint.isFilterBitmap()
    open fun setTileModeXY(xmode: Shader.TileMode?, ymode: Shader.TileMode?) {
        tileX = xmode
        tileY = ymode
        invalidateSelf()
    }

    open fun setTargetDensity(density: Int) {}
    open fun setTargetDensity(metrics: android.util.DisplayMetrics?) {}
    open fun setTargetDensity(canvas: Canvas?) {}

    override fun draw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val b = getBounds()
        if (b.isEmpty) return
        paint.setColorFilter(paint.getColorFilter() ?: tintFilter())
        if (tileX != null || tileY != null) {
            val p = Paint(paint)
            p.setShader(android.graphics.BitmapShader(bmp, tileX ?: Shader.TileMode.CLAMP, tileY ?: Shader.TileMode.CLAMP))
            canvas.drawRect(b, p)
        } else {
            canvas.drawBitmap(bmp, null, b, paint)
        }
    }

    override fun getAlpha(): Int = paint.getAlpha()
    override fun setAlpha(alpha: Int) {
        paint.setAlpha(alpha)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.setColorFilter(colorFilter)
        invalidateSelf()
    }

    override fun getColorFilter(): ColorFilter? = paint.getColorFilter()
    override fun getIntrinsicWidth(): Int = bitmap?.getWidth() ?: -1
    override fun getIntrinsicHeight(): Int = bitmap?.getHeight() ?: -1
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState {
        val bmp = bitmap
        return object : ConstantState() {
            override fun newDrawable(): Drawable = BitmapDrawable(null, bmp)
            override fun getChangingConfigurations(): Int = 0
        }
    }
}

open class LayerDrawable(layers: Array<Drawable?>) : Drawable(), Drawable.Callback {
    private class Layer(var drawable: Drawable?, var id: Int = -1, var insetL: Int = 0, var insetT: Int = 0, var insetR: Int = 0, var insetB: Int = 0, var gravity: Int = 0)

    private val layerList = ArrayList<Layer>()
    private var alpha = 255

    init {
        for (d in layers) addLayer(d)
    }

    open fun addLayer(dr: Drawable?): Int {
        dr?.setCallback(this)
        layerList.add(Layer(dr))
        onBoundsChange(getBounds())
        return layerList.size - 1
    }

    /** PADDING_MODE_NEST: the paddings of the layers add up, like LayerDrawable.getPadding */
    override fun getPadding(padding: Rect): Boolean {
        padding.set(0, 0, 0, 0)
        val tmp = Rect()
        var any = false
        for (layer in layerList) {
            val d = layer.drawable ?: continue
            tmp.set(0, 0, 0, 0)
            if (d.getPadding(tmp)) any = true
            padding.left += tmp.left + layer.insetL
            padding.top += tmp.top + layer.insetT
            padding.right += tmp.right + layer.insetR
            padding.bottom += tmp.bottom + layer.insetB
        }
        return any || padding.left != 0 || padding.top != 0 || padding.right != 0 || padding.bottom != 0
    }

    open fun getNumberOfLayers(): Int = layerList.size
    open fun getDrawable(index: Int): Drawable? = layerList.getOrNull(index)?.drawable
    open fun setDrawable(index: Int, drawable: Drawable?) {
        layerList.getOrNull(index)?.drawable = drawable
        drawable?.setCallback(this)
        invalidateSelf()
    }

    open fun findDrawableByLayerId(id: Int): Drawable? = layerList.firstOrNull { it.id == id }?.drawable
    open fun setId(index: Int, id: Int) {
        layerList.getOrNull(index)?.id = id
    }

    open fun getId(index: Int): Int = layerList.getOrNull(index)?.id ?: -1
    open fun findIndexByLayerId(id: Int): Int = layerList.indexOfFirst { it.id == id }
    open fun setDrawableByLayerId(id: Int, drawable: Drawable?): Boolean {
        val idx = findIndexByLayerId(id)
        if (idx < 0) return false
        setDrawable(idx, drawable)
        return true
    }

    open fun setLayerInset(index: Int, l: Int, t: Int, r: Int, b: Int) {
        layerList.getOrNull(index)?.apply { insetL = l; insetT = t; insetR = r; insetB = b }
        onBoundsChange(getBounds())
    }

    open fun setLayerGravity(index: Int, gravity: Int) {
        layerList.getOrNull(index)?.gravity = gravity
    }

    open fun setLayerSize(index: Int, w: Int, h: Int) {}

    override fun onBoundsChange(bounds: Rect) {
        for (l in layerList) {
            l.drawable?.setBounds(bounds.left + l.insetL, bounds.top + l.insetT, bounds.right - l.insetR, bounds.bottom - l.insetB)
        }
    }

    override fun draw(canvas: Canvas) {
        for (l in layerList) l.drawable?.draw(canvas)
    }

    override fun setState(stateSet: IntArray): Boolean {
        var changed = super.setState(stateSet)
        for (l in layerList) if (l.drawable?.setState(stateSet) == true) changed = true
        return changed
    }

    override fun isStateful(): Boolean = layerList.any { it.drawable?.isStateful() == true }
    override fun getAlpha(): Int = alpha
    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        for (l in layerList) l.drawable?.setAlpha(alpha)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        for (l in layerList) l.drawable?.setColorFilter(colorFilter)
    }

    override fun getIntrinsicWidth(): Int = layerList.maxOfOrNull { (it.drawable?.getIntrinsicWidth() ?: -1) + it.insetL + it.insetR } ?: -1
    override fun getIntrinsicHeight(): Int = layerList.maxOfOrNull { (it.drawable?.getIntrinsicHeight() ?: -1) + it.insetT + it.insetB } ?: -1
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}

open class StateListDrawable : Drawable(), Drawable.Callback {
    private val specs = ArrayList<IntArray>()
    private val drawables = ArrayList<Drawable?>()
    private var current: Drawable? = null
    private var alpha = 255
    private var colorFilter: ColorFilter? = null

    open fun addState(stateSet: IntArray, drawable: Drawable?) {
        specs.add(stateSet)
        drawables.add(drawable)
        drawable?.setCallback(this)
        onStateChange(getState())
    }

    /** Constant padding (DrawableContainer default): the largest padding of any state */
    override fun getPadding(padding: Rect): Boolean {
        padding.set(0, 0, 0, 0)
        val tmp = Rect()
        var any = false
        for (d in drawables) {
            if (d == null) continue
            tmp.set(0, 0, 0, 0)
            if (d.getPadding(tmp)) {
                any = true
                padding.left = maxOf(padding.left, tmp.left)
                padding.top = maxOf(padding.top, tmp.top)
                padding.right = maxOf(padding.right, tmp.right)
                padding.bottom = maxOf(padding.bottom, tmp.bottom)
            }
        }
        return any
    }

    open fun getStateCount(): Int = specs.size
    open fun getStateSet(index: Int): IntArray = specs[index]
    open fun getStateDrawable(index: Int): Drawable? = drawables[index]
    open fun findStateDrawableIndex(stateSet: IntArray): Int = specs.indexOfFirst { matches(it, stateSet) }

    private fun matches(spec: IntArray, state: IntArray): Boolean {
        for (s in spec) {
            if (s == 0) return true
            val want = if (s > 0) s else -s
            val found = state.contains(want)
            if ((s > 0) != found) return false
        }
        return true
    }

    override fun onStateChange(state: IntArray): Boolean {
        val idx = findStateDrawableIndex(state)
        val next = if (idx >= 0) drawables[idx] else null
        if (next !== current) {
            current = next
            next?.setBounds(getBounds())
            next?.setAlpha(alpha)
            if (colorFilter != null) next?.setColorFilter(colorFilter)
            invalidateSelf()
            return true
        }
        return false
    }

    override fun isStateful(): Boolean = true
    override fun getCurrent(): Drawable = current ?: this
    override fun onBoundsChange(bounds: Rect) {
        current?.setBounds(bounds)
    }

    override fun draw(canvas: Canvas) {
        current?.draw(canvas)
    }

    override fun getAlpha(): Int = alpha
    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        current?.setAlpha(alpha)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        current?.setColorFilter(colorFilter)
    }

    override fun getIntrinsicWidth(): Int = current?.getIntrinsicWidth() ?: -1
    override fun getIntrinsicHeight(): Int = current?.getIntrinsicHeight() ?: -1
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}

open class InsetDrawable(private val inner: Drawable?, private val insetLeft: Int, private val insetTop: Int, private val insetRight: Int, private val insetBottom: Int) :
    Drawable(), Drawable.Callback {
    constructor(drawable: Drawable?, inset: Int) : this(drawable, inset, inset, inset, inset)
    constructor(drawable: Drawable?, inset: Float) : this(drawable, 0)

    init {
        inner?.setCallback(this)
    }

    override fun getPadding(padding: Rect): Boolean {
        val any = inner?.getPadding(padding) ?: false.also { padding.set(0, 0, 0, 0) }
        padding.left += insetLeft
        padding.top += insetTop
        padding.right += insetRight
        padding.bottom += insetBottom
        return any || insetLeft != 0 || insetTop != 0 || insetRight != 0 || insetBottom != 0
    }

    open fun getDrawable(): Drawable? = inner
    override fun onBoundsChange(bounds: Rect) {
        inner?.setBounds(bounds.left + insetLeft, bounds.top + insetTop, bounds.right - insetRight, bounds.bottom - insetBottom)
    }

    override fun draw(canvas: Canvas) {
        inner?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        inner?.setAlpha(alpha)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner?.setColorFilter(colorFilter)
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = inner?.getIntrinsicWidth()?.takeIf { it >= 0 }?.plus(insetLeft + insetRight) ?: -1
    override fun getIntrinsicHeight(): Int = inner?.getIntrinsicHeight()?.takeIf { it >= 0 }?.plus(insetTop + insetBottom) ?: -1
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}

open class RippleDrawable(color: ColorStateList, private val content: Drawable?, private val mask: Drawable?) : LayerDrawable(arrayOf(content)) {
    private var rippleColor: ColorStateList = color
    open fun setColor(color: ColorStateList) {
        rippleColor = color
    }

    open fun setRadius(radius: Int) {}
}

open class ShapeDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var intrinsicWidth = -1
    private var intrinsicHeight = -1

    fun getPaint(): Paint = paint
    open fun setIntrinsicWidth(width: Int) {
        intrinsicWidth = width
    }

    open fun setIntrinsicHeight(height: Int) {
        intrinsicHeight = height
    }

    override fun getIntrinsicWidth(): Int = intrinsicWidth
    override fun getIntrinsicHeight(): Int = intrinsicHeight
    override fun draw(canvas: Canvas) {
        canvas.drawRect(getBounds(), paint)
    }

    override fun setAlpha(alpha: Int) = paint.setAlpha(alpha)
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.setColorFilter(colorFilter)
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

open class AnimationDrawable : Drawable() {
    private val frames = ArrayList<Pair<Drawable, Int>>()
    private var currentIndex = 0
    private var oneShot = false

    open fun addFrame(frame: Drawable, duration: Int) {
        frames.add(frame to duration)
    }

    open fun setOneShot(oneShot: Boolean) {
        this.oneShot = oneShot
    }

    open fun start() {}
    open fun stop() {}
    open fun isRunning(): Boolean = false
    open fun getNumberOfFrames(): Int = frames.size
    open fun getFrame(index: Int): Drawable = frames[index].first
    override fun draw(canvas: Canvas) {
        frames.getOrNull(currentIndex)?.first?.apply { setBounds(getBounds()); draw(canvas) }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

interface Animatable {
    fun start()
    fun stop()
    fun isRunning(): Boolean
}

open class AnimatedImageDrawable : Drawable(), Animatable {
    override fun start() {}
    override fun stop() {}
    override fun isRunning(): Boolean = false
    override fun draw(canvas: Canvas) {}
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

