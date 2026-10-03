package com.google.android.gms.cast.framework.media.widget

import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.uicontroller.UIMediaController

open class ExpandedControllerActivity : AppCompatActivity() {
    open val uiMediaController: UIMediaController = UIMediaController()
    open fun onSessionConnected(castSession: CastSession?) {}
    open fun getButtonImageViewAt(index: Int): ImageView = ImageView(this)
}
