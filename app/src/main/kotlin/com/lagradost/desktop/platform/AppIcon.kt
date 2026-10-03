package com.lagradost.desktop.platform

import java.awt.RenderingHints
import java.awt.Window
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** The CloudStream logo for the taskbar, Alt+Tab and the window; the exe itself gets icon.ico from the packaging */
object AppIcon {
    private val SIZES = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    @Volatile
    private var prepared: List<BufferedImage>? = null

    /** Own identity for the taskbar (not "java"), so the window shows its own icon and groups under it */
    fun setAppId() {
        runCatching { com.sun.jna.platform.win32.Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(com.sun.jna.WString("CloudStream.Desktop")) }
    }

    /** Scales the icon in the background at start; getScaledInstance on the UI thread took 1.7 s while the window opened */
    fun prepareAsync() {
        Thread({ prepared = runCatching { build() }.getOrNull() }, "app-icon").apply { isDaemon = true }.start()
    }

    private fun build(): List<BufferedImage>? {
        val source = AppIcon::class.java.classLoader.getResourceAsStream("app-icon.png")?.use { ImageIO.read(it) } ?: return null
        return SIZES.map { scale(source, it) }
    }

    /** Halving steps and a final bicubic one: no jagged edges at 16 px, and fast */
    private fun scale(source: BufferedImage, size: Int): BufferedImage {
        var current = source
        while (current.width / 2 >= size) current = draw(current, current.width / 2)
        return draw(current, size)
    }

    private fun draw(source: BufferedImage, size: Int): BufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { out ->
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(source, 0, 0, size, size, null)
        g.dispose()
    }

    fun install(window: Window) {
        runCatching {
            val images = prepared ?: build() ?: return
            window.iconImages = images
        }
    }
}
