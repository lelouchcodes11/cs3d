package com.lagradost.desktop.platform

import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.RootPaneContainer
import javax.swing.border.AbstractBorder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Keeps the picture of a window pixel-exact on a display that is scaled by a fraction (125 %, 150 %, 175 %).
 *
 * Skiko makes its Direct3D buffer `(int) (dp * scale)` pixels big and the native canvas window one that is `dp * scale` rounded up (one dp more when the
 * fraction is about a half). When the content area is a size in dp that is not a whole number of pixels (1281 dp at 150 % = 1921.5 px) the two differ by a
 * pixel or two and Windows stretches the buffer to the window: every 1 px line is smeared over two pixels with a phase that changes across the window, so
 * borders look broken (bright and crisp here, split in two and faint there) and text is soft. Measured at 150 %: buffer 1921 x 1129, canvas 1923 x 1131.
 *
 * The cure is a content area whose size in dp is a whole number of pixels: the dp that do not fit are taken off the right and bottom edge with an empty
 * border of the content pane (a strip of the page colour, 1 dp at 150 %), so the buffer, the canvas and the Compose scene are the same size.
 * The state of the window (maximized or not) does not matter: it is looked at on every resize and when the window moves to another display.
 */
object EvenPixels {
    private const val KEY = "cloudstream.evenPixels"

    fun install(window: Window) {
        val pane = (window as? RootPaneContainer)?.contentPane as? JComponent ?: return
        if (pane.getClientProperty(KEY) != null) return
        pane.putClientProperty(KEY, true)
        val listener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = update(window, pane)
            override fun componentMoved(e: ComponentEvent) = update(window, pane)
            override fun componentShown(e: ComponentEvent) = update(window, pane)
        }
        pane.addComponentListener(listener)
        window.addComponentListener(listener)
        update(window, pane)
    }

    /** The smallest size in dp that is a whole number of pixels at [scale] (2 at 150 %, 4 at 125 % and 175 %), 1 when there is no need or none up to 8 dp */
    internal fun step(scale: Double): Int {
        for (k in 1..8) {
            val px = k * scale
            if (abs(px - px.roundToInt()) < 0.01) return k
        }
        return 1
    }

    private fun update(window: Window, pane: JComponent) {
        if (pane.width <= 0 || pane.height <= 0) return
        val scale = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
        val step = step(scale)
        val extraW = if (step > 1) pane.width % step else 0
        val extraH = if (step > 1) pane.height % step else 0
        val ours = pane.border as? TrimBorder
        if ((ours?.right ?: 0) == extraW && (ours?.bottom ?: 0) == extraH) return
        // a border somebody else gave the pane stays
        if (pane.border != null && ours == null) return
        pane.border = if (extraW == 0 && extraH == 0) null else TrimBorder(extraW, extraH)
        pane.revalidate()
    }

    private class TrimBorder(val right: Int, val bottom: Int) : AbstractBorder() {
        override fun getBorderInsets(c: Component): Insets = Insets(0, 0, bottom, right)
        override fun getBorderInsets(c: Component, insets: Insets): Insets {
            insets.set(0, 0, bottom, right)
            return insets
        }
        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {}
        override fun isBorderOpaque(): Boolean = false
    }
}
