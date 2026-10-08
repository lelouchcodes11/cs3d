package android.view

import android.content.ClipData
import android.content.ClipDescription

/** android.view.DragEvent: what a view's OnDragListener gets during a drag started with View.startDragAndDrop */
class DragEvent internal constructor(
    private val action: Int,
    private val x: Float,
    private val y: Float,
    private val localState: Any?,
    private val clipData: ClipData?,
    private val result: Boolean,
) {
    fun getAction(): Int = action
    fun getX(): Float = x
    fun getY(): Float = y
    fun getLocalState(): Any? = localState
    fun getClipData(): ClipData? = if (action == ACTION_DROP) clipData else null
    fun getClipDescription(): ClipDescription? = clipData?.getDescription()
    fun getResult(): Boolean = result

    override fun toString(): String = "DragEvent{action=$action x=$x y=$y}"

    companion object {
        const val ACTION_DRAG_STARTED = 1
        const val ACTION_DRAG_LOCATION = 2
        const val ACTION_DROP = 3
        const val ACTION_DRAG_ENDED = 4
        const val ACTION_DRAG_ENTERED = 5
        const val ACTION_DRAG_EXITED = 6
    }
}

/**
 * Drag and drop inside one view tree, the way Android runs it: STARTED to every view with a drag listener, ENTERED / LOCATION /
 * EXITED to the listening view under the pointer (coordinates of that view), DROP to it on release, ENDED to all. The pointer
 * comes from the press that started the drag (the renderer's touch loop calls [move] / [release] / [cancel]).
 */
internal object DragAndDrop {
    private class Drag(val root: View, val data: ClipData?, val localState: Any?, val listeners: List<View>) {
        var over: View? = null
    }

    @Volatile
    private var drag: Drag? = null

    val active: Boolean get() = drag != null

    fun start(source: View, data: ClipData?, localState: Any?): Boolean {
        drag?.let { finish(it, false) }
        val root = source.getRootView()
        val listeners = ArrayList<View>()
        fun collect(v: View) {
            if (v.mOnDragListener != null) listeners += v
            if (v is ViewGroup) for (i in 0 until v.getChildCount()) v.getChildAt(i)?.let { collect(it) }
        }
        collect(root)
        android.util.Log.i("DragAndDrop", "drag from ${source.javaClass.simpleName}: ${listeners.size} listening view(s)")
        val d = Drag(root, data, localState, listeners)
        drag = d
        for (v in listeners) v.dispatchDragEvent(DragEvent(DragEvent.ACTION_DRAG_STARTED, 0f, 0f, localState, null, false))
        return true
    }

    private fun bounds(v: View): IntArray = IntArray(2).also { v.getLocationInWindow(it) }

    /** The listening view under the window point: the deepest (smallest) one that contains it */
    private fun target(d: Drag, wx: Float, wy: Float): View? = d.listeners.filter { v ->
        if (!v.isShown()) return@filter false
        val at = bounds(v)
        wx >= at[0] && wy >= at[1] && wx < at[0] + v.getWidth() && wy < at[1] + v.getHeight()
    }.minByOrNull { it.getWidth().toLong() * it.getHeight() }

    private fun local(v: View, wx: Float, wy: Float): Pair<Float, Float> = bounds(v).let { wx - it[0] to wy - it[1] }

    /** The pointer moved to ([x], [y]) in the coordinates of [from] (the pressed view) */
    fun move(from: View, x: Float, y: Float) {
        val d = drag ?: return
        val at = bounds(from)
        val wx = at[0] + x
        val wy = at[1] + y
        val t = target(d, wx, wy)
        if (t !== d.over) {
            d.over?.dispatchDragEvent(DragEvent(DragEvent.ACTION_DRAG_EXITED, 0f, 0f, d.localState, null, false))
            d.over = t
            t?.let { val (lx, ly) = local(it, wx, wy); it.dispatchDragEvent(DragEvent(DragEvent.ACTION_DRAG_ENTERED, lx, ly, d.localState, null, false)) }
        }
        t?.let { val (lx, ly) = local(it, wx, wy); it.dispatchDragEvent(DragEvent(DragEvent.ACTION_DRAG_LOCATION, lx, ly, d.localState, null, false)) }
    }

    /** The press ended at ([x], [y]) of [from]: a drop on the listening view there */
    fun release(from: View, x: Float, y: Float) {
        val d = drag ?: return
        val at = bounds(from)
        val wx = at[0] + x
        val wy = at[1] + y
        val t = target(d, wx, wy)
        val dropped = t?.let { val (lx, ly) = local(it, wx, wy); it.dispatchDragEvent(DragEvent(DragEvent.ACTION_DROP, lx, ly, d.localState, d.data, false)) } ?: false
        android.util.Log.i("DragAndDrop", "drop on ${t?.javaClass?.simpleName} handled=$dropped")
        finish(d, dropped)
    }

    fun cancel() {
        drag?.let { finish(it, false) }
    }

    private fun finish(d: Drag, result: Boolean) {
        if (drag === d) drag = null
        for (v in d.listeners) v.dispatchDragEvent(DragEvent(DragEvent.ACTION_DRAG_ENDED, 0f, 0f, d.localState, null, result))
    }
}
