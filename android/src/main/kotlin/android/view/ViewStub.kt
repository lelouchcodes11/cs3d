package android.view

import android.content.Context
import android.util.AttributeSet

/**
 * Placeholder that replaces itself with the layout from [layoutResource].
 * [setVisibility] of VISIBLE or INVISIBLE inflates, matching AOSP.
 */
open class ViewStub : View {
    fun interface OnInflateListener {
        fun onInflate(stub: ViewStub, inflated: View)
    }

    private var inflatedId = NO_ID
    private var layoutResource = 0
    private var listener: OnInflateListener? = null
    private var inflatedView: View? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        readAttrs(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        readAttrs(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        readAttrs(attrs)
    }

    private fun readAttrs(attrs: AttributeSet?) {
        if (attrs == null) return
        val ns = "http://schemas.android.com/apk/res/android"
        val inflated = attrs.getAttributeResourceValue(ns, "inflatedId", NO_ID)
        if (inflated != NO_ID) inflatedId = inflated
        val layout = attrs.getAttributeResourceValue(ns, "layout", 0)
        if (layout != 0) layoutResource = layout
    }

    fun setInflatedId(id: Int) {
        inflatedId = id
    }

    fun getInflatedId(): Int = inflatedId

    fun setLayoutResource(layoutResource: Int) {
        this.layoutResource = layoutResource
    }

    fun getLayoutResource(): Int = layoutResource

    fun setOnInflateListener(listener: OnInflateListener?) {
        this.listener = listener
    }

    override fun setVisibility(visibility: Int) {
        if (inflatedView != null) {
            inflatedView?.setVisibility(visibility)
            return
        }
        if (visibility == VISIBLE || visibility == INVISIBLE) {
            val view = inflate()
            if (visibility == INVISIBLE) view.setVisibility(INVISIBLE)
        } else {
            super.setVisibility(visibility)
        }
    }

    open fun inflate(): View {
        inflatedView?.let { return it }
        val parent = getParent() as? ViewGroup
            ?: throw IllegalStateException("ViewStub must have a non-null ViewGroup viewParent")
        if (layoutResource == 0) throw IllegalArgumentException("ViewStub must have a valid layoutResource")
        val view = LayoutInflater.from(getContext()).inflate(layoutResource, parent, false)
        if (inflatedId != NO_ID) view.setId(inflatedId)
        val index = parent.indexOfChild(this)
        val params = getLayoutParams()
        parent.removeViewInLayout(this)
        if (params != null) parent.addView(view, index, params) else parent.addView(view, index)
        inflatedView = view
        listener?.onInflate(this, view)
        return view
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(0, 0)
    }
}
