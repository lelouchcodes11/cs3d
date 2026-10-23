package com.lagradost.desktop.net

import androidx.compose.runtime.mutableStateMapOf
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject

/**
 * Which of the services the app talks to (AniList, MyAnimeList, Simkl, Kitsu) say that they are down, refuse the app or no longer accept the sign-in.
 *
 * On 2026-10-21 AniList answered every request with HTTP 403 "The AniList API has been temporarily disabled due to severe stability issues" and
 * MyAnimeList with "maintenance"; the app read both as "nothing there" (an empty library, "nothing is scheduled today") or as a parser error. This
 * interceptor looks at what those hosts answer, without changing it, and keeps the service's own words in [notes] for the pages to show. A later good
 * answer from the same service removes the note again.
 */
object ServiceOutage : Interceptor {
    class Note(val message: String, val since: Long = System.currentTimeMillis())

    /** service name (as the sync providers are named: "AniList", "MAL", "Simkl", "Kitsu") -> what it said */
    val notes = mutableStateMapOf<String, Note>()

    /** the same, readable from any thread at once (the state map above is written on the UI thread, a moment later) */
    private val latest = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** the plain sentence for [service], or null while it answers */
    fun message(service: String): String? = latest[service]

    private fun serviceOf(host: String): String? = when (host) {
        "graphql.anilist.co" -> "AniList"
        "api.myanimelist.net", "myanimelist.net" -> "MAL"
        "api.simkl.com" -> "Simkl"
        "kitsu.io", "kitsu.app" -> "Kitsu"
        else -> null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val service = serviceOf(chain.request().url.host) ?: return response
        runCatching {
            val body = if (response.body != null) response.peekBody(4096).string() else ""
            val why = reason(service, response.code, body)
            if (why != null) record(service, why) else if (response.isSuccessful) forget(service)
        }
        return response
    }

    /** What the service said, in a sentence, when [code] and [body] are what a service that is down, full or no longer trusting the sign-in answers; else null */
    fun reason(service: String, code: Int, body: String): String? {
        val text = body.trimStart()
        if (code == 404) return null
        if (text.startsWith("<")) {
            // a web page where data was asked for: a maintenance page, a Cloudflare page, an error page
            return if (code >= 500 || code == 403 || code == 429 || text.contains("maintenance", ignoreCase = true))
                "$service is not answering right now (it sent a web page, HTTP $code)." else null
        }
        if (text.startsWith("{")) {
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json != null) {
                // AniList (GraphQL): {"errors":[{"message":"...","status":403}],"data":null}
                val first = json.optJSONArray("errors")?.optJSONObject(0)
                if (first != null && (!json.has("data") || json.isNull("data"))) {
                    val status = first.optInt("status")
                    if (status == 404) return null
                    val said = first.optString("message").trim()
                    if (said.contains("Invalid token", ignoreCase = true)) return "$service no longer accepts this sign-in. Sign in again in Settings > Accounts."
                    if (said.isNotEmpty()) return "$service says: $said"
                }
                // MyAnimeList: {"error":"maintenance","message":"..."}
                val err = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
                if (err != null && !json.has("data")) {
                    if (err == "not_found") return null
                    val said = json.optString("message").trim()
                    return when {
                        err == "invalid_token" || err == "invalid_grant" -> "$service no longer accepts this sign-in. Sign in again in Settings > Accounts."
                        said.isNotEmpty() -> "$service says: $said"
                        else -> "$service answered with an error ($err)."
                    }
                }
            }
        }
        if (code in 502..504) return "$service is not answering right now (HTTP $code)."
        return null
    }

    /** Throws the service's own words when [json] is its answer without data, so a page can say "AniList says ..." instead of "nothing found" */
    fun requireData(service: String, json: JSONObject) {
        if (json.has("data") && !json.isNull("data")) return
        val why = reason(service, 0, json.toString()) ?: message(service) ?: return
        throw IllegalStateException(why)
    }

    private fun record(service: String, message: String) {
        latest[service] = message
        // written on the UI thread: the pages read these in composition
        java.awt.EventQueue.invokeLater {
            val old = notes[service]
            if (old == null || old.message != message) notes[service] = Note(message)
        }
    }

    private fun forget(service: String) {
        latest.remove(service)
        if (notes.containsKey(service)) java.awt.EventQueue.invokeLater { notes.remove(service) }
    }
}
