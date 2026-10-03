package android.graphics.drawable

import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity

/** Base of the level-driven wrappers: forwards everything to the wrapped drawable */
abstract class LevelWrapperDrawable(protected val inner: Drawable?) : Drawable() {
    override fun getIntrinsicWidth(): Int = inner?.getIntrinsicWidth() ?: -1
    override fun getIntrinsicHeight(): Int = inner?.getIntrinsicHeight() ?: -1
    override fun setAlpha(alpha: Int) {
        inner?.setAlpha(alpha)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner?.setColorFilter(colorFilter)
    }

    override fun setTintList(tint: ColorStateList?) {
        inner?.setTintList(tint)
    }

    override fun setTintBlendMode(blendMode: BlendMode?) {
        inner?.setTintBlendMode(blendMode)
    }

    override fun isStateful(): Boolean = inner?.isStateful() == true
    override fun onStateChange(state: IntArray): Boolean = inner?.setState(state) == true
    override fun onLevelChange(level: Int): Boolean {
        inner?.setLevel(level)
        invalidateSelf()
        return true
    }

    open fun getDrawable(): Drawable? = inner

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** ClipDrawable: shows level/10000 of the drawable along the orientation, from the gravity side */
open class ClipDrawable(drawable: Drawable?, private val gravity: Int, private val orientation: Int) : LevelWrapperDrawable(drawable) {
    companion object {
        const val HORIZONTAL = 1
        const val VERTICAL = 2
    }

    private val clip = Rect()

    override fun draw(canvas: Canvas) {
        val d = inner ?: return
        val level = getLevel()
        if (level == 0) return
        val bounds = getBounds()
        var w = bounds.width()
        var h = bounds.height()
        if (orientation and HORIZONTAL != 0) w = w * level / 10000
        if (orientation and VERTICAL != 0) h = h * level / 10000
        Gravity.apply(gravity, w, h, bounds, clip)
        if (w <= 0 || h <= 0) return
        d.setBounds(bounds)
        canvas.save()
        canvas.clipRect(clip)
        d.draw(canvas)
        canvas.restore()
    }
}

/** ScaleDrawable: the drawable shrunk by (10000 - level) times the scale fractions, placed by gravity */
open class ScaleDrawable(
    drawable: Drawable?,
    private val gravity: Int,
    private val scaleWidth: Float,
    private val scaleHeight: Float,
) : LevelWrapperDrawable(drawable) {
    private val target = Rect()

    override fun draw(canvas: Canvas) {
        val d = inner ?: return
        val level = getLevel()
        if (level == 0) return
        val bounds = getBounds()
        var w = bounds.width()
        var h = bounds.height()
        if (scaleWidth > 0) w -= (w * (10000 - level) * scaleWidth / 10000).toInt()
        if (scaleHeight > 0) h -= (h * (10000 - level) * scaleHeight / 10000).toInt()
        Gravity.apply(gravity, w, h, bounds, target)
        if (w <= 0 || h <= 0) return
        d.setBounds(target)
        d.draw(canvas)
    }
}
