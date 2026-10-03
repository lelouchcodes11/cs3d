package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey

/**
 * The OAuth client IDs of AniList, MyAnimeList and Simkl.
 *
 * The upstream app is built with its own registered clients (secrets of its release pipeline). This build has none: they used to be the
 * text "null", so the sign-in page said `client_id=null` / "Client authentication failed" and every request to the three services
 * (the MAL / Simkl calls carry the id too) failed. Every user registers their own free client once and enters it in Settings >
 * Accounts & security > Sign-in keys; a value baked in at build time (env / local.properties) is the fallback.
 */
object ApiKeys {
    private const val FOLDER = "sign_in_clients"

    enum class Key(val label: String, val secret: Boolean = false) {
        ANILIST_ID("AniList client ID"),
        MAL_ID("MyAnimeList client ID"),
        SIMKL_ID("Simkl client ID"),
        SIMKL_SECRET("Simkl client secret", secret = true),
    }

    private fun builtIn(key: Key): String? = when (key) {
        Key.ANILIST_ID -> BuildConfig.ANILIST_KEY
        Key.MAL_ID -> BuildConfig.MAL_KEY
        Key.SIMKL_ID -> BuildConfig.SIMKL_CLIENT_ID
        Key.SIMKL_SECRET -> BuildConfig.SIMKL_CLIENT_SECRET
    }.trim().takeIf { it.isNotEmpty() && it != "null" }

    /** What the user entered, if anything */
    fun saved(key: Key): String? = getKey<String>(FOLDER, key.name)?.trim()?.takeIf { it.isNotEmpty() }

    fun hasBuiltIn(key: Key): Boolean = builtIn(key) != null

    /** The value in use: the user's, else the one built in, else empty */
    fun get(key: Key): String = saved(key) ?: builtIn(key) ?: ""

    fun set(key: Key, value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isEmpty()) removeKey(FOLDER, key.name) else setKey(FOLDER, key.name, clean)
    }

    val aniList: String get() = get(Key.ANILIST_ID)
    val mal: String get() = get(Key.MAL_ID)
    val simklId: String get() = get(Key.SIMKL_ID)
    val simklSecret: String get() = get(Key.SIMKL_SECRET)

    /** The keys a service needs before it can sign in (Simkl's secret is only needed by the browser flow, not by the PIN) */
    fun missingFor(idPrefix: String, browserFlow: Boolean = false): List<Key> = when (idPrefix) {
        "anilist" -> listOf(Key.ANILIST_ID)
        "mal" -> listOf(Key.MAL_ID)
        "simkl" -> if (browserFlow) listOf(Key.SIMKL_ID, Key.SIMKL_SECRET) else listOf(Key.SIMKL_ID)
        else -> emptyList()
    }.filter { get(it).isEmpty() }
}
