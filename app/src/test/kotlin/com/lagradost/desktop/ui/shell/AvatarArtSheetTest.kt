package com.lagradost.desktop.ui.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Not a check of the app: with `AVATAR_SHEET=<folder>` set it draws every picture of [avatarArt] into PNG sheets there (one per time of the loop,
 * each picture cut to its circle on its own colour) so that the drawings can be looked at without starting the app.
 */
class AvatarArtSheetTest {
    @Test
    fun sheet() {
        val out = System.getenv("AVATAR_SHEET") ?: return
        val only = System.getenv("AVATAR_ONLY")?.split(',')?.filter { it.isNotBlank() }
        val times = (System.getenv("AVATAR_TIMES") ?: "0,1.1,2.2").split(',').map { it.toFloat() }
        val cell = (System.getenv("AVATAR_CELL") ?: "150").toInt()
        val ids = avatarArt.keys.filter { only == null || it in only }
        val cols = 5
        val rows = (ids.size + cols - 1) / cols
        File(out).mkdirs()
        for (t in times) {
            val w = cols * cell; val h = rows * cell
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
                drawRect(Color(0xFF14161C))
                ids.forEachIndexed { i, id ->
                    val av = animatedAvatars.firstOrNull { it.id == id }
                    val bg = avatarBackgrounds[(av?.bg ?: 0).coerceIn(0, avatarBackgrounds.lastIndex)]
                    translate((i % cols) * cell + 5f, (i / cols) * cell + 5f) {
                        val d = cell - 10f
                        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, d, d)) }
                        clipPath(circle) {
                            drawRect(bg, Offset.Zero, Size(d, d))
                            drawAvatarArt(avatarArt.getValue(id), t, d)
                        }
                    }
                }
            }
            val png = Image.makeFromBitmap(bmp.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "sheet-${t}.png").writeBytes(png)
        }
        assertTrue(File(out, "sheet-${times.first()}.png").length() > 0)
    }
}
