package com.github.rubensousa.previewseekbar

import android.view.View
import android.widget.FrameLayout

interface PreviewBar {
    interface OnScrubListener {
        fun onScrubStart(previewBar: PreviewBar?)
        fun onScrubMove(previewBar: PreviewBar?, progress: Int, fromUser: Boolean)
        fun onScrubStop(previewBar: PreviewBar?)
    }

    fun interface PreviewLoader {
        fun loadPreview(currentPosition: Long, max: Long)
    }

    var isPreviewEnabled: Boolean
    val isShowingPreview: Boolean

    fun attach(previewFrameLayout: FrameLayout)
    fun attachPreviewView(previewFrameLayout: View)
    fun setPreviewLoader(loader: PreviewLoader?)
    fun addOnScrubListener(listener: OnScrubListener)
    fun removeOnScrubListener(listener: OnScrubListener)
}
