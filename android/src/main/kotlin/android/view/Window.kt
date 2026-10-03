package android.view

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.widget.FrameLayout



/**
 * Window of an Activity or Dialog. Its attributes are observed by the Compose host that renders
 * the dialog (dim, gravity, size, background).
 */
open class Window(private val context: Context) {
    companion object {
        const val FEATURE_NO_TITLE = 1
        const val FEATURE_OPTIONS_PANEL = 0
        const val FEATURE_ACTION_BAR = 8
        const val FEATURE_ACTION_BAR_OVERLAY = 9
        const val FEATURE_CONTENT_TRANSITIONS = 12
        const val ID_ANDROID_CONTENT = 0x01020002
    }

    interface OnFrameMetricsAvailableListener {
        fun onFrameMetricsAvailable(window: Window?, frameMetrics: Any?, dropCountSinceLastInvocation: Int)
    }

    interface Callback {
        fun dispatchKeyEvent(event: KeyEvent): Boolean
    }

    private var attributes = WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /** Observed state for the renderer */
    var backgroundDrawableState by mutableStateOf<Drawable?>(null)
    var dimAmountState by mutableFloatStateOf(0.6f)
    var gravityState by mutableIntStateOf(0)
    var layoutWidthState by mutableIntStateOf(ViewGroup.LayoutParams.WRAP_CONTENT)
    var layoutHeightState by mutableIntStateOf(ViewGroup.LayoutParams.WRAP_CONTENT)
    var flagsState by mutableIntStateOf(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    private var callback: Callback? = null

    private val decorView: FrameLayout by lazy {
        FrameLayout(context).also { it.setId(ID_ANDROID_CONTENT) }
    }

    fun getContext(): Context = context
    open fun getDecorView(): View = decorView
    open fun peekDecorView(): View? = decorView
    open fun findViewById(id: Int): View? = decorView.findViewById(id)
    open fun <T : View> requireViewById(id: Int): T = decorView.findViewById<T?>(id) ?: throw IllegalArgumentException("ID does not reference a View inside this Window")
    open fun setContentView(view: View?) {
        decorView.removeAllViews()
        if (view != null) {
            (view.getParent() as? ViewGroup)?.removeView(view)
            decorView.addView(view)
        }
    }

    open fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        decorView.removeAllViews()
        if (view != null) {
            (view.getParent() as? ViewGroup)?.removeView(view)
            decorView.addView(view, params)
        }
    }

    open fun addContentView(view: View, params: ViewGroup.LayoutParams?) = decorView.addView(view, params)
    open fun getCurrentFocus(): View? = decorView.findFocus()
    open fun getAttributes(): WindowManager.LayoutParams = attributes
    open fun setAttributes(a: WindowManager.LayoutParams?) {
        if (a == null) return
        attributes = a
        layoutWidthState = a.width
        layoutHeightState = a.height
        gravityState = a.gravity
        dimAmountState = a.dimAmount
        flagsState = a.flags
    }

    open fun setLayout(width: Int, height: Int) {
        attributes.width = width
        attributes.height = height
        layoutWidthState = width
        layoutHeightState = height
    }

    open fun setGravity(gravity: Int) {
        attributes.gravity = gravity
        gravityState = gravity
    }

    open fun setDimAmount(amount: Float) {
        attributes.dimAmount = amount
        dimAmountState = amount
    }

    open fun addFlags(flags: Int) = setFlags(flags, flags)
    open fun clearFlags(flags: Int) = setFlags(0, flags)
    open fun setFlags(flags: Int, mask: Int) {
        attributes.flags = (attributes.flags and mask.inv()) or (flags and mask)
        flagsState = attributes.flags
    }

    open fun setBackgroundDrawable(drawable: Drawable?) {
        backgroundDrawableState = drawable
    }

    open fun setBackgroundDrawableResource(resId: Int) = setBackgroundDrawable(context.getDrawable(resId))
    open fun setStatusBarColor(color: Int) {}
    open fun getStatusBarColor(): Int = 0
    open fun setNavigationBarColor(color: Int) {}
    open fun getNavigationBarColor(): Int = 0
    open fun setNavigationBarContrastEnforced(enforce: Boolean) {}
    open fun setDecorFitsSystemWindows(decorFitsSystemWindows: Boolean) {}
    open fun setSoftInputMode(mode: Int) {
        attributes.softInputMode = mode
    }

    open fun setWindowAnimations(resId: Int) {}
    open fun requestFeature(featureId: Int): Boolean = true
    open fun setFormat(format: Int) {}
    open fun setType(type: Int) {}
    open fun setElevation(elevation: Float) {}
    open fun setSustainedPerformanceMode(enable: Boolean) {}
    open fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    open fun getCallback(): Callback? = callback
    open fun getWindowManager(): WindowManager? = null
    open fun getInsetsController(): WindowInsetsController? = WindowInsetsController()
    open fun takeKeyEvents(get: Boolean) {}
    open fun setTitle(title: CharSequence?) {}
    open fun isFloating(): Boolean = true
    open fun getLayoutInflater(): LayoutInflater = LayoutInflater.from(context)
}

open class WindowInsetsController {
    companion object {
        const val BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2
        const val APPEARANCE_LIGHT_STATUS_BARS = 1 shl 3
    }

    open fun hide(types: Int) {}
    open fun show(types: Int) {}
    open fun setSystemBarsBehavior(behavior: Int) {}
    open fun setSystemBarsAppearance(appearance: Int, mask: Int) {}
}
