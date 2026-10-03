package android.view

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources

open class ContextThemeWrapper : ContextWrapper {
    private var mThemeResource = 0
    private var mTheme: Resources.Theme? = null
    private var mInflater: LayoutInflater? = null
    private var mOverrideConfiguration: Configuration? = null

    constructor() : super(null)
    constructor(base: Context?, themeResId: Int) : super(base) {
        mThemeResource = themeResId
    }

    constructor(base: Context?, theme: Resources.Theme?) : super(base) {
        mTheme = theme
    }

    open fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        mOverrideConfiguration = overrideConfiguration
    }

    override fun setTheme(resid: Int) {
        if (mThemeResource != resid) {
            mThemeResource = resid
            mTheme?.applyStyle(resid, true)
        }
    }

    open fun getThemeResId(): Int = mThemeResource

    override fun getTheme(): Resources.Theme {
        val t = mTheme
        if (t != null) return t
        val theme = resources.newTheme()
        val base = baseContext?.theme
        if (base != null) theme.setTo(base)
        if (mThemeResource != 0) theme.applyStyle(mThemeResource, true)
        mTheme = theme
        return theme
    }

    override fun getSystemService(name: String): Any? {
        if (Context.LAYOUT_INFLATER_SERVICE == name) {
            return mInflater ?: LayoutInflater.from(baseContext).cloneInContext(this).also { mInflater = it }
        }
        return baseContext.getSystemService(name)
    }
}
