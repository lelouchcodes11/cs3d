package androidx.viewpager.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * The support ViewPager. Pages are either the adapter's views or the XML children, one page wide,
 * and [setCurrentItem] scrolls to that page.
 */
open class ViewPager : ViewGroup {
    abstract class PagerAdapter {
        abstract fun getCount(): Int
        open fun instantiateItem(container: ViewGroup, position: Int): Any = View(container.getContext())
        open fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            if (`object` is View) container.removeView(`object`)
        }

        open fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
        open fun getPageTitle(position: Int): CharSequence? = null
        open fun getItemPosition(`object`: Any): Int = POSITION_UNCHANGED

        companion object {
            const val POSITION_UNCHANGED = -1
            const val POSITION_NONE = -2
        }
    }

    interface OnPageChangeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int)
        fun onPageSelected(position: Int)
        fun onPageScrollStateChanged(state: Int)
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }

    private val listeners = ArrayList<OnPageChangeListener>()
    private var adapter: PagerAdapter? = null
    private var current = 0

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    fun setAdapter(adapter: PagerAdapter?) {
        this.adapter = adapter
        removeAllViews()
        current = 0
        if (adapter != null) {
            for (i in 0 until adapter.getCount()) {
                val page = adapter.instantiateItem(this, i)
                if (page is View && page.getParent() == null) addView(page)
            }
        }
        requestLayout()
    }

    fun getAdapter(): PagerAdapter? = adapter

    fun setCurrentItem(item: Int) = setCurrentItem(item, true)

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        val count = pageCount()
        if (count <= 0) return
        val next = item.coerceIn(0, count - 1)
        if (next == current) return
        current = next
        val x = next * getWidth()
        if (smoothScroll) scrollTo(x, 0) else scrollTo(x, 0)
        listeners.forEach { it.onPageSelected(next) }
        requestLayout()
    }

    fun getCurrentItem(): Int = current

    fun addOnPageChangeListener(listener: OnPageChangeListener) {
        listeners.add(listener)
    }

    fun removeOnPageChangeListener(listener: OnPageChangeListener) {
        listeners.remove(listener)
    }

    fun setOnPageChangeListener(listener: OnPageChangeListener?) {
        listeners.clear()
        if (listener != null) listeners.add(listener)
    }

    private fun pageCount(): Int = adapter?.getCount() ?: getChildCount()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val childW = MeasureSpec.makeMeasureSpec(maxOf(0, w - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY)
        val childH = MeasureSpec.makeMeasureSpec(maxOf(0, h - getPaddingTop() - getPaddingBottom()), MeasureSpec.EXACTLY)
        for (i in 0 until getChildCount()) getChildAt(i)?.measure(childW, childH)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left - getPaddingLeft() - getPaddingRight()
        val h = bottom - top - getPaddingTop() - getPaddingBottom()
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val x = getPaddingLeft() + i * w
            child.layout(x, getPaddingTop(), x + w, getPaddingTop() + h)
        }
        scrollTo(current * w, 0)
    }
}
