package com.lagradost.safefile

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.lagradost.desktop.DesktopPlatform
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/*
 * Desktop implementation of com.lagradost.safefile (https://github.com/LagradOst/SafeFile), the
 * storage abstraction used by CloudStream for downloads and backups. The API is identical; files
 * are plain java.io.File and the "media" folders map to the user's folders of the same kind.
 */

const val TAG = "SafeFile"

fun Closeable.closeQuietly() {
    try {
        this.close()
    } catch (_: Throwable) {
    }
}

fun logError(throwable: Throwable) {
    Log.d(TAG, "-------------------------------------------------------------------")
    Log.d(TAG, "safeApiCall: " + throwable.localizedMessage)
    Log.d(TAG, "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d(TAG, "-------------------------------------------------------------------")
}

internal inline fun <T> safe(block: () -> T): T? {
    return try {
        block()
    } catch (t: Throwable) {
        logError(t)
        null
    }
}

enum class MediaFileContentType {
    Downloads,
    Audio,
    Video,
    Images,
}

fun MediaFileContentType.toPath(): String {
    return when (this) {
        MediaFileContentType.Downloads -> "Download"
        MediaFileContentType.Audio -> "Music"
        MediaFileContentType.Video -> "Movies"
        MediaFileContentType.Images -> "Pictures"
    }
}

fun MediaFileContentType.defaultPrefix(): String = toAbsolutePath()

/** Desktop: the user's folder of this kind */
fun MediaFileContentType.toFile(): File {
    return when (this) {
        MediaFileContentType.Downloads -> DesktopPlatform.downloadsDir()
        MediaFileContentType.Audio -> DesktopPlatform.musicDir()
        MediaFileContentType.Video -> DesktopPlatform.videosDir()
        MediaFileContentType.Images -> DesktopPlatform.picturesDir()
    }
}

fun MediaFileContentType.toAbsolutePath(): String = toFile().absolutePath

fun replaceDuplicateFileSeparators(path: String): String {
    return path.replace(Regex("${Regex.escape(File.separator)}+"), File.separator)
}

fun MediaFileContentType.toUri(external: Boolean): Uri = Uri.fromFile(toFile())

interface SafeFile {
    /** file.gotoDirectory("a/b/c") -> "file/a/b/c/" where a null or blank directoryName
     * returns itself. createMissingDirectories specifies if the dirs should be created
     * when travelling or break at a dir not found */
    fun gotoDirectory(
        directoryName: String?,
        createMissingDirectories: Boolean = true
    ): SafeFile? {
        if (directoryName == null) return this

        return directoryName.split(File.separatorChar, '/').filter { it.isNotBlank() }
            .fold(this) { file: SafeFile?, directory ->
                if (createMissingDirectories) {
                    file?.createDirectory(directory)
                } else {
                    val next = file?.findFile(directory)

                    // we require the file to be a directory
                    if (next?.isDirectory() != true) {
                        null
                    } else {
                        next
                    }
                }
            }
    }

    /** Create a new file as a direct child of this directory. */
    @Throws(IOException::class)
    fun createFileOrThrow(displayName: String?): SafeFile

    /** Create a new file as a direct child of this directory. Returns null if failed */
    fun createFile(displayName: String?): SafeFile? = safe { createFileOrThrow(displayName) }

    /** Create a new directory as a direct child of this directory. */
    @Throws(IOException::class)
    fun createDirectoryOrThrow(directoryName: String?): SafeFile

    /** Create a new directory as a direct child of this directory. Returns null if failed */
    fun createDirectory(directoryName: String?): SafeFile? =
        safe { createDirectoryOrThrow(directoryName) }

    /** returns the uri of the file */
    @Throws(IOException::class)
    fun uriOrThrow(): Uri

    /** returns the uri of the file */
    fun uri(): Uri? = safe { uriOrThrow() }

    /** returns the display name of this file */
    @Throws(IOException::class)
    fun nameOrThrow(): String

    /** returns the display name of this file, returns null if failed or is a directory */
    fun name(): String? = safe { nameOrThrow() }

    @Throws(IOException::class)
    fun typeOrThrow(): String

    fun type(): String? = safe { typeOrThrow() }

    /** returns the file as a readable file path*/
    @Throws(IOException::class)
    fun filePathOrThrow(): String

    /** returns the file as a readable file path*/
    fun filePath(): String? = safe { filePathOrThrow() }

    /** returns true if the current SafeFile is a directory, because of file security this may throw */
    fun isDirectoryOrThrow(): Boolean

    /** returns true if the current SafeFile is a directory, because of file security this may return null */
    fun isDirectory(): Boolean? = safe { isDirectoryOrThrow() }

    /** returns true if the current SafeFile is a file, because of file security this may throw */
    @Throws(IOException::class)
    fun isFileOrThrow(): Boolean

    /** returns true if the current SafeFile is a file, because of file security this may return null */
    fun isFile(): Boolean? = safe { isFileOrThrow() }

    @Throws(IOException::class)
    fun lastModifiedOrThrow(): Long

    fun lastModified(): Long? = safe { lastModifiedOrThrow() }

    /** returns the file length in bytes, will throw if error or file not found */
    @Throws(IOException::class)
    fun lengthOrThrow(): Long

    /** returns the file length in bytes, will return null if error or file not found */
    fun length(): Long? = safe { lengthOrThrow() }

    /** Indicates whether the current context is allowed to read from this file. */
    @Throws(IOException::class)
    fun canReadOrThrow(): Boolean

    fun canRead(): Boolean? = safe { canReadOrThrow() }

    /** Indicates whether the current context is allowed to write to this file. */
    @Throws(IOException::class)
    fun canWriteOrThrow(): Boolean

    fun canWrite(): Boolean? = safe { canWriteOrThrow() }

    /** Deletes this file/directory. returns true if successful  */
    @Throws(IOException::class)
    fun deleteOrThrow(): Boolean

    fun delete(): Boolean? = safe { deleteOrThrow() }

    /** Returns a boolean indicating whether this file can be found. throws if some sort of error happened, can be treated as false */
    @Throws(IOException::class)
    fun existsOrThrow(): Boolean

    fun exists(): Boolean? = safe { existsOrThrow() }

    /** lists all files in the directory, throws if error or if not a directory */
    @Throws(IOException::class)
    fun listFilesOrThrow(): List<SafeFile>

    fun listFiles(): List<SafeFile>? = safe { listFilesOrThrow() }

    /** returns the file with the display name in the directory */
    @Throws(IOException::class)
    fun findFileOrThrow(displayName: String?, ignoreCase: Boolean = false): SafeFile

    fun findFile(displayName: String?, ignoreCase: Boolean = false): SafeFile? =
        safe { findFileOrThrow(displayName, ignoreCase) }

    /** Renames this file to displayName. returns true if successful */
    @Throws(IOException::class)
    fun renameToOrThrow(name: String?): Boolean

    fun renameTo(name: String?): Boolean? = safe { renameToOrThrow(name) }

    /** Open a stream on to the content associated with the file */
    @Throws(IOException::class)
    fun openOutputStreamOrThrow(append: Boolean = false): OutputStream

    fun openOutputStream(append: Boolean = false): OutputStream? =
        safe { openOutputStreamOrThrow(append) }

    /** Open a stream on to the content associated with the file */
    @Throws(IOException::class)
    fun openInputStreamOrThrow(): InputStream

    fun openInputStream(): InputStream? = safe { openInputStreamOrThrow() }

    companion object {
        fun fromUri(context: Context, uri: Uri): SafeFile? {
            return when (uri.scheme) {
                null, "", "file" -> uri.path?.let {
                    var p = it
                    if (DesktopPlatform.isWindows && p.length >= 3 && p[0] == '/' && p[1].isLetter() && p[2] == ':') {
                        p = p.substring(1)
                    }
                    JavaFile(File(p))
                }
                else -> {
                    // Paths stored by the Android app (content://...) can not be resolved here
                    Log.w(TAG, "Unsupported uri $uri")
                    null
                }
            }
        }

        fun fromFile(context: Context, file: File?): SafeFile? {
            if (file == null) return null
            return JavaFile(file.absoluteFile)
        }

        fun fromFilePath(context: Context, absolutePath: String?): SafeFile? {
            if (absolutePath == null) return null
            if (absolutePath.startsWith("file:")) return fromUri(context, Uri.parse(absolutePath))
            var p = absolutePath
            if (DesktopPlatform.isWindows && p.length >= 3 && p[0] == '/' && p[1].isLetter() && p[2] == ':') {
                p = p.substring(1)
            }
            return JavaFile(File(p).absoluteFile)
        }

        fun fromAsset(context: Context, filename: String?): SafeFile? {
            // Assets are not files on desktop, copy to the cache folder
            filename ?: return null
            return try {
                val out = File(context.cacheDir, "assets/$filename")
                out.parentFile?.mkdirs()
                context.assets.open(filename).use { input -> out.outputStream().use { input.copyTo(it) } }
                JavaFile(out)
            } catch (t: Throwable) {
                logError(t)
                null
            }
        }

        fun fromMedia(
            context: Context,
            folderType: MediaFileContentType,
            path: String = File.separator,
            external: Boolean = true,
        ): SafeFile? {
            val base = folderType.toFile()
            base.mkdirs()
            return JavaFile(base).gotoDirectory(path)
        }

        /** Upstream anti-repackaging check, not applicable on desktop */
        fun check(context: Context) {
        }
    }
}

/** java.io.File backed [SafeFile] */
class JavaFile(val file: File) : SafeFile {
    override fun createFileOrThrow(displayName: String?): SafeFile {
        if (displayName == null) throw IOException("No name")
        file.mkdirs()
        val child = File(file, displayName)
        if (!child.exists()) {
            child.parentFile?.mkdirs()
            if (!child.createNewFile() && !child.exists()) throw IOException("Could not create $child")
        }
        return JavaFile(child)
    }

    override fun createDirectoryOrThrow(directoryName: String?): SafeFile {
        if (directoryName == null) throw IOException("No name")
        val child = File(file, directoryName)
        if (!child.isDirectory && !child.mkdirs()) throw IOException("Could not create $child")
        return JavaFile(child)
    }

    override fun uriOrThrow(): Uri = Uri.fromFile(file)

    override fun nameOrThrow(): String = file.name

    override fun typeOrThrow(): String {
        if (file.isDirectory) throw IOException("Directory")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
    }

    override fun filePathOrThrow(): String = file.absolutePath

    override fun isDirectoryOrThrow(): Boolean = file.isDirectory

    override fun isFileOrThrow(): Boolean = file.isFile

    override fun lastModifiedOrThrow(): Long {
        if (!file.exists()) throw FileNotFoundException(file.path)
        return file.lastModified()
    }

    override fun lengthOrThrow(): Long {
        if (!file.exists()) throw FileNotFoundException(file.path)
        return file.length()
    }

    override fun canReadOrThrow(): Boolean = file.canRead()

    override fun canWriteOrThrow(): Boolean = if (file.exists()) file.canWrite() else file.parentFile?.canWrite() == true

    override fun deleteOrThrow(): Boolean = if (file.isDirectory) file.deleteRecursively() else file.delete()

    override fun existsOrThrow(): Boolean = file.exists()

    override fun listFilesOrThrow(): List<SafeFile> {
        val list = file.listFiles() ?: throw IOException("Not a directory: $file")
        return list.map { JavaFile(it) }
    }

    override fun findFileOrThrow(displayName: String?, ignoreCase: Boolean): SafeFile {
        if (displayName == null) throw IOException("No name")
        if (!ignoreCase) {
            val child = File(file, displayName)
            if (child.exists()) return JavaFile(child)
            throw FileNotFoundException(child.path)
        }
        return JavaFile(
            file.listFiles()?.firstOrNull { it.name.equals(displayName, true) }
                ?: throw FileNotFoundException(File(file, displayName).path)
        )
    }

    override fun renameToOrThrow(name: String?): Boolean {
        if (name == null) return false
        return file.renameTo(File(file.parentFile, name))
    }

    override fun openOutputStreamOrThrow(append: Boolean): OutputStream {
        file.parentFile?.mkdirs()
        return FileOutputStream(file, append)
    }

    override fun openInputStreamOrThrow(): InputStream = FileInputStream(file)

    override fun toString(): String = file.toString()

    override fun equals(other: Any?): Boolean = other is JavaFile && other.file == file

    override fun hashCode(): Int = file.hashCode()
}
