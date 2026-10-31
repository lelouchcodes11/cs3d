package com.lagradost.desktop.isolate

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * An extractor (a video host's link reader) that an extension in the sandbox registered, as the app sees it: the app's own lookups (a trailer, an extension that runs in
 * the app and meets that host) find it in the list of extractors, and the work is done in the sandbox, where the extension's code is.
 */
class RemoteExtractorApi(meta: Meta, path: String) : ExtractorApi() {
    class Meta(val name: String, val mainUrl: String, val requiresReferer: Boolean) {
        companion object {
            fun from(node: JsonNode) = Meta(node.get("name").asText(), node.get("mainUrl").asText(), node.get("requiresReferer")?.asBoolean() == true)
        }
    }

    override val name: String = meta.name
    override val mainUrl: String = meta.mainUrl
    override val requiresReferer: Boolean = meta.requiresReferer

    init {
        sourcePlugin = path
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val body = mapper.createObjectNode().put("path", sourcePlugin).put("name", name).put("url", url).also { if (referer != null) it.put("referer", referer) }
        IsolatedHost.call("extract", body, 120_000) { op, node ->
            when (op) {
                "link" -> IsolationSerde.read(node, ExtractorLink::class.java)?.let(callback) ?: Log.w("RemoteExtractorApi", "a link of $name could not be read")
                "sub" -> IsolationSerde.plain(node, SubtitleFile::class.java)?.let(subtitleCallback)
            }
        }
    }
}
