package com.lagradost.desktop.ui.shell

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The picture of a profile, the way Netflix does it: a flat colour and a character with a face, drawn here with shapes (no image files).
// Every character is drawn on a square of 0..1 and takes [p], the progress of its little action (0 = at rest): a blink, a wiggle of the
// ears, a wink of light. The action runs for about a second every few seconds (see ProfileAvatar), between those the picture stands still,
// so a window full of profile pictures does not keep the window redrawing.

/** The flat colours behind a character */
val avatarBackgrounds = listOf(
    Color(0xFF3D8BFD), // blue
    Color(0xFFE5484D), // red
    Color(0xFFFFC53D), // yellow
    Color(0xFF30C48D), // green
    Color(0xFF8E5BF5), // purple
    Color(0xFFFF8A3D), // orange
    Color(0xFFFF5CA8), // pink
    Color(0xFF19B5C4), // teal
    Color(0xFF5B6478), // slate
    Color(0xFF1B1E2B), // midnight
)

/** [bg] is the background it looks best on (an index of [avatarBackgrounds]); the viewer can choose another */
class AvatarCharacter(val id: String, val label: String, val bg: Int, val group: String = "Characters", val draw: DrawScope.(Float) -> Unit)

val avatarCharacters: List<AvatarCharacter> = listOf(
    AvatarCharacter("smile", "Happy", 0) { p -> charSmile(p) },
    AvatarCharacter("wink", "Wink", 3) { p -> charWink(p) },
    AvatarCharacter("cool", "Cool", 4) { p -> charCool(p) },
    AvatarCharacter("tongue", "Silly", 2) { p -> charTongue(p) },
    AvatarCharacter("stars", "Starstruck", 9) { p -> charStars(p) },
    AvatarCharacter("hearts", "In love", 6) { p -> charHearts(p) },
    AvatarCharacter("sleepy", "Sleepy", 4) { p -> charSleepy(p) },
    AvatarCharacter("cat", "Cat", 5) { p -> charCat(p) },
    AvatarCharacter("fox", "Fox", 8) { p -> charFox(p) },
    AvatarCharacter("panda", "Panda", 7) { p -> charPanda(p) },
    AvatarCharacter("bear", "Bear", 2) { p -> charBear(p) },
    AvatarCharacter("bunny", "Bunny", 6) { p -> charBunny(p) },
    AvatarCharacter("owl", "Owl", 7) { p -> charOwl(p) },
    AvatarCharacter("penguin", "Penguin", 0) { p -> charPenguin(p) },
    AvatarCharacter("frog", "Frog", 6) { p -> charFrog(p) },
    AvatarCharacter("robot", "Robot", 0) { p -> charRobot(p) },
    AvatarCharacter("alien", "Alien", 4) { p -> charAlien(p) },
    AvatarCharacter("ghost", "Ghost", 9) { p -> charGhost(p) },
    AvatarCharacter("monster", "Monster", 2) { p -> charMonster(p) },
    AvatarCharacter("ninja", "Ninja", 4) { p -> charNinja(p) },
    // anime
    AvatarCharacter("goku", "Goku", 7, "Anime") { p -> charGoku(p) },
    AvatarCharacter("naruto", "Naruto", 0, "Anime") { p -> charNaruto(p) },
    AvatarCharacter("kakashi", "Kakashi", 4, "Anime") { p -> charKakashi(p) },
    AvatarCharacter("luffy", "Luffy", 7, "Anime") { p -> charLuffy(p) },
    AvatarCharacter("pikachu", "Pikachu", 3, "Anime") { p -> charPikachu(p) },
    AvatarCharacter("totoro", "Totoro", 3, "Anime") { p -> charTotoro(p) },
    AvatarCharacter("doraemon", "Doraemon", 2, "Anime") { p -> charDoraemon(p) },
    AvatarCharacter("gojo", "Gojo", 0, "Anime") { p -> charGojo(p) },
    AvatarCharacter("tanjiro", "Tanjiro", 8, "Anime") { p -> charTanjiro(p) },
    AvatarCharacter("anya", "Anya", 4, "Anime") { p -> charAnya(p) },
    AvatarCharacter("saitama", "Saitama", 1, "Anime") { p -> charSaitama(p) },
    // films and series
    AvatarCharacter("vader", "Darth Vader", 1, "Movies and series") { p -> charVader(p) },
    AvatarCharacter("trooper", "Stormtrooper", 8, "Movies and series") { p -> charTrooper(p) },
    AvatarCharacter("yoda", "Yoda", 4, "Movies and series") { p -> charYoda(p) },
    AvatarCharacter("batman", "Batman", 8, "Movies and series") { p -> charBatman(p) },
    AvatarCharacter("joker", "Joker", 4, "Movies and series") { p -> charJoker(p) },
    AvatarCharacter("spiderman", "Spider-Man", 0, "Movies and series") { p -> charSpiderMan(p) },
    AvatarCharacter("ironman", "Iron Man", 9, "Movies and series") { p -> charIronMan(p) },
    AvatarCharacter("harry", "Harry Potter", 1, "Movies and series") { p -> charHarry(p) },
    AvatarCharacter("minion", "Minion", 7, "Movies and series") { p -> charMinion(p) },
    AvatarCharacter("shrek", "Shrek", 2, "Movies and series") { p -> charShrek(p) },
    AvatarCharacter("wednesday", "Wednesday", 9, "Movies and series") { p -> charWednesday(p) },
    AvatarCharacter("guard", "Squid Game guard", 9, "Movies and series") { p -> charGuard(p) },
)

fun avatarCharacter(id: String?): AvatarCharacter = avatarCharacters.firstOrNull { it.id == id } ?: avatarCharacters.first()

// ---------------------------------------------------------------------------------------------------------------------------------
// the pen

internal val Ink = Color(0xFF1B1520)
internal val Pink = Color(0xFFFF8FB0)
internal val Cream = Color(0xFFFFF1DC)
internal val Face = Color(0xFFFFD43B)

/** 0 at rest, up to 1 and back to 0 during a blink that happens near the start of the action */
internal fun blinkOf(p: Float): Float = if (p in 0.06f..0.26f) sin(((p - 0.06f) / 0.20f * PI).toFloat()) else 0f

/** A wiggle that dies away (0 at rest and at the end) */
internal fun wiggleOf(p: Float): Float = sin(p * 4f * PI.toFloat()) * (1f - p)

internal fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) {
    val u = size.minDimension
    drawOval(c, Offset((cx - rx) * u, (cy - ry) * u), Size(rx * 2f * u, ry * 2f * u))
}

internal fun DrawScope.disc(cx: Float, cy: Float, r: Float, c: Color) {
    val u = size.minDimension
    drawCircle(c, r * u, Offset(cx * u, cy * u))
}

/** An eye that closes by [b] (0 open, 1 shut) */
internal fun DrawScope.eye(cx: Float, cy: Float, rx: Float, ry: Float, c: Color, b: Float) = oval(cx, cy, rx, (ry * (1f - 0.92f * b)).coerceAtLeast(0.008f), c)

/** A piece of the outline of an ellipse (centre, width, height, angles in degrees, clockwise from 3 o'clock: 90 is the bottom) */
internal fun DrawScope.arcLine(cx: Float, cy: Float, w: Float, h: Float, from: Float, sweep: Float, c: Color, sw: Float) {
    val u = size.minDimension
    drawArc(c, from, sweep, false, Offset((cx - w / 2f) * u, (cy - h / 2f) * u), Size(w * u, h * u), style = Stroke(sw * u, cap = StrokeCap.Round))
}

internal fun DrawScope.seg(x1: Float, y1: Float, x2: Float, y2: Float, c: Color, sw: Float) {
    val u = size.minDimension
    drawLine(c, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), sw * u, StrokeCap.Round)
}

internal fun DrawScope.poly(c: Color, vararg p: Float) {
    val u = size.minDimension
    val path = Path().apply {
        moveTo(p[0] * u, p[1] * u)
        var i = 2
        while (i < p.size) { lineTo(p[i] * u, p[i + 1] * u); i += 2 }
        close()
    }
    drawPath(path, c)
}

internal fun DrawScope.rrect(l: Float, t: Float, r: Float, b: Float, radius: Float, c: Color) {
    val u = size.minDimension
    drawRoundRect(c, Offset(l * u, t * u), Size((r - l) * u, (b - t) * u), CornerRadius(radius * u))
}

internal fun DrawScope.turn(deg: Float, px: Float, py: Float, block: DrawScope.() -> Unit) = rotate(deg, Offset(px * size.minDimension, py * size.minDimension), block)

internal fun DrawScope.star(cx: Float, cy: Float, r: Float, deg: Float, c: Color) {
    val u = size.minDimension
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = ((deg + i * 36f - 90f) * PI / 180.0).toFloat()
        val x = (cx + rad * cos(a)) * u
        val y = (cy + rad * sin(a)) * u
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, c)
}

internal fun DrawScope.heart(cx: Float, cy: Float, s: Float, c: Color) {
    val u = size.minDimension
    val path = Path().apply {
        moveTo(cx * u, (cy + s * 0.9f) * u)
        cubicTo((cx - s * 1.4f) * u, (cy - s * 0.1f) * u, (cx - s * 0.7f) * u, (cy - s * 1.1f) * u, cx * u, (cy - s * 0.35f) * u)
        cubicTo((cx + s * 0.7f) * u, (cy - s * 1.1f) * u, (cx + s * 1.4f) * u, (cy - s * 0.1f) * u, cx * u, (cy + s * 0.9f) * u)
        close()
    }
    drawPath(path, c)
}

internal fun DrawScope.zed(x: Float, y: Float, s: Float, c: Color) {
    val u = size.minDimension
    val path = Path().apply {
        moveTo((x - s) * u, (y - s) * u); lineTo((x + s) * u, (y - s) * u); lineTo((x - s) * u, (y + s) * u); lineTo((x + s) * u, (y + s) * u)
    }
    drawPath(path, c, style = Stroke(s * 0.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------------------------------------------------------------
// the faces that fill the whole picture

private fun DrawScope.charSmile(p: Float) {
    val b = blinkOf(p)
    eye(0.35f, 0.42f, 0.058f, 0.10f, Ink, b); eye(0.65f, 0.42f, 0.058f, 0.10f, Ink, b)
    arcLine(0.5f, 0.50f, 0.52f, 0.40f, 22f, 136f, Ink, 0.07f)
}

private fun DrawScope.charWink(p: Float) {
    eye(0.35f, 0.42f, 0.058f, 0.10f, Ink, blinkOf(p))
    arcLine(0.65f, 0.47f, 0.17f, 0.16f, 200f, 140f, Ink, 0.055f)
    arcLine(0.5f, 0.52f, 0.54f, 0.42f, 20f, 140f, Ink, 0.07f)
    oval(0.2f, 0.62f, 0.07f, 0.045f, Pink.copy(alpha = 0.6f)); oval(0.8f, 0.62f, 0.07f, 0.045f, Pink.copy(alpha = 0.6f))
}

private fun DrawScope.charCool(p: Float) {
    val lens = Color(0xFF14121A)
    rrect(0.12f, 0.32f, 0.46f, 0.54f, 0.07f, lens); rrect(0.54f, 0.32f, 0.88f, 0.54f, 0.07f, lens)
    seg(0.46f, 0.37f, 0.54f, 0.37f, lens, 0.035f)
    seg(0.12f, 0.36f, 0.04f, 0.33f, lens, 0.03f); seg(0.88f, 0.36f, 0.96f, 0.33f, lens, 0.03f)
    // a glint that crosses both lenses
    val a = sin(p * PI.toFloat())
    if (a > 0.01f) {
        val sx = 0.05f + 0.20f * p
        for (l in floatArrayOf(0.12f, 0.54f)) seg(l + sx, 0.50f, l + sx + 0.06f, 0.36f, Color.White.copy(alpha = 0.55f * a), 0.025f)
    }
    arcLine(0.54f, 0.66f, 0.38f, 0.26f, 25f, 110f, Ink, 0.06f)
}

private fun DrawScope.charTongue(p: Float) {
    val w = wiggleOf(p)
    val u = size.minDimension
    seg(0.22f, 0.33f, 0.38f, 0.42f, Ink, 0.06f); seg(0.38f, 0.42f, 0.22f, 0.51f, Ink, 0.06f)
    seg(0.78f, 0.33f, 0.62f, 0.42f, Ink, 0.06f); seg(0.62f, 0.42f, 0.78f, 0.51f, Ink, 0.06f)
    // the mouth is the lower half of an ellipse; the tongue hangs out of it
    drawArc(Ink, 0f, 180f, true, Offset(0.25f * u, 0.38f * u), Size(0.50f * u, 0.40f * u))
    oval(0.5f + 0.03f * w, 0.73f, 0.12f, 0.10f, Pink)
    seg(0.5f + 0.03f * w, 0.66f, 0.5f + 0.03f * w, 0.76f, Color(0xFFE5557F), 0.02f)
}

private fun DrawScope.charStars(p: Float) {
    disc(0.5f, 0.5f, 0.40f, Face)
    val k = 1f + 0.28f * sin(p * PI.toFloat())
    star(0.34f, 0.40f, 0.12f * k, p * 72f, Color(0xFFFF7A1A)); star(0.66f, 0.40f, 0.12f * k, -p * 72f, Color(0xFFFF7A1A))
    val u = size.minDimension
    drawArc(Ink, 0f, 180f, true, Offset(0.30f * u, 0.50f * u), Size(0.40f * u, 0.34f * u))
    oval(0.5f, 0.76f, 0.09f, 0.05f, Pink)
}

private fun DrawScope.charHearts(p: Float) {
    disc(0.5f, 0.5f, 0.40f, Face)
    val beat = 1f + 0.30f * (sin(p * 3f * PI.toFloat()).coerceAtLeast(0f))
    heart(0.34f, 0.42f, 0.085f * beat, Color(0xFFE5304F)); heart(0.66f, 0.42f, 0.085f * beat, Color(0xFFE5304F))
    arcLine(0.5f, 0.56f, 0.34f, 0.26f, 25f, 130f, Ink, 0.05f)
    oval(0.24f, 0.64f, 0.06f, 0.04f, Pink.copy(alpha = 0.7f)); oval(0.76f, 0.64f, 0.06f, 0.04f, Pink.copy(alpha = 0.7f))
}

private fun DrawScope.charSleepy(p: Float) {
    disc(0.5f, 0.55f, 0.38f, Color(0xFF9CC3FF))
    arcLine(0.36f, 0.50f, 0.16f, 0.12f, 20f, 140f, Ink, 0.045f); arcLine(0.64f, 0.50f, 0.16f, 0.12f, 20f, 140f, Ink, 0.045f)
    oval(0.5f, 0.69f, 0.045f, 0.055f + 0.012f * sin(p * 2f * PI.toFloat()), Ink)
    oval(0.26f, 0.62f, 0.05f, 0.035f, Pink.copy(alpha = 0.6f)); oval(0.74f, 0.62f, 0.05f, 0.035f, Pink.copy(alpha = 0.6f))
    // the Z's: two at rest, floating up while it sleeps
    if (p <= 0.001f) {
        zed(0.70f, 0.24f, 0.05f, Color.White); zed(0.79f, 0.14f, 0.032f, Color.White.copy(alpha = 0.8f))
    } else {
        for (k in 0 until 2) {
            val ph = (p + k * 0.45f) % 1f
            zed(0.66f + 0.12f * ph, 0.30f - 0.18f * ph, 0.03f + 0.02f * (1f - k), Color.White.copy(alpha = sin(ph * PI.toFloat())))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// animals

private fun DrawScope.charCat(p: Float) {
    val fur = Color(0xFFFFE0B8)
    val w = wiggleOf(p)
    turn(9f * w, 0.30f, 0.40f) { poly(fur, 0.14f, 0.46f, 0.17f, 0.08f, 0.44f, 0.26f); poly(Pink, 0.20f, 0.38f, 0.21f, 0.17f, 0.36f, 0.28f) }
    poly(fur, 0.86f, 0.46f, 0.83f, 0.08f, 0.56f, 0.26f); poly(Pink, 0.80f, 0.38f, 0.79f, 0.17f, 0.64f, 0.28f)
    disc(0.5f, 0.58f, 0.36f, fur)
    val b = blinkOf(p)
    eye(0.36f, 0.54f, 0.05f, 0.07f, Ink, b); eye(0.64f, 0.54f, 0.05f, 0.07f, Ink, b)
    poly(Pink, 0.46f, 0.62f, 0.54f, 0.62f, 0.5f, 0.67f)
    arcLine(0.455f, 0.685f, 0.09f, 0.07f, 0f, 170f, Ink, 0.02f); arcLine(0.545f, 0.685f, 0.09f, 0.07f, 10f, 170f, Ink, 0.02f)
    for (s in floatArrayOf(-1f, 1f)) {
        seg(0.5f + s * 0.19f, 0.64f, 0.5f + s * 0.42f, 0.60f, Ink.copy(alpha = 0.55f), 0.014f)
        seg(0.5f + s * 0.19f, 0.69f, 0.5f + s * 0.42f, 0.71f, Ink.copy(alpha = 0.55f), 0.014f)
    }
}

private fun DrawScope.charFox(p: Float) {
    val orange = Color(0xFFF5762B)
    val white = Color(0xFFFFF4E6)
    val w = wiggleOf(p)
    turn(-8f * w, 0.28f, 0.38f) { poly(orange, 0.12f, 0.50f, 0.10f, 0.06f, 0.44f, 0.28f); poly(Ink, 0.19f, 0.40f, 0.17f, 0.16f, 0.34f, 0.30f) }
    poly(orange, 0.88f, 0.50f, 0.90f, 0.06f, 0.56f, 0.28f); poly(Ink, 0.81f, 0.40f, 0.83f, 0.16f, 0.66f, 0.30f)
    disc(0.5f, 0.57f, 0.35f, orange)
    poly(white, 0.17f, 0.62f, 0.36f, 0.58f, 0.5f, 0.66f, 0.64f, 0.58f, 0.83f, 0.62f, 0.5f, 0.92f)
    val b = blinkOf(p)
    eye(0.35f, 0.50f, 0.045f, 0.065f, Ink, b); eye(0.65f, 0.50f, 0.045f, 0.065f, Ink, b)
    oval(0.5f, 0.79f, 0.05f, 0.038f, Ink)
}

private fun DrawScope.charPanda(p: Float) {
    disc(0.22f, 0.24f, 0.12f, Ink); disc(0.78f, 0.24f, 0.12f, Ink)
    oval(0.5f, 0.56f, 0.38f, 0.34f, Color.White)
    turn(-22f, 0.34f, 0.52f) { oval(0.34f, 0.52f, 0.075f, 0.10f, Ink) }
    turn(22f, 0.66f, 0.52f) { oval(0.66f, 0.52f, 0.075f, 0.10f, Ink) }
    val b = blinkOf(p)
    eye(0.35f, 0.52f, 0.03f, 0.04f, Color.White, b); eye(0.65f, 0.52f, 0.03f, 0.04f, Color.White, b)
    oval(0.5f, 0.64f, 0.055f, 0.04f, Ink)
    arcLine(0.5f, 0.675f, 0.14f, 0.09f, 20f, 140f, Ink, 0.022f)
}

private fun DrawScope.charBear(p: Float) {
    val fur = Color(0xFF9A6240)
    val ear = Color(0xFFD9A47F)
    disc(0.20f, 0.26f, 0.13f, fur); disc(0.20f, 0.26f, 0.07f, ear)
    disc(0.80f, 0.26f, 0.13f, fur); disc(0.80f, 0.26f, 0.07f, ear)
    disc(0.5f, 0.57f, 0.36f, fur)
    oval(0.5f, 0.69f, 0.17f, 0.13f, Color(0xFFE8C49C))
    val b = blinkOf(p)
    eye(0.36f, 0.50f, 0.04f, 0.055f, Ink, b); eye(0.64f, 0.50f, 0.04f, 0.055f, Ink, b)
    oval(0.5f, 0.63f, 0.055f, 0.04f, Ink)
    seg(0.5f, 0.66f, 0.5f, 0.70f, Ink, 0.02f)
    arcLine(0.5f, 0.71f, 0.14f, 0.09f, 20f, 140f, Ink, 0.02f)
}

private fun DrawScope.charBunny(p: Float) {
    val w = wiggleOf(p)
    turn(-8f + 7f * w, 0.36f, 0.40f) { oval(0.34f, 0.24f, 0.075f, 0.20f, Color.White); oval(0.34f, 0.26f, 0.04f, 0.15f, Pink) }
    turn(8f - 7f * w, 0.64f, 0.40f) { oval(0.66f, 0.24f, 0.075f, 0.20f, Color.White); oval(0.66f, 0.26f, 0.04f, 0.15f, Pink) }
    oval(0.5f, 0.64f, 0.32f, 0.28f, Color.White)
    val b = blinkOf(p)
    eye(0.38f, 0.60f, 0.04f, 0.055f, Ink, b); eye(0.62f, 0.60f, 0.04f, 0.055f, Ink, b)
    oval(0.5f, 0.68f, 0.035f, 0.025f, Pink)
    arcLine(0.465f, 0.705f, 0.07f, 0.06f, 0f, 170f, Ink, 0.016f); arcLine(0.535f, 0.705f, 0.07f, 0.06f, 10f, 170f, Ink, 0.016f)
    oval(0.27f, 0.70f, 0.05f, 0.032f, Pink.copy(alpha = 0.55f)); oval(0.73f, 0.70f, 0.05f, 0.032f, Pink.copy(alpha = 0.55f))
}

private fun DrawScope.charOwl(p: Float) {
    val brown = Color(0xFF8D6E63)
    poly(brown, 0.16f, 0.34f, 0.17f, 0.06f, 0.40f, 0.22f); poly(brown, 0.84f, 0.34f, 0.83f, 0.06f, 0.60f, 0.22f)
    oval(0.5f, 0.56f, 0.38f, 0.38f, brown)
    oval(0.5f, 0.78f, 0.22f, 0.15f, Color(0xFFBCA297))
    val look = 0.025f * sin(p * 2f * PI.toFloat())
    val b = blinkOf(p)
    disc(0.34f, 0.46f, 0.15f, Color(0xFFFFF3E0)); disc(0.66f, 0.46f, 0.15f, Color(0xFFFFF3E0))
    eye(0.34f + look, 0.47f, 0.075f, 0.075f, Ink, b); eye(0.66f + look, 0.47f, 0.075f, 0.075f, Ink, b)
    poly(Color(0xFFFFB300), 0.45f, 0.56f, 0.55f, 0.56f, 0.5f, 0.68f)
}

private fun DrawScope.charPenguin(p: Float) {
    disc(0.5f, 0.52f, 0.40f, Color(0xFF263238))
    disc(0.37f, 0.47f, 0.17f, Color.White); disc(0.63f, 0.47f, 0.17f, Color.White); oval(0.5f, 0.65f, 0.21f, 0.19f, Color.White)
    val b = blinkOf(p)
    eye(0.38f, 0.46f, 0.045f, 0.06f, Ink, b); eye(0.62f, 0.46f, 0.045f, 0.06f, Ink, b)
    poly(Color(0xFFFF9800), 0.43f, 0.58f, 0.57f, 0.58f, 0.5f, 0.70f)
    oval(0.27f, 0.60f, 0.045f, 0.03f, Pink.copy(alpha = 0.6f)); oval(0.73f, 0.60f, 0.045f, 0.03f, Pink.copy(alpha = 0.6f))
}

private fun DrawScope.charFrog(p: Float) {
    val green = Color(0xFF4CC36B)
    val dark = Color(0xFF1F7A3E)
    val b = blinkOf(p)
    oval(0.5f, 0.62f, 0.42f, 0.30f, green)
    disc(0.30f, 0.34f, 0.14f, green); disc(0.70f, 0.34f, 0.14f, green)
    disc(0.30f, 0.34f, 0.095f, Color.White); disc(0.70f, 0.34f, 0.095f, Color.White)
    eye(0.31f, 0.35f, 0.045f, 0.045f, Ink, b); eye(0.69f, 0.35f, 0.045f, 0.045f, Ink, b)
    arcLine(0.5f, 0.60f, 0.60f, 0.34f, 22f, 136f, dark, 0.035f)
    disc(0.45f, 0.52f, 0.012f, dark); disc(0.55f, 0.52f, 0.012f, dark)
    oval(0.19f, 0.68f, 0.05f, 0.032f, Pink.copy(alpha = 0.55f)); oval(0.81f, 0.68f, 0.05f, 0.032f, Pink.copy(alpha = 0.55f))
}

// ---------------------------------------------------------------------------------------------------------------------------------
// others

private fun DrawScope.charRobot(p: Float) {
    val metal = Color(0xFFD5DEE6)
    val steel = Color(0xFF9AA7B3)
    val dark = Color(0xFF263238)
    seg(0.5f, 0.24f, 0.5f, 0.13f, steel, 0.035f)
    val glow = sin(p * 2f * PI.toFloat()).let { it * it }
    disc(0.5f, 0.10f, 0.045f, Color(0xFFFF5252).copy(alpha = 0.55f + 0.45f * glow))
    rrect(0.08f, 0.44f, 0.16f, 0.64f, 0.03f, steel); rrect(0.84f, 0.44f, 0.92f, 0.64f, 0.03f, steel)
    rrect(0.16f, 0.24f, 0.84f, 0.86f, 0.12f, metal)
    rrect(0.24f, 0.34f, 0.76f, 0.58f, 0.07f, dark)
    val b = blinkOf(p)
    eye(0.38f, 0.46f, 0.07f, 0.07f, Color(0xFF22D3EE), b); eye(0.62f, 0.46f, 0.07f, 0.07f, Color(0xFF22D3EE), b)
    rrect(0.34f, 0.67f, 0.66f, 0.77f, 0.04f, dark)
    for (i in 0 until 4) seg(0.41f + 0.06f * i, 0.69f, 0.41f + 0.06f * i, 0.75f, Color.White.copy(alpha = 0.6f), 0.014f)
}

private fun DrawScope.charAlien(p: Float) {
    val green = Color(0xFF7BE06B)
    val b = blinkOf(p)
    oval(0.5f, 0.52f, 0.36f, 0.40f, green)
    turn(-22f, 0.34f, 0.50f) { eye(0.34f, 0.50f, 0.10f, 0.15f, Ink, b) }
    turn(22f, 0.66f, 0.50f) { eye(0.66f, 0.50f, 0.10f, 0.15f, Ink, b) }
    if (b < 0.5f) { disc(0.31f, 0.44f, 0.022f, Color.White); disc(0.63f, 0.44f, 0.022f, Color.White) }
    arcLine(0.5f, 0.73f, 0.14f, 0.07f, 20f, 140f, Ink, 0.02f)
}

private fun DrawScope.charGhost(p: Float) {
    val u = size.minDimension
    translate(0f, -0.035f * u * sin(p * 2f * PI.toFloat())) {
        val path = Path().apply {
            moveTo(0.2f * u, 0.86f * u); lineTo(0.2f * u, 0.46f * u)
            cubicTo(0.2f * u, 0.12f * u, 0.8f * u, 0.12f * u, 0.8f * u, 0.46f * u)
            lineTo(0.8f * u, 0.86f * u)
            quadraticTo(0.7f * u, 0.96f * u, 0.6f * u, 0.86f * u)
            quadraticTo(0.5f * u, 0.76f * u, 0.4f * u, 0.86f * u)
            quadraticTo(0.3f * u, 0.96f * u, 0.2f * u, 0.86f * u)
            close()
        }
        drawPath(path, Color(0xFFF2F2FF))
        val b = blinkOf(p)
        eye(0.38f, 0.46f, 0.05f, 0.075f, Ink, b); eye(0.62f, 0.46f, 0.05f, 0.075f, Ink, b)
        oval(0.5f, 0.63f, 0.05f, 0.06f, Ink)
        oval(0.29f, 0.58f, 0.05f, 0.032f, Pink.copy(alpha = 0.6f)); oval(0.71f, 0.58f, 0.05f, 0.032f, Pink.copy(alpha = 0.6f))
    }
}

private fun DrawScope.charMonster(p: Float) {
    val purple = Color(0xFF7E57C2)
    poly(Cream, 0.22f, 0.30f, 0.16f, 0.06f, 0.38f, 0.22f); poly(Cream, 0.78f, 0.30f, 0.84f, 0.06f, 0.62f, 0.22f)
    disc(0.5f, 0.56f, 0.38f, purple)
    val u = size.minDimension
    val b = blinkOf(p)
    oval(0.5f, 0.46f, 0.17f, 0.17f * (1f - 0.9f * b).coerceAtLeast(0.05f), Color.White)
    if (b < 0.6f) disc(0.5f + 0.06f * sin(p * 2f * PI.toFloat()), 0.46f, 0.08f, Ink)
    drawArc(Ink, 0f, 180f, true, Offset(0.28f * u, 0.54f * u), Size(0.44f * u, 0.26f * u))
    poly(Color.White, 0.34f, 0.67f, 0.42f, 0.67f, 0.38f, 0.74f); poly(Color.White, 0.46f, 0.67f, 0.54f, 0.67f, 0.5f, 0.74f); poly(Color.White, 0.58f, 0.67f, 0.66f, 0.67f, 0.62f, 0.74f)
}

private fun DrawScope.charNinja(p: Float) {
    val w = wiggleOf(p)
    disc(0.5f, 0.54f, 0.40f, Color(0xFF262626))
    rrect(0.14f, 0.40f, 0.86f, 0.66f, 0.13f, Color(0xFFFFD6B0))
    val b = blinkOf(p)
    eye(0.36f, 0.54f, 0.055f, 0.07f, Ink, b); eye(0.64f, 0.54f, 0.055f, 0.07f, Ink, b)
    seg(0.25f, 0.44f, 0.42f, 0.49f, Ink, 0.03f); seg(0.75f, 0.44f, 0.58f, 0.49f, Ink, 0.03f)
    rrect(0.10f, 0.26f, 0.90f, 0.37f, 0.04f, Color(0xFFE5484D))
    turn(10f * w, 0.88f, 0.31f) { poly(Color(0xFFE5484D), 0.88f, 0.28f, 1.00f, 0.18f, 0.96f, 0.32f); poly(Color(0xFFE5484D), 0.88f, 0.32f, 1.00f, 0.40f, 0.94f, 0.26f) }
}
