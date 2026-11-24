package com.lagradost.desktop.player

/**
 * How well a subtitle's release name fits the video that plays. Online subtitles are named after the release they were timed on
 * ("Interstellar.2014.IMAX.1080p.BluRay.x264-SPARKS"); one made for the same release group or the same kind of source (BluRay, WEB, HDTV,
 * the IMAX cut) is far more often in sync than one for another encode. Used as one more point in the automatic choice (PlayerSession).
 */
object SubtitleRelease {
    private val kinds = listOf(
        "bluray" to setOf("bluray", "bdrip", "brrip", "bdremux", "bd"),
        "web" to setOf("webdl", "webrip", "web", "amzn", "nf", "dsnp", "hmax", "atvp"),
        "hdtv" to setOf("hdtv", "pdtv"),
        "dvd" to setOf("dvdrip", "dvd", "dvdscr"),
        "cam" to setOf("cam", "hdcam", "ts", "telesync", "hdts"),
    )
    private val cuts = setOf("imax", "extended", "unrated", "directors", "remastered", "theatrical")
    private val notGroups = setOf("x264", "x265", "h264", "h265", "hevc", "avc", "aac", "ac3", "dts", "mkv", "mp4", "srt", "vtt", "1080p", "720p", "2160p", "480p", "web", "dl")

    private fun tokens(text: String): List<String> =
        text.lowercase().replace("web-dl", "webdl").split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun kindOf(t: List<String>): String? = kinds.firstOrNull { (_, words) -> t.any { it in words } }?.first

    /** The release group: the word after the last "-" of a scene name ("...x264-SPARKS" -> sparks), null when there is none */
    internal fun groupOf(text: String): String? {
        val base = text.substringAfterLast('/').substringBefore('?').trim().replace(Regex("""\.(mkv|mp4|avi|ts|m3u8|mpd|webm|mov|srt|vtt|ass|ssa|sub)$""", RegexOption.IGNORE_CASE), "")
        val g = Regex("""-([A-Za-z0-9]{3,15})\s*(?:\[[^\]]*\])?\s*$""").find(base)?.groupValues?.get(1)?.lowercase() ?: return null
        return g.takeIf { it !in notGroups }
    }

    /** 0 to 3: the same group +2, the same kind of source +1, the same special cut +1 (at most 3) */
    fun match(subtitleName: String, videoName: String): Int {
        val s = tokens(subtitleName)
        val v = tokens(videoName)
        var score = 0
        val sg = groupOf(subtitleName)
        if (sg != null && (sg == groupOf(videoName) || sg in v)) score += 2
        val sk = kindOf(s)
        if (sk != null && sk == kindOf(v)) score += 1
        if (cuts.any { it in s && it in v }) score += 1
        return minOf(score, 3)
    }
}
