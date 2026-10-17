package com.lagradost.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.R
import com.lagradost.desktop.runtime.AndroidRuntime
import java.nio.charset.Charset

/**
 * The character set subtitle files are read with (Settings > Player > Subtitle encoding, and Subtitles > Encoding in the player), the same
 * choice as on the phone: Automatic (byte order mark, UTF-16 or UTF-8 when it is valid, else Windows-1252) or one of the code pages that old
 * Japanese, Chinese, Korean, Cyrillic, Arabic ... files are made in, where the automatic guess shows rubbish.
 */
object SubtitleEncoding {
    private val ctx get() = AndroidRuntime.context

    /** Changes with every choice: what shows the current name reads it and draws again */
    var version by mutableIntStateOf(0)
        private set

    // the phone's key; the literal is for a runtime without the resource table (unit tests)
    private fun key() = runCatching { ctx.getString(R.string.subtitles_encoding_key) }.getOrDefault("subtitles_encoding_key")

    /** (name, charset) of every entry the phone offers that this runtime can read; the first is Automatic with an empty charset */
    fun entries(): List<Pair<String, String>> {
        val names = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
        val values = ctx.resources.getStringArray(R.array.subtitles_encoding_values)
        return names.indices.mapNotNull { i ->
            val v = values.getOrNull(i) ?: return@mapNotNull null
            if (v.isNotEmpty() && !supported(v)) null else names[i] to v
        }
    }

    private fun supported(name: String): Boolean = runCatching { Charset.isSupported(name) }.getOrDefault(false)

    /** "" is Automatic */
    fun value(): String = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).getString(key(), null).orEmpty()

    fun label(value: String = value()): String = entries().firstOrNull { it.second == value }?.first ?: entries().first().first

    fun set(value: String) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(key(), value).apply()
        version++
    }

    /** The chosen charset; null for Automatic */
    fun charset(): Charset? = runCatching { value().takeIf { it.isNotEmpty() }?.let { Charset.forName(it) } }.getOrNull()
}
