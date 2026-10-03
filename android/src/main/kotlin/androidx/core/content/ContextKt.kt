@file:JvmName("ContextKt")

package androidx.core.content

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes

inline fun <reified T : Any> Context.getSystemService(): T? = ContextCompat.getSystemService(this, T::class.java)

inline fun Context.withStyledAttributes(
    set: AttributeSet? = null,
    attrs: IntArray,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    block: TypedArray.() -> Unit
) {
    val typedArray = obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes)
    try {
        typedArray.block()
    } finally {
        typedArray.recycle()
    }
}

inline fun Context.withStyledAttributes(
    @StyleRes resourceId: Int,
    attrs: IntArray,
    block: TypedArray.() -> Unit
) {
    val typedArray = obtainStyledAttributes(resourceId, attrs)
    try {
        typedArray.block()
    } finally {
        typedArray.recycle()
    }
}
