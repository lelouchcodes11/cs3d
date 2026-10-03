@file:JvmName("AdaptersKt")

package android.widget

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.runtime.res.FrameworkResources
import kotlin.math.max
import kotlin.math.min

interface Adapter {
    fun registerDataSetObserver(observer: DataSetObserver?)
    fun unregisterDataSetObserver(observer: DataSetObserver?)
    fun getCount(): Int
    fun getItem(position: Int): Any?
    fun getItemId(position: Int): Long
    fun hasStableIds(): Boolean
    fun getView(position: Int, convertView: View?, parent: ViewGroup): View
    fun getItemViewType(position: Int): Int
    fun getViewTypeCount(): Int
    fun isEmpty(): Boolean
    fun getAutofillOptions(): Array<CharSequence>? = null

    companion object {
        const val IGNORE_ITEM_VIEW_TYPE = -1
        const val NO_SELECTION = Int.MIN_VALUE
    }
}

interface ListAdapter : Adapter {
    fun areAllItemsEnabled(): Boolean
    fun isEnabled(position: Int): Boolean
}

interface SpinnerAdapter : Adapter {
    fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View
}

interface ThemedSpinnerAdapter : SpinnerAdapter {
    fun setDropDownViewTheme(theme: android.content.res.Resources.Theme?)
    fun getDropDownViewTheme(): android.content.res.Resources.Theme?
}

abstract class BaseAdapter : ListAdapter, SpinnerAdapter {
    private val observers = ArrayList<DataSetObserver>()

    /** Observed by the renderer */
    val dataVersion = mutableIntStateOf(0)

    override fun hasStableIds(): Boolean = false
    override fun registerDataSetObserver(observer: DataSetObserver?) {
        if (observer != null) observers.add(observer)
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver?) {
        observers.remove(observer)
    }

    open fun notifyDataSetChanged() {
        dataVersion.intValue++
        for (o in observers.toList()) o.onChanged()
    }

    open fun notifyDataSetInvalidated() {
        dataVersion.intValue++
        for (o in observers.toList()) o.onInvalidated()
    }

    override fun areAllItemsEnabled(): Boolean = true
    override fun isEnabled(position: Int): Boolean = true
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = getView(position, convertView, parent)
    override fun getItemViewType(position: Int): Int = 0
    override fun getViewTypeCount(): Int = 1
    override fun isEmpty(): Boolean = getCount() == 0
}
open class SimpleAdapter(
    private val context: Context,
    private val data: List<Map<String, *>>,
    private val resource: Int,
    private val from: Array<String>,
    private val to: IntArray,
) : BaseAdapter(), Filterable {
    override fun getCount(): Int = data.size
    override fun getItem(position: Int): Any? = data[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(context).inflate(resource, parent, false)
        val map = data[position]
        for (i in from.indices) {
            val child = v.findViewById<View>(to[i])
            val value = map[from[i]]
            if (child is TextView) child.setText(value?.toString() ?: "")
        }
        return v
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults = FilterResults()
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {}
    }
}

abstract class AdapterView<T : Adapter> : ViewGroup {
    fun interface OnItemClickListener {
        fun onItemClick(parent: AdapterView<*>, view: View?, position: Int, id: Long)
    }

    fun interface OnItemLongClickListener {
        fun onItemLongClick(parent: AdapterView<*>, view: View?, position: Int, id: Long): Boolean
    }

    interface OnItemSelectedListener {
        fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long)
        fun onNothingSelected(parent: AdapterView<*>)
    }

    companion object {
        const val ITEM_VIEW_TYPE_IGNORE = -1
        const val ITEM_VIEW_TYPE_HEADER_OR_FOOTER = -2
        const val INVALID_POSITION = -1
        const val INVALID_ROW_ID = Long.MIN_VALUE
    }

    internal var mOnItemClickListener: OnItemClickListener? = null
    internal var mOnItemLongClickListener: OnItemLongClickListener? = null
    internal var mOnItemSelectedListener: OnItemSelectedListener? = null
    private var mEmptyView: View? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    abstract fun getAdapter(): T?
    abstract fun setAdapter(adapter: T?)
    abstract fun getSelectedView(): View?
    abstract fun setSelection(position: Int)

    open fun setOnItemClickListener(listener: OnItemClickListener?) {
        mOnItemClickListener = listener
    }

    fun getOnItemClickListener(): OnItemClickListener? = mOnItemClickListener
    open fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        mOnItemLongClickListener = listener
    }

    open fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        mOnItemSelectedListener = listener
    }

    fun getOnItemSelectedListener(): OnItemSelectedListener? = mOnItemSelectedListener

    open fun performItemClick(view: View?, position: Int, id: Long): Boolean {
        val l = mOnItemClickListener ?: return false
        l.onItemClick(this, view, position, id)
        return true
    }

    open fun getCount(): Int = getAdapter()?.getCount() ?: 0
    open fun getItemAtPosition(position: Int): Any? = getAdapter()?.getItem(position)
    open fun getItemIdAtPosition(position: Int): Long = getAdapter()?.getItemId(position) ?: INVALID_ROW_ID
    open fun getSelectedItemPosition(): Int = INVALID_POSITION
    open fun getSelectedItemId(): Long = INVALID_ROW_ID
    open fun getSelectedItem(): Any? {
        val p = getSelectedItemPosition()
        return if (p >= 0) getItemAtPosition(p) else null
    }

    open fun getPositionForView(view: View?): Int = INVALID_POSITION
    open fun getFirstVisiblePosition(): Int = 0
    open fun getLastVisiblePosition(): Int = getCount() - 1
    open fun setEmptyView(emptyView: View?) {
        mEmptyView = emptyView
    }

    open fun getEmptyView(): View? = mEmptyView
}

/**
 * AbsListView with native layout like Android: the visible items are real children obtained from
 * the adapter (convert views come from the RecycleBin), laid out top to bottom from the first
 * visible position; scrolling offsets them, fills the gaps and recycles what leaves the list.
 */
abstract class AbsListView : AdapterView<ListAdapter> {
    companion object {
        const val CHOICE_MODE_NONE = 0
        const val CHOICE_MODE_SINGLE = 1
        const val CHOICE_MODE_MULTIPLE = 2
        const val CHOICE_MODE_MULTIPLE_MODAL = 3
        const val TRANSCRIPT_MODE_DISABLED = 0
        const val TRANSCRIPT_MODE_NORMAL = 1
        const val TRANSCRIPT_MODE_ALWAYS_SCROLL = 2
    }

    interface OnScrollListener {
        fun onScrollStateChanged(view: AbsListView, scrollState: Int)
        fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int)

        companion object {
            const val SCROLL_STATE_IDLE = 0
            const val SCROLL_STATE_TOUCH_SCROLL = 1
            const val SCROLL_STATE_FLING = 2
        }
    }

    open class LayoutParams : ViewGroup.LayoutParams {
        @JvmField var viewType = 0
        @JvmField var forceAdd = false
        internal var position = -1

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(w: Int, h: Int) : super(w, h)
        constructor(w: Int, h: Int, viewType: Int) : super(w, h) {
            this.viewType = viewType
        }

        constructor(source: ViewGroup.LayoutParams) : super(source)
    }

    private var mAdapter: ListAdapter? = null
    private var mChoiceMode = CHOICE_MODE_NONE
    private val mCheckStates = HashMap<Int, Boolean>()
    private var mSelectedPosition = INVALID_POSITION
    private var mScrollListener: OnScrollListener? = null
    private var mScrollState = OnScrollListener.SCROLL_STATE_IDLE

    /** Adapter position of the first child and the top of the first child */
    protected var mFirstPosition = 0
    private var mFirstTop = Int.MIN_VALUE
    private var mInLayout = false
    private var mWidthMeasureSpec = 0
    private val mScrap = HashMap<Int, ArrayList<View>>()
    private val observer = object : android.database.DataSetObserver() {
        override fun onChanged() = dataChanged()
        override fun onInvalidated() = dataChanged()
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private fun dataChanged() {
        updateEmptyStatus()
        requestLayout()
        invalidate()
    }

    override fun getAdapter(): ListAdapter? = mAdapter
    override fun setAdapter(adapter: ListAdapter?) {
        mAdapter?.unregisterDataSetObserver(observer)
        mAdapter = adapter
        adapter?.registerDataSetObserver(observer)
        removeAllViewsInLayout()
        mScrap.clear()
        mFirstPosition = 0
        mFirstTop = Int.MIN_VALUE
        mCheckStates.clear()
        updateEmptyStatus()
        requestLayout()
    }

    /** AdapterView.updateEmptyStatus: the empty view replaces the list while there are no items */
    private fun updateEmptyStatus() {
        val empty = getEmptyView() ?: return
        if (getItemCountInternal() == 0) {
            empty.setVisibility(VISIBLE)
            setVisibility(GONE)
        } else {
            empty.setVisibility(GONE)
            setVisibility(VISIBLE)
        }
    }

    override fun setEmptyView(emptyView: View?) {
        super.setEmptyView(emptyView)
        updateEmptyStatus()
    }

    /** Items including headers and footers (HeaderViewListAdapter) */
    internal open fun getItemCountInternal(): Int = mAdapter?.getCount() ?: 0
    override fun getCount(): Int = getItemCountInternal()

    /** The view of a position with a convert view from the RecycleBin */
    protected open fun obtainView(position: Int): View {
        val adapter = mAdapter!!
        val type = adapter.getItemViewType(position)
        val scrap = if (type >= 0) mScrap[type]?.removeLastOrNull() else null
        val child = adapter.getView(position, scrap, this)
        if (scrap != null && child !== scrap) addScrap(scrap, type)
        val lp = child.layoutParamsOrNull().let { it as? LayoutParams ?: LayoutParams(it ?: generateDefaultLayoutParams()) }
        lp.viewType = type
        lp.position = position
        child.setLayoutParamsSilently(lp)
        return child
    }

    private fun addScrap(view: View, type: Int) {
        if (type < 0) return
        val list = mScrap.getOrPut(type) { ArrayList() }
        if (list.none { it === view }) list.add(view)
    }

    private fun viewTypeOf(v: View): Int = (v.getLayoutParams() as? LayoutParams)?.viewType ?: -1

    /** List position of a child laid out by this list */
    fun positionOf(v: View): Int = (v.getLayoutParams() as? LayoutParams)?.position ?: INVALID_POSITION

    override fun getPositionForView(view: View?): Int {
        var v = view ?: return INVALID_POSITION
        while (true) {
            val p = v.getParent() ?: return INVALID_POSITION
            if (p === this) break
            v = p as? View ?: return INVALID_POSITION
        }
        return positionOf(v)
    }

    override fun getFirstVisiblePosition(): Int = mFirstPosition
    override fun getLastVisiblePosition(): Int = mFirstPosition + getChildCount() - 1

    // ------------------------------------------------------------------ rows (GridView has several items per row)

    protected open fun columns(): Int = 1

    /** Space between rows (ListView divider, GridView vertical spacing) */
    protected open fun rowSpacing(): Int = 0

    /** ListView.measureScrapChild: width from the list (or exact), height from the item */
    protected fun measureItem(child: View, widthSpec: Int, padding: Int, exactWidth: Int = -1) {
        val p = child.layoutParamsOrNull() ?: generateDefaultLayoutParams()
        val childWidthSpec = if (exactWidth >= 0) MeasureSpec.makeMeasureSpec(exactWidth, MeasureSpec.EXACTLY)
        else ViewGroup.getChildMeasureSpec(widthSpec, padding, p.width)
        val childHeightSpec = if (p.height > 0) MeasureSpec.makeMeasureSpec(p.height, MeasureSpec.EXACTLY)
        else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child.measure(childWidthSpec, childHeightSpec)
    }

    protected open fun measureRowItem(child: View, widthSpec: Int) = measureItem(child, widthSpec, getPaddingLeft() + getPaddingRight())

    /** Measures a row's views, then positions them at [top]; returns the row height */
    protected open fun layoutRow(views: List<View>, top: Int, widthSpec: Int): Int {
        var h = 0
        for (v in views) {
            measureRowItem(v, widthSpec)
            h = max(h, v.getMeasuredHeight())
        }
        views.forEachIndexed { i, v ->
            val left = rowItemLeft(i)
            v.layout(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight())
        }
        return h
    }

    protected open fun rowItemLeft(index: Int): Int = getPaddingLeft()

    private fun obtainAndAdd(position: Int, index: Int): View {
        val child = obtainView(position)
        if (mChoiceMode != CHOICE_MODE_NONE) {
            if (child is Checkable) child.setChecked(isItemChecked(position))
            child.setActivated(isItemChecked(position))
        }
        if (child.getParent() === this) {
            val i = indexOfChild(child)
            if (i >= 0) detachViewFromParent(i)
            attachViewToParent(child, index, child.getLayoutParams())
        } else addViewInLayout(child, index, child.getLayoutParams(), true)
        return child
    }

    /** Lays out rows from [row] with its top at [top] while above [bottomLimit]; returns the next top */
    private fun fillDown(row: Int, top: Int, bottomLimit: Int): Int {
        val count = getItemCountInternal()
        val cols = columns()
        var r = row
        var y = top
        while (r * cols < count && y < bottomLimit) {
            val first = r * cols
            val views = (first until min(count, first + cols)).map { obtainAndAdd(it, -1) }
            y += layoutRow(views, y, mWidthMeasureSpec) + rowSpacing()
            r++
        }
        return y
    }

    /** Lays out the rows above [row] ending at [bottom] while below [topLimit]; returns the new top */
    private fun fillUp(row: Int, bottom: Int, topLimit: Int): Int {
        val cols = columns()
        var r = row - 1
        var y = bottom
        while (r >= 0 && y > topLimit) {
            val first = r * cols
            val last = min(getItemCountInternal(), first + cols)
            val views = (first until last).mapIndexed { i, p -> obtainAndAdd(p, i) }
            var h = 0
            for (v in views) {
                measureRowItem(v, mWidthMeasureSpec)
                h = max(h, v.getMeasuredHeight())
            }
            val top = y - rowSpacing() - h
            layoutRow(views, top, mWidthMeasureSpec)
            y = top
            mFirstPosition = first
            r--
        }
        return y
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        layoutChildren()
    }

    /** AbsListView.layoutChildren: the visible rows again from the first position */
    protected open fun layoutChildren() {
        if (mInLayout) return
        mInLayout = true
        try {
            val count = getItemCountInternal()
            val top = getPaddingTop()
            val bottom = getHeight() - getPaddingBottom()
            mWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY)
            for (i in getChildCount() - 1 downTo 0) {
                val c = getChildAt(i) ?: continue
                detachViewFromParent(i)
                addScrap(c, viewTypeOf(c))
            }
            if (count == 0) {
                mFirstPosition = 0
                cleanupScrap()
                return
            }
            val cols = columns()
            if (mFirstPosition >= count) mFirstPosition = max(0, count - 1)
            mFirstPosition -= mFirstPosition % cols
            val startTop = if (mFirstTop == Int.MIN_VALUE) top else mFirstTop
            fillDown(mFirstPosition / cols, startTop, bottom)
            // no gap below the last row when the end is reached (correctTooHigh)
            val lastChild = getChildAt(getChildCount() - 1)
            if (lastChild != null && mFirstPosition + getChildCount() >= count && lastChild.getBottom() < bottom) {
                val gap = bottom - lastChild.getBottom()
                val firstTop = getChildAt(0)!!.getTop()
                val shift = if (mFirstPosition == 0) min(gap, max(0, top - firstTop)) else gap
                if (shift > 0) {
                    for (i in 0 until getChildCount()) getChildAt(i)?.offsetTopAndBottom(shift)
                    if (mFirstPosition > 0) fillUp(mFirstPosition / cols, getChildAt(0)!!.getTop(), top)
                }
            }
            // no gap above the first row
            val firstChild = getChildAt(0)
            if (mFirstPosition == 0 && firstChild != null && firstChild.getTop() > top) {
                val d = firstChild.getTop() - top
                for (i in 0 until getChildCount()) getChildAt(i)?.offsetTopAndBottom(-d)
                val last = getChildAt(getChildCount() - 1)!!
                val nextPos = mFirstPosition + getChildCount()
                if (nextPos < count) fillDown(nextPos / cols, last.getBottom() + rowSpacing(), bottom)
            }
            cleanupScrap()
            recycleOffscreen()
        } finally {
            mInLayout = false
        }
        invokeOnItemScrollListener()
    }

    /** Scrap views not reused by the layout leave the window (kept as convert views) */
    private fun cleanupScrap() {
        for (list in mScrap.values) {
            for (v in list) if (v.getParent() == null && v.isAttachedToWindow()) v.dispatchDetachedFromWindow()
            while (list.size > 8) list.removeAt(0)
        }
    }

    /** Rows entirely outside the list go back to the scrap */
    private fun recycleOffscreen() {
        val top = if (getClipToPadding()) getPaddingTop() else 0
        val bottom = getHeight() - if (getClipToPadding()) getPaddingBottom() else 0
        val cols = columns()
        while (getChildCount() > cols) {
            val rowViews = (0 until min(cols, getChildCount())).mapNotNull { getChildAt(it) }
            if (rowViews.all { it.getBottom() <= top }) {
                for (v in rowViews) removeToScrap(v)
                mFirstPosition += rowViews.size
            } else break
        }
        while (getChildCount() > cols) {
            val lastRowSize = (mFirstPosition + getChildCount() - 1) % cols + 1
            val rowViews = (getChildCount() - lastRowSize until getChildCount()).mapNotNull { getChildAt(it) }
            if (rowViews.all { it.getTop() >= bottom }) for (v in rowViews) removeToScrap(v) else break
        }
        mFirstTop = getChildAt(0)?.getTop() ?: getPaddingTop()
    }

    private fun removeToScrap(v: View) {
        removeViewInLayout(v)
        addScrap(v, viewTypeOf(v))
    }

    /** Scrolls the content by [dy] (positive: towards the end); returns the consumed px */
    fun trackMotionScroll(dy: Int): Int {
        if (getChildCount() == 0 || dy == 0) return 0
        val count = getItemCountInternal()
        val top = getPaddingTop()
        val bottom = getHeight() - getPaddingBottom()
        val cols = columns()
        mWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY)
        mInLayout = true
        val consumed: Int
        try {
            if (dy > 0) {
                var lastBottom = getChildAt(getChildCount() - 1)!!.getBottom()
                val lastPos = mFirstPosition + getChildCount() - 1
                if (lastBottom - dy < bottom && lastPos < count - 1) {
                    lastBottom = fillDown((lastPos + 1) / cols, lastBottom + rowSpacing(), bottom + dy) - rowSpacing()
                }
                consumed = min(dy, max(0, lastBottom - bottom))
            } else {
                var firstTop = getChildAt(0)!!.getTop()
                if (firstTop - dy > top && mFirstPosition > 0) firstTop = fillUp(mFirstPosition / cols, firstTop, top + dy)
                consumed = max(dy, min(0, firstTop - top))
            }
            if (consumed != 0) for (i in 0 until getChildCount()) getChildAt(i)?.offsetTopAndBottom(-consumed)
            recycleOffscreen()
        } finally {
            mInLayout = false
        }
        if (consumed != 0) invokeOnItemScrollListener()
        return consumed
    }

    open fun scrollListBy(y: Int) {
        trackMotionScroll(y)
    }

    open fun canScrollList(direction: Int): Boolean {
        val count = getChildCount()
        if (count == 0) return false
        return if (direction > 0) {
            mFirstPosition + count < getItemCountInternal() || getChildAt(count - 1)!!.getBottom() > getHeight() - getPaddingBottom()
        } else mFirstPosition > 0 || getChildAt(0)!!.getTop() < getPaddingTop()
    }

    override fun canScrollVertically(direction: Int): Boolean = canScrollList(direction)

    private fun invokeOnItemScrollListener() {
        mScrollListener?.onScroll(this, mFirstPosition, getChildCount(), getItemCountInternal())
    }

    /** Renderer: user scrolling state */
    fun onUserScrollState(scrolling: Boolean) {
        val s = if (scrolling) OnScrollListener.SCROLL_STATE_TOUCH_SCROLL else OnScrollListener.SCROLL_STATE_IDLE
        if (s != mScrollState) {
            mScrollState = s
            mScrollListener?.onScrollStateChanged(this, s)
        }
    }

    override fun getSelectedView(): View? = if (mSelectedPosition >= 0) getChildAt(mSelectedPosition - mFirstPosition) else null

    /** Puts the item at the top of the list (ListView.setSelection) */
    override fun setSelection(position: Int) = setSelectionFromTop(position, 0)

    open fun setSelectionFromTop(position: Int, y: Int) {
        mSelectedPosition = position
        if (position < 0) return
        mFirstPosition = position
        mFirstTop = getPaddingTop() + y
        requestLayout()
    }

    override fun getSelectedItemPosition(): Int = mSelectedPosition
    open fun setChoiceMode(choiceMode: Int) {
        mChoiceMode = choiceMode
    }

    open fun getChoiceMode(): Int = mChoiceMode
    open fun setItemChecked(position: Int, value: Boolean) {
        if (mChoiceMode == CHOICE_MODE_NONE) return
        if (mChoiceMode == CHOICE_MODE_SINGLE && value) mCheckStates.clear()
        if (position >= 0) mCheckStates[position] = value
        for (i in 0 until getChildCount()) {
            val c = getChildAt(i) ?: continue
            val p = positionOf(c)
            if (c is Checkable) c.setChecked(isItemChecked(p))
            c.setActivated(isItemChecked(p))
        }
    }

    /** AbsListView.performItemClick: updates the checked items for the choice mode, then notifies */
    override fun performItemClick(view: View?, position: Int, id: Long): Boolean {
        when (mChoiceMode) {
            CHOICE_MODE_MULTIPLE, CHOICE_MODE_MULTIPLE_MODAL -> setItemChecked(position, !isItemChecked(position))
            CHOICE_MODE_SINGLE -> setItemChecked(position, true)
        }
        return super.performItemClick(view, position, id) || mChoiceMode != CHOICE_MODE_NONE
    }

    open fun isItemChecked(position: Int): Boolean = mCheckStates[position] == true
    open fun getCheckedItemPosition(): Int = if (mChoiceMode == CHOICE_MODE_SINGLE) mCheckStates.entries.firstOrNull { it.value }?.key ?: INVALID_POSITION else INVALID_POSITION
    open fun getCheckedItemCount(): Int = mCheckStates.count { it.value }
    open fun getCheckedItemPositions(): android.util.SparseBooleanArray {
        val a = android.util.SparseBooleanArray()
        for ((k, v) in mCheckStates) a.put(k, v)
        return a
    }

    open fun getCheckedItemIds(): LongArray = mCheckStates.filter { it.value }.keys.map { mAdapter?.getItemId(it) ?: it.toLong() }.toLongArray()
    open fun clearChoices() {
        mCheckStates.clear()
        setItemChecked(-1, false)
    }

    open fun setOnScrollListener(l: OnScrollListener?) {
        mScrollListener = l
        invokeOnItemScrollListener()
    }

    open fun setFastScrollEnabled(enabled: Boolean) {}
    open fun setFastScrollAlwaysVisible(alwaysShow: Boolean) {}
    open fun setSmoothScrollbarEnabled(enabled: Boolean) {}
    open fun setSelector(sel: android.graphics.drawable.Drawable?) {}
    open fun setSelector(resID: Int) {}
    open fun setDrawSelectorOnTop(onTop: Boolean) {}
    open fun setCacheColorHint(color: Int) {}
    open fun setTextFilterEnabled(textFilterEnabled: Boolean) {}
    open fun setScrollingCacheEnabled(enabled: Boolean) {}
    open fun setStackFromBottom(stackFromBottom: Boolean) {}
    open fun setTranscriptMode(mode: Int) {}
    open fun smoothScrollToPosition(position: Int) {
        if (position < mFirstPosition || position > getLastVisiblePosition()) setSelection(position)
    }

    open fun smoothScrollBy(distance: Int, duration: Int) {
        trackMotionScroll(distance)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0)
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = LayoutParams(p)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    /** ListView.onMeasure: without a height bound the first item decides, else rows up to the bound */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        var widthSize = MeasureSpec.getSize(widthMeasureSpec)
        var heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val count = getItemCountInternal()
        var childWidth = 0
        var childHeight = 0
        mWidthMeasureSpec = widthMeasureSpec
        if (count > 0 && (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED)) {
            val child = obtainView(0)
            measureRowItem(child, widthMeasureSpec)
            childWidth = child.getMeasuredWidth()
            childHeight = child.getMeasuredHeight()
            addScrap(child, viewTypeOf(child))
        }
        if (widthMode == MeasureSpec.UNSPECIFIED) widthSize = getPaddingLeft() + getPaddingRight() + childWidth * columns()
        if (heightMode == MeasureSpec.UNSPECIFIED) heightSize = getPaddingTop() + getPaddingBottom() + childHeight
        if (heightMode == MeasureSpec.AT_MOST) heightSize = measureHeightOfRows(widthMeasureSpec, heightSize)
        setMeasuredDimension(widthSize, heightSize)
    }

    /** ListView.measureHeightOfChildren: rows until [maxHeight] */
    private fun measureHeightOfRows(widthMeasureSpec: Int, maxHeight: Int): Int {
        val count = getItemCountInternal()
        var h = getPaddingTop() + getPaddingBottom()
        if (mAdapter == null && count == 0) return h
        val cols = columns()
        var p = 0
        while (p < count) {
            var rowH = 0
            for (q in p until min(count, p + cols)) {
                val child = obtainView(q)
                measureRowItem(child, widthMeasureSpec)
                rowH = max(rowH, child.getMeasuredHeight())
                if (child.getParent() == null) addScrap(child, viewTypeOf(child))
            }
            if (p > 0) h += rowSpacing()
            h += rowH
            if (h >= maxHeight) return maxHeight
            p += cols
        }
        return h
    }
}

open class ListView : AbsListView {
    private var mDivider: android.graphics.drawable.Drawable? = null
    private var mDividerHeight = 0
    private val mHeaders = ArrayList<View>()
    private val mFooters = ArrayList<View>()
    private var mItemsCanFocus = false

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun setDivider(divider: android.graphics.drawable.Drawable?) {
        mDivider = divider
        mDividerHeight = divider?.getIntrinsicHeight()?.takeIf { it > 0 } ?: 0
        requestLayout()
    }

    open fun getDivider(): android.graphics.drawable.Drawable? = mDivider
    open fun setDividerHeight(height: Int) {
        mDividerHeight = height
        requestLayout()
    }

    open fun getDividerHeight(): Int = mDividerHeight
    override fun rowSpacing(): Int = if (mDivider != null) mDividerHeight else 0

    open fun addHeaderView(v: View) = addHeaderView(v, null, true)
    open fun addHeaderView(v: View, data: Any?, isSelectable: Boolean) {
        mHeaders.add(v)
        requestLayout()
    }

    open fun addFooterView(v: View) = addFooterView(v, null, true)
    open fun addFooterView(v: View, data: Any?, isSelectable: Boolean) {
        mFooters.add(v)
        requestLayout()
    }

    open fun removeHeaderView(v: View?): Boolean = if (v != null) mHeaders.remove(v).also { if (it) requestLayout() } else false
    open fun removeFooterView(v: View?): Boolean = if (v != null) mFooters.remove(v).also { if (it) requestLayout() } else false
    open fun getHeaderViewsCount(): Int = mHeaders.size
    open fun getFooterViewsCount(): Int = mFooters.size
    open fun setItemsCanFocus(itemsCanFocus: Boolean) {
        mItemsCanFocus = itemsCanFocus
    }

    open fun getItemsCanFocus(): Boolean = mItemsCanFocus
    open fun setFooterDividersEnabled(footerDividersEnabled: Boolean) {}
    open fun setHeaderDividersEnabled(headerDividersEnabled: Boolean) {}

    override fun getItemCountInternal(): Int = mHeaders.size + (getAdapter()?.getCount() ?: 0) + mFooters.size

    /** Headers, then the adapter's items, then footers (HeaderViewListAdapter) */
    override fun obtainView(position: Int): View {
        val h = mHeaders.size
        val n = getAdapter()?.getCount() ?: 0
        val fixed = when {
            position < h -> mHeaders[position]
            position >= h + n -> mFooters[position - h - n]
            else -> null
        }
        if (fixed == null) {
            val v = super.obtainView(position - h)
            (v.getLayoutParams() as? LayoutParams)?.position = position
            return v
        }
        val lp = fixed.layoutParamsOrNull().let { it as? LayoutParams ?: LayoutParams(it ?: generateDefaultLayoutParams()) }
        lp.viewType = AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER
        lp.position = position
        fixed.setLayoutParamsSilently(lp)
        return fixed
    }

    override fun getItemAtPosition(position: Int): Any? {
        val h = mHeaders.size
        val n = getAdapter()?.getCount() ?: 0
        return if (position in h until h + n) getAdapter()?.getItem(position - h) else null
    }

    override fun getItemIdAtPosition(position: Int): Long {
        val h = mHeaders.size
        val n = getAdapter()?.getCount() ?: 0
        return if (position in h until h + n) getAdapter()?.getItemId(position - h) ?: INVALID_ROW_ID else INVALID_ROW_ID
    }

    /** Dividers between the children (ListView.dispatchDraw) */
    fun drawDividers(canvas: android.graphics.Canvas) {
        val divider = mDivider ?: return
        if (mDividerHeight <= 0) return
        for (i in 0 until getChildCount() - 1) {
            val c = getChildAt(i) ?: continue
            divider.setBounds(getPaddingLeft(), c.getBottom(), getWidth() - getPaddingRight(), c.getBottom() + mDividerHeight)
            divider.draw(canvas)
        }
    }
}

open class GridView : AbsListView {
    companion object {
        const val NO_STRETCH = 0
        const val STRETCH_SPACING = 1
        const val STRETCH_COLUMN_WIDTH = 2
        const val STRETCH_SPACING_UNIFORM = 3
        const val AUTO_FIT = -1
    }

    private var mRequestedNumColumns = AUTO_FIT
    private var mNumColumns = 1
    private var mColumnWidth = 0
    private var mRequestedColumnWidth = 0
    private var mHorizontalSpacing = 0
    private var mVerticalSpacing = 0
    private var mStretchMode = STRETCH_COLUMN_WIDTH

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun setNumColumns(numColumns: Int) {
        mRequestedNumColumns = numColumns
        requestLayout()
    }

    open fun getNumColumns(): Int = mNumColumns
    open fun setColumnWidth(columnWidth: Int) {
        mRequestedColumnWidth = columnWidth
        requestLayout()
    }

    open fun getColumnWidth(): Int = mColumnWidth
    open fun getRequestedColumnWidth(): Int = mRequestedColumnWidth
    open fun setHorizontalSpacing(horizontalSpacing: Int) {
        mHorizontalSpacing = horizontalSpacing
        requestLayout()
    }

    open fun setVerticalSpacing(verticalSpacing: Int) {
        mVerticalSpacing = verticalSpacing
        requestLayout()
    }

    open fun getHorizontalSpacing(): Int = mHorizontalSpacing
    open fun getVerticalSpacing(): Int = mVerticalSpacing
    open fun setStretchMode(stretchMode: Int) {
        mStretchMode = stretchMode
        requestLayout()
    }

    open fun getStretchMode(): Int = mStretchMode
    override fun columns(): Int = max(1, mNumColumns)
    override fun rowSpacing(): Int = mVerticalSpacing
    override fun rowItemLeft(index: Int): Int = getPaddingLeft() + index * (mColumnWidth + mHorizontalSpacing)
    override fun measureRowItem(child: View, widthSpec: Int) = measureItem(child, widthSpec, 0, mColumnWidth)

    /** GridView.determineColumns */
    private fun determineColumns(availableSpace: Int) {
        val spacing = mHorizontalSpacing
        val requested = mRequestedColumnWidth
        mNumColumns = if (mRequestedNumColumns == AUTO_FIT) {
            if (requested > 0) max(1, (availableSpace + spacing) / (requested + spacing)) else 2
        } else max(1, mRequestedNumColumns)
        mColumnWidth = when {
            mStretchMode == NO_STRETCH -> requested
            requested <= 0 || mStretchMode == STRETCH_COLUMN_WIDTH -> max(0, (availableSpace - (mNumColumns - 1) * spacing) / mNumColumns)
            else -> requested
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            determineColumns(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight())
        } else {
            mNumColumns = max(1, if (mRequestedNumColumns > 0) mRequestedNumColumns else 1)
            mColumnWidth = max(0, mRequestedColumnWidth)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        determineColumns(r - l - getPaddingLeft() - getPaddingRight())
        super.onLayout(changed, l, t, r, b)
    }
}

abstract class AbsSpinner : AdapterView<SpinnerAdapter> {
    private var mAdapter by mutableStateOf<SpinnerAdapter?>(null)
    private var mSelected by mutableIntStateOf(INVALID_POSITION)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun getAdapter(): SpinnerAdapter? = mAdapter
    override fun setAdapter(adapter: SpinnerAdapter?) {
        mAdapter = adapter
        requestLayout()
        if (adapter != null && adapter.getCount() > 0 && mSelected == INVALID_POSITION) setSelectionInternal(0, false)
        else if (adapter == null || adapter.getCount() == 0) mSelected = INVALID_POSITION
    }

    override fun setSelection(position: Int) = setSelection(position, false)
    open fun setSelection(position: Int, animate: Boolean) = setSelectionInternal(position, true)

    /** Changes the selection, notifying the listener like Android does (asynchronously) */
    fun setSelectionInternal(position: Int, notify: Boolean) {
        val count = mAdapter?.getCount() ?: 0
        if (position < 0 || position >= count) return
        val changed = mSelected != position
        mSelected = position
        if (changed) requestLayout()
        if (changed || !notify) {
            post {
                mOnItemSelectedListener?.onItemSelected(this, null, position, mAdapter?.getItemId(position) ?: position.toLong())
            }
        }
    }

    override fun getSelectedItemPosition(): Int = mSelected
    override fun getSelectedItemId(): Long = if (mSelected >= 0) mAdapter?.getItemId(mSelected) ?: INVALID_ROW_ID else INVALID_ROW_ID
    override fun getSelectedView(): View? = mSelectedView
    override fun getCount(): Int = mAdapter?.getCount() ?: 0

    private var mSelectedView: View? = null
    private var mSelectedViewPosition = INVALID_POSITION
    private var mSelectedViewVersion = -1

    /** The adapter view of the selected item (AbsSpinner's selected child), shown by the renderer */
    fun selectedItemView(): View? {
        val adapter = mAdapter ?: return null
        val pos = mSelected
        if (pos < 0 || pos >= adapter.getCount()) return null
        val version = (adapter as? BaseAdapter)?.dataVersion?.intValue ?: 0
        if (mSelectedView == null || mSelectedViewPosition != pos || mSelectedViewVersion != version) {
            val v = adapter.getView(pos, mSelectedView, this)
            if (v.layoutParamsOrNull() == null) v.setLayoutParamsSilently(generateDefaultLayoutParams())
            mSelectedView = v
            mSelectedViewPosition = pos
            mSelectedViewVersion = version
        }
        return mSelectedView
    }

    /** AbsSpinner.onMeasure: the size of the selected item view plus the padding */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var preferredHeight: Int
        var preferredWidth = 0
        val v = selectedItemView()
        if (v != null) {
            measureChild(v, widthMeasureSpec, heightMeasureSpec)
            preferredHeight = v.getMeasuredHeight() + getPaddingTop() + getPaddingBottom()
            preferredWidth = v.getMeasuredWidth() + getPaddingLeft() + getPaddingRight()
        } else {
            preferredHeight = getPaddingTop() + getPaddingBottom()
            if (widthMode == MeasureSpec.UNSPECIFIED) preferredWidth = getPaddingLeft() + getPaddingRight()
        }
        preferredHeight = kotlin.math.max(preferredHeight, getSuggestedMinimumHeight())
        preferredWidth = kotlin.math.max(preferredWidth, getSuggestedMinimumWidth())
        setMeasuredDimension(resolveSizeAndState(preferredWidth, widthMeasureSpec, 0), resolveSizeAndState(preferredHeight, heightMeasureSpec, 0))
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

open class Spinner : AbsSpinner {
    companion object {
        const val MODE_DIALOG = 0
        const val MODE_DROPDOWN = 1
    }

    private var mPrompt: CharSequence? = null

    init {
        // Widget.AppCompat.Spinner: room for the drop down arrow, list item height
        val d = com.lagradost.desktop.runtime.ui.WidgetDefaults
        if (getPaddingRight() == 0) setPadding(getPaddingLeft(), getPaddingTop(), d.dp(24f), getPaddingBottom())
        if (getMinimumHeight() == 0) setMinimumHeight(d.dp(48f))
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, mode: Int) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, mode: Int) : super(context, attrs, defStyleAttr)

    override fun setOnItemClickListener(listener: OnItemClickListener?) {
        throw RuntimeException("setOnItemClickListener cannot be used with a spinner.")
    }

    open fun setPrompt(prompt: CharSequence?) {
        mPrompt = prompt
    }

    open fun getPrompt(): CharSequence? = mPrompt
    open fun setDropDownWidth(pixels: Int) {}
    open fun setDropDownVerticalOffset(pixels: Int) {}
    open fun setPopupBackgroundDrawable(background: android.graphics.drawable.Drawable?) {}
    open fun setPopupBackgroundResource(resId: Int) {}
    open fun setGravity(gravity: Int) {}
    open fun performClick(open: Boolean): Boolean = true
}
