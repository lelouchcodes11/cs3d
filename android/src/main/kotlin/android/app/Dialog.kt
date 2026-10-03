package android.app

import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.lagradost.desktop.runtime.AndroidRuntime

/**
 * android.app.Dialog. Showing the dialog hands it to the desktop UI host which renders the window
 * content (decor view) with Compose.
 */
open class Dialog : DialogInterface, Window.Callback, LifecycleOwner {
    private val mContext: Context
    private val mWindow: Window
    private var mCreated = false
    private var mCancelable = true
    private var mCanceledOnTouchOutside = true
    private var mOnCancelListener: DialogInterface.OnCancelListener? = null
    private var mOnDismissListener: DialogInterface.OnDismissListener? = null
    private var mOnShowListener: DialogInterface.OnShowListener? = null
    private var mOnKeyListener: DialogInterface.OnKeyListener? = null
    private var mOwnerActivity: Activity? = null
    private var mTitle by mutableStateOf<CharSequence?>(null)
    private var mShowing by mutableStateOf(false)
    private val mHandler = Handler(Looper.getMainLooper())
    private val mLifecycle = LifecycleRegistry.createUnsafe(this)

    constructor(context: Context) : this(context, 0)
    constructor(context: Context, themeResId: Int) {
        mContext = if (themeResId != 0) android.view.ContextThemeWrapper(context, themeResId) else context
        mWindow = Window(mContext)
        mWindow.setCallback(this)
        var c: Context? = context
        while (c != null) {
            if (c is Activity) {
                mOwnerActivity = c
                break
            }
            c = (c as? ContextWrapper)?.baseContext
        }
        mLifecycle.currentState = Lifecycle.State.CREATED
    }

    protected constructor(context: Context, cancelable: Boolean, cancelListener: DialogInterface.OnCancelListener?) : this(context) {
        mCancelable = cancelable
        mOnCancelListener = cancelListener
    }

    override val lifecycle: Lifecycle get() = mLifecycle

    fun getContext(): Context = mContext
    fun getOwnerActivity(): Activity? = mOwnerActivity
    fun setOwnerActivity(activity: Activity?) {
        mOwnerActivity = activity
    }

    open fun getWindow(): Window = mWindow
    open fun getLayoutInflater(): LayoutInflater = LayoutInflater.from(mContext)
    open fun getCurrentFocus(): View? = mWindow.getCurrentFocus()
    open fun <T : View?> findViewById(id: Int): T = mWindow.findViewById(id) as T
    open fun <T : View> requireViewById(id: Int): T = findViewById<T?>(id) ?: throw IllegalArgumentException("ID does not reference a View inside this Dialog")
    open fun setContentView(layoutResID: Int) = mWindow.setContentView(getLayoutInflater().inflate(layoutResID, null))
    open fun setContentView(view: View) = mWindow.setContentView(view)
    open fun setContentView(view: View, params: ViewGroup.LayoutParams?) = mWindow.setContentView(view, params)
    open fun addContentView(view: View, params: ViewGroup.LayoutParams?) = mWindow.addContentView(view, params)
    open fun setTitle(title: CharSequence?) {
        mTitle = title
    }

    open fun setTitle(titleId: Int) = setTitle(mContext.getText(titleId))
    fun getTitle(): CharSequence? = mTitle
    fun requestWindowFeature(featureId: Int): Boolean = mWindow.requestFeature(featureId)
    fun setFeatureDrawableResource(featureId: Int, resId: Int) {}

    open fun isShowing(): Boolean = mShowing
    open fun create() {
        if (!mCreated) dispatchOnCreate(null)
    }

    private fun dispatchOnCreate(savedInstanceState: Bundle?) {
        if (!mCreated) {
            onCreate(savedInstanceState)
            mCreated = true
        }
    }

    open fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mHandler.post { show() }
            return
        }
        if (mShowing) return
        dispatchOnCreate(null)
        onStart()
        mLifecycle.currentState = Lifecycle.State.RESUMED
        mShowing = true
        AndroidRuntime.host.showDialog(this)
        mOnShowListener?.onShow(this)
    }

    open fun hide() {
        if (mShowing) {
            mShowing = false
            AndroidRuntime.host.dismissDialog(this)
        }
    }

    override fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mHandler.post { dismiss() }
            return
        }
        if (!mShowing) return
        mShowing = false
        AndroidRuntime.host.dismissDialog(this)
        onStop()
        mLifecycle.currentState = Lifecycle.State.CREATED
        mOnDismissListener?.onDismiss(this)
    }

    override fun cancel() {
        mOnCancelListener?.onCancel(this)
        dismiss()
    }

    protected open fun onCreate(savedInstanceState: Bundle?) {}
    protected open fun onStart() {}
    protected open fun onStop() {}
    open fun onAttachedToWindow() {}
    open fun onDetachedFromWindow() {}
    open fun onSaveInstanceState(): Bundle = Bundle()
    open fun onRestoreInstanceState(savedInstanceState: Bundle) {}
    open fun onContentChanged() {}
    open fun onWindowFocusChanged(hasFocus: Boolean) {}

    open fun setCancelable(flag: Boolean) {
        mCancelable = flag
    }

    fun isCancelable(): Boolean = mCancelable
    open fun setCanceledOnTouchOutside(cancel: Boolean) {
        if (cancel && !mCancelable) mCancelable = true
        mCanceledOnTouchOutside = cancel
    }

    fun isCanceledOnTouchOutside(): Boolean = mCanceledOnTouchOutside
    open fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) {
        mOnCancelListener = listener
    }

    open fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        mOnDismissListener = listener
    }

    open fun setOnShowListener(listener: DialogInterface.OnShowListener?) {
        mOnShowListener = listener
    }

    open fun setOnKeyListener(onKeyListener: DialogInterface.OnKeyListener?) {
        mOnKeyListener = onKeyListener
    }

    open fun setCancelMessage(msg: android.os.Message?) {}
    open fun setDismissMessage(msg: android.os.Message?) {}
    open fun takeKeyEvents(get: Boolean) {}
    open fun closeOptionsMenu() {}
    open fun openOptionsMenu() {}
    open fun setVolumeControlStream(streamType: Int) {}

    /** Called by the host when the user presses back/escape */
    open fun onBackPressed() {
        if (mCancelable) cancel()
    }

    /** Called by the host when the user clicks outside the dialog */
    fun onTouchOutside() {
        if (mCancelable && mCanceledOnTouchOutside) cancel()
    }

    open fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) return true
        return false
    }

    open fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) && !event.isCanceled) {
            onBackPressed()
            return true
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mOnKeyListener?.onKey(this, event.keyCode, event) == true) return true
        if (mWindow.getDecorView().dispatchKeyEvent(event)) return true
        return if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event) else onKeyUp(event.keyCode, event)
    }

    open fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}

open class ProgressDialog(context: Context) : AlertDialog(context) {
    companion object {
        const val STYLE_SPINNER = 0
        const val STYLE_HORIZONTAL = 1

        @JvmStatic
        fun show(context: Context, title: CharSequence?, message: CharSequence?): ProgressDialog = show(context, title, message, false)

        @JvmStatic
        fun show(context: Context, title: CharSequence?, message: CharSequence?, indeterminate: Boolean): ProgressDialog =
            ProgressDialog(context).also {
                it.setTitle(title)
                it.setMessage(message)
                it.show()
            }
    }

    open fun setProgressStyle(style: Int) {}
    open fun setIndeterminate(indeterminate: Boolean) {}
    open fun setMax(max: Int) {}
    open fun setProgress(value: Int) {}
}
