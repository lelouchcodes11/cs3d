package androidx.fragment.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

open class FragmentActivity : ComponentActivity() {
    private val mFragments = FragmentManager(null).also { it.attachHost(this) }

    open fun getSupportFragmentManager(): FragmentManager = mFragments

    /**
     * desktop: for an activity that never runs its own lifecycle (the engine context of the native UI):
     * fragments and dialog fragments added to it (extension settings, ...) must still start and show.
     */
    fun startFragmentHost() {
        mFragments.dispatchStart()
        mFragments.dispatchResume()
    }

    override fun onStart() {
        super.onStart()
        mFragments.dispatchStart()
    }

    override fun onResume() {
        super.onResume()
        mFragments.dispatchResume()
    }

    override fun onPause() {
        mFragments.dispatchPause()
        super.onPause()
    }

    override fun onStop() {
        mFragments.dispatchStop()
        super.onStop()
    }

    override fun onDestroy() {
        mFragments.dispatchDestroyView()
        mFragments.dispatchDestroy()
        super.onDestroy()
    }

    open fun supportInvalidateOptionsMenu() {}
    open fun supportFinishAfterTransition() = finish()
    open fun supportPostponeEnterTransition() {}
    open fun supportStartPostponedEnterTransition() {}
    open fun onAttachFragment(fragment: Fragment) {}
}

/**
 * Minimal FragmentManager: supports dialog fragments and fragments hosted in container views,
 * transactions (add/replace/remove/show/hide), and a back stack.
 */
open class FragmentManager internal constructor(internal val parentFragment: Fragment?) {
    fun interface OnBackStackChangedListener {
        fun onBackStackChanged()
    }

    interface BackStackEntry {
        fun getId(): Int
        fun getName(): String?
    }

    abstract class FragmentLifecycleCallbacks {
        open fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {}
        open fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {}
        open fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: android.view.View, savedInstanceState: Bundle?) {}
        open fun onFragmentStarted(fm: FragmentManager, f: Fragment) {}
        open fun onFragmentResumed(fm: FragmentManager, f: Fragment) {}
        open fun onFragmentPaused(fm: FragmentManager, f: Fragment) {}
        open fun onFragmentStopped(fm: FragmentManager, f: Fragment) {}
        open fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {}
        open fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {}
        open fun onFragmentDetached(fm: FragmentManager, f: Fragment) {}
    }

    companion object {
        const val POP_BACK_STACK_INCLUSIVE = 1

        @JvmStatic
        fun enableDebugLogging(enabled: Boolean) {}

        @JvmStatic
        fun <F : Fragment> findFragment(view: android.view.View): F = throw IllegalStateException("View $view does not have a Fragment set")
    }

    internal var host: FragmentActivity? = null
    private val added = ArrayList<Fragment>()
    private val backStack = ArrayList<BackStackRecord>()
    private val backStackListeners = ArrayList<OnBackStackChangedListener>()
    private val handler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var hostState = Lifecycle.State.CREATED
    private var nextBackStackId = 0

    internal fun attachHost(host: FragmentActivity?) {
        this.host = host
    }

    private fun context(): Context? = parentFragment?.getContext() ?: host

    open fun beginTransaction(): FragmentTransaction = BackStackRecord(this)
    open fun executePendingTransactions(): Boolean = true
    open fun getFragments(): List<Fragment> = added.toList()
    open fun findFragmentByTag(tag: String?): Fragment? = added.lastOrNull { it.mTag == tag }
    open fun findFragmentById(id: Int): Fragment? = added.lastOrNull { it.mContainerId == id }
    open fun isDestroyed(): Boolean = destroyed
    open fun isStateSaved(): Boolean = false
    open fun getBackStackEntryCount(): Int = backStack.size
    open fun getBackStackEntryAt(index: Int): BackStackEntry = backStack[index]
    open fun addOnBackStackChangedListener(listener: OnBackStackChangedListener) {
        backStackListeners.add(listener)
    }

    open fun removeOnBackStackChangedListener(listener: OnBackStackChangedListener) {
        backStackListeners.remove(listener)
    }

    open fun registerFragmentLifecycleCallbacks(cb: FragmentLifecycleCallbacks, recursive: Boolean) {}
    open fun unregisterFragmentLifecycleCallbacks(cb: FragmentLifecycleCallbacks) {}
    open fun getPrimaryNavigationFragment(): Fragment? = null
    open fun putFragment(bundle: Bundle, key: String, fragment: Fragment) {}
    open fun getFragment(bundle: Bundle, key: String): Fragment? = null
    open fun setFragmentResult(requestKey: String, result: Bundle) {
        fragmentResultListeners[requestKey]?.onFragmentResult(requestKey, result)
    }

    private val fragmentResultListeners = HashMap<String, FragmentResultListener>()
    open fun setFragmentResultListener(requestKey: String, lifecycleOwner: LifecycleOwner, listener: FragmentResultListener) {
        fragmentResultListeners[requestKey] = listener
    }

    open fun clearFragmentResultListener(requestKey: String) {
        fragmentResultListeners.remove(requestKey)
    }

    open fun popBackStack() {
        handler.post { popBackStackImmediate() }
    }

    open fun popBackStack(name: String?, flags: Int) {
        handler.post { popBackStackImmediate(name, flags) }
    }

    open fun popBackStack(id: Int, flags: Int) {
        handler.post { popBackStackImmediate() }
    }

    open fun popBackStackImmediate(): Boolean {
        val record = backStack.removeLastOrNull() ?: return false
        record.reverse()
        backStackListeners.toList().forEach { it.onBackStackChanged() }
        return true
    }

    open fun popBackStackImmediate(name: String?, flags: Int): Boolean {
        if (name == null) return popBackStackImmediate()
        val idx = backStack.indexOfLast { it.mName == name }
        if (idx < 0) return false
        val target = if ((flags and POP_BACK_STACK_INCLUSIVE) != 0) idx else idx + 1
        while (backStack.size > target) popBackStackImmediate()
        return true
    }

    internal fun scheduleCommit(record: BackStackRecord) {
        handler.post { execute(record) }
    }

    internal fun execute(record: BackStackRecord) {
        if (destroyed) return
        record.run()
        if (record.addToBackStack) {
            record.mId = nextBackStackId++
            backStack.add(record)
            backStackListeners.toList().forEach { it.onBackStackChanged() }
        }
    }

    private fun findContainer(id: Int): ViewGroup? {
        if (id == 0) return null
        parentFragment?.getView()?.findViewById<android.view.View>(id)?.let { return it as? ViewGroup }
        return host?.findViewById<android.view.View>(id) as? ViewGroup
    }

    internal fun addFragment(f: Fragment, containerId: Int, tag: String?, containerView: ViewGroup? = null) {
        if (tag != null) f.mTag = tag
        if (containerId != 0) f.mContainerId = containerId
        if (f.mAdded) return
        f.mAdded = true
        f.mRemoving = false
        added.add(f)
        val ctx = context()
        f.performAttach(host, ctx, this)
        host?.onAttachFragment(f)
        f.performCreate(f.getArguments())
        val container = containerView ?: findContainer(f.mContainerId)
        f.mContainer = container
        val view = f.performCreateView(f.onGetLayoutInflater(null), container, null)
        if (view != null && container != null && view.getParent() == null) container.addView(view)
        if (f.mHidden) view?.setVisibility(android.view.View.GONE)
        moveToHostState(f)
    }

    private fun moveToHostState(f: Fragment) {
        val state = if (parentFragment != null) {
            when {
                parentFragment.mResumed -> Lifecycle.State.RESUMED
                parentFragment.mStarted -> Lifecycle.State.STARTED
                else -> Lifecycle.State.CREATED
            }
        } else hostState
        if (state.isAtLeast(Lifecycle.State.STARTED)) f.performStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) f.performResume()
    }

    internal fun removeFragment(f: Fragment) {
        if (!added.remove(f)) return
        f.mRemoving = true
        f.performPause()
        f.performStop()
        f.performDestroyView()
        f.performDestroy()
        f.performDetach()
        f.mAdded = false
    }

    internal fun fragmentsInContainer(containerId: Int): List<Fragment> = added.filter { it.mContainerId == containerId }

    internal fun showFragment(f: Fragment, show: Boolean) {
        if (f.mHidden == !show) return
        f.mHidden = !show
        f.getView()?.setVisibility(if (show) android.view.View.VISIBLE else android.view.View.GONE)
        f.onHiddenChanged(!show)
    }

    internal fun detachFragment(f: Fragment) {
        if (f.mDetached) return
        f.mDetached = true
        f.performDestroyView()
    }

    internal fun attachFragment(f: Fragment) {
        if (!f.mDetached) return
        f.mDetached = false
        val container = findContainer(f.mContainerId)
        val view = f.performCreateView(f.onGetLayoutInflater(null), container, null)
        if (view != null && container != null && view.getParent() == null) container.addView(view)
    }

    internal fun dispatchStart() {
        if (!hostState.isAtLeast(Lifecycle.State.STARTED)) hostState = Lifecycle.State.STARTED
        added.toList().forEach { it.performStart() }
    }

    internal fun dispatchResume() {
        hostState = Lifecycle.State.RESUMED
        added.toList().forEach { it.performResume() }
    }

    internal fun dispatchPause() {
        hostState = Lifecycle.State.STARTED
        added.toList().forEach { it.performPause() }
    }

    internal fun dispatchStop() {
        hostState = Lifecycle.State.CREATED
        added.toList().forEach { it.performStop() }
    }

    internal fun dispatchDestroyView() {
        added.toList().forEach { it.performDestroyView() }
    }

    internal fun dispatchDestroy() {
        destroyed = true
        added.toList().forEach {
            it.performDestroy()
            it.performDetach()
        }
        added.clear()
    }
}

fun interface FragmentResultListener {
    fun onFragmentResult(requestKey: String, result: Bundle)
}

abstract class FragmentTransaction {
    companion object {
        const val TRANSIT_NONE = 0
        const val TRANSIT_UNSET = -1
        const val TRANSIT_FRAGMENT_OPEN = 4097
        const val TRANSIT_FRAGMENT_CLOSE = 8194
        const val TRANSIT_FRAGMENT_FADE = 4099
    }

    abstract fun add(fragment: Fragment, tag: String?): FragmentTransaction
    abstract fun add(containerViewId: Int, fragment: Fragment): FragmentTransaction
    abstract fun add(containerViewId: Int, fragment: Fragment, tag: String?): FragmentTransaction
    abstract fun add(container: ViewGroup, fragment: Fragment, tag: String?): FragmentTransaction
    abstract fun replace(containerViewId: Int, fragment: Fragment): FragmentTransaction
    abstract fun replace(containerViewId: Int, fragment: Fragment, tag: String?): FragmentTransaction
    abstract fun remove(fragment: Fragment): FragmentTransaction
    abstract fun hide(fragment: Fragment): FragmentTransaction
    abstract fun show(fragment: Fragment): FragmentTransaction
    abstract fun detach(fragment: Fragment): FragmentTransaction
    abstract fun attach(fragment: Fragment): FragmentTransaction
    abstract fun setPrimaryNavigationFragment(fragment: Fragment?): FragmentTransaction
    abstract fun setMaxLifecycle(fragment: Fragment, state: Lifecycle.State): FragmentTransaction
    abstract fun isEmpty(): Boolean
    abstract fun setCustomAnimations(enter: Int, exit: Int): FragmentTransaction
    abstract fun setCustomAnimations(enter: Int, exit: Int, popEnter: Int, popExit: Int): FragmentTransaction
    abstract fun setTransition(transit: Int): FragmentTransaction
    abstract fun addSharedElement(sharedElement: android.view.View, name: String): FragmentTransaction
    abstract fun addToBackStack(name: String?): FragmentTransaction
    abstract fun isAddToBackStackAllowed(): Boolean
    abstract fun disallowAddToBackStack(): FragmentTransaction
    abstract fun setReorderingAllowed(reorderingAllowed: Boolean): FragmentTransaction
    abstract fun runOnCommit(runnable: Runnable): FragmentTransaction
    abstract fun commit(): Int
    abstract fun commitAllowingStateLoss(): Int
    abstract fun commitNow()
    abstract fun commitNowAllowingStateLoss()
}

internal class BackStackRecord(private val manager: FragmentManager) : FragmentTransaction(), FragmentManager.BackStackEntry {
    private sealed interface Op
    private data class Add(val f: Fragment, val container: Int, val tag: String?, val containerView: ViewGroup? = null) : Op
    private data class Replace(val f: Fragment, val container: Int, val tag: String?) : Op
    private data class Remove(val f: Fragment) : Op
    private data class Hide(val f: Fragment) : Op
    private data class Show(val f: Fragment) : Op
    private data class Detach(val f: Fragment) : Op
    private data class Attach(val f: Fragment) : Op

    private val ops = ArrayList<Op>()
    private val commitRunnables = ArrayList<Runnable>()
    private val replacedFragments = ArrayList<Fragment>()
    var addToBackStack = false
    var mName: String? = null
    var mId = -1
    private var committed = false

    override fun getId(): Int = mId
    override fun getName(): String? = mName

    override fun add(fragment: Fragment, tag: String?): FragmentTransaction = apply { ops.add(Add(fragment, 0, tag)) }
    override fun add(containerViewId: Int, fragment: Fragment): FragmentTransaction = apply { ops.add(Add(fragment, containerViewId, null)) }
    override fun add(containerViewId: Int, fragment: Fragment, tag: String?): FragmentTransaction = apply { ops.add(Add(fragment, containerViewId, tag)) }
    override fun add(container: ViewGroup, fragment: Fragment, tag: String?): FragmentTransaction {
        if (container.getId() == android.view.View.NO_ID) container.setId(android.view.View.generateViewId())
        return apply { ops.add(Add(fragment, container.getId(), tag, container)) }
    }

    override fun replace(containerViewId: Int, fragment: Fragment): FragmentTransaction = replace(containerViewId, fragment, null)
    override fun replace(containerViewId: Int, fragment: Fragment, tag: String?): FragmentTransaction = apply { ops.add(Replace(fragment, containerViewId, tag)) }
    override fun remove(fragment: Fragment): FragmentTransaction = apply { ops.add(Remove(fragment)) }
    override fun hide(fragment: Fragment): FragmentTransaction = apply { ops.add(Hide(fragment)) }
    override fun show(fragment: Fragment): FragmentTransaction = apply { ops.add(Show(fragment)) }
    override fun detach(fragment: Fragment): FragmentTransaction = apply { ops.add(Detach(fragment)) }
    override fun attach(fragment: Fragment): FragmentTransaction = apply { ops.add(Attach(fragment)) }
    override fun setPrimaryNavigationFragment(fragment: Fragment?): FragmentTransaction = this
    override fun setMaxLifecycle(fragment: Fragment, state: Lifecycle.State): FragmentTransaction = this
    override fun isEmpty(): Boolean = ops.isEmpty()
    override fun setCustomAnimations(enter: Int, exit: Int): FragmentTransaction = this
    override fun setCustomAnimations(enter: Int, exit: Int, popEnter: Int, popExit: Int): FragmentTransaction = this
    override fun setTransition(transit: Int): FragmentTransaction = this
    override fun addSharedElement(sharedElement: android.view.View, name: String): FragmentTransaction = this
    override fun addToBackStack(name: String?): FragmentTransaction = apply {
        addToBackStack = true
        this.mName = name
    }

    override fun isAddToBackStackAllowed(): Boolean = true
    override fun disallowAddToBackStack(): FragmentTransaction = this
    override fun setReorderingAllowed(reorderingAllowed: Boolean): FragmentTransaction = this
    override fun runOnCommit(runnable: Runnable): FragmentTransaction = apply { commitRunnables.add(runnable) }

    override fun commit(): Int {
        check(!committed) { "commit already called" }
        committed = true
        manager.scheduleCommit(this)
        return 0
    }

    override fun commitAllowingStateLoss(): Int = commit()
    override fun commitNow() {
        check(!committed) { "commit already called" }
        committed = true
        manager.execute(this)
    }

    override fun commitNowAllowingStateLoss() = commitNow()

    fun run() {
        for (op in ops) {
            when (op) {
                is Add -> manager.addFragment(op.f, op.container, op.tag, op.containerView)
                is Replace -> {
                    for (old in manager.fragmentsInContainer(op.container)) {
                        if (old !== op.f) {
                            replacedFragments.add(old)
                            manager.removeFragment(old)
                        }
                    }
                    manager.addFragment(op.f, op.container, op.tag)
                }
                is Remove -> manager.removeFragment(op.f)
                is Hide -> manager.showFragment(op.f, false)
                is Show -> manager.showFragment(op.f, true)
                is Detach -> manager.detachFragment(op.f)
                is Attach -> manager.attachFragment(op.f)
            }
        }
        commitRunnables.forEach { it.run() }
    }

    fun reverse() {
        for (op in ops.reversed()) {
            when (op) {
                is Add -> manager.removeFragment(op.f)
                is Replace -> {
                    manager.removeFragment(op.f)
                    for (old in replacedFragments) manager.addFragment(old, op.container, old.getTag())
                }
                is Remove -> manager.addFragment(op.f, op.f.mContainerId, op.f.getTag())
                is Hide -> manager.showFragment(op.f, true)
                is Show -> manager.showFragment(op.f, false)
                is Detach -> manager.attachFragment(op.f)
                is Attach -> manager.detachFragment(op.f)
            }
        }
    }
}

open class FragmentContainerView : android.widget.FrameLayout {
    private var pendingName: String? = null
    private var pendingTag: String? = null
    private var pendingGraph: Int = 0
    private var pendingDefaultHost: Boolean = false
    private var installed = false

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: android.util.AttributeSet?) : super(context, attrs) {
        readFragment(attrs)
        installFragment()
    }
    constructor(context: Context?, attrs: android.util.AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        readFragment(attrs)
        installFragment()
    }

    private fun readFragment(attrs: android.util.AttributeSet?) {
        if (attrs == null) return
        val ns = "http://schemas.android.com/apk/res/android"
        val app = "http://schemas.android.com/apk/res-auto"
        pendingName = attrs.getAttributeValue(ns, "name")
        pendingTag = attrs.getAttributeValue(ns, "tag")
        pendingGraph = attrs.getAttributeResourceValue(app, "navGraph", 0)
        if (pendingGraph == 0) pendingGraph = attrs.getAttributeResourceValue(null, "navGraph", 0)
        pendingDefaultHost = attrs.getAttributeBooleanValue(app, "defaultNavHost", false) ||
            attrs.getAttributeBooleanValue(null, "defaultNavHost", false)
    }

    /** The host must exist before inflation returns, because MainActivity looks it up in onCreate. */
    private fun installFragment() {
        if (installed) return
        val name = pendingName ?: return
        val activity = findActivity() as? FragmentActivity ?: return
        installed = true
        val id = getId()
        if (id != android.view.View.NO_ID && activity.getSupportFragmentManager().findFragmentById(id) != null) return
        val fragment = try {
            Fragment.instantiate(getContext(), name)
        } catch (t: Throwable) {
            android.util.Log.e("FragmentContainerView", "Cannot create $name", t)
            return
        }
        if (pendingGraph != 0 || pendingDefaultHost) {
            val args = fragment.getArguments() ?: android.os.Bundle()
            if (pendingGraph != 0) args.putInt("android-nav-graph", pendingGraph)
            if (pendingDefaultHost) args.putBoolean("android-nav-default-host", true)
            fragment.setArguments(args)
        }
        activity.getSupportFragmentManager().beginTransaction().add(this, fragment, pendingTag).commitNow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installFragment()
    }

    open fun <F : Fragment> getFragment(): F {
        val found = (findActivity() as? FragmentActivity)?.getSupportFragmentManager()?.findFragmentById(getId())
            ?: throw IllegalStateException("No fragment")
        @Suppress("UNCHECKED_CAST")
        return found as F
    }
}
