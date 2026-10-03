package android.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

open class TextClock : TextView {
    private var format12: CharSequence? = null
    private var format24: CharSequence? = null
    private var zone: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            onTimeChanged()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 1000 - now % 1000)
        }
    }

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        format12 = attrs?.getAttributeValue(ANDROID_NS, "format12Hour")
        format24 = attrs?.getAttributeValue(ANDROID_NS, "format24Hour")
        zone = attrs?.getAttributeValue(ANDROID_NS, "timeZone")
        onTimeChanged()
    }

    open fun getFormat12Hour(): CharSequence? = format12
    open fun setFormat12Hour(format: CharSequence?) { format12 = format; onTimeChanged() }
    open fun getFormat24Hour(): CharSequence? = format24
    open fun setFormat24Hour(format: CharSequence?) { format24 = format; onTimeChanged() }
    open fun getTimeZone(): String? = zone
    open fun setTimeZone(timeZone: String?) { zone = timeZone; onTimeChanged() }
    open fun is24HourModeEnabled(): Boolean = android.text.format.DateFormat.is24HourFormat(getContext())

    private fun onTimeChanged() {
        val pattern = (if (is24HourModeEnabled()) format24 ?: format12 ?: DEFAULT_24 else format12 ?: format24 ?: DEFAULT_12).toString()
        val fmt = try { SimpleDateFormat(pattern, Locale.getDefault()) } catch (_: IllegalArgumentException) { SimpleDateFormat(DEFAULT_12, Locale.getDefault()) }
        zone?.let { fmt.timeZone = TimeZone.getTimeZone(it) }
        setText(fmt.format(Date()))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(ticker)
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val DEFAULT_FORMAT_12_HOUR: String = "h:mm a"
        const val DEFAULT_FORMAT_24_HOUR: String = "H:mm"
        private const val DEFAULT_12 = DEFAULT_FORMAT_12_HOUR
        private const val DEFAULT_24 = DEFAULT_FORMAT_24_HOUR
    }
}
