@file:JvmName("MaterialKt")

package com.google.android.material.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Dialog rendered as a sheet at the bottom of the window */
open class BottomSheetDialog : AppCompatDialog {
    private val mBehavior = BottomSheetBehavior<FrameLayout>()
    private val sheet: FrameLayout by lazy {
        FrameLayout(getContext()).also {
            it.setId(it.getContext().getResources().getIdentifier("design_bottom_sheet", "id", null))
            it.setLayoutParams(android.view.ViewGroup.LayoutParams(-1, -2))
            mBehavior.attach(it)
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, theme: Int) : super(context, theme)
    protected constructor(context: Context, cancelable: Boolean, cancelListener: android.content.DialogInterface.OnCancelListener?) : super(context) {
        setCancelable(cancelable)
        setOnCancelListener(cancelListener)
    }

    override fun setContentView(view: View) {
        sheet.removeAllViews()
        (view.getParent() as? android.view.ViewGroup)?.removeView(view)
        sheet.addView(view)
        super.setContentView(sheet)
    }

    override fun setContentView(view: View, params: android.view.ViewGroup.LayoutParams?) {
        sheet.removeAllViews()
        (view.getParent() as? android.view.ViewGroup)?.removeView(view)
        sheet.addView(view, params)
        super.setContentView(sheet)
    }

    override fun setContentView(layoutResID: Int) = setContentView(getLayoutInflater().inflate(layoutResID, sheet, false))

    open fun getBehavior(): BottomSheetBehavior<FrameLayout> = mBehavior
    open fun setDismissWithAnimation(dismissWithAnimation: Boolean) {}
    open fun getDismissWithAnimation(): Boolean = false
    open fun getEdgeToEdgeEnabled(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

open class BottomSheetDialogFragment : androidx.appcompat.app.AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog = BottomSheetDialog(requireContext(), getTheme())
    override fun dismiss() = super.dismiss()
    override fun dismissAllowingStateLoss() = super.dismissAllowingStateLoss()
}

open class BottomSheetBehavior<V : View> {
    companion object {
        const val STATE_DRAGGING = 1
        const val STATE_SETTLING = 2
        const val STATE_EXPANDED = 3
        const val STATE_COLLAPSED = 4
        const val STATE_HIDDEN = 5
        const val STATE_HALF_EXPANDED = 6
        const val PEEK_HEIGHT_AUTO = -1
        const val SAVE_ALL = -1

        private val behaviors = java.util.WeakHashMap<View, BottomSheetBehavior<*>>()

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <V : View> from(view: V): BottomSheetBehavior<V> =
            synchronized(behaviors) { behaviors.getOrPut(view) { BottomSheetBehavior<V>() } } as BottomSheetBehavior<V>
    }

    abstract class BottomSheetCallback {
        abstract fun onStateChanged(bottomSheet: View, newState: Int)
        abstract fun onSlide(bottomSheet: View, slideOffset: Float)
    }

    private var view: View? = null
    private val callbacks = ArrayList<BottomSheetCallback>()

    /** Observed by the renderer */
    var stateValue by mutableIntStateOf(STATE_COLLAPSED)
    var peekHeightValue by mutableIntStateOf(PEEK_HEIGHT_AUTO)
    var skipCollapsedValue by mutableStateOf(false)
    var draggableValue by mutableStateOf(true)
    var hideableValue by mutableStateOf(true)

    internal fun attach(v: View) {
        view = v
        synchronized(behaviors) { behaviors[v] = this }
    }

    open fun getState(): Int = stateValue
    open fun setState(state: Int) {
        if (stateValue == state) return
        stateValue = state
        val v = view
        if (v != null) callbacks.toList().forEach { it.onStateChanged(v, state) }
    }

    open fun getPeekHeight(): Int = peekHeightValue
    open fun setPeekHeight(peekHeight: Int) {
        peekHeightValue = peekHeight
    }

    open fun setPeekHeight(peekHeight: Int, animate: Boolean) = setPeekHeight(peekHeight)
    open fun getSkipCollapsed(): Boolean = skipCollapsedValue
    open fun setSkipCollapsed(skipCollapsed: Boolean) {
        skipCollapsedValue = skipCollapsed
    }

    open fun isHideable(): Boolean = hideableValue
    open fun setHideable(hideable: Boolean) {
        hideableValue = hideable
    }

    open fun isDraggable(): Boolean = draggableValue
    open fun setDraggable(draggable: Boolean) {
        draggableValue = draggable
    }

    open fun setFitToContents(fitToContents: Boolean) {}
    open fun setHalfExpandedRatio(ratio: Float) {}
    open fun setExpandedOffset(offset: Int) {}
    open fun setMaxHeight(maxHeight: Int) {}
    open fun setMaxWidth(maxWidth: Int) {}
    open fun setSaveFlags(flags: Int) {}
    open fun setGestureInsetBottomIgnored(gestureInsetBottomIgnored: Boolean) {}
    open fun addBottomSheetCallback(callback: BottomSheetCallback) {
        callbacks.add(callback)
    }

    open fun removeBottomSheetCallback(callback: BottomSheetCallback) {
        callbacks.remove(callback)
    }

    @Deprecated("")
    open fun setBottomSheetCallback(callback: BottomSheetCallback?) {
        callbacks.clear()
        if (callback != null) callbacks.add(callback)
    }
}
