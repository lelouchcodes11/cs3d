package com.lagradost.desktop.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll

/**
 * What the user sees of the updater: the banner at the bottom of the window (a new version, the download with its progress, the restart when it is
 * ready, a failed download with its retry), and the window with a release's notes. The Settings > Updates page has its own cards ([updateCards]).
 */
object UpdateUi {
    private fun megabytes(bytes: Long) = "%.0f MB".format(bytes / 1_048_576.0)

    /** The release the banner is about, or null when there is nothing to say */
    private fun bannerRelease(): UpdateCheck.Release? {
        val status = UpdateCheck.status
        val release = when (status) {
            is UpdateCheck.Status.Available -> status.release
            is UpdateCheck.Status.Downloading -> status.release
            is UpdateCheck.Status.Ready -> status.release
            else -> return null
        }
        // a video or the first-run set-up owns the window
        val route = Navigator.current.route
        if (route is Route.Player || route is Route.Setup) return null
        return when (status) {
            is UpdateCheck.Status.Available -> release.takeIf { UpdateCheck.skipped != it.tag && UpdateCheck.dismissedTag != it.tag }
            is UpdateCheck.Status.Ready -> release.takeIf { UpdateCheck.dismissedTag != it.tag }
            else -> release
        }
    }

    @Composable
    fun Banner() {
        val c = Fluent.colors
        val release = bannerRelease()
        // the text stays while the banner slides away
        val last = androidx.compose.runtime.remember { arrayOfNulls<UpdateCheck.Release>(1) }
        if (release != null) last[0] = release
        val shown = release ?: last[0]
        Box(Modifier.fillMaxSize().padding(bottom = 96.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                release != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                if (shown != null) BannerCard(shown)
            }
        }
    }

    @Composable
    private fun BannerCard(release: UpdateCheck.Release) {
        val c = Fluent.colors
        val status = UpdateCheck.status
        val shape = RoundedCornerShape(FluentShapes.card)
        val downloading = status as? UpdateCheck.Status.Downloading
        val ready = status is UpdateCheck.Status.Ready
        val error = UpdateCheck.downloadError.takeIf { status is UpdateCheck.Status.Available }
        val progress = downloading?.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) }
        val accent = c.accent
        val size = UpdateCheck.sizeOf(release)
        val canInstall = UpdateCheck.canInstall(release)
        Row(
            Modifier
                .widthIn(max = 760.dp).fillMaxWidth()
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(c.flyout, shape)
                // the download fills the banner from the left
                .drawBehind { if (progress != null && progress > 0f) drawRect(accent.copy(alpha = 0.22f), size = Size(this.size.width * progress, this.size.height)) }
                .hairBorder(c.strokeStrong.copy(alpha = 0.4f), shape)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (ready) Icons.Accept else if (error != null) Icons.Warning else Icons.Update, size = 18.dp, tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                FText(
                    "CloudStream ${release.label}" + (if (release.prerelease) " (pre-release)" else "") + (size?.let { " · ${megabytes(it)}" } ?: ""),
                    style = Fluent.type.bodyStrong, maxLines = 1,
                )
                FText(
                    when {
                        error != null -> "Could not download: $error"
                        downloading != null -> if (progress != null) "Downloading… ${(progress * 100).toInt()} %" else "Downloading… ${megabytes(downloading.done)}"
                        ready -> "Downloaded and checked. Restart to finish: your settings, accounts and extensions stay."
                        canInstall -> "A new version is available. You have ${UpdateCheck.currentVersion}."
                        else -> "A new version is available. Get it from the release page."
                    },
                    style = Fluent.type.caption, color = if (error != null) c.critical else c.textSecondary, maxLines = 2,
                )
            }
            if (release.notes.isNotBlank()) IconButton(Icons.Info, { showNotes(release) }, tooltip = "Release notes", size = 36.dp, iconSize = 16.dp)
            when {
                downloading != null -> Button("Cancel", { UpdateCheck.cancelDownload() }, kind = ButtonKind.Subtle)
                ready -> Button("Restart & update", { (UpdateCheck.status as? UpdateCheck.Status.Ready)?.let { UpdateCheck.install(it.release, it.file) } }, kind = ButtonKind.Accent)
                error != null -> Button("Retry", { UpdateCheck.startUpdate(release) }, kind = ButtonKind.Accent)
                canInstall -> Button("Update", { UpdateCheck.startUpdate(release) }, kind = ButtonKind.Accent)
                else -> Button("Download", { UpdateCheck.downloadInBrowser(release) }, kind = ButtonKind.Accent)
            }
            if (downloading == null) IconButton(Icons.Close, { UpdateCheck.dismissBanner(release) }, tooltip = "Hide", size = 36.dp, iconSize = 14.dp)
        }
    }

    /** The notes of a release, with the ways to act on it */
    fun showNotes(release: UpdateCheck.Release) {
        val status = UpdateCheck.status
        val offered = status is UpdateCheck.Status.Available && status.release.tag == release.tag
        Overlays.show(
            Overlays.Dialog(
                title = "What's new in ${release.label}", width = 640.dp,
                primary = if (offered && UpdateCheck.canInstall(release)) "Update now" else null,
                onPrimary = { UpdateCheck.startUpdate(release) },
                secondary = if (offered) "Skip this version" else "Release page",
                onSecondary = { if (offered) UpdateCheck.skip(release) else DesktopPlatform.openExternalBrowser(release.pageUrl) },
                close = "Close", bodyMax = 460.dp,
            ) {
                Box(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Markdown(release.markdown.ifBlank { "This release has no notes." })
                }
            },
        )
    }

    // ---- release notes as text people can read ----------------------------------------------------------------------------------------------

    /** What GitHub release notes use: headings, bullets, **bold**, `code` and links (shown as their text). Nothing else is drawn specially. */
    @Composable
    fun Markdown(text: String) {
        val c = Fluent.colors
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (raw in text.lines()) {
                val line = raw.trimEnd()
                val trimmed = line.trimStart()
                when {
                    line.isBlank() -> Box(Modifier.size(2.dp))
                    trimmed.startsWith("#") -> {
                        val level = trimmed.takeWhile { it == '#' }.length
                        FText(inline(trimmed.dropWhile { it == '#' }.trim()), style = if (level <= 2) Fluent.type.subtitle else Fluent.type.bodyStrong, modifier = Modifier.padding(top = 6.dp))
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row(Modifier.padding(start = (line.length - trimmed.length).coerceAtMost(8).dp * 2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FText("•", color = c.textSecondary)
                        FText(inline(trimmed.drop(2)), modifier = Modifier.weight(1f))
                    }
                    else -> FText(inline(trimmed))
                }
            }
        }
    }

    /** One line: **bold** and `code` styled, [text](address) as its text, other marks left alone */
    internal fun inline(line: String): AnnotatedString {
        val withoutLinks = line.replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
        return buildAnnotatedString {
            val pattern = Regex("""\*\*([^*]+)\*\*|`([^`]+)`""")
            var at = 0
            for (m in pattern.findAll(withoutLinks)) {
                append(withoutLinks.substring(at, m.range.first))
                if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
                else withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[2]) }
                at = m.range.last + 1
            }
            append(withoutLinks.substring(at))
        }
    }

    /** The release page of this app, for the Updates page */
    fun openReleases() = DesktopPlatform.openExternalBrowser(AppInfo.RELEASES_URL)
}
