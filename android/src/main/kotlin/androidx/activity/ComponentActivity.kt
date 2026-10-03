package androidx.activity

import android.app.Activity
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

open class ComponentActivity : Activity(), LifecycleOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner,
    androidx.activity.result.ActivityResultCaller, androidx.activity.result.ActivityResultRegistryOwner {
    private val mActivityResultRegistry = androidx.activity.result.ActivityResultRegistry { this }
    override val activityResultRegistry: androidx.activity.result.ActivityResultRegistry get() = mActivityResultRegistry

    override fun <I, O> registerForActivityResult(
        contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
        callback: androidx.activity.result.ActivityResultCallback<O>
    ): androidx.activity.result.ActivityResultLauncher<I> =
        mActivityResultRegistry.register("activity_rq#" + System.identityHashCode(callback), contract, callback)

    private val mLifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val mViewModelStore = ViewModelStore()
    private val mOnBackPressedDispatcher = OnBackPressedDispatcher { super.onBackPressed() }

    override val lifecycle: Lifecycle get() = mLifecycleRegistry
    override val viewModelStore: ViewModelStore get() = mViewModelStore
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = mOnBackPressedDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * Desktop (native UI): the engine's activity never runs onCreate/onStart/onResume; its lifecycle follows the window instead,
     * so code that waits for a resumed activity (extensions refreshing the app after a sync) works.
     */
    fun desktopSetResumed(resumed: Boolean) {
        val r = mLifecycleRegistry
        if (resumed) {
            if (r.currentState == Lifecycle.State.INITIALIZED) r.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            if (r.currentState == Lifecycle.State.CREATED) r.handleLifecycleEvent(Lifecycle.Event.ON_START)
            if (r.currentState == Lifecycle.State.STARTED) r.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } else if (r.currentState == Lifecycle.State.RESUMED) {
            r.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    override fun onStart() {
        super.onStart()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onPause() {
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        mViewModelStore.clear()
        super.onDestroy()
    }

    override fun onBackPressed() {
        mOnBackPressedDispatcher.onBackPressed()
    }

    open fun addMenuProvider(provider: Any?) {}
    open fun reportFullyDrawn() {}
}

interface OnBackPressedDispatcherOwner : LifecycleOwner {
    val onBackPressedDispatcher: OnBackPressedDispatcher
}

abstract class OnBackPressedCallback(enabled: Boolean) {
    var isEnabled: Boolean = enabled
    internal var dispatcher: OnBackPressedDispatcher? = null

    fun remove() {
        dispatcher?.remove(this)
    }

    abstract fun handleOnBackPressed()
    open fun handleOnBackStarted(backEvent: Any?) {}
    open fun handleOnBackProgressed(backEvent: Any?) {}
    open fun handleOnBackCancelled() {}
}

class OnBackPressedDispatcher(private val fallbackOnBackPressed: Runnable?) {
    private val callbacks = ArrayList<OnBackPressedCallback>()

    constructor() : this(null)

    fun addCallback(onBackPressedCallback: OnBackPressedCallback) {
        onBackPressedCallback.dispatcher = this
        callbacks.add(onBackPressedCallback)
    }

    fun addCallback(owner: LifecycleOwner, onBackPressedCallback: OnBackPressedCallback) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        addCallback(onBackPressedCallback)
        owner.lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) remove(onBackPressedCallback)
            }
        })
    }

    internal fun remove(callback: OnBackPressedCallback) {
        callbacks.remove(callback)
    }

    fun hasEnabledCallbacks(): Boolean = callbacks.any { it.isEnabled }

    fun onBackPressed() {
        val cb = callbacks.lastOrNull { it.isEnabled }
        if (cb != null) {
            cb.handleOnBackPressed()
            return
        }
        fallbackOnBackPressed?.run()
    }
}

fun OnBackPressedDispatcher.addCallback(
    owner: LifecycleOwner? = null,
    enabled: Boolean = true,
    onBackPressed: OnBackPressedCallback.() -> Unit
): OnBackPressedCallback {
    val callback = object : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() = onBackPressed()
    }
    if (owner != null) addCallback(owner, callback) else addCallback(callback)
    return callback
}
