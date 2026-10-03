package android.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.KeyListener
import android.text.method.MovementMethod
import android.text.method.PasswordTransformationMethod
import android.text.method.TransformationMethod
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.ui.ViewAttributes

open class TextView : View {
    enum class BufferType { NORMAL, SPANNABLE, EDITABLE }

    fun interface OnEditorActionListener {
        fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context) {
        if (attrs != null) {
            ViewAttributes.applyViewAttributes(this, attrs)
            ViewAttributes.applyTextViewAttributes(this, attrs)
        }
    }

    // ---------------------------------------------------------------- state observed by the renderer
    private var mText by mutableStateOf<CharSequence>("")
    private var mHint by mutableStateOf<CharSequence?>(null)
    private var mTextColor by mutableStateOf<ColorStateList?>(null)
    private var mHintTextColor by mutableStateOf<ColorStateList?>(null)
    private var mLinkTextColor by mutableStateOf<ColorStateList?>(null)
    private var mTextSizePx by mutableFloatStateOf(14f * AndroidRuntime.displayMetrics.scaledDensity)
    private var mTypeface by mutableStateOf<Typeface?>(null)
    private var mTypefaceStyle by mutableIntStateOf(Typeface.NORMAL)
    private var mGravity by mutableIntStateOf(defaultGravity())
    private var mMaxLines by mutableIntStateOf(Int.MAX_VALUE)
    private var mMinLines by mutableIntStateOf(0)
    private var mSingleLine by mutableStateOf(false)
    private var mEllipsize by mutableStateOf<TextUtils.TruncateAt?>(null)
    private var mAllCaps by mutableStateOf(false)
    private var mLetterSpacing by mutableFloatStateOf(0f)
    private var mLineSpacingExtra by mutableFloatStateOf(0f)
    private var mLineSpacingMultiplier by mutableFloatStateOf(1f)
    private var mInputType by mutableIntStateOf(InputType.TYPE_NULL)
    private var mTransformation by mutableStateOf<TransformationMethod?>(null)
    private var mError by mutableStateOf<CharSequence?>(null)
    private var mPaintFlags by mutableIntStateOf(0)
    private var mMaxWidth by mutableIntStateOf(Int.MAX_VALUE)
    private var mMaxHeight by mutableIntStateOf(Int.MAX_VALUE)
    private var mCompoundPadding by mutableIntStateOf(0)
    private val mCompound = arrayOfNulls<Drawable>(4)
    val compoundVersion = mutableIntStateOf(0)
    private var mCursorVisible = true
    private var mImeOptions = 0
    private var mEditorListener: OnEditorActionListener? = null
    private val mWatchers = ArrayList<TextWatcher>()
    private var mMovement: MovementMethod? = null
    private var mKeyListener: KeyListener? = null
    private var mFilters: Array<InputFilter> = emptyArray()
    private var mSelectable = false
    private var mEditable: SpannableStringBuilder? = null
    private val mPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var mShadowRadius = 0f
    private var mShadowColor = 0
    private var mIncludeFontPadding = true
    private var mFreezesText = false

    /** Selection requested by setSelection, consumed by the renderer */
    var selStart by mutableIntStateOf(-1)
    var selEnd by mutableIntStateOf(-1)
    val selectAllRequest = mutableIntStateOf(0)

    protected open fun getDefaultEditable(): Boolean = false

    /** Gravity before attributes are applied (EditText's style uses center_vertical) */
    protected open fun defaultGravity(): Int = Gravity.TOP or Gravity.START

    /** Whether this TextView edits text (EditText, or editable input type) */
    open fun isEditableText(): Boolean = getDefaultEditable()

    // ---------------------------------------------------------------- text
    open fun getText(): CharSequence {
        if (isEditableText()) return editable()
        return mText
    }

    fun getEditableText(): Editable? = if (isEditableText()) editable() else null

    protected fun editable(): SpannableStringBuilder {
        val e = mEditable
        if (e != null) return e
        return SpannableStringBuilder(mText).also {
            it.setFilters(mFilters)
            it.addChangeListener { onEditableChanged() }
            mEditable = it
        }
    }

    private var inEditableCallback = false
    private fun onEditableChanged() {
        if (inEditableCallback) return
        mText = SpannableStringBuilder(mEditable ?: return)
        textLayoutChanged()
    }

    open fun setText(text: CharSequence?) = setText(text, BufferType.NORMAL)

    open fun setText(text: CharSequence?, type: BufferType?) {
        val newText = text ?: ""
        val old = getText().toString()
        for (w in mWatchers.toList()) w.beforeTextChanged(old, 0, old.length, newText.length)
        if (isEditableText()) {
            inEditableCallback = true
            try {
                editable().replace(0, editable().length, newText)
            } finally {
                inEditableCallback = false
            }
            mText = SpannableStringBuilder(editable())
        } else {
            mText = if (mAllCaps && newText !is android.text.Spanned) newText.toString() else newText
        }
        for (w in mWatchers.toList()) w.onTextChanged(getText(), 0, old.length, newText.length)
        if (mWatchers.isNotEmpty()) {
            val e = if (isEditableText()) editable() else SpannableStringBuilder(newText)
            for (w in mWatchers.toList()) w.afterTextChanged(e)
        }
        textLayoutChanged()
    }

    fun setText(resid: Int) = setText(getContext().resources.getText(resid))
    fun setText(resid: Int, type: BufferType?) = setText(getContext().resources.getText(resid), type)
    fun setText(text: CharArray, start: Int, len: Int) = setText(String(text, start, len))
    fun setTextKeepState(text: CharSequence?) = setText(text)
    fun setTextKeepState(text: CharSequence?, type: BufferType?) = setText(text, type)

    fun append(text: CharSequence?) = append(text, 0, text?.length ?: 0)
    open fun append(text: CharSequence?, start: Int, end: Int) {
        if (text == null) return
        if (isEditableText()) {
            editable().append(text, start, end)
            mText = SpannableStringBuilder(editable())
        } else {
            mText = SpannableStringBuilder(mText).append(text, start, end)
        }
        textLayoutChanged()
    }

    /** Called by the renderer when the user edits the text */
    fun onUserTextChanged(newValue: String) {
        val old = getText().toString()
        if (old == newValue) return
        // Compute the changed range like an IME would
        var prefix = 0
        while (prefix < old.length && prefix < newValue.length && old[prefix] == newValue[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < newValue.length - prefix &&
            old[old.length - 1 - suffix] == newValue[newValue.length - 1 - suffix]) suffix++
        val before = old.length - prefix - suffix
        val count = newValue.length - prefix - suffix
        for (w in mWatchers.toList()) w.beforeTextChanged(old, prefix, before, count)
        inEditableCallback = true
        try {
            editable().replace(prefix, prefix + before, newValue, prefix, prefix + count)
        } finally {
            inEditableCallback = false
        }
        mText = SpannableStringBuilder(editable())
        for (w in mWatchers.toList()) w.onTextChanged(editable(), prefix, before, count)
        for (w in mWatchers.toList()) w.afterTextChanged(editable())
        textLayoutChanged()
    }

    open fun length(): Int = getText().length

    /** Desktop renderer: the displayed text, read from snapshot state so edits recompose */
    fun textState(): CharSequence = mText

    fun addTextChangedListener(watcher: TextWatcher) {
        mWatchers.add(watcher)
    }

    fun removeTextChangedListener(watcher: TextWatcher) {
        mWatchers.remove(watcher)
    }

    open fun getHint(): CharSequence? = mHint
    fun setHint(hint: CharSequence?) {
        mHint = hint
        textLayoutChanged()
    }

    fun setHint(resid: Int) = setHint(getContext().resources.getText(resid))

    // ---------------------------------------------------------------- appearance
    fun setTextColor(color: Int) = setTextColor(ColorStateList.valueOf(color))
    open fun setTextColor(colors: ColorStateList?) {
        mTextColor = colors
    }

    fun getTextColors(): ColorStateList = mTextColor ?: ColorStateList.valueOf(defaultTextColor())
    fun getCurrentTextColor(): Int = getTextColors().getColorForState(getDrawableState(), defaultTextColor())
    fun setHintTextColor(color: Int) = setHintTextColor(ColorStateList.valueOf(color))
    fun setHintTextColor(colors: ColorStateList?) {
        mHintTextColor = colors
    }

    fun getHintTextColors(): ColorStateList? = mHintTextColor
    fun getCurrentHintTextColor(): Int = mHintTextColor?.getColorForState(getDrawableState(), Color.GRAY) ?: Color.GRAY
    fun setLinkTextColor(color: Int) = setLinkTextColor(ColorStateList.valueOf(color))
    fun setLinkTextColor(colors: ColorStateList?) {
        mLinkTextColor = colors
    }

    fun getLinkTextColors(): ColorStateList? = mLinkTextColor
    fun hasExplicitTextColor(): Boolean = mTextColor != null

    /** Default text color follows the desktop theme (set by the renderer) */
    protected open fun defaultTextColor(): Int = com.lagradost.desktop.runtime.ui.ThemeBridge.textColorPrimary

    open fun setTextSize(size: Float) = setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    open fun setTextSize(unit: Int, size: Float) {
        mTextSizePx = TypedValue.applyDimension(unit, size, AndroidRuntime.displayMetrics)
        textLayoutChanged()
    }

    open fun getTextSize(): Float = mTextSizePx
    fun getTextSizeUnit(): Int = TypedValue.COMPLEX_UNIT_PX
    open fun setTypeface(tf: Typeface?) {
        mTypeface = tf
        mTypefaceStyle = tf?.getStyle() ?: Typeface.NORMAL
        textLayoutChanged()
    }

    open fun setTypeface(tf: Typeface?, style: Int) {
        mTypeface = tf
        mTypefaceStyle = style
        textLayoutChanged()
    }

    open fun getTypeface(): Typeface? = mTypeface
    fun getTypefaceStyle(): Int = mTypefaceStyle
    open fun setTextAppearance(resId: Int) {}
    open fun setTextAppearance(context: Context?, resId: Int) {}
    open fun getGravity(): Int = mGravity
    open fun setGravity(gravity: Int) {
        var g = gravity
        if ((g and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) g = g or Gravity.START
        if ((g and Gravity.VERTICAL_GRAVITY_MASK) == 0) g = g or Gravity.TOP
        mGravity = g
        textLayoutChanged()
    }

    open fun setMaxLines(maxLines: Int) {
        mMaxLines = maxLines
        textLayoutChanged()
    }

    open fun getMaxLines(): Int = mMaxLines
    open fun setMinLines(minLines: Int) {
        mMinLines = minLines
        textLayoutChanged()
    }

    open fun getMinLines(): Int = mMinLines
    open fun setLines(lines: Int) {
        mMaxLines = lines
        mMinLines = lines
        textLayoutChanged()
    }

    open fun setSingleLine() = setSingleLine(true)
    open fun setSingleLine(singleLine: Boolean) {
        mSingleLine = singleLine
        if (singleLine) mMaxLines = 1 else if (mMaxLines == 1) mMaxLines = Int.MAX_VALUE
        textLayoutChanged()
    }

    fun isSingleLine(): Boolean = mSingleLine
    open fun setEllipsize(where: TextUtils.TruncateAt?) {
        mEllipsize = where
        textLayoutChanged()
    }

    open fun getEllipsize(): TextUtils.TruncateAt? = mEllipsize
    open fun setMarqueeRepeatLimit(marqueeLimit: Int) {}
    open fun setHorizontallyScrolling(whether: Boolean) {}
    open fun setAllCaps(allCaps: Boolean) {
        mAllCaps = allCaps
        textLayoutChanged()
    }

    fun isAllCaps(): Boolean = mAllCaps
    open fun setLetterSpacing(letterSpacing: Float) {
        mLetterSpacing = letterSpacing
        textLayoutChanged()
    }

    open fun getLetterSpacing(): Float = mLetterSpacing
    open fun setLineSpacing(add: Float, mult: Float) {
        mLineSpacingExtra = add
        mLineSpacingMultiplier = mult
        textLayoutChanged()
    }

    open fun getLineSpacingExtra(): Float = mLineSpacingExtra
    open fun getLineSpacingMultiplier(): Float = mLineSpacingMultiplier
    open fun setLineHeight(lineHeight: Int) {}
    open fun getLineHeight(): Int =
        (com.lagradost.desktop.runtime.ui.TextLayoutSupport.lineHeight(this) * mLineSpacingMultiplier + mLineSpacingExtra).toInt()
    open fun setMinWidth(minPixels: Int) = setMinimumWidth(minPixels)
    open fun setMaxWidth(maxPixels: Int) {
        mMaxWidth = maxPixels
        textLayoutChanged()
    }

    open fun getMaxWidth(): Int = mMaxWidth
    open fun setMinHeight(minPixels: Int) = setMinimumHeight(minPixels)
    open fun setMaxHeight(maxPixels: Int) {
        mMaxHeight = maxPixels
        textLayoutChanged()
    }

    open fun getMaxHeight(): Int = mMaxHeight
    open fun setEms(ems: Int) {}
    open fun setMinEms(minEms: Int) {}
    open fun setMaxEms(maxEms: Int) {}
    open fun setWidth(pixels: Int) = setMinWidth(pixels)
    open fun setHeight(pixels: Int) = setMinHeight(pixels)
    open fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) {
        mShadowRadius = radius
        mShadowColor = color
    }

    open fun getShadowRadius(): Float = mShadowRadius
    open fun getShadowColor(): Int = mShadowColor
    open fun setIncludeFontPadding(includepad: Boolean) {
        mIncludeFontPadding = includepad
    }

    open fun setPaintFlags(flags: Int) {
        mPaintFlags = flags
    }

    open fun getPaintFlags(): Int = mPaintFlags
    fun getPaint(): TextPaint = mPaint.also {
        it.setTextSize(mTextSizePx)
        it.setTypeface(mTypeface)
        it.setColor(getCurrentTextColor())
    }

    open fun setBreakStrategy(breakStrategy: Int) {}
    open fun setHyphenationFrequency(hyphenationFrequency: Int) {}
    open fun setJustificationMode(justificationMode: Int) {}
    open fun setAutoSizeTextTypeWithDefaults(autoSizeTextType: Int) {}
    open fun setAutoSizeTextTypeUniformWithConfiguration(min: Int, max: Int, step: Int, unit: Int) {}
    open fun getLineCount(): Int = mTextLayout?.lineCount ?: 0
    open fun getLayout(): android.text.Layout? = mTextLayout?.let { com.lagradost.desktop.runtime.ui.DesktopTextLayout(it, getText()) }
    open fun setFreezesText(freezesText: Boolean) {
        mFreezesText = freezesText
    }

    // ---------------------------------------------------------------- measure (TextView.onMeasure)
    /** Text layouts of the last measure pass, [mLayoutWidth] wide (the text area) */
    private var mTextLayout: androidx.compose.ui.text.TextLayoutResult? = null
    private var mHintLayout: androidx.compose.ui.text.TextLayoutResult? = null
    private var mLayoutWidth = -1

    /** checkForRelayout: the text or its style changed, measure and lay out again */
    protected fun textLayoutChanged() {
        mTextLayout = null
        mHintLayout = null
        mLayoutWidth = -1
        requestLayout()
        invalidate()
    }

    /** Size of a compound drawable (its bounds, else its intrinsic size) */
    fun compoundSize(index: Int): IntArray {
        val d = mCompound[index] ?: return intArrayOf(0, 0)
        val b = d.getBounds()
        return intArrayOf(
            if (b.width() > 0) b.width() else kotlin.math.max(0, d.getIntrinsicWidth()),
            if (b.height() > 0) b.height() else kotlin.math.max(0, d.getIntrinsicHeight()),
        )
    }

    open fun getCompoundPaddingLeft(): Int = getPaddingLeft() + if (mCompound[0] != null) compoundSize(0)[0] + mCompoundPadding else 0
    open fun getCompoundPaddingRight(): Int = getPaddingRight() + if (mCompound[2] != null) compoundSize(2)[0] + mCompoundPadding else 0
    open fun getCompoundPaddingTop(): Int = getPaddingTop() + if (mCompound[1] != null) compoundSize(1)[1] + mCompoundPadding else 0
    open fun getCompoundPaddingBottom(): Int = getPaddingBottom() + if (mCompound[3] != null) compoundSize(3)[1] + mCompoundPadding else 0
    open fun getCompoundPaddingStart(): Int = getCompoundPaddingLeft()
    open fun getCompoundPaddingEnd(): Int = getCompoundPaddingRight()
    open fun getExtendedPaddingTop(): Int = getCompoundPaddingTop()
    open fun getExtendedPaddingBottom(): Int = getCompoundPaddingBottom()
    open fun getTotalPaddingLeft(): Int = getCompoundPaddingLeft()
    open fun getTotalPaddingRight(): Int = getCompoundPaddingRight()
    open fun getTotalPaddingStart(): Int = getCompoundPaddingLeft()
    open fun getTotalPaddingEnd(): Int = getCompoundPaddingRight()
    open fun getTotalPaddingTop(): Int = getExtendedPaddingTop() + getVerticalOffset(true)
    open fun getTotalPaddingBottom(): Int = getExtendedPaddingBottom() + getBottomVerticalOffset(true)

    /** Offset of the text inside the box for center/bottom gravity (TextView.getVerticalOffset) */
    fun getVerticalOffset(forceNormal: Boolean): Int {
        val gravity = mGravity and Gravity.VERTICAL_GRAVITY_MASK
        if (gravity == Gravity.TOP) return 0
        val hint = !forceNormal && mText.isEmpty() && mHintLayout != null
        val l = (if (hint) mHintLayout else mTextLayout) ?: return 0
        val boxht = getMeasuredHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom()
        val textht = l.size.height
        if (textht >= boxht) return 0
        return if (gravity == Gravity.BOTTOM) boxht - textht else (boxht - textht) shr 1
    }

    private fun getBottomVerticalOffset(forceNormal: Boolean): Int {
        val gravity = mGravity and Gravity.VERTICAL_GRAVITY_MASK
        if (gravity == Gravity.BOTTOM) return 0
        val l = mTextLayout ?: return 0
        val boxht = getMeasuredHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom()
        val textht = l.size.height
        if (textht >= boxht) return 0
        return if (gravity == Gravity.TOP) boxht - textht else (boxht - textht) shr 1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val support = com.lagradost.desktop.runtime.ui.TextLayoutSupport
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val style = support.styleOf(this)
        val text = support.annotated(this, false)
        val hint = if (mHint != null) support.annotated(this, true) else null
        val cl = getCompoundPaddingLeft()
        val cr = getCompoundPaddingRight()
        var width: Int
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize
        } else {
            var des = support.unboundedWidth(this, text, style)
            if (hint != null) des = kotlin.math.max(des, support.unboundedWidth(this, hint, style))
            des = kotlin.math.max(des, kotlin.math.max(compoundSize(1)[0], compoundSize(3)[0]))
            width = des + cl + cr
            if (mMaxWidth != Int.MAX_VALUE) width = kotlin.math.min(width, mMaxWidth)
            width = kotlin.math.max(width, getSuggestedMinimumWidth())
            if (widthMode == MeasureSpec.AT_MOST) width = kotlin.math.min(widthSize, width)
        }
        val want = kotlin.math.max(0, width - cl - cr)
        val params = support.paramsOf(this)
        mTextLayout = support.layout(this, text, style, want, params)
        mHintLayout = hint?.let { support.layout(this, it, style, want, params) }
        mLayoutWidth = want
        var height: Int
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize
        } else {
            height = kotlin.math.max(desiredHeight(mTextLayout), desiredHeight(mHintLayout))
            if (heightMode == MeasureSpec.AT_MOST) height = kotlin.math.min(height, heightSize)
        }
        setMeasuredDimension(width, height)
    }

    /** TextView.getDesiredHeight */
    private fun desiredHeight(layout: androidx.compose.ui.text.TextLayoutResult?): Int {
        if (layout == null) return 0
        var desired = layout.size.height
        desired = kotlin.math.max(desired, kotlin.math.max(compoundSize(0)[1], compoundSize(2)[1]))
        desired += getCompoundPaddingTop() + getCompoundPaddingBottom()
        val lines = layout.lineCount
        if (lines < mMinLines) desired += getLineHeight() * (mMinLines - lines)
        if (mMaxHeight != Int.MAX_VALUE) desired = kotlin.math.min(desired, mMaxHeight)
        return kotlin.math.max(desired, getSuggestedMinimumHeight())
    }

    override fun getBaseline(): Int {
        val layout = mTextLayout ?: return super.getBaseline()
        val voffset = if ((mGravity and Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) getVerticalOffset(true) else 0
        return getExtendedPaddingTop() + voffset + kotlin.math.round(layout.firstBaseline).toInt()
    }

    /**
     * Renderer: the layout of the text (or the hint when [hint]) for the current content width,
     * the one measured when the width did not change since.
     */
    fun textLayoutForDrawing(hint: Boolean): androidx.compose.ui.text.TextLayoutResult? {
        val want = kotlin.math.max(0, getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight())
        val support = com.lagradost.desktop.runtime.ui.TextLayoutSupport
        if (mLayoutWidth != want || (if (hint) mHintLayout else mTextLayout) == null) {
            val style = support.styleOf(this)
            val params = support.paramsOf(this)
            mTextLayout = support.layout(this, support.annotated(this, false), style, want, params)
            mHintLayout = if (mHint != null) support.layout(this, support.annotated(this, true), style, want, params) else null
            mLayoutWidth = want
        }
        return if (hint) mHintLayout else mTextLayout
    }

    // ---------------------------------------------------------------- compound drawables
    fun getCompoundDrawables(): Array<Drawable?> = mCompound.copyOf()
    fun getCompoundDrawablesRelative(): Array<Drawable?> = mCompound.copyOf()
    open fun setCompoundDrawables(left: Drawable?, top: Drawable?, right: Drawable?, bottom: Drawable?) {
        mCompound[0] = left; mCompound[1] = top; mCompound[2] = right; mCompound[3] = bottom
        compoundVersion.intValue++
        textLayoutChanged()
    }

    open fun setCompoundDrawablesWithIntrinsicBounds(left: Drawable?, top: Drawable?, right: Drawable?, bottom: Drawable?) {
        for (d in arrayOf(left, top, right, bottom)) d?.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight())
        setCompoundDrawables(left, top, right, bottom)
    }

    open fun setCompoundDrawablesWithIntrinsicBounds(left: Int, top: Int, right: Int, bottom: Int) {
        val ctx = getContext()
        setCompoundDrawablesWithIntrinsicBounds(
            if (left != 0) ctx.getDrawable(left) else null, if (top != 0) ctx.getDrawable(top) else null,
            if (right != 0) ctx.getDrawable(right) else null, if (bottom != 0) ctx.getDrawable(bottom) else null
        )
    }

    open fun setCompoundDrawablesRelative(start: Drawable?, top: Drawable?, end: Drawable?, bottom: Drawable?) = setCompoundDrawables(start, top, end, bottom)
    open fun setCompoundDrawablesRelativeWithIntrinsicBounds(start: Drawable?, top: Drawable?, end: Drawable?, bottom: Drawable?) =
        setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom)

    open fun setCompoundDrawablesRelativeWithIntrinsicBounds(start: Int, top: Int, end: Int, bottom: Int) =
        setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom)

    open fun setCompoundDrawablePadding(pad: Int) {
        mCompoundPadding = pad
        textLayoutChanged()
    }

    open fun getCompoundDrawablePadding(): Int = mCompoundPadding
    open fun setCompoundDrawableTintList(tint: ColorStateList?) {
        for (d in mCompound) d?.setTintList(tint)
        compoundVersion.intValue++
        textLayoutChanged()
    }

    // ---------------------------------------------------------------- input
    open fun setInputType(type: Int) {
        mInputType = type
        val variation = type and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION)
        val password = variation == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) ||
                variation == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
                variation == (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        mTransformation = if (password) PasswordTransformationMethod.getInstance() else null
        if ((type and InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0 && (type and InputType.TYPE_MASK_CLASS) != InputType.TYPE_NULL) {
            if (this is EditText) setSingleLine(true)
        } else if ((type and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) setSingleLine(false)
        textLayoutChanged()
    }

    open fun getInputType(): Int = mInputType
    open fun setRawInputType(type: Int) {
        mInputType = type
    }

    open fun setTransformationMethod(method: TransformationMethod?) {
        mTransformation = method
        textLayoutChanged()
    }

    open fun getTransformationMethod(): TransformationMethod? = mTransformation
    fun isPasswordField(): Boolean = mTransformation is PasswordTransformationMethod
    open fun setImeOptions(imeOptions: Int) {
        mImeOptions = imeOptions
    }

    open fun getImeOptions(): Int = mImeOptions
    open fun setImeActionLabel(label: CharSequence?, actionId: Int) {}
    open fun setOnEditorActionListener(l: OnEditorActionListener?) {
        mEditorListener = l
    }

    /** Called by the renderer when Enter is pressed in a single line field */
    open fun onEditorAction(actionCode: Int) {
        val l = mEditorListener
        if (l != null) {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            if (l.onEditorAction(this, actionCode, event)) return
        }
    }

    fun hasEditorActionListener(): Boolean = mEditorListener != null
    open fun setKeyListener(input: KeyListener?) {
        mKeyListener = input
        if (input != null) mInputType = input.getInputType()
    }

    open fun getKeyListener(): KeyListener? = mKeyListener
    open fun setFilters(filters: Array<InputFilter>?) {
        mFilters = filters ?: emptyArray()
        mEditable?.setFilters(mFilters)
    }

    open fun getFilters(): Array<InputFilter> = mFilters
    open fun setMovementMethod(movement: MovementMethod?) {
        mMovement = movement
    }

    fun getMovementMethod(): MovementMethod? = mMovement
    open fun setLinksClickable(whether: Boolean) {}
    open fun setAutoLinkMask(mask: Int) {}
    open fun setTextIsSelectable(selectable: Boolean) {
        mSelectable = selectable
    }

    open fun isTextSelectable(): Boolean = mSelectable
    open fun setCursorVisible(visible: Boolean) {
        mCursorVisible = visible
    }

    open fun isCursorVisible(): Boolean = mCursorVisible
    open fun setSelectAllOnFocus(selectAllOnFocus: Boolean) {}
    open fun setError(error: CharSequence?) {
        mError = error
    }

    open fun setError(error: CharSequence?, icon: Drawable?) = setError(error)
    open fun getError(): CharSequence? = mError
    open fun getSelectionStart(): Int = if (selStart >= 0) selStart else getText().length
    open fun getSelectionEnd(): Int = if (selEnd >= 0) selEnd else getText().length
    open fun hasSelection(): Boolean = getSelectionStart() != getSelectionEnd()
    open fun setEnabledMultiline() {}

    override fun onCreateDrawableState(states: MutableList<Int>) {}

    // State accessors for the renderer
    fun renderText(): CharSequence = mText
    fun renderHint(): CharSequence? = mHint
    fun renderTextColor(): ColorStateList? = mTextColor
    fun renderHintColor(): ColorStateList? = mHintTextColor
    fun renderTextSizePx(): Float = mTextSizePx
    fun renderTypeface(): Typeface? = mTypeface
    fun renderTypefaceStyle(): Int = mTypefaceStyle
    fun renderGravity(): Int = mGravity
    fun renderMaxLines(): Int = mMaxLines
    fun renderMinLines(): Int = mMinLines
    fun renderEllipsize(): TextUtils.TruncateAt? = mEllipsize
    fun renderAllCaps(): Boolean = mAllCaps
    fun renderLetterSpacing(): Float = mLetterSpacing
    fun renderLineSpacing(): Pair<Float, Float> = mLineSpacingExtra to mLineSpacingMultiplier
    fun renderPaintFlags(): Int = mPaintFlags
    fun renderError(): CharSequence? = mError
    fun renderInputType(): Int = mInputType
    fun renderMaxWidth(): Int = mMaxWidth
    fun renderMaxHeight(): Int = mMaxHeight
    fun renderTransformation(): TransformationMethod? = mTransformation
    fun renderSingleLine(): Boolean = mSingleLine
    fun renderCompoundPadding(): Int = mCompoundPadding

    /** The widget's default style (buttonStyle, editTextStyle ...), applied before the XML attributes */
    protected open fun applyWidgetDefaults() {}

    init {
        applyWidgetDefaults()
    }
}

open class EditText : TextView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        setFocusable(true)
        setFocusableInTouchMode(true)
        setClickable(true)
        // Android: an editable TextView without inputType is multi line text unless singleLine is set
        if (getInputType() == InputType.TYPE_NULL) {
            super.setInputType(if (isSingleLine()) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        }
    }

    override fun defaultGravity(): Int = Gravity.CENTER_VERTICAL or Gravity.START
    override fun applyWidgetDefaults() {
        setBackground(com.lagradost.desktop.runtime.ui.EditTextBackgroundDrawable())
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
    }

    override fun getDefaultEditable(): Boolean = true
    override fun getText(): Editable = editable()

    open fun setSelection(start: Int, stop: Int) {
        selStart = start
        selEnd = stop
    }

    open fun setSelection(index: Int) = setSelection(index, index)
    open fun selectAll() {
        setSelection(0, getText().length)
        selectAllRequest.intValue++
    }

    open fun extendSelection(index: Int) = setSelection(getSelectionStart(), index)
}

open class AutoCompleteTextView : EditText {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var adapter: ListAdapter? = null
    open fun <T> setAdapter(adapter: T) where T : ListAdapter, T : Filterable {
        this.adapter = adapter
    }

    open fun getAdapter(): ListAdapter? = adapter
    open fun setThreshold(threshold: Int) {}
    open fun showDropDown() {}
    open fun dismissDropDown() {}
    open fun setOnItemClickListener(l: AdapterView.OnItemClickListener?) {}
}

open class MultiAutoCompleteTextView : AutoCompleteTextView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
}

open class Button : TextView {
    constructor(context: Context?) : super(context) {
        initButton()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initButton()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initButton()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initButton()
    }

    private fun initButton() {
        setClickable(true)
        setFocusable(true)
    }

    /** Widget.Material.Button */
    override fun applyWidgetDefaults() {
        val d = com.lagradost.desktop.runtime.ui.WidgetDefaults
        setBackground(d.buttonBackground())
        setPadding(d.dp(12f), d.dp(10f), d.dp(12f), d.dp(10f))
        setMinimumHeight(d.dp(48f))
        setMinimumWidth(d.dp(88f))
        setAllCaps(true)
        setGravity(Gravity.CENTER)
    }
}

abstract class CompoundButton : Button, Checkable {
    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean)
    }

    private var mChecked by mutableStateOf(false)
    private var mListener: OnCheckedChangeListener? = null
    internal var mGroupListener: OnCheckedChangeListener? = null
    private var mBroadcasting = false
    private var mButtonTint by mutableStateOf<ColorStateList?>(null)
    private var mButtonDrawable: Drawable? = null

    constructor(context: Context?) : super(context) {
        initCompound()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initCompound()
        if (attrs != null) ViewAttributes.applyCompoundButtonAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initCompound()
        if (attrs != null) ViewAttributes.applyCompoundButtonAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initCompound()
    }

    private fun initCompound() {}

    /** Widget.Material.CompoundButton: no background, the button drawable before the text */
    override fun applyWidgetDefaults() {
        setAllCaps(false)
        setGravity(Gravity.CENTER_VERTICAL or Gravity.START)
        setMinimumHeight(com.lagradost.desktop.runtime.ui.WidgetDefaults.dp(48f))
    }

    override fun getCompoundPaddingLeft(): Int =
        super.getCompoundPaddingLeft() + if (!showsButton()) 0 else com.lagradost.desktop.runtime.ui.WidgetDefaults.compoundButtonWidth()

    override fun getSuggestedMinimumHeight(): Int =
        kotlin.math.max(super.getSuggestedMinimumHeight(), if (this is ToggleButton || (this !is Switch && !showsButton())) 0 else com.lagradost.desktop.runtime.ui.WidgetDefaults.compoundButtonWidth())

    /** desktop: false for compound buttons that draw no check box (Switch, ToggleButton, Chip) */
    internal open fun showsButton(): Boolean = !(this is Switch || this is ToggleButton)

    override fun toggle() = setChecked(!mChecked)
    override fun performClick(): Boolean {
        toggle()
        return super.performClick()
    }

    override fun isChecked(): Boolean = mChecked
    override fun setChecked(checked: Boolean) {
        if (mChecked != checked) {
            mChecked = checked
            refreshDrawableState()
            if (mBroadcasting) return
            mBroadcasting = true
            mListener?.onCheckedChanged(this, mChecked)
            mGroupListener?.onCheckedChanged(this, mChecked)
            mBroadcasting = false
        }
    }

    open fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        mListener = listener
    }

    open fun setButtonDrawable(resId: Int) {}
    open fun setButtonDrawable(drawable: Drawable?) {
        mButtonDrawable = drawable
    }

    open fun getButtonDrawable(): Drawable? = mButtonDrawable
    open fun setButtonTintList(tint: ColorStateList?) {
        mButtonTint = tint
    }

    open fun getButtonTintList(): ColorStateList? = mButtonTint
    override fun onCreateDrawableState(states: MutableList<Int>) {
        // also called by View(context, attrs) before this class is initialized
        if (constructed && mChecked) states.add(STATE_CHECKED)
    }

    private var constructed = false

    init {
        constructed = true
    }
}

interface Checkable {
    fun setChecked(checked: Boolean)
    fun isChecked(): Boolean
    fun toggle()
}

open class CheckBox : CompoundButton {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
}

open class RadioButton : CompoundButton {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun toggle() {
        if (!isChecked()) super.toggle()
    }
}

open class ToggleButton : CompoundButton {
    private var textOn: CharSequence? = "ON"
    private var textOff: CharSequence? = "OFF"

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun getTextOn(): CharSequence? = textOn
    open fun setTextOn(textOn: CharSequence?) {
        this.textOn = textOn
    }

    open fun getTextOff(): CharSequence? = textOff
    open fun setTextOff(textOff: CharSequence?) {
        this.textOff = textOff
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        setText(if (checked) textOn else textOff)
    }
}

open class Switch : CompoundButton {
    private var mThumbTint by mutableStateOf<ColorStateList?>(null)
    private var mTrackTint by mutableStateOf<ColorStateList?>(null)
    private var mShowText = false
    private var mTextOn: CharSequence? = null
    private var mTextOff: CharSequence? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) com.lagradost.desktop.runtime.ui.ViewAttributes.applySwitchAttributes(this, attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        if (attrs != null) com.lagradost.desktop.runtime.ui.ViewAttributes.applySwitchAttributes(this, attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        if (attrs != null) com.lagradost.desktop.runtime.ui.ViewAttributes.applySwitchAttributes(this, attrs)
    }

    open fun setThumbTintList(tint: ColorStateList?) {
        mThumbTint = tint
    }

    open fun getThumbTintList(): ColorStateList? = mThumbTint
    open fun setTrackTintList(tint: ColorStateList?) {
        mTrackTint = tint
    }

    open fun getTrackTintList(): ColorStateList? = mTrackTint
    open fun setThumbDrawable(thumb: Drawable?) {}
    open fun setTrackDrawable(track: Drawable?) {}
    open fun setThumbResource(resId: Int) {}
    open fun setTrackResource(resId: Int) {}
    open fun setShowText(showText: Boolean) {
        mShowText = showText
    }

    open fun getShowText(): Boolean = mShowText
    open fun setTextOn(textOn: CharSequence?) {
        mTextOn = textOn
    }

    open fun setTextOff(textOff: CharSequence?) {
        mTextOff = textOff
    }

    open fun getTextOn(): CharSequence? = mTextOn
    open fun getTextOff(): CharSequence? = mTextOff
    override fun getCompoundPaddingRight(): Int {
        val d = com.lagradost.desktop.runtime.ui.WidgetDefaults
        return super.getCompoundPaddingRight() + d.switchWidth() + if (textState().isNotEmpty()) d.switchPadding() else 0
    }

    open fun setSwitchMinWidth(pixels: Int) {}
    open fun setSwitchPadding(pixels: Int) {}
    open fun setSplitTrack(splitTrack: Boolean) {}
}

open class CheckedTextView : TextView, Checkable {
    private var mChecked by mutableStateOf(false)

    /** Desktop: how the check mark is drawn (CHECK_MARK_*) */
    var checkMarkType by mutableIntStateOf(CHECK_MARK_NONE)

    companion object {
        const val CHECK_MARK_NONE = 0
        const val CHECK_MARK_SINGLE = 1
        const val CHECK_MARK_MULTIPLE = 2
        const val CHECK_MARK_CHECK = 3
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun toggle() = setChecked(!mChecked)
    override fun isChecked(): Boolean = mChecked
    override fun setChecked(checked: Boolean) {
        mChecked = checked
        refreshDrawableState()
    }

    override fun getCompoundPaddingRight(): Int =
        super.getCompoundPaddingRight() + if (checkMarkType != CHECK_MARK_NONE) com.lagradost.desktop.runtime.ui.WidgetDefaults.compoundButtonWidth() else 0

    open fun setCheckMarkDrawable(resId: Int) {}
    open fun setCheckMarkDrawable(d: Drawable?) {}
    override fun onCreateDrawableState(states: MutableList<Int>) {
        if (constructed && mChecked) states.add(STATE_CHECKED)
    }

    private var constructed = false

    init {
        constructed = true
    }
}

interface Filterable {
    fun getFilter(): Filter
}

abstract class Filter {
    class FilterResults {
        @JvmField var values: Any? = null
        @JvmField var count = 0
    }

    fun interface FilterListener {
        fun onFilterComplete(count: Int)
    }

    protected abstract fun performFiltering(constraint: CharSequence?): FilterResults
    protected abstract fun publishResults(constraint: CharSequence?, results: FilterResults)

    fun filter(constraint: CharSequence?) = filter(constraint, null)
    fun filter(constraint: CharSequence?, listener: FilterListener?) {
        val results = performFiltering(constraint)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            publishResults(constraint, results)
            listener?.onFilterComplete(results.count)
        }
    }

    open fun convertResultToString(resultValue: Any?): CharSequence = resultValue?.toString() ?: ""
}
