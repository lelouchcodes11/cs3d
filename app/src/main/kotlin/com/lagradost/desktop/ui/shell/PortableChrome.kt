package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.platform.WinChrome
import java.awt.Cursor
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Rectangle

/**
 * Linux: the window has no system frame (see [WinChrome.portable]), so nothing at its edges resizes it. Thin strips along the edges and corners
 * do: the cursor changes over them and a drag moves that edge. Not while maximized or full screen.
 */
@Composable
fun BoxScope.PortableResizeEdges() {
    val frame = WinChrome.portableFrame ?: return
    if (!WinChrome.windowedBar || WinChrome.maximized) return
    val edge = 5.dp
    val corner = 12.dp
    // left, top, right, bottom: which sides of the window the drag moves
    Box(Modifier.align(Alignment.CenterStart).width(edge).fillMaxHeight().resizer(frame, Cursor.W_RESIZE_CURSOR, l = true))
    Box(Modifier.align(Alignment.CenterEnd).width(edge).fillMaxHeight().resizer(frame, Cursor.E_RESIZE_CURSOR, r = true))
    Box(Modifier.align(Alignment.TopCenter).height(edge).fillMaxWidth().resizer(frame, Cursor.N_RESIZE_CURSOR, t = true))
    Box(Modifier.align(Alignment.BottomCenter).height(edge).fillMaxWidth().resizer(frame, Cursor.S_RESIZE_CURSOR, b = true))
    Box(Modifier.align(Alignment.TopStart).size(corner).resizer(frame, Cursor.NW_RESIZE_CURSOR, l = true, t = true))
    Box(Modifier.align(Alignment.TopEnd).size(corner).resizer(frame, Cursor.NE_RESIZE_CURSOR, r = true, t = true))
    Box(Modifier.align(Alignment.BottomStart).size(corner).resizer(frame, Cursor.SW_RESIZE_CURSOR, l = true, b = true))
    Box(Modifier.align(Alignment.BottomEnd).size(corner).resizer(frame, Cursor.SE_RESIZE_CURSOR, r = true, b = true))
}

private fun Modifier.resizer(frame: Frame, cursor: Int, l: Boolean = false, t: Boolean = false, r: Boolean = false, b: Boolean = false): Modifier =
    this.pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(cursor))).pointerInput(frame, cursor) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Initial)
                if (!down.buttons.isPrimaryPressed) continue
                down.changes.forEach { it.consume() }
                val start = MouseInfo.getPointerInfo()?.location ?: continue
                val from = Rectangle(frame.bounds)
                val min = frame.minimumSize.let { java.awt.Dimension(maxOf(it.width, 640), maxOf(it.height, 420)) }
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    e.changes.forEach { it.consume() }
                    if (!e.buttons.isPrimaryPressed) break
                    val p = MouseInfo.getPointerInfo()?.location ?: continue
                    val dx = p.x - start.x
                    val dy = p.y - start.y
                    var x = from.x; var y = from.y; var w = from.width; var h = from.height
                    if (r) w = maxOf(min.width, from.width + dx)
                    if (b) h = maxOf(min.height, from.height + dy)
                    if (l) { w = maxOf(min.width, from.width - dx); x = from.x + from.width - w }
                    if (t) { h = maxOf(min.height, from.height - dy); y = from.y + from.height - h }
                    frame.setBounds(x, y, w, h)
                }
            }
        }
    }
