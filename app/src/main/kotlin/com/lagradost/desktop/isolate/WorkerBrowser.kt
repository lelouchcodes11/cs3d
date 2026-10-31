package com.lagradost.desktop.isolate

import android.util.Log
import android.webkit.RemoteBrowser
import android.webkit.RemoteBrowserFactory
import android.webkit.RemoteBrowsers
import android.webkit.WebView
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.mapper
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The sandbox process's side of the browser: a WebView an extension builds here has no engine in this process, its page is made by the app (see [RemoteBrowserHost]).
 * What the view is asked to do goes to the app one call after the other (a queue for each page keeps the order), what the page does comes back as events
 * ([RemoteBrowsers.deliver]). The window of an extension's page dialog is the app's too: the dialog is shown to the app, and a Cancel there ends the dialog here.
 */
internal class WorkerBrowser(private val rpc: () -> RpcPeer?, private val extension: () -> String?) : RemoteBrowserFactory {
    private class Dialogs {
        val shown = ConcurrentHashMap<String, WeakReference<android.app.Dialog>>()
    }

    private val dialogs = Dialogs()

    /** The dialogs that are on screen as far as the extension knows (until it dismisses them) */
    private val showing = java.util.Collections.synchronizedMap(java.util.WeakHashMap<android.app.Dialog, Boolean>())

    private inner class Page(val id: String) : RemoteBrowser {
        private val queue = Executors.newSingleThreadExecutor { r -> Thread(r, "wv-$id").also { it.isDaemon = true } }

        private fun body(op: String, args: JSONObject): JsonNode = mapper.readTree(JSONObject(args.toString()).put("id", id).put("op", op).toString())

        override fun op(op: String, args: JSONObject) {
            val request = body(op, args)
            queue.execute {
                val peer = rpc() ?: return@execute
                runCatching { runBlocking { peer.call("wv", request, 30_000) } }.onFailure { Log.w(TAG, "page $id: $op failed: $it") }
            }
        }

        override fun evaluate(script: String, callback: (String?) -> Unit) {
            val request = body("eval", JSONObject().put("script", script))
            queue.execute {
                val peer = rpc()
                val result = if (peer == null) null else runCatching { runBlocking { peer.call("wv", request, 90_000) } }.getOrNull()?.get("v")?.takeIf { !it.isNull }?.asText()
                callback(result)
            }
        }
    }

    override fun create(id: String, view: WebView): RemoteBrowser? {
        val peer = rpc() ?: return null
        val path = extension() ?: run {
            Log.w(TAG, "a page was asked for by code that belongs to no extension: " + Throwable().stackTrace.drop(2).take(16).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName })
            return null
        }
        val ok = runCatching { runBlocking { peer.call("wv", mapper.createObjectNode().put("id", id).put("op", "open").put("path", path), 20_000).get("ok")?.asBoolean() == true } }
            .onFailure { Log.w(TAG, "the app could not make a page: $it") }.getOrDefault(false)
        return if (ok) Page(id) else null
    }

    /** The app tells a page what it does; the answer is for an event that asks one (see [RemoteBrowsers.deliver]) */
    fun event(b: JsonNode): JsonNode? {
        val out = RemoteBrowsers.deliver(b.get("id").asText(), b.get("event").asText(), JSONObject(b.get("data")?.toString() ?: "{}")) ?: return null
        return mapper.readTree(out.toString())
    }

    // ---- an extension's page dialog: shown by the app ---------------------------------------------------------------------------------------------------

    /**
     * A dialog was shown. When it holds pages (an extension's Cloudflare check) the app opens a window with them (false: not shown, the extension's own Cancel is pressed).
     * An extension often builds its page after the dialog is on screen, so the dialog is looked at again for a while until a page is in it.
     */
    fun dialogShown(dialog: android.app.Dialog) {
        showing[dialog] = true
        Log.i(TAG, "a dialog was shown: ${dialog.javaClass.name}")
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var tries = 0
        fun look() {
            if (showing[dialog] != true) return
            val ids = RemoteBrowsers.idsIn(dialog.getWindow().getDecorView())
            if (ids.isEmpty()) {
                if (++tries < 80) main.postDelayed({ look() }, 250)
                return
            }
            val id = ids.first()
            if (dialogs.shown.containsKey(id)) return
            Log.i(TAG, "the dialog holds the page $id: the app is asked to show it")
            ids.forEach { dialogs.shown[it] = WeakReference(dialog) }
            val peer = rpc() ?: return
            // not on the main thread: the app answers when its window is up
            Thread({
                val shown = runCatching { runBlocking { peer.call("dialogShow", mapper.createObjectNode().put("id", id), 20_000).asBoolean() } }.getOrDefault(false)
                if (!shown) cancel(id)
            }, "wv-dialog").apply { isDaemon = true }.start()
        }
        look()
    }

    fun dialogDismissed(dialog: android.app.Dialog) {
        showing.remove(dialog)
        val ids = RemoteBrowsers.idsIn(dialog.getWindow().getDecorView()).filter { dialogs.shown.containsKey(it) }
        if (ids.isEmpty()) return
        ids.forEach { dialogs.shown.remove(it) }
        val peer = rpc() ?: return
        Thread({ runCatching { runBlocking { peer.call("dialogHide", mapper.createObjectNode().put("id", ids.first()), 10_000) } } }, "wv-dialog").apply { isDaemon = true }.start()
    }

    /** The user pressed Cancel in the app's window (or it was refused): the extension's dialog is cancelled as the phone's would be */
    fun cancel(id: String) {
        val dialog = dialogs.shown.remove(id)?.get() ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            com.lagradost.desktop.runtime.web.CloudflareGate.cancelBrowserDialog(dialog.getWindow().getDecorView()) { dialog.cancel() }
        }
    }

    companion object {
        private const val TAG = "WorkerBrowser"
    }
}
