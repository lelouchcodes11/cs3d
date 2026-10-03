@file:JvmName("LayoutsKt")

package android.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.lagradost.desktop.runtime.ui.ViewAttributes
import kotlin.math.max
import kotlin.math.min

/*
 * Framework layouts with the measure and layout passes of AOSP (FrameLayout, LinearLayout,
 * RelativeLayout, ScrollView ...), left to right only.
 */

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val EXACTLY = View.MeasureSpec.EXACTLY
private const val AT_MOST = View.MeasureSpec.AT_MOST
private const val UNSPECIFIED = View.MeasureSpec.UNSPECIFIED

private fun spec(size: Int, mode: Int) = View.MeasureSpec.makeMeasureSpec(size, mode)
private fun modeOf(spec: Int) = View.MeasureSpec.getMode(spec)
private fun sizeOf(spec: Int) = View.MeasureSpec.getSize(spec)
private val View.mlp: ViewGroup.MarginLayoutParams
    get() = getLayoutParams() as? ViewGroup.MarginLayoutParams ?: ViewGroup.MarginLayoutParams(WRAP, WRAP)

open class LinearLayout : ViewGroup {
    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
        const val SHOW_DIVIDER_NONE = 0
        const val SHOW_DIVIDER_BEGINNING = 1
        const val SHOW_DIVIDER_MIDDLE = 2
        const val SHOW_DIVIDER_END = 4
        private const val INDEX_CENTER_VERTICAL = 0
        private const val INDEX_TOP = 1
        private const val INDEX_BOTTOM = 2
        private const val INDEX_FILL = 3
    }

    open class LayoutParams : ViewGroup.MarginLayoutParams {
        companion object {
            @Deprecated("") const val FILL_PARENT = ViewGroup.LayoutParams.FILL_PARENT
            const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
            const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        @JvmField
        var weight = 0f

        @JvmField
        var gravity = -1

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs) {
            if (attrs != null) ViewAttributes.applyLinearLayoutParams(this, c, attrs)
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(width: Int, height: Int, weight: Float) : super(width, height) {
            this.weight = weight
        }

        constructor(p: ViewGroup.LayoutParams) : super(p) {
            if (p is LayoutParams) {
                weight = p.weight
                gravity = p.gravity
            }
        }

        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: LayoutParams) : super(source) {
            weight = source.weight
            gravity = source.gravity
        }
    }

    private var mOrientation = HORIZONTAL
    private var mGravity = Gravity.START or Gravity.TOP
    private var mWeightSum = -1f
    private var mBaselineAligned = true
    private var mBaselineAlignedChildIndex = -1
    private var mBaselineChildTop = 0
    private var mUseLargestChild = false
    private var mDivider: Drawable? = null
    private var mDividerWidth = 0
    private var mDividerHeight = 0
    private var mShowDividers = SHOW_DIVIDER_NONE
    private var mDividerPadding = 0
    private var mTotalLength = 0
    private val mMaxAscent = IntArray(4)
    private val mMaxDescent = IntArray(4)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) ViewAttributes.applyLinearLayoutAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : this(context, attrs)

    open fun setOrientation(orientation: Int) {
        if (mOrientation != orientation) {
            mOrientation = orientation
            requestLayout()
        }
    }

    open fun getOrientation(): Int = mOrientation
    open fun setGravity(gravity: Int) {
        var g = gravity
        if ((g and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) g = g or Gravity.START
        if ((g and Gravity.VERTICAL_GRAVITY_MASK) == 0) g = g or Gravity.TOP
        if (mGravity != g) {
            mGravity = g
            requestLayout()
        }
    }

    open fun getGravity(): Int = mGravity
    open fun setHorizontalGravity(horizontalGravity: Int) = setGravity((mGravity and Gravity.VERTICAL_GRAVITY_MASK) or (horizontalGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK))
    open fun setVerticalGravity(verticalGravity: Int) = setGravity((mGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) or (verticalGravity and Gravity.VERTICAL_GRAVITY_MASK))
    open fun setWeightSum(weightSum: Float) {
        mWeightSum = max(0f, weightSum)
    }

    open fun getWeightSum(): Float = mWeightSum
    open fun setBaselineAligned(baselineAligned: Boolean) {
        mBaselineAligned = baselineAligned
    }

    open fun isBaselineAligned(): Boolean = mBaselineAligned
    open fun setBaselineAlignedChildIndex(i: Int) {
        mBaselineAlignedChildIndex = i
    }

    open fun getBaselineAlignedChildIndex(): Int = mBaselineAlignedChildIndex
    open fun setMeasureWithLargestChildEnabled(enabled: Boolean) {
        mUseLargestChild = enabled
    }

    open fun isMeasureWithLargestChildEnabled(): Boolean = mUseLargestChild
    open fun setDividerDrawable(divider: Drawable?) {
        if (divider === mDivider) return
        mDivider = divider
        mDividerWidth = divider?.getIntrinsicWidth()?.coerceAtLeast(0) ?: 0
        mDividerHeight = divider?.getIntrinsicHeight()?.coerceAtLeast(0) ?: 0
        requestLayout()
    }

    open fun getDividerDrawable(): Drawable? = mDivider
    open fun getDividerWidth(): Int = mDividerWidth
    open fun setShowDividers(showDividers: Int) {
        if (showDividers != mShowDividers) requestLayout()
        mShowDividers = showDividers
    }

    open fun getShowDividers(): Int = mShowDividers
    open fun setDividerPadding(padding: Int) {
        mDividerPadding = padding
        invalidate()
    }

    open fun getDividerPadding(): Int = mDividerPadding

    override fun getBaseline(): Int {
        if (mBaselineAlignedChildIndex < 0) return super.getBaseline()
        if (getChildCount() <= mBaselineAlignedChildIndex) return -1
        val child = getChildAt(mBaselineAlignedChildIndex) ?: return -1
        val childBaseline = child.getBaseline()
        if (childBaseline == -1) return -1
        var childTop = mBaselineChildTop
        if (mOrientation == VERTICAL) {
            when (mGravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.BOTTOM -> childTop = getBottom() - getTop() - getPaddingBottom() - mTotalLength
                Gravity.CENTER_VERTICAL -> childTop += ((getBottom() - getTop() - getPaddingTop() - getPaddingBottom()) - mTotalLength) / 2
            }
        }
        return childTop + child.mlp.topMargin + childBaseline
    }

    protected open fun getVirtualChildCount(): Int = getChildCount()
    protected open fun getVirtualChildAt(index: Int): View? = getChildAt(index)

    protected open fun hasDividerBeforeChildAt(childIndex: Int): Boolean {
        if (mShowDividers == SHOW_DIVIDER_NONE || mDivider == null) return false
        if (childIndex == getVirtualChildCount()) return (mShowDividers and SHOW_DIVIDER_END) != 0
        var allGoneBefore = true
        for (i in childIndex - 1 downTo 0) {
            val c = getVirtualChildAt(i)
            if (c != null && c.getVisibility() != GONE) {
                allGoneBefore = false
                break
            }
        }
        return if (allGoneBefore) (mShowDividers and SHOW_DIVIDER_BEGINNING) != 0 else (mShowDividers and SHOW_DIVIDER_MIDDLE) != 0
    }

    private val View.llp: LayoutParams get() = getLayoutParams() as? LayoutParams ?: LayoutParams(layoutParamsOrNull() ?: generateDefaultLayoutParams())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mOrientation == VERTICAL) measureVertical(widthMeasureSpec, heightMeasureSpec)
        else measureHorizontal(widthMeasureSpec, heightMeasureSpec)
    }

    private fun measureVertical(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mTotalLength = 0
        var maxWidth = 0
        var childState = 0
        var alternativeMaxWidth = 0
        var weightedMaxWidth = 0
        var allFillParent = true
        var totalWeight = 0f
        val count = getVirtualChildCount()
        val widthMode = modeOf(widthMeasureSpec)
        val heightMode = modeOf(heightMeasureSpec)
        var matchWidth = false
        var skippedMeasure = false
        val baselineChildIndex = mBaselineAlignedChildIndex
        val useLargestChild = mUseLargestChild
        var largestChildHeight = Int.MIN_VALUE
        var consumedExcessSpace = 0
        var nonSkippedChildCount = 0

        for (i in 0 until count) {
            val child = getVirtualChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            nonSkippedChildCount++
            if (hasDividerBeforeChildAt(i)) mTotalLength += mDividerHeight
            val lp = child.llp
            totalWeight += lp.weight
            val useExcessSpace = lp.height == 0 && lp.weight > 0
            if (heightMode == EXACTLY && useExcessSpace) {
                val totalLength = mTotalLength
                mTotalLength = max(totalLength, totalLength + lp.topMargin + lp.bottomMargin)
                skippedMeasure = true
            } else {
                if (useExcessSpace) lp.height = WRAP
                val usedHeight = if (totalWeight == 0f) mTotalLength else 0
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, usedHeight)
                val childHeight = child.getMeasuredHeight()
                if (useExcessSpace) {
                    lp.height = 0
                    consumedExcessSpace += childHeight
                }
                val totalLength = mTotalLength
                mTotalLength = max(totalLength, totalLength + childHeight + lp.topMargin + lp.bottomMargin)
                if (useLargestChild) largestChildHeight = max(childHeight, largestChildHeight)
            }
            if (baselineChildIndex >= 0 && baselineChildIndex == i + 1) mBaselineChildTop = mTotalLength
            var matchWidthLocally = false
            if (widthMode != EXACTLY && lp.width == MATCH) {
                matchWidth = true
                matchWidthLocally = true
            }
            val margin = lp.leftMargin + lp.rightMargin
            val measuredWidth = child.getMeasuredWidth() + margin
            maxWidth = max(maxWidth, measuredWidth)
            childState = combineMeasuredStates(childState, child.getMeasuredState())
            allFillParent = allFillParent && lp.width == MATCH
            if (lp.weight > 0) weightedMaxWidth = max(weightedMaxWidth, if (matchWidthLocally) margin else measuredWidth)
            else alternativeMaxWidth = max(alternativeMaxWidth, if (matchWidthLocally) margin else measuredWidth)
        }
        if (nonSkippedChildCount > 0 && hasDividerBeforeChildAt(count)) mTotalLength += mDividerHeight

        if (useLargestChild && (heightMode == AT_MOST || heightMode == UNSPECIFIED)) {
            mTotalLength = 0
            for (i in 0 until count) {
                val child = getVirtualChildAt(i) ?: continue
                if (child.getVisibility() == GONE) continue
                val lp = child.llp
                val totalLength = mTotalLength
                mTotalLength = max(totalLength, totalLength + largestChildHeight + lp.topMargin + lp.bottomMargin)
            }
        }

        mTotalLength += getPaddingTop() + getPaddingBottom()
        var heightSize = max(mTotalLength, getSuggestedMinimumHeight())
        val heightSizeAndState = resolveSizeAndState(heightSize, heightMeasureSpec, 0)
        heightSize = heightSizeAndState and MEASURED_SIZE_MASK
        var remainingExcess = heightSize - mTotalLength + consumedExcessSpace
        if (skippedMeasure || totalWeight > 0f) {
            var remainingWeightSum = if (mWeightSum > 0f) mWeightSum else totalWeight
            mTotalLength = 0
            for (i in 0 until count) {
                val child = getVirtualChildAt(i) ?: continue
                if (child.getVisibility() == GONE) continue
                val lp = child.llp
                val childWeight = lp.weight
                if (childWeight > 0) {
                    val share = (childWeight * remainingExcess / remainingWeightSum).toInt()
                    remainingExcess -= share
                    remainingWeightSum -= childWeight
                    val childHeight = when {
                        mUseLargestChild && heightMode != EXACTLY -> largestChildHeight
                        lp.height == 0 -> share
                        else -> child.getMeasuredHeight() + share
                    }
                    val childHeightMeasureSpec = spec(max(0, childHeight), EXACTLY)
                    val childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin, lp.width)
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
                    childState = combineMeasuredStates(childState, child.getMeasuredState() and (MEASURED_STATE_MASK shr MEASURED_HEIGHT_STATE_SHIFT))
                }
                val margin = lp.leftMargin + lp.rightMargin
                val measuredWidth = child.getMeasuredWidth() + margin
                maxWidth = max(maxWidth, measuredWidth)
                val matchWidthLocally = widthMode != EXACTLY && lp.width == MATCH
                alternativeMaxWidth = max(alternativeMaxWidth, if (matchWidthLocally) margin else measuredWidth)
                allFillParent = allFillParent && lp.width == MATCH
                val totalLength = mTotalLength
                mTotalLength = max(totalLength, totalLength + child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin)
            }
            mTotalLength += getPaddingTop() + getPaddingBottom()
        } else {
            alternativeMaxWidth = max(alternativeMaxWidth, weightedMaxWidth)
            if (useLargestChild && heightMode != EXACTLY) {
                for (i in 0 until count) {
                    val child = getVirtualChildAt(i) ?: continue
                    if (child.getVisibility() == GONE) continue
                    if (child.llp.weight > 0) child.measure(spec(child.getMeasuredWidth(), EXACTLY), spec(largestChildHeight, EXACTLY))
                }
            }
        }
        if (!allFillParent && widthMode != EXACTLY) maxWidth = alternativeMaxWidth
        maxWidth += getPaddingLeft() + getPaddingRight()
        maxWidth = max(maxWidth, getSuggestedMinimumWidth())
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState), heightSizeAndState)
        if (matchWidth) forceUniformWidth(count, heightMeasureSpec)
    }

    private fun forceUniformWidth(count: Int, heightMeasureSpec: Int) {
        val uniformMeasureSpec = spec(getMeasuredWidth(), EXACTLY)
        for (i in 0 until count) {
            val child = getVirtualChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val lp = child.llp
            if (lp.width == MATCH) {
                val oldHeight = lp.height
                lp.height = child.getMeasuredHeight()
                measureChildWithMargins(child, uniformMeasureSpec, 0, heightMeasureSpec, 0)
                lp.height = oldHeight
            }
        }
    }

    private fun measureHorizontal(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mTotalLength = 0
        var maxHeight = 0
        var childState = 0
        var alternativeMaxHeight = 0
        var weightedMaxHeight = 0
        var allFillParent = true
        var totalWeight = 0f
        val count = getVirtualChildCount()
        val widthMode = modeOf(widthMeasureSpec)
        val heightMode = modeOf(heightMeasureSpec)
        var matchHeight = false
        var skippedMeasure = false
        val maxAscent = mMaxAscent
        val maxDescent = mMaxDescent
        maxAscent.fill(-1)
        maxDescent.fill(-1)
        val baselineAligned = mBaselineAligned
        val useLargestChild = mUseLargestChild
        val isExactly = widthMode == EXACTLY
        var largestChildWidth = Int.MIN_VALUE
        var usedExcessSpace = 0
        var nonSkippedChildCount = 0

        fun addLength(w: Int) {
            mTotalLength = if (isExactly) mTotalLength + w else max(mTotalLength, mTotalLength + w)
        }

        fun baselineIndex(lp: LayoutParams): Int {
            val gravity = (if (lp.gravity < 0) mGravity else lp.gravity) and Gravity.VERTICAL_GRAVITY_MASK
            return ((gravity shr Gravity.AXIS_Y_SHIFT) and Gravity.AXIS_SPECIFIED.inv()) shr 1
        }

        for (i in 0 until count) {
            val child = getVirtualChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            nonSkippedChildCount++
            if (hasDividerBeforeChildAt(i)) mTotalLength += mDividerWidth
            val lp = child.llp
            totalWeight += lp.weight
            val useExcessSpace = lp.width == 0 && lp.weight > 0
            if (widthMode == EXACTLY && useExcessSpace) {
                addLength(lp.leftMargin + lp.rightMargin)
                if (baselineAligned) {
                    child.measure(spec(sizeOf(widthMeasureSpec), UNSPECIFIED), spec(sizeOf(heightMeasureSpec), UNSPECIFIED))
                } else {
                    skippedMeasure = true
                }
            } else {
                if (useExcessSpace) lp.width = WRAP
                val usedWidth = if (totalWeight == 0f) mTotalLength else 0
                measureChildWithMargins(child, widthMeasureSpec, usedWidth, heightMeasureSpec, 0)
                val childWidth = child.getMeasuredWidth()
                if (useExcessSpace) {
                    lp.width = 0
                    usedExcessSpace += childWidth
                }
                addLength(childWidth + lp.leftMargin + lp.rightMargin)
                if (useLargestChild) largestChildWidth = max(childWidth, largestChildWidth)
            }
            var matchHeightLocally = false
            if (heightMode != EXACTLY && lp.height == MATCH) {
                matchHeight = true
                matchHeightLocally = true
            }
            val margin = lp.topMargin + lp.bottomMargin
            val childHeight = child.getMeasuredHeight() + margin
            childState = combineMeasuredStates(childState, child.getMeasuredState())
            if (baselineAligned) {
                val childBaseline = child.getBaseline()
                if (childBaseline != -1) {
                    val index = baselineIndex(lp)
                    maxAscent[index] = max(maxAscent[index], childBaseline)
                    maxDescent[index] = max(maxDescent[index], childHeight - childBaseline)
                }
            }
            maxHeight = max(maxHeight, childHeight)
            allFillParent = allFillParent && lp.height == MATCH
            if (lp.weight > 0) weightedMaxHeight = max(weightedMaxHeight, if (matchHeightLocally) margin else childHeight)
            else alternativeMaxHeight = max(alternativeMaxHeight, if (matchHeightLocally) margin else childHeight)
        }
        if (nonSkippedChildCount > 0 && hasDividerBeforeChildAt(count)) mTotalLength += mDividerWidth

        if (maxAscent[INDEX_TOP] != -1 || maxAscent[INDEX_CENTER_VERTICAL] != -1 || maxAscent[INDEX_BOTTOM] != -1 || maxAscent[INDEX_FILL] != -1) {
            val ascent = max(maxAscent[INDEX_FILL], max(maxAscent[INDEX_CENTER_VERTICAL], max(maxAscent[INDEX_TOP], maxAscent[INDEX_BOTTOM])))
            val descent = max(maxDescent[INDEX_FILL], max(maxDescent[INDEX_CENTER_VERTICAL], max(maxDescent[INDEX_TOP], maxDescent[INDEX_BOTTOM])))
            maxHeight = max(maxHeight, ascent + descent)
        }

        if (useLargestChild && (widthMode == AT_MOST || widthMode == UNSPECIFIED)) {
            mTotalLength = 0
            for (i in 0 until count) {
                val child = getVirtualChildAt(i) ?: continue
                if (child.getVisibility() == GONE) continue
                val lp = child.llp
                addLength(largestChildWidth + lp.leftMargin + lp.rightMargin)
            }
        }

        mTotalLength += getPaddingLeft() + getPaddingRight()
        var widthSize = max(mTotalLength, getSuggestedMinimumWidth())
        val widthSizeAndState = resolveSizeAndState(widthSize, widthMeasureSpec, 0)
        widthSize = widthSizeAndState and MEASURED_SIZE_MASK
        var remainingExcess = widthSize - mTotalLength + usedExcessSpace
        if (skippedMeasure || totalWeight > 0f) {
            var remainingWeightSum = if (mWeightSum > 0f) mWeightSum else totalWeight
            maxAscent.fill(-1)
            maxDescent.fill(-1)
            maxHeight = -1
            mTotalLength = 0
            for (i in 0 until count) {
                val child = getVirtualChildAt(i) ?: continue
                if (child.getVisibility() == GONE) continue
                val lp = child.llp
                val childWeight = lp.weight
                if (childWeight > 0) {
                    val share = (childWeight * remainingExcess / remainingWeightSum).toInt()
                    remainingExcess -= share
                    remainingWeightSum -= childWeight
                    val childWidth = when {
                        mUseLargestChild && widthMode != EXACTLY -> largestChildWidth
                        lp.width == 0 -> share
                        else -> child.getMeasuredWidth() + share
                    }
                    val childWidthMeasureSpec = spec(max(0, childWidth), EXACTLY)
                    val childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height)
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
                    childState = combineMeasuredStates(childState, child.getMeasuredState() and MEASURED_STATE_MASK)
                }
                addLength(child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin)
                val matchHeightLocally = heightMode != EXACTLY && lp.height == MATCH
                val margin = lp.topMargin + lp.bottomMargin
                val childHeight = child.getMeasuredHeight() + margin
                maxHeight = max(maxHeight, childHeight)
                alternativeMaxHeight = max(alternativeMaxHeight, if (matchHeightLocally) margin else childHeight)
                allFillParent = allFillParent && lp.height == MATCH
                if (baselineAligned) {
                    val childBaseline = child.getBaseline()
                    if (childBaseline != -1) {
                        val index = baselineIndex(lp)
                        maxAscent[index] = max(maxAscent[index], childBaseline)
                        maxDescent[index] = max(maxDescent[index], childHeight - childBaseline)
                    }
                }
            }
            mTotalLength += getPaddingLeft() + getPaddingRight()
            if (maxAscent[INDEX_TOP] != -1 || maxAscent[INDEX_CENTER_VERTICAL] != -1 || maxAscent[INDEX_BOTTOM] != -1 || maxAscent[INDEX_FILL] != -1) {
                val ascent = max(maxAscent[INDEX_FILL], max(maxAscent[INDEX_CENTER_VERTICAL], max(maxAscent[INDEX_TOP], maxAscent[INDEX_BOTTOM])))
                val descent = max(maxDescent[INDEX_FILL], max(maxDescent[INDEX_CENTER_VERTICAL], max(maxDescent[INDEX_TOP], maxDescent[INDEX_BOTTOM])))
                maxHeight = max(maxHeight, ascent + descent)
            }
        } else {
            alternativeMaxHeight = max(alternativeMaxHeight, weightedMaxHeight)
            if (useLargestChild && widthMode != EXACTLY) {
                for (i in 0 until count) {
                    val child = getVirtualChildAt(i) ?: continue
                    if (child.getVisibility() == GONE) continue
                    if (child.llp.weight > 0) child.measure(spec(largestChildWidth, EXACTLY), spec(child.getMeasuredHeight(), EXACTLY))
                }
            }
        }
        if (!allFillParent && heightMode != EXACTLY) maxHeight = alternativeMaxHeight
        maxHeight += getPaddingTop() + getPaddingBottom()
        maxHeight = max(maxHeight, getSuggestedMinimumHeight())
        setMeasuredDimension(
            widthSizeAndState or (childState and MEASURED_STATE_MASK),
            resolveSizeAndState(maxHeight, heightMeasureSpec, childState shl MEASURED_HEIGHT_STATE_SHIFT)
        )
        if (matchHeight) forceUniformHeight(count, widthMeasureSpec)
    }

    private fun forceUniformHeight(count: Int, widthMeasureSpec: Int) {
        val uniformMeasureSpec = spec(getMeasuredHeight(), EXACTLY)
        for (i in 0 until count) {
            val child = getVirtualChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val lp = child.llp
            if (lp.height == MATCH) {
                val oldWidth = lp.width
                lp.width = child.getMeasuredWidth()
                measureChildWithMargins(child, widthMeasureSpec, 0, uniformMeasureSpec, 0)
                lp.width = oldWidth
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (mOrientation == VERTICAL) layoutVertical(l, t, r, b) else layoutHorizontal(l, t, r, b)
    }

    private fun layoutVertical(left: Int, top: Int, right: Int, bottom: Int) {
        val paddingLeft = getPaddingLeft()
        val width = right - left
        val childRight = width - getPaddingRight()
        val childSpace = width - paddingLeft - getPaddingRight()
        val count = getVirtualChildCount()
        val minorGravity = mGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK
        var childTop = when (mGravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.BOTTOM -> getPaddingTop() + bottom - top - mTotalLength
            Gravity.CENTER_VERTICAL -> getPaddingTop() + (bottom - top - mTotalLength) / 2
            else -> getPaddingTop()
        }
        for (i in 0 until count) {
            val child = getVirtualChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val childWidth = child.getMeasuredWidth()
            val childHeight = child.getMeasuredHeight()
            val lp = child.llp
            var gravity = lp.gravity
            if (gravity < 0) gravity = minorGravity
            val childLeft = when (gravity and 0x07) {
                Gravity.CENTER_HORIZONTAL -> paddingLeft + (childSpace - childWidth) / 2 + lp.leftMargin - lp.rightMargin
                Gravity.RIGHT -> childRight - childWidth - lp.rightMargin
                else -> paddingLeft + lp.leftMargin
            }
            if (hasDividerBeforeChildAt(i)) childTop += mDividerHeight
            childTop += lp.topMargin
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
            childTop += childHeight + lp.bottomMargin
        }
    }

    private fun layoutHorizontal(left: Int, top: Int, right: Int, bottom: Int) {
        val paddingTop = getPaddingTop()
        val height = bottom - top
        val childBottom = height - getPaddingBottom()
        val childSpace = height - paddingTop - getPaddingBottom()
        val count = getVirtualChildCount()
        val minorGravity = mGravity and Gravity.VERTICAL_GRAVITY_MASK
        var childLeft = when (mGravity and 0x07) {
            Gravity.RIGHT -> getPaddingLeft() + right - left - mTotalLength
            Gravity.CENTER_HORIZONTAL -> getPaddingLeft() + (right - left - mTotalLength) / 2
            else -> getPaddingLeft()
        }
        for (i in 0 until count) {
            val child = getVirtualChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val childWidth = child.getMeasuredWidth()
            val childHeight = child.getMeasuredHeight()
            val lp = child.llp
            val childBaseline = if (mBaselineAligned && lp.height != MATCH) child.getBaseline() else -1
            var gravity = lp.gravity
            if (gravity < 0) gravity = minorGravity
            val childTop = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.TOP -> paddingTop + lp.topMargin + if (childBaseline != -1) mMaxAscent[INDEX_TOP] - childBaseline else 0
                Gravity.CENTER_VERTICAL -> paddingTop + (childSpace - childHeight) / 2 + lp.topMargin - lp.bottomMargin
                Gravity.BOTTOM -> childBottom - childHeight - lp.bottomMargin -
                    if (childBaseline != -1) mMaxDescent[INDEX_BOTTOM] - (child.getMeasuredHeight() - childBaseline) else 0
                else -> paddingTop
            }
            if (hasDividerBeforeChildAt(i)) childLeft += mDividerWidth
            childLeft += lp.leftMargin
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
            childLeft += childWidth + lp.rightMargin
        }
    }

    /** LinearLayout.onDraw: the dividers (the renderer calls it when a divider is set) */
    fun drawDividers(canvas: Canvas) {
        val divider = mDivider ?: return
        val count = getVirtualChildCount()
        if (mOrientation == VERTICAL) {
            fun draw(top: Int) {
                divider.setBounds(getPaddingLeft() + mDividerPadding, top, getWidth() - getPaddingRight() - mDividerPadding, top + mDividerHeight)
                divider.draw(canvas)
            }
            for (i in 0 until count) {
                val child = getVirtualChildAt(i) ?: continue
                if (child.getVisibility() != GONE && hasDividerBeforeChildAt(i)) draw(child.getTop() - child.mlp.topMargin - mDividerHeight)
            }
            if (hasDividerBeforeChildAt(count)) {
                val last = (count - 1 downTo 0).mapNotNull { getVirtualChildAt(it) }.firstOrNull { it.getVisibility() != GONE }
                draw(if (last == null) getHeight() - getPaddingBottom() - mDividerHeight else last.getBottom() + last.mlp.bottomMargin)
            }
        } else {
            fun draw(left: Int) {
                divider.setBounds(left, getPaddingTop() + mDividerPadding, left + mDividerWidth, getHeight() - getPaddingBottom() - mDividerPadding)
                divider.draw(canvas)
            }
            for (i in 0 until count) {
                val child = getVirtualChildAt(i) ?: continue
                if (child.getVisibility() != GONE && hasDividerBeforeChildAt(i)) draw(child.getLeft() - child.mlp.leftMargin - mDividerWidth)
            }
            if (hasDividerBeforeChildAt(count)) {
                val last = (count - 1 downTo 0).mapNotNull { getVirtualChildAt(it) }.firstOrNull { it.getVisibility() != GONE }
                draw(if (last == null) getWidth() - getPaddingRight() - mDividerWidth else last.getRight() + last.mlp.rightMargin)
            }
        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
        if (mOrientation == HORIZONTAL) LayoutParams(WRAP, WRAP) else LayoutParams(MATCH, WRAP)

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = when (p) {
        is LayoutParams -> LayoutParams(p)
        is ViewGroup.MarginLayoutParams -> LayoutParams(p)
        else -> LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams
}

open class FrameLayout : ViewGroup {
    open class LayoutParams : ViewGroup.MarginLayoutParams {
        companion object {
            const val UNSPECIFIED_GRAVITY = -1
            const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
            const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        @JvmField
        var gravity = UNSPECIFIED_GRAVITY

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs) {
            if (attrs != null) ViewAttributes.applyFrameLayoutParams(this, c, attrs)
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(width: Int, height: Int, gravity: Int) : super(width, height) {
            this.gravity = gravity
        }

        constructor(source: ViewGroup.LayoutParams) : super(source) {
            if (source is LayoutParams) gravity = source.gravity
            if (source is LinearLayout.LayoutParams) gravity = source.gravity
        }

        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: LayoutParams) : super(source) {
            gravity = source.gravity
        }
    }

    private var mForegroundGravity = Gravity.FILL
    private var mMeasureAllChildren = false
    private val mMatchParentChildren = ArrayList<View>(1)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    open fun setForegroundGravity(foregroundGravity: Int) {
        mForegroundGravity = foregroundGravity
    }

    open fun getForegroundGravity(): Int = mForegroundGravity
    open fun setMeasureAllChildren(measureAll: Boolean) {
        mMeasureAllChildren = measureAll
    }

    open fun getMeasureAllChildren(): Boolean = mMeasureAllChildren
    @Deprecated("") open fun getConsiderGoneChildrenWhenMeasuring(): Boolean = mMeasureAllChildren

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = getChildCount()
        val measureMatchParentChildren = modeOf(widthMeasureSpec) != EXACTLY || modeOf(heightMeasureSpec) != EXACTLY
        mMatchParentChildren.clear()
        var maxHeight = 0
        var maxWidth = 0
        var childState = 0
        for (i in 0 until count) {
            val child = getChildAt(i) ?: continue
            if (mMeasureAllChildren || child.getVisibility() != GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
                val lp = child.mlp
                maxWidth = max(maxWidth, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin)
                maxHeight = max(maxHeight, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin)
                childState = combineMeasuredStates(childState, child.getMeasuredState())
                if (measureMatchParentChildren && (lp.width == MATCH || lp.height == MATCH)) mMatchParentChildren.add(child)
            }
        }
        maxWidth += getPaddingLeft() + getPaddingRight()
        maxHeight += getPaddingTop() + getPaddingBottom()
        maxHeight = max(maxHeight, getSuggestedMinimumHeight())
        maxWidth = max(maxWidth, getSuggestedMinimumWidth())
        getForeground()?.let {
            maxHeight = max(maxHeight, it.getMinimumHeight())
            maxWidth = max(maxWidth, it.getMinimumWidth())
        }
        setMeasuredDimension(
            resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
            resolveSizeAndState(maxHeight, heightMeasureSpec, childState shl MEASURED_HEIGHT_STATE_SHIFT)
        )
        if (mMatchParentChildren.size > 1) {
            for (child in mMatchParentChildren) {
                val lp = child.mlp
                val childWidthMeasureSpec = if (lp.width == MATCH) {
                    spec(max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - lp.leftMargin - lp.rightMargin), EXACTLY)
                } else getChildMeasureSpec(widthMeasureSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin, lp.width)
                val childHeightMeasureSpec = if (lp.height == MATCH) {
                    spec(max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - lp.topMargin - lp.bottomMargin), EXACTLY)
                } else getChildMeasureSpec(heightMeasureSpec, getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height)
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = layoutChildren(left, top, right, bottom, false)

    fun layoutChildren(left: Int, top: Int, right: Int, bottom: Int, forceLeftGravity: Boolean) {
        val parentLeft = getPaddingLeft()
        val parentRight = right - left - getPaddingRight()
        val parentTop = getPaddingTop()
        val parentBottom = bottom - top - getPaddingBottom()
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val lp = child.mlp
            val width = child.getMeasuredWidth()
            val height = child.getMeasuredHeight()
            var gravity = (lp as? LayoutParams)?.gravity ?: -1
            if (gravity == -1) gravity = Gravity.TOP or Gravity.START
            val childLeft = when (gravity and 0x07) {
                Gravity.CENTER_HORIZONTAL -> parentLeft + (parentRight - parentLeft - width) / 2 + lp.leftMargin - lp.rightMargin
                Gravity.RIGHT -> if (!forceLeftGravity) parentRight - width - lp.rightMargin else parentLeft + lp.leftMargin
                else -> parentLeft + lp.leftMargin
            }
            val childTop = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.CENTER_VERTICAL -> parentTop + (parentBottom - parentTop - height) / 2 + lp.topMargin - lp.bottomMargin
                Gravity.BOTTOM -> parentBottom - height - lp.bottomMargin
                else -> parentTop + lp.topMargin
            }
            child.layout(childLeft, childTop, childLeft + width, childTop + height)
        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(MATCH, MATCH)
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = when (p) {
        is LayoutParams -> LayoutParams(p)
        is ViewGroup.MarginLayoutParams -> LayoutParams(p as ViewGroup.MarginLayoutParams).also { if (p is LinearLayout.LayoutParams) it.gravity = p.gravity }
        else -> LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams
}

open class RelativeLayout : ViewGroup {
    companion object {
        const val TRUE = -1
        const val LEFT_OF = 0
        const val RIGHT_OF = 1
        const val ABOVE = 2
        const val BELOW = 3
        const val ALIGN_BASELINE = 4
        const val ALIGN_LEFT = 5
        const val ALIGN_TOP = 6
        const val ALIGN_RIGHT = 7
        const val ALIGN_BOTTOM = 8
        const val ALIGN_PARENT_LEFT = 9
        const val ALIGN_PARENT_TOP = 10
        const val ALIGN_PARENT_RIGHT = 11
        const val ALIGN_PARENT_BOTTOM = 12
        const val CENTER_IN_PARENT = 13
        const val CENTER_HORIZONTAL = 14
        const val CENTER_VERTICAL = 15
        const val START_OF = 16
        const val END_OF = 17
        const val ALIGN_START = 18
        const val ALIGN_END = 19
        const val ALIGN_PARENT_START = 20
        const val ALIGN_PARENT_END = 21
        const val VERB_COUNT = 22
        private const val VALUE_NOT_SET = Int.MIN_VALUE
        private val RULES_VERTICAL = intArrayOf(ABOVE, BELOW, ALIGN_BASELINE, ALIGN_TOP, ALIGN_BOTTOM)
        private val RULES_HORIZONTAL = intArrayOf(LEFT_OF, RIGHT_OF, ALIGN_LEFT, ALIGN_RIGHT, START_OF, END_OF, ALIGN_START, ALIGN_END)
    }

    open class LayoutParams : ViewGroup.MarginLayoutParams {
        private val mRules = IntArray(VERB_COUNT)
        private val mResolved = IntArray(VERB_COUNT)
        private var mResolvedDirty = true

        @JvmField
        var alignWithParent = false
        internal var mLeft = 0
        internal var mTop = 0
        internal var mRight = 0
        internal var mBottom = 0

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs) {
            if (attrs != null) ViewAttributes.applyRelativeLayoutParams(this, c, attrs)
        }

        constructor(w: Int, h: Int) : super(w, h)
        constructor(source: ViewGroup.LayoutParams) : super(source)
        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: LayoutParams) : super(source) {
            source.mRules.copyInto(mRules)
            alignWithParent = source.alignWithParent
        }

        open fun addRule(verb: Int) = addRule(verb, TRUE)
        open fun addRule(verb: Int, subject: Int) {
            mRules[verb] = subject
            mResolvedDirty = true
        }

        open fun removeRule(verb: Int) {
            mRules[verb] = 0
            mResolvedDirty = true
        }

        open fun getRule(verb: Int): Int = mRules[verb]
        open fun getRules(): IntArray = mRules

        /** Rules with START/END resolved for a left to right layout (LayoutParams.resolveRules) */
        open fun getRules(layoutDirection: Int): IntArray {
            if (!mResolvedDirty) return mResolved
            val r = mResolved
            mRules.copyInto(r)
            if ((r[ALIGN_START] != 0 || r[ALIGN_END] != 0) && (r[ALIGN_LEFT] != 0 || r[ALIGN_RIGHT] != 0)) {
                r[ALIGN_LEFT] = 0
                r[ALIGN_RIGHT] = 0
            }
            if (r[ALIGN_START] != 0) r[ALIGN_LEFT] = r[ALIGN_START].also { r[ALIGN_START] = 0 }
            if (r[ALIGN_END] != 0) r[ALIGN_RIGHT] = r[ALIGN_END].also { r[ALIGN_END] = 0 }
            if ((r[START_OF] != 0 || r[END_OF] != 0) && (r[LEFT_OF] != 0 || r[RIGHT_OF] != 0)) {
                r[LEFT_OF] = 0
                r[RIGHT_OF] = 0
            }
            if (r[START_OF] != 0) r[LEFT_OF] = r[START_OF].also { r[START_OF] = 0 }
            if (r[END_OF] != 0) r[RIGHT_OF] = r[END_OF].also { r[END_OF] = 0 }
            if ((r[ALIGN_PARENT_START] != 0 || r[ALIGN_PARENT_END] != 0) && (r[ALIGN_PARENT_LEFT] != 0 || r[ALIGN_PARENT_RIGHT] != 0)) {
                r[ALIGN_PARENT_LEFT] = 0
                r[ALIGN_PARENT_RIGHT] = 0
            }
            if (r[ALIGN_PARENT_START] != 0) r[ALIGN_PARENT_LEFT] = r[ALIGN_PARENT_START].also { r[ALIGN_PARENT_START] = 0 }
            if (r[ALIGN_PARENT_END] != 0) r[ALIGN_PARENT_RIGHT] = r[ALIGN_PARENT_END].also { r[ALIGN_PARENT_END] = 0 }
            mResolvedDirty = false
            return r
        }
    }

    private var mGravity = Gravity.START or Gravity.TOP
    private var mIgnoreGravity = View.NO_ID
    private var mBaselineView: View? = null
    private var mSortedHorizontal: List<View> = emptyList()
    private var mSortedVertical: List<View> = emptyList()
    private val mKeyNodes = HashMap<Int, View>()

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) ViewAttributes.applyRelativeLayoutAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : this(context, attrs)

    open fun setGravity(gravity: Int) {
        var g = gravity
        if ((g and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) g = g or Gravity.START
        if ((g and Gravity.VERTICAL_GRAVITY_MASK) == 0) g = g or Gravity.TOP
        if (mGravity != g) {
            mGravity = g
            requestLayout()
        }
    }

    open fun getGravity(): Int = mGravity
    open fun setIgnoreGravity(viewId: Int) {
        mIgnoreGravity = viewId
    }

    open fun setHorizontalGravity(horizontalGravity: Int) {
        val g = horizontalGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK
        if ((mGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) != g) {
            mGravity = (mGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK.inv()) or g
            requestLayout()
        }
    }

    open fun setVerticalGravity(verticalGravity: Int) {
        val g = verticalGravity and Gravity.VERTICAL_GRAVITY_MASK
        if ((mGravity and Gravity.VERTICAL_GRAVITY_MASK) != g) {
            mGravity = (mGravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or g
            requestLayout()
        }
    }

    override fun getBaseline(): Int = mBaselineView?.getBaseline() ?: super.getBaseline()

    private val View.rlp: LayoutParams
        get() = getLayoutParams() as? LayoutParams ?: LayoutParams(layoutParamsOrNull() ?: generateDefaultLayoutParams()).also { setLayoutParamsSilently(it) }

    /** DependencyGraph.getSortedViews: children ordered so anchors come before dependents */
    private fun sortChildren() {
        val views = (0 until getChildCount()).mapNotNull { getChildAt(it) }
        mKeyNodes.clear()
        for (v in views) if (v.getId() != View.NO_ID) mKeyNodes[v.getId()] = v
        fun sorted(filter: IntArray): List<View> {
            val dependencies = java.util.IdentityHashMap<View, HashSet<Int>>()
            val dependents = java.util.IdentityHashMap<View, LinkedHashSet<View>>()
            for (v in views) {
                val rules = v.rlp.getRules()
                val deps = HashSet<Int>()
                for (f in filter) {
                    val rule = rules[f]
                    if (rule > 0 || rule < -1) {
                        val dependency = mKeyNodes[rule] ?: continue
                        if (dependency === v) continue
                        dependents.getOrPut(dependency) { LinkedHashSet() }.add(v)
                        deps.add(rule)
                    }
                }
                dependencies[v] = deps
            }
            val roots = ArrayDeque<View>()
            for (v in views) if (dependencies[v]!!.isEmpty()) roots.addLast(v)
            val out = ArrayList<View>(views.size)
            while (roots.isNotEmpty()) {
                val v = roots.removeLast()
                out.add(v)
                for (d in dependents[v] ?: emptySet()) {
                    val deps = dependencies[d]!!
                    if (deps.remove(v.getId()) && deps.isEmpty()) roots.addLast(d)
                }
            }
            if (out.size < views.size) {
                android.util.Log.w("RelativeLayout", "Circular dependencies cannot exist in RelativeLayout")
                for (v in views) if (v !in out) out.add(v)
            }
            return out
        }
        mSortedVertical = sorted(RULES_VERTICAL)
        mSortedHorizontal = sorted(RULES_HORIZONTAL)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        sortChildren()
        var myWidth = -1
        var myHeight = -1
        var width = 0
        var height = 0
        val widthMode = modeOf(widthMeasureSpec)
        val heightMode = modeOf(heightMeasureSpec)
        val widthSize = sizeOf(widthMeasureSpec)
        val heightSize = sizeOf(heightMeasureSpec)
        if (widthMode != UNSPECIFIED) myWidth = widthSize
        if (heightMode != UNSPECIFIED) myHeight = heightSize
        if (widthMode == EXACTLY) width = myWidth
        if (heightMode == EXACTLY) height = myHeight

        var ignore: View? = null
        var gravity = mGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK
        val horizontalGravity = gravity != Gravity.START && gravity != 0
        gravity = mGravity and Gravity.VERTICAL_GRAVITY_MASK
        val verticalGravity = gravity != Gravity.TOP && gravity != 0
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        var offsetHorizontalAxis = false
        var offsetVerticalAxis = false
        if ((horizontalGravity || verticalGravity) && mIgnoreGravity != View.NO_ID) ignore = findViewById(mIgnoreGravity)
        val isWrapContentWidth = widthMode != EXACTLY
        val isWrapContentHeight = heightMode != EXACTLY

        for (child in mSortedHorizontal) {
            if (child.getVisibility() == GONE) continue
            val params = child.rlp
            val rules = params.getRules(LAYOUT_DIRECTION_LTR)
            applyHorizontalSizeRules(params, myWidth, rules)
            measureChildHorizontal(child, params, myWidth, myHeight)
            if (positionChildHorizontal(child, params, myWidth, isWrapContentWidth)) offsetHorizontalAxis = true
        }

        for (child in mSortedVertical) {
            if (child.getVisibility() == GONE) continue
            val params = child.rlp
            applyVerticalSizeRules(params, myHeight, child.getBaseline())
            measureChild(child, params, myWidth, myHeight)
            if (positionChildVertical(child, params, myHeight, isWrapContentHeight)) offsetVerticalAxis = true
            if (isWrapContentWidth) width = max(width, params.mRight + params.rightMargin)
            if (isWrapContentHeight) height = max(height, params.mBottom + params.bottomMargin)
            if (child !== ignore || verticalGravity) {
                left = min(left, params.mLeft - params.leftMargin)
                top = min(top, params.mTop - params.topMargin)
            }
            if (child !== ignore || horizontalGravity) {
                right = max(right, params.mRight + params.rightMargin)
                bottom = max(bottom, params.mBottom + params.bottomMargin)
            }
        }

        var baselineView: View? = null
        var baselineParams: LayoutParams? = null
        for (child in mSortedVertical) {
            if (child.getVisibility() == GONE) continue
            val childParams = child.rlp
            val bp = baselineParams
            if (baselineView == null || bp == null ||
                (childParams.mTop - bp.mTop).let { if (it != 0) it else childParams.mLeft - bp.mLeft } < 0
            ) {
                baselineView = child
                baselineParams = childParams
            }
        }
        mBaselineView = baselineView

        if (isWrapContentWidth) {
            width += getPaddingRight()
            val own = getLayoutParams()
            if (own != null && own.width >= 0) width = max(width, own.width)
            width = max(width, getSuggestedMinimumWidth())
            width = resolveSize(width, widthMeasureSpec)
            if (offsetHorizontalAxis) {
                for (child in mSortedVertical) {
                    if (child.getVisibility() == GONE) continue
                    val params = child.rlp
                    val rules = params.getRules(LAYOUT_DIRECTION_LTR)
                    if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_HORIZONTAL] != 0) {
                        centerHorizontal(child, params, width)
                    } else if (rules[ALIGN_PARENT_RIGHT] != 0) {
                        val childWidth = child.getMeasuredWidth()
                        params.mLeft = width - getPaddingRight() - childWidth
                        params.mRight = params.mLeft + childWidth
                    }
                }
            }
        }
        if (isWrapContentHeight) {
            height += getPaddingBottom()
            val own = getLayoutParams()
            if (own != null && own.height >= 0) height = max(height, own.height)
            height = max(height, getSuggestedMinimumHeight())
            height = resolveSize(height, heightMeasureSpec)
            if (offsetVerticalAxis) {
                for (child in mSortedVertical) {
                    if (child.getVisibility() == GONE) continue
                    val params = child.rlp
                    val rules = params.getRules(LAYOUT_DIRECTION_LTR)
                    if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_VERTICAL] != 0) {
                        centerVertical(child, params, height)
                    } else if (rules[ALIGN_PARENT_BOTTOM] != 0) {
                        val childHeight = child.getMeasuredHeight()
                        params.mTop = height - getPaddingBottom() - childHeight
                        params.mBottom = params.mTop + childHeight
                    }
                }
            }
        }

        if (horizontalGravity || verticalGravity) {
            // Gravity.apply of the content bounds inside the padded self bounds
            val selfLeft = getPaddingLeft()
            val selfTop = getPaddingTop()
            val selfRight = width - getPaddingRight()
            val selfBottom = height - getPaddingBottom()
            val contentW = right - left
            val contentH = bottom - top
            val contentLeft = when (mGravity and 0x07) {
                Gravity.CENTER_HORIZONTAL -> selfLeft + (selfRight - selfLeft - contentW) / 2
                Gravity.RIGHT -> selfRight - contentW
                else -> selfLeft
            }
            val contentTop = when (mGravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.CENTER_VERTICAL -> selfTop + (selfBottom - selfTop - contentH) / 2
                Gravity.BOTTOM -> selfBottom - contentH
                else -> selfTop
            }
            val horizontalOffset = contentLeft - left
            val verticalOffset = contentTop - top
            if (horizontalOffset != 0 || verticalOffset != 0) {
                for (child in mSortedVertical) {
                    if (child.getVisibility() == GONE || child === ignore) continue
                    val params = child.rlp
                    if (horizontalGravity) {
                        params.mLeft += horizontalOffset
                        params.mRight += horizontalOffset
                    }
                    if (verticalGravity) {
                        params.mTop += verticalOffset
                        params.mBottom += verticalOffset
                    }
                }
            }
        }
        setMeasuredDimension(width, height)
    }

    private fun getRelatedView(rules: IntArray, relation: Int): View? {
        val id = rules[relation]
        if (id == 0) return null
        var v = mKeyNodes[id] ?: return null
        while (v.getVisibility() == GONE) {
            val r = v.rlp.getRules(LAYOUT_DIRECTION_LTR)
            val next = mKeyNodes[r[relation]] ?: return null
            if (next === v) return null
            v = next
        }
        return v
    }

    private fun getRelatedViewParams(rules: IntArray, relation: Int): LayoutParams? =
        getRelatedView(rules, relation)?.getLayoutParams() as? LayoutParams

    private fun getRelatedViewBaselineOffset(rules: IntArray): Int {
        val v = getRelatedView(rules, ALIGN_BASELINE) ?: return -1
        val baseline = v.getBaseline()
        if (baseline != -1) {
            val params = v.getLayoutParams()
            if (params is LayoutParams) return params.mTop + baseline
        }
        return -1
    }

    private fun applyHorizontalSizeRules(childParams: LayoutParams, myWidth: Int, rules: IntArray) {
        childParams.mLeft = VALUE_NOT_SET
        childParams.mRight = VALUE_NOT_SET
        var anchor = getRelatedViewParams(rules, LEFT_OF)
        if (anchor != null) childParams.mRight = anchor.mLeft - (anchor.leftMargin + childParams.rightMargin)
        else if (childParams.alignWithParent && rules[LEFT_OF] != 0 && myWidth >= 0) childParams.mRight = myWidth - getPaddingRight() - childParams.rightMargin
        anchor = getRelatedViewParams(rules, RIGHT_OF)
        if (anchor != null) childParams.mLeft = anchor.mRight + (anchor.rightMargin + childParams.leftMargin)
        else if (childParams.alignWithParent && rules[RIGHT_OF] != 0) childParams.mLeft = getPaddingLeft() + childParams.leftMargin
        anchor = getRelatedViewParams(rules, ALIGN_LEFT)
        if (anchor != null) childParams.mLeft = anchor.mLeft + childParams.leftMargin
        else if (childParams.alignWithParent && rules[ALIGN_LEFT] != 0) childParams.mLeft = getPaddingLeft() + childParams.leftMargin
        anchor = getRelatedViewParams(rules, ALIGN_RIGHT)
        if (anchor != null) childParams.mRight = anchor.mRight - childParams.rightMargin
        else if (childParams.alignWithParent && rules[ALIGN_RIGHT] != 0 && myWidth >= 0) childParams.mRight = myWidth - getPaddingRight() - childParams.rightMargin
        if (rules[ALIGN_PARENT_LEFT] != 0) childParams.mLeft = getPaddingLeft() + childParams.leftMargin
        if (rules[ALIGN_PARENT_RIGHT] != 0 && myWidth >= 0) childParams.mRight = myWidth - getPaddingRight() - childParams.rightMargin
    }

    private fun applyVerticalSizeRules(childParams: LayoutParams, myHeight: Int, myBaseline: Int) {
        val rules = childParams.getRules(LAYOUT_DIRECTION_LTR)
        var baselineOffset = getRelatedViewBaselineOffset(rules)
        if (baselineOffset != -1) {
            if (myBaseline != -1) baselineOffset -= myBaseline
            childParams.mTop = baselineOffset
            childParams.mBottom = VALUE_NOT_SET
            return
        }
        childParams.mTop = VALUE_NOT_SET
        childParams.mBottom = VALUE_NOT_SET
        var anchor = getRelatedViewParams(rules, ABOVE)
        if (anchor != null) childParams.mBottom = anchor.mTop - (anchor.topMargin + childParams.bottomMargin)
        else if (childParams.alignWithParent && rules[ABOVE] != 0 && myHeight >= 0) childParams.mBottom = myHeight - getPaddingBottom() - childParams.bottomMargin
        anchor = getRelatedViewParams(rules, BELOW)
        if (anchor != null) childParams.mTop = anchor.mBottom + (anchor.bottomMargin + childParams.topMargin)
        else if (childParams.alignWithParent && rules[BELOW] != 0) childParams.mTop = getPaddingTop() + childParams.topMargin
        anchor = getRelatedViewParams(rules, ALIGN_TOP)
        if (anchor != null) childParams.mTop = anchor.mTop + childParams.topMargin
        else if (childParams.alignWithParent && rules[ALIGN_TOP] != 0) childParams.mTop = getPaddingTop() + childParams.topMargin
        anchor = getRelatedViewParams(rules, ALIGN_BOTTOM)
        if (anchor != null) childParams.mBottom = anchor.mBottom - childParams.bottomMargin
        else if (childParams.alignWithParent && rules[ALIGN_BOTTOM] != 0 && myHeight >= 0) childParams.mBottom = myHeight - getPaddingBottom() - childParams.bottomMargin
        if (rules[ALIGN_PARENT_TOP] != 0) childParams.mTop = getPaddingTop() + childParams.topMargin
        if (rules[ALIGN_PARENT_BOTTOM] != 0 && myHeight >= 0) childParams.mBottom = myHeight - getPaddingBottom() - childParams.bottomMargin
    }

    private fun measureChildHorizontal(child: View, params: LayoutParams, myWidth: Int, myHeight: Int) {
        val childWidthMeasureSpec = relChildSpec(params.mLeft, params.mRight, params.width, params.leftMargin, params.rightMargin, getPaddingLeft(), getPaddingRight(), myWidth)
        val childHeightMeasureSpec = if (myHeight < 0) {
            if (params.height >= 0) spec(params.height, EXACTLY) else spec(0, UNSPECIFIED)
        } else {
            val maxHeight = max(0, myHeight - getPaddingTop() - getPaddingBottom() - params.topMargin - params.bottomMargin)
            spec(maxHeight, if (params.height == MATCH) EXACTLY else AT_MOST)
        }
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    private fun measureChild(child: View, params: LayoutParams, myWidth: Int, myHeight: Int) {
        child.measure(
            relChildSpec(params.mLeft, params.mRight, params.width, params.leftMargin, params.rightMargin, getPaddingLeft(), getPaddingRight(), myWidth),
            relChildSpec(params.mTop, params.mBottom, params.height, params.topMargin, params.bottomMargin, getPaddingTop(), getPaddingBottom(), myHeight),
        )
    }

    private fun relChildSpec(childStart: Int, childEnd: Int, childSize: Int, startMargin: Int, endMargin: Int, startPadding: Int, endPadding: Int, mySize: Int): Int {
        val isUnspecified = mySize < 0
        if (isUnspecified) {
            return when {
                childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET -> spec(max(0, childEnd - childStart), EXACTLY)
                childSize >= 0 -> spec(childSize, EXACTLY)
                else -> spec(0, UNSPECIFIED)
            }
        }
        val tempStart = if (childStart == VALUE_NOT_SET) startPadding + startMargin else childStart
        val tempEnd = if (childEnd == VALUE_NOT_SET) mySize - endPadding - endMargin else childEnd
        val maxAvailable = tempEnd - tempStart
        if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET) return spec(max(0, maxAvailable), EXACTLY)
        return when {
            childSize >= 0 -> spec(if (maxAvailable >= 0) min(maxAvailable, childSize) else childSize, EXACTLY)
            childSize == MATCH -> spec(max(0, maxAvailable), EXACTLY)
            childSize == WRAP -> if (maxAvailable >= 0) spec(maxAvailable, AT_MOST) else spec(0, UNSPECIFIED)
            else -> spec(0, UNSPECIFIED)
        }
    }

    private fun positionChildHorizontal(child: View, params: LayoutParams, myWidth: Int, wrapContent: Boolean): Boolean {
        val rules = params.getRules(LAYOUT_DIRECTION_LTR)
        if (params.mLeft == VALUE_NOT_SET && params.mRight != VALUE_NOT_SET) {
            params.mLeft = params.mRight - child.getMeasuredWidth()
        } else if (params.mLeft != VALUE_NOT_SET && params.mRight == VALUE_NOT_SET) {
            params.mRight = params.mLeft + child.getMeasuredWidth()
        } else if (params.mLeft == VALUE_NOT_SET && params.mRight == VALUE_NOT_SET) {
            if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_HORIZONTAL] != 0) {
                if (!wrapContent) centerHorizontal(child, params, myWidth)
                else {
                    params.mLeft = getPaddingLeft() + params.leftMargin
                    params.mRight = params.mLeft + child.getMeasuredWidth()
                }
                return true
            } else {
                params.mLeft = getPaddingLeft() + params.leftMargin
                params.mRight = params.mLeft + child.getMeasuredWidth()
            }
        }
        return rules[ALIGN_PARENT_END] != 0
    }

    private fun positionChildVertical(child: View, params: LayoutParams, myHeight: Int, wrapContent: Boolean): Boolean {
        val rules = params.getRules(LAYOUT_DIRECTION_LTR)
        if (params.mTop == VALUE_NOT_SET && params.mBottom != VALUE_NOT_SET) {
            params.mTop = params.mBottom - child.getMeasuredHeight()
        } else if (params.mTop != VALUE_NOT_SET && params.mBottom == VALUE_NOT_SET) {
            params.mBottom = params.mTop + child.getMeasuredHeight()
        } else if (params.mTop == VALUE_NOT_SET && params.mBottom == VALUE_NOT_SET) {
            if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_VERTICAL] != 0) {
                if (!wrapContent) centerVertical(child, params, myHeight)
                else {
                    params.mTop = getPaddingTop() + params.topMargin
                    params.mBottom = params.mTop + child.getMeasuredHeight()
                }
                return true
            } else {
                params.mTop = getPaddingTop() + params.topMargin
                params.mBottom = params.mTop + child.getMeasuredHeight()
            }
        }
        return rules[ALIGN_PARENT_BOTTOM] != 0
    }

    private fun centerHorizontal(child: View, params: LayoutParams, myWidth: Int) {
        val childWidth = child.getMeasuredWidth()
        val left = (myWidth - childWidth) / 2
        params.mLeft = left
        params.mRight = left + childWidth
    }

    private fun centerVertical(child: View, params: LayoutParams, myHeight: Int) {
        val childHeight = child.getMeasuredHeight()
        val top = (myHeight - childHeight) / 2
        params.mTop = top
        params.mBottom = top + childHeight
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val st = child.rlp
            child.layout(st.mLeft, st.mTop, st.mRight, st.mBottom)
        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(WRAP, WRAP)
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = when (p) {
        is LayoutParams -> LayoutParams(p)
        is ViewGroup.MarginLayoutParams -> LayoutParams(p)
        else -> LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams
}

@Deprecated("")
open class AbsoluteLayout : ViewGroup {
    open class LayoutParams : ViewGroup.LayoutParams {
        @JvmField
        var x = 0

        @JvmField
        var y = 0

        constructor(width: Int, height: Int, x: Int, y: Int) : super(width, height) {
            this.x = x
            this.y = y
        }

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(source: ViewGroup.LayoutParams) : super(source)
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxHeight = 0
        var maxWidth = 0
        measureChildren(widthMeasureSpec, heightMeasureSpec)
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val lp = child.getLayoutParams() as? LayoutParams
            maxWidth = max(maxWidth, (lp?.x ?: 0) + child.getMeasuredWidth())
            maxHeight = max(maxHeight, (lp?.y ?: 0) + child.getMeasuredHeight())
        }
        maxWidth = max(maxWidth + getPaddingLeft() + getPaddingRight(), getSuggestedMinimumWidth())
        maxHeight = max(maxHeight + getPaddingTop() + getPaddingBottom(), getSuggestedMinimumHeight())
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, 0), resolveSizeAndState(maxHeight, heightMeasureSpec, 0))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val lp = child.getLayoutParams() as? LayoutParams
            val left = getPaddingLeft() + (lp?.x ?: 0)
            val top = getPaddingTop() + (lp?.y ?: 0)
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight())
        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(WRAP, WRAP, 0, 0)
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = LayoutParams(p)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams
}

/** Smooth scrolling of the scroll containers (OverScroller.startScroll with the default duration) */
internal class ScrollAnimator(private val view: View) {
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private val interpolator = DecelerateInterpolator()

    fun start(fromX: Int, fromY: Int, toX: Int, toY: Int, apply: (Int, Int) -> Unit) {
        val gen = ++generation
        val start = System.nanoTime()
        val duration = 250f
        val step = object : Runnable {
            override fun run() {
                if (gen != generation) return
                val t = ((System.nanoTime() - start) / 1_000_000f / duration).coerceIn(0f, 1f)
                val f = interpolator.getInterpolation(t)
                apply(fromX + ((toX - fromX) * f).toInt(), fromY + ((toY - fromY) * f).toInt())
                if (t < 1f) handler.postDelayed(this, 16)
            }
        }
        handler.post(step)
    }

    fun abort() {
        generation++
    }
}

open class ScrollView : FrameLayout {
    private var mFillViewport = false
    private var mSmoothScrollingEnabled = true
    private var mChildToScrollTo: View? = null
    private val mScroller by lazy { ScrollAnimator(this) }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) ViewAttributes.applyScrollAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : this(context, attrs)

    open fun setFillViewport(fillViewport: Boolean) {
        if (fillViewport != mFillViewport) {
            mFillViewport = fillViewport
            requestLayout()
        }
    }

    open fun isFillViewport(): Boolean = mFillViewport
    open fun setSmoothScrollingEnabled(smoothScrollingEnabled: Boolean) {
        mSmoothScrollingEnabled = smoothScrollingEnabled
    }

    open fun isSmoothScrollingEnabled(): Boolean = mSmoothScrollingEnabled

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!mFillViewport) return
        if (modeOf(heightMeasureSpec) == UNSPECIFIED) return
        val child = getChildAt(0) ?: return
        val lp = child.mlp
        val widthPadding = getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
        val heightPadding = getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin
        val desiredHeight = getMeasuredHeight() - heightPadding
        if (child.getMeasuredHeight() < desiredHeight) {
            child.measure(getChildMeasureSpec(widthMeasureSpec, widthPadding, lp.width), spec(desiredHeight, EXACTLY))
        }
    }

    override fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val lp = child.layoutParamsOrNull() ?: generateDefaultLayoutParams()
        child.measure(
            getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight(), lp.width),
            spec(max(0, sizeOf(parentHeightMeasureSpec) - getPaddingTop() - getPaddingBottom()), UNSPECIFIED)
        )
    }

    override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int, parentHeightMeasureSpec: Int, heightUsed: Int) {
        val lp = child.mlp
        val childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed, lp.width)
        val usedTotal = getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + heightUsed
        child.measure(childWidthMeasureSpec, spec(max(0, sizeOf(parentHeightMeasureSpec) - usedTotal), UNSPECIFIED))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        mChildToScrollTo?.let { if (isDescendant(it)) scrollToChild(it) }
        mChildToScrollTo = null
        // Calling this with the present values causes it to re-claim them (clamped to the new range)
        scrollTo(getScrollX(), getScrollY())
    }

    private fun isDescendant(v: View): Boolean {
        var p = v.getParent()
        while (p != null) {
            if (p === this) return true
            p = p.getParent()
        }
        return false
    }

    /** Range the content can scroll (ScrollView.getScrollRange) */
    fun getScrollRange(): Int {
        val child = getChildAt(0) ?: return 0
        return max(0, child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()))
    }

    private fun clamp(n: Int, my: Int, child: Int): Int {
        if (my >= child || n < 0) return 0
        return if (my + n > child) child - my else n
    }

    override fun scrollTo(x: Int, y: Int) {
        val child = getChildAt(0) ?: return
        val cx = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth())
        val cy = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight())
        if (cx != getScrollX() || cy != getScrollY()) super.scrollTo(cx, cy)
    }

    /** Scrolling done by the user (mouse wheel, drag) */
    fun onUserScroll(y: Int) {
        mScroller.abort()
        scrollTo(getScrollX(), y)
    }

    open fun smoothScrollBy(dx: Int, dy: Int) {
        if (getChildCount() == 0) return
        if (!mSmoothScrollingEnabled) {
            scrollBy(dx, dy)
            return
        }
        val maxY = getScrollRange()
        val target = max(0, min(getScrollY() + dy, maxY))
        mScroller.start(getScrollX(), getScrollY(), getScrollX(), target) { x, y -> scrollTo(x, y) }
    }

    fun smoothScrollTo(x: Int, y: Int) = smoothScrollBy(x - getScrollX(), y - getScrollY())

    open fun fullScroll(direction: Int): Boolean {
        val down = direction == View.FOCUS_DOWN
        val target = if (down) getScrollRange() else 0
        if (target == getScrollY()) return false
        smoothScrollTo(getScrollX(), target)
        return true
    }

    open fun pageScroll(direction: Int): Boolean {
        val height = getHeight()
        val target = if (direction == View.FOCUS_DOWN) min(getScrollY() + height, getScrollRange()) else max(getScrollY() - height, 0)
        if (target == getScrollY()) return false
        smoothScrollTo(getScrollX(), target)
        return true
    }

    open fun arrowScroll(direction: Int): Boolean = pageScroll(direction)

    override fun canScrollVertically(direction: Int): Boolean {
        val range = getScrollRange()
        if (range == 0) return false
        return if (direction < 0) getScrollY() > 0 else getScrollY() < range
    }

    /** Scrolls so the child is completely visible (ScrollView.scrollToChild) */
    private fun scrollToChild(child: View) {
        val loc = IntArray(2)
        var top = 0
        var v: View? = child
        while (v != null && v !== this) {
            top += v.getTop()
            v = v.getParent() as? View
        }
        val bottom = top + child.getHeight()
        val screenTop = getScrollY()
        val screenBottom = screenTop + getHeight() - getPaddingTop() - getPaddingBottom()
        val delta = when {
            bottom > screenBottom && top > screenTop -> min(bottom - screenBottom, top - screenTop)
            top < screenTop && bottom < screenBottom -> -min(screenTop - top, screenBottom - bottom)
            else -> 0
        }
        if (delta != 0) scrollBy(0, delta)
        loc[0] = 0
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        if (focused != null) {
            if (!isLayoutRequested()) scrollToChild(focused) else mChildToScrollTo = focused
        }
        super.requestChildFocus(child, focused)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (getChildCount() > 0) throw IllegalStateException("ScrollView can host only one direct child")
        super.addView(child, index, params)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(MATCH, WRAP)
}

open class HorizontalScrollView : FrameLayout {
    private var mFillViewport = false
    private var mSmoothScrollingEnabled = true
    private val mScroller by lazy { ScrollAnimator(this) }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) ViewAttributes.applyScrollAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs)

    open fun setFillViewport(fillViewport: Boolean) {
        if (fillViewport != mFillViewport) {
            mFillViewport = fillViewport
            requestLayout()
        }
    }

    open fun isFillViewport(): Boolean = mFillViewport
    open fun setSmoothScrollingEnabled(smoothScrollingEnabled: Boolean) {
        mSmoothScrollingEnabled = smoothScrollingEnabled
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!mFillViewport) return
        if (modeOf(widthMeasureSpec) == UNSPECIFIED) return
        val child = getChildAt(0) ?: return
        val lp = child.mlp
        val widthPadding = getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
        val heightPadding = getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin
        val desiredWidth = getMeasuredWidth() - widthPadding
        if (child.getMeasuredWidth() < desiredWidth) {
            child.measure(spec(desiredWidth, EXACTLY), getChildMeasureSpec(heightMeasureSpec, heightPadding, lp.height))
        }
    }

    override fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val lp = child.layoutParamsOrNull() ?: generateDefaultLayoutParams()
        child.measure(
            spec(max(0, sizeOf(parentWidthMeasureSpec) - getPaddingLeft() - getPaddingRight()), UNSPECIFIED),
            getChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop() + getPaddingBottom(), lp.height)
        )
    }

    override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int, parentHeightMeasureSpec: Int, heightUsed: Int) {
        val lp = child.mlp
        val childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + heightUsed, lp.height)
        val usedTotal = getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed
        child.measure(spec(max(0, sizeOf(parentWidthMeasureSpec) - usedTotal), UNSPECIFIED), childHeightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        scrollTo(getScrollX(), getScrollY())
    }

    fun getScrollRange(): Int {
        val child = getChildAt(0) ?: return 0
        return max(0, child.getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()))
    }

    private fun clamp(n: Int, my: Int, child: Int): Int {
        if (my >= child || n < 0) return 0
        return if (my + n > child) child - my else n
    }

    override fun scrollTo(x: Int, y: Int) {
        val child = getChildAt(0) ?: return
        val cx = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth())
        val cy = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight())
        if (cx != getScrollX() || cy != getScrollY()) super.scrollTo(cx, cy)
    }

    /** Scrolling done by the user (mouse wheel, drag) */
    fun onUserScroll(x: Int) {
        mScroller.abort()
        scrollTo(x, getScrollY())
    }

    open fun smoothScrollBy(dx: Int, dy: Int) {
        if (getChildCount() == 0) return
        if (!mSmoothScrollingEnabled) {
            scrollBy(dx, dy)
            return
        }
        val target = max(0, min(getScrollX() + dx, getScrollRange()))
        mScroller.start(getScrollX(), getScrollY(), target, getScrollY()) { x, y -> scrollTo(x, y) }
    }

    fun smoothScrollTo(x: Int, y: Int) = smoothScrollBy(x - getScrollX(), y - getScrollY())

    open fun fullScroll(direction: Int): Boolean {
        val target = if (direction == View.FOCUS_RIGHT) getScrollRange() else 0
        if (target == getScrollX()) return false
        smoothScrollTo(target, getScrollY())
        return true
    }

    open fun pageScroll(direction: Int): Boolean {
        val width = getWidth()
        val target = if (direction == View.FOCUS_RIGHT) min(getScrollX() + width, getScrollRange()) else max(getScrollX() - width, 0)
        if (target == getScrollX()) return false
        smoothScrollTo(target, getScrollY())
        return true
    }

    open fun arrowScroll(direction: Int): Boolean = pageScroll(direction)

    override fun canScrollHorizontally(direction: Int): Boolean {
        val range = getScrollRange()
        if (range == 0) return false
        return if (direction < 0) getScrollX() > 0 else getScrollX() < range
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (getChildCount() > 0) throw IllegalStateException("HorizontalScrollView can host only one direct child")
        super.addView(child, index, params)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(WRAP, MATCH)
}

open class Space : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private fun getDefaultSize2(size: Int, measureSpec: Int): Int = when (modeOf(measureSpec)) {
        AT_MOST -> min(size, sizeOf(measureSpec))
        EXACTLY -> sizeOf(measureSpec)
        else -> size
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(getDefaultSize2(getSuggestedMinimumWidth(), widthMeasureSpec), getDefaultSize2(getSuggestedMinimumHeight(), heightMeasureSpec))
    }
}

open class ViewAnimator : FrameLayout {
    private var mWhich = 0

    constructor(context: Context?) : super(context) {
        setMeasureAllChildren(true)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setMeasureAllChildren(true)
    }

    open fun setDisplayedChild(whichChild: Int) {
        mWhich = whichChild
        if (whichChild >= getChildCount()) mWhich = 0 else if (whichChild < 0) mWhich = getChildCount() - 1
        for (i in 0 until getChildCount()) getChildAt(i)?.setVisibility(if (i == mWhich) VISIBLE else GONE)
    }

    open fun getDisplayedChild(): Int = mWhich
    open fun getCurrentView(): View? = getChildAt(mWhich)
    open fun showNext() = setDisplayedChild(mWhich + 1)
    open fun showPrevious() = setDisplayedChild(mWhich - 1)
    open fun setInAnimation(context: Context?, resourceID: Int) {}
    open fun setOutAnimation(context: Context?, resourceID: Int) {}
    open fun setAnimateFirstView(animate: Boolean) {}

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        if (getChildCount() == 1) child.setVisibility(VISIBLE) else child.setVisibility(GONE)
        if (index in 0..mWhich) setDisplayedChild(mWhich + 1)
    }

    override fun getBaseline(): Int = getCurrentView()?.getBaseline() ?: super.getBaseline()
}

open class ViewFlipper : ViewAnimator {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    open fun startFlipping() {}
    open fun stopFlipping() {}
    open fun isFlipping(): Boolean = false
    open fun setFlipInterval(milliseconds: Int) {}
}

open class TableLayout : LinearLayout {
    constructor(context: Context?) : super(context) {
        setOrientation(VERTICAL)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setOrientation(VERTICAL)
    }

    open fun setStretchAllColumns(stretchAllColumns: Boolean) {}
    open fun setShrinkAllColumns(shrinkAllColumns: Boolean) {}
    open fun setColumnStretchable(columnIndex: Int, isStretchable: Boolean) {}
}

open class TableRow : LinearLayout {
    constructor(context: Context?) : super(context) {
        setOrientation(HORIZONTAL)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setOrientation(HORIZONTAL)
    }
}
