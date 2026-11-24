package com.lagradost.cloudstream3.utils.downloader

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.getFolderPrefix
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile

object DownloadFileManagement {
    private const val RESERVED_CHARS = "|\\?*<\":>+[]/\'"
    internal fun sanitizeFilename(name: String, removeSpaces: Boolean = false): String {
        var tempName = name
        for (c in RESERVED_CHARS) {
            tempName = tempName.replace(c, ' ')
        }
        // desktop: what else Windows refuses in a file or folder name: control characters (a scraped title ending in a line break), a trailing dot,
        // and the device names (a series called "Con" or "Aux" could not get a folder, the download failed with "Cant create directory")
        tempName = tempName.map { if (it < ' ') ' ' else it }.joinToString("")
        if (removeSpaces) tempName = tempName.replace(" ", "")
        tempName = tempName.replace("  ", " ").trim(' ').trimEnd('.', ' ')
        // desktop: Windows takes at most 255 characters in one file or folder name: an episode whose "name" was its whole plot could not be
        // saved ("Could not create"). Cut where a character is not split in two
        if (tempName.length > MAX_NAME_LENGTH) {
            var cut = MAX_NAME_LENGTH
            if (tempName[cut - 1].isHighSurrogate()) cut--
            tempName = tempName.substring(0, cut).trimEnd('.', ' ')
        }
        if (tempName.substringBefore('.').uppercase() in WINDOWS_DEVICE_NAMES) tempName = "_$tempName"
        return tempName
    }

    /** desktop: room is left for " (id)", the extension and a subtitle's language after it (Windows: 255 at most) */
    private const val MAX_NAME_LENGTH = 200

    private val WINDOWS_DEVICE_NAMES = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

    /**
     * Used for getting video player subs.
     * @return List of pairs for the files in this format: <Name, Uri>
     * */
    internal fun getFolder(
        context: Context,
        relativePath: String,
        basePath: String?
    ): List<Pair<String, Uri>>? {
        val base = basePathToFile(context, basePath)
        val folder =
            base?.gotoDirectory(relativePath, createMissingDirectories = false) ?: return null

        //if (folder.isDirectory() != false) return null

        return folder.listFiles()
            ?.mapNotNull { (it.name() ?: "") to (it.uri() ?: return@mapNotNull null) }
    }

    /**
     * Turns a string to an UniFile. Used for stored string paths such as settings.
     * Should only be used to get a download path.
     * */
    internal fun basePathToFile(context: Context, path: String?): SafeFile? {
        return when {
            path.isNullOrBlank() -> getDefaultDir(context)
            path.startsWith("content://") -> SafeFile.fromUri(context, path.toUri())
            else -> SafeFile.fromFilePath(context, path)
        }
    }

    /**
     * Base path where downloaded things should be stored, changes depending on settings.
     * Returns the file and a string to be stored for future file retrieval.
     * UniFile.filePath is not sufficient for storage.
     * */
    internal fun Context.getBasePath(): Pair<SafeFile?, String?> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val basePathSetting = settingsManager.getString(getString(R.string.download_path_key), null)
        return basePathToFile(this, basePathSetting) to basePathSetting
    }

    internal fun getFileName(
        context: Context,
        metadata: DownloadObjects.DownloadEpisodeMetadata
    ): String {
        return getFileName(context, metadata.name, metadata.episode, metadata.season)
    }

    internal fun getFileName(
        context: Context,
        epName: String?,
        episode: Int?,
        season: Int?
    ): String {
        // kinda ugly ik
        return sanitizeFilename(
            if (epName == null) {
                if (season != null) {
                    "${context.getString(R.string.season)} $season ${context.getString(R.string.episode)} $episode"
                } else {
                    "${context.getString(R.string.episode)} $episode"
                }
            } else {
                if (episode != null) {
                    if (season != null) {
                        "${context.getString(R.string.season)} $season ${context.getString(R.string.episode)} $episode - $epName"
                    } else {
                        "${context.getString(R.string.episode)} $episode - $epName"
                    }
                } else {
                    epName
                }
            }
        )
    }


    internal fun DownloadObjects.DownloadedFileInfo.toFile(context: Context): SafeFile? {
        return basePathToFile(context, this.basePath)?.gotoDirectory(
            relativePath,
            createMissingDirectories = false
        )
            ?.findFile(displayName)
    }

    internal fun getFolder(currentType: TvType, titleName: String): String {
        return if (currentType.isEpisodeBased()) {
            val sanitizedFileName = sanitizeFilename(titleName)
            "${currentType.getFolderPrefix()}/$sanitizedFileName"
        } else currentType.getFolderPrefix()
    }

    /**
     * Gets the default download path as an UniFile.
     * Vital for legacy downloads, be careful about changing anything here.
     *
     * As of writing UniFile is used for everything but download directory on scoped storage.
     * Special ContentResolver fuckery is needed for that as UniFile doesn't work.
     * */
    fun getDefaultDir(context: Context): SafeFile? {
        // See https://www.py4u.net/discuss/614761
        return SafeFile.fromMedia(
            context, MediaFileContentType.Downloads
        )
    }

}