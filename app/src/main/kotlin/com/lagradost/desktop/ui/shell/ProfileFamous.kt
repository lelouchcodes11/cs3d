package com.lagradost.desktop.ui.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.sin

// Simple flat portraits of characters people know from films, series and anime, in the style of ProfileCharacters.kt: a few shapes each, the
// one or two things everybody recognises (the hair, the mask, the hat, the glasses), and the same small action every few seconds.
// These are tiny drawings made with shapes, not the studios' artwork; the characters and their names belong to their owners.

private val Skin = Color(0xFFFFD6B0)

// ---------------------------------------------------------------------------------------------------------------------------------
// anime

internal fun DrawScope.charGoku(p: Float) {
    val b = blinkOf(p)
    poly(Color(0xFFFF8A00), 0.10f, 1.0f, 0.26f, 0.84f, 0.74f, 0.84f, 0.90f, 1.0f)
    poly(Color(0xFF1E6FD9), 0.42f, 0.84f, 0.58f, 0.84f, 0.5f, 0.97f)
    disc(0.5f, 0.60f, 0.29f, Skin)
    // the hair: black spikes standing up, a notch of forehead between the two sides
    poly(
        Color(0xFF16121A),
        0.20f, 0.56f, 0.10f, 0.28f, 0.28f, 0.34f, 0.24f, 0.08f, 0.42f, 0.24f, 0.50f, 0.0f, 0.58f, 0.24f, 0.76f, 0.08f, 0.72f, 0.34f, 0.90f, 0.28f, 0.80f, 0.56f,
        0.72f, 0.46f, 0.62f, 0.40f, 0.50f, 0.46f, 0.38f, 0.40f, 0.28f, 0.46f,
    )
    seg(0.30f, 0.52f, 0.43f, 0.55f, Ink, 0.035f); seg(0.70f, 0.52f, 0.57f, 0.55f, Ink, 0.035f)
    eye(0.37f, 0.62f, 0.04f, 0.055f, Ink, b); eye(0.63f, 0.62f, 0.04f, 0.055f, Ink, b)
    arcLine(0.5f, 0.72f, 0.26f, 0.16f, 25f, 130f, Ink, 0.03f)
}

internal fun DrawScope.charNaruto(p: Float) {
    val b = blinkOf(p)
    val w = wiggleOf(p)
    poly(Color(0xFFFF7A1A), 0.10f, 1.0f, 0.26f, 0.84f, 0.74f, 0.84f, 0.90f, 1.0f)
    disc(0.5f, 0.60f, 0.29f, Skin)
    poly(
        Color(0xFFFFD43B),
        0.20f, 0.52f, 0.10f, 0.30f, 0.26f, 0.34f, 0.20f, 0.12f, 0.38f, 0.26f, 0.50f, 0.06f, 0.62f, 0.26f, 0.80f, 0.12f, 0.74f, 0.34f, 0.90f, 0.30f, 0.80f, 0.52f,
        0.70f, 0.40f, 0.30f, 0.40f,
    )
    // the forehead protector and its plate; the band's tail flutters
    rrect(0.19f, 0.38f, 0.81f, 0.49f, 0.02f, Color(0xFF2B4C9B))
    rrect(0.36f, 0.385f, 0.64f, 0.485f, 0.02f, Color(0xFFC9D3DD))
    arcLine(0.5f, 0.435f, 0.10f, 0.05f, 200f, 290f, Color(0xFF55606C), 0.014f)
    eye(0.37f, 0.60f, 0.04f, 0.055f, Ink, b); eye(0.63f, 0.60f, 0.04f, 0.055f, Ink, b)
    // the whisker marks
    for (s in floatArrayOf(-1f, 1f)) for (k in 0 until 3) seg(0.5f + s * 0.17f, 0.64f + 0.045f * k, 0.5f + s * 0.27f, 0.62f + 0.05f * k + 0.01f * w, Ink.copy(alpha = 0.8f), 0.014f)
    arcLine(0.5f, 0.70f, 0.30f, 0.20f, 20f, 140f, Ink, 0.03f)
}

internal fun DrawScope.charKakashi(p: Float) {
    val b = blinkOf(p)
    poly(Color(0xFF37474F), 0.10f, 1.0f, 0.26f, 0.84f, 0.74f, 0.84f, 0.90f, 1.0f)
    disc(0.5f, 0.60f, 0.29f, Skin)
    // silver hair that falls to one side
    poly(
        Color(0xFFD3DAE2),
        0.20f, 0.56f, 0.12f, 0.26f, 0.30f, 0.30f, 0.26f, 0.06f, 0.46f, 0.20f, 0.60f, 0.0f, 0.66f, 0.22f, 0.84f, 0.10f, 0.78f, 0.34f, 0.92f, 0.34f, 0.82f, 0.56f,
        0.72f, 0.40f, 0.28f, 0.42f,
    )
    // the mask over the lower face, and the protector pulled down over one eye
    poly(Color(0xFF37474F), 0.22f, 0.62f, 0.78f, 0.62f, 0.76f, 0.80f, 0.5f, 0.90f, 0.24f, 0.80f)
    rrect(0.19f, 0.38f, 0.81f, 0.47f, 0.02f, Color(0xFF2B4C9B))
    turn(-6f, 0.34f, 0.55f) { rrect(0.20f, 0.46f, 0.48f, 0.64f, 0.05f, Color(0xFF2B4C9B)) }
    disc(0.62f, 0.56f, 0.045f, Ink)
    if (b > 0.5f) seg(0.57f, 0.56f, 0.67f, 0.56f, Skin, 0.07f)
    seg(0.55f, 0.50f, 0.70f, 0.50f, Ink, 0.02f)
}

internal fun DrawScope.charLuffy(p: Float) {
    val b = blinkOf(p)
    val w = wiggleOf(p)
    disc(0.5f, 0.62f, 0.29f, Skin)
    poly(Ink, 0.22f, 0.56f, 0.20f, 0.36f, 0.5f, 0.32f, 0.80f, 0.36f, 0.78f, 0.56f, 0.70f, 0.44f, 0.30f, 0.44f)
    eye(0.38f, 0.60f, 0.04f, 0.055f, Ink, b); eye(0.62f, 0.60f, 0.04f, 0.055f, Ink, b)
    seg(0.34f, 0.67f, 0.37f, 0.71f, Color(0xFFB0563A), 0.014f); seg(0.31f, 0.69f, 0.38f, 0.69f, Color(0xFFB0563A), 0.014f)
    // the big grin
    val u = size.minDimension
    drawArc(Ink, 0f, 180f, true, androidx.compose.ui.geometry.Offset(0.28f * u, 0.60f * u), androidx.compose.ui.geometry.Size(0.44f * u, 0.30f * u))
    rrect(0.32f, 0.745f, 0.68f, 0.775f, 0.01f, Color.White)
    // the straw hat, its brim wide and its red band; it tips a little
    turn(-3f * w, 0.5f, 0.3f) {
        oval(0.5f, 0.34f, 0.30f, 0.21f, Color(0xFFF4C542))
        rrect(0.20f, 0.32f, 0.80f, 0.42f, 0.02f, Color(0xFFD32F2F))
        oval(0.5f, 0.42f, 0.47f, 0.085f, Color(0xFFF4C542))
        oval(0.5f, 0.405f, 0.47f, 0.02f, Color(0xFFD9A524))
    }
}

internal fun DrawScope.charPikachu(p: Float) {
    val yellow = Color(0xFFFFDD33)
    val w = wiggleOf(p)
    val b = blinkOf(p)
    turn(8f * w, 0.28f, 0.44f) { poly(yellow, 0.20f, 0.50f, 0.08f, 0.06f, 0.36f, 0.34f); poly(Ink, 0.08f, 0.06f, 0.116f, 0.186f, 0.164f, 0.138f) }
    turn(-8f * w, 0.72f, 0.44f) { poly(yellow, 0.80f, 0.50f, 0.92f, 0.06f, 0.64f, 0.34f); poly(Ink, 0.92f, 0.06f, 0.884f, 0.186f, 0.836f, 0.138f) }
    oval(0.5f, 0.60f, 0.38f, 0.32f, yellow)
    disc(0.19f, 0.66f, 0.075f, Color(0xFFE5484D)); disc(0.81f, 0.66f, 0.075f, Color(0xFFE5484D))
    eye(0.33f, 0.54f, 0.055f, 0.065f, Ink, b); eye(0.67f, 0.54f, 0.055f, 0.065f, Ink, b)
    if (b < 0.5f) { disc(0.345f, 0.525f, 0.018f, Color.White); disc(0.685f, 0.525f, 0.018f, Color.White) }
    oval(0.5f, 0.60f, 0.018f, 0.012f, Ink)
    arcLine(0.465f, 0.64f, 0.08f, 0.07f, 0f, 170f, Ink, 0.016f); arcLine(0.535f, 0.64f, 0.08f, 0.07f, 10f, 170f, Ink, 0.016f)
}

internal fun DrawScope.charTotoro(p: Float) {
    val gray = Color(0xFF8C9AAE)
    val w = wiggleOf(p)
    turn(5f * w, 0.30f, 0.30f) { poly(gray, 0.20f, 0.34f, 0.18f, 0.0f, 0.38f, 0.24f) }
    poly(gray, 0.80f, 0.34f, 0.82f, 0.0f, 0.62f, 0.24f)
    oval(0.5f, 0.58f, 0.42f, 0.40f, gray)
    oval(0.5f, 0.76f, 0.30f, 0.20f, Color(0xFFF1EBDD))
    for (k in 0 until 3) arcLine(0.5f + 0.1f * (k - 1), 0.74f + 0.02f * (k % 2), 0.08f, 0.05f, 200f, 140f, gray, 0.014f)
    val b = blinkOf(p)
    disc(0.34f, 0.44f, 0.08f, Color.White); disc(0.66f, 0.44f, 0.08f, Color.White)
    eye(0.35f, 0.45f, 0.035f, 0.035f, Ink, b); eye(0.65f, 0.45f, 0.035f, 0.035f, Ink, b)
    oval(0.5f, 0.50f, 0.05f, 0.03f, Ink)
    arcLine(0.5f, 0.56f, 0.52f, 0.20f, 18f, 144f, Ink, 0.018f)
    for (s in floatArrayOf(-1f, 1f)) for (k in 0 until 3) seg(0.5f + s * 0.26f, 0.52f + 0.03f * k, 0.5f + s * 0.45f, 0.48f + 0.05f * k, Ink.copy(alpha = 0.6f), 0.012f)
}

internal fun DrawScope.charDoraemon(p: Float) {
    val blue = Color(0xFF1E88E5)
    val b = blinkOf(p)
    disc(0.5f, 0.50f, 0.42f, blue)
    oval(0.5f, 0.58f, 0.34f, 0.30f, Color.White)
    // the two big eyes that share one outline
    oval(0.43f, 0.35f, 0.095f, 0.125f, Ink); oval(0.57f, 0.35f, 0.095f, 0.125f, Ink)
    oval(0.43f, 0.35f, 0.08f, 0.11f, Color.White); oval(0.57f, 0.35f, 0.08f, 0.11f, Color.White)
    eye(0.465f, 0.37f, 0.025f, 0.03f, Ink, b); eye(0.535f, 0.37f, 0.025f, 0.03f, Ink, b)
    disc(0.5f, 0.50f, 0.055f, Color(0xFFE5484D)); disc(0.485f, 0.485f, 0.015f, Color.White)
    seg(0.5f, 0.555f, 0.5f, 0.66f, Ink, 0.014f)
    arcLine(0.5f, 0.56f, 0.58f, 0.32f, 22f, 136f, Ink, 0.02f)
    for (s in floatArrayOf(-1f, 1f)) for (k in 0 until 3) seg(0.5f + s * 0.20f, 0.54f + 0.04f * k, 0.5f + s * 0.36f, 0.50f + 0.06f * k, Ink, 0.012f)
    rrect(0.22f, 0.89f, 0.78f, 0.96f, 0.03f, Color(0xFFE5484D))
    disc(0.5f, 0.97f, 0.05f, Color(0xFFFFC107))
}

internal fun DrawScope.charGojo(p: Float) {
    val b = blinkOf(p)
    poly(Color(0xFF1F2A44), 0.10f, 1.0f, 0.22f, 0.84f, 0.78f, 0.84f, 0.90f, 1.0f)
    disc(0.5f, 0.60f, 0.28f, Skin)
    poly(
        Color(0xFFF2F5F9),
        0.18f, 0.58f, 0.10f, 0.30f, 0.28f, 0.36f, 0.26f, 0.08f, 0.44f, 0.26f, 0.56f, 0.02f, 0.62f, 0.26f, 0.82f, 0.10f, 0.76f, 0.38f, 0.92f, 0.32f, 0.82f, 0.58f,
        0.72f, 0.44f, 0.50f, 0.38f, 0.28f, 0.44f,
    )
    // the blindfold
    rrect(0.18f, 0.46f, 0.82f, 0.60f, 0.03f, Color(0xFF16121A))
    if (b > 0.0f) seg(0.2f, 0.53f, 0.8f, 0.53f, Color.White.copy(alpha = 0.35f * b), 0.012f)
    arcLine(0.5f, 0.70f, 0.18f, 0.12f, 30f, 120f, Ink, 0.025f)
}

internal fun DrawScope.charTanjiro(p: Float) {
    val b = blinkOf(p)
    // the green and black chequered coat
    for (i in 0 until 6) for (j in 0 until 2) rrect(0.08f + 0.14f * i, 0.84f + 0.085f * j, 0.22f + 0.14f * i, 0.925f + 0.085f * j, 0.0f, if ((i + j) % 2 == 0) Color(0xFF1B5E20) else Color(0xFF111111))
    disc(0.5f, 0.60f, 0.29f, Skin)
    val hair = Color(0xFF5A1F2B)
    poly(
        hair,
        0.20f, 0.60f, 0.12f, 0.34f, 0.26f, 0.36f, 0.24f, 0.12f, 0.40f, 0.26f, 0.52f, 0.06f, 0.64f, 0.26f, 0.80f, 0.12f, 0.76f, 0.38f, 0.90f, 0.36f, 0.80f, 0.60f,
        0.74f, 0.46f, 0.62f, 0.40f, 0.52f, 0.52f, 0.42f, 0.42f, 0.28f, 0.48f,
    )
    // the scar on the forehead
    poly(Color(0xFFC62828), 0.60f, 0.36f, 0.68f, 0.40f, 0.64f, 0.52f, 0.58f, 0.46f)
    oval(0.37f, 0.62f, 0.05f, 0.065f * (1f - 0.9f * b).coerceAtLeast(0.1f), Color(0xFF9B1C31)); oval(0.63f, 0.62f, 0.05f, 0.065f * (1f - 0.9f * b).coerceAtLeast(0.1f), Color(0xFF9B1C31))
    if (b < 0.5f) { disc(0.37f, 0.63f, 0.02f, Ink); disc(0.63f, 0.63f, 0.02f, Ink) }
    arcLine(0.5f, 0.74f, 0.20f, 0.12f, 30f, 120f, Ink, 0.022f)
    // the earring
    rrect(0.775f, 0.66f, 0.82f, 0.78f, 0.01f, Color(0xFFFFFFFF)); rrect(0.775f, 0.66f, 0.82f, 0.70f, 0.0f, Color(0xFFD32F2F))
}

internal fun DrawScope.charAnya(p: Float) {
    val pink = Color(0xFFFFB3C7)
    val b = blinkOf(p)
    oval(0.5f, 0.48f, 0.40f, 0.42f, pink)
    disc(0.5f, 0.62f, 0.27f, Skin)
    poly(pink, 0.22f, 0.58f, 0.26f, 0.30f, 0.5f, 0.40f, 0.74f, 0.30f, 0.78f, 0.58f, 0.64f, 0.46f, 0.5f, 0.54f, 0.36f, 0.46f)
    poly(Ink, 0.30f, 0.26f, 0.32f, 0.04f, 0.42f, 0.22f); poly(Ink, 0.70f, 0.26f, 0.68f, 0.04f, 0.58f, 0.22f)
    oval(0.38f, 0.64f, 0.06f, 0.075f * (1f - 0.9f * b).coerceAtLeast(0.1f), Color(0xFF3FB864)); oval(0.62f, 0.64f, 0.06f, 0.075f * (1f - 0.9f * b).coerceAtLeast(0.1f), Color(0xFF3FB864))
    if (b < 0.5f) { disc(0.38f, 0.645f, 0.025f, Ink); disc(0.62f, 0.645f, 0.025f, Ink); disc(0.395f, 0.62f, 0.014f, Color.White); disc(0.635f, 0.62f, 0.014f, Color.White) }
    oval(0.26f, 0.74f, 0.045f, 0.03f, Pink.copy(alpha = 0.6f)); oval(0.74f, 0.74f, 0.045f, 0.03f, Pink.copy(alpha = 0.6f))
    // her smug smile
    arcLine(0.5f, 0.77f, 0.16f, 0.09f, 20f, 100f, Ink, 0.02f)
}

internal fun DrawScope.charSaitama(p: Float) {
    val b = blinkOf(p)
    poly(Color(0xFFFFD43B), 0.10f, 1.0f, 0.24f, 0.86f, 0.76f, 0.86f, 0.90f, 1.0f)
    poly(Color.White, 0.0f, 1.0f, 0.10f, 0.80f, 0.18f, 1.0f); poly(Color.White, 1.0f, 1.0f, 0.90f, 0.80f, 0.82f, 1.0f)
    disc(0.5f, 0.50f, 0.37f, Skin)
    // a bald head shines
    val a = sin(p * PI.toFloat())
    arcLine(0.5f, 0.50f, 0.58f, 0.58f, 200f, 50f, Color.White.copy(alpha = 0.35f + 0.5f * a), 0.035f)
    eye(0.37f, 0.52f, 0.032f, 0.05f, Ink, b); eye(0.63f, 0.52f, 0.032f, 0.05f, Ink, b)
    seg(0.30f, 0.45f, 0.43f, 0.46f, Ink, 0.016f); seg(0.70f, 0.45f, 0.57f, 0.46f, Ink, 0.016f)
    arcLine(0.5f, 0.60f, 0.06f, 0.05f, 20f, 120f, Ink.copy(alpha = 0.6f), 0.012f)
    seg(0.43f, 0.72f, 0.57f, 0.72f, Ink, 0.022f)
}

// ---------------------------------------------------------------------------------------------------------------------------------
// films and series

internal fun DrawScope.charVader(p: Float) {
    val helmet = Color(0xFF26272E)
    oval(0.5f, 0.42f, 0.36f, 0.36f, helmet)
    poly(helmet, 0.16f, 0.50f, 0.84f, 0.50f, 0.90f, 0.86f, 0.10f, 0.86f)
    // the shine on the dome
    arcLine(0.5f, 0.42f, 0.56f, 0.56f, 215f, 60f, Color.White.copy(alpha = 0.18f), 0.04f)
    val glint = sin(p * PI.toFloat())
    turn(-18f, 0.34f, 0.52f) { oval(0.34f, 0.52f, 0.12f, 0.075f, Color(0xFF050507)) }
    turn(18f, 0.66f, 0.52f) { oval(0.66f, 0.52f, 0.12f, 0.075f, Color(0xFF050507)) }
    if (glint > 0.02f) { disc(0.30f, 0.50f, 0.02f, Color.White.copy(alpha = 0.7f * glint)); disc(0.62f, 0.50f, 0.02f, Color.White.copy(alpha = 0.7f * glint)) }
    poly(Color(0xFF111115), 0.5f, 0.58f, 0.45f, 0.72f, 0.55f, 0.72f)
    rrect(0.34f, 0.74f, 0.66f, 0.86f, 0.03f, Color(0xFF111115))
    for (i in 0 until 5) seg(0.38f + 0.06f * i, 0.76f, 0.38f + 0.06f * i, 0.84f, Color(0xFF6B6E78), 0.014f)
}

internal fun DrawScope.charTrooper(p: Float) {
    val white = Color(0xFFF4F6F8)
    val grey = Color(0xFF3A3F46)
    // the dome, the narrower jaw and the grey ear covers
    disc(0.15f, 0.54f, 0.07f, grey); disc(0.85f, 0.54f, 0.07f, grey)
    oval(0.5f, 0.44f, 0.34f, 0.36f, white)
    poly(white, 0.20f, 0.56f, 0.80f, 0.56f, 0.72f, 0.90f, 0.28f, 0.90f)
    // the brow line, the two tapering lenses and the marks under them
    seg(0.22f, 0.30f, 0.78f, 0.30f, Color(0xFFD7DCE1), 0.012f)
    val glint = sin(p * PI.toFloat())
    poly(Ink, 0.23f, 0.35f, 0.47f, 0.40f, 0.45f, 0.55f, 0.29f, 0.53f)
    poly(Ink, 0.77f, 0.35f, 0.53f, 0.40f, 0.55f, 0.55f, 0.71f, 0.53f)
    if (glint > 0.02f) { seg(0.30f + 0.08f * p, 0.45f, 0.34f + 0.08f * p, 0.38f, Color.White.copy(alpha = 0.6f * glint), 0.016f); seg(0.56f + 0.08f * p, 0.45f, 0.60f + 0.08f * p, 0.38f, Color.White.copy(alpha = 0.6f * glint), 0.016f) }
    seg(0.32f, 0.60f, 0.38f, 0.68f, Ink, 0.022f); seg(0.68f, 0.60f, 0.62f, 0.68f, Ink, 0.022f)
    poly(grey, 0.5f, 0.56f, 0.465f, 0.68f, 0.535f, 0.68f)
    rrect(0.40f, 0.76f, 0.60f, 0.86f, 0.03f, grey)
    seg(0.46f, 0.78f, 0.46f, 0.84f, white, 0.014f); seg(0.54f, 0.78f, 0.54f, 0.84f, white, 0.014f)
}

internal fun DrawScope.charYoda(p: Float) {
    val green = Color(0xFF9CCB6A)
    val w = wiggleOf(p)
    val b = blinkOf(p)
    turn(5f * w, 0.30f, 0.52f) { poly(green, 0.30f, 0.48f, -0.02f, 0.40f, 0.26f, 0.68f) }
    turn(-5f * w, 0.70f, 0.52f) { poly(green, 0.70f, 0.48f, 1.02f, 0.40f, 0.74f, 0.68f) }
    oval(0.5f, 0.54f, 0.30f, 0.36f, green)
    // a few thin hairs
    for (k in 0 until 3) seg(0.46f + 0.04f * k, 0.20f, 0.44f + 0.06f * k, 0.10f, Color(0xFFE8E6D8), 0.012f)
    for (k in 0 until 3) arcLine(0.5f, 0.38f + 0.035f * k, 0.36f - 0.04f * k, 0.06f, 200f, 140f, Color(0xFF6E9A45), 0.012f)
    seg(0.30f, 0.50f, 0.44f, 0.47f, Color(0xFF6E9A45), 0.03f); seg(0.70f, 0.50f, 0.56f, 0.47f, Color(0xFF6E9A45), 0.03f)
    eye(0.38f, 0.56f, 0.05f, 0.06f, Ink, b); eye(0.62f, 0.56f, 0.05f, 0.06f, Ink, b)
    if (b < 0.5f) { disc(0.39f, 0.545f, 0.015f, Color.White); disc(0.63f, 0.545f, 0.015f, Color.White) }
    oval(0.5f, 0.66f, 0.03f, 0.02f, Color(0xFF6E9A45))
    arcLine(0.5f, 0.76f, 0.20f, 0.08f, 200f, 140f, Ink, 0.018f)
}

internal fun DrawScope.charBatman(p: Float) {
    val cowl = Color(0xFF16161C)
    poly(cowl, 0.18f, 0.40f, 0.20f, 0.0f, 0.40f, 0.22f); poly(cowl, 0.82f, 0.40f, 0.80f, 0.0f, 0.60f, 0.22f)
    disc(0.5f, 0.52f, 0.34f, cowl)
    poly(Skin, 0.24f, 0.66f, 0.76f, 0.66f, 0.70f, 0.84f, 0.5f, 0.94f, 0.30f, 0.84f)
    val glow = 0.75f + 0.25f * sin(p * PI.toFloat())
    poly(Color.White.copy(alpha = glow), 0.24f, 0.46f, 0.46f, 0.55f, 0.43f, 0.62f, 0.26f, 0.54f)
    poly(Color.White.copy(alpha = glow), 0.76f, 0.46f, 0.54f, 0.55f, 0.57f, 0.62f, 0.74f, 0.54f)
    seg(0.42f, 0.76f, 0.58f, 0.76f, Ink, 0.02f)
    arcLine(0.5f, 0.88f, 0.08f, 0.04f, 20f, 140f, Ink.copy(alpha = 0.4f), 0.012f)
}

internal fun DrawScope.charJoker(p: Float) {
    val b = blinkOf(p)
    poly(Color(0xFF6A1B9A), 0.10f, 1.0f, 0.22f, 0.86f, 0.78f, 0.86f, 0.90f, 1.0f)
    poly(Color(0xFF2E9E4A), 0.14f, 0.96f, 0.5f, 0.88f, 0.86f, 0.96f, 0.5f, 1.0f)
    disc(0.5f, 0.56f, 0.30f, Color(0xFFF4F4EC))
    // the green hair
    poly(
        Color(0xFF2E9E4A),
        0.20f, 0.52f, 0.12f, 0.28f, 0.28f, 0.32f, 0.24f, 0.08f, 0.42f, 0.24f, 0.52f, 0.02f, 0.62f, 0.24f, 0.78f, 0.08f, 0.74f, 0.32f, 0.90f, 0.28f, 0.80f, 0.52f,
        0.72f, 0.38f, 0.5f, 0.34f, 0.28f, 0.38f,
    )
    oval(0.38f, 0.50f, 0.07f, 0.06f, Ink); oval(0.62f, 0.50f, 0.07f, 0.06f, Ink)
    eye(0.38f, 0.50f, 0.025f, 0.025f, Color.White, b); eye(0.62f, 0.50f, 0.025f, 0.025f, Color.White, b)
    // the red grin with its teeth
    val u = size.minDimension
    drawArc(Color(0xFFD32F2F), 0f, 180f, true, androidx.compose.ui.geometry.Offset(0.24f * u, 0.60f * u), androidx.compose.ui.geometry.Size(0.52f * u, 0.30f * u))
    rrect(0.30f, 0.755f, 0.70f, 0.80f, 0.01f, Color.White)
    arcLine(0.5f, 0.68f, 0.64f, 0.30f, 20f, 140f, Color(0xFFD32F2F), 0.035f)
}

internal fun DrawScope.charSpiderMan(p: Float) {
    val red = Color(0xFFE23636)
    disc(0.5f, 0.50f, 0.42f, red)
    // the web
    val web = Ink.copy(alpha = 0.75f)
    for (i in 0 until 7) {
        val a = (i * 25.7f + 95f) * (PI / 180.0)
        seg(0.5f, 0.5f, 0.5f + 0.5f * kotlin.math.cos(a).toFloat(), 0.5f + 0.5f * sin(a).toFloat(), web, 0.012f)
    }
    for (r in floatArrayOf(0.14f, 0.26f, 0.38f)) arcLine(0.5f, 0.5f, r * 2f, r * 2f, 0f, 360f, web, 0.012f)
    // the two big lenses
    val glint = sin(p * PI.toFloat())
    poly(Ink, 0.15f, 0.40f, 0.47f, 0.50f, 0.43f, 0.69f, 0.20f, 0.62f)
    poly(Ink, 0.85f, 0.40f, 0.53f, 0.50f, 0.57f, 0.69f, 0.80f, 0.62f)
    poly(Color.White, 0.20f, 0.43f, 0.44f, 0.51f, 0.41f, 0.64f, 0.24f, 0.58f)
    poly(Color.White, 0.80f, 0.43f, 0.56f, 0.51f, 0.59f, 0.64f, 0.76f, 0.58f)
    if (glint > 0.02f) { seg(0.24f + 0.12f * p, 0.57f, 0.30f + 0.12f * p, 0.49f, Color(0xFFBFD9FF).copy(alpha = glint), 0.02f); seg(0.64f + 0.12f * p, 0.57f, 0.70f + 0.12f * p, 0.49f, Color(0xFFBFD9FF).copy(alpha = glint), 0.02f) }
}

internal fun DrawScope.charIronMan(p: Float) {
    val glow = 0.7f + 0.3f * sin(p * PI.toFloat())
    oval(0.5f, 0.50f, 0.36f, 0.42f, Color(0xFFC62828))
    poly(Color(0xFFFFC107), 0.28f, 0.34f, 0.72f, 0.34f, 0.70f, 0.70f, 0.58f, 0.88f, 0.42f, 0.88f, 0.30f, 0.70f)
    seg(0.5f, 0.34f, 0.5f, 0.88f, Color(0xFFD59B00), 0.012f)
    poly(Color(0xFFB71C1C), 0.28f, 0.34f, 0.72f, 0.34f, 0.71f, 0.40f, 0.29f, 0.40f)
    poly(Color(0xFFE8FBFF).copy(alpha = glow), 0.31f, 0.47f, 0.47f, 0.47f, 0.45f, 0.55f, 0.33f, 0.55f)
    poly(Color(0xFFE8FBFF).copy(alpha = glow), 0.69f, 0.47f, 0.53f, 0.47f, 0.55f, 0.55f, 0.67f, 0.55f)
    seg(0.44f, 0.76f, 0.56f, 0.76f, Color(0xFF8A6500), 0.018f)
    seg(0.40f, 0.62f, 0.44f, 0.70f, Color(0xFFD59B00), 0.012f); seg(0.60f, 0.62f, 0.56f, 0.70f, Color(0xFFD59B00), 0.012f)
}

internal fun DrawScope.charHarry(p: Float) {
    val b = blinkOf(p)
    poly(Color(0xFFB71C1C), 0.10f, 1.0f, 0.26f, 0.84f, 0.74f, 0.84f, 0.90f, 1.0f)
    for (k in 0 until 3) seg(0.14f + 0.28f * k, 0.99f, 0.22f + 0.28f * k, 0.88f, Color(0xFFFFC107), 0.03f)
    disc(0.5f, 0.58f, 0.29f, Skin)
    // untidy black hair
    oval(0.5f, 0.34f, 0.32f, 0.20f, Color(0xFF16121A))
    poly(Color(0xFF16121A), 0.22f, 0.40f, 0.14f, 0.54f, 0.26f, 0.50f); poly(Color(0xFF16121A), 0.78f, 0.40f, 0.86f, 0.54f, 0.74f, 0.50f)
    poly(Color(0xFF16121A), 0.36f, 0.32f, 0.44f, 0.46f, 0.30f, 0.46f); poly(Color(0xFF16121A), 0.52f, 0.30f, 0.62f, 0.44f, 0.46f, 0.46f)
    // the lightning scar
    poly(Color(0xFFD32F2F), 0.56f, 0.42f, 0.62f, 0.42f, 0.58f, 0.50f, 0.64f, 0.50f, 0.54f, 0.62f, 0.57f, 0.54f, 0.52f, 0.54f)
    // round glasses
    arcLine(0.37f, 0.58f, 0.22f, 0.22f, 0f, 360f, Ink, 0.022f); arcLine(0.63f, 0.58f, 0.22f, 0.22f, 0f, 360f, Ink, 0.022f)
    seg(0.48f, 0.57f, 0.52f, 0.57f, Ink, 0.02f)
    eye(0.37f, 0.58f, 0.03f, 0.04f, Color(0xFF2E7D32), b); eye(0.63f, 0.58f, 0.03f, 0.04f, Color(0xFF2E7D32), b)
    arcLine(0.5f, 0.73f, 0.16f, 0.10f, 30f, 120f, Ink, 0.02f)
}

internal fun DrawScope.charMinion(p: Float) {
    val yellow = Color(0xFFFFD43B)
    val look = 0.03f * sin(p * 2f * PI.toFloat())
    rrect(0.22f, 0.04f, 0.78f, 1.02f, 0.28f, yellow)
    for (k in 0 until 3) seg(0.44f + 0.05f * k, 0.10f, 0.42f + 0.07f * k, 0.03f + 0.02f * k, Ink, 0.012f)
    rrect(0.22f, 0.32f, 0.78f, 0.44f, 0.0f, Color(0xFF3A3A40))
    disc(0.5f, 0.38f, 0.18f, Color(0xFF9EA3AA))
    disc(0.5f, 0.38f, 0.135f, Color.White)
    disc(0.5f + look, 0.38f, 0.055f, Color(0xFF6D4C41)); disc(0.5f + look, 0.38f, 0.028f, Ink)
    arcLine(0.5f, 0.60f, 0.30f, 0.20f, 25f, 130f, Ink, 0.025f)
    rrect(0.22f, 0.76f, 0.78f, 1.02f, 0.10f, Color(0xFF1E5AA8))
    seg(0.30f, 0.76f, 0.28f, 0.56f, Color(0xFF1E5AA8), 0.045f); seg(0.70f, 0.76f, 0.72f, 0.56f, Color(0xFF1E5AA8), 0.045f)
    rrect(0.42f, 0.82f, 0.58f, 0.92f, 0.02f, Color(0xFF16498A))
}

internal fun DrawScope.charShrek(p: Float) {
    val green = Color(0xFF8BC34A)
    val w = wiggleOf(p)
    val b = blinkOf(p)
    turn(6f * w, 0.18f, 0.44f) { oval(0.14f, 0.42f, 0.10f, 0.14f, green); oval(0.15f, 0.42f, 0.05f, 0.08f, Color(0xFFC5E1A5)) }
    turn(-6f * w, 0.82f, 0.44f) { oval(0.86f, 0.42f, 0.10f, 0.14f, green); oval(0.85f, 0.42f, 0.05f, 0.08f, Color(0xFFC5E1A5)) }
    disc(0.5f, 0.56f, 0.34f, green)
    // one thick brow and the small eyes
    arcLine(0.5f, 0.44f, 0.56f, 0.08f, 200f, 140f, Color(0xFF6A9A2E), 0.04f)
    oval(0.38f, 0.50f, 0.065f, 0.045f * (1f - 0.9f * b).coerceAtLeast(0.1f), Color.White); oval(0.62f, 0.50f, 0.065f, 0.045f * (1f - 0.9f * b).coerceAtLeast(0.1f), Color.White)
    if (b < 0.5f) { disc(0.39f, 0.505f, 0.022f, Ink); disc(0.61f, 0.505f, 0.022f, Ink) }
    oval(0.5f, 0.60f, 0.085f, 0.07f, Color(0xFFA5D36A))
    disc(0.47f, 0.61f, 0.012f, Color(0xFF6A9A2E)); disc(0.53f, 0.61f, 0.012f, Color(0xFF6A9A2E))
    arcLine(0.5f, 0.70f, 0.40f, 0.16f, 25f, 130f, Ink, 0.02f)
    poly(Color(0xFF8D6E63), 0.12f, 1.0f, 0.26f, 0.90f, 0.74f, 0.90f, 0.88f, 1.0f)
    poly(Color(0xFFF2EDE0), 0.40f, 0.90f, 0.60f, 0.90f, 0.5f, 1.0f)
}

internal fun DrawScope.charWednesday(p: Float) {
    val w = wiggleOf(p)
    val b = blinkOf(p)
    poly(Ink, 0.10f, 1.0f, 0.24f, 0.86f, 0.76f, 0.86f, 0.90f, 1.0f)
    poly(Color.White, 0.34f, 0.86f, 0.66f, 0.86f, 0.5f, 0.98f)
    // the two braids swing a little
    for (i in 0 until 5) {
        val sway = 0.012f * w * (i + 1)
        disc(0.20f + sway, 0.60f + 0.075f * i, 0.055f - 0.004f * i, Ink); disc(0.80f + sway, 0.60f + 0.075f * i, 0.055f - 0.004f * i, Ink)
    }
    oval(0.5f, 0.46f, 0.33f, 0.38f, Ink)
    oval(0.5f, 0.54f, 0.24f, 0.30f, Color(0xFFFFEAD8))
    // the centre parting and the straight fringe
    poly(Ink, 0.24f, 0.46f, 0.5f, 0.20f, 0.5f, 0.28f, 0.28f, 0.54f); poly(Ink, 0.76f, 0.46f, 0.5f, 0.20f, 0.5f, 0.28f, 0.72f, 0.54f)
    eye(0.40f, 0.56f, 0.04f, 0.06f, Ink, b); eye(0.60f, 0.56f, 0.04f, 0.06f, Ink, b)
    if (b < 0.5f) { disc(0.41f, 0.54f, 0.012f, Color.White); disc(0.61f, 0.54f, 0.012f, Color.White) }
    seg(0.44f, 0.72f, 0.56f, 0.72f, Color(0xFF8E3B46), 0.02f)
}

internal fun DrawScope.charGuard(p: Float) {
    val pink = Color(0xFFFF2E7E)
    disc(0.5f, 0.56f, 0.40f, pink)
    seg(0.5f, 0.16f, 0.5f, 0.30f, Color(0xFFC21E5E), 0.02f)
    oval(0.5f, 0.54f, 0.25f, 0.30f, Ink)
    val w = Color.White
    val sw = 0.026f
    // the mark on the mask: a circle, then a triangle, then a square while it acts
    when {
        p < 0.34f -> arcLine(0.5f, 0.54f, 0.22f, 0.22f, 0f, 360f, w, sw)
        p < 0.67f -> { seg(0.5f, 0.42f, 0.40f, 0.62f, w, sw); seg(0.40f, 0.62f, 0.60f, 0.62f, w, sw); seg(0.60f, 0.62f, 0.5f, 0.42f, w, sw) }
        else -> { seg(0.40f, 0.44f, 0.60f, 0.44f, w, sw); seg(0.60f, 0.44f, 0.60f, 0.64f, w, sw); seg(0.60f, 0.64f, 0.40f, 0.64f, w, sw); seg(0.40f, 0.64f, 0.40f, 0.44f, w, sw) }
    }
}
