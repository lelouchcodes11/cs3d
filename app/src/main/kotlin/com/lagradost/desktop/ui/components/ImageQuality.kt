package com.lagradost.desktop.ui.components

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Precision
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/**
 * Two things that make every picture of the app sharp.
 *
 * 1. [CrispDecoder]: Coil's decoder for the desktop draws a picture that is larger (or smaller) than its place with Skia's default sampling, which is
 *    "nearest neighbour": a 2000 x 3000 poster in a 340 x 510 card keeps one pixel in six, so edges turn into stairs and thin lines into
 *    speckles, and a 185 px poster stretched to 340 px is a mosaic. The same size is computed here, and the picture is made with the
 *    filters a photo editor uses (halving with a box average while it is more than twice too big, then a bicubic last step).
 * 2. [ImageUpgrade]: an extension often sends the small version of a picture (TMDB's w185 for a card that is 340 px on this screen, a banner of 780 px for a
 *    1920 px wide window). Before the picture is asked for, its address is changed to the size the place needs; when the larger one
 *    does not exist the original is used.
 */
internal object ImageQuality {
    val upgrade: Interceptor = ImageUpgrade
    val decoder: Decoder.Factory = CrispDecoder.Factory()

    /** The picture at [w] x [h] pixels: halved with a box average while it is more than twice too large, then one bicubic step (also for a larger size) */
    fun scaled(image: Image, w: Int, h: Int): Bitmap {
        var current = image
        var owned = false
        try {
            while (current.width >= w * 2 && current.height >= h * 2) {
                val half = draw(current, current.width / 2, current.height / 2, SamplingMode.LINEAR)
                half.setImmutable()
                val next = Image.makeFromBitmap(half)
                half.close()
                if (owned) current.close()
                current = next
                owned = true
            }
            return draw(current, w, h, SamplingMode.CATMULL_ROM)
        } finally {
            if (owned) current.close()
        }
    }

    /**
     * The picture of [codec] decoded at 1/2, 1/4 or 1/8 of its size (the codec scales while it decodes) as long as that is still at least [w] x [h];
     * null when it is not worth it or the format cannot (the caller then decodes it whole). Same look as the halving loop: a box average, made by the decoder.
     */
    fun reducedDecode(codec: org.jetbrains.skia.Codec, srcW: Int, srcH: Int, w: Int, h: Int): Image? {
        var denom = 1
        while (denom < 8 && srcW / (denom * 2) >= w && srcH / (denom * 2) >= h) denom *= 2
        if (denom == 1) return null
        val bitmap = Bitmap()
        return try {
            bitmap.allocN32Pixels((srcW + denom - 1) / denom, (srcH + denom - 1) / denom, false)
            codec.readPixels(bitmap)
            bitmap.setImmutable()
            Image.makeFromBitmap(bitmap)
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.close()
        }
    }

    private fun draw(image: Image, w: Int, h: Int, sampling: SamplingMode): Bitmap {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(w, h, false)
        val canvas = Canvas(bitmap)
        try {
            canvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), sampling, null, true)
        } finally {
            canvas.close()
        }
        return bitmap
    }

    private val tmdb = Regex("""(image\.tmdb\.org/t/p/)(w\d+|original)(/)""")
    private val tmdbSizes = intArrayOf(185, 342, 500, 780, 1280)
    private val metahub = Regex("""((?:images|live)\.metahub\.space/(?:poster|background|logo)/)(small|medium|large)(/)""")
    private val metahubSizes = listOf("small", "medium", "large")
    // the episode stills of the Netflix / Hotstar mirrors (CNCVerse): the extensions ask for 150 px (a 150 x 84 picture blown up on a 430 px card);
    // the same address serves 300, 480, 720 and 1280 px (checked 2026-11-11). A size that is missing falls back to the original (ImageUpgrade).
    private val mirrorStill = Regex("""(imgcdn\.[a-z]+/(?:epimg|hsepimg)/)(\d+)(/)""")
    private val mirrorStillSizes = intArrayOf(300, 480, 720, 1280)

    /**
     * The address of a larger version of a picture that has several, or null (the address is not one of those, the picture is large enough already, or
     * [need], the pixels the picture must have across to be sharp where it is drawn, is not known)
     */
    fun betterUrl(url: String, need: Int): String? {
        if (need <= 0) return null
        tmdb.find(url)?.let { m ->
            val current = m.groupValues[2]
            if (current == "original") return null
            val have = current.drop(1).toIntOrNull() ?: return null
            if (have * 10 >= need * 9) return null
            val pick = tmdbSizes.firstOrNull { it > have && it * 100 >= need * 95 }
            return url.replaceRange(m.groups[2]!!.range, if (pick != null) "w$pick" else "original")
        }
        mirrorStill.find(url)?.let { m ->
            val have = m.groupValues[2].toIntOrNull() ?: return null
            if (have * 10 >= need * 9) return null
            val pick = mirrorStillSizes.firstOrNull { it > have && it * 100 >= need * 95 } ?: mirrorStillSizes.last()
            return if (pick <= have) null else url.replaceRange(m.groups[2]!!.range, pick.toString())
        }
        metahub.find(url)?.let { m ->
            val rank = metahubSizes.indexOf(m.groupValues[2])
            val want = if (need > 320) 2 else if (need > 160) 1 else 0
            if (want <= rank) return null
            return url.replaceRange(m.groups[2]!!.range, metahubSizes[want])
        }
        return null
    }
}

private class CrispDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        // a JPEG (or WebP) is decoded at a half, a quarter or an eighth of its size when the card needs no more: the decoder skips the work that the
        // halving loop below used to do on the whole picture (a 2000 x 3000 poster was decoded in full and then scaled down three times on the processor)
        val codec = runCatching { Codec.makeFromData(Data.makeFromBytes(bytes)) }.getOrNull()
        val srcW = codec?.width ?: 0
        val srcH = codec?.height ?: 0
        val image: Image = codec?.let { c ->
            val (tw, th) = target(srcW, srcH)
            ImageQuality.reducedDecode(c, srcW, srcH, tw, th)
        } ?: Image.makeFromEncoded(bytes)
        try {
            val fullW = if (srcW > 0) srcW else image.width
            val fullH = if (srcH > 0) srcH else image.height
            val (w, h) = target(fullW, fullH)
            val bitmap = ImageQuality.scaled(image, w, h)
            bitmap.setImmutable()
            return DecodeResult(bitmap.asImage(), isSampled = w < fullW || h < fullH)
        } finally {
            image.close()
            codec?.close()
        }
    }

    /** The size Coil's own decoder would make (same rules, so the memory cache treats the result as before) */
    private fun target(srcW: Int, srcH: Int): Pair<Int, Int> {
        val dst = DecodeUtils.computeDstSize(srcW, srcH, options.size, options.scale, options.maxBitmapSize)
        var multiplier = DecodeUtils.computeSizeMultiplier(srcW, srcH, dst.first, dst.second, options.scale)
        if (options.precision == Precision.INEXACT) multiplier = multiplier.coerceAtMost(1.0)
        return (multiplier * srcW).toInt().coerceAtLeast(1) to (multiplier * srcH).toInt().coerceAtLeast(1)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder = CrispDecoder(result.source, options)
    }
}

private object ImageUpgrade : Interceptor {
    /** addresses whose larger version did not exist: not asked again in this run */
    private val missing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String
        if (url == null || url in missing) return chain.proceed()
        val size = chain.size
        val width = (size.width as? Dimension.Pixels)?.px ?: 0
        val height = (size.height as? Dimension.Pixels)?.px ?: 0
        val better = ImageQuality.betterUrl(url, maxOf(width, height * 2 / 3))
        if (better == null || better == url) return chain.proceed()
        val result = chain.withRequest(chain.request.newBuilder().data(better).build()).proceed()
        if (result is ErrorResult) {
            missing += url
            return chain.proceed()
        }
        return result
    }
}
