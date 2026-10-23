package com.lagradost.desktop.player

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A ClearKey channel that carries a license address instead of the key (the Tata Play channels of the DRMlive lists: `licenseUrl` = `.../tp/tpck?id=956`).
 * ExoPlayer asks that address for the keys itself; mpv has no license client, so the app does: it asks the way the phone does (a POST with the key ids
 * of the manifest as `{"kids":[...],"type":"temporary"}`, then a plain GET), reads the keys from the answer and hands them to ffmpeg's CENC decryption
 * like a link with a key (MpvPlayer.loadPlayer).
 *
 * The answer is a JWK set (`{"keys":[{"kty":"oct","k":"<base64url key>","kid":"<base64url key id>"}]}`) or, for the simple servers, a plain map of key id to key.
 * Both are given as hex or base64url; [parse] returns hex pairs (key id, key).
 */
object ClearKeyLicense {
    private val cache = ConcurrentHashMap<String, List<Pair<String, String>>>()

    /** The keys fetched earlier for [licenseUrl] (kept for the run: a channel is opened again and again while zapping) */
    fun cached(licenseUrl: String): List<Pair<String, String>>? = cache[licenseUrl]

    fun remember(licenseUrl: String, keys: List<Pair<String, String>>) {
        if (keys.isNotEmpty()) cache[licenseUrl] = keys
    }

    private fun hexOf(text: String): String? {
        val t = text.trim().replace("-", "")
        if (t.length == 32 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return t.lowercase()
        return runCatching {
            val bytes = java.util.Base64.getUrlDecoder().decode(text.trim().replace('+', '-').replace('/', '_').trimEnd('='))
            if (bytes.size == 16) bytes.joinToString("") { "%02x".format(it) } else null
        }.getOrNull()
    }

    /** (key id, key) pairs, both hex, from a license answer; empty when it holds none */
    fun parse(body: String): List<Pair<String, String>> {
        val text = body.trim()
        val out = ArrayList<Pair<String, String>>()
        fun add(kid: String?, key: String?) {
            val k = key?.let { hexOf(it) } ?: return
            out.add((kid?.let { hexOf(it) } ?: "") to k)
        }
        runCatching {
            if (text.startsWith("[")) {
                val a = JSONArray(text)
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { o -> add(o.optString("kid").ifEmpty { o.optString("keyid") }, o.optString("k").ifEmpty { o.optString("key") }) }
            } else {
                val o = JSONObject(text)
                val keys = o.optJSONArray("keys")
                if (keys != null) {
                    for (i in 0 until keys.length()) keys.optJSONObject(i)?.let { k -> add(k.optString("kid"), k.optString("k").ifEmpty { k.optString("key") }) }
                } else if (o.has("k") || o.has("key")) {
                    add(o.optString("kid").ifEmpty { o.optString("keyid") }, o.optString("k").ifEmpty { o.optString("key") })
                } else {
                    for (name in o.keys()) {
                        val v = o.opt(name)
                        if (v is String) add(name, v)
                    }
                }
            }
        }
        return out
    }

    /** The key ids (hex) the manifest names (`cenc:default_KID`) */
    fun manifestKids(manifest: String): List<String> =
        Regex("""default_KID\s*=\s*["']([0-9a-fA-F-]{32,36})["']""").findAll(manifest).mapNotNull { hexOf(it.groupValues[1]) }.distinct().toList()

    private fun kidToBase64Url(hex: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() })

    /** The request body ExoPlayer's ClearKey sends */
    fun requestBody(kidsHex: List<String>): String =
        "{\"kids\":" + JSONArray(kidsHex.map { kidToBase64Url(it) }).toString() + ",\"type\":\"temporary\"}"

    /**
     * Asks the license server for the keys of the channel. [manifestUrl] (the DASH manifest, fetched with the same [headers]) names the key ids to ask for.
     * Blocking: call it off the UI thread. Returns the pairs, empty when the server gave none.
     */
    fun fetch(licenseUrl: String, manifestUrl: String?, headers: Map<String, String>, client: OkHttpClient = com.lagradost.cloudstream3.app.baseClient): List<Pair<String, String>> {
        val http = client.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()
        fun request(url: String, body: String?): String? = runCatching {
            val b = Request.Builder().url(url)
            for ((k, v) in headers) if (!k.equals("Content-Length", true) && !k.equals("Content-Type", true) && !k.equals("Host", true)) com.lagradost.desktop.net.RawHeaders.set(b, k, v)
            if (body != null) b.post(body.toRequestBody("application/json".toMediaType()))
            http.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body.string() else null }
        }.getOrNull()
        val kids = manifestUrl?.let { request(it, null) }?.let { manifestKids(it) }.orEmpty()
        // the way the phone asks first (a POST naming the key ids), then the plain address: the simple servers answer the same to both
        val tries: List<String?> = if (kids.isNotEmpty()) listOf(requestBody(kids), null) else listOf(null, requestBody(emptyList()))
        for (body in tries) {
            val keys = request(licenseUrl, body)?.let { parse(it) }.orEmpty()
            if (keys.isNotEmpty()) {
                // the keys of this channel's key ids first (a server may send more)
                return keys.sortedBy { (kid, _) -> if (kid in kids) 0 else 1 }
            }
        }
        return emptyList()
    }
}
