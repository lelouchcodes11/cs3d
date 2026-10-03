@file:JvmName("WidgetsKt")

package android.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.ui.ViewAttributes

open class ImageView : View {
    enum class ScaleType { MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE }

    private var mDrawable by mutableStateOf<Drawable?>(null)
    private var mScaleType by mutableStateOf(ScaleType.FIT_CENTER)
    private var mImageTint by mutableStateOf<ColorStateList?>(null)
    private var mAdjustViewBounds by mutableStateOf(false)
    private var mImageAlpha by mutableIntStateOf(255)
    private var mImageMatrix = Matrix()
    private var mMaxWidth = Int.MAX_VALUE
    private var mMaxHeight = Int.MAX_VALUE
    private var mCropToPadding = false
    private var mImageUri: Uri? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context) {
        if (attrs != null) {
            ViewAttributes.applyViewAttributes(this, attrs)
            ViewAttributes.applyImageViewAttributes(this, attrs)
        }
    }

    open fun getDrawable(): Drawable? = mDrawable
    open fun setImageDrawable(drawable: Drawable?) {
        mDrawable?.setCallback(null)
        val oldW = mDrawable?.getIntrinsicWidth() ?: -2
        val oldH = mDrawable?.getIntrinsicHeight() ?: -2
        mDrawable = drawable
        drawable?.setCallback(this)
        drawable?.setState(getDrawableState())
        if (oldW != (drawable?.getIntrinsicWidth() ?: -2) || oldH != (drawable?.getIntrinsicHeight() ?: -2)) requestLayout()
        invalidate()
    }

    open fun setImageResource(resId: Int) = setImageDrawable(if (resId == 0) null else getContext().getDrawable(resId))
    open fun setImageBitmap(bm: Bitmap?) = setImageDrawable(bm?.let { BitmapDrawable(getContext().resources, it) })
    open fun setImageURI(uri: Uri?) {
        mImageUri = uri
        val file = android.content.ContentResolver.resolveToFile(uri)
        setImageDrawable(file?.let { Drawable.createFromPath(it.absolutePath) })
    }

    open fun setImageIcon(icon: android.graphics.drawable.Icon?) = setImageDrawable(icon?.loadDrawable(getContext()))
    open fun setImageLevel(level: Int) {
        mDrawable?.setLevel(level)
    }

    open fun setImageState(state: IntArray, merge: Boolean) {
        mDrawable?.setState(state)
    }

    open fun getScaleType(): ScaleType = mScaleType
    open fun setScaleType(scaleType: ScaleType?) {
        mScaleType = scaleType ?: ScaleType.FIT_CENTER
    }

    open fun getImageTintList(): ColorStateList? = mImageTint
    open fun setImageTintList(tint: ColorStateList?) {
        mImageTint = tint
        mDrawable?.setTintList(tint)
        invalidate()
    }

    open fun setImageTintMode(tintMode: PorterDuff.Mode?) {
        mDrawable?.setTintMode(tintMode)
    }

    open fun setColorFilter(color: Int) = setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    open fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        mDrawable?.setColorFilter(color, mode)
        invalidate()
    }

    open fun setColorFilter(cf: android.graphics.ColorFilter?) {
        mDrawable?.setColorFilter(cf)
        invalidate()
    }

    open fun clearColorFilter() = setColorFilter(null)
    open fun getAdjustViewBounds(): Boolean = mAdjustViewBounds
    open fun setAdjustViewBounds(adjustViewBounds: Boolean) {
        mAdjustViewBounds = adjustViewBounds
        requestLayout()
    }

    open fun setImageAlpha(alpha: Int) {
        mImageAlpha = alpha
        mDrawable?.setAlpha(alpha)
    }

    open fun getImageAlpha(): Int = mImageAlpha

    @Deprecated("")
    open fun setAlpha(alpha: Int) = setImageAlpha(alpha)
    open fun getImageMatrix(): Matrix = mImageMatrix
    open fun setImageMatrix(matrix: Matrix?) {
        mImageMatrix = matrix ?: Matrix()
        invalidate()
    }

    open fun setMaxWidth(maxWidth: Int) {
        mMaxWidth = maxWidth
        requestLayout()
    }

    open fun setMaxHeight(maxHeight: Int) {
        mMaxHeight = maxHeight
        requestLayout()
    }

    open fun getMaxWidth(): Int = mMaxWidth
    open fun getMaxHeight(): Int = mMaxHeight
    open fun setCropToPadding(cropToPadding: Boolean) {
        mCropToPadding = cropToPadding
    }

    open fun getCropToPadding(): Boolean = mCropToPadding

    /** ImageView.onMeasure (AOSP) */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w: Int
        var h: Int
        var desiredAspect = 0f
        var resizeWidth = false
        var resizeHeight = false
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val d = mDrawable
        if (d == null) {
            w = 0
            h = 0
        } else {
            w = d.getIntrinsicWidth()
            h = d.getIntrinsicHeight()
            if (w <= 0) w = 1
            if (h <= 0) h = 1
            if (mAdjustViewBounds) {
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
                desiredAspect = w.toFloat() / h.toFloat()
            }
        }
        val pleft = getPaddingLeft()
        val pright = getPaddingRight()
        val ptop = getPaddingTop()
        val pbottom = getPaddingBottom()
        var widthSize: Int
        var heightSize: Int
        if (resizeWidth || resizeHeight) {
            widthSize = resolveAdjustedSize(w + pleft + pright, mMaxWidth, widthMeasureSpec)
            heightSize = resolveAdjustedSize(h + ptop + pbottom, mMaxHeight, heightMeasureSpec)
            if (desiredAspect != 0f) {
                val actualAspect = (widthSize - pleft - pright).toFloat() / (heightSize - ptop - pbottom)
                if (kotlin.math.abs(actualAspect - desiredAspect) > 0.0000001) {
                    var done = false
                    if (resizeWidth) {
                        val newWidth = (desiredAspect * (heightSize - ptop - pbottom)).toInt() + pleft + pright
                        if (!resizeHeight) widthSize = resolveAdjustedSize(newWidth, mMaxWidth, widthMeasureSpec)
                        if (newWidth <= widthSize) {
                            widthSize = newWidth
                            done = true
                        }
                    }
                    if (!done && resizeHeight) {
                        val newHeight = ((widthSize - pleft - pright) / desiredAspect).toInt() + ptop + pbottom
                        if (!resizeWidth) heightSize = resolveAdjustedSize(newHeight, mMaxHeight, heightMeasureSpec)
                        if (newHeight <= heightSize) heightSize = newHeight
                    }
                }
            }
        } else {
            w += pleft + pright
            h += ptop + pbottom
            w = kotlin.math.max(w, getSuggestedMinimumWidth())
            h = kotlin.math.max(h, getSuggestedMinimumHeight())
            widthSize = resolveSizeAndState(w, widthMeasureSpec, 0)
            heightSize = resolveSizeAndState(h, heightMeasureSpec, 0)
        }
        setMeasuredDimension(widthSize, heightSize)
    }

    private fun resolveAdjustedSize(desiredSize: Int, maxSize: Int, measureSpec: Int): Int {
        val specSize = MeasureSpec.getSize(measureSpec)
        return when (MeasureSpec.getMode(measureSpec)) {
            MeasureSpec.UNSPECIFIED -> kotlin.math.min(desiredSize, maxSize)
            MeasureSpec.AT_MOST -> kotlin.math.min(kotlin.math.min(desiredSize, specSize), maxSize)
            else -> specSize
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        // also called by View(context, attrs) before this class is initialized
        if (constructed) mDrawable?.setState(getDrawableState())
    }

    private var constructed = false

    init {
        constructed = true
    }
}

open class ImageButton : ImageView {
    constructor(context: Context?) : super(context) {
        setFocusable(true)
        setClickable(true)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setFocusable(true)
        setClickable(true)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setFocusable(true)
        setClickable(true)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setFocusable(true)
        setClickable(true)
    }
}

open class ProgressBar : View {
    private var mIndeterminate by mutableStateOf(true)
    private var mProgress by mutableIntStateOf(0)
    private var mSecondaryProgress by mutableIntStateOf(0)
    private var mMin by mutableIntStateOf(0)
    private var mMax by mutableIntStateOf(100)
    private var mHorizontal by mutableStateOf(false)
    private var mProgressTint by mutableStateOf<ColorStateList?>(null)
    private var mProgressBackgroundTint by mutableStateOf<ColorStateList?>(null)
    private var mIndeterminateTint by mutableStateOf<ColorStateList?>(null)
    private var mProgressDrawable: Drawable? = null
    private var mIndeterminateDrawable: Drawable? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context) {
        // android.R.attr.progressBarStyleHorizontal
        if (defStyleAttr == 0x01010078) {
            mHorizontal = true
            mIndeterminate = false
        }
        if (attrs != null) {
            ViewAttributes.applyViewAttributes(this, attrs)
            ViewAttributes.applyProgressBarAttributes(this, attrs)
        }
    }

    /** Desktop: horizontal (determinate bar) vs circular */
    open fun setHorizontalStyle(horizontal: Boolean) {
        mHorizontal = horizontal
        requestLayout()
    }

    open fun isHorizontalStyle(): Boolean = mHorizontal
    open fun isIndeterminate(): Boolean = mIndeterminate
    open fun setIndeterminate(indeterminate: Boolean) {
        mIndeterminate = indeterminate
    }

    open fun getProgress(): Int = mProgress
    open fun setProgress(progress: Int) {
        mProgress = progress.coerceIn(mMin, mMax)
        if (!mIndeterminate || mHorizontal) mIndeterminate = false
    }

    open fun setProgress(progress: Int, animate: Boolean) = setProgress(progress)
    open fun incrementProgressBy(diff: Int) = setProgress(mProgress + diff)
    open fun getSecondaryProgress(): Int = mSecondaryProgress
    open fun setSecondaryProgress(secondaryProgress: Int) {
        mSecondaryProgress = secondaryProgress
    }

    open fun getMax(): Int = mMax
    open fun setMax(max: Int) {
        mMax = kotlin.math.max(max, mMin)
        mHorizontal = true
        mIndeterminate = false
    }

    open fun getMin(): Int = mMin
    open fun setMin(min: Int) {
        mMin = min
    }

    open fun getProgressTintList(): ColorStateList? = mProgressTint
    open fun setProgressTintList(tint: ColorStateList?) {
        mProgressTint = tint
    }

    open fun setProgressBackgroundTintList(tint: ColorStateList?) {
        mProgressBackgroundTint = tint
    }

    open fun getProgressBackgroundTintList(): ColorStateList? = mProgressBackgroundTint
    open fun setIndeterminateTintList(tint: ColorStateList?) {
        mIndeterminateTint = tint
    }

    open fun getIndeterminateTintList(): ColorStateList? = mIndeterminateTint
    open fun setProgressDrawable(d: Drawable?) {
        mProgressDrawable = d
        if (d != null) mHorizontal = true
    }

    open fun getProgressDrawable(): Drawable? = mProgressDrawable
    open fun setIndeterminateDrawable(d: Drawable?) {
        mIndeterminateDrawable = d
    }

    open fun getIndeterminateDrawable(): Drawable? = mIndeterminateDrawable
    open fun setInterpolator(interpolator: android.view.animation.Interpolator?) {}

    /**
     * ProgressBar.onMeasure with the default styles: Widget.Material.ProgressBar is 48dp square,
     * .Horizontal 16dp high (the drawables are painted by the renderer)
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = com.lagradost.desktop.runtime.ui.WidgetDefaults
        var dw: Int
        var dh: Int
        if (mHorizontal) {
            dw = if (this is RatingBar) getNumStars() * d.dp(32f) else d.dp(if (this is SeekBar) 48f else 24f)
            dh = d.dp(if (this is SeekBar) 40f else if (this is RatingBar) 32f else 16f)
        } else {
            dw = d.dp(48f)
            dh = d.dp(48f)
        }
        dw += getPaddingLeft() + getPaddingRight()
        dh += getPaddingTop() + getPaddingBottom()
        dw = kotlin.math.max(dw, getSuggestedMinimumWidth())
        dh = kotlin.math.max(dh, getSuggestedMinimumHeight())
        setMeasuredDimension(resolveSizeAndState(dw, widthMeasureSpec, 0), resolveSizeAndState(dh, heightMeasureSpec, 0))
    }
}

abstract class AbsSeekBar : ProgressBar {
    private var mThumbTint by mutableStateOf<ColorStateList?>(null)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun setThumb(thumb: Drawable?) {}
    open fun setThumbTintList(tint: ColorStateList?) {
        mThumbTint = tint
    }

    open fun getThumbTintList(): ColorStateList? = mThumbTint
    open fun setKeyProgressIncrement(increment: Int) {}
}

open class SeekBar : AbsSeekBar {
    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: SeekBar)
        fun onStopTrackingTouch(seekBar: SeekBar)
    }

    private var mListener: OnSeekBarChangeListener? = null

    constructor(context: Context?) : super(context) {
        initSeek()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initSeek()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initSeek()
    }

    private fun initSeek() {
        setHorizontalStyle(true)
        setIndeterminate(false)
        setFocusable(true)
    }

    open fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        mListener = l
    }

    override fun setProgress(progress: Int) {
        val old = getProgress()
        super.setProgress(progress)
        if (old != getProgress()) mListener?.onProgressChanged(this, getProgress(), false)
    }

    /** Called by the renderer while dragging */
    fun onUserProgress(progress: Int, phase: Int) {
        when (phase) {
            0 -> mListener?.onStartTrackingTouch(this)
            2 -> mListener?.onStopTrackingTouch(this)
            else -> {
                val old = getProgress()
                super.setProgress(progress)
                if (old != getProgress()) mListener?.onProgressChanged(this, getProgress(), true)
            }
        }
    }
}

open class RatingBar : AbsSeekBar {
    fun interface OnRatingBarChangeListener {
        fun onRatingChanged(ratingBar: RatingBar, rating: Float, fromUser: Boolean)
    }

    private var mRating by mutableFloatStateOf(0f)
    private var mNumStars by mutableIntStateOf(5)
    private var mListener: OnRatingBarChangeListener? = null

    init {
        setHorizontalStyle(true)
        setIndeterminate(false)
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    open fun setRating(rating: Float) {
        mRating = rating
        mListener?.onRatingChanged(this, rating, false)
    }

    open fun getRating(): Float = mRating
    open fun setNumStars(numStars: Int) {
        mNumStars = numStars
        requestLayout()
    }

    open fun getNumStars(): Int = mNumStars
    open fun setStepSize(stepSize: Float) {}
    open fun setIsIndicator(isIndicator: Boolean) {}
    open fun setOnRatingBarChangeListener(listener: OnRatingBarChangeListener?) {
        mListener = listener
    }
}

open class RadioGroup : LinearLayout {
    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(group: RadioGroup, checkedId: Int)
    }

    private var mCheckedId = View.NO_ID
    private var mProtectFromCheckedChange = false
    private var mListener: OnCheckedChangeListener? = null

    constructor(context: Context?) : super(context) {
        setOrientation(VERTICAL)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    private val childListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        if (mProtectFromCheckedChange) return@OnCheckedChangeListener
        if (!isChecked) return@OnCheckedChangeListener
        mProtectFromCheckedChange = true
        if (mCheckedId != View.NO_ID && mCheckedId != buttonView.getId()) setCheckedStateForView(mCheckedId, false)
        mProtectFromCheckedChange = false
        var id = buttonView.getId()
        if (id == View.NO_ID) {
            id = View.generateViewId()
            buttonView.setId(id)
        }
        setCheckedId(id)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child is RadioButton) {
            if (child.getId() == View.NO_ID) child.setId(View.generateViewId())
            child.mGroupListener = childListener
            if (child.isChecked()) {
                mProtectFromCheckedChange = true
                if (mCheckedId != View.NO_ID) setCheckedStateForView(mCheckedId, false)
                mProtectFromCheckedChange = false
                setCheckedId(child.getId())
            }
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child is RadioButton) child.mGroupListener = null
    }

    open fun check(id: Int) {
        if (id != View.NO_ID && id == mCheckedId) return
        if (mCheckedId != View.NO_ID) setCheckedStateForView(mCheckedId, false)
        if (id != View.NO_ID) setCheckedStateForView(id, true)
        setCheckedId(id)
    }

    private fun setCheckedId(id: Int) {
        val changed = id != mCheckedId
        mCheckedId = id
        if (changed) mListener?.onCheckedChanged(this, mCheckedId)
    }

    private fun setCheckedStateForView(viewId: Int, checked: Boolean) {
        val checkedView = findViewById<View>(viewId)
        if (checkedView is RadioButton) {
            mProtectFromCheckedChange = true
            checkedView.setChecked(checked)
            mProtectFromCheckedChange = false
        }
    }

    open fun getCheckedRadioButtonId(): Int = mCheckedId
    open fun clearCheck() = check(View.NO_ID)
    open fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        mListener = listener
    }
}

open class Toast(private val context: Context?) {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @JvmStatic
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast =
            Toast(context).also {
                it.text = text
                it.setDuration(duration)
            }

        @JvmStatic
        fun makeText(context: Context?, resId: Int, duration: Int): Toast =
            makeText(context, (context ?: AndroidRuntime.applicationContext)?.resources?.getText(resId), duration)
    }

    private var text: CharSequence? = null
    private var mDuration = LENGTH_SHORT
    private var mView: View? = null

    open fun show() {
        val t = text ?: (mView as? TextView)?.getText() ?: return
        AndroidRuntime.host.showToast(t, mDuration == LENGTH_LONG)
    }

    open fun cancel() {}
    open fun setText(s: CharSequence?) {
        text = s
    }

    open fun setText(resId: Int) {
        text = context?.resources?.getText(resId)
    }

    open fun setDuration(duration: Int) {
        mDuration = duration
    }

    open fun getDuration(): Int = mDuration
    open fun setGravity(gravity: Int, xOffset: Int, yOffset: Int) {}
    open fun setMargin(horizontalMargin: Float, verticalMargin: Float) {}

    open fun setView(view: View?) {
        mView = view
    }

    open fun getView(): View? = mView
}

open class PopupMenu(private val context: Context, private val anchor: View) {
    fun interface OnMenuItemClickListener {
        fun onMenuItemClick(item: android.view.MenuItem): Boolean
    }

    fun interface OnDismissListener {
        fun onDismiss(menu: PopupMenu)
    }

    val menu = androidx.appcompat.view.menu.MenuBuilder(context)
    private var clickListener: OnMenuItemClickListener? = null
    private var dismissListener: OnDismissListener? = null

    constructor(context: Context, anchor: View, gravity: Int) : this(context, anchor)

    open fun inflate(menuRes: Int) = android.view.MenuInflater(context).inflate(menuRes, menu)
    open fun getMenuInflater(): android.view.MenuInflater = android.view.MenuInflater(context)
    open fun setOnMenuItemClickListener(listener: OnMenuItemClickListener?) {
        clickListener = listener
    }

    open fun setOnDismissListener(listener: OnDismissListener?) {
        dismissListener = listener
    }

    private val popup = PopupWindow()

    open fun show() {
        val column = LinearLayout(context)
        column.setOrientation(LinearLayout.VERTICAL)
        column.setBackgroundColor(com.lagradost.desktop.runtime.ui.ThemeBridge.surface)
        val pad = com.lagradost.desktop.runtime.ui.WidgetDefaults.dp(12f)
        for (item in menu.visibleItems()) {
            val row = LinearLayout(context)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setGravity(Gravity.CENTER_VERTICAL)
            row.setPadding(pad, pad / 2, pad, pad / 2)
            row.setMinimumWidth(com.lagradost.desktop.runtime.ui.WidgetDefaults.dp(196f))
            item.getIcon()?.let { icon ->
                val image = ImageView(context)
                image.setImageDrawable(icon)
                val s = com.lagradost.desktop.runtime.ui.WidgetDefaults.dp(24f)
                row.addView(image, LinearLayout.LayoutParams(s, s))
            }
            val label = TextView(context)
            val title = item.getTitle() ?: ""
            label.setText(if (item.isChecked()) "✓ $title" else title)
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            if (!item.isEnabled()) label.setAlpha(0.4f)
            row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            row.setOnClickListener {
                if (!item.isEnabled()) return@setOnClickListener
                if (item.isCheckable()) item.setChecked(!item.isChecked())
                if (clickListener?.onMenuItemClick(item) != true) item.invokeAction()
                popup.dismiss()
            }
            column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        popup.setContentView(column)
        popup.setFocusable(true)
        popup.setOnDismissListener(object : PopupWindow.OnDismissListener {
            override fun onDismiss() {
                dismissListener?.onDismiss(this@PopupMenu)
            }
        })
        popup.showAsDropDown(anchor)
    }

    open fun dismiss() = popup.dismiss()
}

open class PopupWindow {
    private var contentView: View? = null
    private var showing = false
    private var token: Any? = null
    private var width = ViewGroup.LayoutParams.WRAP_CONTENT
    private var height = ViewGroup.LayoutParams.WRAP_CONTENT
    private var dismissListener: OnDismissListener? = null

    fun interface OnDismissListener {
        fun onDismiss()
    }

    constructor()
    constructor(context: Context?)
    constructor(contentView: View?) {
        this.contentView = contentView
    }

    constructor(contentView: View?, width: Int, height: Int) {
        this.contentView = contentView
        this.width = width
        this.height = height
    }

    constructor(contentView: View?, width: Int, height: Int, focusable: Boolean) {
        this.contentView = contentView
        this.width = width
        this.height = height
    }

    open fun setContentView(contentView: View?) {
        this.contentView = contentView
    }

    open fun getContentView(): View? = contentView
    open fun setWidth(width: Int) { this.width = width }
    open fun getWidth(): Int = width
    open fun setHeight(height: Int) { this.height = height }
    open fun getHeight(): Int = height
    open fun setFocusable(focusable: Boolean) {}
    open fun setOutsideTouchable(touchable: Boolean) {}
    open fun setBackgroundDrawable(background: Drawable?) {}
    open fun setElevation(elevation: Float) {}
    open fun setAnimationStyle(animationStyle: Int) {}
    open fun setOnDismissListener(onDismissListener: OnDismissListener?) {
        dismissListener = onDismissListener
    }

    open fun isShowing(): Boolean = showing
    open fun showAsDropDown(anchor: View) = showAsDropDown(anchor, 0, 0)
    open fun showAsDropDown(anchor: View, xoff: Int, yoff: Int) = showAsDropDown(anchor, xoff, yoff, Gravity.NO_GRAVITY)
    open fun showAsDropDown(anchor: View, xoff: Int, yoff: Int, gravity: Int) {
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        show(loc[0] + xoff, loc[1] + anchor.getHeight() + yoff)
    }

    open fun showAtLocation(parent: View, gravity: Int, x: Int, y: Int) {
        val loc = IntArray(2)
        parent.getLocationInWindow(loc)
        show(loc[0] + x, loc[1] + y)
    }

    private fun show(x: Int, y: Int) {
        val content = contentView ?: return
        dismiss()
        token = com.lagradost.desktop.runtime.AndroidRuntime.host.showPopup(x, y, 0, 0, content) {
            token = null
            showing = false
            dismissListener?.onDismiss()
        }
        showing = true
    }

    open fun dismiss() {
        val current = token ?: return
        token = null
        showing = false
        com.lagradost.desktop.runtime.AndroidRuntime.host.dismissPopup(current)
        dismissListener?.onDismiss()
    }

    open fun update() {}
}

open class ListPopupWindow(private val context: Context) {
    private var anchor: View? = null
    private var adapter: ListAdapter? = null
    private var click: AdapterView.OnItemClickListener? = null
    private var modal = false
    private val popup = PopupWindow()
    private var showing = false

    fun setAdapter(adapter: ListAdapter?) { this.adapter = adapter }
    fun setAnchorView(anchor: View?) { this.anchor = anchor }
    fun setModal(modal: Boolean) { this.modal = modal }
    fun setOnItemClickListener(listener: AdapterView.OnItemClickListener?) { click = listener }
    fun setOnDismissListener(listener: PopupWindow.OnDismissListener?) {
        popup.setOnDismissListener(listener)
    }
    fun setWidth(width: Int) { popup.setWidth(width) }
    fun setHeight(height: Int) { popup.setHeight(height) }
    fun setContentWidth(width: Int) { popup.setWidth(width) }
    fun isShowing(): Boolean = showing

    fun show() {
        val anchor = anchor ?: return
        val list = ListView(context)
        adapter?.let { list.setAdapter(it) }
        list.setOnItemClickListener(AdapterView.OnItemClickListener { parent, view, position, id ->
            click?.onItemClick(parent, view, position, id)
            dismiss()
        })
        popup.setContentView(list)
        popup.setFocusable(modal)
        popup.showAsDropDown(anchor)
        showing = true
    }

    fun dismiss() {
        showing = false
        popup.dismiss()
    }

    companion object {
        const val POSITION_PROMPT_ABOVE = 0
        const val POSITION_PROMPT_BELOW = 1
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
