@file:JvmName("MaterialMisc7Kt")

package com.google.android.material.snackbar

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import com.lagradost.desktop.runtime.AndroidRuntime

/**
 * Material's transient bar at the bottom of the window. Shown by the desktop UI host; same public
 * API (and erased JVM signatures) as com.google.android.material.snackbar.BaseTransientBottomBar.
 */
abstract class BaseTransientBottomBar<B : BaseTransientBottomBar<B>> protected constructor(
    private val targetView: View?,
    private val ctx: Context,
    private var durationValue: Int,
) {
    companion object {
        const val LENGTH_INDEFINITE = -2
        const val LENGTH_SHORT = -1
        const val LENGTH_LONG = 0
        const val ANIMATION_MODE_SLIDE = 0
        const val ANIMATION_MODE_FADE = 1
    }

    abstract class BaseCallback<B> {
        companion object {
            const val DISMISS_EVENT_SWIPE = 0
            const val DISMISS_EVENT_ACTION = 1
            const val DISMISS_EVENT_TIMEOUT = 2
            const val DISMISS_EVENT_MANUAL = 3
            const val DISMISS_EVENT_CONSECUTIVE = 4
        }

        open fun onDismissed(transientBottomBar: B, event: Int) {}
        open fun onShown(transientBottomBar: B) {}
    }

    private val callbacks = ArrayList<BaseCallback<B>>()
    private var anchor: View? = null
    private var animationMode = ANIMATION_MODE_SLIDE

    @Volatile
    private var shown = false

    @Suppress("UNCHECKED_CAST")
    private fun self(): B = this as B

    open fun getDuration(): Int = durationValue
    open fun setDuration(duration: Int): B = self().also { durationValue = duration }
    open fun getContext(): Context = ctx
    open fun getView(): View? = targetView
    open fun getAnchorView(): View? = anchor
    open fun setAnchorView(anchorView: View?): B = self().also { anchor = anchorView }
    open fun setAnchorView(anchorViewId: Int): B = self().also { anchor = targetView?.getRootView()?.findViewById<View>(anchorViewId) }
    open fun getAnimationMode(): Int = animationMode
    open fun setAnimationMode(mode: Int): B = self().also { animationMode = mode }
    open fun addCallback(callback: BaseCallback<B>?): B = self().also { if (callback != null) callbacks.add(callback) }
    open fun removeCallback(callback: BaseCallback<B>?): B = self().also { callbacks.remove(callback) }

    open fun show() {
        AndroidRuntime.host.showSnackbar(this)
    }

    open fun dismiss() {
        dispatchDismiss(BaseCallback.DISMISS_EVENT_MANUAL)
    }

    open fun isShown(): Boolean = shown
    open fun isShownOrQueued(): Boolean = shown

    /** Called by the UI host once the bar is visible */
    fun dispatchShown() {
        shown = true
        for (c in callbacks.toList()) c.onShown(self())
    }

    /** Hides the bar (if shown) and notifies the callbacks with the reason */
    fun dispatchDismiss(event: Int) {
        AndroidRuntime.host.dismissSnackbar(this)
        if (!shown) return
        shown = false
        for (c in callbacks.toList()) c.onDismissed(self(), event)
    }
}

open class Snackbar private constructor(view: View?, context: Context, text: CharSequence?, duration: Int) :
    BaseTransientBottomBar<Snackbar>(view, context, duration) {

    companion object {
        const val LENGTH_INDEFINITE = BaseTransientBottomBar.LENGTH_INDEFINITE
        const val LENGTH_SHORT = BaseTransientBottomBar.LENGTH_SHORT
        const val LENGTH_LONG = BaseTransientBottomBar.LENGTH_LONG

        // The view is only used to find the window on Android; a null view still shows the bar here
        @JvmStatic
        fun make(view: View?, text: CharSequence, duration: Int): Snackbar =
            Snackbar(view, view?.getContext() ?: AndroidRuntime.context, text, duration)

        @JvmStatic
        fun make(view: View?, resId: Int, duration: Int): Snackbar {
            val context = view?.getContext() ?: AndroidRuntime.context
            return Snackbar(view, context, context.getText(resId), duration)
        }

        @JvmStatic
        fun make(context: Context, view: View?, text: CharSequence, duration: Int): Snackbar =
            Snackbar(view, context, text, duration)
    }

    open class Callback : BaseCallback<Snackbar>() {
        companion object {
            const val DISMISS_EVENT_SWIPE = BaseCallback.DISMISS_EVENT_SWIPE
            const val DISMISS_EVENT_ACTION = BaseCallback.DISMISS_EVENT_ACTION
            const val DISMISS_EVENT_TIMEOUT = BaseCallback.DISMISS_EVENT_TIMEOUT
            const val DISMISS_EVENT_MANUAL = BaseCallback.DISMISS_EVENT_MANUAL
            const val DISMISS_EVENT_CONSECUTIVE = BaseCallback.DISMISS_EVENT_CONSECUTIVE
        }

        override fun onShown(sb: Snackbar) {}
        override fun onDismissed(transientBottomBar: Snackbar, event: Int) {}
    }

    private var message: CharSequence? = text
    private var action: CharSequence? = null
    private var actionListener: View.OnClickListener? = null
    private var textMaxLines = 2

    /** ARGB colors set by the app, null means the theme default */
    var textColorOverride: Int? = null
        private set
    var actionTextColorOverride: Int? = null
        private set
    var backgroundTintOverride: Int? = null
        private set

    fun getText(): CharSequence? = message
    fun getActionText(): CharSequence? = action
    fun getTextMaxLines(): Int = textMaxLines

    open fun setText(text: CharSequence): Snackbar = apply { message = text }
    open fun setText(resId: Int): Snackbar = apply { message = getContext().getText(resId) }

    open fun setAction(text: CharSequence?, listener: View.OnClickListener?): Snackbar = apply {
        if (text.isNullOrEmpty() || listener == null) {
            action = null
            actionListener = null
        } else {
            action = text
            actionListener = listener
        }
    }

    open fun setAction(resId: Int, listener: View.OnClickListener?): Snackbar =
        setAction(getContext().getText(resId), listener)

    open fun setActionTextColor(color: Int): Snackbar = apply { actionTextColorOverride = color }
    open fun setActionTextColor(colors: ColorStateList?): Snackbar = apply { actionTextColorOverride = colors?.defaultColor }
    open fun setTextColor(color: Int): Snackbar = apply { textColorOverride = color }
    open fun setTextColor(colors: ColorStateList?): Snackbar = apply { textColorOverride = colors?.defaultColor }
    open fun setTextMaxLines(maxLines: Int): Snackbar = apply { textMaxLines = maxLines }
    open fun setBackgroundTint(color: Int): Snackbar = apply { backgroundTintOverride = color }
    open fun setBackgroundTintList(colorStateList: ColorStateList?): Snackbar =
        apply { backgroundTintOverride = colorStateList?.defaultColor }

    @Deprecated("Use addCallback")
    open fun setCallback(callback: Callback?): Snackbar = apply { if (callback != null) addCallback(callback) }

    /** Called by the UI host when the action button is clicked */
    fun performAction() {
        val listener = actionListener ?: return
        listener.onClick(getView() ?: return)
        dispatchDismiss(BaseCallback.DISMISS_EVENT_ACTION)
    }
}
