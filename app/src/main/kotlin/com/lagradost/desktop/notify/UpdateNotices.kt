package com.lagradost.desktop.notify

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * New episodes of the titles the person subscribed to (the bell on a title page).
 *
 * The Android app has a background worker for this; on the desktop nothing ran it, so "Get notified about new episodes" saved the
 * subscription and told nobody. This checks every subscribed title now and then while the app is open (first a little after the start,
 * then every few hours), keeps what it found in a list the Schedule page shows (read / unread) and raises one silent tray notification
 * per new episode when the window is not the one in front.
 */
object UpdateNotices {
    private const val TAG = "UpdateNotices"
    private const val MAX_KEPT = 120
    private val CHECK_EVERY_MS = 3L * 60 * 60 * 1000
    private const val FIRST_CHECK_MS = 90_000L

    class Notice(
        val key: String,
        val title: String,
        val text: String,
        val apiName: String,
        val url: String,
        val poster: String?,
        val posterHeaders: Map<String, String>?,
        val time: Long,
        val read: Boolean,
        val episode: Int?,
    )

    /** Newest first. Compose state: the Schedule page and the badge on the navigation follow it */
    val items = mutableStateListOf<Notice>()

    @Volatile var checking = false
        private set

    val unread: Int get() = items.count { !it.read }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private val file: File get() = File(AndroidRuntime.dataDir, "notices.json")

    fun start() {
        if (started) return
        started = true
        scope.launch {
            load()
            delay(FIRST_CHECK_MS)
            while (true) {
                runCatching { check() }.onFailure { Log.w(TAG, "check failed: $it") }
                delay(CHECK_EVERY_MS)
            }
        }
    }

    /** The "Check now" button */
    fun checkNow() {
        if (checking) return
        scope.launch { runCatching { check() }.onFailure { Log.w(TAG, "check failed: $it") } }
    }

    fun markRead(key: String) {
        val i = items.indexOfFirst { it.key == key }
        if (i < 0 || items[i].read) return
        items[i] = items[i].copy(read = true)
        save()
    }

    fun markAllRead() {
        if (items.none { !it.read }) return
        for (i in items.indices) if (!items[i].read) items[i] = items[i].copy(read = true)
        save()
    }

    fun remove(key: String) {
        items.removeAll { it.key == key }
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun Notice.copy(read: Boolean) = Notice(key, title, text, apiName, url, poster, posterHeaders, time, read, episode)

    // ------------------------------------------------------------------ checking

    private suspend fun check() {
        val subscriptions = DataStoreHelper.getAllSubscriptions()
        if (subscriptions.isEmpty()) return
        checking = true
        try {
            coroutineScope {
                // a few at a time: every one is a request to an extension's site
                subscriptions.chunked(4).forEach { chunk -> chunk.map { s -> async { runCatching { checkOne(s) }.onFailure { Log.w(TAG, "${s.name}: $it") } } }.awaitAll() }
            }
        } finally {
            checking = false
        }
    }

    private suspend fun checkOne(saved: DataStoreHelper.SubscribedData) {
        val id = saved.id ?: return
        val api = APIHolder.getApiFromNameNull(saved.apiName) ?: return
        val response = withTimeoutOrNull(60_000) { api.load(saved.url) as? EpisodeResponse } ?: return
        val ctx = DesktopBootstrap.activityOrNull()
        val dub = DataStoreHelper.getDub(id) ?: if (ctx?.getApiDubstatusSettings()?.contains(DubStatus.Dubbed) == true) DubStatus.Dubbed else DubStatus.Subbed
        val latest = response.getLatestEpisodes()
        val preferred = latest[dub]
        val (isNew, episode) = if (preferred != null) {
            (preferred > (saved.lastSeenEpisodeCount[dub] ?: Int.MIN_VALUE)) to preferred
        } else {
            val e = latest[DubStatus.None] ?: Int.MIN_VALUE
            (e > (saved.lastSeenEpisodeCount[DubStatus.None] ?: Int.MIN_VALUE)) to e
        }
        DataStoreHelper.updateSubscribedData(id, saved, response)
        if (!isNew || episode == Int.MIN_VALUE) return
        val key = "$id:$episode"
        if (items.any { it.key == key }) return
        val notice = Notice(
            key, saved.name, "Episode $episode is out", saved.apiName, saved.url, saved.posterUrl, saved.posterHeaders,
            System.currentTimeMillis(), false, episode,
        )
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            items.add(0, notice)
            while (items.size > MAX_KEPT) items.removeAt(items.size - 1)
        }
        save()
        // the window in front already shows it (the badge), a tray balloon would only be noise
        val inFront = runCatching { com.lagradost.desktop.ui.DesktopUiHost.window?.isActive == true }.getOrDefault(false)
        if (!inFront) {
            val n = android.app.Notification()
            n.title = saved.name
            n.text = com.lagradost.desktop.i18n.Tr.t("Episode $episode is out")
            Notifications.post("subscription", id, n)
        }
    }

    // ------------------------------------------------------------------ storage

    private fun save() {
        runCatching {
            val arr = JSONArray()
            for (n in items.toList()) arr.put(JSONObject().apply {
                put("key", n.key); put("title", n.title); put("text", n.text); put("api", n.apiName); put("url", n.url)
                put("poster", n.poster ?: JSONObject.NULL); put("time", n.time); put("read", n.read); put("ep", n.episode ?: JSONObject.NULL)
                n.posterHeaders?.let { h -> put("headers", JSONObject(h)) }
            })
            file.writeText(arr.toString())
        }.onFailure { Log.w(TAG, "save: $it") }
    }

    private suspend fun load() {
        val list = runCatching {
            if (!file.isFile) return@runCatching emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val h = o.optJSONObject("headers")?.let { j -> j.keys().asSequence().associateWith { k -> j.optString(k) } }
                Notice(
                    o.optString("key"), o.optString("title"), o.optString("text"), o.optString("api"), o.optString("url"),
                    o.optString("poster").takeIf { it.isNotBlank() && it != "null" }, h, o.optLong("time"), o.optBoolean("read"),
                    if (o.isNull("ep")) null else o.optInt("ep"),
                )
            }
        }.getOrDefault(emptyList())
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            items.clear()
            items.addAll(list)
        }
    }
}
