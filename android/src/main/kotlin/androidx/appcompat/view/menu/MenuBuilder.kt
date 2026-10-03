package androidx.appcompat.view.menu

import android.content.Context
import android.view.MenuImpl
import com.lagradost.desktop.runtime.AndroidRuntime

open class MenuBuilder(context: Context) : MenuImpl(context) {
    constructor() : this(AndroidRuntime.applicationContext ?: android.content.ContextWrapper(null))

    open fun setOptionalIconsVisible(visible: Boolean) {}
}
