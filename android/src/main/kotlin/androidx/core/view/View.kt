@file:JvmName("ViewKt")

package androidx.core.view

import android.view.View
import android.view.ViewGroup

inline var View.isVisible: Boolean
    get() = getVisibility() == View.VISIBLE
    set(value) {
        setVisibility(if (value) View.VISIBLE else View.GONE)
    }

inline var View.isInvisible: Boolean
    get() = getVisibility() == View.INVISIBLE
    set(value) {
        setVisibility(if (value) View.INVISIBLE else View.VISIBLE)
    }

inline var View.isGone: Boolean
    get() = getVisibility() == View.GONE
    set(value) {
        setVisibility(if (value) View.GONE else View.VISIBLE)
    }

inline fun View.doOnAttach(crossinline action: (view: View) -> Unit) {
    if (isAttachedToWindow()) {
        action(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                removeOnAttachStateChangeListener(this)
                action(view)
            }

            override fun onViewDetachedFromWindow(view: View) {}
        })
    }
}

inline fun View.doOnDetach(crossinline action: (view: View) -> Unit) {
    if (!isAttachedToWindow()) {
        action(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}

            override fun onViewDetachedFromWindow(view: View) {
                removeOnAttachStateChangeListener(this)
                action(view)
            }
        })
    }
}

inline fun View.doOnLayout(crossinline action: (view: View) -> Unit) {
    post { action(this) }
}

inline fun View.doOnPreDraw(crossinline action: (view: View) -> Unit) {
    post { action(this) }
}

inline fun View.updateLayoutParams(block: ViewGroup.LayoutParams.() -> Unit) {
    val params = getLayoutParams()
    block(params)
    setLayoutParams(params)
}

@JvmName("updateLayoutParamsTyped")
inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParams(block: T.() -> Unit) {
    val params = getLayoutParams() as T
    block(params)
    setLayoutParams(params)
}

fun View.updatePadding(
    left: Int = getPaddingLeft(),
    top: Int = getPaddingTop(),
    right: Int = getPaddingRight(),
    bottom: Int = getPaddingBottom()
) {
    setPadding(left, top, right, bottom)
}

fun View.setPadding(size: Int) {
    setPadding(size, size, size, size)
}

fun ViewGroup.isNotEmpty(): Boolean = getChildCount() > 0

inline var View.marginStart: Int
    get() = (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.getMarginStart() ?: 0
    set(value) { (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.setMarginStart(value) }

inline var View.marginEnd: Int
    get() = (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.getMarginEnd() ?: 0
    set(value) { (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.setMarginEnd(value) }

inline val View.marginLeft: Int
    get() = (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0

inline val View.marginRight: Int
    get() = (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0

inline var View.marginTop: Int
    get() = (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
    set(value) { (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.topMargin = value }

inline var View.marginBottom: Int
    get() = (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    set(value) { (getLayoutParams() as? ViewGroup.MarginLayoutParams)?.bottomMargin = value }

inline var View.paddingStart: Int
    get() = getPaddingStart()
    set(value) { setPaddingRelative(value, getPaddingTop(), getPaddingEnd(), getPaddingBottom()) }

inline var View.paddingEnd: Int
    get() = getPaddingEnd()
    set(value) { setPaddingRelative(getPaddingStart(), getPaddingTop(), value, getPaddingBottom()) }

val ViewGroup.children: Sequence<View>
    get() = object : Sequence<View> {
        override fun iterator() = object : MutableIterator<View> {
            private var index = 0
            override fun hasNext() = index < getChildCount()
            override fun next() = getChildAt(index++) ?: throw IndexOutOfBoundsException()
            override fun remove() = removeViewAt(--index)
        }
    }

operator fun ViewGroup.get(index: Int): View =
    getChildAt(index) ?: throw IndexOutOfBoundsException("Index: $index, Size: ${getChildCount()}")

inline fun ViewGroup.forEach(action: (view: View) -> Unit) {
    for (index in 0 until getChildCount()) {
        action(getChildAt(index)!!)
    }
}

val View.allViews: Sequence<View>
    get() = sequence {
        yield(this@allViews)
        if (this@allViews is ViewGroup) {
            yieldAll(this@allViews.descendants)
        }
    }

val ViewGroup.descendants: Sequence<View>
    get() = sequence {
        forEach { child ->
            yield(child)
            if (child is ViewGroup) {
                yieldAll(child.descendants)
            }
        }
    }

