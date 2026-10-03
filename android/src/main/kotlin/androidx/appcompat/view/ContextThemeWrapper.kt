package androidx.appcompat.view

open class ContextThemeWrapper : android.view.ContextThemeWrapper {
    constructor() : super()
    constructor(base: android.content.Context?, themeResId: Int) : super(base, themeResId)
    constructor(base: android.content.Context?, theme: android.content.res.Resources.Theme?) : super(base, theme)
}
