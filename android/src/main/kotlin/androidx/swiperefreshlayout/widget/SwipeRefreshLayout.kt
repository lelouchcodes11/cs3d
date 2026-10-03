package androidx.swiperefreshlayout.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout

/**
 * Pull-to-refresh container. [setRefreshing] draws the spinner over the child; an upward scroll the
 * child cannot consume (the list is already at the top) calls the refresh listener.
 */
open class SwipeRefreshLayout : FrameLayout {
    fun interface OnRefreshListener {
        fun onRefresh()
    }

    interface OnChildScrollUpCallback {
        fun canChildScrollUp(parent: SwipeRefreshLayout, child: View?): Boolean
    }

    private var listener: OnRefreshListener? = null
    private var scrollCallback: OnChildScrollUpCallback? = null
    private var refreshing = false
    private var indicatorColor = 0xFF3D50FA.toInt()
    private var generation = 0
    private val spinner = SpinnerDrawable()

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    fun setOnRefreshListener(listener: OnRefreshListener?) {
        this.listener = listener
    }

    fun setRefreshing(refreshing: Boolean) {
        if (this.refreshing == refreshing) return
        this.refreshing = refreshing
        if (refreshing) showSpinner() else hideSpinner()
    }

    fun isRefreshing(): Boolean = refreshing

    fun setColorSchemeColors(vararg colors: Int) {
        if (colors.isNotEmpty()) indicatorColor = colors[0]
        invalidate()
    }

    fun setProgressBackgroundColorSchemeColor(color: Int) {}
    fun setDistanceToTriggerSync(distance: Int) {}
    fun setOnChildScrollUpCallback(callback: OnChildScrollUpCallback?) {
        scrollCallback = callback
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
        if (dyUnconsumed < 0 && !refreshing && !canChildScrollUp()) {
            refreshing = true
            showSpinner()
            listener?.onRefresh()
        }
    }

    fun canChildScrollUp(): Boolean {
        val child = getChildAt(0)
        scrollCallback?.let { return it.canChildScrollUp(this, child) }
        return child?.canScrollVertically(-1) == true
    }

    private fun showSpinner() {
        if (getForeground() == null || getForeground() === spinner) setForeground(spinner)
        val gen = ++generation
        val tick = object : Runnable {
            override fun run() {
                if (gen != generation || !refreshing) return
                invalidate()
                postDelayed(this, 16)
            }
        }
        post(tick)
    }

    private fun hideSpinner() {
        generation++
        if (getForeground() === spinner) setForeground(null)
        invalidate()
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext().getResources().getDisplayMetrics()).toInt()

    private inner class SpinnerDrawable : Drawable() {
        private val paint = Paint().apply {
            setAntiAlias(true)
            setStyle(Paint.Style.STROKE)
            setStrokeCap(Paint.Cap.ROUND)
        }
        private val oval = RectF()
        private val startedAt = System.nanoTime()

        override fun draw(canvas: Canvas) {
            if (!refreshing) return
            val b = getBounds()
            val size = dp(36f).toFloat()
            val cx = b.exactCenterX()
            val cy = b.top + dp(28f)
            paint.setStrokeWidth(dp(3f).toFloat())
            paint.setColor(indicatorColor)
            oval.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
            val t = (System.nanoTime() - startedAt) / 1_000_000_000.0
            canvas.drawArc(oval, ((t * 300.0) % 360.0).toFloat() - 90f, 270f, false, paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
