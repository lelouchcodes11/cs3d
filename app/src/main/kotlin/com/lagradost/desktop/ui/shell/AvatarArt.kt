package com.lagradost.desktop.ui.shell

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The length of every picture's idle loop, seconds: every movement in it repeats within this time, so the loop has no seam */
const val ART_LOOP = 4f

private const val TAU = (2 * PI).toFloat()
internal val Ink = Color(0xFF1B1626)

internal fun Color.light(f: Float) = lerp(this, Color.White, f)
internal fun Color.dark(f: Float) = lerp(this, Color.Black, f)

/** A picture: drawn by code in a 100 x 100 space (the circle it is cut to is the middle of it), so it is sharp at every size */
internal typealias ArtDraw = Rig.() -> Unit

/**
 * The parts the pictures are made of. [t] is the time in the loop (0 to [ART_LOOP]); the picture at 0 is the one at rest (eyes open, nothing mid-motion).
 */
internal class Rig(val s: DrawScope, val t: Float) {
    /** where in the loop, as an angle: sin(ph) is one swing per loop, sin(ph * 2) two */
    val ph = t / ART_LOOP * TAU
    /** where in the loop, 0 to 1 */
    val u = t / ART_LOOP
    /** the whole figure floats up and down a little */
    val bob = sin(ph) * 1.3f

    /** 1 open to 0 shut: two quick blinks in the loop */
    fun lid(shift: Float = 0f): Float {
        var o = 1f
        for (c in floatArrayOf(1.1f, 2.9f)) o = min(o, (abs(t - c - shift) / 0.1f).coerceIn(0f, 1f))
        return o
    }

    /** 0 to 1 to 0 around the moment [c] (a short event: an ear twitch, a wink) */
    fun bump(c: Float, w: Float) = max(0f, 1f - abs(t - c) / w)

    // ---- shapes
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color, a: Float = 1f) = s.drawOval(c, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = a)
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, b: Brush) = s.drawOval(b, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
    fun circle(cx: Float, cy: Float, r: Float, c: Color, a: Float = 1f) = s.drawCircle(c, r, Offset(cx, cy), alpha = a)
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, a: Float = 1f) = s.drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r), alpha = a)
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, b: Brush) = s.drawRoundRect(b, Offset(x, y), Size(w, h), CornerRadius(r, r))
    fun path(c: Color, a: Float = 1f, build: Path.() -> Unit) = s.drawPath(Path().apply(build), c, alpha = a)
    fun path(b: Brush, build: Path.() -> Unit) = s.drawPath(Path().apply(build), b)
    fun stroke(w: Float, c: Color, a: Float = 1f, build: Path.() -> Unit) =
        s.drawPath(Path().apply(build), c, alpha = a, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color, a: Float = 1f) = s.drawLine(c, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round, alpha = a)
    fun move(dx: Float, dy: Float, block: () -> Unit) = s.translate(dx, dy) { block() }
    fun turn(deg: Float, px: Float, py: Float, block: () -> Unit) = s.rotate(deg, Offset(px, py)) { block() }
    fun clip(build: Path.() -> Unit, block: () -> Unit) = s.clipPath(Path().apply(build)) { block() }

    // ---- paint
    fun vgrad(top: Color, bottom: Color, y0: Float, y1: Float): Brush = Brush.verticalGradient(listOf(top, bottom), startY = y0, endY = y1)
    fun hgrad(left: Color, right: Color, x0: Float, x1: Float): Brush = Brush.horizontalGradient(listOf(left, right), startX = x0, endX = x1)
    fun rgrad(inner: Color, outer: Color, cx: Float, cy: Float, r: Float): Brush = Brush.radialGradient(listOf(inner, outer), Offset(cx, cy), r)

    /** a ball of [base] lit from the top left */
    fun ball(cx: Float, cy: Float, rx: Float, ry: Float, base: Color) =
        oval(cx, cy, rx, ry, rgrad(base.light(0.3f), base.dark(0.16f), cx - rx * 0.35f, cy - ry * 0.45f, max(rx, ry) * 1.6f))

    /** the shoulders: the bottom of the picture, floating a little less than the head */
    fun bust(top: Color, bottom: Color, y: Float = 76f) = move(0f, bob * 0.4f) {
        path(vgrad(top, bottom, y, 104f)) {
            moveTo(0f, 110f); cubicTo(0f, y + 16f, 22f, y, 50f, y); cubicTo(78f, y, 100f, y + 16f, 100f, 110f); close()
        }
    }

    /** a person's head and neck: ears, neck, head */
    fun human(skin: Color, cy: Float = 50f) {
        rrect(43f, cy + 14f, 14f, 20f, 6f, skin.dark(0.14f))
        oval(24.5f, cy + 2f, 3.6f, 5.2f, skin.dark(0.06f)); oval(75.5f, cy + 2f, 3.6f, 5.2f, skin.dark(0.06f))
        ball(50f, cy, 25.5f, 27.5f, skin)
    }

    // ---- face parts
    /** a big glossy eye looking ([lx], [ly]) in -1..1; [open] 0..1 */
    fun eye(cx: Float, cy: Float, r: Float, iris: Color, open: Float = lid(), lx: Float = 0f, ly: Float = 0f, white: Color = Color.White) {
        if (open < 0.14f) { stroke(r * 0.3f, Ink) { moveTo(cx - r * 0.9f, cy); quadraticBezierTo(cx, cy + r * 0.4f, cx + r * 0.9f, cy) }; return }
        oval(cx, cy, r, r * 1.12f * open, white)
        val ix = cx + lx * r * 0.3f; val iy = cy + ly * r * 0.25f
        oval(ix, iy, r * 0.66f, r * 0.7f * open, iris)
        oval(ix, iy, r * 0.34f, r * 0.38f * open, Ink)
        if (open > 0.5f) { circle(ix - r * 0.24f, iy - r * 0.28f * open, r * 0.2f, Color.White); circle(ix + r * 0.22f, iy + r * 0.2f * open, r * 0.09f, Color.White, 0.8f) }
    }

    /** a round dark eye with a shine: for the simple faces */
    fun dotEye(cx: Float, cy: Float, r: Float, open: Float = lid(), lx: Float = 0f, ly: Float = 0f, c: Color = Ink) {
        if (open < 0.14f) { stroke(r * 0.5f, c) { moveTo(cx - r * 0.8f, cy); quadraticBezierTo(cx, cy + r * 0.55f, cx + r * 0.8f, cy) }; return }
        oval(cx + lx * r * 0.25f, cy + ly * r * 0.2f, r * 0.84f, r * open, c)
        circle(cx + lx * r * 0.25f - r * 0.28f, cy + ly * r * 0.2f - r * 0.3f * open, r * 0.3f, Color.White)
        circle(cx + lx * r * 0.25f + r * 0.3f, cy + ly * r * 0.2f + r * 0.28f * open, r * 0.13f, Color.White, 0.7f)
    }

    fun smile(cx: Float, cy: Float, w: Float, depth: Float, sw: Float = 1.7f, c: Color = Ink) =
        stroke(sw, c) { moveTo(cx - w / 2, cy); quadraticBezierTo(cx, cy + depth * 2, cx + w / 2, cy) }

    /** an open smile: dark inside, a tongue, optionally teeth */
    fun grin(cx: Float, cy: Float, w: Float, h: Float, teeth: Boolean = true) {
        val shape: Path.() -> Unit = { moveTo(cx - w / 2, cy); quadraticBezierTo(cx, cy + h * 2f, cx + w / 2, cy); close() }
        path(Color(0xFF4A1620), build = shape)
        clip(shape) {
            oval(cx, cy + h * 0.95f, w * 0.28f, h * 0.5f, Color(0xFFFF7A8A))
            if (teeth) rrect(cx - w * 0.4f, cy - 0.3f, w * 0.8f, h * 0.32f, 0.8f, Color.White)
        }
    }

    fun blush(cx: Float, cy: Float, rx: Float = 4.6f, ry: Float = 2.8f, c: Color = Color(0xFFFF6F91)) = oval(cx, cy, rx, ry, c, 0.45f)

    fun brow(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 2f, c: Color = Ink) = line(x1, y1, x2, y2, w, c)

    /** a four-pointed twinkle that swells and fades twice in the loop */
    fun sparkle(cx: Float, cy: Float, r: Float, c: Color = Color.White, phase: Float = 0f) {
        val k = sin(ph * 2 + phase) * 0.5f + 0.5f
        val rr = r * (0.3f + 0.7f * k)
        path(c, 0.5f + 0.5f * k) {
            moveTo(cx, cy - rr); quadraticBezierTo(cx, cy, cx + rr, cy); quadraticBezierTo(cx, cy, cx, cy + rr)
            quadraticBezierTo(cx, cy, cx - rr, cy); quadraticBezierTo(cx, cy, cx, cy - rr); close()
        }
    }

    /** a soft sheen over the top left of a round head */
    fun sheen(cx: Float, cy: Float, rx: Float, ry: Float) = oval(cx - rx * 0.38f, cy - ry * 0.52f, rx * 0.34f, ry * 0.2f, Color.White, 0.16f)
}

/** The picture [art] at time [t] of its loop, filling the square of the scope: a soft light from the top left over the disc, then the figure */
internal fun DrawScope.drawAvatarArt(art: ArtDraw, t: Float, side: Float = size.minDimension) {
    val k = side / 100f
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent), Offset(side * 0.3f, side * 0.2f), side * 0.8f), size = Size(side, side))
    withTransform({ scale(k, k, Offset.Zero) }) { Rig(this, t).art() }
}

// ------------------------------------------------------------------------------------------------ the pictures

private val ninja: ArtDraw = {
    val hood = Color(0xFF2B3556)
    bust(Color(0xFF34406C), Color(0xFF161C34))
    move(0f, bob * 0.5f) { path(Color(0xFFE2364B)) { moveTo(24f, 74f); quadraticBezierTo(50f, 86f, 76f, 74f); lineTo(72f, 84f); quadraticBezierTo(50f, 95f, 28f, 84f); close() } }
    move(0f, bob) {
        val f = sin(ph * 2)
        path(Color(0xFFC72B3F)) { moveTo(70f, 36f); cubicTo(82f, 33f + f * 3, 90f, 40f + f * 5, 99f, 33f + f * 8); lineTo(97f, 42f + f * 8); cubicTo(88f, 46f + f * 4, 80f, 45f + f * 2, 70f, 43f); close() }
        path(Color(0xFFA81F33)) { moveTo(70f, 40f); cubicTo(80f, 42f - f * 3, 88f, 50f - f * 5, 95f, 49f - f * 7); lineTo(96f, 56f - f * 7); cubicTo(86f, 55f - f * 4, 78f, 49f - f * 2, 70f, 46f); close() }
        ball(50f, 50f, 27.5f, 27f, hood)
        rrect(24f, 42.5f, 52f, 18f, 9f, vgrad(Color(0xFFF6D2AA), Color(0xFFE2AE80), 42f, 60f))
        eye(39.5f, 52.5f, 4.6f, Color(0xFF3A2A22), lid(), 0.3f)
        eye(60.5f, 52.5f, 4.6f, Color(0xFF3A2A22), lid(), 0.3f)
        brow(33f, 45.8f, 44f, 48.2f, 2.3f); brow(67f, 45.8f, 56f, 48.2f, 2.3f)
        rrect(23.5f, 31.5f, 53f, 10.5f, 3f, Color(0xFFE2364B))
        rrect(36f, 30.5f, 28f, 12.5f, 3.5f, vgrad(Color(0xFFE4EAF4), Color(0xFF8793AD), 30f, 43f))
        path(Ink) { moveTo(50f, 33.2f); lineTo(54.4f, 36.8f); lineTo(50f, 40.4f); lineTo(45.6f, 36.8f); close() }
        sheen(50f, 50f, 27.5f, 27f)
    }
    sparkle(86f, 22f, 4f, Color(0xFFFFE08A)); sparkle(13f, 70f, 3f, Color(0xFFFFE08A), 2f)
}

private val samurai: ArtDraw = {
    bust(Color(0xFFC4283A), Color(0xFF6E1220))
    path(Color(0xFFF4E9DA)) { moveTo(38f, 76f); lineTo(50f, 94f); lineTo(62f, 76f); lineTo(57f, 76f); lineTo(50f, 87f); lineTo(43f, 76f); close() }
    move(0f, bob) {
        human(Color(0xFFF0C29A))
        // topknot and hair
        ball(50f, 17f, 6.6f, 6.4f, Color(0xFF2B2833)); rrect(46f, 20.5f, 8f, 4f, 1.4f, Color(0xFFE2364B))
        path(vgrad(Color(0xFF34313F), Ink, 20f, 38f)) { moveTo(23.8f, 49f); cubicTo(20f, 27f, 33f, 21f, 50f, 21f); cubicTo(67f, 21f, 80f, 27f, 76.2f, 49f); cubicTo(71f, 39f, 62f, 37f, 50f, 37f); cubicTo(38f, 37f, 29f, 39f, 23.8f, 49f); close() }
        rrect(23.5f, 36f, 53f, 6.4f, 3f, Color(0xFFF7F2EA)); circle(50f, 39.2f, 2.5f, Color(0xFFE2364B))
        val f = sin(ph * 2)
        path(Color(0xFFF7F2EA)) { moveTo(73f, 37f); quadraticBezierTo(86f, 33f + f * 3, 94f, 38f + f * 6); lineTo(92f, 43f + f * 6); quadraticBezierTo(84f, 40f + f * 2, 73f, 42f); close() }
        dotEye(39f, 51f, 3.2f, lid()); dotEye(61f, 51f, 3.2f, lid())
        brow(33f, 45f, 44.5f, 47.5f, 2.4f); brow(67f, 45f, 55.5f, 47.5f, 2.4f)
        line(34f, 46f, 45f, 58f, 1.2f, Color(0xFFD9534F), 0.9f)
        line(44f, 62f, 56f, 62.5f, 1.7f, Color(0xFF8A4B3A))
    }
    // petals drifting down
    for (i in 0..4) {
        val y = -8f + ((u + i * 0.2f) % 1f) * 116f
        val x = 12f + i * 19f + sin(ph + i) * 5f
        turn(sin(ph * 2 + i) * 40f, x, y) { oval(x, y, 2.6f, 1.6f, Color(0xFFFFB3C7)) }
    }
}

private val astronaut: ArtDraw = {
    bust(Color(0xFFF1F4FA), Color(0xFFB4BFD6))
    rrect(18f, 89f, 64f, 4f, 2f, Color(0xFFFF8A3D)); rrect(10f, 97f, 80f, 4f, 2f, Color(0xFFFF8A3D))
    circle(50f, 84f, 3f, Color(0xFFE5484D), 0.55f + 0.45f * (sin(ph * 2) * 0.5f + 0.5f))
    move(0f, bob) {
        line(72f, 24f, 79f, 12f, 2f, Color(0xFF9AA7C0)); circle(79.5f, 11f, 3.1f, Color(0xFFFF5A5A), 0.55f + 0.45f * (sin(ph * 2) * 0.5f + 0.5f))
        rrect(30f, 69f, 40f, 9f, 4.5f, vgrad(Color(0xFFC3CCDD), Color(0xFF8793AD), 69f, 78f))
        ball(50f, 47f, 33f, 32f, Color(0xFFF2F5FB))
        val visor: Path.() -> Unit = { addOval(androidx.compose.ui.geometry.Rect(26f, 30f, 74f, 68f)) }
        path(rgrad(Color(0xFF3A5FB0), Color(0xFF0B1030), 42f, 40f, 34f), build = visor)
        clip(visor) {
            // the eyes glow in the dark of the visor
            oval(41.5f, 50f, 3.4f, 4.4f * lid(), Color(0xFF7DF0FF)); oval(58.5f, 50f, 3.4f, 4.4f * lid(), Color(0xFF7DF0FF))
            smile(50f, 58f, 9f, 2.6f, 1.4f, Color(0xFF7DF0FF))
            // a band of light sweeps over the glass
            val x = -20f + 140f * ((u * 1f) % 1f)
            turn(24f, 50f, 50f) { rrect(x - 40f, 20f, 7f, 60f, 3f, Color.White, 0.22f) }
        }
        stroke(2.6f, Color.White, 0.6f) { moveTo(32f, 45f); quadraticBezierTo(35f, 36f, 45f, 34f) }
        circle(33f, 51f, 1.3f, Color.White, 0.6f)
    }
    sparkle(14f, 26f, 4.5f); sparkle(88f, 40f, 3.4f, phase = 1.6f); sparkle(84f, 72f, 3f, Color(0xFFFFE08A), 3f)
}

private val cat: ArtDraw = {
    move(0f, bob) {
        val tw = bump(2.4f, 0.22f)
        turn(-10f * tw, 76f, 36f) { path(Color(0xFFE98A3C)) { moveTo(60f, 34f); lineTo(80f, 8f); lineTo(88f, 44f); close() }; path(Color(0xFFFFB3B8)) { moveTo(68f, 33f); lineTo(80f, 17f); lineTo(84f, 40f); close() } }
        path(Color(0xFFE98A3C)) { moveTo(40f, 34f); lineTo(20f, 8f); lineTo(12f, 44f); close() }; path(Color(0xFFFFB3B8)) { moveTo(32f, 33f); lineTo(20f, 17f); lineTo(16f, 40f); close() }
        ball(50f, 57f, 38f, 31f, Color(0xFFF29A4A))
        for (dx in floatArrayOf(-7f, 0f, 7f)) line(50f + dx, 31f, 50f + dx * 0.9f, 39f, 2.6f, Color(0xFFC96A22))
        oval(50f, 68f, 14.5f, 10f, Color(0xFFFFEBD2))
        dotEye(35f, 57f, 4.8f, lid(), 0.3f); dotEye(65f, 57f, 4.8f, lid(), 0.3f)
        path(Color(0xFFFF6F8E)) { moveTo(46.5f, 63f); lineTo(53.5f, 63f); lineTo(50f, 67f); close() }
        stroke(1.4f, Ink) { moveTo(50f, 67f); lineTo(50f, 69f); moveTo(50f, 69f); quadraticBezierTo(46f, 74f, 42f, 69.5f); moveTo(50f, 69f); quadraticBezierTo(54f, 74f, 58f, 69.5f) }
        for (sgn in floatArrayOf(-1f, 1f)) for (i in 0..2) line(50f + sgn * 22f, 66f + i * 3.4f, 50f + sgn * 40f, 61f + i * 7f, 0.9f, Color.White, 0.8f)
        blush(26f, 66f, 4.8f, 3f); blush(74f, 66f, 4.8f, 3f)
    }
}

private val ghost: ArtDraw = {
    val fl = bob * 1.7f
    move(0f, fl) {
        oval(50f, 52f, 38f, 40f, Color(0xFFBFD4FF), 0.2f)
        val w = sin(ph) * 2.2f
        path(vgrad(Color.White, Color(0xFFD7E0F4), 18f, 92f)) {
            moveTo(22f, 86f); lineTo(22f, 48f); cubicTo(22f, 28f, 36f, 17f, 50f, 17f); cubicTo(64f, 17f, 78f, 28f, 78f, 48f); lineTo(78f, 86f)
            quadraticBezierTo(70f, 95f + w, 64f, 86f); quadraticBezierTo(57f, 77f - w, 50f, 86f); quadraticBezierTo(43f, 95f + w, 36f, 86f); quadraticBezierTo(29f, 77f - w, 22f, 86f); close()
        }
        oval(20f, 62f - sin(ph) * 3f, 4.2f, 6.5f, Color(0xFFE9EFFC)); oval(80f, 62f + sin(ph) * 3f, 4.2f, 6.5f, Color(0xFFE9EFFC))
        dotEye(39f, 47f, 4.8f, lid(), 0.4f); dotEye(61f, 47f, 4.8f, lid(), 0.4f)
        val m = 3.4f + sin(ph * 2) * 0.9f
        oval(50f, 60f, 3.2f, m, Color(0xFF3A2D55))
        blush(30f, 56f); blush(70f, 56f)
    }
    sparkle(16f, 22f, 4.2f); sparkle(88f, 34f, 3.2f, phase = 1.5f); sparkle(82f, 80f, 3.6f, phase = 3f)
}

private val robot: ArtDraw = {
    bust(Color(0xFF4A5878), Color(0xFF262E44))
    rrect(16f, 90f, 68f, 5f, 2.5f, Color(0xFF8FA6C8), 0.5f)
    move(0f, bob) {
        line(50f, 14f, 50f, 24f, 2.2f, Color(0xFF8FA0BC))
        val p = sin(ph * 2) * 0.5f + 0.5f
        circle(50f, 11.5f, 7f, Color(0xFFFFC53D), 0.22f * p); circle(50f, 11.5f, 4.4f, lerp(Color(0xFFFF5A5A), Color(0xFFFFD84A), p))
        circle(19.5f, 48f, 4.8f, Color(0xFF55627C)); circle(80.5f, 48f, 4.8f, Color(0xFF55627C))
        rrect(20f, 22f, 60f, 54f, 15f, vgrad(Color(0xFFA7BBDA), Color(0xFF5F7396), 22f, 76f))
        rrect(25f, 31f, 50f, 36f, 10f, Color(0xFF0E1426))
        val h = 12f * lid()
        rrect(33f, 49f - h / 2f - 6f, 11f, max(h, 1.6f), 4f, Color(0xFF7DF0FF), 0.3f); rrect(56f, 49f - h / 2f - 6f, 11f, max(h, 1.6f), 4f, Color(0xFF7DF0FF), 0.3f)
        rrect(34f, 49f - h / 2f - 6f + 1f, 9f, max(h - 2f, 1.2f), 3.4f, Color(0xFF7DF0FF)); rrect(57f, 49f - h / 2f - 6f + 1f, 9f, max(h - 2f, 1.2f), 3.4f, Color(0xFF7DF0FF))
        for (i in 0..4) { val bh = 2.4f + 5.5f * (sin(ph * 2 + i * 1.4f) * 0.5f + 0.5f); rrect(36f + i * 5.6f, 59f - bh / 2f, 3.4f, bh, 1.4f, Color(0xFF7DF0FF)) }
        oval(34f, 25f, 9f, 2.6f, Color.White, 0.3f)
    }
}

private val wizard: ArtDraw = {
    bust(Color(0xFF4A66E0), Color(0xFF26338F))
    rrect(44f, 80f, 12f, 30f, 3f, Color(0xFFFFD25A), 0.9f)
    move(0f, bob) {
        human(Color(0xFFF2C9A4), 54f)
        // beard
        path(vgrad(Color.White, Color(0xFFD6DBE8), 58f, 94f)) { moveTo(27f, 56f); cubicTo(24f, 82f, 38f, 96f, 50f, 96f); cubicTo(62f, 96f, 76f, 82f, 73f, 56f); cubicTo(66f, 68f, 58f, 66f, 50f, 68f); cubicTo(42f, 66f, 34f, 68f, 27f, 56f); close() }
        circle(50f, 60f, 3.6f, Color(0xFFF08F80))
        dotEye(39f, 52f, 3.2f, lid()); dotEye(61f, 52f, 3.2f, lid())
        line(33f, 46f, 44f, 47.5f, 3f, Color.White); line(67f, 46f, 56f, 47.5f, 3f, Color.White)
        oval(43f, 65f, 6.5f, 3f, Color.White); oval(57f, 65f, 6.5f, 3f, Color.White)
        // hat
        val sw = sin(ph) * 2.6f
        oval(50f, 40f, 36f, 7.5f, Color(0xFF3B2A86))
        path(vgrad(Color(0xFF6A4BD6), Color(0xFF362A8A), 6f, 40f)) { moveTo(29f, 40f); quadraticBezierTo(42f, 26f, 60f + sw, 5f); quadraticBezierTo(62f + sw, 22f, 71f, 40f); close() }
        rrect(29f, 34.5f, 42f, 5f, 2.4f, Color(0xFFFFD25A))
        sparkle(48f, 27f, 3.2f, Color(0xFFFFE08A)); sparkle(58f, 17f, 2.4f, Color(0xFFFFE08A), 2f)
    }
    sparkle(12f, 30f, 3.6f, Color(0xFFBFD4FF)); sparkle(88f, 62f, 4.2f, Color(0xFFFFE08A), 1.4f)
}

private val alien: ArtDraw = {
    bust(Color(0xFF3A4A8C), Color(0xFF1C2350))
    path(Color(0xFF7DF0FF)) { moveTo(38f, 77f); lineTo(50f, 90f); lineTo(62f, 77f); lineTo(58f, 76f); lineTo(50f, 84f); lineTo(42f, 76f); close() }
    move(0f, bob) {
        for (sgn in floatArrayOf(-1f, 1f)) {
            val sw = sin(ph + (if (sgn < 0) 0f else 1.6f)) * 3f
            stroke(1.8f, Color(0xFF63C27A)) { moveTo(50f + sgn * 10f, 26f); quadraticBezierTo(50f + sgn * 14f, 14f, 50f + sgn * 20f + sw, 7f) }
            circle(50f + sgn * 20f + sw, 7f, 3.6f, Color(0xFFFFE066)); circle(50f + sgn * 20f + sw, 7f, 6.4f, Color(0xFFFFE066), 0.25f)
        }
        path(rgrad(Color(0xFF9DF0A8), Color(0xFF3FAE62), 40f, 34f, 52f)) {
            moveTo(50f, 17f); cubicTo(78f, 17f, 85f, 46f, 73f, 65f); cubicTo(67f, 74f, 58f, 79f, 50f, 79f); cubicTo(42f, 79f, 33f, 74f, 27f, 65f); cubicTo(15f, 46f, 22f, 17f, 50f, 17f); close()
        }
        val o = lid()
        turn(-18f, 36f, 52f) { oval(36f, 52f, 9.5f, 13f * o + 0.5f, Ink); circle(32.5f, 47f, 2.6f, Color.White, 0.9f * min(1f, o * 2)) }
        turn(18f, 64f, 52f) { oval(64f, 52f, 9.5f, 13f * o + 0.5f, Ink); circle(60.5f, 47f, 2.6f, Color.White, 0.9f * min(1f, o * 2)) }
        smile(50f, 68f, 8f, 2.2f, 1.4f, Color(0xFF1F6B3A))
    }
    sparkle(14f, 40f, 3.6f); sparkle(88f, 54f, 3f, phase = 2f); sparkle(20f, 84f, 3.2f, Color(0xFFFFE08A), 1f)
}

/** id -> how it is drawn; the order here is the order of the picker inside each group */
internal val avatarArt: Map<String, ArtDraw> = linkedMapOf(
    "ninja" to ninja, "samurai" to samurai, "astronaut" to astronaut, "wizard" to wizard, "cyber" to cyber, "pirate" to pirate, "knight" to knight, "hacker" to hacker, "dj" to dj, "vampire" to vampire,
    "cat" to cat, "fox" to fox, "panda" to panda, "bunny" to bunny, "penguin" to penguin, "dragon" to dragon, "owl" to owl, "frog" to frog,
    "ghost" to ghost, "alien" to alien, "robot" to robot, "skull" to skull, "oni" to oni, "monster" to monster, "invader" to invader,
)
