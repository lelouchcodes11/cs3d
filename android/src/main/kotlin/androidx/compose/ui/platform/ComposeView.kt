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
 */
open class AbstractComposeView : FrameLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    protected open fun onMeasureContent(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

enum class ViewCompositionStrategy {
    DisposeOnDetachedFromWindow,
    DisposeOnViewTreeLifecycleDestroyed,
    DisposeOnDetachedFromWindowOrReleasedFromPool,
}

open class ComposeView : AbstractComposeView {
    private var content: (@Composable () -> Unit)? = null
    var contentVersion by mutableIntStateOf(0)
        private set

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setViewCompositionStrategy(strategy: ViewCompositionStrategy) {}

    fun setContent(content: @Composable () -> Unit) {
        this.content = content
        contentVersion++
        requestLayout()
        invalidate()
    }

    fun getContent(): (@Composable () -> Unit)? = content

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val hMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val h = View.MeasureSpec.getSize(heightMeasureSpec)
        if (wMode == View.MeasureSpec.EXACTLY && hMode == View.MeasureSpec.EXACTLY) {
            setMeasuredDimension(w, h)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (getMeasuredWidth() == 0 && wMode != View.MeasureSpec.UNSPECIFIED) setMeasuredDimension(w, getMeasuredHeight())
        if (getMeasuredHeight() == 0 && hMode != View.MeasureSpec.UNSPECIFIED) setMeasuredDimension(getMeasuredWidth(), h)
    }
}
