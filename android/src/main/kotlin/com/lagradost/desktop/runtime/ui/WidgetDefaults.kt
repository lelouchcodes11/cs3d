package com.lagradost.desktop.runtime.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import com.lagradost.desktop.runtime.AndroidRuntime

/**
 * Default look of the framework widgets (what the theme's buttonStyle / editTextStyle ... give on
 * Android): background drawables and the sizes the widgets measure with.
 */
object WidgetDefaults {
    fun dp(v: Float): Int = (v * AndroidRuntime.displayMetrics.density + 0.5f).toInt()

    /** Widget.Material.Button background: rounded rect inset 4dp x 6dp (btn_default_material) */
    fun buttonBackground(): Drawable {
        val shape = GradientDrawable()
        shape.setCornerRadius(dp(2f).toFloat())
        shape.setColor(ThemeBridge.surfaceVariant)
        return InsetDrawable(shape, dp(4f), dp(6f), dp(4f), dp(6f))
    }

    /** Width of the check box / radio button drawable (abc_btn_check_material) */
    fun compoundButtonWidth(): Int = dp(32f)

    /** Width of a switch (track and thumb) and its padding to the text */
    fun switchWidth(): Int = dp(52f)
    fun switchPadding(): Int = dp(16f)
}

/**
 * abc_edit_text_material: an underline, accent colored when focused, inset 4dp at the sides, with
 * the padding of the AppCompat text field
 */
class EditTextBackgroundDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var focused = false
    private var enabled = true

    override fun draw(canvas: Canvas) {
        val b = getBounds()
        val inset = WidgetDefaults.dp(4f)
        val bottom = b.bottom - WidgetDefaults.dp(8f)
        val thickness = if (focused) WidgetDefaults.dp(2f) else WidgetDefaults.dp(1f)
        paint.setColor(
            when {
                !enabled -> (ThemeBridge.textColorSecondary and 0x00FFFFFF) or 0x61000000
                focused -> ThemeBridge.colorPrimary
                else -> ThemeBridge.textColorSecondary
            }
        )
        canvas.drawRect((b.left + inset).toFloat(), (bottom - thickness).toFloat(), (b.right - inset).toFloat(), bottom.toFloat(), paint)
    }

    override fun getPadding(padding: Rect): Boolean {
        padding.set(WidgetDefaults.dp(4f), WidgetDefaults.dp(10f), WidgetDefaults.dp(4f), WidgetDefaults.dp(13f))
        return true
    }

    override fun isStateful(): Boolean = true
    override fun onStateChange(state: IntArray): Boolean {
        val f = state.contains(View.STATE_FOCUSED) || state.contains(View.STATE_ACTIVATED)
        val e = state.contains(View.STATE_ENABLED)
        if (f == focused && e == enabled) return false
        focused = f
        enabled = e
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
