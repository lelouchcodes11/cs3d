package android.view

import android.content.Context

open class ScaleGestureDetector(
    context: Context,
    val listener: OnScaleGestureListener
) {
    interface OnScaleGestureListener {
        fun onScale(detector: ScaleGestureDetector): Boolean
        fun onScaleBegin(detector: ScaleGestureDetector): Boolean
        fun onScaleEnd(detector: ScaleGestureDetector)
    }

    open class SimpleOnScaleGestureListener : OnScaleGestureListener {
        override fun onScale(detector: ScaleGestureDetector): Boolean = false
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
        override fun onScaleEnd(detector: ScaleGestureDetector) {}
    }

    open val scaleFactor: Float = 1.0f
    open val focusX: Float = 0.0f
    open val focusY: Float = 0.0f

    open fun onTouchEvent(event: MotionEvent): Boolean = false
}
