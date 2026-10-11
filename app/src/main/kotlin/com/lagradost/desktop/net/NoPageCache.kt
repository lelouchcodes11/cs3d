package com.lagradost.desktop.net

import okhttp3.Interceptor
import okhttp3.Response
import java.io.File

/**
 * Web pages are not kept in the HTTP cache on disk. Extensions load the pages of streaming sites, and those pages are full of advertising
 * scripts and redirects; a copy of such a page in the cache folder is what antivirus programs find when they scan the folder (a user's report:
 * Windows Security named `cache\http_cache\<hash>.1.tmp` "Trojan:HTML/Redirector"). The page was never run by the app, but the message is
 * frightening and the file is of no use: only data (JSON, images, subtitles ...) is cached, which is what makes repeated requests quick.
 *
 * A network interceptor sees the response before the cache stores it, so a "no-store" it adds keeps the cache from writing the body.
 */
object NoPageCache : Interceptor {
    private val pageTypes = listOf("text/html", "application/xhtml", "javascript", "ecmascript", "image/svg", "application/x-shockwave-flash", "text/vbscript", "text/event-stream")

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val type = response.header("Content-Type")?.lowercase().orEmpty()
        if (pageTypes.none { type.contains(it) }) return response
        return response.newBuilder().header("Cache-Control", "no-store").removeHeader("Expires").removeHeader("ETag").removeHeader("Last-Modified").build()
    }

    /**
     * Once per version of this change: what an older version put into the cache folder is removed (pages among it). The folder is made again
     * by the cache; entries are only a speed-up.
     */
    fun purgeOldPages(directory: File, done: () -> Boolean, markDone: () -> Unit) {
        runCatching {
            if (done()) return
            directory.deleteRecursively()
            markDone()
        }
    }
}
