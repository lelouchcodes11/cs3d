package com.google.android.material.slider

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import com.lagradost.desktop.runtime.ui.ViewAttributes
import kotlin.math.roundToInt

/**
 * Material Slider on the compat SeekBar: the value range is mapped onto integer progress steps
 * (stepSize, or 1000 steps for a continuous slider), so the SeekBar renderer and drag handling apply.
 */
open class Slider : SeekBar {
    fun interface OnChangeListener {
        fun onValueChange(slider: Slider, value: Float, fromUser: Boolean)
    }

    interface OnSliderTouchListener {
        fun onStartTrackingTouch(slider: Slider)
        fun onStopTrackingTouch(slider: Slider)
    }

    fun interface LabelFormatter {
        fun getFormattedValue(value: Float): String
    }

    private var mValueFrom = 0f
    private var mValueTo = 1f
    private var mValue = 0f
    private var mStepSize = 0f
    private var syncing = false
    private val changeListeners = ArrayList<OnChangeListener>()
    private val touchListeners = ArrayList<OnSliderTouchListener>()

    var valueFrom: Float
        get() = mValueFrom
        set(v) {
            mValueFrom = v
            sync()
        }

    var valueTo: Float
        get() = mValueTo
        set(v) {
            mValueTo = v
            sync()
        }

    var stepSize: Float
        get() = mStepSize
        set(v) {
            mStepSize = v
            sync()
        }

    var value: Float
        get() = mValue
        set(v) {
            val changed = v != mValue
            mValue = v
            sync()
            if (changed) for (l in changeListeners.toList()) l.onValueChange(this, mValue, false)
        }

    constructor(context: Context?) : super(context) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        if (attrs != null) {
            val r = ViewAttributes.reader(this, attrs)
            r.float("valueFrom")?.let { mValueFrom = it }
            r.float("valueTo")?.let { mValueTo = it }
            r.float("stepSize")?.let { mStepSize = it }
            mValue = r.float("value") ?: mValueFrom
            r.colorStateList("thumbColor")?.let { setThumbTintList(it) }
            r.colorStateList("thumbTint")?.let { setThumbTintList(it) }
            r.colorStateList("trackColorActive")?.let { setProgressTintList(it) }
            r.colorStateList("trackColorInactive")?.let { setProgressBackgroundTintList(it) }
            r.colorStateList("progressTint")?.let { setProgressTintList(it) }
        }
        super.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (syncing || !fromUser) return
                val v = valueOf(progress)
                if (v == mValue) return
                mValue = v
                for (l in changeListeners.toList()) l.onValueChange(this@Slider, v, true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                for (l in touchListeners.toList()) l.onStartTrackingTouch(this@Slider)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                for (l in touchListeners.toList()) l.onStopTrackingTouch(this@Slider)
            }
        })
        sync()
    }

    private fun steps(): Int {
        val range = mValueTo - mValueFrom
        if (range <= 0f) return 1
        return if (mStepSize > 0f) maxOf(1, (range / mStepSize).roundToInt()) else 1000
    }

    private fun valueOf(progress: Int): Float {
        val range = mValueTo - mValueFrom
        return if (mStepSize > 0f) mValueFrom + progress * mStepSize else mValueFrom + range * progress / steps()
    }

    private fun sync() {
        syncing = true
        try {
            val steps = steps()
            setMax(steps)
            val range = mValueTo - mValueFrom
            val p = if (range <= 0f) 0 else ((mValue - mValueFrom) / range * steps).roundToInt()
            setProgress(p.coerceIn(0, steps))
        } finally {
            syncing = false
        }
    }

    fun addOnChangeListener(listener: OnChangeListener) {
        changeListeners.add(listener)
    }

    fun removeOnChangeListener(listener: OnChangeListener) {
        changeListeners.remove(listener)
    }

    fun clearOnChangeListeners() = changeListeners.clear()

    fun addOnSliderTouchListener(listener: OnSliderTouchListener) {
        touchListeners.add(listener)
    }

    fun removeOnSliderTouchListener(listener: OnSliderTouchListener) {
        touchListeners.remove(listener)
    }

    fun clearOnSliderTouchListeners() = touchListeners.clear()
    fun setLabelFormatter(formatter: LabelFormatter?) {}
    fun setLabelBehavior(labelBehavior: Int) {}
    fun setThumbRadius(radius: Int) {}
    fun setTrackHeight(height: Int) {}
}
