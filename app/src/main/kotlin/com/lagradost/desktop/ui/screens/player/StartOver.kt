package com.lagradost.desktop.ui.screens.player

/** "Start from beginning": the next time the player opens the video (episode) with [id] it starts at 0:00 instead of where it was left */
object StartOver {
    @Volatile private var pending: Pair<Int, Long>? = null

    fun request(id: Int) { pending = id to System.currentTimeMillis() }

    /** Whether [id] is the requested video (asked for in the last minute); the request is used up */
    fun take(id: Int?): Boolean {
        val p = pending ?: return false
        if (id == null || p.first != id || System.currentTimeMillis() - p.second > 60_000) return false
        pending = null
        return true
    }
}
