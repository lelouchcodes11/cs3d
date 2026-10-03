package androidx.lifecycle

import android.content.ContextWrapper
import android.view.View

fun View.findViewTreeLifecycleOwner(): LifecycleOwner? {
    var current: View? = this
    while (current != null) {
        val tagOwner = current.getTag(0x7f0a0001) as? LifecycleOwner
        if (tagOwner != null) return tagOwner
        val parent = current.getParent()
        current = parent as? View
    }
    var ctx = getContext()
    while (ctx is ContextWrapper) {
        if (ctx is LifecycleOwner) return ctx
        ctx = ctx.getBaseContext()
    }
    if (ctx is LifecycleOwner) return ctx
    return null
}

fun View.setViewTreeLifecycleOwner(lifecycleOwner: LifecycleOwner?) {
    setTag(0x7f0a0001, lifecycleOwner)
}
