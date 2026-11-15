package androidx.compose.ui.platform

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * View that hosts Compose content. The renderer draws [content] inside this view's bounds.
 * The constructors take default arguments as on Android: extensions built with Kotlin call `ComposeView(context)` through the synthetic
 * `<init>(Context, AttributeSet, int, int, DefaultConstructorMarker)` (AnimePahe's and AniVortex's settings failed with NoSuchMethodError).
 */
open class AbstractComposeView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    protected open fun onMeasureContent(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** As on Android: an interface with one object per strategy (extensions read `ViewCompositionStrategy$DisposeOnViewTreeLifecycleDestroyed.INSTANCE`) */
interface ViewCompositionStrategy {
    object DisposeOnDetachedFromWindow : ViewCompositionStrategy
    object DisposeOnDetachedFromWindowOrReleasedFromPool : ViewCompositionStrategy
    object DisposeOnViewTreeLifecycleDestroyed : ViewCompositionStrategy
    class DisposeOnLifecycleDestroyed(val lifecycle: Any?) : ViewCompositionStrategy

    companion object {
        val Default: ViewCompositionStrategy get() = DisposeOnDetachedFromWindowOrReleasedFromPool
    }
}

open class ComposeView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AbstractComposeView(context, attrs, defStyleAttr) {
    private var content: (@Composable () -> Unit)? = null
    var contentVersion by mutableIntStateOf(0)
        private set
    fun setViewCompositionStrategy(strategy: ViewCompositionStrategy) {}

    fun setContent(content: @Composable () -> Unit) {
        this.content = content
        contentVersion++
        requestLayout()
        invalidate()
    }

    fun getContent(): (@Composable () -> Unit)? = content

    /**
     * True when the last measure left the height to the view (WRAP_CONTENT, or a MATCH_PARENT view in a window that wraps its content: an extension's
     * settings dialog, which was 0 px high and showed nothing): the height is then the content's own, [contentHeight], measured by the renderer
     */
    @Volatile var wrapsHeight = false
        private set

    /** The most the content may take when it sets the height: the AT_MOST size, or the screen's height */
    @Volatile var heightLimit = 0
        private set

    /** The height the content wants at the view's width (the renderer reports it; -1 = not known yet) */
    @Volatile var contentHeight = -1
        private set

    /** Told by the renderer: a new height of the content makes the view measure again (posted: this comes during a Compose measure) */
    fun reportContentHeight(px: Int) {
        if (px == contentHeight) return
        contentHeight = px
        if (wrapsHeight) post { requestLayout() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val hMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val h = View.MeasureSpec.getSize(heightMeasureSpec)
        wrapsHeight = hMode != View.MeasureSpec.EXACTLY
        heightLimit = if (hMode == View.MeasureSpec.AT_MOST) h else runCatching { getContext()?.getResources()?.getDisplayMetrics()?.heightPixels }.getOrNull()?.takeIf { it > 0 } ?: 1080
        if (wMode == View.MeasureSpec.EXACTLY && hMode == View.MeasureSpec.EXACTLY) {
            setMeasuredDimension(w, h)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val mw = if (getMeasuredWidth() == 0 && wMode != View.MeasureSpec.UNSPECIFIED) w else getMeasuredWidth()
        val mh = when {
            hMode == View.MeasureSpec.EXACTLY -> h
            contentHeight >= 0 -> minOf(contentHeight, heightLimit)
            getMeasuredHeight() == 0 && hMode == View.MeasureSpec.AT_MOST -> h
            else -> getMeasuredHeight()
        }
        setMeasuredDimension(mw, mh)
    }
}
