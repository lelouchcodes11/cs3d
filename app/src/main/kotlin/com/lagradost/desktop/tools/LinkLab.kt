package com.lagradost.desktop.tools

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.ErrorEvent
import com.lagradost.cloudstream3.ui.player.StatusEvent
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.player.MpvPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Link lab: runs real extension links through the two ways a player can fetch them and compares:
 *  - `exo`: what Android's ExoPlayer does (OkHttp with the app's client, the link's headers, the default user agent);
 *  - `mpv`: the desktop player (the real MpvPlayer wrapper, headless: same headers, playlist server, options).
 * A link where `exo` works and `mpv` does not is a desktop problem; where both fail the source is dead.
 *
 * Usage: LinkLab <dataDir> <repos> <providerFilter,...> [maxLinksPerScenario] [queries separated by |]
 */
object LinkLab {
    data class Probe(val link: ExtractorLink, val provider: String, val scenario: String, var exo: String = "-", var mpv: String = "-")

    private val SCENARIOS = listOf("Inception", "Breaking Bad", "Naruto")

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        System.setProperty("java.awt.headless", "true")
        MpvPlayer.headless = true
        val dataDir = File(args.getOrNull(0) ?: "build/linklab-data")
        val repos = (args.getOrNull(1) ?: "phisher,megix").split(",")
        val filters = (args.getOrNull(2) ?: "streamplay,cinestream").lowercase().split(",")
        val maxLinks = args.getOrNull(3)?.toIntOrNull() ?: 60
        // queries separated by | ; + stands for a space (the gradle argument list is split at spaces)
        val scenarios = args.getOrNull(4)?.split("|")?.map { it.replace('+', ' ') } ?: SCENARIOS

        val activity = DesktopBootstrap.init(dataDir)
        DesktopBootstrap.onCreate(activity, loadPlugins = false, activityLifecycle = false)
        // crypto the extensions look up by name (tools/StringScan.java lists them): -Dlinklab.crypto=true
        if (System.getProperty("linklab.crypto") != null) {
            for (t in listOf("AES/CBC/NoPadding", "AES/CBC/PKCS5Padding", "AES/CBC/PKCS5PADDING", "AES/CBC/PKCS7Padding", "AES/CBC/PKCS7PADDING", "AES/ECB/PKCS7Padding", "AES/CFB/NoPadding", "AES/CTR/NoPadding", "AES/GCM/NoPadding", "DESede/CBC/PKCS5Padding", "RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "AES", "DESede", "RSA", "ChaCha20-Poly1305")) {
                val r = runCatching { val c = javax.crypto.Cipher.getInstance(t); "OK ${c.provider.name}" }.getOrElse { "MISSING ${it.javaClass.simpleName}" }
                println("cipher $t: $r")
            }
            for (m in listOf("HmacMD5", "HmacSHA1", "HmacSHA256", "HmacSHA512")) println("mac $m: " + runCatching { javax.crypto.Mac.getInstance(m).provider.name }.getOrElse { "MISSING" })
            for (m in listOf("PBKDF2WithHmacSHA1", "PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA512")) println("keyfactory $m: " + runCatching { javax.crypto.SecretKeyFactory.getInstance(m).provider.name }.getOrElse { "MISSING" })
            for (m in listOf("MD5", "SHA-1", "SHA-256", "SHA-512", "SHA3-256")) println("digest $m: " + runCatching { java.security.MessageDigest.getInstance(m).provider.name }.getOrElse { "MISSING" })
            for (m in listOf("SHA256withECDSA", "SHA256withRSA", "SHA1withRSA")) println("signature $m: " + runCatching { java.security.Signature.getInstance(m).provider.name }.getOrElse { "MISSING" })
            println("providers: " + java.security.Security.getProviders().joinToString { it.name })
            // a real round trip with the padding name Android accepts
            val key = javax.crypto.spec.SecretKeySpec(ByteArray(16) { it.toByte() }, "AES")
            val iv = javax.crypto.spec.IvParameterSpec(ByteArray(16))
            val enc = javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key, iv) }.doFinal("hello world".toByteArray())
            val dec = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(javax.crypto.Cipher.DECRYPT_MODE, key, iv) }.doFinal(enc)
            println("pkcs7 round trip: ${String(dec)}")
            kotlin.system.exitProcess(0)
        }
        // DNS timing of the app's resolver: -Dlinklab.dns=host1,host2
        System.getProperty("linklab.dns")?.let { hosts ->
            for (round in 1..2) for (h in hosts.split(",")) {
                val t = System.currentTimeMillis()
                val r = runCatching { app.baseClient.dns.lookup(h).joinToString { it.hostAddress ?: "?" } }.getOrElse { "ERR ${it.javaClass.simpleName}: ${it.message?.take(70)}" }
                println("dns round $round $h: ${System.currentTimeMillis() - t} ms -> $r")
            }
            kotlin.system.exitProcess(0)
        }
        // plain requests with the app's client, each with the error chain: -Dlinklab.get=url1,url2
        System.getProperty("linklab.get")?.let { urls ->
            for (u in urls.split(",")) {
                val t = System.currentTimeMillis()
                val r = runCatching { app.get(u, timeout = 20).let { "HTTP ${it.code} ${it.text.length} chars" } }.getOrElse { e ->
                    generateSequence<Throwable>(e) { it.cause }.take(4).joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message?.take(90)}" }
                }
                println("get $u: ${System.currentTimeMillis() - t} ms -> $r")
            }
            kotlin.system.exitProcess(0)
        }
        // one address: -Dlinklab.url=<address> [-Dlinklab.referer=<referer>]
        System.getProperty("linklab.url")?.let { url ->
            val link = com.lagradost.cloudstream3.utils.newExtractorLink("test", "test", url) {
                referer = System.getProperty("linklab.referer") ?: ""
                // -Dlinklab.headers=Name=value;;Name=value
                headers = System.getProperty("linklab.headers")?.split(";;")?.filter { it.contains('=') }?.associate { it.substringBefore('=') to it.substringAfter('=') } ?: emptyMap()
            }
            println("exo: ${exoProbe(link)}")
            println("mpv: ${mpvProbe(link)}")
            com.lagradost.desktop.runtime.LogBuffer.snapshot().filter { it.contains(" mpv:") || it.contains("NetProxy") || it.contains("HlsProxy") }.takeLast(40).forEach { println("   $it") }
            kotlin.system.exitProcess(0)
        }
        val status = ExtensionHarness.installPlugins(activity, repos, filters)
        status.toSortedMap().forEach { (k, v) -> println("plugin $k: $v") }

        val apis = com.lagradost.cloudstream3.APIHolder.apis.filter { api -> filters.any { api.name.lowercase().contains(it) } }
        println("providers: ${apis.joinToString { it.name }}")
        if (System.getProperty("linklab.mains") != null) { apis.forEach { println("main ${it.name} ${it.mainUrl}") }; kotlin.system.exitProcess(0) }

        // 1. collect links
        val collected = ConcurrentHashMap<String, List<Probe>>()
        val gather = Semaphore(2)
        apis.flatMap { api -> scenarios.map { api to it } }.map { (api, query) ->
            async(Dispatchers.IO) {
                gather.withPermit {
                    val key = "${api.name} / $query"
                    val started = System.currentTimeMillis()
                    val probes = collect(api, query, maxLinks)
                    collected[key] = probes
                    println("[collect] $key: ${probes.size} links in ${(System.currentTimeMillis() - started) / 1000} s")
                }
            }
        }.awaitAll()

        // 2. probe
        val all = collected.values.flatten()
        println("probing ${all.size} links")
        val limit = Semaphore(5)
        all.map { p ->
            async(Dispatchers.IO) {
                limit.withPermit {
                    // some links work once (single use tokens): the order decides who sees the failure; -Dlinklab.order=mpvfirst
                    if (System.getProperty("linklab.order") == "mpvfirst") {
                        p.mpv = mpvProbe(p.link)
                        p.exo = exoProbe(p.link)
                    } else {
                        p.exo = exoProbe(p.link)
                        p.mpv = mpvProbe(p.link)
                    }
                }
            }
        }.awaitAll()

        report(all, File(dataDir, "linklab-report-" + java.text.SimpleDateFormat("HHmmss").format(java.util.Date()) + ".txt"))
        File(dataDir, "linklab-log.txt").writeText(com.lagradost.desktop.runtime.LogBuffer.snapshot().joinToString("\n"))
        kotlin.system.exitProcess(0)
    }

    private suspend fun collect(api: MainAPI, query: String, maxLinks: Int): List<Probe> {
        val items = (APIRepository(api).search(query, 1) as? Resource.Success)?.value?.items.orEmpty()
        val first = items.firstOrNull() ?: run { println("   ${api.name} / $query: search found nothing"); return emptyList() }
        val response = runCatching { api.load(first.url) }.onFailure { println("   ${api.name} / $query: load failed: ${it.javaClass.simpleName} ${it.message?.take(80)}") }.getOrNull() ?: return emptyList()
        val data = when (response) {
            is MovieLoadResponse -> response.dataUrl
            is TvSeriesLoadResponse -> (response.episodes.firstOrNull { (it.season ?: 1) == 1 && (it.episode ?: 1) == 1 } ?: response.episodes.firstOrNull())?.data
            is AnimeLoadResponse -> response.episodes.values.firstOrNull()?.firstOrNull()?.data
            is LiveStreamLoadResponse -> response.dataUrl
            else -> null
        } ?: return emptyList()
        val links = ArrayList<ExtractorLink>()
        withTimeoutOrNull(150_000) {
            runCatching { api.loadLinks(data, false, {}, { synchronized(links) { links.add(it) } }) }
        }
        // a few of every source, so one chatty source does not hide the others
        val bySource = synchronized(links) { links.distinctBy { it.url }.groupBy { it.name } }
        val picked = ArrayList<ExtractorLink>()
        var round = 0
        while (picked.size < maxLinks && bySource.values.any { it.size > round }) {
            for (list in bySource.values) list.getOrNull(round)?.let { if (picked.size < maxLinks) picked.add(it) }
            round++
            if (round >= 3) break
        }
        return picked.map { Probe(it, api.name, query) }
    }

    /** What ExoPlayer would see: OkHttp with the app's client, headers of the link and the default user agent */
    private fun exoProbe(link: ExtractorLink): String = runCatching {
        val ua = link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: USER_AGENT
        val headers = (if (link.referer.isBlank()) emptyMap() else mapOf("referer" to link.referer)) + link.headers
        fun get(url: String, range: Boolean): Triple<Int, String, ByteArray> {
            val b = Request.Builder().url(url).header("User-Agent", ua)
            for ((k, v) in headers) if (!k.equals("User-Agent", true)) b.header(k, v)
            if (range) b.header("Range", "bytes=0-8191")
            val client = app.baseClient.newBuilder().callTimeout(java.time.Duration.ofSeconds(25)).build()
            client.newCall(b.build()).execute().use { r ->
                val bytes = r.body.source().let { src -> src.request(8192); src.buffer.readByteArray(minOf(src.buffer.size, 8192)) }
                return Triple(r.code, r.header("content-type") ?: "", bytes)
            }
        }
        val isPlaylist = link.url.contains(".m3u8", true) || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
        val (code, type, body) = get(link.url, !isPlaylist)
        val head = String(body.copyOf(minOf(body.size, 200)), Charsets.ISO_8859_1)
        when {
            code !in 200..299 -> "HTTP $code"
            head.startsWith("#EXTM3U") -> {
                // master -> media playlist -> first segment, as the player does (a variant list that loads says nothing about the media)
                var current = link.url
                var result = "m3u8 empty"
                for (depth in 0..2) {
                    val text = String(get(current, false).third, Charsets.UTF_8)
                    val entry = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: break
                    val abs = java.net.URI.create(current).resolve(entry).toString()
                    if (entry.contains(".m3u8")) { current = abs; continue }
                    val (c2, _, _) = get(abs, true)
                    result = if (c2 in 200..299) "OK m3u8" else "m3u8 segment HTTP $c2"
                    break
                }
                result
            }
            head.contains("<html", true) || head.contains("<!doctype", true) -> "HTML page"
            else -> "OK ${type.substringBefore(';')} ${body.size}b"
        }
    }.getOrElse { "ERR ${it.javaClass.simpleName}: ${it.message?.take(60)}" }

    /** The desktop player (headless) opens the link and plays a moment */
    private suspend fun mpvProbe(link: ExtractorLink): String {
        val player = MpvPlayer()
        val outcome = CompletableDeferred<String>()
        player.initCallbacks({ event ->
            when (event) {
                is StatusEvent -> if (event.isPlaying == CSPlayerLoading.IsPlaying) outcome.complete("opened")
                is ErrorEvent -> outcome.complete("ERR ${event.error.message}")
                else -> {}
            }
        })
        return try {
            player.loadPlayer(DesktopBootstrap.activity, false, link, null, null, emptySet(), null, true, false)
            val first = withTimeoutOrNull(30_000) { outcome.await() } ?: "TIMEOUT opening"
            if (first != "opened") first else {
                kotlinx.coroutines.delay(2500)
                val pos = player.getMpvPropertyString("time-pos")?.toDoubleOrNull() ?: 0.0
                val codec = player.getMpvPropertyString("video-format") ?: "-"
                val size = (player.getMpvPropertyString("video-params/w") ?: "?") + "x" + (player.getMpvPropertyString("video-params/h") ?: "?")
                if (pos > 0.4) "PLAY $codec $size t=${"%.1f".format(pos)}" else "OPENED but no progress ($codec $size t=$pos)"
            }
        } catch (t: Throwable) {
            "EXC ${t.javaClass.simpleName}: ${t.message?.take(60)}"
        } finally {
            runCatching { player.release() }
        }
    }

    private fun report(all: List<Probe>, out: File) {
        val sb = StringBuilder()
        fun exoOk(p: Probe) = p.exo.startsWith("OK")
        fun mpvOk(p: Probe) = p.mpv.startsWith("PLAY")
        sb.appendLine("links: ${all.size}; exo ok ${all.count(::exoOk)}; mpv plays ${all.count(::mpvOk)}")
        sb.appendLine("exo ok and mpv fails: ${all.count { exoOk(it) && !mpvOk(it) }}; exo fails and mpv plays: ${all.count { !exoOk(it) && mpvOk(it) }}")
        sb.appendLine()
        for (p in all.sortedWith(compareBy({ it.provider }, { it.scenario }, { it.link.name }))) {
            val host = runCatching { java.net.URI(p.link.url).host }.getOrNull() ?: "?"
            sb.appendLine("${p.provider} | ${p.scenario} | ${p.link.name.take(28)} | ${p.link.type} | $host | exo=${p.exo} | mpv=${p.mpv}")
        }
        out.writeText(sb.toString())
        File(out.parentFile, out.nameWithoutExtension + "-links.tsv").writeText(
            all.joinToString("\n") { p ->
                listOf(p.provider, p.scenario, p.link.name, p.link.type, p.link.referer, p.link.url, p.link.headers.entries.joinToString(";;") { it.key + "=" + it.value }, p.exo, p.mpv).joinToString("\t")
            },
        )
        println(sb.lineSequence().take(3).joinToString("\n"))
        println("report: ${out.absolutePath}")
    }
}
