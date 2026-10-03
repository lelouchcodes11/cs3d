package android.graphics

import android.content.res.Resources
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import java.io.File
import java.io.FileDescriptor
import java.io.InputStream

object BitmapFactory {
    class Options {
        @JvmField var inBitmap: Bitmap? = null
        @JvmField var inMutable = false
        @JvmField var inJustDecodeBounds = false
        @JvmField var inSampleSize = 0
        @JvmField var inPreferredConfig: Bitmap.Config? = Bitmap.Config.ARGB_8888
        @JvmField var inPremultiplied = true
        @JvmField var inDither = false
        @JvmField var inDensity = 0
        @JvmField var inTargetDensity = 0
        @JvmField var inScreenDensity = 0
        @JvmField var inScaled = true
        @JvmField var inPurgeable = false
        @JvmField var inInputShareable = false
        @JvmField var inPreferQualityOverSpeed = false
        @JvmField var outWidth = 0
        @JvmField var outHeight = 0
        @JvmField var outMimeType: String? = null
        @JvmField var outConfig: Bitmap.Config? = null
        @JvmField var inTempStorage: ByteArray? = null
        @JvmField var mCancel = false
    }

    private fun mime(bytes: ByteArray): String? = when {
        bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 11 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        bytes.size > 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF" -> "image/gif"
        bytes.size > 1 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() -> "image/bmp"
        else -> null
    }

    private fun decode(bytes: ByteArray, opts: Options?): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            if (opts?.inJustDecodeBounds == true) {
                val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
                opts.outWidth = codec.width
                opts.outHeight = codec.height
                opts.outMimeType = mime(bytes)
                opts.outConfig = Bitmap.Config.ARGB_8888
                codec.close()
                return null
            }
            val image = Image.makeFromEncoded(bytes)
            var bitmap = Bitmap.fromImage(image)
            val sample = opts?.inSampleSize ?: 0
            if (sample > 1) {
                bitmap = Bitmap.createScaledBitmap(bitmap, kotlin.math.max(1, bitmap.getWidth() / sample), kotlin.math.max(1, bitmap.getHeight() / sample), true)
            }
            opts?.apply {
                outWidth = bitmap.getWidth()
                outHeight = bitmap.getHeight()
                outMimeType = mime(bytes)
                outConfig = Bitmap.Config.ARGB_8888
            }
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    @JvmStatic
    fun decodeFile(pathName: String?, opts: Options?): Bitmap? {
        if (pathName == null) return null
        val f = File(pathName)
        if (!f.exists()) return null
        return decode(f.readBytes(), opts)
    }

    @JvmStatic
    fun decodeFile(pathName: String?): Bitmap? = decodeFile(pathName, null)

    @JvmStatic
    fun decodeResource(res: Resources, id: Int, opts: Options?): Bitmap? =
        try {
            decode(res.openRawResource(id).use { it.readBytes() }, opts)
        } catch (e: Exception) {
            null
        }

    @JvmStatic
    fun decodeResource(res: Resources, id: Int): Bitmap? = decodeResource(res, id, null)

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? =
        decode(if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length), opts)

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = decodeByteArray(data, offset, length, null)

    @JvmStatic
    fun decodeStream(`is`: InputStream?, outPadding: Rect?, opts: Options?): Bitmap? =
        if (`is` == null) null else decode(`is`.readBytes(), opts)

    @JvmStatic
    fun decodeStream(`is`: InputStream?): Bitmap? = decodeStream(`is`, null, null)

    @JvmStatic
    fun decodeFileDescriptor(fd: FileDescriptor?, outPadding: Rect?, opts: Options?): Bitmap? =
        if (fd == null) null else decode(java.io.FileInputStream(fd).readBytes(), opts)

    @JvmStatic
    fun decodeFileDescriptor(fd: FileDescriptor?): Bitmap? = decodeFileDescriptor(fd, null, null)
}
