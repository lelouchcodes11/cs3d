package com.lagradost.desktop.runtime.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.MutableIntState
import java.awt.EventQueue
import java.util.IdentityHashMap

/** The Compose node of a view, invalidated when native layout changes the view's frame */
interface ViewNodeHandle {
    fun invalidateNodeMeasurement()
}

/**
 * The traversal part of ViewRootImpl for the view hosts: a layout request that reaches a hosted
 * root bumps the host's request state; the host then measures and lays out the root natively
 * ([performTraversal]). Requests made during a layout pass get a second pass like
 * ViewRootImpl.requestLayoutDuringLayout, and runnables posted while a layout is pending run after
 * it (the traversal barrier), so post { view.width } sees the laid out size.
 */
object LayoutScheduler {
    /** View currently propagating requestLayout() (AttachInfo.mViewRequestingLayout) */
    @JvmField
    var viewRequestingLayout: View? = null

    /** true while a host runs View.layout() on its root */
    @JvmField
    var isInLayout = false

    private var handlingLayoutInLayoutRequest = false
    private val layoutRequesters = ArrayList<View>()
    private val afterLayout = IdentityHashMap<View, ArrayList<Runnable>>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun scheduleTraversal(request: MutableIntState) {
        if (handlingLayoutInLayoutRequest) return
        if (EventQueue.isDispatchThread()) request.intValue++
        else EventQueue.invokeLater { request.intValue++ }
    }

    /** ViewRootImpl.requestLayoutDuringLayout: false postpones the request to the next frame */
    fun requestLayoutDuringLayout(view: View): Boolean {
        if (view.getParent() == null || !view.isAttachedToWindow()) return true
        if (!layoutRequesters.contains(view)) layoutRequesters.add(view)
        return !handlingLayoutInLayoutRequest
    }

    /** Measures and lays out a hosted root at (0, 0), then runs what waited for the layout */
    fun performTraversal(root: View, widthSpec: Int, heightSpec: Int) {
        try {
            root.measure(widthSpec, heightSpec)
            layoutRoot(root)
            if (layoutRequesters.isNotEmpty()) {
                val valid = validRequesters(root, false)
                if (valid.isNotEmpty()) {
                    handlingLayoutInLayoutRequest = true
                    try {
                        for (v in valid) v.requestLayout()
                        root.measure(widthSpec, heightSpec)
                        layoutRoot(root)
                    } finally {
                        handlingLayoutInLayoutRequest = false
                    }
                    val again = validRequesters(root, true)
                    if (again.isNotEmpty()) handler.post { for (v in again) v.requestLayout() }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("LayoutScheduler", "layout of $root failed", t)
        } finally {
            isInLayout = false
        }
        try {
            val observer = root.getViewTreeObserver()
            observer.dispatchOnGlobalLayout()
            observer.dispatchOnPreDraw()
        } catch (t: Throwable) {
            android.util.Log.e("LayoutScheduler", "global layout listener failed", t)
        }
        val pending = synchronized(afterLayout) { afterLayout.remove(root) }
        if (pending != null) for (r in pending) handler.post(r)
    }

    private fun layoutRoot(root: View) {
        isInLayout = true
        try {
            root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight())
        } finally {
            isInLayout = false
        }
    }

    /** Requesters of [root] still waiting for a layout, visible, attached */
    private fun validRequesters(root: View, second: Boolean): List<View> {
        val out = ArrayList<View>()
        val it = layoutRequesters.iterator()
        while (it.hasNext()) {
            val v = it.next()
            if (v.getRootView() !== root) continue
            it.remove()
            if (!v.isAttachedToWindow() || !v.isLayoutRequested()) continue
            var gone = false
            var p: View? = v
            while (p != null) {
                if (p.getVisibility() == View.GONE) {
                    gone = true
                    break
                }
                p = p.getParent() as? View
            }
            if (!gone || second) out.add(v)
        }
        return out
    }

    /** Runs [action] after the pending layout of [root]; false when no layout is pending */
    fun postAfterLayout(root: View, action: Runnable): Boolean {
        if (root.hostLayoutRequest == null || !root.isLayoutRequested()) return false
        synchronized(afterLayout) { afterLayout.getOrPut(root) { ArrayList() }.add(action) }
        return true
    }

    fun removeCallbacks(action: Runnable) {
        synchronized(afterLayout) { for (list in afterLayout.values) list.removeAll { it === action } }
    }

    /** Drops what waited for a root that is no longer shown (posted to the handler instead) */
    fun onHostDisposed(root: View) {
        val pending = synchronized(afterLayout) { afterLayout.remove(root) } ?: return
        for (r in pending) handler.post(r)
    }
}
