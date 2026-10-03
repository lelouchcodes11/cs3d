package android.graphics

import android.os.Parcel
import android.os.Parcelable
import android.util.DisplayMetrics
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** android.graphics.Bitmap backed by a Skia N32 bitmap. */
class Bitmap private constructor(
    @JvmField val skia: org.jetbrains.skia.Bitmap,
    private var config: Config,
    private var mutable: Boolean,
) : Parcelable {
    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102 }
    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    private var recycled = false
    private var density = DisplayMetrics.DENSITY_DEFAULT
    private var hasMipMap = false

    @Volatile
    private var cachedImage: Image? = null
    private var cachedGeneration = -1

    fun getWidth(): Int = skia.width
    fun getHeight(): Int = skia.height
    fun getConfig(): Config = config
    fun setConfig(config: Config) {
        this.config = config
    }

    fun isMutable(): Boolean = mutable
    fun isRecycled(): Boolean = recycled
    fun recycle() {
        recycled = true
    }

    fun getDensity(): Int = density
    fun setDensity(density: Int) {
        this.density = density
    }

    fun hasAlpha(): Boolean = !skia.computeIsOpaque()
    fun setHasAlpha(hasAlpha: Boolean) {}
    fun hasMipMap(): Boolean = hasMipMap
    fun setHasMipMap(hasMipMap: Boolean) {
        this.hasMipMap = hasMipMap
    }

    fun getRowBytes(): Int = skia.rowBytes
    fun getByteCount(): Int = skia.computeByteSize()
    fun getAllocationByteCount(): Int = skia.computeByteSize()
    fun getGenerationId(): Int = skia.generationId

    fun getScaledWidth(targetDensity: Int): Int = scale(getWidth(), density, targetDensity)
    fun getScaledHeight(targetDensity: Int): Int = scale(getHeight(), density, targetDensity)
    fun getScaledWidth(metrics: DisplayMetrics): Int = getScaledWidth(metrics.densityDpi)
    fun getScaledHeight(metrics: DisplayMetrics): Int = getScaledHeight(metrics.densityDpi)
    fun getScaledWidth(canvas: Canvas): Int = getWidth()
    fun getScaledHeight(canvas: Canvas): Int = getHeight()

    fun getPixel(x: Int, y: Int): Int = skia.getColor(x, y)

    fun setPixel(x: Int, y: Int, color: Int) {
        skia.erase(color, IRect.makeXYWH(x, y, 1, 1))
        skia.notifyPixelsChanged()
    }

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bytes = skia.readPixels(info, width * 4, x, y) ?: return
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (row in 0 until height) {
            for (col in 0 until width) {
                pixels[offset + row * stride + col] = buf.getInt((row * width + col) * 4)
            }
        }
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        if (x == 0 && y == 0 && width == getWidth() && height == getHeight()) {
            val buf = ByteBuffer.allocate(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (row in 0 until height) for (col in 0 until width) buf.putInt(pixels[offset + row * stride + col])
            skia.installPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), buf.array(), width * 4)
        } else {
            for (row in 0 until height) for (col in 0 until width) {
                skia.erase(pixels[offset + row * stride + col], IRect.makeXYWH(x + col, y + row, 1, 1))
            }
        }
        skia.notifyPixelsChanged()
    }

    fun eraseColor(c: Int) {
        skia.erase(c)
        skia.notifyPixelsChanged()
    }

    fun eraseColor(color: Long) = eraseColor(color.toInt())

    fun copy(config: Config?, isMutable: Boolean): Bitmap = Bitmap(skia.makeClone(), config ?: this.config, isMutable)

    fun extractAlpha(): Bitmap {
        val dst = org.jetbrains.skia.Bitmap()
        skia.extractAlpha(dst)
        return Bitmap(dst, Config.ALPHA_8, true)
    }

    fun sameAs(other: Bitmap?): Boolean {
        if (other == null || other.getWidth() != getWidth() || other.getHeight() != getHeight()) return false
        val info = ImageInfo(getWidth(), getHeight(), ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        return skia.readPixels(info, getWidth() * 4, 0, 0).contentEquals(other.skia.readPixels(info, getWidth() * 4, 0, 0))
    }

    fun compress(format: CompressFormat?, quality: Int, stream: OutputStream): Boolean {
        val fmt = when (format) {
            CompressFormat.JPEG -> EncodedImageFormat.JPEG
            CompressFormat.WEBP, CompressFormat.WEBP_LOSSY, CompressFormat.WEBP_LOSSLESS -> EncodedImageFormat.WEBP
            else -> EncodedImageFormat.PNG
        }
        val data = toImage().encodeToData(fmt, quality.coerceIn(0, 100)) ?: return false
        stream.write(data.bytes)
        stream.flush()
        return true
    }

    /** Skia image snapshot of the current pixels, cached until the pixels change */
    fun toImage(): Image {
        val gen = skia.generationId
        val img = cachedImage
        if (img != null && gen == cachedGeneration) return img
        return Image.makeFromBitmap(skia).also {
            cachedImage = it
            cachedGeneration = gen
        }
    }

    fun prepareToDraw() {}

    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}

    companion object {
        private fun scale(size: Int, sdensity: Int, tdensity: Int): Int {
            if (sdensity == 0 || tdensity == 0 || sdensity == tdensity) return size
            return (size * tdensity + (sdensity shr 1)) / sdensity
        }

        private fun alloc(width: Int, height: Int, config: Config?): Bitmap {
            require(width > 0 && height > 0) { "width and height must be > 0" }
            val b = org.jetbrains.skia.Bitmap()
            b.allocN32Pixels(width, height, config == Config.RGB_565)
            b.erase(0)
            return Bitmap(b, config ?: Config.ARGB_8888, true)
        }

        @JvmStatic
        fun fromSkia(bitmap: org.jetbrains.skia.Bitmap): Bitmap = Bitmap(bitmap, Config.ARGB_8888, true)

        @JvmStatic
        fun fromImage(image: Image): Bitmap {
            val b = org.jetbrains.skia.Bitmap()
            b.allocN32Pixels(image.width, image.height, false)
            image.readPixels(b, 0, 0)
            return Bitmap(b, Config.ARGB_8888, false)
        }

        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config?): Bitmap = alloc(width, height, config)

        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config?, hasAlpha: Boolean): Bitmap = alloc(width, height, config)

        @JvmStatic
        fun createBitmap(display: DisplayMetrics?, width: Int, height: Int, config: Config?): Bitmap = alloc(width, height, config)

        @JvmStatic
        fun createBitmap(src: Bitmap): Bitmap = createBitmap(src, 0, 0, src.getWidth(), src.getHeight())

        @JvmStatic
        fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap =
            createBitmap(source, x, y, width, height, null, false)

        @JvmStatic
        fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int, m: Matrix?, filter: Boolean): Bitmap {
            val srcRect = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
            val dstRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            m?.mapRect(dstRect, RectF(0f, 0f, width.toFloat(), height.toFloat()))
            val out = alloc(kotlin.math.max(1, kotlin.math.round(dstRect.width()).toInt()), kotlin.math.max(1, kotlin.math.round(dstRect.height()).toInt()), source.config)
            val canvas = Canvas(out)
            canvas.translate(-dstRect.left, -dstRect.top)
            if (m != null) canvas.concat(m)
            val paint = Paint().apply { setFilterBitmap(filter) }
            canvas.drawBitmap(source, Rect(x, y, x + width, y + height), RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
            srcRect.hashCode()
            return out
        }

        @JvmStatic
        fun createBitmap(colors: IntArray, width: Int, height: Int, config: Config?): Bitmap =
            createBitmap(colors, 0, width, width, height, config)

        @JvmStatic
        fun createBitmap(colors: IntArray, offset: Int, stride: Int, width: Int, height: Int, config: Config?): Bitmap {
            val b = alloc(width, height, config)
            b.setPixels(colors, offset, stride, 0, 0, width, height)
            return b
        }

        @JvmStatic
        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap {
            val m = Matrix()
            m.setScale(dstWidth.toFloat() / src.getWidth(), dstHeight.toFloat() / src.getHeight())
            return createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, filter)
        }
    }
}
