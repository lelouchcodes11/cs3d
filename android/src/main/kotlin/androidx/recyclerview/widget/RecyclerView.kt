package androidx.recyclerview.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * RecyclerView with native layout like AndroidX: the layout manager adds, measures and lays out the
 * visible item views as real children, scrolling offsets them and recycles those that leave the
 * viewport (scrap, view cache, RecycledViewPool). The renderer only draws the children and forwards
 * scroll input to [scrollBy].
 */
open class RecyclerView : ViewGroup {
    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
        const val NO_POSITION = -1
        const val NO_ID = -1L
        const val INVALID_TYPE = -1
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
        const val TOUCH_SLOP_DEFAULT = 0
        const val TOUCH_SLOP_PAGING = 1
        const val UNDEFINED_DURATION = Int.MIN_VALUE
        internal const val DEFAULT_CACHE_SIZE = 2
    }

    // ============================================================================ ViewHolder

    abstract class ViewHolder(@JvmField val itemView: View) {
        internal var mPosition = NO_POSITION
        internal var mOldPosition = NO_POSITION
        internal var mItemId = NO_ID
        internal var mItemViewType = INVALID_TYPE
        internal var mOwnerRecyclerView: RecyclerView? = null
        internal var mBindingAdapter: Adapter<*>? = null
        internal var mFlags = 0
        internal var mIsRecyclableCount = 0
        internal var mScrap = false
        internal var mPayloads: MutableList<Any>? = null

        internal companion object {
            const val FLAG_BOUND = 1
            const val FLAG_UPDATE = 2
            const val FLAG_INVALID = 4
            const val FLAG_REMOVED = 8
        }

        internal fun has(flag: Int) = (mFlags and flag) != 0
        internal fun isInvalid() = has(FLAG_INVALID)
        internal fun isRemoved() = has(FLAG_REMOVED)
        internal fun isBound() = has(FLAG_BOUND)
        internal fun needsUpdate() = has(FLAG_UPDATE)
        internal fun addPayload(payload: Any?) {
            if (payload == null) {
                mPayloads = null
                mFlags = mFlags or FLAG_UPDATE
                return
            }
            if (!needsUpdate() || mPayloads != null) (mPayloads ?: ArrayList<Any>().also { mPayloads = it }).add(payload)
            mFlags = mFlags or FLAG_UPDATE
        }

        val adapterPosition: Int get() = if (isInvalid() || isRemoved()) NO_POSITION else mPosition
        val bindingAdapterPosition: Int get() = adapterPosition
        val absoluteAdapterPosition: Int get() = adapterPosition
        val layoutPosition: Int get() = mPosition
        val position: Int get() = mPosition
        val oldPosition: Int get() = mOldPosition
        val itemId: Long get() = mItemId
        val itemViewType: Int get() = mItemViewType
        fun getBindingAdapter(): Adapter<out ViewHolder>? = mBindingAdapter
        fun isRecyclable(): Boolean = mIsRecyclableCount <= 0
        fun setIsRecyclable(recyclable: Boolean) {
            mIsRecyclableCount = if (recyclable) max(0, mIsRecyclableCount - 1) else mIsRecyclableCount + 1
        }

        override fun toString(): String = "ViewHolder{${Integer.toHexString(hashCode())} position=$mPosition id=$mItemId}"
    }

    // ============================================================================ Adapter

    abstract class AdapterDataObserver {
        open fun onChanged() {}
        open fun onItemRangeChanged(positionStart: Int, itemCount: Int) {}
        open fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = onItemRangeChanged(positionStart, itemCount)
        open fun onItemRangeInserted(positionStart: Int, itemCount: Int) {}
        open fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {}
        open fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {}
        open fun onStateRestorationPolicyChanged() {}
    }

    abstract class Adapter<VH : ViewHolder> {
        enum class StateRestorationPolicy { ALLOW, PREVENT_WHEN_EMPTY, PREVENT }

        private val observers = java.util.concurrent.CopyOnWriteArrayList<AdapterDataObserver>()
        private var hasStableIds = false
        private var restorationPolicy = StateRestorationPolicy.ALLOW

        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
        abstract fun onBindViewHolder(holder: VH, position: Int)
        open fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) = onBindViewHolder(holder, position)
        abstract fun getItemCount(): Int
        open fun getItemViewType(position: Int): Int = 0
        open fun getItemId(position: Int): Long = NO_ID
        open fun setHasStableIds(hasStableIds: Boolean) {
            if (hasObservers()) throw IllegalStateException("Cannot change whether this adapter has stable IDs while the adapter has registered observers.")
            this.hasStableIds = hasStableIds
        }

        fun hasStableIds(): Boolean = hasStableIds
        open fun onViewRecycled(holder: VH) {}
        open fun onFailedToRecycleView(holder: VH): Boolean = false
        open fun onViewAttachedToWindow(holder: VH) {}
        open fun onViewDetachedFromWindow(holder: VH) {}
        open fun onAttachedToRecyclerView(recyclerView: RecyclerView) {}
        open fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {}
        open fun setStateRestorationPolicy(strategy: StateRestorationPolicy) {
            restorationPolicy = strategy
        }

        fun getStateRestorationPolicy(): StateRestorationPolicy = restorationPolicy
        open fun findRelativeAdapterPositionIn(adapter: Adapter<out ViewHolder>, viewHolder: ViewHolder, localPosition: Int): Int =
            if (adapter === this) localPosition else NO_POSITION

        open fun registerAdapterDataObserver(observer: AdapterDataObserver) {
            observers.add(observer)
        }

        open fun unregisterAdapterDataObserver(observer: AdapterDataObserver) {
            observers.remove(observer)
        }

        fun hasObservers(): Boolean = observers.isNotEmpty()

        fun createViewHolder(parent: ViewGroup, viewType: Int): VH {
            val holder = onCreateViewHolder(parent, viewType)
            if (holder.itemView.getParent() != null) {
                throw IllegalStateException("ViewHolder views must not be attached when created. Ensure that you are not passing 'true' to the attachToRoot parameter of LayoutInflater.inflate(..., boolean attachToRoot)")
            }
            holder.mItemViewType = viewType
            return holder
        }

        fun bindViewHolder(holder: VH, position: Int) {
            val rootBind = holder.mBindingAdapter == null
            holder.mPosition = position
            holder.mBindingAdapter = this
            if (hasStableIds) holder.mItemId = getItemId(position)
            val payloads = if (holder.mPayloads != null && holder.isBound() && !holder.isInvalid()) holder.mPayloads!! else ArrayList()
            holder.mFlags = (holder.mFlags or ViewHolder.FLAG_BOUND) and (ViewHolder.FLAG_UPDATE or ViewHolder.FLAG_INVALID).inv()
            holder.mPayloads = null
            @Suppress("UNUSED_VARIABLE") val unused = rootBind
            onBindViewHolder(holder, position, payloads)
            (holder.itemView.getLayoutParams() as? LayoutParams)?.mInsetsDirty = true
        }

        fun notifyDataSetChanged() = observers.forEach { it.onChanged() }
        fun notifyItemChanged(position: Int) = notifyItemRangeChanged(position, 1)
        fun notifyItemChanged(position: Int, payload: Any?) = notifyItemRangeChanged(position, 1, payload)
        fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) = observers.forEach { it.onItemRangeChanged(positionStart, itemCount, null) }
        fun notifyItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = observers.forEach { it.onItemRangeChanged(positionStart, itemCount, payload) }
        fun notifyItemInserted(position: Int) = notifyItemRangeInserted(position, 1)
        fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) = observers.forEach { it.onItemRangeInserted(positionStart, itemCount) }
        fun notifyItemMoved(fromPosition: Int, toPosition: Int) = observers.forEach { it.onItemRangeMoved(fromPosition, toPosition, 1) }
        fun notifyItemRemoved(position: Int) = notifyItemRangeRemoved(position, 1)
        fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) = observers.forEach { it.onItemRangeRemoved(positionStart, itemCount) }
    }

    // ============================================================================ support types

    class State {
        internal var mItemCount = 0
        internal var mIsMeasuring = false
        internal var mStructureChanged = false
        internal var mTargetPosition = NO_POSITION
        private var mData: HashMap<Int, Any?>? = null
        fun getItemCount(): Int = mItemCount
        fun isPreLayout(): Boolean = false
        fun isMeasuring(): Boolean = mIsMeasuring
        fun willRunPredictiveAnimations(): Boolean = false
        fun willRunSimpleAnimations(): Boolean = false
        fun didStructureChange(): Boolean = mStructureChanged
        fun hasTargetScrollPosition(): Boolean = mTargetPosition != NO_POSITION
        fun getTargetScrollPosition(): Int = mTargetPosition
        fun getRemainingScrollHorizontal(): Int = 0
        fun getRemainingScrollVertical(): Int = 0

        @Suppress("UNCHECKED_CAST")
        fun <T> get(resourceId: Int): T? = mData?.get(resourceId) as T?
        fun put(resourceId: Int, data: Any?) {
            (mData ?: HashMap<Int, Any?>().also { mData = it })[resourceId] = data
        }

        fun remove(resourceId: Int) {
            mData?.remove(resourceId)
        }
    }

    open class LayoutParams : ViewGroup.MarginLayoutParams {
        internal var mViewHolder: ViewHolder? = null
        internal val mDecorInsets = Rect()
        internal var mInsetsDirty = true

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: ViewGroup.LayoutParams) : super(source)
        constructor(source: LayoutParams) : super(source as ViewGroup.MarginLayoutParams)

        val viewAdapterPosition: Int get() = mViewHolder?.adapterPosition ?: NO_POSITION
        val viewLayoutPosition: Int get() = mViewHolder?.layoutPosition ?: NO_POSITION
        val absoluteAdapterPosition: Int get() = viewAdapterPosition
        val bindingAdapterPosition: Int get() = viewAdapterPosition
        val viewPosition: Int get() = viewLayoutPosition
        fun viewNeedsUpdate(): Boolean = mViewHolder?.needsUpdate() == true
        fun isViewInvalid(): Boolean = mViewHolder?.isInvalid() == true
        fun isItemRemoved(): Boolean = mViewHolder?.isRemoved() == true
        fun isItemChanged(): Boolean = false

        companion object {
            const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
            const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    abstract class ItemDecoration {
        open fun onDraw(c: Canvas, parent: RecyclerView, state: State) {}

        @Deprecated("") open fun onDraw(c: Canvas, parent: RecyclerView) {}
        open fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {}

        @Deprecated("") open fun onDrawOver(c: Canvas, parent: RecyclerView) {}
        open fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
            @Suppress("DEPRECATION")
            getItemOffsets(outRect, (view.getLayoutParams() as? LayoutParams)?.viewLayoutPosition ?: NO_POSITION, parent)
        }

        @Deprecated("") open fun getItemOffsets(outRect: Rect, itemPosition: Int, parent: RecyclerView) {
            outRect.set(0, 0, 0, 0)
        }
    }

    abstract class OnScrollListener {
        open fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {}
        open fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}
    }

    interface OnItemTouchListener {
        fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean
        fun onTouchEvent(rv: RecyclerView, e: MotionEvent)
        fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean)
    }

    open class SimpleOnItemTouchListener : OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean = false
        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    interface OnChildAttachStateChangeListener {
        fun onChildViewAttachedToWindow(view: View)
        fun onChildViewDetachedFromWindow(view: View)
    }

    abstract class OnFlingListener {
        abstract fun onFling(velocityX: Int, velocityY: Int): Boolean
    }

    fun interface RecyclerListener {
        fun onViewRecycled(holder: ViewHolder)
    }

    fun interface ChildDrawingOrderCallback {
        fun onGetChildDrawingOrder(childCount: Int, i: Int): Int
    }

    open class EdgeEffectFactory {
        companion object {
            const val DIRECTION_LEFT = 0
            const val DIRECTION_TOP = 1
            const val DIRECTION_RIGHT = 2
            const val DIRECTION_BOTTOM = 3
        }
    }

    abstract class ItemAnimator {
        companion object {
            const val FLAG_CHANGED = 2
            const val FLAG_REMOVED = 8
            const val FLAG_INVALIDATED = 4
            const val FLAG_MOVED = 2048
            const val FLAG_APPEARED_IN_PRE_LAYOUT = 4096
        }

        open class ItemHolderInfo {
            @JvmField var left = 0
            @JvmField var top = 0
            @JvmField var right = 0
            @JvmField var bottom = 0
            @JvmField var changeFlags = 0
            open fun setFrom(holder: ViewHolder): ItemHolderInfo {
                val v = holder.itemView
                left = v.getLeft(); top = v.getTop(); right = v.getRight(); bottom = v.getBottom()
                return this
            }
        }

        interface ItemAnimatorFinishedListener {
            fun onAnimationsFinished()
        }

        private var addDuration = 120L
        private var removeDuration = 120L
        private var moveDuration = 250L
        private var changeDuration = 250L
        open fun getAddDuration(): Long = addDuration
        open fun setAddDuration(addDuration: Long) {
            this.addDuration = addDuration
        }

        open fun getRemoveDuration(): Long = removeDuration
        open fun setRemoveDuration(removeDuration: Long) {
            this.removeDuration = removeDuration
        }

        open fun getMoveDuration(): Long = moveDuration
        open fun setMoveDuration(moveDuration: Long) {
            this.moveDuration = moveDuration
        }

        open fun getChangeDuration(): Long = changeDuration
        open fun setChangeDuration(changeDuration: Long) {
            this.changeDuration = changeDuration
        }

        open fun isRunning(): Boolean = false
        open fun isRunning(listener: ItemAnimatorFinishedListener?): Boolean {
            listener?.onAnimationsFinished()
            return false
        }

        open fun endAnimation(item: ViewHolder) {}
        open fun endAnimations() {}
        open fun runPendingAnimations() {}
        open fun canReuseUpdatedViewHolder(viewHolder: ViewHolder): Boolean = true
    }

    open class RecycledViewPool {
        private class ScrapData {
            val scrap = ArrayList<ViewHolder>()
            var max = 5
        }

        private val mScrap = HashMap<Int, ScrapData>()
        private fun data(type: Int) = mScrap.getOrPut(type) { ScrapData() }

        open fun clear() = mScrap.values.forEach { it.scrap.clear() }
        open fun setMaxRecycledViews(viewType: Int, max: Int) {
            val d = data(viewType)
            d.max = max
            while (d.scrap.size > max) d.scrap.removeAt(d.scrap.size - 1)
        }

        open fun getRecycledViewCount(viewType: Int): Int = mScrap[viewType]?.scrap?.size ?: 0
        open fun getRecycledView(viewType: Int): ViewHolder? {
            val d = mScrap[viewType] ?: return null
            for (i in d.scrap.indices.reversed()) {
                val h = d.scrap[i]
                d.scrap.removeAt(i)
                return h
            }
            return null
        }

        open fun putRecycledView(scrap: ViewHolder) {
            val d = data(scrap.itemViewType)
            if (d.max <= d.scrap.size) return
            scrap.mPosition = NO_POSITION
            scrap.mOldPosition = NO_POSITION
            scrap.mItemId = NO_ID
            scrap.mFlags = 0
            scrap.mPayloads = null
            scrap.mOwnerRecyclerView = null
            d.scrap.add(scrap)
        }
    }

    /** Recycler: scrap (detached during a layout), a small cache of valid views and the pool */
    inner class Recycler {
        internal val mAttachedScrap = ArrayList<ViewHolder>()
        internal val mCachedViews = ArrayList<ViewHolder>()
        internal var mViewCacheMax = DEFAULT_CACHE_SIZE

        fun setViewCacheSize(viewCount: Int) {
            mViewCacheMax = viewCount
            while (mCachedViews.size > max(0, viewCount)) recycleCachedViewAt(mCachedViews.size - 1)
        }

        fun getScrapList(): List<ViewHolder> = mAttachedScrap
        fun convertPreLayoutPositionToPostLayout(position: Int): Int = position
        fun getViewForPosition(position: Int): View = tryGetViewHolderForPosition(position).itemView

        internal fun tryGetViewHolderForPosition(position: Int): ViewHolder {
            val adapter = mAdapter ?: throw IllegalStateException("No adapter attached")
            if (position < 0 || position >= adapter.getItemCount()) {
                throw IndexOutOfBoundsException("Invalid item position $position($position). Item count:${adapter.getItemCount()} $this")
            }
            val type = adapter.getItemViewType(position)
            val stableId = if (adapter.hasStableIds()) adapter.getItemId(position) else NO_ID
            var holder: ViewHolder? = null
            // 1: scrap and cache by position (or stable id)
            for (list in arrayOf(mAttachedScrap, mCachedViews)) {
                val idx = list.indexOfFirst { h ->
                    !h.isRemoved() && h.itemViewType == type &&
                        (if (adapter.hasStableIds()) h.mItemId == stableId else !h.isInvalid() && h.mPosition == position)
                }
                if (idx >= 0) {
                    holder = list.removeAt(idx)
                    holder.mScrap = false
                    break
                }
            }
            // 2: the pool, then a new holder
            if (holder == null) {
                holder = getRecycledViewPool().getRecycledView(type)
                if (holder == null) {
                    @Suppress("UNCHECKED_CAST")
                    holder = (adapter as Adapter<ViewHolder>).createViewHolder(this@RecyclerView, type)
                }
            }
            holder.mOwnerRecyclerView = this@RecyclerView
            if (!holder.isBound() || holder.needsUpdate() || holder.isInvalid() || holder.mPosition != position) {
                @Suppress("UNCHECKED_CAST")
                (adapter as Adapter<ViewHolder>).bindViewHolder(holder, position)
            }
            holder.mPosition = position
            val lp = holder.itemView.getLayoutParams()
            // like AOSP: params the layout manager does not accept (GridLayoutManager needs its own) are
            // converted here, before attaching, so the holder link survives addView
            val accepted = lp != null && this@RecyclerView.checkLayoutParams(lp)
            val rvLp = when {
                lp == null -> generateDefaultLayoutParams() as LayoutParams
                accepted -> lp as LayoutParams
                else -> generateLayoutParams(lp) as LayoutParams
            }
            rvLp.mViewHolder = holder
            if (rvLp !== lp) holder.itemView.setLayoutParamsSilently(rvLp)
            return holder
        }

        fun recycleView(view: View) {
            val holder = getChildViewHolderInt(view) ?: return
            if (view.getParent() === this@RecyclerView) removeDetachedView(view, false)
            recycleViewHolderInternal(holder)
        }

        internal fun scrapView(holder: ViewHolder) {
            holder.mScrap = true
            mAttachedScrap.add(holder)
        }

        internal fun recycleViewHolderInternal(holder: ViewHolder) {
            holder.mScrap = false
            if (!holder.isRecyclable()) return
            if (mViewCacheMax > 0 && !holder.isInvalid() && !holder.isRemoved() && !holder.needsUpdate()) {
                if (mCachedViews.size >= mViewCacheMax && mCachedViews.isNotEmpty()) recycleCachedViewAt(0)
                if (mCachedViews.size < mViewCacheMax) {
                    mCachedViews.add(holder)
                    return
                }
            }
            addToPool(holder)
        }

        private fun recycleCachedViewAt(index: Int) = addToPool(mCachedViews.removeAt(index))

        internal fun addToPool(holder: ViewHolder) {
            mRecyclerListeners.forEach { it.onViewRecycled(holder) }
            @Suppress("UNCHECKED_CAST")
            (holder.mBindingAdapter as? Adapter<ViewHolder>)?.onViewRecycled(holder)
            getRecycledViewPool().putRecycledView(holder)
        }

        internal fun clearScrap() {
            for (h in mAttachedScrap.toList()) {
                // scrap left over after a layout is detached (no parent): remove it for good, like AOSP
                if (h.itemView.getParent() === this@RecyclerView || h.itemView.getParent() == null) removeDetachedView(h.itemView, false)
                recycleViewHolderInternal(h)
            }
            mAttachedScrap.clear()
        }

        internal fun recycleAndClearCachedViews() {
            while (mCachedViews.isNotEmpty()) recycleCachedViewAt(mCachedViews.size - 1)
        }

        fun clear() {
            mAttachedScrap.clear()
            recycleAndClearCachedViews()
        }
    }

    // ============================================================================ LayoutManager

    abstract class LayoutManager {
        internal var mRecyclerView: RecyclerView? = null
        private var mWidth = 0
        private var mHeight = 0
        private var mWidthMode = View.MeasureSpec.EXACTLY
        private var mHeightMode = View.MeasureSpec.EXACTLY
        private var mAutoMeasure = true
        private var mSmoothScroller: SmoothScroller? = null

        interface LayoutPrefetchRegistry {
            fun addPosition(layoutPosition: Int, pixelDistance: Int)
        }

        class Properties {
            @JvmField var orientation = VERTICAL
            @JvmField var spanCount = 1
            @JvmField var reverseLayout = false
            @JvmField var stackFromEnd = false
        }

        companion object {
            @JvmStatic
            fun getProperties(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int): Properties {
                val p = Properties()
                if (attrs == null) return p
                for (i in 0 until attrs.getAttributeCount()) {
                    if (com.lagradost.desktop.runtime.res.ResourceSupport.isToolsAttribute(attrs, i)) continue
                    val raw = attrs.getAttributeValue(i) ?: continue
                    val value = if (raw.startsWith("@")) {
                        val id = context.getResources().getIdentifier(raw.removePrefix("@"), null, null)
                        if (id == 0) raw else runCatching { context.getResources().getString(id) }
                            .recoverCatching { context.getResources().getInteger(id).toString() }.getOrDefault(raw)
                    } else raw
                    when (attrs.getAttributeName(i).substringAfter(':')) {
                        "orientation" -> p.orientation = if (value == "horizontal" || value == "0") HORIZONTAL else VERTICAL
                        "spanCount" -> value.toIntOrNull()?.let { p.spanCount = maxOf(1, it) }
                        "reverseLayout" -> p.reverseLayout = value == "true"
                        "stackFromEnd" -> p.stackFromEnd = value == "true"
                    }
                }
                return p
            }

            @JvmStatic
            fun chooseSize(spec: Int, desired: Int, min: Int): Int {
                val mode = View.MeasureSpec.getMode(spec)
                val size = View.MeasureSpec.getSize(spec)
                return when (mode) {
                    View.MeasureSpec.EXACTLY -> size
                    View.MeasureSpec.AT_MOST -> min(size, max(desired, min))
                    else -> max(desired, min)
                }
            }

            /** LayoutManager.getChildMeasureSpec: wrap_content items are unbounded in the scrolling direction */
            @JvmStatic
            fun getChildMeasureSpec(parentSize: Int, parentMode: Int, padding: Int, childDimension: Int, canScroll: Boolean): Int {
                val size = max(0, parentSize - padding)
                var resultSize = 0
                var resultMode = View.MeasureSpec.UNSPECIFIED
                if (canScroll) {
                    if (childDimension >= 0) {
                        resultSize = childDimension
                        resultMode = View.MeasureSpec.EXACTLY
                    } else if (childDimension == ViewGroup.LayoutParams.MATCH_PARENT) {
                        if (parentMode == View.MeasureSpec.AT_MOST || parentMode == View.MeasureSpec.EXACTLY) {
                            resultSize = size
                            resultMode = parentMode
                        }
                    }
                } else {
                    if (childDimension >= 0) {
                        resultSize = childDimension
                        resultMode = View.MeasureSpec.EXACTLY
                    } else if (childDimension == ViewGroup.LayoutParams.MATCH_PARENT) {
                        resultSize = size
                        resultMode = parentMode
                    } else if (childDimension == ViewGroup.LayoutParams.WRAP_CONTENT) {
                        resultSize = size
                        resultMode = if (parentMode == View.MeasureSpec.AT_MOST || parentMode == View.MeasureSpec.EXACTLY) View.MeasureSpec.AT_MOST else View.MeasureSpec.UNSPECIFIED
                    }
                }
                return View.MeasureSpec.makeMeasureSpec(resultSize, resultMode)
            }

            @JvmStatic
            @Deprecated("")
            fun getChildMeasureSpec(parentSize: Int, padding: Int, childDimension: Int, canScroll: Boolean): Int =
                getChildMeasureSpec(parentSize, View.MeasureSpec.EXACTLY, padding, childDimension, canScroll)
        }

        internal fun setRecyclerView(rv: RecyclerView?) {
            mRecyclerView = rv
            if (rv == null) {
                mWidth = 0
                mHeight = 0
            } else {
                mWidth = rv.getWidth()
                mHeight = rv.getHeight()
            }
            mWidthMode = View.MeasureSpec.EXACTLY
            mHeightMode = View.MeasureSpec.EXACTLY
        }

        internal fun setMeasureSpecs(wSpec: Int, hSpec: Int) {
            mWidth = View.MeasureSpec.getSize(wSpec)
            mWidthMode = View.MeasureSpec.getMode(wSpec)
            if (mWidthMode == View.MeasureSpec.UNSPECIFIED) mWidth = 0
            mHeight = View.MeasureSpec.getSize(hSpec)
            mHeightMode = View.MeasureSpec.getMode(hSpec)
            if (mHeightMode == View.MeasureSpec.UNSPECIFIED) mHeight = 0
        }

        internal fun setExactMeasureSpecsFrom(rv: RecyclerView) {
            setMeasureSpecs(View.MeasureSpec.makeMeasureSpec(rv.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(rv.getHeight(), View.MeasureSpec.EXACTLY))
        }

        /** LayoutManager.setMeasuredDimensionFromChildren (auto measure) */
        internal fun setMeasuredDimensionFromChildren(widthSpec: Int, heightSpec: Int) {
            val count = getChildCount()
            if (count == 0) {
                mRecyclerView?.defaultOnMeasure(widthSpec, heightSpec)
                return
            }
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            val r = Rect()
            for (i in 0 until count) {
                val child = getChildAt(i) ?: continue
                getDecoratedBoundsWithMargins(child, r)
                minX = min(minX, r.left)
                maxX = max(maxX, r.right)
                minY = min(minY, r.top)
                maxY = max(maxY, r.bottom)
            }
            val rv = mRecyclerView ?: return
            val usedW = maxX - minX + getPaddingLeft() + getPaddingRight()
            val usedH = maxY - minY + getPaddingTop() + getPaddingBottom()
            rv.setMeasuredDimensionPublic(chooseSize(widthSpec, usedW, rv.getMinimumWidth()), chooseSize(heightSpec, usedH, rv.getMinimumHeight()))
        }

        open fun isAutoMeasureEnabled(): Boolean = mAutoMeasure

        @Deprecated("")
        open fun setAutoMeasureEnabled(enabled: Boolean) {
            mAutoMeasure = enabled
        }

        open fun onMeasure(recycler: Recycler, state: State, widthSpec: Int, heightSpec: Int) {
            mRecyclerView?.defaultOnMeasure(widthSpec, heightSpec)
        }

        open fun setMeasuredDimension(widthSize: Int, heightSize: Int) {
            mRecyclerView?.setMeasuredDimensionPublic(widthSize, heightSize)
        }

        open fun shouldMeasureTwice(): Boolean = false

        open fun getWidth(): Int = mWidth
        open fun getHeight(): Int = mHeight
        open fun getWidthMode(): Int = mWidthMode
        open fun getHeightMode(): Int = mHeightMode
        open fun getPaddingLeft(): Int = mRecyclerView?.getPaddingLeft() ?: 0
        open fun getPaddingTop(): Int = mRecyclerView?.getPaddingTop() ?: 0
        open fun getPaddingRight(): Int = mRecyclerView?.getPaddingRight() ?: 0
        open fun getPaddingBottom(): Int = mRecyclerView?.getPaddingBottom() ?: 0
        open fun getPaddingStart(): Int = getPaddingLeft()
        open fun getPaddingEnd(): Int = getPaddingRight()
        open fun getClipToPadding(): Boolean = mRecyclerView?.getClipToPadding() ?: true
        open fun getLayoutDirection(): Int = View.LAYOUT_DIRECTION_LTR
        open fun isAttachedToWindow(): Boolean = mRecyclerView?.isAttachedToWindow() == true
        open fun isLayoutRequested(): Boolean = mRecyclerView?.isLayoutRequested() == true
        open fun requestLayout() {
            mRecyclerView?.requestLayout()
        }

        open fun assertNotInLayoutOrScroll(message: String?) {}
        open fun isFocused(): Boolean = false
        open fun hasFocus(): Boolean = false
        open fun getFocusedChild(): View? = null
        open fun getMinimumWidth(): Int = mRecyclerView?.getMinimumWidth() ?: 0
        open fun getMinimumHeight(): Int = mRecyclerView?.getMinimumHeight() ?: 0

        open fun canScrollHorizontally(): Boolean = false
        open fun canScrollVertically(): Boolean = false
        open fun scrollHorizontallyBy(dx: Int, recycler: Recycler, state: State): Int = 0
        open fun scrollVerticallyBy(dy: Int, recycler: Recycler, state: State): Int = 0
        open fun scrollToPosition(position: Int) {}
        open fun smoothScrollToPosition(recyclerView: RecyclerView, state: State, position: Int) {}
        open fun startSmoothScroll(smoothScroller: SmoothScroller) {
            mSmoothScroller?.stop()
            mSmoothScroller = smoothScroller
            val rv = mRecyclerView ?: return
            smoothScroller.start(rv, this)
        }

        open fun isSmoothScrolling(): Boolean = mSmoothScroller?.isRunning() == true
        internal fun stopSmoothScroller() {
            mSmoothScroller?.stop()
        }

        open fun supportsPredictiveItemAnimations(): Boolean = false
        open fun setItemPrefetchEnabled(enabled: Boolean) {}
        open fun isItemPrefetchEnabled(): Boolean = false
        open fun collectAdjacentPrefetchPositions(dx: Int, dy: Int, state: State, layoutPrefetchRegistry: LayoutPrefetchRegistry) {}
        open fun collectInitialPrefetchPositions(adapterItemCount: Int, layoutPrefetchRegistry: LayoutPrefetchRegistry) {}
        open fun onLayoutChildren(recycler: Recycler, state: State) {}
        open fun onLayoutCompleted(state: State?) {}
        open fun onAdapterChanged(oldAdapter: Adapter<*>?, newAdapter: Adapter<*>?) {}
        open fun onItemsChanged(recyclerView: RecyclerView) {}
        open fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {}
        open fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {}
        open fun onItemsUpdated(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {}
        open fun onItemsMoved(recyclerView: RecyclerView, from: Int, to: Int, itemCount: Int) {}
        open fun onAttachedToWindow(view: RecyclerView) {}
        open fun onDetachedFromWindow(view: RecyclerView, recycler: Recycler) {}
        open fun onScrollStateChanged(state: Int) {}
        open fun onSaveInstanceState(): android.os.Parcelable? = null
        open fun onRestoreInstanceState(state: android.os.Parcelable?) {}

        abstract fun generateDefaultLayoutParams(): LayoutParams
        open fun generateLayoutParams(lp: ViewGroup.LayoutParams): LayoutParams = when (lp) {
            is LayoutParams -> LayoutParams(lp)
            is ViewGroup.MarginLayoutParams -> LayoutParams(lp)
            else -> LayoutParams(lp)
        }

        open fun generateLayoutParams(c: Context, attrs: AttributeSet?): LayoutParams = LayoutParams(c, attrs)
        open fun checkLayoutParams(lp: LayoutParams?): Boolean = lp != null

        // children
        open fun getChildCount(): Int = mRecyclerView?.getChildCount() ?: 0
        open fun getChildAt(index: Int): View? = mRecyclerView?.getChildAt(index)
        open fun getItemCount(): Int = mRecyclerView?.getAdapter()?.getItemCount() ?: 0
        open fun getPosition(view: View): Int = (view.getLayoutParams() as? LayoutParams)?.viewLayoutPosition ?: NO_POSITION
        open fun getItemViewType(view: View): Int = getChildViewHolderInt(view)?.itemViewType ?: INVALID_TYPE
        open fun findViewByPosition(position: Int): View? {
            for (i in 0 until getChildCount()) {
                val child = getChildAt(i) ?: continue
                val h = getChildViewHolderInt(child) ?: continue
                if (h.layoutPosition == position && !h.isRemoved()) return child
            }
            return null
        }

        open fun findContainingItemView(view: View): View? = mRecyclerView?.findContainingItemView(view)

        open fun addView(child: View) = addView(child, -1)
        open fun addView(child: View, index: Int) {
            val rv = mRecyclerView ?: return
            val holder = getChildViewHolderInt(child) ?: return
            val lp = child.getLayoutParams() as? LayoutParams ?: return
            if (child.getParent() === rv) {
                val current = rv.indexOfChild(child)
                val target = if (index < 0) rv.getChildCount() - 1 else index
                if (current != target) {
                    rv.detachViewFromParentPublic(current)
                    rv.attachViewToParentPublic(child, target, lp)
                }
            } else if (holder.mScrap) {
                holder.mScrap = false
                rv.recycler.mAttachedScrap.remove(holder)
                rv.attachViewToParentPublic(child, index, lp)
            } else {
                rv.addViewInLayoutPublic(child, index, lp)
                rv.dispatchChildAttached(child)
            }
        }

        open fun addDisappearingView(child: View) = addView(child)
        open fun addDisappearingView(child: View, index: Int) = addView(child, index)

        open fun removeView(child: View) {
            mRecyclerView?.removeViewPublic(child)
        }

        open fun removeViewAt(index: Int) {
            val child = getChildAt(index) ?: return
            removeView(child)
        }

        open fun removeAllViews() {
            for (i in getChildCount() - 1 downTo 0) removeViewAt(i)
        }

        open fun detachView(child: View) {
            val rv = mRecyclerView ?: return
            val i = rv.indexOfChild(child)
            if (i >= 0) rv.detachViewFromParentPublic(i)
        }

        open fun detachViewAt(index: Int) {
            mRecyclerView?.detachViewFromParentPublic(index)
        }

        open fun attachView(child: View, index: Int, lp: LayoutParams) {
            mRecyclerView?.attachViewToParentPublic(child, index, lp)
        }

        open fun attachView(child: View, index: Int) = attachView(child, index, child.getLayoutParams() as LayoutParams)
        open fun attachView(child: View) = attachView(child, -1)
        open fun removeDetachedView(child: View) {
            mRecyclerView?.removeDetachedViewPublic(child)
        }

        open fun moveView(fromIndex: Int, toIndex: Int) {
            val child = getChildAt(fromIndex) ?: return
            detachViewAt(fromIndex)
            attachView(child, toIndex)
        }

        open fun detachAndScrapView(child: View, recycler: Recycler) {
            val rv = mRecyclerView ?: return
            val i = rv.indexOfChild(child)
            if (i >= 0) scrapOrRecycleView(recycler, i, child)
        }

        open fun detachAndScrapViewAt(index: Int, recycler: Recycler) {
            val child = getChildAt(index) ?: return
            scrapOrRecycleView(recycler, index, child)
        }

        open fun removeAndRecycleView(child: View, recycler: Recycler) {
            removeView(child)
            val h = getChildViewHolderInt(child) ?: return
            recycler.recycleViewHolderInternal(h)
        }

        open fun removeAndRecycleViewAt(index: Int, recycler: Recycler) {
            val child = getChildAt(index) ?: return
            removeAndRecycleView(child, recycler)
        }

        open fun removeAndRecycleAllViews(recycler: Recycler) {
            for (i in getChildCount() - 1 downTo 0) removeAndRecycleViewAt(i, recycler)
        }

        open fun detachAndScrapAttachedViews(recycler: Recycler) {
            for (i in getChildCount() - 1 downTo 0) {
                val child = getChildAt(i) ?: continue
                scrapOrRecycleView(recycler, i, child)
            }
        }

        private fun scrapOrRecycleView(recycler: Recycler, index: Int, view: View) {
            val holder = getChildViewHolderInt(view) ?: return
            val rv = mRecyclerView ?: return
            if (holder.isInvalid() && !holder.isRemoved() && rv.getAdapter()?.hasStableIds() != true) {
                removeViewAt(index)
                recycler.recycleViewHolderInternal(holder)
            } else {
                detachViewAt(index)
                recycler.scrapView(holder)
            }
        }

        internal fun removeAndRecycleScrapInt(recycler: Recycler) = recycler.clearScrap()

        // measuring and positioning
        open fun measureChild(child: View, widthUsed: Int, heightUsed: Int) {
            val lp = child.getLayoutParams() as LayoutParams
            val insets = mRecyclerView!!.getItemDecorInsetsForChild(child)
            val widthSpec = getChildMeasureSpec(getWidth(), getWidthMode(), getPaddingLeft() + getPaddingRight() + widthUsed + insets.left + insets.right, lp.width, canScrollHorizontally())
            val heightSpec = getChildMeasureSpec(getHeight(), getHeightMode(), getPaddingTop() + getPaddingBottom() + heightUsed + insets.top + insets.bottom, lp.height, canScrollVertically())
            child.measure(widthSpec, heightSpec)
        }

        open fun measureChildWithMargins(child: View, widthUsed: Int, heightUsed: Int) {
            val lp = child.getLayoutParams() as LayoutParams
            val insets = mRecyclerView!!.getItemDecorInsetsForChild(child)
            val widthSpec = getChildMeasureSpec(
                getWidth(), getWidthMode(),
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed + insets.left + insets.right, lp.width, canScrollHorizontally()
            )
            val heightSpec = getChildMeasureSpec(
                getHeight(), getHeightMode(),
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + heightUsed + insets.top + insets.bottom, lp.height, canScrollVertically()
            )
            child.measure(widthSpec, heightSpec)
        }

        open fun layoutDecorated(child: View, left: Int, top: Int, right: Int, bottom: Int) {
            val insets = (child.getLayoutParams() as LayoutParams).mDecorInsets
            child.layout(left + insets.left, top + insets.top, right - insets.right, bottom - insets.bottom)
        }

        open fun layoutDecoratedWithMargins(child: View, left: Int, top: Int, right: Int, bottom: Int) {
            val lp = child.getLayoutParams() as LayoutParams
            val insets = lp.mDecorInsets
            child.layout(left + insets.left + lp.leftMargin, top + insets.top + lp.topMargin, right - insets.right - lp.rightMargin, bottom - insets.bottom - lp.bottomMargin)
        }

        open fun getDecoratedMeasuredWidth(child: View): Int = child.getMeasuredWidth() + (child.getLayoutParams() as LayoutParams).mDecorInsets.let { it.left + it.right }
        open fun getDecoratedMeasuredHeight(child: View): Int = child.getMeasuredHeight() + (child.getLayoutParams() as LayoutParams).mDecorInsets.let { it.top + it.bottom }
        open fun getDecoratedLeft(child: View): Int = child.getLeft() - getLeftDecorationWidth(child)
        open fun getDecoratedTop(child: View): Int = child.getTop() - getTopDecorationHeight(child)
        open fun getDecoratedRight(child: View): Int = child.getRight() + getRightDecorationWidth(child)
        open fun getDecoratedBottom(child: View): Int = child.getBottom() + getBottomDecorationHeight(child)
        open fun getLeftDecorationWidth(child: View): Int = (child.getLayoutParams() as LayoutParams).mDecorInsets.left
        open fun getTopDecorationHeight(child: View): Int = (child.getLayoutParams() as LayoutParams).mDecorInsets.top
        open fun getRightDecorationWidth(child: View): Int = (child.getLayoutParams() as LayoutParams).mDecorInsets.right
        open fun getBottomDecorationHeight(child: View): Int = (child.getLayoutParams() as LayoutParams).mDecorInsets.bottom
        open fun calculateItemDecorationsForChild(child: View, outRect: Rect) {
            outRect.set(mRecyclerView?.getItemDecorInsetsForChild(child) ?: Rect())
        }

        open fun getDecoratedBoundsWithMargins(view: View, outBounds: Rect) {
            val lp = view.getLayoutParams() as LayoutParams
            val insets = lp.mDecorInsets
            outBounds.set(
                view.getLeft() - insets.left - lp.leftMargin, view.getTop() - insets.top - lp.topMargin,
                view.getRight() + insets.right + lp.rightMargin, view.getBottom() + insets.bottom + lp.bottomMargin
            )
        }

        open fun getTransformedBoundingBox(child: View, includeDecorInsets: Boolean, out: Rect) {
            val insets = if (includeDecorInsets) (child.getLayoutParams() as LayoutParams).mDecorInsets else Rect()
            out.set(child.getLeft() - insets.left, child.getTop() - insets.top, child.getRight() + insets.right, child.getBottom() + insets.bottom)
        }

        open fun offsetChildrenHorizontal(dx: Int) {
            mRecyclerView?.offsetChildrenHorizontal(dx)
        }

        open fun offsetChildrenVertical(dy: Int) {
            mRecyclerView?.offsetChildrenVertical(dy)
        }

        open fun computeHorizontalScrollOffset(state: State): Int = 0
        open fun computeHorizontalScrollExtent(state: State): Int = 0
        open fun computeHorizontalScrollRange(state: State): Int = 0
        open fun computeVerticalScrollOffset(state: State): Int = 0
        open fun computeVerticalScrollExtent(state: State): Int = 0
        open fun computeVerticalScrollRange(state: State): Int = 0
        open fun isViewPartiallyVisible(child: View, completelyVisible: Boolean, acceptEndPointInclusion: Boolean): Boolean {
            val rv = mRecyclerView ?: return false
            val left = getPaddingLeft()
            val top = getPaddingTop()
            val right = getWidth() - getPaddingRight()
            val bottom = getHeight() - getPaddingBottom()
            val r = Rect()
            getDecoratedBoundsWithMargins(child, r)
            @Suppress("UNUSED_VARIABLE") val unused = rv
            val h = r.left < right && r.right > left
            val v = r.top < bottom && r.bottom > top
            return if (completelyVisible) r.left >= left && r.right <= right && r.top >= top && r.bottom <= bottom else h && v
        }

        open fun ignoreView(view: View) {}
        open fun stopIgnoringView(view: View) {}
        open fun onInterceptFocusSearch(focused: View, direction: Int): View? = null
        open fun onFocusSearchFailed(focused: View, direction: Int, recycler: Recycler, state: State): View? = null
        open fun onRequestChildFocus(parent: RecyclerView, child: View, focused: View?): Boolean = false
        open fun onRequestChildFocus(parent: RecyclerView, state: State, child: View, focused: View?): Boolean = false
        open fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean): Boolean = false
        open fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean): Boolean = requestChildRectangleOnScreen(parent, child, rect, immediate)
        open fun getRowCountForAccessibility(recycler: Recycler, state: State): Int = 1
        open fun getColumnCountForAccessibility(recycler: Recycler, state: State): Int = 1
        open fun getBaseline(): Int = -1
        open fun endAnimation(view: View) {}
    }

    // ============================================================================ smooth scrolling

    abstract class SmoothScroller {
        private var mTargetPosition = NO_POSITION
        private var mRecyclerView: RecyclerView? = null
        private var mLayoutManager: LayoutManager? = null
        private var mRunning = false
        private var mGeneration = 0

        class Action(var dx: Int, var dy: Int, var duration: Int = UNDEFINED_DURATION, var interpolator: android.view.animation.Interpolator? = null) {
            fun update(dx: Int, dy: Int, duration: Int, interpolator: android.view.animation.Interpolator?) {
                this.dx = dx
                this.dy = dy
                this.duration = duration
                this.interpolator = interpolator
            }

            fun jumpTo(targetPosition: Int) {}        }

        interface ScrollVectorProvider {
            fun computeScrollVectorForPosition(targetPosition: Int): android.graphics.PointF?
        }

        fun setTargetPosition(targetPosition: Int) {
            mTargetPosition = targetPosition
        }

        fun getTargetPosition(): Int = mTargetPosition
        fun getLayoutManager(): LayoutManager? = mLayoutManager
        fun isRunning(): Boolean = mRunning
        fun isPendingInitialRun(): Boolean = false
        fun getChildCount(): Int = mRecyclerView?.getChildCount() ?: 0
        fun getChildPosition(view: View): Int = mRecyclerView?.getChildLayoutPosition(view) ?: NO_POSITION
        fun findViewByPosition(position: Int): View? = mLayoutManager?.findViewByPosition(position)

        protected abstract fun onStart()
        protected abstract fun onStop()
        protected abstract fun onSeekTargetStep(dx: Int, dy: Int, state: State, action: Action)
        protected abstract fun onTargetFound(targetView: View, state: State, action: Action)

        internal fun start(rv: RecyclerView, lm: LayoutManager) {
            mRecyclerView = rv
            mLayoutManager = lm
            mRunning = true
            val gen = ++mGeneration
            rv.setScrollStateInternal(SCROLL_STATE_SETTLING)
            onStart()
            val handler = Handler(Looper.getMainLooper())
            var steps = 0
            val step = object : Runnable {
                override fun run() {
                    if (gen != mGeneration || !mRunning) return
                    val target = findViewByPosition(mTargetPosition)
                    val action = Action(0, 0)
                    if (target != null) {
                        onTargetFound(target, rv.mState, action)
                        rv.animateScrollBy(action.dx, action.dy, if (action.duration > 0) action.duration else 250) { stop() }
                        return
                    }
                    onSeekTargetStep(0, 0, rv.mState, action)
                    if ((action.dx == 0 && action.dy == 0) || ++steps > 400) {
                        // the target can't be reached by scrolling: jump
                        rv.scrollToPosition(mTargetPosition)
                        stop()
                        return
                    }
                    rv.scrollByInternal(action.dx, action.dy)
                    handler.postDelayed(this, 16)
                }
            }
            handler.post(step)
        }

        fun stop() {
            if (!mRunning) return
            mRunning = false
            mGeneration++
            onStop()
            mRecyclerView?.setScrollStateInternal(SCROLL_STATE_IDLE)
            mTargetPosition = NO_POSITION
        }
    }

    // ============================================================================ the view

    private var mAdapter: Adapter<*>? = null
    private var mLayout: LayoutManager? = null
    private val mItemDecorations = ArrayList<ItemDecoration>()
    private val mScrollListeners = java.util.concurrent.CopyOnWriteArrayList<OnScrollListener>()
    private var mScrollListener: OnScrollListener? = null
    private val mOnItemTouchListeners = ArrayList<OnItemTouchListener>()
    private val mChildAttachListeners = ArrayList<OnChildAttachStateChangeListener>()
    internal val mRecyclerListeners = ArrayList<RecyclerListener>()
    private var mItemAnimator: ItemAnimator? = DefaultItemAnimator()
    private var mRecycledViewPool: RecycledViewPool? = null
    private var mHasFixedSize = false
    private var mOnFlingListener: OnFlingListener? = null
    private var mScrollState = SCROLL_STATE_IDLE
    private var mInLayout = false
    private var mLayoutFrozen = false
    private val mObserver = DataObserver()
    internal val recycler = Recycler()
    internal val mState = State()
    private var mPendingUpdateLayout = false

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        createLayoutManager(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        createLayoutManager(attrs)
    }

    /** AOSP createLayoutManager: app:layoutManager names the class, built with its attrs constructor */
    private fun createLayoutManager(attrs: AttributeSet?) {
        attrs ?: return
        var name: String? = null
        for (i in 0 until attrs.getAttributeCount()) {
            if (com.lagradost.desktop.runtime.res.ResourceSupport.isToolsAttribute(attrs, i)) continue
            if (attrs.getAttributeName(i).substringAfter(':') == "layoutManager") name = attrs.getAttributeValue(i)
        }
        name = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val className = when {
            name.startsWith(".") -> getContext().getPackageName() + name
            name.contains('.') -> name
            else -> "androidx.recyclerview.widget.$name"
        }
        try {
            val cls = Class.forName(className, true, com.lagradost.desktop.runtime.ui.WidgetFactory::class.java.classLoader).asSubclass(LayoutManager::class.java)
            val lm = try {
                cls.getConstructor(Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .newInstance(getContext(), attrs, 0, 0)
            } catch (e: NoSuchMethodException) {
                cls.getConstructor().newInstance()
            }
            setLayoutManager(lm)
        } catch (e: Exception) {
            throw IllegalStateException("Unable to instantiate LayoutManager $className", e)
        }
    }

    init {
        setFocusableInTouchMode(true)
    }

    private inner class DataObserver : AdapterDataObserver() {
        override fun onChanged() {
            mState.mStructureChanged = true
            forEachHolder { it.mFlags = it.mFlags or ViewHolder.FLAG_INVALID or ViewHolder.FLAG_UPDATE }
            recycler.recycleAndClearCachedViews()
            mLayout?.onItemsChanged(this@RecyclerView)
            requestLayout()
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            forEachHolder { h ->
                if (h.mPosition >= positionStart && h.mPosition < positionStart + itemCount) h.addPayload(payload)
            }
            mLayout?.onItemsUpdated(this@RecyclerView, positionStart, itemCount)
            triggerUpdate(structural = false)
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            forEachHolder { h -> if (h.mPosition >= positionStart) h.mPosition += itemCount }
            mState.mStructureChanged = true
            mLayout?.onItemsAdded(this@RecyclerView, positionStart, itemCount)
            triggerUpdate(structural = true)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            forEachHolder { h ->
                if (h.mPosition >= positionStart + itemCount) h.mPosition -= itemCount
                else if (h.mPosition >= positionStart) {
                    h.mFlags = h.mFlags or ViewHolder.FLAG_REMOVED
                    h.mPosition = NO_POSITION
                }
            }
            recycler.mCachedViews.removeAll { h ->
                if (h.isRemoved()) {
                    recycler.addToPool(h)
                    true
                } else false
            }
            mState.mStructureChanged = true
            mLayout?.onItemsRemoved(this@RecyclerView, positionStart, itemCount)
            triggerUpdate(structural = true)
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            forEachHolder { h ->
                val p = h.mPosition
                if (p == fromPosition) h.mPosition = toPosition
                else if (fromPosition < toPosition && p > fromPosition && p <= toPosition) h.mPosition--
                else if (fromPosition > toPosition && p >= toPosition && p < fromPosition) h.mPosition++
            }
            mState.mStructureChanged = true
            mLayout?.onItemsMoved(this@RecyclerView, fromPosition, toPosition, itemCount)
            triggerUpdate(structural = true)
        }
    }

    private fun forEachHolder(block: (ViewHolder) -> Unit) {
        for (i in 0 until getChildCount()) getChildAt(i)?.let { getChildViewHolderInt(it) }?.let(block)
        recycler.mCachedViews.forEach(block)
        recycler.mAttachedScrap.forEach(block)
    }

    /** Fixed size adapters change in place without asking the parents for a layout, like AndroidX */
    private fun triggerUpdate(structural: Boolean) {
        if (mHasFixedSize && isAttachedToWindow() && !structural) {
            if (!mPendingUpdateLayout) {
                mPendingUpdateLayout = true
                post {
                    if (mPendingUpdateLayout) {
                        mPendingUpdateLayout = false
                        if (!isLayoutRequested()) dispatchLayout()
                    }
                }
            }
        } else requestLayout()
    }

    // ------------------------------------------------------------------ adapter / layout manager

    open fun getAdapter(): Adapter<*>? = mAdapter
    open fun setAdapter(adapter: Adapter<*>?) = setAdapterInternal(adapter, removeAndRecycleViews = true)
    open fun swapAdapter(adapter: Adapter<*>?, removeAndRecycleExistingViews: Boolean) = setAdapterInternal(adapter, removeAndRecycleExistingViews)

    private fun setAdapterInternal(adapter: Adapter<*>?, removeAndRecycleViews: Boolean) {
        val old = mAdapter
        stopScroll()
        if (old != null) {
            old.unregisterAdapterDataObserver(mObserver)
            old.onDetachedFromRecyclerView(this)
        }
        mLayout?.let { lm ->
            lm.removeAndRecycleAllViews(recycler)
            lm.removeAndRecycleScrapInt(recycler)
        }
        recycler.clear()
        if (removeAndRecycleViews || old == null) getRecycledViewPool().clear()
        mAdapter = adapter
        if (adapter != null) {
            adapter.registerAdapterDataObserver(mObserver)
            adapter.onAttachedToRecyclerView(this)
        }
        mLayout?.onAdapterChanged(old, adapter)
        mState.mStructureChanged = true
        requestLayout()
    }

    open fun getLayoutManager(): LayoutManager? = mLayout
    open fun setLayoutManager(layout: LayoutManager?) {
        if (layout === mLayout) return
        stopScroll()
        mLayout?.let { old ->
            old.removeAndRecycleAllViews(recycler)
            old.removeAndRecycleScrapInt(recycler)
            recycler.clear()
            if (isAttachedToWindow()) old.onDetachedFromWindow(this, recycler)
            old.setRecyclerView(null)
        }
        mLayout = layout
        if (layout != null) {
            if (layout.mRecyclerView != null) throw IllegalArgumentException("LayoutManager $layout is already attached to a RecyclerView")
            layout.setRecyclerView(this)
            if (isAttachedToWindow()) layout.onAttachedToWindow(this)
        }
        requestLayout()
    }

    open fun setHasFixedSize(hasFixedSize: Boolean) {
        mHasFixedSize = hasFixedSize
    }

    open fun hasFixedSize(): Boolean = mHasFixedSize
    open fun setItemAnimator(animator: ItemAnimator?) {
        mItemAnimator = animator
    }

    open fun getItemAnimator(): ItemAnimator? = mItemAnimator
    open fun isAnimating(): Boolean = false
    open fun setRecycledViewPool(pool: RecycledViewPool?) {
        mRecycledViewPool = pool
    }

    open fun getRecycledViewPool(): RecycledViewPool = mRecycledViewPool ?: RecycledViewPool().also { mRecycledViewPool = it }
    open fun setItemViewCacheSize(size: Int) = recycler.setViewCacheSize(size)
    open fun setRecyclerListener(listener: RecyclerListener?) {
        mRecyclerListeners.clear()
        if (listener != null) mRecyclerListeners.add(listener)
    }

    open fun addRecyclerListener(listener: RecyclerListener) {
        mRecyclerListeners.add(listener)
    }

    open fun removeRecyclerListener(listener: RecyclerListener) {
        mRecyclerListeners.remove(listener)
    }

    open fun setChildDrawingOrderCallback(callback: ChildDrawingOrderCallback?) {}
    open fun setEdgeEffectFactory(edgeEffectFactory: EdgeEffectFactory) {}
    open fun setOnFlingListener(onFlingListener: OnFlingListener?) {
        mOnFlingListener = onFlingListener
    }

    open fun getOnFlingListener(): OnFlingListener? = mOnFlingListener
    open fun setLayoutFrozen(frozen: Boolean) = suppressLayout(frozen)
    open fun isLayoutFrozen(): Boolean = mLayoutFrozen
    open fun suppressLayout(suppress: Boolean) {
        if (mLayoutFrozen == suppress) return
        mLayoutFrozen = suppress
        if (!suppress) requestLayout()
    }

    open fun isLayoutSuppressed(): Boolean = mLayoutFrozen
    open fun isComputingLayout(): Boolean = mInLayout
    open fun hasPendingAdapterUpdates(): Boolean = false
    open fun setPreserveFocusAfterLayout(preserveFocusAfterLayout: Boolean) {}
    // AOSP field name: upstream reads and writes it by reflection (reduceDragSensitivity)
    private var mTouchSlop: Int = (8 * com.lagradost.desktop.runtime.AndroidRuntime.displayMetrics.density).toInt()
    open fun setScrollingTouchSlop(slopConstant: Int) {
        mTouchSlop = (8 * com.lagradost.desktop.runtime.AndroidRuntime.displayMetrics.density).toInt()
    }
    open fun setAccessibilityDelegateCompat(accessibilityDelegate: Any?) {}
    open fun getMaxFlingVelocity(): Int = 8000
    open fun getMinFlingVelocity(): Int = 50

    // ------------------------------------------------------------------ decorations

    open fun addItemDecoration(decor: ItemDecoration) = addItemDecoration(decor, -1)
    open fun addItemDecoration(decor: ItemDecoration, index: Int) {
        if (index < 0) mItemDecorations.add(decor) else mItemDecorations.add(index, decor)
        markItemDecorInsetsDirty()
        requestLayout()
    }

    open fun removeItemDecoration(decor: ItemDecoration) {
        mItemDecorations.remove(decor)
        markItemDecorInsetsDirty()
        requestLayout()
    }

    open fun removeItemDecorationAt(index: Int) = removeItemDecoration(mItemDecorations[index])
    open fun getItemDecorationCount(): Int = mItemDecorations.size
    open fun getItemDecorationAt(index: Int): ItemDecoration = mItemDecorations[index]
    open fun invalidateItemDecorations() {
        markItemDecorInsetsDirty()
        requestLayout()
    }

    private fun markItemDecorInsetsDirty() {
        for (i in 0 until getChildCount()) (getChildAt(i)?.getLayoutParams() as? LayoutParams)?.mInsetsDirty = true
        recycler.mCachedViews.forEach { (it.itemView.getLayoutParams() as? LayoutParams)?.mInsetsDirty = true }
    }

    internal fun getItemDecorInsetsForChild(child: View): Rect {
        val lp = child.getLayoutParams() as LayoutParams
        if (!lp.mInsetsDirty) return lp.mDecorInsets
        val insets = lp.mDecorInsets
        insets.set(0, 0, 0, 0)
        val tmp = Rect()
        for (d in mItemDecorations) {
            tmp.set(0, 0, 0, 0)
            d.getItemOffsets(tmp, child, this, mState)
            insets.left += tmp.left
            insets.top += tmp.top
            insets.right += tmp.right
            insets.bottom += tmp.bottom
        }
        lp.mInsetsDirty = false
        return insets
    }

    /** RecyclerView.onDraw / draw: decorations under and over the children (called by the renderer) */
    fun drawDecorations(c: Canvas, over: Boolean) {
        for (d in mItemDecorations) {
            if (over) {
                d.onDrawOver(c, this, mState)
            } else {
                d.onDraw(c, this, mState)
            }
        }
    }

    // ------------------------------------------------------------------ listeners

    open fun addOnScrollListener(listener: OnScrollListener) {
        mScrollListeners.add(listener)
    }

    open fun removeOnScrollListener(listener: OnScrollListener) {
        mScrollListeners.remove(listener)
    }

    open fun clearOnScrollListeners() = mScrollListeners.clear()

    @Deprecated("")
    open fun setOnScrollListener(listener: OnScrollListener?) {
        mScrollListener = listener
    }

    fun scrollListeners(): List<OnScrollListener> = mScrollListeners.toList()
    open fun addOnItemTouchListener(listener: OnItemTouchListener) {
        mOnItemTouchListeners.add(listener)
    }

    open fun removeOnItemTouchListener(listener: OnItemTouchListener) {
        mOnItemTouchListeners.remove(listener)
    }

    open fun addOnChildAttachStateChangeListener(listener: OnChildAttachStateChangeListener) {
        mChildAttachListeners.add(listener)
    }

    open fun removeOnChildAttachStateChangeListener(listener: OnChildAttachStateChangeListener) {
        mChildAttachListeners.remove(listener)
    }

    open fun clearOnChildAttachStateChangeListeners() = mChildAttachListeners.clear()

    internal fun dispatchChildAttached(child: View) {
        val holder = getChildViewHolderInt(child)
        @Suppress("UNCHECKED_CAST")
        if (holder != null) (mAdapter as? Adapter<ViewHolder>)?.onViewAttachedToWindow(holder)
        for (l in mChildAttachListeners.toList()) l.onChildViewAttachedToWindow(child)
    }

    internal fun dispatchChildDetached(child: View) {
        val holder = getChildViewHolderInt(child)
        @Suppress("UNCHECKED_CAST")
        if (holder != null) (mAdapter as? Adapter<ViewHolder>)?.onViewDetachedFromWindow(holder)
        for (l in mChildAttachListeners.toList()) l.onChildViewDetachedFromWindow(child)
    }

    // ------------------------------------------------------------------ children bookkeeping for the layout manager

    internal fun addViewInLayoutPublic(child: View, index: Int, lp: LayoutParams) {
        addViewInLayout(child, index, lp, true)
    }

    internal fun attachViewToParentPublic(child: View, index: Int, lp: LayoutParams) = attachViewToParent(child, index, lp)
    internal fun detachViewFromParentPublic(index: Int) = detachViewFromParent(index)
    internal fun removeDetachedViewPublic(child: View) = removeDetachedView(child, false)
    internal fun removeViewPublic(child: View) {
        val i = indexOfChild(child)
        if (i < 0) return
        dispatchChildDetached(child)
        removeViewInLayout(child)
    }

    override fun removeDetachedView(child: View, animate: Boolean) {
        dispatchChildDetached(child)
        super.removeDetachedView(child, animate)
    }

    internal fun setMeasuredDimensionPublic(w: Int, h: Int) = setMeasuredDimension(w, h)

    internal fun defaultOnMeasure(widthSpec: Int, heightSpec: Int) {
        val width = LayoutManager.chooseSize(widthSpec, getPaddingLeft() + getPaddingRight(), getMinimumWidth())
        val height = LayoutManager.chooseSize(heightSpec, getPaddingTop() + getPaddingBottom(), getMinimumHeight())
        setMeasuredDimension(width, height)
    }

    open fun offsetChildrenVertical(dy: Int) {
        for (i in 0 until getChildCount()) getChildAt(i)?.offsetTopAndBottom(dy)
    }

    open fun offsetChildrenHorizontal(dx: Int) {
        for (i in 0 until getChildCount()) getChildAt(i)?.offsetLeftAndRight(dx)
    }

    // ------------------------------------------------------------------ measure / layout

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val layout = mLayout
        if (layout == null) {
            defaultOnMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        if (layout.isAutoMeasureEnabled()) {
            val exact = View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY &&
                View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY
            layout.onMeasure(recycler, mState, widthMeasureSpec, heightMeasureSpec)
            if (exact || mAdapter == null) return
            // wrap content: lay the children out within the specs, then take their bounds
            layout.setMeasureSpecs(widthMeasureSpec, heightMeasureSpec)
            mState.mIsMeasuring = true
            layoutChildrenInternal()
            layout.setMeasuredDimensionFromChildren(widthMeasureSpec, heightMeasureSpec)
            mState.mIsMeasuring = false
        } else {
            layout.onMeasure(recycler, mState, widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        dispatchLayout()
    }

    /** RecyclerView.dispatchLayout: lays the children out for the current size */
    internal fun dispatchLayout() {
        val layout = mLayout ?: return
        if (mAdapter == null) return
        layout.setExactMeasureSpecsFrom(this)
        val before = visibleRange()
        layoutChildrenInternal()
        layout.onLayoutCompleted(mState)
        mState.mStructureChanged = false
        if (before != visibleRange()) dispatchOnScrolled(0, 0)
    }

    private fun layoutChildrenInternal() {
        val layout = mLayout ?: return
        if (mLayoutFrozen) return
        mInLayout = true
        try {
            mState.mItemCount = mAdapter?.getItemCount() ?: 0
            layout.onLayoutChildren(recycler, mState)
            layout.removeAndRecycleScrapInt(recycler)
        } catch (t: Throwable) {
            android.util.Log.e("RecyclerView", "layout failed", t)
        } finally {
            mInLayout = false
        }
    }

    private fun visibleRange(): Long {
        var minP = Int.MAX_VALUE
        var maxP = Int.MIN_VALUE
        for (i in 0 until getChildCount()) {
            val p = getChildViewHolderInt(getChildAt(i) ?: continue)?.layoutPosition ?: continue
            minP = min(minP, p)
            maxP = max(maxP, p)
        }
        return (minP.toLong() shl 32) or (maxP.toLong() and 0xffffffffL)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
        mLayout?.generateDefaultLayoutParams() ?: LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams =
        mLayout?.generateLayoutParams(getContext(), attrs) ?: LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = mLayout?.generateLayoutParams(p) ?: LayoutParams(p)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams && (mLayout?.checkLayoutParams(p) ?: true)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mLayout?.onAttachedToWindow(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopScroll()
        mLayout?.onDetachedFromWindow(this, recycler)
    }

    // ------------------------------------------------------------------ scrolling

    open fun getScrollState(): Int = mScrollState

    internal fun setScrollStateInternal(state: Int) {
        if (state == mScrollState) return
        mScrollState = state
        if (state != SCROLL_STATE_SETTLING) mAnimGeneration++
        mLayout?.onScrollStateChanged(state)
        onScrollStateChanged(state)
        mScrollListener?.onScrollStateChanged(this, state)
        for (l in mScrollListeners) l.onScrollStateChanged(this, state)
    }

    open fun onScrollStateChanged(state: Int) {}
    open fun onScrolled(dx: Int, dy: Int) {}

    internal fun dispatchOnScrolled(dx: Int, dy: Int) {
        onScrolled(dx, dy)
        mScrollListener?.onScrolled(this, dx, dy)
        for (l in mScrollListeners) l.onScrolled(this, dx, dy)
    }

    /** Scrolls the content natively; returns the consumed amounts (RecyclerView.scrollByInternal) */
    fun scrollByInternal(x: Int, y: Int): IntArray {
        val layout = mLayout ?: return intArrayOf(0, 0)
        if (mAdapter == null || mLayoutFrozen) return intArrayOf(0, 0)
        val pre = intArrayOf(0, 0)
        if (x != 0 || y != 0) dispatchNestedPreScroll(x, y, pre)
        val sx = x - pre[0]
        val sy = y - pre[1]
        layout.setExactMeasureSpecsFrom(this)
        mState.mItemCount = mAdapter?.getItemCount() ?: 0
        mInLayout = true
        var cx = 0
        var cy = 0
        try {
            if (sx != 0 && layout.canScrollHorizontally()) cx = layout.scrollHorizontallyBy(sx, recycler, mState)
            if (sy != 0 && layout.canScrollVertically()) cy = layout.scrollVerticallyBy(sy, recycler, mState)
            layout.removeAndRecycleScrapInt(recycler)
        } finally {
            mInLayout = false
        }
        val unX = sx - cx
        val unY = sy - cy
        if (unX != 0 || unY != 0) dispatchNestedScroll(cx, cy, unX, unY, null)
        if (cx != 0 || cy != 0) dispatchOnScrolled(cx, cy)
        return intArrayOf(cx + pre[0], cy + pre[1])
    }

    override fun scrollBy(x: Int, y: Int) {
        scrollByInternal(x, y)
    }

    override fun scrollTo(x: Int, y: Int) {
        android.util.Log.w("RecyclerView", "RecyclerView does not support scrolling to an absolute position. Use scrollToPosition instead")
    }

    open fun scrollToPosition(position: Int) {
        stopScroll()
        mLayout?.scrollToPosition(position)
        awakenScrollBars()
    }

    open fun smoothScrollToPosition(position: Int) {
        val layout = mLayout ?: return
        layout.smoothScrollToPosition(this, mState, position)
    }

    open fun smoothScrollBy(dx: Int, dy: Int) = smoothScrollBy(dx, dy, null)
    open fun smoothScrollBy(dx: Int, dy: Int, interpolator: android.view.animation.Interpolator?) = smoothScrollBy(dx, dy, interpolator, UNDEFINED_DURATION)
    open fun smoothScrollBy(dx: Int, dy: Int, interpolator: android.view.animation.Interpolator?, duration: Int) {
        val layout = mLayout ?: return
        val x = if (layout.canScrollHorizontally()) dx else 0
        val y = if (layout.canScrollVertically()) dy else 0
        if (x == 0 && y == 0) return
        setScrollStateInternal(SCROLL_STATE_SETTLING)
        animateScrollBy(x, y, if (duration > 0) duration else 250) { setScrollStateInternal(SCROLL_STATE_IDLE) }
    }

    private var mAnimGeneration = 0

    /** Scroll animation (the settling part of ViewFlinger) */
    internal fun animateScrollBy(dx: Int, dy: Int, duration: Int, onEnd: () -> Unit) {
        val gen = ++mAnimGeneration
        val handler = Handler(Looper.getMainLooper())
        val start = System.nanoTime()
        val interpolator = android.view.animation.DecelerateInterpolator()
        var doneX = 0
        var doneY = 0
        val step = object : Runnable {
            override fun run() {
                if (gen != mAnimGeneration) return
                val t = ((System.nanoTime() - start) / 1_000_000f / duration).coerceIn(0f, 1f)
                val f = interpolator.getInterpolation(t)
                val tx = (dx * f).toInt()
                val ty = (dy * f).toInt()
                val consumed = scrollByInternal(tx - doneX, ty - doneY)
                doneX += consumed[0]
                doneY += consumed[1]
                val blocked = (tx - doneX != 0 && consumed[0] == 0) || (ty - doneY != 0 && consumed[1] == 0)
                if (t < 1f && !blocked) handler.postDelayed(this, 16) else onEnd()
            }
        }
        handler.post(step)
    }

    open fun stopScroll() {
        mAnimGeneration++
        mLayout?.stopSmoothScroller()
        if (mScrollState != SCROLL_STATE_DRAGGING) setScrollStateInternal(SCROLL_STATE_IDLE)
    }

    open fun stopNestedScroll() {}
    open fun fling(velocityX: Int, velocityY: Int): Boolean {
        if (mOnFlingListener?.onFling(velocityX, velocityY) == true) return true
        return false
    }

    private fun awakenScrollBars() {}

    /** Renderer: user drag / wheel state */
    fun onUserScrollState(scrolling: Boolean) {
        if (scrolling) {
            mAnimGeneration++
            mLayout?.stopSmoothScroller()
            setScrollStateInternal(SCROLL_STATE_DRAGGING)
        } else if (mScrollState == SCROLL_STATE_DRAGGING) {
            if (mOnFlingListener?.onFling(0, 0) != true) setScrollStateInternal(SCROLL_STATE_IDLE)
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        val layout = mLayout ?: return false
        if (!layout.canScrollVertically()) return false
        return canScrollInDirection(direction, vertical = true)
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        val layout = mLayout ?: return false
        if (!layout.canScrollHorizontally()) return false
        return canScrollInDirection(direction, vertical = false)
    }

    private fun canScrollInDirection(direction: Int, vertical: Boolean): Boolean {
        val count = mAdapter?.getItemCount() ?: 0
        if (count == 0 || getChildCount() == 0) return false
        var first: View? = null
        var last: View? = null
        var minP = Int.MAX_VALUE
        var maxP = Int.MIN_VALUE
        for (i in 0 until getChildCount()) {
            val c = getChildAt(i) ?: continue
            val p = getChildLayoutPosition(c)
            if (p == NO_POSITION) continue
            if (p < minP) {
                minP = p
                first = c
            }
            if (p > maxP) {
                maxP = p
                last = c
            }
        }
        val reverse = (mLayout as? LinearLayoutManager)?.getReverseLayout() == true
        val lm = mLayout!!
        val r = Rect()
        return if ((direction > 0) != reverse) {
            val v = last ?: return false
            if (maxP < count - 1) return true
            lm.getDecoratedBoundsWithMargins(v, r)
            if (vertical) r.bottom > getHeight() - getPaddingBottom() else r.right > getWidth() - getPaddingRight()
        } else {
            val v = first ?: return false
            if (minP > 0) return true
            lm.getDecoratedBoundsWithMargins(v, r)
            if (vertical) r.top < getPaddingTop() else r.left < getPaddingLeft()
        }
    }

    override fun computeVerticalScrollOffset(): Int = mLayout?.computeVerticalScrollOffset(mState) ?: 0
    override fun computeVerticalScrollExtent(): Int = mLayout?.computeVerticalScrollExtent(mState) ?: 0
    override fun computeVerticalScrollRange(): Int = mLayout?.computeVerticalScrollRange(mState) ?: 0
    override fun computeHorizontalScrollOffset(): Int = mLayout?.computeHorizontalScrollOffset(mState) ?: 0
    override fun computeHorizontalScrollExtent(): Int = mLayout?.computeHorizontalScrollExtent(mState) ?: 0
    override fun computeHorizontalScrollRange(): Int = mLayout?.computeHorizontalScrollRange(mState) ?: 0

    // ------------------------------------------------------------------ finding holders

    open fun findViewHolderForAdapterPosition(position: Int): ViewHolder? = findHolder { it.adapterPosition == position }
    open fun findViewHolderForLayoutPosition(position: Int): ViewHolder? = findHolder { it.layoutPosition == position && !it.isRemoved() }

    @Deprecated("")
    open fun findViewHolderForPosition(position: Int): ViewHolder? = findViewHolderForLayoutPosition(position)
    open fun findViewHolderForItemId(id: Long): ViewHolder? = if (mAdapter?.hasStableIds() == true) findHolder { it.itemId == id } else null

    private inline fun findHolder(predicate: (ViewHolder) -> Boolean): ViewHolder? {
        for (i in 0 until getChildCount()) {
            val h = getChildViewHolderInt(getChildAt(i) ?: continue) ?: continue
            if (predicate(h)) return h
        }
        return null
    }

    open fun findChildViewUnder(x: Float, y: Float): View? {
        for (i in getChildCount() - 1 downTo 0) {
            val child = getChildAt(i) ?: continue
            val tx = child.getTranslationX()
            val ty = child.getTranslationY()
            if (x >= child.getLeft() + tx && x <= child.getRight() + tx && y >= child.getTop() + ty && y <= child.getBottom() + ty) return child
        }
        return null
    }

    open fun findContainingItemView(view: View): View? {
        var v: View? = view
        var parent = v?.getParent()
        while (parent != null && parent !== this && parent is View) {
            v = parent
            parent = v.getParent()
        }
        return if (parent === this) v else null
    }

    open fun findContainingViewHolder(view: View): ViewHolder? = findContainingItemView(view)?.let { getChildViewHolder(it) }
    open fun getChildViewHolder(child: View): ViewHolder {
        val parent = child.getParent()
        if (parent != null && parent !== this) throw IllegalArgumentException("View $child is not a direct child of $this")
        return getChildViewHolderInt(child) ?: throw IllegalArgumentException("View $child is not a RecyclerView item")
    }

    open fun getChildAdapterPosition(child: View): Int = getChildViewHolderInt(child)?.adapterPosition ?: NO_POSITION
    open fun getChildLayoutPosition(child: View): Int = getChildViewHolderInt(child)?.layoutPosition ?: NO_POSITION
    open fun getChildItemId(child: View): Long = getChildViewHolderInt(child)?.itemId ?: NO_ID

    @Deprecated("")
    open fun getChildPosition(child: View): Int = getChildAdapterPosition(child)
    open fun getDecoratedBoundsWithMargins(view: View, outBounds: Rect) = mLayout?.getDecoratedBoundsWithMargins(view, outBounds) ?: Unit
    open fun post(action: Runnable, unused: Boolean): Boolean = post(action)
    open fun setOverScrollMode(mode: Int, unused: Boolean) {}
    open fun onChildAttachedToWindow(child: View) {}
    open fun onChildDetachedFromWindow(child: View) {}
}

/** ViewHolder of a RecyclerView child (RecyclerView.getChildViewHolderInt) */
internal fun getChildViewHolderInt(child: View?): RecyclerView.ViewHolder? =
    (child?.getLayoutParams() as? RecyclerView.LayoutParams)?.mViewHolder

// ================================================================================ LinearLayoutManager

/**
 * LinearLayoutManager: items in a row (vertical) or column (horizontal), optionally reversed.
 * Layout works in "virtual" coordinates along the scrolling axis (0 = start edge, growing towards the
 * end) that are mirrored for reverse layouts. GridLayoutManager lays out rows of spans the same way.
 */
open class LinearLayoutManager : RecyclerView.LayoutManager, RecyclerView.SmoothScroller.ScrollVectorProvider {
    companion object {
        const val HORIZONTAL = RecyclerView.HORIZONTAL
        const val VERTICAL = RecyclerView.VERTICAL
        const val INVALID_OFFSET = Int.MIN_VALUE
    }

    private var mOrientation = VERTICAL
    private var mReverseLayout = false
    private var mStackFromEnd = false
    private var mRecycleChildrenOnDetach = false
    internal var mPendingScrollPosition = RecyclerView.NO_POSITION
    internal var mPendingScrollPositionOffset = INVALID_OFFSET

    constructor(context: Context?) : this(context, VERTICAL, false)
    constructor(context: Context?, orientation: Int, reverseLayout: Boolean) : super() {
        mOrientation = orientation
        mReverseLayout = reverseLayout
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super() {
        val p = getProperties(context ?: com.lagradost.desktop.runtime.AndroidRuntime.context, attrs, defStyleAttr, defStyleRes)
        mOrientation = p.orientation
        mReverseLayout = p.reverseLayout
        setStackFromEnd(p.stackFromEnd)
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    open fun getOrientation(): Int = mOrientation
    open fun setOrientation(orientation: Int) {
        if (orientation != mOrientation) {
            mOrientation = orientation
            requestLayout()
        }
    }

    open fun getReverseLayout(): Boolean = mReverseLayout
    open fun setReverseLayout(reverseLayout: Boolean) {
        if (reverseLayout != mReverseLayout) {
            mReverseLayout = reverseLayout
            requestLayout()
        }
    }

    open fun getStackFromEnd(): Boolean = mStackFromEnd
    open fun setStackFromEnd(stackFromEnd: Boolean) {
        if (stackFromEnd != mStackFromEnd) {
            mStackFromEnd = stackFromEnd
            requestLayout()
        }
    }

    open fun getRecycleChildrenOnDetach(): Boolean = mRecycleChildrenOnDetach
    open fun setRecycleChildrenOnDetach(recycleChildrenOnDetach: Boolean) {
        mRecycleChildrenOnDetach = recycleChildrenOnDetach
    }

    open fun setSmoothScrollbarEnabled(enabled: Boolean) {}
    open fun isSmoothScrollbarEnabled(): Boolean = true
    open fun setInitialPrefetchItemCount(itemCount: Int) {}
    open fun getInitialPrefetchItemCount(): Int = 2
    open val isLayoutRTL: Boolean get() = false

    override fun canScrollHorizontally(): Boolean = mOrientation == HORIZONTAL
    override fun canScrollVertically(): Boolean = mOrientation == VERTICAL
    override fun computeScrollVectorForPosition(targetPosition: Int): android.graphics.PointF? {
        if (getChildCount() == 0) return null
        val first = getPosition(getChildAt(0) ?: return null)
        val direction = if ((targetPosition < first) != mReverseLayout) -1f else 1f
        return if (mOrientation == HORIZONTAL) android.graphics.PointF(direction, 0f) else android.graphics.PointF(0f, direction)
    }

    // --------------------------------------------------------------- rows (one item each here)

    protected open fun rowCount(itemCount: Int): Int = itemCount
    protected open fun rowOfPosition(position: Int): Int = position
    protected open fun rowPositions(row: Int): IntRange = row..row
    protected open fun invalidateRows() {}

    /** Adds, measures and returns the views of [row] (not positioned yet) */
    protected open fun measureRow(row: Int, recycler: RecyclerView.Recycler, forward: Boolean): List<View> {
        val v = recycler.getViewForPosition(row)
        if (forward) addView(v) else addView(v, 0)
        measureChildWithMargins(v, 0, 0)
        return listOf(v)
    }

    /** Positions the measured views of a row: main axis [start, start + size), cross axis from the padding */
    protected open fun layoutRow(views: List<View>, start: Int, size: Int) {
        for (v in views) {
            val cross = decoratedCross(v)
            if (mOrientation == VERTICAL) {
                val left = getPaddingLeft()
                placeMain(v, start, size, left, left + cross)
            } else {
                val top = getPaddingTop()
                placeMain(v, start, size, top, top + cross)
            }
        }
    }

    protected fun decoratedMain(v: View): Int {
        val lp = v.getLayoutParams() as RecyclerView.LayoutParams
        return if (mOrientation == VERTICAL) getDecoratedMeasuredHeight(v) + lp.topMargin + lp.bottomMargin
        else getDecoratedMeasuredWidth(v) + lp.leftMargin + lp.rightMargin
    }

    protected fun decoratedCross(v: View): Int {
        val lp = v.getLayoutParams() as RecyclerView.LayoutParams
        return if (mOrientation == VERTICAL) getDecoratedMeasuredWidth(v) + lp.leftMargin + lp.rightMargin
        else getDecoratedMeasuredHeight(v) + lp.topMargin + lp.bottomMargin
    }

    /** Lays out [v] at virtual main axis [start, start + size) and cross axis [crossStart, crossEnd) */
    protected fun placeMain(v: View, start: Int, size: Int, crossStart: Int, crossEnd: Int) {
        val realStart = if (mReverseLayout) totalMain() - start - size else start
        if (mOrientation == VERTICAL) layoutDecoratedWithMargins(v, crossStart, realStart, crossEnd, realStart + size)
        else layoutDecoratedWithMargins(v, realStart, crossStart, realStart + size, crossEnd)
    }

    private fun totalMain(): Int = if (mOrientation == VERTICAL) getHeight() else getWidth()
    private fun startAfterPadding(): Int = if (mOrientation == VERTICAL) (if (mReverseLayout) getPaddingBottom() else getPaddingTop()) else (if (mReverseLayout) getPaddingRight() else getPaddingLeft())
    private fun endAfterPadding(): Int = totalMain() - if (mOrientation == VERTICAL) (if (mReverseLayout) getPaddingTop() else getPaddingBottom()) else (if (mReverseLayout) getPaddingLeft() else getPaddingRight())
    private fun isInfinite(): Boolean = (if (mOrientation == VERTICAL) getHeightMode() else getWidthMode()) == View.MeasureSpec.UNSPECIFIED

    /** Virtual start / end of a child's decorated bounds with margins */
    private fun vStart(v: View): Int {
        val r = Rect()
        getDecoratedBoundsWithMargins(v, r)
        return if (mOrientation == VERTICAL) (if (mReverseLayout) totalMain() - r.bottom else r.top) else (if (mReverseLayout) totalMain() - r.right else r.left)
    }

    private fun vEnd(v: View): Int {
        val r = Rect()
        getDecoratedBoundsWithMargins(v, r)
        return if (mOrientation == VERTICAL) (if (mReverseLayout) totalMain() - r.top else r.bottom) else (if (mReverseLayout) totalMain() - r.left else r.right)
    }

    private fun offsetVirtual(d: Int) {
        val real = if (mReverseLayout) -d else d
        if (mOrientation == VERTICAL) offsetChildrenVertical(real) else offsetChildrenHorizontal(real)
    }

    /** Children with their rows: (first row, first start, last row, last end) */
    private fun edges(): IntArray? {
        if (getChildCount() == 0) return null
        var firstRow = Int.MAX_VALUE
        var lastRow = Int.MIN_VALUE
        var firstStart = Int.MAX_VALUE
        var lastEnd = Int.MIN_VALUE
        for (i in 0 until getChildCount()) {
            val c = getChildAt(i) ?: continue
            val p = getPosition(c)
            if (p == RecyclerView.NO_POSITION) continue
            val row = rowOfPosition(p)
            firstRow = min(firstRow, row)
            lastRow = max(lastRow, row)
            firstStart = min(firstStart, vStart(c))
            lastEnd = max(lastEnd, vEnd(c))
        }
        if (firstRow == Int.MAX_VALUE) return null
        return intArrayOf(firstRow, firstStart, lastRow, lastEnd)
    }

    private fun fillForward(recycler: RecyclerView.Recycler, fromRow: Int, fromStart: Int, limit: Int, rows: Int): Int {
        var row = fromRow
        var u = fromStart
        while (row < rows && (u < limit || isInfinite())) {
            val views = measureRow(row, recycler, forward = true)
            val size = views.maxOfOrNull { decoratedMain(it) } ?: 0
            layoutRow(views, u, size)
            u += size
            row++
        }
        return u
    }

    private fun fillBackward(recycler: RecyclerView.Recycler, fromRow: Int, fromEnd: Int, limit: Int): Int {
        var row = fromRow
        var u = fromEnd
        while (row >= 0 && u > limit) {
            val views = measureRow(row, recycler, forward = false)
            val size = views.maxOfOrNull { decoratedMain(it) } ?: 0
            layoutRow(views, u - size, size)
            u -= size
            row--
        }
        return u
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        val itemCount = state.getItemCount()
        invalidateRows()
        if (itemCount == 0) {
            removeAndRecycleAllViews(recycler)
            return
        }
        val rows = rowCount(itemCount)
        // anchor: the pending scroll position, else the first laid out child
        var anchorRow: Int
        var anchorStart: Int
        var alignEnd = false
        val start = startAfterPadding()
        val end = endAfterPadding()
        val pending = mPendingScrollPosition
        if (pending != RecyclerView.NO_POSITION && pending < itemCount) {
            anchorRow = rowOfPosition(pending)
            val existing = findViewByPosition(pending)
            if (mPendingScrollPositionOffset != INVALID_OFFSET) {
                anchorStart = start + mPendingScrollPositionOffset
            } else if (existing != null && decoratedMain(existing) <= end - start) {
                val s = vStart(existing)
                val e = vEnd(existing)
                anchorStart = when {
                    s < start -> start
                    e > end -> {
                        alignEnd = true
                        end
                    }
                    else -> s
                }
            } else {
                val firstChildPos = (0 until getChildCount()).mapNotNull { getChildAt(it)?.let { c -> getPosition(c) } }.filter { it >= 0 }.minOrNull()
                if (existing == null && firstChildPos != null && pending > firstChildPos) {
                    alignEnd = true
                    anchorStart = end
                } else anchorStart = start
            }
        } else {
            val e = edges()
            if (e != null) {
                anchorRow = e[0]
                anchorStart = e[1]
            } else if (mStackFromEnd) {
                anchorRow = rows - 1
                anchorStart = end
                alignEnd = true
            } else {
                anchorRow = 0
                anchorStart = start
            }
        }
        anchorRow = anchorRow.coerceIn(0, rows - 1)
        mPendingScrollPosition = RecyclerView.NO_POSITION
        mPendingScrollPositionOffset = INVALID_OFFSET

        detachAndScrapAttachedViews(recycler)
        if (alignEnd) {
            fillBackward(recycler, anchorRow, anchorStart, start)
            fillForward(recycler, anchorRow + 1, anchorStart, end, rows)
        } else {
            fillForward(recycler, anchorRow, anchorStart, end, rows)
            fillBackward(recycler, anchorRow - 1, anchorStart, start)
        }
        // no gaps at the start or the end (fixLayoutStartGap / fixLayoutEndGap)
        if (!isInfinite()) {
            edges()?.let { e ->
                if (e[0] == 0 && e[1] > start) {
                    offsetVirtual(-(e[1] - start))
                    edges()?.let { e2 -> if (e2[3] < end) fillForward(recycler, e2[2] + 1, e2[3], end, rows) }
                }
            }
            edges()?.let { e ->
                if (e[2] == rows - 1 && e[3] < end) {
                    var gap = end - e[3]
                    if (e[0] == 0) gap = min(gap, max(0, start - e[1]))
                    if (gap > 0) {
                        offsetVirtual(gap)
                        edges()?.let { e2 -> if (e2[1] > start && e2[0] > 0) fillBackward(recycler, e2[0] - 1, e2[1], start) }
                    }
                    // content smaller than the viewport and stacked from the start
                    edges()?.let { e3 -> if (e3[0] == 0 && e3[1] > start) offsetVirtual(-(e3[1] - start)) }
                }
            }
        }
        recycleOutOfBounds(recycler)
    }

    /** Recycles children entirely outside the viewport (clipToPadding like LayoutState recycling) */
    private fun recycleOutOfBounds(recycler: RecyclerView.Recycler) {
        if (isInfinite()) return
        val clip = getClipToPadding()
        val limitStart = if (clip) startAfterPadding() else 0
        val limitEnd = if (clip) endAfterPadding() else totalMain()
        for (i in getChildCount() - 1 downTo 0) {
            val c = getChildAt(i) ?: continue
            if (vEnd(c) <= limitStart || vStart(c) >= limitEnd) {
                // keep rows whole: only recycle when the whole row is out
                val row = rowOfPosition(getPosition(c))
                val rowOut = (0 until getChildCount()).all { j ->
                    val o = getChildAt(j) ?: return@all true
                    rowOfPosition(getPosition(o)) != row || vEnd(o) <= limitStart || vStart(o) >= limitEnd
                }
                if (rowOut) removeAndRecycleView(c, recycler)
            }
        }
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int =
        if (mOrientation == HORIZONTAL) 0 else scrollBy(dy, recycler, state)

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int =
        if (mOrientation == VERTICAL) 0 else scrollBy(dx, recycler, state)

    /** Scrolls by [delta] real px; returns the consumed real px (LinearLayoutManager.scrollBy) */
    private fun scrollBy(delta: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (getChildCount() == 0 || delta == 0) return 0
        val itemCount = state.getItemCount()
        if (itemCount == 0) return 0
        val rows = rowCount(itemCount)
        val d = if (mReverseLayout) -delta else delta
        val start = startAfterPadding()
        val end = endAfterPadding()
        var consumed: Int
        val e = edges() ?: return 0
        if (d > 0) {
            var lastEnd = e[3]
            if (lastEnd - d < end && e[2] < rows - 1) lastEnd = fillForward(recycler, e[2] + 1, lastEnd, end + d, rows)
            consumed = min(d, max(0, lastEnd - end))
        } else {
            var firstStart = e[1]
            if (firstStart - d > start && e[0] > 0) firstStart = fillBackward(recycler, e[0] - 1, firstStart, start + d)
            consumed = max(d, min(0, firstStart - start))
        }
        if (consumed != 0) offsetVirtual(-consumed)
        recycleOutOfBounds(recycler)
        return if (mReverseLayout) -consumed else consumed
    }

    override fun scrollToPosition(position: Int) {
        mPendingScrollPosition = position
        mPendingScrollPositionOffset = INVALID_OFFSET
        requestLayout()
    }

    open fun scrollToPositionWithOffset(position: Int, offset: Int) {
        mPendingScrollPosition = position
        mPendingScrollPositionOffset = offset
        requestLayout()
    }

    override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
        val scroller = LinearSmoothScroller(recyclerView.getContext())
        scroller.setTargetPosition(position)
        startSmoothScroll(scroller)
    }

    // --------------------------------------------------------------- queries

    private fun findOneVisibleChild(fromFirst: Boolean, completelyVisible: Boolean): View? {
        val children = (0 until getChildCount()).mapNotNull { getChildAt(it) }.filter { getPosition(it) != RecyclerView.NO_POSITION }
            .sortedBy { getPosition(it) }
        val ordered = if (fromFirst) children else children.reversed()
        val start = startAfterPadding()
        val end = endAfterPadding()
        for (c in ordered) {
            val s = vStart(c)
            val e = vEnd(c)
            if (completelyVisible) {
                if (s >= start && e <= end) return c
            } else if (s < end && e > start) return c
        }
        return null
    }

    open fun findFirstVisibleItemPosition(): Int = findOneVisibleChild(true, false)?.let { getPosition(it) } ?: RecyclerView.NO_POSITION
    open fun findFirstCompletelyVisibleItemPosition(): Int = findOneVisibleChild(true, true)?.let { getPosition(it) } ?: RecyclerView.NO_POSITION
    open fun findLastVisibleItemPosition(): Int = findOneVisibleChild(false, false)?.let { getPosition(it) } ?: RecyclerView.NO_POSITION
    open fun findLastCompletelyVisibleItemPosition(): Int = findOneVisibleChild(false, true)?.let { getPosition(it) } ?: RecyclerView.NO_POSITION

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = if (mOrientation == VERTICAL) scrollOffset(state) else 0
    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = if (mOrientation == VERTICAL) scrollExtent() else 0
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = if (mOrientation == VERTICAL) scrollRange(state) else 0
    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int = if (mOrientation == HORIZONTAL) scrollOffset(state) else 0
    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int = if (mOrientation == HORIZONTAL) scrollExtent() else 0
    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int = if (mOrientation == HORIZONTAL) scrollRange(state) else 0

    /** ScrollbarHelper (smooth scrollbar): estimates from the average laid out row size */
    private fun avgRowSize(): Float {
        val e = edges() ?: return 0f
        val rowsShown = e[2] - e[0] + 1
        return if (rowsShown > 0) (e[3] - e[1]).toFloat() / rowsShown else 0f
    }

    private fun scrollOffset(state: RecyclerView.State): Int {
        val e = edges() ?: return 0
        return (e[0] * avgRowSize() + (startAfterPadding() - e[1])).toInt().coerceAtLeast(0)
    }

    private fun scrollExtent(): Int = endAfterPadding() - startAfterPadding()
    private fun scrollRange(state: RecyclerView.State): Int = (rowCount(state.getItemCount()) * avgRowSize()).toInt()

    override fun onSaveInstanceState(): android.os.Parcelable? = null
}

// ================================================================================ GridLayoutManager

open class GridLayoutManager : LinearLayoutManager {
    companion object {
        const val DEFAULT_SPAN_COUNT = -1
    }

    abstract class SpanSizeLookup {
        private var mCacheSpanIndices = false
        abstract fun getSpanSize(position: Int): Int
        open fun getSpanIndex(position: Int, spanCount: Int): Int {
            val spanSize = getSpanSize(position)
            if (spanSize == spanCount) return 0
            var span = 0
            for (i in 0 until position) {
                val size = getSpanSize(i)
                span += size
                if (span == spanCount) span = 0 else if (span > spanCount) span = size
            }
            return if (span + spanSize <= spanCount) span else 0
        }

        open fun getSpanGroupIndex(adapterPosition: Int, spanCount: Int): Int {
            var span = 0
            var group = 0
            val positionSpanSize = getSpanSize(adapterPosition)
            for (i in 0 until adapterPosition) {
                val size = getSpanSize(i)
                span += size
                if (span == spanCount) {
                    span = 0
                    group++
                } else if (span > spanCount) {
                    span = size
                    group++
                }
            }
            if (span + positionSpanSize > spanCount) group++
            return group
        }

        open fun setSpanIndexCacheEnabled(cacheSpanIndices: Boolean) {
            mCacheSpanIndices = cacheSpanIndices
        }

        open fun setSpanGroupIndexCacheEnabled(cacheSpanGroupIndices: Boolean) {}
        open fun isSpanIndexCacheEnabled(): Boolean = mCacheSpanIndices
        open fun invalidateSpanIndexCache() {}
        open fun invalidateSpanGroupIndexCache() {}
    }

    class DefaultSpanSizeLookup : SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int = 1
        override fun getSpanIndex(position: Int, spanCount: Int): Int = position % spanCount
    }

    open class LayoutParams : RecyclerView.LayoutParams {
        internal var mSpanIndex = -1
        internal var mSpanSize = 0

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: ViewGroup.LayoutParams) : super(source)
        constructor(source: RecyclerView.LayoutParams) : super(source)

        fun getSpanIndex(): Int = mSpanIndex
        fun getSpanSize(): Int = mSpanSize
    }

    private var mSpanCount = DEFAULT_SPAN_COUNT
    private var mSpanSizeLookup: SpanSizeLookup = DefaultSpanSizeLookup()
    private var rowStarts = IntArray(0)
    private var rowOf = IntArray(0)
    private var spanIndexOf = IntArray(0)
    private var cachedCount = -1

    constructor(context: Context?, spanCount: Int) : super(context) {
        setSpanCount(spanCount)
    }

    constructor(context: Context?, spanCount: Int, orientation: Int, reverseLayout: Boolean) : super(context, orientation, reverseLayout) {
        setSpanCount(spanCount)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setSpanCount(getProperties(context ?: com.lagradost.desktop.runtime.AndroidRuntime.context, attrs, defStyleAttr, defStyleRes).spanCount)
    }

    open fun getSpanCount(): Int = mSpanCount
    open fun setSpanCount(spanCount: Int) {
        if (spanCount == mSpanCount) return
        if (spanCount < 1) throw IllegalArgumentException("Span count should be at least 1. Provided $spanCount")
        mSpanCount = spanCount
        cachedCount = -1
        requestLayout()
    }

    open fun getSpanSizeLookup(): SpanSizeLookup = mSpanSizeLookup
    open fun setSpanSizeLookup(spanSizeLookup: SpanSizeLookup?) {
        mSpanSizeLookup = spanSizeLookup ?: DefaultSpanSizeLookup()
        cachedCount = -1
    }

    open fun setUsingSpansToEstimateScrollbarDimensions(useSpansToEstimateScrollBarDimensions: Boolean) {}
    override fun supportsPredictiveItemAnimations(): Boolean = false

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        if (getOrientation() == HORIZONTAL) LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        else LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): RecyclerView.LayoutParams = when (lp) {
        is ViewGroup.MarginLayoutParams -> LayoutParams(lp)
        else -> LayoutParams(lp)
    }

    override fun generateLayoutParams(c: Context, attrs: AttributeSet?): RecyclerView.LayoutParams = LayoutParams(c, attrs)
    override fun checkLayoutParams(lp: RecyclerView.LayoutParams?): Boolean = lp is LayoutParams

    override fun invalidateRows() {
        cachedCount = -1
    }

    /** Rows of spans for all positions (SpanSizeLookup spanGroupIndex / spanIndex) */
    private fun ensureRows(itemCount: Int) {
        if (cachedCount == itemCount) return
        val span = max(1, mSpanCount)
        val starts = ArrayList<Int>()
        rowOf = IntArray(itemCount)
        spanIndexOf = IntArray(itemCount)
        var used = span
        for (p in 0 until itemCount) {
            val size = mSpanSizeLookup.getSpanSize(p).coerceIn(1, span)
            if (used + size > span) {
                starts.add(p)
                used = 0
            }
            spanIndexOf[p] = used
            rowOf[p] = starts.size - 1
            used += size
        }
        rowStarts = starts.toIntArray()
        cachedCount = itemCount
    }

    override fun rowCount(itemCount: Int): Int {
        ensureRows(itemCount)
        return rowStarts.size
    }

    override fun rowOfPosition(position: Int): Int {
        ensureRows(getItemCount())
        return if (position in rowOf.indices) rowOf[position] else position / max(1, mSpanCount)
    }

    override fun rowPositions(row: Int): IntRange {
        ensureRows(getItemCount())
        val start = rowStarts[row]
        val end = if (row + 1 < rowStarts.size) rowStarts[row + 1] else getItemCount()
        return start until end
    }

    /** GridLayoutManager.calculateItemBorders: span edges across the cross axis */
    private fun borders(totalSpace: Int): IntArray {
        val span = max(1, mSpanCount)
        val borders = IntArray(span + 1)
        var consumed = 0
        var consumedPixels = 0
        val sizePerSpan = totalSpace / span
        val sizePerSpanRemainder = totalSpace % span
        var additionalSize = 0
        for (i in 1..span) {
            var itemSize = sizePerSpan
            additionalSize += sizePerSpanRemainder
            if (additionalSize > 0 && span - additionalSize < sizePerSpanRemainder) {
                itemSize += 1
                additionalSize -= span
            }
            consumedPixels += itemSize
            borders[i] = consumedPixels
            consumed++
        }
        @Suppress("UNUSED_VARIABLE") val unused = consumed
        return borders
    }

    private fun crossSpace(): Int = if (getOrientation() == VERTICAL) getWidth() - getPaddingLeft() - getPaddingRight() else getHeight() - getPaddingTop() - getPaddingBottom()

    override fun measureRow(row: Int, recycler: RecyclerView.Recycler, forward: Boolean): List<View> {
        val positions = rowPositions(row)
        val b = borders(crossSpace())
        val views = ArrayList<View>(positions.count())
        for ((i, p) in positions.withIndex()) {
            val v = recycler.getViewForPosition(p)
            if (forward) addView(v) else addView(v, i)
            val lp = v.getLayoutParams() as? LayoutParams
            val spanSize = mSpanSizeLookup.getSpanSize(p).coerceIn(1, max(1, mSpanCount))
            lp?.mSpanIndex = spanIndexOf[p]
            lp?.mSpanSize = spanSize
            measureSpanChild(v, b, spanIndexOf[p], spanSize, -1)
            views.add(v)
        }
        // views that did not measure the row size are measured again with it
        val maxSize = views.maxOfOrNull { decoratedMain(it) } ?: 0
        for ((i, v) in views.withIndex()) {
            if (decoratedMain(v) != maxSize) {
                val p = positions.first + i
                measureSpanChild(v, b, spanIndexOf[p], mSpanSizeLookup.getSpanSize(p).coerceIn(1, max(1, mSpanCount)), maxSize)
            }
        }
        return views
    }

    private fun measureSpanChild(v: View, borders: IntArray, spanIndex: Int, spanSize: Int, exactMain: Int) {
        val lp = v.getLayoutParams() as RecyclerView.LayoutParams
        val rv = mRecyclerView ?: return
        val insets = rv.getItemDecorInsetsForChild(v)
        val verticalInsets = insets.top + insets.bottom + lp.topMargin + lp.bottomMargin
        val horizontalInsets = insets.left + insets.right + lp.leftMargin + lp.rightMargin
        val end = min(spanIndex + spanSize, borders.size - 1)
        val spanSpace = borders[end] - borders[spanIndex.coerceIn(0, borders.size - 1)]
        if (getOrientation() == VERTICAL) {
            val wSpec = getChildMeasureSpec(spanSpace, View.MeasureSpec.EXACTLY, horizontalInsets, lp.width, false)
            val hSpec = if (exactMain >= 0) View.MeasureSpec.makeMeasureSpec(max(0, exactMain - verticalInsets), View.MeasureSpec.EXACTLY)
            else getChildMeasureSpec(getHeight(), getHeightMode(), verticalInsets, lp.height, true)
            v.measure(wSpec, hSpec)
        } else {
            val hSpec = getChildMeasureSpec(spanSpace, View.MeasureSpec.EXACTLY, verticalInsets, lp.height, false)
            val wSpec = if (exactMain >= 0) View.MeasureSpec.makeMeasureSpec(max(0, exactMain - horizontalInsets), View.MeasureSpec.EXACTLY)
            else getChildMeasureSpec(getWidth(), getWidthMode(), horizontalInsets, lp.width, true)
            v.measure(wSpec, hSpec)
        }
    }

    override fun layoutRow(views: List<View>, start: Int, size: Int) {
        val b = borders(crossSpace())
        for (v in views) {
            val lp = v.getLayoutParams() as? LayoutParams
            val spanIndex = (lp?.mSpanIndex ?: 0).coerceIn(0, b.size - 1)
            val cross0 = if (getOrientation() == VERTICAL) getPaddingLeft() else getPaddingTop()
            val crossStart = cross0 + b[spanIndex]
            placeMain(v, start, size, crossStart, crossStart + decoratedCross(v))
        }
    }
}

// ================================================================================ StaggeredGridLayoutManager

/** StaggeredGridLayoutManager laid out as rows of equal spans (items share their row's height) */
open class StaggeredGridLayoutManager(spanCount: Int, orientation: Int) : GridLayoutManager(null, spanCount, orientation, false) {
    companion object {
        const val HORIZONTAL = RecyclerView.HORIZONTAL
        const val VERTICAL = RecyclerView.VERTICAL
        const val GAP_HANDLING_NONE = 0
        const val GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS = 2
    }

    open class LayoutParams : GridLayoutManager.LayoutParams {
        private var fullSpan = false

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: ViewGroup.LayoutParams) : super(source)

        fun setFullSpan(fullSpan: Boolean) {
            this.fullSpan = fullSpan
        }

        fun isFullSpan(): Boolean = fullSpan
    }

    open fun setGapStrategy(gapStrategy: Int) {}
    open fun getGapStrategy(): Int = GAP_HANDLING_NONE
    open fun findFirstVisibleItemPositions(into: IntArray?): IntArray = IntArray(getSpanCount()) { findFirstVisibleItemPosition() }
    open fun findLastVisibleItemPositions(into: IntArray?): IntArray = IntArray(getSpanCount()) { findLastVisibleItemPosition() }
    open fun findFirstCompletelyVisibleItemPositions(into: IntArray?): IntArray = IntArray(getSpanCount()) { findFirstCompletelyVisibleItemPosition() }
    open fun findLastCompletelyVisibleItemPositions(into: IntArray?): IntArray = IntArray(getSpanCount()) { findLastCompletelyVisibleItemPosition() }
    open fun invalidateSpanAssignments() = requestLayout()
}

// ================================================================================ helpers

abstract class SimpleItemAnimator : RecyclerView.ItemAnimator() {
    open var supportsChangeAnimations: Boolean = true
}

open class DefaultItemAnimator : SimpleItemAnimator()

/** LinearSmoothScroller: scrolls until the target is visible, then aligns it (SNAP_TO_ANY) */
open class LinearSmoothScroller(context: Context?) : RecyclerView.SmoothScroller() {
    companion object {
        const val SNAP_TO_START = -1
        const val SNAP_TO_END = 1
        const val SNAP_TO_ANY = 0
    }

    private val density = com.lagradost.desktop.runtime.AndroidRuntime.displayMetrics.density

    override fun onStart() {}
    override fun onStop() {}

    protected open fun getVerticalSnapPreference(): Int = SNAP_TO_ANY
    protected open fun getHorizontalSnapPreference(): Int = SNAP_TO_ANY
    protected open fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float = 25f / displayMetrics.densityDpi
    protected open fun calculateTimeForScrolling(dx: Int): Int =
        kotlin.math.ceil(abs(dx) * calculateSpeedPerPixel(com.lagradost.desktop.runtime.AndroidRuntime.displayMetrics)).toInt()

    protected open fun calculateTimeForDeceleration(dx: Int): Int = kotlin.math.ceil(calculateTimeForScrolling(dx) / .3356).toInt()

    open fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int = when (snapPreference) {
        SNAP_TO_START -> boxStart - viewStart
        SNAP_TO_END -> boxEnd - viewEnd
        else -> {
            val dtStart = boxStart - viewStart
            if (dtStart > 0) dtStart else {
                val dtEnd = boxEnd - viewEnd
                if (dtEnd < 0) dtEnd else 0
            }
        }
    }

    open fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
        val lm = getLayoutManager() ?: return 0
        if (!lm.canScrollVertically()) return 0
        val r = Rect()
        lm.getDecoratedBoundsWithMargins(view, r)
        return calculateDtToFit(r.top, r.bottom, lm.getPaddingTop(), lm.getHeight() - lm.getPaddingBottom(), snapPreference)
    }

    open fun calculateDxToMakeVisible(view: View, snapPreference: Int): Int {
        val lm = getLayoutManager() ?: return 0
        if (!lm.canScrollHorizontally()) return 0
        val r = Rect()
        lm.getDecoratedBoundsWithMargins(view, r)
        return calculateDtToFit(r.left, r.right, lm.getPaddingLeft(), lm.getWidth() - lm.getPaddingRight(), snapPreference)
    }

    override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
        val dx = calculateDxToMakeVisible(targetView, getHorizontalSnapPreference())
        val dy = calculateDyToMakeVisible(targetView, getVerticalSnapPreference())
        val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toInt()
        val time = calculateTimeForDeceleration(distance).coerceIn(1, 600)
        action.update(-dx, -dy, time, android.view.animation.DecelerateInterpolator())
    }

    override fun onSeekTargetStep(dx: Int, dy: Int, state: RecyclerView.State, action: Action) {
        val lm = getLayoutManager() ?: return
        val vector = (lm as? ScrollVectorProvider)?.computeScrollVectorForPosition(getTargetPosition()) ?: return
        val step = (120 * density).toInt()
        action.update((vector.x * step).toInt(), (vector.y * step).toInt(), 16, null)
    }
}

/** OrientationHelper: edge helpers for one axis of a layout manager */
abstract class OrientationHelper private constructor(protected val mLayoutManager: RecyclerView.LayoutManager) {
    companion object {
        const val HORIZONTAL = RecyclerView.HORIZONTAL
        const val VERTICAL = RecyclerView.VERTICAL
        const val INVALID_SIZE = Int.MIN_VALUE

        @JvmStatic
        fun createOrientationHelper(layoutManager: RecyclerView.LayoutManager, orientation: Int): OrientationHelper =
            if (orientation == HORIZONTAL) createHorizontalHelper(layoutManager) else createVerticalHelper(layoutManager)

        @JvmStatic
        fun createHorizontalHelper(lm: RecyclerView.LayoutManager): OrientationHelper = object : OrientationHelper(lm) {
            override fun getDecoratedStart(view: View): Int = bounds(view).left
            override fun getDecoratedEnd(view: View): Int = bounds(view).right
            override fun getDecoratedMeasurement(view: View): Int = bounds(view).width()
            override fun getStartAfterPadding(): Int = lm.getPaddingLeft()
            override fun getEndAfterPadding(): Int = lm.getWidth() - lm.getPaddingRight()
            override fun getEnd(): Int = lm.getWidth()
            override fun getTotalSpace(): Int = lm.getWidth() - lm.getPaddingLeft() - lm.getPaddingRight()
            override fun offsetChildren(amount: Int) = lm.offsetChildrenHorizontal(amount)
        }

        @JvmStatic
        fun createVerticalHelper(lm: RecyclerView.LayoutManager): OrientationHelper = object : OrientationHelper(lm) {
            override fun getDecoratedStart(view: View): Int = bounds(view).top
            override fun getDecoratedEnd(view: View): Int = bounds(view).bottom
            override fun getDecoratedMeasurement(view: View): Int = bounds(view).height()
            override fun getStartAfterPadding(): Int = lm.getPaddingTop()
            override fun getEndAfterPadding(): Int = lm.getHeight() - lm.getPaddingBottom()
            override fun getEnd(): Int = lm.getHeight()
            override fun getTotalSpace(): Int = lm.getHeight() - lm.getPaddingTop() - lm.getPaddingBottom()
            override fun offsetChildren(amount: Int) = lm.offsetChildrenVertical(amount)
        }
    }

    protected fun bounds(view: View): Rect = Rect().also { mLayoutManager.getDecoratedBoundsWithMargins(view, it) }
    abstract fun getDecoratedStart(view: View): Int
    abstract fun getDecoratedEnd(view: View): Int
    abstract fun getDecoratedMeasurement(view: View): Int
    abstract fun getStartAfterPadding(): Int
    abstract fun getEndAfterPadding(): Int
    abstract fun getEnd(): Int
    abstract fun getTotalSpace(): Int
    abstract fun offsetChildren(amount: Int)
    fun getLayoutManager(): RecyclerView.LayoutManager = mLayoutManager
}

/** SnapHelper: when a scroll ends, scrolls so the snap view is aligned */
abstract class SnapHelper : RecyclerView.OnFlingListener() {
    protected var mRecyclerView: RecyclerView? = null
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        var scrolled = false
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && scrolled) {
                scrolled = false
                snapToTargetExistingView()
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dx != 0 || dy != 0) scrolled = true
        }
    }

    open fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (mRecyclerView === recyclerView) return
        mRecyclerView?.let {
            it.removeOnScrollListener(scrollListener)
            it.setOnFlingListener(null)
        }
        mRecyclerView = recyclerView
        recyclerView?.let {
            if (it.getOnFlingListener() != null) throw IllegalStateException("An instance of OnFlingListener already set.")
            it.addOnScrollListener(scrollListener)
            it.setOnFlingListener(this)
        }
    }

    override fun onFling(velocityX: Int, velocityY: Int): Boolean = false

    fun snapToTargetExistingView() {
        val rv = mRecyclerView ?: return
        val lm = rv.getLayoutManager() ?: return
        val snapView = findSnapView(lm) ?: return
        val d = calculateDistanceToFinalSnap(lm, snapView) ?: return
        if (d[0] != 0 || d[1] != 0) rv.smoothScrollBy(d[0], d[1])
    }

    abstract fun calculateDistanceToFinalSnap(layoutManager: RecyclerView.LayoutManager, targetView: View): IntArray?
    abstract fun findSnapView(layoutManager: RecyclerView.LayoutManager?): View?
    abstract fun findTargetSnapPosition(layoutManager: RecyclerView.LayoutManager?, velocityX: Int, velocityY: Int): Int
    open fun calculateScrollDistance(velocityX: Int, velocityY: Int): IntArray = intArrayOf(0, 0)
}

/** LinearSnapHelper: centers the child closest to the center */
open class LinearSnapHelper : SnapHelper() {
    override fun calculateDistanceToFinalSnap(layoutManager: RecyclerView.LayoutManager, targetView: View): IntArray {
        val out = IntArray(2)
        val r = Rect()
        layoutManager.getDecoratedBoundsWithMargins(targetView, r)
        if (layoutManager.canScrollHorizontally()) {
            val center = layoutManager.getPaddingLeft() + (layoutManager.getWidth() - layoutManager.getPaddingLeft() - layoutManager.getPaddingRight()) / 2
            out[0] = (r.left + r.width() / 2) - center
        }
        if (layoutManager.canScrollVertically()) {
            val center = layoutManager.getPaddingTop() + (layoutManager.getHeight() - layoutManager.getPaddingTop() - layoutManager.getPaddingBottom()) / 2
            out[1] = (r.top + r.height() / 2) - center
        }
        return out
    }

    override fun findSnapView(layoutManager: RecyclerView.LayoutManager?): View? {
        val lm = layoutManager ?: return null
        var best: View? = null
        var bestDistance = Int.MAX_VALUE
        val horizontal = lm.canScrollHorizontally()
        val center = if (horizontal) lm.getPaddingLeft() + (lm.getWidth() - lm.getPaddingLeft() - lm.getPaddingRight()) / 2
        else lm.getPaddingTop() + (lm.getHeight() - lm.getPaddingTop() - lm.getPaddingBottom()) / 2
        val r = Rect()
        for (i in 0 until lm.getChildCount()) {
            val c = lm.getChildAt(i) ?: continue
            lm.getDecoratedBoundsWithMargins(c, r)
            val childCenter = if (horizontal) r.left + r.width() / 2 else r.top + r.height() / 2
            val d = abs(childCenter - center)
            if (d < bestDistance) {
                bestDistance = d
                best = c
            }
        }
        return best
    }

    override fun findTargetSnapPosition(layoutManager: RecyclerView.LayoutManager?, velocityX: Int, velocityY: Int): Int =
        findSnapView(layoutManager)?.let { layoutManager?.getPosition(it) } ?: RecyclerView.NO_POSITION
}

/** PagerSnapHelper: snaps one page (item) at a time, centered */
open class PagerSnapHelper : LinearSnapHelper()

/** DividerItemDecoration: a divider drawable between items */
open class DividerItemDecoration(context: Context, orientation: Int) : RecyclerView.ItemDecoration() {
    companion object {
        const val HORIZONTAL = LinearLayoutManager.HORIZONTAL
        const val VERTICAL = LinearLayoutManager.VERTICAL
    }

    private var mDivider: android.graphics.drawable.Drawable? = null
    private var mOrientation = orientation

    open fun setOrientation(orientation: Int) {
        mOrientation = orientation
    }

    open fun setDrawable(drawable: android.graphics.drawable.Drawable) {
        mDivider = drawable
    }

    open fun getDrawable(): android.graphics.drawable.Drawable? = mDivider

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val divider = mDivider ?: return
        for (i in 0 until parent.getChildCount()) {
            val child = parent.getChildAt(i) ?: continue
            if (mOrientation == VERTICAL) {
                val bottom = child.getBottom() + child.getTranslationY().toInt()
                divider.setBounds(parent.getPaddingLeft(), bottom, parent.getWidth() - parent.getPaddingRight(), bottom + divider.getIntrinsicHeight())
            } else {
                val right = child.getRight() + child.getTranslationX().toInt()
                divider.setBounds(right, parent.getPaddingTop(), right + divider.getIntrinsicWidth(), parent.getHeight() - parent.getPaddingBottom())
            }
            divider.draw(c)
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val divider = mDivider
        if (divider == null) {
            outRect.set(0, 0, 0, 0)
            return
        }
        if (mOrientation == VERTICAL) outRect.set(0, 0, 0, divider.getIntrinsicHeight()) else outRect.set(0, 0, divider.getIntrinsicWidth(), 0)
    }
}

// ================================================================================ list diffing

interface ListUpdateCallback {
    fun onInserted(position: Int, count: Int)
    fun onRemoved(position: Int, count: Int)
    fun onMoved(fromPosition: Int, toPosition: Int)
    fun onChanged(position: Int, count: Int, payload: Any?)
}

open class AdapterListUpdateCallback(private val mAdapter: RecyclerView.Adapter<*>) : ListUpdateCallback {
    override fun onInserted(position: Int, count: Int) = mAdapter.notifyItemRangeInserted(position, count)
    override fun onRemoved(position: Int, count: Int) = mAdapter.notifyItemRangeRemoved(position, count)
    override fun onMoved(fromPosition: Int, toPosition: Int) = mAdapter.notifyItemMoved(fromPosition, toPosition)
    override fun onChanged(position: Int, count: Int, payload: Any?) = mAdapter.notifyItemRangeChanged(position, count, payload)
}

/** Merges consecutive operations of the same kind (BatchingListUpdateCallback) */
open class BatchingListUpdateCallback(private val mWrapped: ListUpdateCallback) : ListUpdateCallback {
    private var type = 0
    private var start = -1
    private var count = -1
    private var payload: Any? = null

    fun dispatchLastEvent() {
        when (type) {
            1 -> mWrapped.onInserted(start, count)
            2 -> mWrapped.onRemoved(start, count)
            3 -> mWrapped.onChanged(start, count, payload)
        }
        payload = null
        type = 0
    }

    override fun onInserted(position: Int, count: Int) {
        if (type == 1 && position >= start && position <= start + this.count) {
            this.count += count
            start = min(position, start)
            return
        }
        dispatchLastEvent()
        start = position
        this.count = count
        type = 1
    }

    override fun onRemoved(position: Int, count: Int) {
        if (type == 2 && start >= position && start <= position + count) {
            this.count += count
            start = position
            return
        }
        dispatchLastEvent()
        start = position
        this.count = count
        type = 2
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        dispatchLastEvent()
        mWrapped.onMoved(fromPosition, toPosition)
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        if (type == 3 && !(position > start + this.count || position + count < start || this.payload != payload)) {
            val previousEnd = start + this.count
            start = min(position, start)
            this.count = max(previousEnd, position + count) - start
            return
        }
        dispatchLastEvent()
        start = position
        this.count = count
        this.payload = payload
        type = 3
    }
}

object DiffUtil {
    abstract class ItemCallback<T> {
        abstract fun areItemsTheSame(oldItem: T, newItem: T): Boolean
        abstract fun areContentsTheSame(oldItem: T, newItem: T): Boolean
        open fun getChangePayload(oldItem: T, newItem: T): Any? = null
    }

    abstract class Callback {
        abstract fun getOldListSize(): Int
        abstract fun getNewListSize(): Int
        abstract fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean
        abstract fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean
        open fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? = null
    }

    /** Result of the diff: matched (old, new) pairs in order, dispatched as removes / inserts / changes */
    class DiffResult internal constructor(
        private val callback: Callback,
        private val matches: List<IntArray>,
        private val oldSize: Int,
        private val newSize: Int,
    ) {
        companion object {
            const val NO_POSITION = -1
        }

        private val oldToNew = IntArray(oldSize) { NO_POSITION }.also { a -> for (m in matches) a[m[0]] = m[1] }
        private val newToOld = IntArray(newSize) { NO_POSITION }.also { a -> for (m in matches) a[m[1]] = m[0] }

        fun convertOldPositionToNew(oldListPosition: Int): Int = oldToNew[oldListPosition]
        fun convertNewPositionToOld(newListPosition: Int): Int = newToOld[newListPosition]

        fun dispatchUpdatesTo(adapter: RecyclerView.Adapter<*>) = dispatchUpdatesTo(AdapterListUpdateCallback(adapter))

        fun dispatchUpdatesTo(updateCallback: ListUpdateCallback) {
            val batching = if (updateCallback is BatchingListUpdateCallback) updateCallback else BatchingListUpdateCallback(updateCallback)
            // walk the matches backwards so positions before the current point stay valid
            var oldEnd = oldSize
            var newEnd = newSize
            for (i in matches.indices.reversed() + listOf(-1)) {
                val oldPos = if (i >= 0) matches[i][0] else -1
                val newPos = if (i >= 0) matches[i][1] else -1
                // items between this match and the previous one: removed from old, inserted from new
                val removed = oldEnd - oldPos - 1
                val inserted = newEnd - newPos - 1
                if (removed > 0) batching.onRemoved(oldPos + 1, removed)
                if (inserted > 0) batching.onInserted(oldPos + 1, inserted)
                if (i >= 0 && !callback.areContentsTheSame(oldPos, newPos)) {
                    batching.onChanged(oldPos, 1, callback.getChangePayload(oldPos, newPos))
                }
                oldEnd = oldPos
                newEnd = newPos
            }
            batching.dispatchLastEvent()
        }
    }

    @JvmStatic
    fun calculateDiff(cb: Callback): DiffResult = calculateDiff(cb, true)

    /** Myers' diff (O((N+M)D)): the longest common subsequence of same items */
    @JvmStatic
    fun calculateDiff(cb: Callback, detectMoves: Boolean): DiffResult {
        val n = cb.getOldListSize()
        val m = cb.getNewListSize()
        // trim common prefix and suffix
        var prefix = 0
        while (prefix < n && prefix < m && cb.areItemsTheSame(prefix, prefix)) prefix++
        var suffix = 0
        while (suffix < n - prefix && suffix < m - prefix && cb.areItemsTheSame(n - 1 - suffix, m - 1 - suffix)) suffix++
        val a0 = prefix
        val b0 = prefix
        val an = n - prefix - suffix
        val bm = m - prefix - suffix
        val matches = ArrayList<IntArray>()
        for (i in 0 until prefix) matches.add(intArrayOf(i, i))
        if (an > 0 && bm > 0) {
            val max = an + bm
            val offset = max + 1
            val v = IntArray(2 * max + 3)
            val trace = ArrayList<IntArray>()
            var found = false
            var dEnd = 0
            loop@ for (d in 0..max) {
                trace.add(v.copyOf())
                var k = -d
                while (k <= d) {
                    var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) v[offset + k + 1] else v[offset + k - 1] + 1
                    var y = x - k
                    while (x < an && y < bm && cb.areItemsTheSame(a0 + x, b0 + y)) {
                        x++
                        y++
                    }
                    v[offset + k] = x
                    if (x >= an && y >= bm) {
                        found = true
                        dEnd = d
                        break@loop
                    }
                    k += 2
                }
            }
            if (found) {
                // backtrack the snakes
                val snakes = ArrayList<IntArray>()
                var x = an
                var y = bm
                for (d in dEnd downTo 0) {
                    val vd = trace[d]
                    val k = x - y
                    val prevK = if (k == -d || (k != d && vd[offset + k - 1] < vd[offset + k + 1])) k + 1 else k - 1
                    val prevX = if (d == 0) 0 else vd[offset + prevK]
                    val prevY = prevX - prevK
                    var sx = x
                    var sy = y
                    while (sx > (if (d == 0) 0 else if (prevK == k + 1) prevX else prevX + 1) && sy > (if (d == 0) 0 else if (prevK == k + 1) prevY + 1 else prevY)) {
                        sx--
                        sy--
                        snakes.add(intArrayOf(a0 + sx, b0 + sy))
                    }
                    x = if (d == 0) 0 else prevX
                    y = if (d == 0) 0 else prevY
                }
                snakes.sortBy { it[0] }
                matches.addAll(snakes)
            }
        }
        for (i in 0 until suffix) matches.add(intArrayOf(n - suffix + i, m - suffix + i))
        @Suppress("UNUSED_VARIABLE") val unused = detectMoves
        return DiffResult(cb, matches, n, m)
    }
}

class AsyncDifferConfig<T> private constructor(val diffCallback: DiffUtil.ItemCallback<T>) {
    class Builder<T>(private val mDiffCallback: DiffUtil.ItemCallback<T>) {
        fun setBackgroundThreadExecutor(executor: java.util.concurrent.Executor?): Builder<T> = this
        fun build(): AsyncDifferConfig<T> = AsyncDifferConfig(mDiffCallback)
    }
}

/** AsyncListDiffer: diffs submitted lists off the main thread and dispatches the updates on it */
open class AsyncListDiffer<T>(private val mUpdateCallback: ListUpdateCallback, private val mConfig: AsyncDifferConfig<T>) {
    fun interface ListListener<T> {
        fun onCurrentListChanged(previousList: List<T>, currentList: List<T>)
    }

    constructor(adapter: RecyclerView.Adapter<*>, diffCallback: DiffUtil.ItemCallback<T>) :
        this(AdapterListUpdateCallback(adapter), AsyncDifferConfig.Builder(diffCallback).build())

    private var mList: List<T>? = null
    private var mReadOnlyList: List<T> = emptyList()
    private var mMaxScheduledGeneration = 0
    private val mListeners = java.util.concurrent.CopyOnWriteArrayList<ListListener<T>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    val currentList: List<T> get() = mReadOnlyList

    fun submitList(newList: List<T>?) = submitList(newList, null)

    fun submitList(newList: List<T>?, commitCallback: Runnable?) {
        val runGeneration = ++mMaxScheduledGeneration
        if (newList === mList) {
            commitCallback?.run()
            return
        }
        val previousList = mReadOnlyList
        if (newList == null) {
            val countRemoved = mList?.size ?: 0
            mList = null
            mReadOnlyList = emptyList()
            if (countRemoved > 0) mUpdateCallback.onRemoved(0, countRemoved)
            onCurrentListChanged(previousList, commitCallback)
            return
        }
        val oldList = mList
        if (oldList == null) {
            mList = newList
            mReadOnlyList = java.util.Collections.unmodifiableList(newList)
            mUpdateCallback.onInserted(0, newList.size)
            onCurrentListChanged(previousList, commitCallback)
            return
        }
        val cb = mConfig.diffCallback
        diffExecutor.execute {
            val result = try {
                DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = oldList.size
                    override fun getNewListSize(): Int = newList.size
                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        val o = oldList[oldItemPosition]
                        val n = newList[newItemPosition]
                        return if (o != null && n != null) cb.areItemsTheSame(o, n) else o == null && n == null
                    }

                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        val o = oldList[oldItemPosition]
                        val n = newList[newItemPosition]
                        return if (o != null && n != null) cb.areContentsTheSame(o, n) else o == null && n == null
                    }

                    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                        val o = oldList[oldItemPosition]
                        val n = newList[newItemPosition]
                        return if (o != null && n != null) cb.getChangePayload(o, n) else null
                    }
                })
            } catch (t: Throwable) {
                android.util.Log.e("AsyncListDiffer", "diff failed", t)
                null
            }
            mainHandler.post {
                if (mMaxScheduledGeneration != runGeneration) return@post
                val prev = mReadOnlyList
                mList = newList
                mReadOnlyList = java.util.Collections.unmodifiableList(newList)
                if (result != null) result.dispatchUpdatesTo(mUpdateCallback)
                else {
                    mUpdateCallback.onRemoved(0, oldList.size)
                    mUpdateCallback.onInserted(0, newList.size)
                }
                onCurrentListChanged(prev, commitCallback)
            }
        }
    }

    private fun onCurrentListChanged(previousList: List<T>, commitCallback: Runnable?) {
        for (l in mListeners) l.onCurrentListChanged(previousList, mReadOnlyList)
        commitCallback?.run()
    }

    fun addListListener(listener: ListListener<T>) {
        mListeners.add(listener)
    }

    fun removeListListener(listener: ListListener<T>) {
        mListeners.remove(listener)
    }

    private companion object {
        val diffExecutor: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
            Thread(r, "AsyncListDiffer").apply { isDaemon = true }
        }
    }
}

abstract class ListAdapter<T, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH> {
    private val mDiffer: AsyncListDiffer<T>

    protected constructor(diffCallback: DiffUtil.ItemCallback<T>) : super() {
        mDiffer = AsyncListDiffer(AdapterListUpdateCallback(this), AsyncDifferConfig.Builder(diffCallback).build())
        mDiffer.addListListener { previous, current -> onCurrentListChanged(previous, current) }
    }

    protected constructor(config: AsyncDifferConfig<T>) : super() {
        mDiffer = AsyncListDiffer(AdapterListUpdateCallback(this), config)
        mDiffer.addListListener { previous, current -> onCurrentListChanged(previous, current) }
    }

    open fun submitList(list: List<T>?) = mDiffer.submitList(list)
    open fun submitList(list: List<T>?, commitCallback: Runnable?) = mDiffer.submitList(list, commitCallback)
    protected open fun getItem(position: Int): T = mDiffer.currentList[position]
    override fun getItemCount(): Int = mDiffer.currentList.size
    val currentList: List<T> get() = mDiffer.currentList
    open fun onCurrentListChanged(previousList: List<T>, currentList: List<T>) {}
}

/** ItemTouchHelper: drag and swipe helper; attaches, drag/swipe gestures are not supported yet */
open class ItemTouchHelper(private val mCallback: Callback) : RecyclerView.ItemDecoration(), RecyclerView.OnChildAttachStateChangeListener {
    companion object {
        const val UP = 1
        const val DOWN = 1 shl 1
        const val LEFT = 1 shl 2
        const val RIGHT = 1 shl 3
        const val START = LEFT shl 2
        const val END = RIGHT shl 2
        const val ACTION_STATE_IDLE = 0
        const val ACTION_STATE_SWIPE = 1
        const val ACTION_STATE_DRAG = 2
        const val ANIMATION_TYPE_SWIPE_SUCCESS = 1 shl 1
        const val ANIMATION_TYPE_SWIPE_CANCEL = 1 shl 2
        const val ANIMATION_TYPE_DRAG = 1 shl 3
    }

    abstract class Callback {
        companion object {
            const val DEFAULT_DRAG_ANIMATION_DURATION = 200
            const val DEFAULT_SWIPE_ANIMATION_DURATION = 250

            @JvmStatic
            fun makeMovementFlags(dragFlags: Int, swipeFlags: Int): Int =
                makeFlag(ACTION_STATE_IDLE, swipeFlags or dragFlags) or makeFlag(ACTION_STATE_SWIPE, swipeFlags) or makeFlag(ACTION_STATE_DRAG, dragFlags)

            @JvmStatic
            fun makeFlag(actionState: Int, directions: Int): Int = directions shl (actionState * 8)

            @JvmStatic
            fun convertToRelativeDirection(flags: Int, layoutDirection: Int): Int = flags
        }

        abstract fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int
        abstract fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean
        abstract fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)
        open fun isLongPressDragEnabled(): Boolean = true
        open fun isItemViewSwipeEnabled(): Boolean = true
        open fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {}
        open fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {}
        open fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = true
        open fun onMoved(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, fromPos: Int, target: RecyclerView.ViewHolder, toPos: Int, x: Int, y: Int) {}
        open fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = .5f
        open fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float = .5f
        open fun getAnimationDuration(recyclerView: RecyclerView, animationType: Int, animateDx: Float, animateDy: Float): Long = 200
        open fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {}
        open fun onChildDrawOver(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder?, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {}
        open fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView, viewSize: Int, viewSizeOutOfBounds: Int, totalSize: Int, msSinceStartScroll: Long): Int = 0
    }

    abstract class SimpleCallback(private var mDefaultDragDirs: Int, private var mDefaultSwipeDirs: Int) : Callback() {
        open fun setDefaultSwipeDirs(defaultSwipeDirs: Int) {
            mDefaultSwipeDirs = defaultSwipeDirs
        }

        open fun setDefaultDragDirs(defaultDragDirs: Int) {
            mDefaultDragDirs = defaultDragDirs
        }

        open fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int = mDefaultSwipeDirs
        open fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int = mDefaultDragDirs
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            makeMovementFlags(getDragDirs(recyclerView, viewHolder), getSwipeDirs(recyclerView, viewHolder))
    }

    private var mRecyclerView: RecyclerView? = null

    open fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (mRecyclerView === recyclerView) return
        mRecyclerView?.let {
            it.removeItemDecoration(this)
            it.removeOnChildAttachStateChangeListener(this)
        }
        mRecyclerView = recyclerView
        recyclerView?.let {
            it.addItemDecoration(this)
            it.addOnChildAttachStateChangeListener(this)
        }
    }

    open fun startDrag(viewHolder: RecyclerView.ViewHolder) {}
    open fun startSwipe(viewHolder: RecyclerView.ViewHolder) {}
    override fun onChildViewAttachedToWindow(view: View) {}
    override fun onChildViewDetachedFromWindow(view: View) {}
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.setEmpty()
    }

    @Suppress("unused")
    private val callback get() = mCallback
}
