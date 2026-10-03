package com.github.rubensousa.previewseekbar.media3

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.media3.ui.DefaultTimeBar
import com.github.rubensousa.previewseekbar.PreviewBar

open class PreviewTimeBar(context: Context, attrs: AttributeSet? = null) : DefaultTimeBar(context, attrs), PreviewBar {
    override var isPreviewEnabled: Boolean = false
    override val isShowingPreview: Boolean = false

    override fun attach(previewFrameLayout: FrameLayout) {}
    override fun attachPreviewView(previewFrameLayout: View) {}
    override fun setPreviewLoader(loader: PreviewBar.PreviewLoader?) {}
    override fun addOnScrubListener(listener: PreviewBar.OnScrubListener) {}
    override fun removeOnScrubListener(listener: PreviewBar.OnScrubListener) {}
}
