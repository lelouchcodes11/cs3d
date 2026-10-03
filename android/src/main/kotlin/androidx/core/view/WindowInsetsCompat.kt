package androidx.core.view

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import androidx.core.graphics.Insets
import java.util.Collections
import java.util.WeakHashMap

// desktop: a window has no system bars, cutouts or on-screen keyboard, so every inset is zero

class WindowInsetsCompat private constructor() {
    fun getInsets(typeMask: Int): Insets = Insets.NONE
    fun getInsetsIgnoringVisibility(typeMask: Int): Insets = Insets.NONE
    fun isVisible(typeMask: Int): Boolean = false
    val displayCutout: DisplayCutoutCompat? get() = null
    val systemWindowInsetLeft: Int get() = 0
    val systemWindowInsetTop: Int get() = 0
    val systemWindowInsetRight: Int get() = 0
    val systemWindowInsetBottom: Int get() = 0
    fun hasInsets(): Boolean = false
    val isConsumed: Boolean get() = this === CONSUMED
    fun consumeSystemWindowInsets(): WindowInsetsCompat = CONSUMED

    object Type {
        @JvmStatic fun statusBars(): Int = 1
        @JvmStatic fun navigationBars(): Int = 1 shl 1
        @JvmStatic fun captionBar(): Int = 1 shl 2
        @JvmStatic fun ime(): Int = 1 shl 3
        @JvmStatic fun systemGestures(): Int = 1 shl 4
        @JvmStatic fun mandatorySystemGestures(): Int = 1 shl 5
        @JvmStatic fun tappableElement(): Int = 1 shl 6
        @JvmStatic fun displayCutout(): Int = 1 shl 7
        @JvmStatic fun systemBars(): Int = statusBars() or navigationBars() or captionBar()
    }

    companion object {
        @JvmField
        val CONSUMED = WindowInsetsCompat()

        @JvmStatic
        internal fun empty(): WindowInsetsCompat = WindowInsetsCompat()
    }
}

class DisplayCutoutCompat {
    val safeInsetLeft: Int get() = 0
    val safeInsetTop: Int get() = 0
    val safeInsetRight: Int get() = 0
    val safeInsetBottom: Int get() = 0
    val boundingRects: List<android.graphics.Rect> get() = emptyList()
}

fun interface OnApplyWindowInsetsListener {
    fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat
}

object ViewCompat {
    private val insetListeners: MutableMap<View, OnApplyWindowInsetsListener> = Collections.synchronizedMap(WeakHashMap())

    /** Android dispatches insets on the next traversal, so the listener runs posted */
    @JvmStatic
    fun setOnApplyWindowInsetsListener(v: View, listener: OnApplyWindowInsetsListener?) {
        if (listener == null) {
            insetListeners.remove(v)
            return
        }
        insetListeners[v] = listener
        requestApplyInsets(v)
    }

    @JvmStatic
    fun requestApplyInsets(view: View) {
        Handler(Looper.getMainLooper()).post { dispatchApplyWindowInsets(view, WindowInsetsCompat.empty()) }
    }

    @JvmStatic
    fun dispatchApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat =
        insetListeners[view]?.onApplyWindowInsets(view, insets) ?: insets

    @JvmStatic
    fun getRootWindowInsets(view: View): WindowInsetsCompat? = WindowInsetsCompat.empty()

    @JvmStatic
    fun getWindowInsetsController(view: View): WindowInsetsControllerCompat? = WindowInsetsControllerCompat(null, view)
}

class WindowInsetsControllerCompat(private val window: Window?, private val view: View) {
    var systemBarsBehavior: Int = BEHAVIOR_DEFAULT

    fun hide(types: Int) {}
    fun show(types: Int) {}
    var isAppearanceLightStatusBars: Boolean = false
    var isAppearanceLightNavigationBars: Boolean = false

    companion object {
        const val BEHAVIOR_DEFAULT = 1
        const val BEHAVIOR_SHOW_BARS_BY_TOUCH = 0
        const val BEHAVIOR_SHOW_BARS_BY_SWIPE = 1
        const val BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2
    }
}

object WindowCompat {
    const val FEATURE_ACTION_BAR = 8
    const val FEATURE_ACTION_BAR_OVERLAY = 9
    const val FEATURE_ACTION_MODE_OVERLAY = 10

    @JvmStatic
    fun getInsetsController(window: Window, view: View): WindowInsetsControllerCompat = WindowInsetsControllerCompat(window, view)

    @JvmStatic
    fun setDecorFitsSystemWindows(window: Window, decorFitsSystemWindows: Boolean) {}

    @JvmStatic
    fun enableEdgeToEdge(window: Window) {}
}
