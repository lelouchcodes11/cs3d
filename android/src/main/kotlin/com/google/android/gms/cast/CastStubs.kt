package com.google.android.gms.cast.framework

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.framework.media.RemoteMediaClient

/** No Cast session on desktop. The types exist so MainActivity and UI controllers can compile. */
open class Session

open class CastSession : Session() {
    val remoteMediaClient: RemoteMediaClient? = null
}

class SessionManager {
    val currentCastSession: CastSession? = null
    fun <T : Session> addSessionManagerListener(listener: SessionManagerListener<T>) {}
    fun <T : Session> removeSessionManagerListener(listener: SessionManagerListener<T>) {}
}

interface SessionManagerListener<T : Session> {
    fun onSessionStarting(session: T)
    fun onSessionStarted(session: T, sessionId: String)
    fun onSessionStartFailed(session: T, error: Int)
    fun onSessionEnding(session: T)
    fun onSessionEnded(session: T, error: Int)
    fun onSessionResuming(session: T, sessionId: String)
    fun onSessionResumed(session: T, wasSuspended: Boolean)
    fun onSessionResumeFailed(session: T, error: Int)
    fun onSessionSuspended(session: T, reason: Int)
}

class CastContext {
    val sessionManager: SessionManager = SessionManager()
    val castState: Int get() = CastState.NO_DEVICES_AVAILABLE

    companion object {
        @JvmStatic
        fun getSharedInstance(context: Context, executor: java.util.concurrent.Executor): CastTask = CastTask(CastContext())

        @JvmStatic
        fun getSharedInstance(context: Context): CastContext = CastContext()
    }
}

class CastTask(private val context: CastContext) {
    val isSuccessful: Boolean = false
    val result: CastContext get() = context

    fun addOnSuccessListener(listener: (CastContext) -> Unit): CastTask {
        listener(context)
        return this
    }

    fun addOnCompleteListener(listener: (CastTask) -> Unit): CastTask {
        listener(this)
        return this
    }
}

object CastButtonFactory {
    @JvmStatic
    fun setUpMediaRouteButton(context: Context, menu: android.view.Menu, menuId: Int) {}

    @JvmStatic
    fun setUpMediaRouteButton(context: Context, button: androidx.mediarouter.app.MediaRouteButton) {}
}

object R {
    object drawable {
        const val quantum_ic_refresh_white_24 = 0x7f080001
    }
}


object CastState {
    const val NO_DEVICES_AVAILABLE = 1
    const val NOT_CONNECTED = 2
    const val CONNECTING = 3
    const val CONNECTED = 4
}
