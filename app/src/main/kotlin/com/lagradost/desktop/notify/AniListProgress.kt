package com.lagradost.desktop.notify

import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncRepo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the signed-in AniList account is watching (episodes seen of each title in progress), read from the account's library. The Schedule page
 * uses it without the viewer doing anything: the upcoming card shows these titles as "in your list" and the notification cards say how far
 * behind the viewer is. Nothing when no AniList account is signed in.
 */
object AniListProgress {
    class Entry(val anilistId: Int, val name: String, val seen: Int, val total: Int?)

    private const val TAG = "AniListProgress"
    private const val CACHE_MS = 20L * 60 * 1000

    @Volatile private var cached: Pair<Long, Map<Int, Entry>>? = null

    /** The cached answer without asking AniList (empty before the first [refresh]) */
    val known: Map<Int, Entry> get() = cached?.second ?: emptyMap()

    /** Titles in progress on the AniList account by AniList id; an empty map when nobody is signed in or AniList does not answer */
    suspend fun refresh(force: Boolean = false): Map<Int, Entry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.let { (at, m) -> if (!force && now - at < CACHE_MS) return@withContext m }
        val repo = SyncRepo(AccountManager.aniListApi)
        val library = runCatching { repo.library().getOrNull() }.getOrNull()
        val out = HashMap<Int, Entry>()
        library?.allLibraryLists?.forEach { list ->
            list.items.forEach { item ->
                val id = item.syncId.toIntOrNull() ?: return@forEach
                val seen = item.episodesCompleted ?: return@forEach
                val total = item.episodesTotal?.takeIf { it > 0 }
                // "in progress": started and not finished (the Completed and Planning lists drop out on their own)
                if (seen > 0 && (total == null || seen < total)) out[id] = Entry(id, item.name, seen, total)
            }
        }
        if (library != null) Log.i(TAG, "${out.size} titles in progress on AniList")
        cached = now to out
        out
    }
}
