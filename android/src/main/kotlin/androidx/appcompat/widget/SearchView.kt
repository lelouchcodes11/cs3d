package androidx.appcompat.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.CollapsibleActionView
import com.lagradost.desktop.runtime.ui.ViewAttributes

/**
 * AppCompat SearchView: inflates the library's abc_search_view layout (so upstream can look up
 * search_src_text, search_close_btn, ...) and follows AOSP's collapsed/expanded visibility rules.
 */
open class SearchView : SearchViewPlatform, CollapsibleActionView {
    interface OnQueryTextListener : SearchViewPlatform.OnQueryTextListener
    interface OnCloseListener : SearchViewPlatform.OnCloseListener

    fun interface OnSuggestionListener {
        fun onSuggestionSelect(position: Int): Boolean
    }

    private lateinit var mSearchSrcTextView: SearchAutoComplete
    private lateinit var mSearchEditFrame: View
    private lateinit var mSearchPlate: View
    private lateinit var mSubmitArea: View
    private lateinit var mSearchButton: ImageView
    private lateinit var mGoButton: ImageView
    private lateinit var mCloseButton: ImageView
    private lateinit var mVoiceButton: ImageView
    private lateinit var mCollapsedIcon: ImageView

    private var mQueryListener: SearchViewPlatform.OnQueryTextListener? = null
    private var mCloseListener: SearchViewPlatform.OnCloseListener? = null
    private var mOnQueryTextFocusChangeListener: OnFocusChangeListener? = null
    private var mOnSearchClickListener: OnClickListener? = null
    private var mIconifiedByDefault = true
    private var mIconified = true
    private var mSubmitButtonEnabled = false
    private var mExpandedInActionView = false
    private var mQueryHint: CharSequence? = null
    private var mDefaultQueryHint: CharSequence? = null
    private var mMaxWidth = 0
    private var mOldQueryText: CharSequence? = null
    private var built = false

    var queryHint: CharSequence?
        get() = mQueryHint ?: mDefaultQueryHint
        set(value) {
            mQueryHint = value
            updateQueryHint()
        }

    constructor(context: Context?) : super(context) { build(null) }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) { build(attrs) }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { build(attrs) }

    private fun id(name: String): Int = getContext().resources.getIdentifier(name, "id", null)
    private fun drawableRes(name: String): Drawable? {
        val id = getContext().resources.getIdentifier(name, "drawable", null)
        return if (id == 0) null else runCatching { getContext().getDrawable(id) }.getOrNull()
    }

    private fun build(attrs: AttributeSet?) {
        val reader = attrs?.let { ViewAttributes.reader(this, it) }
        val layout = reader?.resourceId("layout")?.takeIf { it != 0 }
            ?: getContext().resources.getIdentifier("abc_search_view", "layout", null)
        LayoutInflater.from(getContext()).inflate(layout, this, true)

        mSearchSrcTextView = findViewById(id("search_src_text"))
        mSearchEditFrame = findViewById(id("search_edit_frame"))
        mSearchPlate = findViewById(id("search_plate"))
        mSubmitArea = findViewById(id("submit_area"))
        mSearchButton = findViewById(id("search_button"))
        mGoButton = findViewById(id("search_go_btn"))
        mCloseButton = findViewById(id("search_close_btn"))
        mVoiceButton = findViewById(id("search_voice_btn"))
        mCollapsedIcon = findViewById(id("search_mag_icon"))
        mSearchSrcTextView.searchView = this

        // Widget.AppCompat.SearchView.ActionBar defaults when there is no XML (menus create it in code)
        val searchIcon = reader?.drawable("searchIcon") ?: drawableRes("abc_ic_search_api_material")
        mSearchButton.setImageDrawable(searchIcon)
        mCollapsedIcon.setImageDrawable(searchIcon)
        mGoButton.setImageDrawable(reader?.drawable("goIcon") ?: drawableRes("abc_ic_go_search_api_material"))
        mCloseButton.setImageDrawable(reader?.drawable("closeIcon") ?: drawableRes("abc_ic_clear_material"))
        mVoiceButton.setImageDrawable(reader?.drawable("voiceIcon") ?: drawableRes("abc_ic_voice_search_api_material"))
        mSearchPlate.setBackground(if (reader?.has("queryBackground") == true) reader.drawable("queryBackground") else null)
        mSubmitArea.setBackground(if (reader?.has("submitBackground") == true) reader.drawable("submitBackground") else null)
        mDefaultQueryHint = reader?.text("defaultQueryHint")
        mQueryHint = reader?.text("queryHint")
        reader?.int("imeOptions")?.let { mSearchSrcTextView.setImeOptions(it) }
        reader?.int("inputType")?.let { mSearchSrcTextView.setInputType(it) }
        reader?.dim("maxWidth")?.let { mMaxWidth = it }
        val iconifiedByDefault = reader?.bool("iconifiedByDefault") ?: true

        mSearchButton.setOnClickListener { onSearchClicked() }
        mCloseButton.setOnClickListener { onCloseClicked() }
        mGoButton.setOnClickListener { onSubmitQuery() }
        mSearchSrcTextView.setOnClickListener { forceSuggestionQuery() }
        mSearchSrcTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onTextChanged(s)
            override fun afterTextChanged(s: Editable?) {}
        })
        mSearchSrcTextView.setOnEditorActionListener { _, _, _ ->
            onSubmitQuery()
            true
        }
        mSearchSrcTextView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                onSubmitQuery()
                true
            } else false
        }
        mSearchSrcTextView.setOnFocusChangeListener { _, hasFocus ->
            mOnQueryTextFocusChangeListener?.onFocusChange(this, hasFocus)
        }
        built = true
        setIconifiedByDefault(iconifiedByDefault)
        updateQueryHint()
    }

    private fun forceSuggestionQuery() {}

    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
        if (!isFocusable()) return false
        if (!isIconified()) return mSearchSrcTextView.requestFocus(direction, previouslyFocusedRect)
        return super.requestFocus(direction, previouslyFocusedRect)
    }

    override fun clearFocus() {
        mSearchSrcTextView.clearFocus()
        super.clearFocus()
    }

    fun setImeOptions(imeOptions: Int) = mSearchSrcTextView.setImeOptions(imeOptions)
    fun getImeOptions(): Int = mSearchSrcTextView.getImeOptions()
    fun setInputType(inputType: Int) = mSearchSrcTextView.setInputType(inputType)
    fun getInputType(): Int = mSearchSrcTextView.getInputType()

    fun setOnQueryTextListener(listener: SearchViewPlatform.OnQueryTextListener?) {
        mQueryListener = listener
    }

    fun setOnCloseListener(listener: SearchViewPlatform.OnCloseListener?) {
        mCloseListener = listener
    }

    fun setOnQueryTextFocusChangeListener(listener: OnFocusChangeListener?) {
        mOnQueryTextFocusChangeListener = listener
    }

    fun setOnSearchClickListener(listener: OnClickListener?) {
        mOnSearchClickListener = listener
    }

    fun setOnSuggestionListener(listener: OnSuggestionListener?) {}

    fun getQuery(): CharSequence = mSearchSrcTextView.getText()

    fun setQuery(query: CharSequence?, submit: Boolean) {
        mSearchSrcTextView.setText(query)
        if (query != null) mSearchSrcTextView.setSelection(mSearchSrcTextView.length())
        if (submit && !TextUtils.isEmpty(query)) onSubmitQuery()
    }

    fun setIconifiedByDefault(iconified: Boolean) {
        if (mIconifiedByDefault == iconified && built && mIconified == iconified) return
        mIconifiedByDefault = iconified
        updateViewsVisibility(iconified)
        updateQueryHint()
    }

    fun isIconfiedByDefault(): Boolean = mIconifiedByDefault
    fun isIconifiedByDefault(): Boolean = mIconifiedByDefault

    fun setIconified(iconify: Boolean) {
        if (iconify) onCloseClicked() else onSearchClicked()
    }

    fun isIconified(): Boolean = mIconified

    fun setSubmitButtonEnabled(enabled: Boolean) {
        mSubmitButtonEnabled = enabled
        updateViewsVisibility(isIconified())
    }

    fun isSubmitButtonEnabled(): Boolean = mSubmitButtonEnabled
    fun setQueryRefinementEnabled(enable: Boolean) {}
    fun isQueryRefinementEnabled(): Boolean = false
    fun setSuggestionsAdapter(adapter: Any?) {}
    fun setMaxWidth(maxpixels: Int) {
        mMaxWidth = maxpixels
        requestLayout()
    }

    fun getMaxWidth(): Int = mMaxWidth

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isIconified()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        var widthSpec = widthMeasureSpec
        val mode = MeasureSpec.getMode(widthSpec)
        var width = MeasureSpec.getSize(widthSpec)
        when (mode) {
            MeasureSpec.AT_MOST -> if (mMaxWidth > 0) width = minOf(mMaxWidth, width)
            MeasureSpec.EXACTLY -> if (mMaxWidth > 0) width = minOf(mMaxWidth, width)
            MeasureSpec.UNSPECIFIED -> if (mMaxWidth > 0) width = mMaxWidth
        }
        widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        super.onMeasure(widthSpec, heightMeasureSpec)
    }

    private fun updateViewsVisibility(collapsed: Boolean) {
        mIconified = collapsed
        val visCollapsed = if (collapsed) VISIBLE else GONE
        val hasText = !TextUtils.isEmpty(mSearchSrcTextView.getText())
        mSearchButton.setVisibility(visCollapsed)
        updateSubmitButton(hasText)
        mSearchEditFrame.setVisibility(if (collapsed) GONE else VISIBLE)
        mCollapsedIcon.setVisibility(if (mCollapsedIcon.getDrawable() == null || mIconifiedByDefault) GONE else VISIBLE)
        updateCloseButton()
        mVoiceButton.setVisibility(GONE)
        updateSubmitArea()
    }

    private fun isSubmitAreaEnabled(): Boolean = mSubmitButtonEnabled && !isIconified()

    private fun updateSubmitButton(hasText: Boolean) {
        mGoButton.setVisibility(if (mSubmitButtonEnabled && isSubmitAreaEnabled() && hasFocus() && hasText) VISIBLE else GONE)
    }

    private fun updateSubmitArea() {
        mSubmitArea.setVisibility(if (isSubmitAreaEnabled() && mGoButton.getVisibility() == VISIBLE) VISIBLE else GONE)
    }

    private fun updateCloseButton() {
        val hasText = !TextUtils.isEmpty(mSearchSrcTextView.getText())
        val showClose = hasText || (mIconifiedByDefault && !mExpandedInActionView)
        mCloseButton.setVisibility(if (showClose) VISIBLE else GONE)
        mCloseButton.getDrawable()?.setState(if (hasText) ENABLED_STATE_SET else EMPTY_STATE_SET)
    }

    private fun updateQueryHint() {
        mSearchSrcTextView.setHint(queryHint ?: "")
    }

    private fun onTextChanged(newText: CharSequence?) {
        val text = mSearchSrcTextView.getText()
        val hasText = !TextUtils.isEmpty(text)
        updateSubmitButton(hasText)
        updateCloseButton()
        updateSubmitArea()
        if (mQueryListener != null && !TextUtils.equals(newText, mOldQueryText)) {
            mQueryListener?.onQueryTextChange(newText?.toString() ?: "")
        }
        mOldQueryText = newText?.toString()
    }

    private fun onSubmitQuery() {
        val query = mSearchSrcTextView.getText()
        if (TextUtils.getTrimmedLength(query) > 0) {
            mQueryListener?.onQueryTextSubmit(query.toString())
        }
    }

    private fun onCloseClicked() {
        val text = mSearchSrcTextView.getText()
        if (TextUtils.isEmpty(text)) {
            if (mIconifiedByDefault) {
                if (mCloseListener == null || !mCloseListener!!.onClose()) {
                    clearFocus()
                    updateViewsVisibility(true)
                }
            }
        } else {
            mSearchSrcTextView.setText("")
            mSearchSrcTextView.requestFocus()
        }
    }

    private fun onSearchClicked() {
        updateViewsVisibility(false)
        mSearchSrcTextView.requestFocus()
        mOnSearchClickListener?.onClick(this)
    }

    override fun onActionViewCollapsed() {
        setQuery("", false)
        clearFocus()
        updateViewsVisibility(true)
        mExpandedInActionView = false
    }

    override fun onActionViewExpanded() {
        if (mExpandedInActionView) return
        mExpandedInActionView = true
        setIconified(false)
    }

    /** The text field inside SearchView (search_src_text) */
    open class SearchAutoComplete : EditText {
        internal var searchView: SearchView? = null

        constructor(context: Context?) : super(context)
        constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
        constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

        open fun setThreshold(threshold: Int) {}
        open fun getThreshold(): Int = 0
        open fun setDropDownBackgroundResource(id: Int) {}
    }

    private companion object {
        val ENABLED_STATE_SET = intArrayOf(0x0101009e) // android.R.attr.state_enabled
        val EMPTY_STATE_SET = intArrayOf()
    }
}
