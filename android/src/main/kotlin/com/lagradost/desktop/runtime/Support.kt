package com.lagradost.desktop.runtime

import android.app.Notification
import android.content.res.Configuration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Application locale override (AppCompatDelegate.setApplicationLocales) */
object AppLocales {
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    @Volatile
    private var tags: String = ""

    fun get(): String = tags

    fun set(languageTags: String) {
        tags = languageTags
        val locale = languageTags.split(',').firstOrNull()?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        Configuration.current().locale = locale
        ContextImpl.invalidateResources()
        listeners.forEach { it(languageTags) }
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }
}

/** Posted notifications, rendered by the desktop UI (tray notifications / in-app list) */
object Notifications {
    data class Key(val tag: String?, val id: Int)

    fun interface Sink {
        fun onNotification(key: Key, notification: Notification?)
    }

    val active = ConcurrentHashMap<Key, Notification>()
    private val sinks = CopyOnWriteArrayList<Sink>()

    fun addSink(sink: Sink) {
        sinks.add(sink)
    }

    fun post(tag: String?, id: Int, notification: Notification) {
        val key = Key(tag, id)
        active[key] = notification
        sinks.forEach { it.onNotification(key, notification) }
    }

    fun cancel(tag: String?, id: Int) {
        val key = Key(tag, id)
        active.remove(key)
        sinks.forEach { it.onNotification(key, null) }
    }

    fun cancelAll() {
        for (k in active.keys.toList()) cancel(k.tag, k.id)
    }
}
