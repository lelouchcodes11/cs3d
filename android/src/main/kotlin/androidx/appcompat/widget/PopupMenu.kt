package androidx.appcompat.widget

import android.content.Context
import android.view.Gravity
import android.view.View

open class PopupMenu : android.widget.PopupMenu {
    constructor(context: Context, anchor: View) : super(context, anchor)
    constructor(context: Context, anchor: View, gravity: Int) : super(context, anchor, gravity)
    constructor(context: Context, anchor: View, gravity: Int, popupStyleAttr: Int, popupStyleRes: Int) : super(context, anchor, gravity)
}
