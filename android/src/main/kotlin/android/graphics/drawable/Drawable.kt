package android.graphics.drawable

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Insets
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import java.io.InputStream
import java.lang.ref.WeakReference

abstract class Drawable {
    interface Callback {
        fun invalidateDrawable(who: Drawable)
        fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long)
        fun unscheduleDrawable(who: Drawable, what: Runnable)
    }

    abstract class ConstantState {
        abstract fun newDrawable(): Drawable
        open fun newDrawable(res: Resources?): Drawable = newDrawable()
        open fun newDrawable(res: Resources?, theme: Resources.Theme?): Drawable = newDrawable(res)
        abstract fun getChangingConfigurations(): Int
        open fun canApplyTheme(): Boolean = false
    }

    private val bounds = Rect()
    private var stateSet: IntArray = IntArray(0)
    private var level = 0
    private var visible = true
    private var callbackRef: WeakReference<Callback>? = null
    private var changingConfigurations = 0
    private var autoMirrored = false
    private var layoutDirection = 0

    /** Tint state shared by subclasses that honor it (color, bitmap, gradient, vector) */
    protected var mTintList: ColorStateList? = null
    protected var mTintMode: PorterDuff.Mode? = PorterDuff.Mode.SRC_IN

    abstract fun draw(canvas: Canvas)
    abstract fun setAlpha(alpha: Int)
    abstract fun setColorFilter(colorFilter: ColorFilter?)
    abstract fun getOpacity(): Int

    open fun getAlpha(): Int = 0xFF

    open fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        setColorFilter(PorterDuffColorFilter(color, mode))
    }

    open fun getColorFilter(): ColorFilter? = null
    open fun clearColorFilter() = setColorFilter(null)

    open fun setTint(tintColor: Int) = setTintList(ColorStateList.valueOf(tintColor))
    open fun setTintList(tint: ColorStateList?) {
        mTintList = tint
        invalidateSelf()
    }

    open fun setTintMode(tintMode: PorterDuff.Mode?) {
        mTintMode = tintMode
        invalidateSelf()
    }

    open fun setTintBlendMode(blendMode: BlendMode?) {
        invalidateSelf()
    }

    /** Effective tint filter for the current state, used by the desktop draw implementations */
    protected fun tintFilter(): ColorFilter? {
        val t = mTintList ?: return null
        val color = t.getColorForState(getState(), t.defaultColor)
        return PorterDuffColorFilter(color, mTintMode ?: PorterDuff.Mode.SRC_IN)
    }

    open fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        if (bounds.left != left || bounds.top != top || bounds.right != right || bounds.bottom != bottom) {
            bounds.set(left, top, right, bottom)
            onBoundsChange(bounds)
        }
    }

    open fun setBounds(bounds: Rect) = setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    fun copyBounds(bounds: Rect) = bounds.set(this.bounds)
    fun copyBounds(): Rect = Rect(bounds)
    fun getBounds(): Rect = bounds
    open fun getDirtyBounds(): Rect = bounds

    protected open fun onBoundsChange(bounds: Rect) {}

    open fun getIntrinsicWidth(): Int = -1
    open fun getIntrinsicHeight(): Int = -1
    open fun getMinimumWidth(): Int = getIntrinsicWidth().coerceAtLeast(0)
    open fun getMinimumHeight(): Int = getIntrinsicHeight().coerceAtLeast(0)
    open fun getPadding(padding: Rect): Boolean {
        padding.set(0, 0, 0, 0)
        return false
    }

    open fun getOpticalInsets(): Insets = Insets.NONE

    open fun mutate(): Drawable = this
    open fun getConstantState(): ConstantState? = null
    open fun getChangingConfigurations(): Int = changingConfigurations
    open fun setChangingConfigurations(configs: Int) {
        changingConfigurations = configs
    }

    open fun isStateful(): Boolean = mTintList?.isStateful == true
    open fun hasFocusStateSpecified(): Boolean = false
    open fun setState(stateSet: IntArray): Boolean {
        if (!this.stateSet.contentEquals(stateSet)) {
            this.stateSet = stateSet
            return onStateChange(stateSet)
        }
        return false
    }

    open fun getState(): IntArray = stateSet
    open fun jumpToCurrentState() {}
    open fun getCurrent(): Drawable = this
    protected open fun onStateChange(state: IntArray): Boolean = false

    fun setLevel(level: Int): Boolean {
        if (this.level != level) {
            this.level = level
            return onLevelChange(level)
        }
        return false
    }

    fun getLevel(): Int = level
    protected open fun onLevelChange(level: Int): Boolean = false

    open fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = this.visible != visible
        this.visible = visible
        if (changed) invalidateSelf()
        return changed
    }

    fun isVisible(): Boolean = visible

    fun setCallback(cb: Callback?) {
        callbackRef = cb?.let { WeakReference(it) }
    }

    fun getCallback(): Callback? = callbackRef?.get()

    open fun invalidateSelf() {
        getCallback()?.invalidateDrawable(this)
    }

    open fun scheduleSelf(what: Runnable, `when`: Long) {
        getCallback()?.scheduleDrawable(this, what, `when`)
    }

    open fun unscheduleSelf(what: Runnable) {
        getCallback()?.unscheduleDrawable(this, what)
    }

    open fun setDither(dither: Boolean) {}
    open fun setFilterBitmap(filter: Boolean) {}
    open fun isFilterBitmap(): Boolean = false
    open fun setAutoMirrored(mirrored: Boolean) {
        autoMirrored = mirrored
    }

    open fun isAutoMirrored(): Boolean = autoMirrored
    open fun setLayoutDirection(layoutDirection: Int): Boolean {
        this.layoutDirection = layoutDirection
        return false
    }

    open fun getLayoutDirection(): Int = layoutDirection
    open fun setHotspot(x: Float, y: Float) {}
    open fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) {}
    open fun canApplyTheme(): Boolean = false
    open fun applyTheme(t: Resources.Theme) {}
    open fun isProjected(): Boolean = false

    companion object {
        @JvmStatic
        fun resolveOpacity(op1: Int, op2: Int): Int {
            if (op1 == op2) return op1
            if (op1 == PixelFormat.UNKNOWN || op2 == PixelFormat.UNKNOWN) return PixelFormat.UNKNOWN
            if (op1 == PixelFormat.TRANSLUCENT || op2 == PixelFormat.TRANSLUCENT) return PixelFormat.TRANSLUCENT
            if (op1 == PixelFormat.TRANSPARENT || op2 == PixelFormat.TRANSPARENT) return PixelFormat.TRANSPARENT
            return PixelFormat.OPAQUE
        }

        @JvmStatic
        fun createFromStream(`is`: InputStream?, srcName: String?): Drawable? {
            val bmp = BitmapFactory.decodeStream(`is`) ?: return null
            return BitmapDrawable(null, bmp)
        }

        @JvmStatic
        fun createFromPath(pathName: String?): Drawable? {
            val bmp = BitmapFactory.decodeFile(pathName) ?: return null
            return BitmapDrawable(null, bmp)
        }

        @JvmStatic
        fun createFromResourceStream(res: Resources?, value: android.util.TypedValue?, `is`: InputStream?, srcName: String?): Drawable? =
            createFromStream(`is`, srcName)

        /** Desktop helper: rasterize any drawable */
        @JvmStatic
        fun toBitmap(d: Drawable, width: Int, height: Int): Bitmap {
            val w = if (width > 0) width else d.getIntrinsicWidth().takeIf { it > 0 } ?: 1
            val h = if (height > 0) height else d.getIntrinsicHeight().takeIf { it > 0 } ?: 1
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val old = d.copyBounds()
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(bmp))
            d.setBounds(old)
            return bmp
        }

        @JvmStatic
        fun blendFilter(color: Int, mode: BlendMode): ColorFilter = BlendModeColorFilter(color, mode)
    }
}
