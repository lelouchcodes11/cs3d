package com.google.android.gms.cast.framework.media.uicontroller

import com.google.android.gms.cast.framework.media.RemoteMediaClient

open class UIController {
    open fun onMediaStatusUpdated() {}
    open fun onSessionConnected(castSession: com.google.android.gms.cast.framework.CastSession) {}
    open fun onSessionEnded() {}
    val remoteMediaClient: RemoteMediaClient? get() = null
}

open class UIMediaController {
    open fun bindViewToUIController(view: android.view.View?, controller: UIController?) {}
    open fun bindViewToUIController(view: android.view.View?, controller: UIController?, mode: Int) {}
}
