package androidx.preference.internal

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import com.lagradost.desktop.runtime.ui.ViewAttributes

/** The icon slot of a preference row. maxWidth and maxHeight (48dp in the library layout) cap the measure. */
open class PreferenceImageView : ImageView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        read(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        read(attrs)
    }

    private fun read(attrs: AttributeSet?) {
        if (attrs == null) return
        val r = ViewAttributes.reader(this, attrs)
        r.dim("maxWidth")?.let { setMaxWidth(it) }
        r.dim("maxHeight")?.let { setMaxHeight(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(cap(widthMeasureSpec, getMaxWidth()), cap(heightMeasureSpec, getMaxHeight()))
    }

    private fun cap(spec: Int, maxPx: Int): Int {
        if (maxPx == Int.MAX_VALUE) return spec
        val mode = MeasureSpec.getMode(spec)
        if (mode != MeasureSpec.AT_MOST && mode != MeasureSpec.UNSPECIFIED) return spec
        val size = MeasureSpec.getSize(spec)
        if (maxPx < size || mode == MeasureSpec.UNSPECIFIED) return MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST)
        return spec
    }
}
