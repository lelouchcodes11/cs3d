package com.lagradost.desktop.core

import android.util.Log
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What playback tells the trackers (AniList, MAL, Kitsu, Simkl): the episodes watched and the list status. A change that could not be sent
 * (the service was down, the network dropped as the episode ended) was lost: it was tried once and the user's list stayed behind. Such a
 * change is kept here (on disk, per account) and tried again every few minutes and after a restart, for a week at most.
 *
 * A change is kept as what it means ("episode 7 is watched"), not as the status it made: it is applied to the status the tracker has when it
 * is sent again, so nothing the user changed meanwhile is overwritten.
 */
object SyncRetry {
    private const val TAG = "SyncRetry"
    private const val RETRY_MS = 5 * 60_000L
    private const val KEEP_MS = 7 * 24 * 3600_000L

    const val MAX_EPISODE = "maxEpisode"
    const val STARTED = "started"
    const val FINISHED = "finished"

    data class Op(
        val prefix: String,
        val id: String,
        val kind: String,
        val episode: Int = 0,
        val includeUnlisted: Boolean = false,
        /** The episode count of the title (when the tracker does not say) */
        val total: Int? = null,
        /** The account it was made for: another account (profile switch, sign-out) does not get it */
        val user: Int? = null,
        val at: Long = System.currentTimeMillis(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var pending: MutableList<Op>? = null
    private var loop: Job? = null

    private fun file() = File(AndroidRuntime.dataDir, "sync-pending.json")

    /** [status] changed by [op]; null when there is nothing to change */
    fun change(op: Op, status: SyncAPI.AbstractSyncStatus): SyncAPI.AbstractSyncStatus? = when (op.kind) {
        MAX_EPISODE -> {
            val watched = status.watchedEpisodes
            // never more than the title has: the count over all seasons (13 for S2 E1) on a tracker entry of one 12-episode season was
            // refused, and the refused change was tried again every few minutes for a week
            val max = (status.maxEpisodes ?: op.total)?.takeIf { it > 0 }
            val episode = if (max != null) minOf(op.episode, max) else op.episode
            when {
                watched == null || episode <= watched -> null
                // a title on no list: MyAnimeList and Simkl put it under Watching, AniList was sent a removal that failed and was tried again
                // for a week. Only with "add every title I play" (as the start of playback does)
                status.status == SyncWatchType.NONE -> if (op.includeUnlisted) status.also { it.watchedEpisodes = episode; it.status = SyncWatchType.WATCHING } else null
                else -> status.also { it.watchedEpisodes = episode }
            }
        }
        // Watching when a title on a list (Planning, On hold, Dropped) is started; one on no list only when [includeUnlisted]
        STARTED -> when (status.status) {
            SyncWatchType.NONE -> if (op.includeUnlisted) status.also { it.status = SyncWatchType.WATCHING } else null
            SyncWatchType.PLANTOWATCH, SyncWatchType.ONHOLD, SyncWatchType.DROPPED -> status.also { it.status = SyncWatchType.WATCHING }
            else -> null
        }
        FINISHED -> {
            val max = status.maxEpisodes ?: op.total
            when {
                status.status == SyncWatchType.NONE && !op.includeUnlisted -> null
                max == null || max <= 0 || op.episode < max || status.status == SyncWatchType.COMPLETED -> null
                else -> status.also { it.watchedEpisodes = max; it.status = SyncWatchType.COMPLETED }
            }
        }
        else -> null
    }

    /** Sends [op] now; kept for later when the tracker could not be reached */
    suspend fun submit(op: Op) {
        val repo = AccountManager.syncApis.firstOrNull { it.idPrefix == op.prefix } ?: return
        val withUser = op.copy(user = repo.authUser()?.id ?: return)
        if (!send(withUser)) keep(withUser)
    }

    /** true: done (or nothing to do, or no longer possible); false: the tracker could not be reached */
    private suspend fun send(op: Op): Boolean {
        val repo = AccountManager.syncApis.firstOrNull { it.idPrefix == op.prefix } ?: return true
        val user = repo.authUser() ?: return true
        if (op.user != null && op.user != user.id) return true
        val status = repo.status(op.id).getOrElse { Log.w(TAG, "${repo.name}: status of ${op.id} not read: $it"); return false } ?: return false
        val changed = change(op, status) ?: return true
        Log.i(TAG, "${repo.name} ${op.id}: ${op.kind} ${op.episode}")
        return repo.updateStatus(op.id, changed).getOrElse { Log.w(TAG, "${repo.name}: update of ${op.id} not sent: $it"); false }
    }

    private fun keep(op: Op) {
        synchronized(lock) {
            val list = load()
            // a newer change of the same kind replaces the older one (episode 8 watched covers episode 7)
            list.removeAll { it.prefix == op.prefix && it.id == op.id && it.kind == op.kind && it.user == op.user }
            list += op
            save(list)
            Log.i(TAG, "kept for later (${list.size} waiting): ${op.prefix} ${op.id} ${op.kind} ${op.episode}")
        }
        resume()
    }

    /** Starts trying the kept changes again (at start-up, from the app's window: not in the extension sandbox) */
    fun resume() {
        synchronized(lock) {
            if (loop?.isActive == true || load().isEmpty()) return
            loop = scope.launch {
                while (true) {
                    delay(RETRY_MS)
                    val now = System.currentTimeMillis()
                    val todo = synchronized(lock) { load().toList() }
                    if (todo.isEmpty()) break
                    val done = todo.filter { now - it.at > KEEP_MS || runCatching { send(it) }.getOrDefault(false) }
                    val left = synchronized(lock) {
                        val list = load()
                        list.removeAll { it in done }
                        save(list)
                        list.size
                    }
                    if (done.isNotEmpty()) Log.i(TAG, "${done.size} tracker change(s) sent or expired, $left waiting")
                    if (left == 0) break
                }
            }
        }
    }

    private fun load(): MutableList<Op> {
        pending?.let { return it }
        val list = ArrayList<Op>()
        runCatching {
            val f = file()
            if (f.isFile) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    list += Op(
                        o.getString("prefix"), o.getString("id"), o.getString("kind"), o.optInt("episode"), o.optBoolean("includeUnlisted"),
                        o.optInt("total", -1).takeIf { it >= 0 }, o.optInt("user", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }, o.optLong("at"),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "pending changes not read: $it") }
        pending = list
        return list
    }

    private fun save(list: List<Op>) {
        val f = file()
        if (list.isEmpty()) { f.delete(); return }
        val a = JSONArray()
        for (op in list) a.put(
            JSONObject().put("prefix", op.prefix).put("id", op.id).put("kind", op.kind).put("episode", op.episode)
                .put("includeUnlisted", op.includeUnlisted).put("at", op.at).apply { op.total?.let { put("total", it) }; op.user?.let { put("user", it) } }
        )
        runCatching { f.writeTextAtomic(a.toString()) }.onFailure { Log.w(TAG, "pending changes not saved: $it") }
    }
}
