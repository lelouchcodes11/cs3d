package com.lagradost.desktop.ui.components

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageQualityTest {
    private val tmdb = "https://image.tmdb.org/t/p/%s/9O7gLzmreU0nGkIB6K3BsJbzvNv.jpg"

    @Test
    fun aSmallTmdbPictureAsksForTheSizeThePlaceNeeds() {
        // a 340 px card on a 150 % screen
        assertEquals(tmdb.format("w342"), ImageQuality.betterUrl(tmdb.format("w185"), 340))
        // the whole width of a 1920 px window: only the original is big enough
        assertEquals(tmdb.format("original"), ImageQuality.betterUrl(tmdb.format("w780"), 1920))
        assertEquals(tmdb.format("w1280"), ImageQuality.betterUrl(tmdb.format("w780"), 1200))
    }

    @Test
    fun aPictureThatIsLargeEnoughIsLeftAlone() {
        assertNull(ImageQuality.betterUrl(tmdb.format("w342"), 340))
        assertNull(ImageQuality.betterUrl(tmdb.format("w500"), 340))
        assertNull(ImageQuality.betterUrl(tmdb.format("original"), 4000))
        assertNull(ImageQuality.betterUrl(tmdb.format("w185"), 0))
        assertNull(ImageQuality.betterUrl("https://example.com/poster-185x278.jpg", 340))
    }

    @Test
    fun metahubSizesGoUpOnly() {
        val small = "https://images.metahub.space/poster/small/tt0111161/img"
        assertEquals("https://images.metahub.space/poster/large/tt0111161/img", ImageQuality.betterUrl(small, 340))
        assertEquals("https://images.metahub.space/poster/medium/tt0111161/img", ImageQuality.betterUrl(small, 200))
        assertNull(ImageQuality.betterUrl("https://images.metahub.space/poster/large/tt0111161/img", 340))
    }

    @Test
    fun shrinkingAveragesInsteadOfPickingOnePixel() {
        // a 1 px checkerboard has no detail that survives a 4 x reduction: every pixel of the result is grey. Nearest neighbour keeps black or white.
        val size = 400
        val pixels = ByteArray(size * size * 4) { i -> if (i % 4 == 3) 0xFF.toByte() else if (((i / 4) % size + (i / 4) / size) % 2 == 0) 0 else 0xFF.toByte() }
        val source = Bitmap().apply { installPixels(ImageInfo.makeN32(size, size, ColorAlphaType.OPAQUE), pixels, size * 4) }
        source.setImmutable()
        val image = Image.makeFromBitmap(source)
        val out = ImageQuality.scaled(image, 100, 100)
        assertEquals(100, out.width)
        val bytes = assertNotNull(out.readPixels(ImageInfo.makeN32(100, 100, ColorAlphaType.OPAQUE), 400, 0, 0))
        var worst = 0
        for (y in 20 until 80) for (x in 20 until 80) {
            val grey = bytes[(y * 100 + x) * 4].toInt() and 0xFF
            worst = maxOf(worst, kotlin.math.abs(grey - 127))
        }
        assertTrue(worst <= 12, "the reduced checkerboard is not grey (off by $worst)")
    }

    @Test
    fun aJpegIsDecodedAtAReducedSizeThatLooksLikeTheFullOneScaledDown() {
        // a smooth 800 x 1200 picture with some detail, as a JPEG
        val w = 800; val h = 1200
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            pixels[i] = (x * 255 / w).toByte(); pixels[i + 1] = (y * 255 / h).toByte(); pixels[i + 2] = (((x / 20 + y / 20) % 2) * 90 + 60).toByte(); pixels[i + 3] = 0xFF.toByte()
        }
        val source = Bitmap().apply { installPixels(ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE), pixels, w * 4) }
        source.setImmutable()
        val jpeg = org.jetbrains.skia.Data.makeFromBytes(Image.makeFromBitmap(source).encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 92)!!.bytes)
        val codec = org.jetbrains.skia.Codec.makeFromData(jpeg)
        // the card needs 200 x 300: a quarter of the picture is enough
        val reduced = assertNotNull(ImageQuality.reducedDecode(codec, w, h, 200, 300))
        assertEquals(200, reduced.width)
        assertEquals(300, reduced.height)
        // a card as large as the picture needs the whole of it
        assertNull(ImageQuality.reducedDecode(codec, w, h, 700, 1000))
        val viaReduced = ImageQuality.scaled(reduced, 200, 300)
        val whole = ImageQuality.scaled(Image.makeFromEncoded(jpeg.bytes), 200, 300)
        val a = assertNotNull(viaReduced.readPixels(ImageInfo.makeN32(200, 300, ColorAlphaType.OPAQUE), 800, 0, 0))
        val b = assertNotNull(whole.readPixels(ImageInfo.makeN32(200, 300, ColorAlphaType.OPAQUE), 800, 0, 0))
        var sum = 0L
        for (i in a.indices) sum += kotlin.math.abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
        val mean = sum.toDouble() / a.size
        assertTrue(mean < 3.0, "the reduced decode differs from the scaled whole one by $mean on average")
    }
}
