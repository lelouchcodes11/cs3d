@file:JvmName("MaterialMisc5Kt")

package com.google.android.material.chip

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import com.lagradost.desktop.runtime.ui.ViewAttributes
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Material ChipDrawable: the chip's container (background, surface, stroke, rounded corners) and its
 * chip / checked / close icons. The text is drawn by the Chip (a TextView) inside the paddings this
 * drawable defines, like Material's Chip with shouldDrawText = false.
 */
open class ChipDrawable internal constructor(private val density: Float) : Drawable() {
    private fun dp(v: Float) = v * density

    internal var chipBackgroundColor: ColorStateList? = null
    internal var chipSurfaceColor: ColorStateList? = null
    internal var chipStrokeColor: ColorStateList? = null
    internal var chipStrokeWidth = dp(1f)
    internal var chipCornerRadius = dp(8f)
    internal var chipMinHeight = dp(32f)
    internal var chipStartPadding = dp(4f)
    internal var iconStartPadding = 0f
    internal var iconEndPadding = 0f
    internal var textStartPadding = dp(8f)
    internal var textEndPadding = dp(6f)
    internal var closeIconStartPadding = dp(2f)
    internal var closeIconEndPadding = dp(2f)
    internal var chipEndPadding = dp(6f)
    internal var chipIcon: Drawable? = null
    internal var chipIconTint: ColorStateList? = null
    internal var chipIconSize = dp(18f)
    internal var chipIconVisible = true
    internal var checkedIcon: Drawable? = null
    internal var checkedIconTint: ColorStateList? = null
    internal var checkedIconVisible = true
    internal var closeIcon: Drawable? = null
    internal var closeIconTint: ColorStateList? = null
    internal var closeIconSize = dp(18f)
    internal var closeIconVisible = false
    internal var checkable = false
    internal var textColor: ColorStateList? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun checked() = getState().contains(View.STATE_CHECKED)

    internal fun showsChipIcon() = chipIconVisible && chipIcon != null
    internal fun showsCheckedIcon() = checkable && checkedIconVisible && checkedIcon != null && checked()
    internal fun iconWidth(): Float =
        if (showsChipIcon() || showsCheckedIcon()) iconStartPadding + chipIconSize + iconEndPadding else 0f

    internal fun showsCloseIcon() = closeIconVisible && closeIcon != null
    internal fun closeIconWidth(): Float =
        if (showsCloseIcon()) closeIconStartPadding + closeIconSize + closeIconEndPadding else 0f

    /** Text paddings of the chip: container padding, icons and the text's own padding */
    internal fun startPadding(): Int = (chipStartPadding + iconWidth() + textStartPadding).roundToInt()
    internal fun endPadding(): Int = (textEndPadding + closeIconWidth() + chipEndPadding).roundToInt()

    /** The close icon's area inside [bounds] */
    internal fun closeIconBounds(bounds: Rect, rtl: Boolean, out: RectF) {
        val size = closeIconSize
        val top = bounds.exactCenterY() - size / 2
        val x = if (rtl) bounds.left + chipEndPadding + closeIconEndPadding else bounds.right - chipEndPadding - closeIconEndPadding - size
        out.set(x, top, x + size, top + size)
    }

    private fun colorOf(list: ColorStateList?, fallback: Int = Color.TRANSPARENT): Int =
        list?.getColorForState(getState(), list.defaultColor) ?: fallback

    private fun drawIcon(canvas: Canvas, d: Drawable, tint: ColorStateList?, left: Float, size: Float) {
        val b = getBounds()
        val top = b.exactCenterY() - size / 2
        d.setBounds(left.roundToInt(), top.roundToInt(), (left + size).roundToInt(), (top + size).roundToInt())
        if (tint != null) d.setTintList(tint)
        d.setState(getState())
        d.draw(canvas)
    }

    override fun draw(canvas: Canvas) {
        val b = getBounds()
        if (b.isEmpty) return
        val half = chipStrokeWidth / 2
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half)
        val radius = minOf(chipCornerRadius, rect.height() / 2)
        paint.setStyle(Paint.Style.FILL)
        val surface = colorOf(chipSurfaceColor)
        if (Color.alpha(surface) != 0) {
            paint.setColor(surface)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        val background = colorOf(chipBackgroundColor)
        if (Color.alpha(background) != 0) {
            paint.setColor(background)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        val stroke = colorOf(chipStrokeColor)
        if (chipStrokeWidth > 0f && Color.alpha(stroke) != 0) {
            paint.setStyle(Paint.Style.STROKE)
            paint.setStrokeWidth(chipStrokeWidth)
            paint.setColor(stroke)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        val rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
        val iconLeft = if (rtl) b.right - chipStartPadding - iconStartPadding - chipIconSize else b.left + chipStartPadding + iconStartPadding
        when {
            showsCheckedIcon() -> drawIcon(canvas, checkedIcon!!, checkedIconTint, iconLeft, chipIconSize)
            showsChipIcon() -> drawIcon(canvas, chipIcon!!, chipIconTint, iconLeft, chipIconSize)
        }
        if (showsCloseIcon()) {
            closeIconBounds(b, rtl, rect)
            drawIcon(canvas, closeIcon!!, closeIconTint, rect.left, closeIconSize)
        }
    }

    override fun isStateful(): Boolean = true
    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun getIntrinsicHeight(): Int = chipMinHeight.roundToInt()
    override fun setAlpha(alpha: Int) {
        paint.setAlpha(alpha)
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.setColorFilter(colorFilter)
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    open fun getChipCornerRadius(): Float = chipCornerRadius
    open fun getChipMinHeight(): Float = chipMinHeight

    /** Material's Chip attributes (element, style and theme chipStyle) */
    internal fun load(r: ViewAttributes.Reader) {
        r.colorStateList("chipBackgroundColor")?.let { chipBackgroundColor = it }
        r.colorStateList("chipSurfaceColor")?.let { chipSurfaceColor = it }
        r.colorStateList("chipStrokeColor")?.let { chipStrokeColor = it }
        r.dim("chipStrokeWidth")?.let { chipStrokeWidth = it.toFloat() }
        r.dim("chipCornerRadius")?.let { chipCornerRadius = it.toFloat() }
        r.dim("chipMinHeight")?.let { chipMinHeight = it.toFloat() }
        r.dim("chipStartPadding")?.let { chipStartPadding = it.toFloat() }
        r.dim("iconStartPadding")?.let { iconStartPadding = it.toFloat() }
        r.dim("iconEndPadding")?.let { iconEndPadding = it.toFloat() }
        r.dim("textStartPadding")?.let { textStartPadding = it.toFloat() }
        r.dim("textEndPadding")?.let { textEndPadding = it.toFloat() }
        r.dim("closeIconStartPadding")?.let { closeIconStartPadding = it.toFloat() }
        r.dim("closeIconEndPadding")?.let { closeIconEndPadding = it.toFloat() }
        r.dim("chipEndPadding")?.let { chipEndPadding = it.toFloat() }
        r.drawable("chipIcon")?.let { chipIcon = it.mutate() }
        r.colorStateList("chipIconTint")?.let { chipIconTint = it }
        r.dim("chipIconSize")?.let { chipIconSize = it.toFloat() }
        (r.bool("chipIconVisible") ?: r.bool("chipIconEnabled"))?.let { chipIconVisible = it }
        r.drawable("checkedIcon")?.let { checkedIcon = it.mutate() }
        r.colorStateList("checkedIconTint")?.let { checkedIconTint = it }
        (r.bool("checkedIconVisible") ?: r.bool("checkedIconEnabled"))?.let { checkedIconVisible = it }
        r.drawable("closeIcon")?.let { closeIcon = it.mutate() }
        r.colorStateList("closeIconTint")?.let { closeIconTint = it }
        r.dim("closeIconSize")?.let { closeIconSize = it.toFloat() }
        (r.bool("closeIconVisible") ?: r.bool("closeIconEnabled"))?.let { closeIconVisible = it }
        r.bool("checkable")?.let { checkable = it }
        r.colorStateList("textColor")?.let { textColor = it }
    }

    companion object {
        @JvmStatic
        fun createFromAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int): ChipDrawable {
            val host = View(context)
            val drawable = ChipDrawable(context.getResources().getDisplayMetrics().density)
            drawable.load(
                if (defStyleRes != 0) ViewAttributes.styleReader(host, defStyleRes)
                else ViewAttributes.reader(host, attrs ?: ViewAttributes.emptyAttributes)
            )
            return drawable
        }
    }
}

/**
 * Material Chip: a checkable TextView whose background is a [ChipDrawable]. Filter chips check on
 * click, the checked icon and close icon take space before/after the text.
 */
open class Chip : CheckBox {
    private var chip: ChipDrawable? = null
    private var closeListener: View.OnClickListener? = null
    private var closePressed = false

    constructor(context: Context?) : super(context) {
        initChip(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initChip(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initChip(attrs)
    }

    private fun initChip(attrs: AttributeSet?) {
        val d = ChipDrawable(getResources().getDisplayMetrics().density)
        d.load(ViewAttributes.reader(this, attrs ?: ViewAttributes.emptyAttributes))
        if (attrs == null) d.textColor?.let { setTextColor(it) }
        setSingleLine()
        setEllipsize(TextUtils.TruncateAt.END)
        setGravity(Gravity.CENTER_VERTICAL or Gravity.START)
        applyChipDrawable(d)
    }

    override fun applyWidgetDefaults() {
        super.applyWidgetDefaults()
        setMinimumHeight(0)
    }

    override fun showsButton(): Boolean = false

    private fun applyChipDrawable(d: ChipDrawable) {
        chip = d
        setBackground(d)
        d.setState(getDrawableState())
        updateChipPadding()
    }

    private fun updateChipPadding() {
        val d = chip ?: return
        setMinHeight(d.chipMinHeight.roundToInt())
        setMinimumHeight(d.chipMinHeight.roundToInt())
        val rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
        val start = d.startPadding()
        val end = d.endPadding()
        setPadding(if (rtl) end else start, 0, if (rtl) start else end, 0)
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        // the checked icon takes space only while shown
        if (chip?.checkedIcon != null) updateChipPadding()
    }

    override fun toggle() {
        if (isCheckable()) super.toggle()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = chip
        if (d != null && d.showsCloseIcon() && isEnabled()) {
            val r = RectF()
            val b = Rect(0, 0, getWidth(), getHeight())
            d.closeIconBounds(b, getLayoutDirection() == View.LAYOUT_DIRECTION_RTL, r)
            val inside = event.getX() >= r.left - d.closeIconStartPadding && event.getX() <= r.right + d.closeIconEndPadding
            when (event.getActionMasked()) {
                MotionEvent.ACTION_DOWN -> if (inside) {
                    closePressed = true
                    return true
                }
                MotionEvent.ACTION_UP -> if (closePressed) {
                    closePressed = false
                    if (inside) performCloseIconClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> if (closePressed) {
                    closePressed = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (closePressed) return true
            }
        }
        return super.onTouchEvent(event)
    }

    open fun performCloseIconClick(): Boolean {
        val l = closeListener ?: return false
        l.onClick(this)
        return true
    }

    open fun setCheckable(checkable: Boolean) {
        chip?.checkable = checkable
        updateChipPadding()
    }

    open fun isCheckable(): Boolean = chip?.checkable ?: false

    open fun setOnCloseIconClickListener(listener: View.OnClickListener?) {
        closeListener = listener
    }

    open fun getChipDrawable(): Drawable? = chip
    open fun setChipDrawable(chipDrawable: ChipDrawable) {
        if (chipDrawable !== chip) applyChipDrawable(chipDrawable)
    }

    private fun changed() {
        updateChipPadding()
        invalidate()
    }

    private fun color(id: Int): ColorStateList? = androidx.core.content.ContextCompat.getColorStateList(getContext(), id)
    private fun dimen(id: Int): Float = getResources().getDimension(id)
    private fun drawableRes(id: Int): Drawable? = androidx.core.content.ContextCompat.getDrawable(getContext(), id)?.mutate()

    open fun setChipBackgroundColor(chipBackgroundColor: ColorStateList?) {
        chip?.chipBackgroundColor = chipBackgroundColor
        changed()
    }

    open fun setChipBackgroundColorResource(id: Int) = setChipBackgroundColor(color(id))
    open fun getChipBackgroundColor(): ColorStateList? = chip?.chipBackgroundColor
    open fun setChipStrokeColor(chipStrokeColor: ColorStateList?) {
        chip?.chipStrokeColor = chipStrokeColor
        changed()
    }

    open fun setChipStrokeColorResource(id: Int) = setChipStrokeColor(color(id))
    open fun getChipStrokeColor(): ColorStateList? = chip?.chipStrokeColor
    open fun setChipStrokeWidth(chipStrokeWidth: Float) {
        chip?.chipStrokeWidth = chipStrokeWidth
        changed()
    }

    open fun setChipStrokeWidthResource(id: Int) = setChipStrokeWidth(dimen(id))
    open fun getChipStrokeWidth(): Float = chip?.chipStrokeWidth ?: 0f
    open fun setChipCornerRadius(chipCornerRadius: Float) {
        chip?.chipCornerRadius = chipCornerRadius
        changed()
    }

    open fun getChipCornerRadius(): Float = chip?.chipCornerRadius ?: 0f
    open fun setChipMinHeight(minHeight: Float) {
        chip?.chipMinHeight = minHeight
        changed()
    }

    open fun getChipMinHeight(): Float = chip?.chipMinHeight ?: 0f
    open fun setChipStartPadding(v: Float) {
        chip?.chipStartPadding = v
        changed()
    }

    open fun setChipEndPadding(v: Float) {
        chip?.chipEndPadding = v
        changed()
    }

    open fun setTextStartPadding(v: Float) {
        chip?.textStartPadding = v
        changed()
    }

    open fun setTextEndPadding(v: Float) {
        chip?.textEndPadding = v
        changed()
    }

    open fun setIconStartPadding(v: Float) {
        chip?.iconStartPadding = v
        changed()
    }

    open fun setIconEndPadding(v: Float) {
        chip?.iconEndPadding = v
        changed()
    }

    open fun setChipIcon(chipIcon: Drawable?) {
        chip?.chipIcon = chipIcon?.mutate()
        changed()
    }

    open fun setChipIconResource(id: Int) = setChipIcon(drawableRes(id))
    open fun getChipIcon(): Drawable? = chip?.chipIcon
    open fun setChipIconTint(tint: ColorStateList?) {
        chip?.chipIconTint = tint
        changed()
    }

    open fun setChipIconTintResource(id: Int) = setChipIconTint(color(id))
    open fun setChipIconSize(size: Float) {
        chip?.chipIconSize = size
        changed()
    }

    open fun setChipIconVisible(visible: Boolean) {
        chip?.chipIconVisible = visible
        changed()
    }

    open fun isChipIconVisible(): Boolean = chip?.chipIconVisible ?: false
    open fun setCheckedIcon(icon: Drawable?) {
        chip?.checkedIcon = icon?.mutate()
        changed()
    }

    open fun setCheckedIconResource(id: Int) = setCheckedIcon(drawableRes(id))
    open fun getCheckedIcon(): Drawable? = chip?.checkedIcon
    open fun setCheckedIconTint(tint: ColorStateList?) {
        chip?.checkedIconTint = tint
        changed()
    }

    open fun setCheckedIconVisible(visible: Boolean) {
        chip?.checkedIconVisible = visible
        changed()
    }

    open fun isCheckedIconVisible(): Boolean = chip?.checkedIconVisible ?: false
    open fun setCloseIcon(icon: Drawable?) {
        chip?.closeIcon = icon?.mutate()
        changed()
    }

    open fun setCloseIconResource(id: Int) = setCloseIcon(drawableRes(id))
    open fun getCloseIcon(): Drawable? = chip?.closeIcon
    open fun setCloseIconTint(tint: ColorStateList?) {
        chip?.closeIconTint = tint
        changed()
    }

    open fun setCloseIconVisible(closeIconVisible: Boolean) {
        chip?.closeIconVisible = closeIconVisible
        changed()
    }

    open fun setCloseIconVisible(id: Int) = setCloseIconVisible(getResources().getBoolean(id))
    open fun isCloseIconVisible(): Boolean = chip?.closeIconVisible ?: false
    open fun setEnsureMinTouchTargetSize(flag: Boolean) {}
    open fun setChipSpacing(spacing: Int) {}
}

/**
 * Material ChipGroup: a FlowLayout of chips (rows wrap unless singleLine) with single / required
 * selection and a checked-state listener.
 */
open class ChipGroup : ViewGroup {
    fun interface OnCheckedStateChangeListener {
        fun onCheckedChanged(group: ChipGroup, checkedIds: List<Int>)
    }

    @Deprecated("Use OnCheckedStateChangeListener")
    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(group: ChipGroup, checkedId: Int)
    }

    private var singleSelection = false
    private var selectionRequired = false
    private var singleLine = false
    private var spacingHorizontal = 0
    private var spacingVertical = 0
    private var listener: OnCheckedStateChangeListener? = null
    private var legacyListener: Any? = null
    private var updating = false
    private var rowCount = 0

    constructor(context: Context?) : super(context) {
        initGroup(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initGroup(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initGroup(attrs)
    }

    private fun initGroup(attrs: AttributeSet?) {
        val density = getResources().getDisplayMetrics().density
        spacingHorizontal = (8 * density).roundToInt()
        spacingVertical = (4 * density).roundToInt()
        val r = ViewAttributes.reader(this, attrs ?: ViewAttributes.emptyAttributes)
        r.dim("chipSpacing")?.let {
            spacingHorizontal = it
            spacingVertical = it
        }
        r.dim("chipSpacingHorizontal")?.let { spacingHorizontal = it }
        r.dim("chipSpacingVertical")?.let { spacingVertical = it }
        r.bool("singleLine")?.let { singleLine = it }
        r.bool("singleSelection")?.let { singleSelection = it }
        r.bool("selectionRequired")?.let { selectionRequired = it }
        val checked = r.resourceId("checkedChip")
        if (checked != 0) post { check(checked) }
    }

    // ---- selection

    private val chipListener = android.widget.CompoundButton.OnCheckedChangeListener { button, isChecked ->
        if (updating) return@OnCheckedChangeListener
        if (!isChecked && selectionRequired && getCheckedChipIds().isEmpty()) {
            updating = true
            (button as? Chip)?.setChecked(true)
            updating = false
            return@OnCheckedChangeListener
        }
        if (isChecked && singleSelection) {
            updating = true
            chips().forEach { if (it !== button && it.isChecked()) it.setChecked(false) }
            updating = false
        }
        notifyChecked()
    }

    private fun chips(): List<Chip> = children.filterIsInstance<Chip>()

    private fun notifyChecked() {
        val ids = getCheckedChipIds()
        listener?.onCheckedChanged(this, ids)
        @Suppress("DEPRECATION")
        (legacyListener as? OnCheckedChangeListener)?.onCheckedChanged(this, ids.firstOrNull() ?: View.NO_ID)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child is Chip) {
            if (child.getId() == View.NO_ID) child.setId(View.generateViewId())
            child.mGroupListener = chipListener
            if (child.isChecked() && singleSelection) {
                updating = true
                chips().forEach { if (it !== child && it.isChecked()) it.setChecked(false) }
                updating = false
            }
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child is Chip && child.mGroupListener === chipListener) child.mGroupListener = null
    }

    open fun setSingleSelection(singleSelection: Boolean) {
        this.singleSelection = singleSelection
        if (singleSelection) clearCheck()
    }

    open fun setSingleSelection(id: Int) = setSingleSelection(getResources().getBoolean(id))
    open fun isSingleSelection(): Boolean = singleSelection
    open fun setSelectionRequired(selectionRequired: Boolean) {
        this.selectionRequired = selectionRequired
    }

    open fun isSelectionRequired(): Boolean = selectionRequired
    open fun setOnCheckedStateChangeListener(listener: OnCheckedStateChangeListener?) {
        this.listener = listener
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use setOnCheckedStateChangeListener")
    open fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        legacyListener = listener
    }

    open fun getCheckedChipIds(): List<Int> = chips().filter { it.isChecked() }.map { it.getId() }
    open fun getCheckedChipId(): Int = if (singleSelection) getCheckedChipIds().firstOrNull() ?: View.NO_ID else View.NO_ID
    open fun check(id: Int) {
        val target = chips().firstOrNull { it.getId() == id } ?: return
        target.setChecked(true)
    }

    open fun clearCheck() {
        updating = true
        chips().forEach { it.setChecked(false) }
        updating = false
        notifyChecked()
    }

    // ---- flow layout

    open fun setChipSpacing(chipSpacing: Int) {
        spacingHorizontal = chipSpacing
        spacingVertical = chipSpacing
        requestLayout()
    }

    open fun setChipSpacingHorizontal(chipSpacingHorizontal: Int) {
        spacingHorizontal = chipSpacingHorizontal
        requestLayout()
    }

    open fun setChipSpacingVertical(chipSpacingVertical: Int) {
        spacingVertical = chipSpacingVertical
        requestLayout()
    }

    open fun getChipSpacingHorizontal(): Int = spacingHorizontal
    open fun getChipSpacingVertical(): Int = spacingVertical
    open fun setSingleLine(singleLine: Boolean) {
        this.singleLine = singleLine
        requestLayout()
    }

    open fun setSingleLine(id: Int) = setSingleLine(getResources().getBoolean(id))
    open fun isSingleLine(): Boolean = singleLine
    open fun getRowCount(): Int = rowCount

    /** Rows never overlap on desktop (Material allows negative vertical spacing with touch-target insets) */
    private fun rowGap(): Int = max(spacingVertical, (4 * getResources().getDisplayMetrics().density).roundToInt())

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: LayoutParams): LayoutParams = if (p is MarginLayoutParams) MarginLayoutParams(p) else MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val maxRight = if (widthMode == MeasureSpec.UNSPECIFIED || singleLine) Int.MAX_VALUE else widthSize - getPaddingRight()
        var childLeft = getPaddingLeft()
        var childTop = getPaddingTop()
        var rowHeight = 0
        var maxChildRight = 0
        var rows = if (getChildCount() > 0) 1 else 0
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == View.GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val lp = child.getLayoutParams()
            val ml = (lp as? MarginLayoutParams)?.leftMargin ?: 0
            val mr = (lp as? MarginLayoutParams)?.rightMargin ?: 0
            val mt = (lp as? MarginLayoutParams)?.topMargin ?: 0
            val mb = (lp as? MarginLayoutParams)?.bottomMargin ?: 0
            val childRight = childLeft + ml + child.getMeasuredWidth()
            if (childRight > maxRight && childLeft > getPaddingLeft()) {
                childLeft = getPaddingLeft()
                childTop += rowHeight + rowGap()
                rowHeight = 0
                rows++
            }
            rowHeight = max(rowHeight, mt + child.getMeasuredHeight() + mb)
            val right = childLeft + ml + child.getMeasuredWidth()
            maxChildRight = max(maxChildRight, right)
            childLeft = right + mr + spacingHorizontal
        }
        rowCount = rows
        val contentWidth = maxChildRight + getPaddingRight()
        val contentHeight = childTop + rowHeight + getPaddingBottom()
        val w = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(contentWidth, widthSize)
            else -> contentWidth
        }
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val h = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(contentHeight, heightSize)
            else -> contentHeight
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
        val width = r - l
        val maxRight = if (singleLine) Int.MAX_VALUE else width - getPaddingRight()
        var childLeft = getPaddingLeft()
        var childTop = getPaddingTop()
        var rowHeight = 0
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == View.GONE) continue
            val lp = child.getLayoutParams()
            val ml = (lp as? MarginLayoutParams)?.leftMargin ?: 0
            val mr = (lp as? MarginLayoutParams)?.rightMargin ?: 0
            val mt = (lp as? MarginLayoutParams)?.topMargin ?: 0
            val mb = (lp as? MarginLayoutParams)?.bottomMargin ?: 0
            if (childLeft + ml + child.getMeasuredWidth() > maxRight && childLeft > getPaddingLeft()) {
                childLeft = getPaddingLeft()
                childTop += rowHeight + rowGap()
                rowHeight = 0
            }
            val left = childLeft + ml
            val top = childTop + mt
            val cw = child.getMeasuredWidth()
            if (rtl) child.layout(width - left - cw, top, width - left, top + child.getMeasuredHeight())
            else child.layout(left, top, left + cw, top + child.getMeasuredHeight())
            rowHeight = max(rowHeight, mt + child.getMeasuredHeight() + mb)
            childLeft = left + cw + mr + spacingHorizontal
        }
    }
}
