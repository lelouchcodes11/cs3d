package androidx.viewpager2.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

/** Horizontal pager. [getChildAt] 0 is the internal RecyclerView, matching the Android widget. */
open class ViewPager2 : FrameLayout {
    abstract class OnPageChangeCallback {
        open fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        open fun onPageSelected(position: Int) {}
        open fun onPageScrollStateChanged(state: Int) {}
    }

    fun interface PageTransformer {
        fun transformPage(page: View, position: Float)
    }

    private val mRecyclerView: RecyclerView
    private val callbacks = ArrayList<OnPageChangeCallback>()
    private var transformer: PageTransformer? = null
    private var current = 0
    var offscreenPageLimit: Int = OFFSCREEN_PAGE_LIMIT_DEFAULT
    var isUserInputEnabled: Boolean = true

    constructor(context: Context?) : super(context) { mRecyclerView = install(context, HORIZONTAL) }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) { mRecyclerView = install(context, HORIZONTAL) }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        mRecyclerView = install(context, HORIZONTAL)
    }

    private fun install(context: Context?, orientation: Int): RecyclerView {
        val rv = RecyclerView(getContext())
        rv.setLayoutManager(LinearLayoutManager(getContext(), orientation, false))
        rv.setLayoutParams(LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        PagerSnapHelper().attachToRecyclerView(rv)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.getLayoutManager() as? LinearLayoutManager ?: return
                val pos = lm.findFirstVisibleItemPosition()
                if (pos != RecyclerView.NO_POSITION && pos != current) {
                    current = pos
                    callbacks.forEach { it.onPageSelected(pos) }
                }
                val pageTransformer = transformer ?: return
                val size = if (orientationOf() == HORIZONTAL) recyclerView.getWidth() else recyclerView.getHeight()
                if (size <= 0) return
                for (i in 0 until recyclerView.getChildCount()) {
                    val child = recyclerView.getChildAt(i) ?: continue
                    val start = if (orientationOf() == HORIZONTAL) child.getLeft() else child.getTop()
                    pageTransformer.transformPage(child, start.toFloat() / size)
                }
            }
        })
        addView(rv, 0)
        return rv
    }

    fun getRecyclerView(): RecyclerView = mRecyclerView

    override fun getChildAt(index: Int): View? = if (index == 0) mRecyclerView else super.getChildAt(index)

    fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        mRecyclerView.setAdapter(adapter)
    }

    fun getAdapter(): RecyclerView.Adapter<*>? = mRecyclerView.getAdapter()

    fun beginFakeDrag(): Boolean = false
    fun fakeDragBy(offsetPxFloat: Float): Boolean = false
    fun endFakeDrag(): Boolean = false
    fun isFakeDragging(): Boolean = false

    var currentItem: Int
        get() = current
        set(value) { setCurrentItem(value, false) }

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        current = item
        if (smoothScroll) mRecyclerView.smoothScrollToPosition(item) else mRecyclerView.scrollToPosition(item)
        callbacks.forEach { it.onPageSelected(item) }
    }

    fun registerOnPageChangeCallback(callback: OnPageChangeCallback) {
        callbacks.add(callback)
    }

    fun unregisterOnPageChangeCallback(callback: OnPageChangeCallback) {
        callbacks.remove(callback)
    }

    fun setPageTransformer(transformer: PageTransformer?) {
        this.transformer = transformer
    }

    var orientation: Int
        get() = orientationOf()
        set(value) {
            mRecyclerView.setLayoutManager(LinearLayoutManager(getContext(), value, false))
        }

    private fun orientationOf(): Int = (mRecyclerView.getLayoutManager() as? LinearLayoutManager)?.getOrientation() ?: HORIZONTAL

    companion object {
        const val ORIENTATION_HORIZONTAL = RecyclerView.HORIZONTAL
        const val ORIENTATION_VERTICAL = RecyclerView.VERTICAL
        const val HORIZONTAL = ORIENTATION_HORIZONTAL
        const val VERTICAL = ORIENTATION_VERTICAL
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
        const val OFFSCREEN_PAGE_LIMIT_DEFAULT = -1
    }
}
