package com.lagradost.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.screens.player.PlayerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File

/**
 * Things dropped on the app's windows (a desktop app is expected to take them):
 *  - a video file (or several: the first plays) or a video link from a browser: it plays in the player, with any subtitle files dropped with it;
 *  - subtitle files while a video plays: added to it and switched to (the same as Subtitles > Add a subtitle file);
 *  - a CloudStream link (a repository, a shared title): opened as when a browser hands it to the app;
 *  - a magnet link or a .torrent file: played as a torrent (the torrent engine asks first, as for any torrent source).
 * While something is dragged over a window, [DropHint] says what letting go will do.
 */
object DropOpen {
    enum class Kind(val text: String) { Video("Drop to play"), Subtitles("Drop to add the subtitles"), Link("Drop to open"), Unknown("Drop a video, a subtitle file or a link") }

    /** What is being dragged over a window of the app (null: nothing) */
    var hover by mutableStateOf<Kind?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "mts", "flv", "wmv", "mpg", "mpeg", "3gp", "ogv", "m3u8", "mpd", "torrent",
        "mp3", "flac", "m4a", "opus", "ogg", "wav")
    private val subtitleExt = setOf("srt", "vtt", "ass", "ssa", "sub", "ttml", "dfxp")
    private val appLinks = listOf("cloudstreamapp:", "cloudstreamrepo:", "cloudstreamsearch:", "cloudstreamcontinuewatching:", "csshare", "https://cs.repo/")

    /** Lets [window] and everything in it take drops (the drawing surface of a Compose window is a component of its own) */
    fun install(window: java.awt.Window) {
        fun walk(c: java.awt.Component) {
            if (c.dropTarget == null) runCatching { DropTarget(c, DnDConstants.ACTION_COPY_OR_MOVE or DnDConstants.ACTION_LINK, listener, true) }
            if (c is java.awt.Container) c.components.forEach { walk(it) }
        }
        walk(window)
    }

    private val listener = object : DropTargetAdapter() {
        override fun dragEnter(e: DropTargetDragEvent) {
            val kind = runCatching { kindOf(e.transferable) }.getOrDefault(Kind.Unknown)
            hover = kind
            e.acceptDrag(DnDConstants.ACTION_COPY)
        }

        override fun dragOver(e: DropTargetDragEvent) = e.acceptDrag(DnDConstants.ACTION_COPY)
        override fun dragExit(e: DropTargetEvent) { hover = null }

        override fun drop(e: DropTargetDropEvent) {
            hover = null
            e.acceptDrop(DnDConstants.ACTION_COPY)
            val ok = runCatching { take(e.transferable) }.onFailure { android.util.Log.w("DropOpen", "drop: $it") }.getOrDefault(false)
            e.dropComplete(ok)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun filesOf(t: Transferable): List<File>? =
        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) (t.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>) else null

    private fun textOf(t: Transferable): String? =
        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) (t.getTransferData(DataFlavor.stringFlavor) as? String)?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } else null

    private fun File.ext() = extension.lowercase()

    private fun kindOf(t: Transferable): Kind {
        filesOf(t)?.let { files ->
            return when {
                files.any { it.ext() in videoExt } -> Kind.Video
                files.any { it.ext() in subtitleExt } -> Kind.Subtitles
                else -> Kind.Unknown
            }
        }
        val text = textOf(t) ?: return Kind.Unknown
        return if (appLinks.any { text.startsWith(it, ignoreCase = true) }) Kind.Link else if (isPlayable(text)) Kind.Video else Kind.Unknown
    }

    private fun isPlayable(text: String) = text.startsWith("magnet:", true) || text.startsWith("http://", true) || text.startsWith("https://", true)

    /** Does what was dropped; false when it was nothing the app can use */
    private fun take(t: Transferable): Boolean {
        filesOf(t)?.let { return openFiles(it) }
        return openText(textOf(t) ?: return false)
    }

    /** Dropped files: the first video plays (with the subtitle files dropped with it), or subtitle files are added to the video that plays */
    fun openFiles(files: List<File>): Boolean {
        run {
            val videos = files.filter { it.isFile && it.ext() in videoExt }
            val subtitles = files.filter { it.isFile && it.ext() in subtitleExt }
            if (videos.isNotEmpty()) {
                // file:///C:/... (Java's File.toURI gives file:/C:/..., which mpv takes for a relative path)
                play(videos.first().nameWithoutExtension, videos.first().toPath().toUri().toString(), subtitles)
                return true
            }
            if (subtitles.isNotEmpty()) {
                val session = PlayerSession.active
                if (session == null || Navigator.current.route !is Route.Player) {
                    Toasts.show("Start a video first, then drop the subtitles on it", false)
                    return false
                }
                subtitles.forEach { session.addSubtitleFile(it) }
                return true
            }
            Toasts.show("Drop a video or a subtitle file (${files.firstOrNull()?.name ?: "this"} is neither)", false)
            return false
        }
    }

    /** True when [text] is a link the app opens itself (a video, a magnet, a CloudStream link) rather than words to search for */
    fun isLink(text: String): Boolean = text.trim().let { s -> !s.contains(' ') && (appLinks.any { s.startsWith(it, ignoreCase = true) } || isPlayable(s)) }

    /** Opens a link (dropped, or pasted into a search box): a CloudStream link as from a browser, anything else plays; false when it is not a link */
    fun openText(raw: String): Boolean {
        val text = raw.trim()
        if (!isLink(text)) return false
        if (appLinks.any { text.startsWith(it, ignoreCase = true) }) { com.lagradost.desktop.NativeLinks.open(text); return true }
        if (isPlayable(text)) {
            val name = runCatching { java.net.URI(text).path?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } }.getOrNull()
                ?: if (text.startsWith("magnet:", true)) "Torrent" else "Link"
            play(java.net.URLDecoder.decode(name, "UTF-8"), text, emptyList())
            return true
        }
        return false
    }

    /** Plays [url] in the player (after the video that plays now, if any, has closed) */
    private fun play(name: String, url: String, subtitles: List<File>) {
        scope.launch {
            val link = newExtractorLink(name, name, url)
            val subs = subtitles.map { SubtitleData(it.name, "", it.toURI().toString(), SubtitleOrigin.DOWNLOADED_FILE, it.name.toSubtitleMimeType(), emptyMap(), null) }
            withContext(Dispatchers.Main) {
                // a video that plays now closes first: its native player core is let go in order (two at once would fight over the window)
                if (Navigator.current.route is Route.Player) {
                    Navigator.back()
                    var waited = 0
                    while (Navigator.current.route is Route.Player && waited < 5_000) { delay(50); waited += 50 }
                }
                Navigator.go(Route.Player(ExtractorLinkGenerator(listOf(link), subs), 0, null))
            }
        }
    }
}

/** Over a window while something is dragged over it: what letting go will do */
@Composable
fun DropHint() {
    val kind = DropOpen.hover ?: return
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Color(0xF21C1C1E), RoundedCornerShape(22.dp)).padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                when (kind) { DropOpen.Kind.Video -> Icons.Play; DropOpen.Kind.Subtitles -> Icons.Subtitles; DropOpen.Kind.Link -> Icons.Link; DropOpen.Kind.Unknown -> Icons.Download },
                size = 34.dp, tint = Color.White,
            )
            FText(kind.text, style = Fluent.type.subtitle, color = Color.White, maxLines = 1)
        }
    }
}
