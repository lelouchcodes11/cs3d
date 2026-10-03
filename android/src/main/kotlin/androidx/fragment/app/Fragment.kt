package androidx.fragment.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry

open class Fragment : LifecycleOwner, ViewModelStoreOwner, ActivityResultCaller {
    private val mLifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val mViewModelStore = ViewModelStore()
    private var mViewLifecycleOwner: FragmentViewLifecycleOwner? = null
    private val mFragmentResultRegistry by lazy { ActivityResultRegistry { getContext() } }

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> =
        (getActivity() as? ActivityResultCaller)?.registerForActivityResult(contract, callback)
            ?: mFragmentResultRegistry.register("fragment_rq#" + System.identityHashCode(callback), contract, callback)

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: ActivityResultRegistry,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> =
        registry.register("fragment_rq#" + System.identityHashCode(callback), contract, callback)


    internal var mHost: FragmentActivity? = null
    internal var mContext: Context? = null
    internal var mFragmentManager: FragmentManager? = null
    internal var mParentFragment: Fragment? = null
    private var mChildFragmentManager: FragmentManager? = null
    internal var mTag: String? = null
    internal var mContainerId = 0
    internal var mView: View? = null
    internal var mContainer: ViewGroup? = null
    internal var mAdded = false
    internal var mRemoving = false
    internal var mDetached = false
    internal var mHidden = false
    internal var mCreated = false
    internal var mStarted = false
    internal var mResumed = false
    private var mArguments: Bundle? = null
    private var mRetainInstance = false
    private var mHasMenu = false
    private var mUserVisibleHint = true

    override val lifecycle: Lifecycle get() = mLifecycleRegistry
    override val viewModelStore: ViewModelStore get() = mViewModelStore

    open fun getViewLifecycleOwner(): LifecycleOwner =
        mViewLifecycleOwner ?: throw IllegalStateException("Can't access the Fragment View's LifecycleOwner when getView() is null")

    open fun getContext(): Context? = mContext ?: mHost
    fun requireContext(): Context = getContext() ?: throw IllegalStateException("Fragment $this not attached to a context.")
    fun getActivity(): FragmentActivity? = mHost
    fun requireActivity(): FragmentActivity = mHost ?: throw IllegalStateException("Fragment $this not attached to an activity.")
    fun getHost(): Any? = mHost
    fun requireHost(): Any = mHost ?: throw IllegalStateException("Fragment $this not attached to a host.")
    fun getResources(): Resources = requireContext().resources
    fun getText(resId: Int): CharSequence = getResources().getText(resId)
    fun getString(resId: Int): String = getResources().getString(resId)
    fun getString(resId: Int, vararg formatArgs: Any?): String = getResources().getString(resId, *formatArgs)
    open fun getArguments(): Bundle? = mArguments
    fun requireArguments(): Bundle = mArguments ?: throw IllegalStateException("Fragment $this does not have any arguments.")
    open fun setArguments(args: Bundle?) {
        mArguments = args
    }

    fun getTag(): String? = mTag
    fun getId(): Int = mContainerId
    open fun getView(): View? = mView
    fun requireView(): View = mView ?: throw IllegalStateException("Fragment $this did not return a View from onCreateView() or this was called before onCreateView().")

    @Deprecated("")
    fun getFragmentManager(): FragmentManager? = mFragmentManager
    fun getParentFragmentManager(): FragmentManager =
        mFragmentManager ?: throw IllegalStateException("Fragment $this not associated with a fragment manager.")

    fun getChildFragmentManager(): FragmentManager {
        val fm = mChildFragmentManager
        if (fm != null) return fm
        return FragmentManager(this).also {
            it.attachHost(mHost)
            mChildFragmentManager = it
        }
    }

    fun getParentFragment(): Fragment? = mParentFragment
    fun requireParentFragment(): Fragment = mParentFragment ?: throw IllegalStateException("Fragment $this is not a child Fragment.")
    fun isAdded(): Boolean = mHost != null && mAdded
    fun isDetached(): Boolean = mDetached
    fun isRemoving(): Boolean = mRemoving
    fun isInLayout(): Boolean = false
    fun isResumed(): Boolean = mResumed
    fun isVisible(): Boolean = isAdded() && !mHidden && mView != null && mView?.getVisibility() == View.VISIBLE
    fun isHidden(): Boolean = mHidden
    fun isStateSaved(): Boolean = false
    open fun setRetainInstance(retain: Boolean) {
        mRetainInstance = retain
    }

    fun getRetainInstance(): Boolean = mRetainInstance
    open fun setHasOptionsMenu(hasMenu: Boolean) {
        mHasMenu = hasMenu
    }

    open fun setMenuVisibility(menuVisible: Boolean) {}
    open fun setUserVisibleHint(isVisibleToUser: Boolean) {
        mUserVisibleHint = isVisibleToUser
    }

    open fun getUserVisibleHint(): Boolean = mUserVisibleHint
    open fun getLayoutInflater(): LayoutInflater = onGetLayoutInflater(null)
    open fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater =
        LayoutInflater.from(requireContext()).cloneInContext(requireContext()).also { it.hostFragment = this }

    open fun startActivity(intent: Intent) = requireContext().startActivity(intent)
    open fun startActivity(intent: Intent, options: Bundle?) = requireContext().startActivity(intent, options)

    @Deprecated("")
    open fun startActivityForResult(intent: Intent, requestCode: Int) = requireActivity().startActivityForResult(intent, requestCode)

    @Deprecated("")
    open fun requestPermissions(permissions: Array<String>, requestCode: Int) {
        onRequestPermissionsResult(requestCode, permissions, IntArray(permissions.size))
    }

    open fun postponeEnterTransition() {}
    open fun startPostponedEnterTransition() {}
    open fun setEnterTransition(transition: Any?) {}
    open fun setExitTransition(transition: Any?) {}

    // ------------------------------------------------------------------ lifecycle callbacks
    open fun onAttach(context: Context) {}

    @Deprecated("")
    open fun onAttach(activity: Activity) {}
    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = null
    open fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    @Deprecated("")
    open fun onActivityCreated(savedInstanceState: Bundle?) {}
    open fun onViewStateRestored(savedInstanceState: Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroyView() {}
    open fun onDestroy() {}
    open fun onDetach() {}
    open fun onSaveInstanceState(outState: Bundle) {}
    open fun onConfigurationChanged(newConfig: Configuration) {}
    open fun onHiddenChanged(hidden: Boolean) {}
    open fun onLowMemory() {}
    open fun onAttachFragment(childFragment: Fragment) {}
    open fun onPrimaryNavigationFragmentChanged(isPrimaryNavigationFragment: Boolean) {}
    open fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {}
    open fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {}

    @Deprecated("")
    open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}

    @Deprecated("")
    open fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {}

    // ------------------------------------------------------------------ lifecycle drivers (FragmentManager)
    internal fun performAttach(host: FragmentActivity?, context: Context?, fm: FragmentManager) {
        mHost = host
        mContext = context
        mFragmentManager = fm
        mParentFragment = fm.parentFragment
        onAttach(requireContext())
        if (host != null) onAttach(host as Activity)
        mParentFragment?.onAttachFragment(this)
    }

    internal fun performCreate(savedInstanceState: Bundle?) {
        if (mCreated) return
        onCreate(savedInstanceState)
        mCreated = true
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    internal open fun performCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mViewLifecycleOwner = FragmentViewLifecycleOwner()
        val view = try {
            onCreateView(inflater, container, savedInstanceState)
        } catch (t: Throwable) {
            mViewLifecycleOwner = null
            throw t
        }
        mView = view
        if (view != null) {
            mViewLifecycleOwner?.registry?.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            onViewCreated(view, savedInstanceState)
            onViewStateRestored(savedInstanceState)
        } else {
            mViewLifecycleOwner = null
        }
        @Suppress("DEPRECATION")
        onActivityCreated(savedInstanceState)
        return view
    }

    internal open fun performStart() {
        if (mStarted) return
        mStarted = true
        onStart()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        mViewLifecycleOwner?.registry?.handleLifecycleEvent(Lifecycle.Event.ON_START)
        mChildFragmentManager?.dispatchStart()
    }

    internal open fun performResume() {
        if (mResumed) return
        mResumed = true
        onResume()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        mViewLifecycleOwner?.registry?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        mChildFragmentManager?.dispatchResume()
    }

    internal open fun performPause() {
        if (!mResumed) return
        mChildFragmentManager?.dispatchPause()
        mViewLifecycleOwner?.registry?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        mResumed = false
        onPause()
    }

    internal open fun performStop() {
        if (!mStarted) return
        mChildFragmentManager?.dispatchStop()
        mViewLifecycleOwner?.registry?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        mStarted = false
        onStop()
    }

    internal open fun performDestroyView() {
        mChildFragmentManager?.dispatchDestroyView()
        mViewLifecycleOwner?.registry?.let {
            if (it.currentState.isAtLeast(Lifecycle.State.CREATED)) it.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        onDestroyView()
        mView?.let { v ->
            (v.getParent() as? ViewGroup)?.removeView(v)
        }
        mView = null
        mViewLifecycleOwner = null
    }

    internal open fun performDestroy() {
        mChildFragmentManager?.dispatchDestroy()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        onDestroy()
        mViewModelStore.clear()
        mCreated = false
    }

    internal fun performDetach() {
        onDetach()
        mHost = null
        mContext = null
        mFragmentManager = null
    }

    internal class FragmentViewLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    override fun toString(): String = "${javaClass.simpleName}{${Integer.toHexString(System.identityHashCode(this))}}" + (mTag?.let { " tag=$it" } ?: "")

    companion object {
        @JvmStatic
        @Deprecated("")
        fun instantiate(context: Context, fname: String): Fragment = instantiate(context, fname, null)

        @JvmStatic
        @Deprecated("")
        fun instantiate(context: Context, fname: String, args: Bundle?): Fragment {
            val clazz = Class.forName(fname, true, context.classLoader)
            val f = clazz.getDeclaredConstructor().newInstance() as Fragment
            if (args != null) f.setArguments(args)
            return f
        }
    }
}
