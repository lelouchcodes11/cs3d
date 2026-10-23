package com.lagradost.desktop.ui.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The rest of the characters (the rig and the first ones are in AvatarArt.kt)

internal val cyber: ArtDraw = {
    val neon = Color(0xFF3DF5E0)
    bust(Color(0xFF2E3352), Color(0xFF12142A))
    stroke(1.8f, neon) { moveTo(20f, 100f); quadraticBezierTo(28f, 84f, 42f, 80f) }; stroke(1.8f, Color(0xFFFF4FD8)) { moveTo(80f, 100f); quadraticBezierTo(72f, 84f, 58f, 80f) }
    move(0f, bob) {
        ball(50f, 48f, 31.5f, 30.5f, Color(0xFF3F4570))
        oval(50f, 56f, 20.5f, 22.5f, vgrad(Color(0xFFD9A07A), Color(0xFFB8805C), 36f, 78f))
        val visor: Path.() -> Unit = { addRoundRect(androidx.compose.ui.geometry.RoundRect(25f, 43f, 75f, 58f, 7.5f, 7.5f)) }
        path(hgrad(Color(0xFF29E6FF), Color(0xFFFF4FD8), 25f, 75f), build = visor)
        clip(visor) { rrect(15f + ((u * 2) % 1f) * 68f, 40f, 9f, 22f, 4f, Color.White, 0.6f) }
        rrect(26f, 44f, 48f, 3f, 1.5f, Color.White, 0.25f)
        stroke(1.7f, Color(0xFF7A3F2C)) { moveTo(43f, 68f); quadraticBezierTo(51f, 72f, 58f, 66f) }
        stroke(1.2f, Color(0xFFA06846)) { moveTo(50f, 60f); lineTo(48.5f, 63.5f); lineTo(51.5f, 63.5f) }
        stroke(5f, Color(0xFF1F2238)) { moveTo(19f, 54f); cubicTo(14f, 6f, 86f, 6f, 81f, 54f) }
        val g = 0.6f + 0.4f * (sin(ph * 2) * 0.5f + 0.5f)
        rrect(12f, 44f, 11f, 22f, 5.5f, Color(0xFF232640)); rrect(77f, 44f, 11f, 22f, 5.5f, Color(0xFF232640))
        rrect(15f, 48f, 4f, 14f, 2f, neon, g); rrect(81f, 48f, 4f, 14f, 2f, Color(0xFFFF4FD8), g)
    }
    sparkle(86f, 22f, 3.6f, neon); sparkle(12f, 30f, 3f, Color(0xFFFF4FD8), 2f)
}

internal val pirate: ArtDraw = {
    bust(Color(0xFFF3EEE4), Color(0xFFC9C0AE))
    path(Color(0xFF5B3A29)) { moveTo(8f, 108f); lineTo(24f, 84f); lineTo(41f, 93f); lineTo(36f, 108f); close() }
    path(Color(0xFF5B3A29)) { moveTo(92f, 108f); lineTo(76f, 84f); lineTo(59f, 93f); lineTo(64f, 108f); close() }
    for (i in 0..2) circle(50f, 88f + i * 5f, 1.3f, Color(0xFFFFD25A))
    move(0f, bob) {
        human(Color(0xFFE7B58A))
        for (i in 0..8) circle(33f + i * 4.2f, 68f + (i % 2) * 1.6f - (if (i < 2 || i > 6) 4f else 0f), 0.55f, Color(0xFF5B3A29), 0.55f)
        val f = sin(ph * 2)
        path(Color(0xFFD32F3F)) { moveTo(72f, 34f); quadraticBezierTo(86f, 38f + f * 2, 92f, 52f + f * 4); lineTo(86f, 54f + f * 4); quadraticBezierTo(80f, 44f, 70f, 42f); close() }
        path(vgrad(Color(0xFFEE4A58), Color(0xFFB8242F), 19f, 44f)) { moveTo(23.8f, 46f); cubicTo(20f, 25f, 34f, 19f, 50f, 19f); cubicTo(66f, 19f, 80f, 25f, 76.2f, 46f); quadraticBezierTo(50f, 37f, 23.8f, 46f); close() }
        for ((x, y) in listOf(34f to 30f, 48f to 25f, 62f to 30f, 41f to 36f, 56f to 36f)) circle(x, y, 1.5f, Color.White, 0.9f)
        // a patch over the left eye, the right one winks
        line(25f, 43f, 75f, 40f, 1.5f, Ink)
        oval(39f, 50.5f, 7.2f, 6.2f, Ink); oval(36.5f, 48.5f, 2.2f, 1.4f, Color.White, 0.25f)
        val o = min(lid(), 1f - bump(2f, 0.22f))
        eye(61f, 50.5f, 4.3f, Color(0xFF3A2A22), o, -0.2f)
        brow(55f, 44.5f, 67f, 43f, 2.2f)
        circle(50f, 60f, 2.6f, Color(0xFFD99A70))
        grin(50f, 65.5f, 15f, 5.5f)
        circle(55f, 66.8f, 1.1f, Color(0xFFFFD25A))
        val sw = sin(ph) * 3f
        stroke(1.6f, Color(0xFFFFD25A)) { addOval(androidx.compose.ui.geometry.Rect(22f + sw * 0.2f, 61f, 26f + sw * 0.2f, 69f)) }
    }
}

internal val knight: ArtDraw = {
    val steel = Color(0xFFB9C3D8)
    bust(Color(0xFF9AA6C0), Color(0xFF55607A))
    oval(16f, 88f, 15f, 9f, vgrad(Color(0xFFD5DCEA), Color(0xFF7D89A4), 80f, 97f)); oval(84f, 88f, 15f, 9f, vgrad(Color(0xFFD5DCEA), Color(0xFF7D89A4), 80f, 97f))
    move(0f, bob) {
        val s2 = sin(ph)
        path(vgrad(Color(0xFFFF5A5A), Color(0xFFB8242F), 4f, 34f)) { moveTo(46f, 22f); cubicTo(60f + s2 * 3f, 0f, 82f + s2 * 4f, 8f, 90f + s2 * 5f, 34f); cubicTo(80f + s2 * 3f, 20f, 66f, 20f, 54f, 28f); close() }
        ball(50f, 50f, 28.5f, 30f, steel)
        rrect(48.2f, 21f, 3.6f, 26f, 1.8f, Color(0xFF8793AD), 0.6f)
        rrect(28f, 43f, 44f, 11f, 5.5f, Ink)
        val g = 0.7f + 0.3f * (sin(ph * 2) * 0.5f + 0.5f)
        oval(40f, 48.5f, 4.6f, 3.4f * lid(), Color(0xFF6FE9FF), 0.3f * g); oval(60f, 48.5f, 4.6f, 3.4f * lid(), Color(0xFF6FE9FF), 0.3f * g)
        oval(40f, 48.5f, 3f, 2.6f * lid(), Color(0xFF6FE9FF), g); oval(60f, 48.5f, 3f, 2.6f * lid(), Color(0xFF6FE9FF), g)
        for (i in 0..2) rrect(42f + i * 6f, 63f, 2.2f, 9f, 1.1f, Color(0xFF55607A))
        for ((x, y) in listOf(29f to 34f, 71f to 34f, 29f to 62f, 71f to 62f)) { circle(x, y, 1.6f, Color(0xFF7D89A4)); circle(x - 0.4f, y - 0.4f, 0.6f, Color.White, 0.7f) }
        sheen(50f, 50f, 28.5f, 30f)
    }
}

internal val hacker: ArtDraw = {
    val green = Color(0xFF5CFF8A)
    bust(Color(0xFF2A5A44), Color(0xFF12281E))
    line(43f, 82f, 41f, 100f, 1.4f, Color.White, 0.7f); line(57f, 82f, 59f, 100f, 1.4f, Color.White, 0.7f)
    move(0f, bob) {
        ball(50f, 48f, 31.5f, 30.5f, Color(0xFF2F6B50))
        oval(50f, 56f, 20.5f, 22f, vgrad(Color(0xFFF0C29A), Color(0xFFD59C74), 36f, 78f))
        // glasses: the code runs over the glass
        for (x0 in floatArrayOf(29f, 52f)) {
            rrect(x0, 47f, 19f, 14f, 5f, Color(0xFF0B1F1C), 0.92f)
            for (i in 0..3) { val l = 5f + 8f * (sin(ph * 2 + i * 1.7f + x0) * 0.5f + 0.5f); rrect(x0 + 2.5f, 49.5f + i * 2.7f, l, 1.3f, 0.6f, green, 0.9f) }
            stroke(1.5f, Color(0xFF101418)) { addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, 47f, x0 + 19f, 61f, 5f, 5f)) }
        }
        line(48f, 52f, 52f, 52f, 1.5f, Color(0xFF101418))
        stroke(1.7f, Color(0xFF7A3F2C)) { moveTo(43f, 69f); quadraticBezierTo(52f, 73f, 59f, 67f) }
        stroke(1.2f, Color(0xFFB98660)) { moveTo(50f, 62f); lineTo(48.5f, 65f); lineTo(51.5f, 65f) }
        brow(30f, 43f, 45f, 43.5f, 2f, Color(0xFF3A2A22)); brow(55f, 43.5f, 70f, 43f, 2f, Color(0xFF3A2A22))
    }
    sparkle(86f, 24f, 3.4f, green); sparkle(13f, 58f, 3f, green, 2.4f)
}

internal val dj: ArtDraw = {
    for (i in 0..6) { val h = 14f + 18f * (sin(ph * 2 + i * 0.9f) * 0.5f + 0.5f); rrect(9f + i * 13f, 100f - h, 8f, h, 4f, vgrad(Color(0xFFFFE066), Color(0xFFFF5CA8), 100f - h, 100f)) }
    bust(Color(0xFFFF5CA8), Color(0xFFB01E6A))
    move(0f, bob) {
        human(Color(0xFF8D5A3C))
        path(vgrad(Color(0xFFFFE066), Color(0xFFE6A92B), 18f, 44f)) { moveTo(23.8f, 46f); cubicTo(20f, 24f, 34f, 18f, 50f, 18f); cubicTo(66f, 18f, 80f, 24f, 76.2f, 46f); quadraticBezierTo(50f, 40f, 23.8f, 46f); close() }
        rrect(27f, 40.5f, 46f, 5.5f, 2.7f, Color(0xFFD99A1F))
        for (x0 in floatArrayOf(28f, 53f)) {
            val lens: Path.() -> Unit = { addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, 48f, x0 + 19f, 60f, 5f, 5f)) }
            path(vgrad(Color(0xFF2B2B3A), Ink, 48f, 60f), build = lens)
            clip(lens) { turn(20f, x0 + 9f, 54f) { rrect(x0 - 12f + ((u * 2) % 1f) * 44f, 40f, 4f, 26f, 2f, Color.White, 0.35f) } }
        }
        line(47f, 51f, 53f, 51f, 1.8f, Ink)
        grin(50f, 67f, 17f, 6.5f)
        stroke(5f, Color(0xFF2D2D3A)) { moveTo(20f, 54f); cubicTo(14f, 8f, 86f, 8f, 80f, 54f) }
        rrect(13f, 45f, 11f, 22f, 5.5f, Color(0xFF2D2D3A)); rrect(76f, 45f, 11f, 22f, 5.5f, Color(0xFF2D2D3A))
        val g = 0.55f + 0.45f * (sin(ph * 2) * 0.5f + 0.5f)
        rrect(16f, 49f, 4f, 14f, 2f, Color(0xFFFF5CA8), g); rrect(80f, 49f, 4f, 14f, 2f, Color(0xFFFF5CA8), g)
    }
}

internal val vampire: ArtDraw = {
    bust(Color(0xFF2E2538), Color(0xFF110D17))
    path(Color(0xFFC2273A)) { moveTo(10f, 96f); lineTo(28f, 68f); lineTo(44f, 86f); lineTo(30f, 106f); close() }
    path(Color(0xFFC2273A)) { moveTo(90f, 96f); lineTo(72f, 68f); lineTo(56f, 86f); lineTo(70f, 106f); close() }
    move(0f, bob) {
        human(Color(0xFFE9E6F2))
        path(vgrad(Color(0xFF34303F), Ink, 18f, 44f)) { moveTo(23.8f, 49f); cubicTo(20f, 26f, 34f, 18f, 50f, 18f); cubicTo(66f, 18f, 80f, 26f, 76.2f, 49f); cubicTo(73f, 38f, 62f, 35f, 50f, 44f); cubicTo(38f, 35f, 27f, 38f, 23.8f, 49f); close() }
        eye(39f, 52f, 4.3f, Color(0xFFE5303C), lid(), 0.2f); eye(61f, 52f, 4.3f, Color(0xFFE5303C), lid(), 0.2f)
        brow(32f, 45.5f, 44f, 48f, 2.3f); brow(68f, 45.5f, 56f, 48f, 2.3f)
        stroke(1.6f, Color(0xFF8A3A4A)) { moveTo(42f, 65f); quadraticBezierTo(50f, 69.5f, 58f, 65f) }
        path(Color.White) { moveTo(44.5f, 65.6f); lineTo(46.4f, 71.5f); lineTo(48.2f, 66.7f); close() }; path(Color.White) { moveTo(55.5f, 65.6f); lineTo(53.6f, 71.5f); lineTo(51.8f, 66.7f); close() }
        blush(31f, 62f, 4f, 2.4f, Color(0xFFFF8FA3))
    }
    // a bat goes round
    val bx = 50f + 38f * cos(ph); val by = 12f + 6f * sin(ph * 2); val fl = sin(ph * 8) * 4f
    path(Ink) {
        moveTo(bx, by); quadraticBezierTo(bx - 5f, by - 5f + fl, bx - 10f, by - 1f + fl); quadraticBezierTo(bx - 7f, by, bx - 5.5f, by + 3f); quadraticBezierTo(bx - 3f, by + 1f, bx, by + 3.5f)
        quadraticBezierTo(bx + 3f, by + 1f, bx + 5.5f, by + 3f); quadraticBezierTo(bx + 7f, by, bx + 10f, by - 1f + fl); quadraticBezierTo(bx + 5f, by - 5f + fl, bx, by); close()
    }
    circle(bx - 0.9f, by + 0.4f, 0.5f, Color(0xFFFF5A5A)); circle(bx + 0.9f, by + 0.4f, 0.5f, Color(0xFFFF5A5A))
}

internal val fox: ArtDraw = {
    val orange = Color(0xFFF2813A)
    move(0f, bob) {
        val tw = bump(2.5f, 0.22f)
        path(orange) { moveTo(24f, 42f); lineTo(16f, 6f); lineTo(46f, 28f); close() }; path(Color(0xFF5B2E1E)) { moveTo(22f, 34f); lineTo(18f, 14f); lineTo(34f, 28f); close() }
        turn(-9f * tw, 78f, 38f) { path(orange) { moveTo(76f, 42f); lineTo(84f, 6f); lineTo(54f, 28f); close() }; path(Color(0xFF5B2E1E)) { moveTo(78f, 34f); lineTo(82f, 14f); lineTo(66f, 28f); close() } }
        val head: Path.() -> Unit = { moveTo(12f, 50f); cubicTo(12f, 30f, 32f, 26f, 50f, 26f); cubicTo(68f, 26f, 88f, 30f, 88f, 50f); cubicTo(88f, 70f, 64f, 88f, 50f, 88f); cubicTo(36f, 88f, 12f, 70f, 12f, 50f); close() }
        path(rgrad(orange.light(0.25f), orange.dark(0.12f), 38f, 36f, 62f), build = head)
        path(Color(0xFFFFF4E6)) { moveTo(12f, 52f); cubicTo(26f, 53f, 38f, 63f, 50f, 65f); cubicTo(62f, 63f, 74f, 53f, 88f, 52f); cubicTo(86f, 72f, 62f, 88f, 50f, 88f); cubicTo(38f, 88f, 14f, 72f, 12f, 52f); close() }
        dotEye(35f, 52f, 4.4f, lid(), 0.3f); dotEye(65f, 52f, 4.4f, lid(), 0.3f)
        oval(50f, 71f, 4.4f, 3.2f, Ink); circle(48.6f, 69.8f, 1f, Color.White, 0.8f)
        stroke(1.5f, Ink) { moveTo(50f, 74f); quadraticBezierTo(46f, 79f, 41.5f, 75.5f); moveTo(50f, 74f); quadraticBezierTo(54f, 79f, 58.5f, 75.5f) }
        blush(26f, 64f, 4.6f, 2.8f); blush(74f, 64f, 4.6f, 2.8f)
    }
}

internal val panda: ArtDraw = {
    move(0f, bob) {
        ball(23f, 26f, 11f, 11f, Ink); ball(77f, 26f, 11f, 11f, Ink)
        ball(50f, 54f, 34f, 31f, Color(0xFFF8F8FC))
        turn(-18f, 36f, 55f) { oval(36f, 55f, 8.4f, 11f, Ink) }; turn(18f, 64f, 55f) { oval(64f, 55f, 8.4f, 11f, Ink) }
        eye(36.5f, 54f, 3.8f, Ink, lid(), 0.3f); eye(63.5f, 54f, 3.8f, Ink, lid(), 0.3f)
        oval(50f, 64f, 5f, 3.6f, Ink); circle(48.4f, 62.8f, 1.1f, Color.White, 0.8f)
        stroke(1.6f, Ink) { moveTo(50f, 67.5f); quadraticBezierTo(46f, 73f, 41f, 69.5f); moveTo(50f, 67.5f); quadraticBezierTo(54f, 73f, 59f, 69.5f) }
        blush(24f, 66f, 4.4f, 2.8f); blush(76f, 66f, 4.4f, 2.8f)
    }
}

internal val bunny: ArtDraw = {
    move(0f, bob) {
        val s2 = sin(ph) * 3f
        turn(-7f + s2, 38f, 46f) { oval(38f, 22f, 9f, 23f, Color(0xFFFBF7FF)); oval(38f, 24f, 4.6f, 16f, Color(0xFFFFB3C7)) }
        turn(7f - s2, 62f, 46f) { oval(62f, 22f, 9f, 23f, Color(0xFFFBF7FF)); oval(62f, 24f, 4.6f, 16f, Color(0xFFFFB3C7)) }
        ball(50f, 62f, 31f, 27f, Color(0xFFFBF7FF))
        dotEye(38f, 60f, 4.4f, lid(), 0.3f); dotEye(62f, 60f, 4.4f, lid(), 0.3f)
        val n = 1f + sin(ph * 4) * 0.12f
        oval(50f, 66f, 3.2f * n, 2.5f * n, Color(0xFFFF7FA3))
        stroke(1.4f, Color(0xFFB07A8C)) { moveTo(50f, 68.5f); lineTo(50f, 71f); moveTo(50f, 71f); quadraticBezierTo(46.5f, 75f, 43f, 71.5f); moveTo(50f, 71f); quadraticBezierTo(53.5f, 75f, 57f, 71.5f) }
        rrect(46.3f, 72.2f, 3.6f, 5f, 1.3f, Color.White); rrect(50.1f, 72.2f, 3.6f, 5f, 1.3f, Color.White)
        stroke(0.8f, Color(0xFFB07A8C), 0.7f) { moveTo(50f, 72.5f); lineTo(50f, 77f) }
        blush(27f, 69f, 4.8f, 3f); blush(73f, 69f, 4.8f, 3f)
    }
}

internal val penguin: ArtDraw = {
    for (i in 0..5) { val y = ((u + i * 0.17f) % 1f) * 112f - 6f; circle(10f + i * 16f + sin(ph + i * 2f) * 3f, y, 1.5f + (i % 2) * 0.6f, Color.White, 0.85f) }
    move(0f, bob) {
        val fl = sin(ph * 2) * 8f
        turn(-14f - fl, 20f, 62f) { oval(14f, 70f, 6.5f, 15f, Color(0xFF232B45)) }
        turn(14f + fl, 80f, 62f) { oval(86f, 70f, 6.5f, 15f, Color(0xFF232B45)) }
        ball(50f, 52f, 33f, 35f, Color(0xFF2E3A5E))
        path(Color(0xFFFDFDFF)) { moveTo(50f, 45f); cubicTo(41f, 32f, 22f, 40f, 24f, 58f); cubicTo(26f, 76f, 42f, 86f, 50f, 86f); cubicTo(58f, 86f, 74f, 76f, 76f, 58f); cubicTo(78f, 40f, 59f, 32f, 50f, 45f); close() }
        dotEye(39f, 55f, 3.8f, lid(), 0.2f); dotEye(61f, 55f, 3.8f, lid(), 0.2f)
        path(Color(0xFFFFA62B)) { moveTo(42f, 63f); quadraticBezierTo(50f, 57f, 58f, 63f); quadraticBezierTo(50f, 69f, 42f, 63f); close() }
        path(Color(0xFFE08A12)) { moveTo(43.5f, 64.5f); quadraticBezierTo(50f, 68.8f, 56.5f, 64.5f); quadraticBezierTo(50f, 73f, 43.5f, 64.5f); close() }
        blush(31f, 63f, 4f, 2.4f); blush(69f, 63f, 4f, 2.4f)
        path(Color(0xFFE5484D)) { moveTo(20f, 84f); quadraticBezierTo(50f, 98f, 80f, 84f); lineTo(78f, 94f); quadraticBezierTo(50f, 108f, 22f, 94f); close() }
    }
}

internal val dragon: ArtDraw = {
    val teal = Color(0xFF2FBF9A)
    move(0f, bob) {
        path(Color(0xFFFFF1CF)) { moveTo(30f, 38f); quadraticBezierTo(22f, 18f, 12f, 12f); quadraticBezierTo(28f, 18f, 42f, 36f); close() }
        path(Color(0xFFFFF1CF)) { moveTo(70f, 38f); quadraticBezierTo(78f, 18f, 88f, 12f); quadraticBezierTo(72f, 18f, 58f, 36f); close() }
        path(Color(0xFF1E8F78)) { moveTo(18f, 46f); lineTo(6f, 40f); lineTo(14f, 58f); close() }; path(Color(0xFF1E8F78)) { moveTo(82f, 46f); lineTo(94f, 40f); lineTo(86f, 58f); close() }
        ball(50f, 54f, 32f, 29f, teal)
        for (i in -2..2) path(Color(0xFF1E8F78)) { moveTo(50f + i * 7f - 3f, 26f); lineTo(50f + i * 7f, 17f - abs(i) * 1.5f); lineTo(50f + i * 7f + 3f, 26f); close() }
        oval(50f, 67f, 19f, 13f, vgrad(Color(0xFF9CF0D4), Color(0xFF5FD7B4), 55f, 80f))
        oval(43f, 65f, 1.9f, 2.6f, Color(0xFF14604F)); oval(57f, 65f, 1.9f, 2.6f, Color(0xFF14604F))
        for (cx in floatArrayOf(36f, 64f)) {
            val o = lid()
            circle(cx, 49f, 5.6f, Color(0xFFFFD14A)); oval(cx, 49f, 1.9f, 4.6f * o + 0.2f, Ink); circle(cx - 1.8f, 46.6f, 1.2f, Color.White, 0.9f)
        }
        brow(30f, 42.5f, 42f, 44.5f, 2.2f, Color(0xFF14604F)); brow(70f, 42.5f, 58f, 44.5f, 2.2f, Color(0xFF14604F))
        stroke(1.5f, Color(0xFF14604F)) { moveTo(40f, 73f); quadraticBezierTo(50f, 78f, 60f, 73f) }
        path(Color.White) { moveTo(43f, 74.5f); lineTo(44.6f, 78.5f); lineTo(46.2f, 75.5f); close() }; path(Color.White) { moveTo(57f, 74.5f); lineTo(55.4f, 78.5f); lineTo(53.8f, 75.5f); close() }
        blush(25f, 62f, 4.2f, 2.6f, Color(0xFFFF8FA3)); blush(75f, 62f, 4.2f, 2.6f, Color(0xFFFF8FA3))
        for (i in 0..2) {
            val p = (u + i / 3f) % 1f; val sx = sin(p * 6f + i) * 2f
            circle(43f + sx - p * 4f, 62f - p * 34f, 1.6f + p * 4.5f, Color.White, (1f - p) * 0.55f); circle(57f - sx + p * 4f, 62f - p * 34f, 1.6f + p * 4.5f, Color.White, (1f - p) * 0.55f)
        }
    }
}

internal val owl: ArtDraw = {
    val brown = Color(0xFF9A6B4B)
    turn(sin(ph) * 3f, 50f, 88f) {
        move(0f, bob) {
            path(Color(0xFF7A5238)) { moveTo(22f, 38f); lineTo(16f, 10f); lineTo(42f, 26f); close() }; path(Color(0xFF7A5238)) { moveTo(78f, 38f); lineTo(84f, 10f); lineTo(58f, 26f); close() }
            ball(50f, 54f, 34f, 32f, brown)
            for (i in -2..2) stroke(1.5f, Color(0xFFC79A74), 0.8f) { moveTo(50f + i * 6f - 3f, 33f); quadraticBezierTo(50f + i * 6f, 37f, 50f + i * 6f + 3f, 33f) }
            ball(35f, 52f, 15.5f, 15.5f, Color(0xFFF6E6C8)); ball(65f, 52f, 15.5f, 15.5f, Color(0xFFF6E6C8))
            val lx = sin(ph) * 0.9f
            eye(35f, 52f, 8.6f, Color(0xFFFFB63D), lid(), lx); eye(65f, 52f, 8.6f, Color(0xFFFFB63D), lid(), lx)
            path(Color(0xFFFF9F2B)) { moveTo(45.5f, 60f); quadraticBezierTo(50f, 57f, 54.5f, 60f); lineTo(50f, 72f); close() }
        }
    }
}

internal val frog: ArtDraw = {
    val green = Color(0xFF59C45A)
    move(0f, bob) {
        ball(32f, 33f, 14f, 14f, green); ball(68f, 33f, 14f, 14f, green)
        ball(50f, 62f, 38f, 27f, green)
        circle(32f, 33f, 9.2f, Color.White); circle(68f, 33f, 9.2f, Color.White)
        dotEye(33f, 33.5f, 5.4f, lid(), 0.2f); dotEye(67f, 33.5f, 5.4f, lid(), 0.2f)
        oval(46.5f, 52f, 1.1f, 1.5f, Color(0xFF2C7A31)); oval(53.5f, 52f, 1.1f, 1.5f, Color(0xFF2C7A31))
        stroke(1.9f, Color(0xFF2C7A31)) { moveTo(21f, 64f); quadraticBezierTo(50f, 86f, 79f, 64f) }
        blush(24f, 66f, 4.8f, 3f, Color(0xFFFF8FA3)); blush(76f, 66f, 4.8f, 3f, Color(0xFFFF8FA3))
        // a fly goes round and the tongue flicks at it
        val fx = 84f + sin(ph * 2) * 2.5f; val fy = 20f + sin(ph * 3) * 3f
        val k = bump(2.5f, 0.28f)
        if (k > 0f) { line(58f, 68f, 58f + (fx - 58f) * k, 68f + (fy - 68f) * k, 3.6f, Color(0xFFFF6F8E)); circle(58f + (fx - 58f) * k, 68f + (fy - 68f) * k, 3.4f, Color(0xFFFF6F8E)) }
        circle(fx, fy, 1.7f, Ink); oval(fx - 2f, fy - 2f, 2f, 1.2f, Color.White, 0.6f); oval(fx + 2f, fy - 2f, 2f, 1.2f, Color.White, 0.6f)
    }
}

internal val skull: ArtDraw = {
    val bone = Color(0xFFF3EEDC)
    for (i in 0..4) {
        val x = 31f + i * 9.5f; val h = 17f + 9f * (sin(ph * 2 + i * 1.7f) * 0.5f + 0.5f)
        path(vgrad(Color(0xFF9DFFC4), Color(0xFF2FD4A0), 22f - h, 26f)) { moveTo(x - 4.6f, 28f); quadraticBezierTo(x - 4.4f, 28f - h * 0.6f, x + sin(ph * 2 + i) * 1.8f, 22f - h + 8f); quadraticBezierTo(x + 4.4f, 28f - h * 0.6f, x + 4.6f, 28f); close() }
    }
    move(0f, bob) {
        val dy = (sin(ph * 2) * 0.5f + 0.5f) * 2.4f
        move(0f, dy) { rrect(34f, 60f, 32f, 19f, 7f, bone.dark(0.1f)); for (i in 0..4) line(39f + i * 5.5f, 69f, 39f + i * 5.5f, 78f, 1.2f, Ink, 0.5f); line(35f, 69f, 65f, 69f, 1.2f, Ink, 0.5f) }
        ball(50f, 42f, 30f, 27f, bone)
        val f = 0.7f + 0.3f * (sin(ph * 3) * 0.5f + 0.5f)
        for (cx in floatArrayOf(38f, 62f)) { oval(cx, 47f, 8.6f, 9.6f, Ink); oval(cx, 48f, 5f * f, 5.4f * f, Color(0xFFFF8A1F), 0.45f); oval(cx, 48f, 2.8f * f, 3.2f * f, Color(0xFFFFD25A)) }
        path(Ink) { moveTo(50f, 55f); quadraticBezierTo(47f, 61f, 45.5f, 63f); lineTo(54.5f, 63f); quadraticBezierTo(53f, 61f, 50f, 55f); close() }
        stroke(1.5f, Ink, 0.45f) { moveTo(62f, 17f); lineTo(58f, 25f); lineTo(62f, 29f); lineTo(59f, 35f) }
        sheen(50f, 42f, 30f, 27f)
    }
}

internal val oni: ArtDraw = {
    val red = Color(0xFFE24A3F)
    bust(Color(0xFF4A3358), Color(0xFF1E1228))
    move(0f, bob) {
        path(Ink) { moveTo(20f, 46f); lineTo(14f, 26f); lineTo(30f, 32f); lineTo(34f, 12f); lineTo(46f, 26f); lineTo(50f, 8f); lineTo(54f, 26f); lineTo(66f, 12f); lineTo(70f, 32f); lineTo(86f, 26f); lineTo(80f, 46f); close() }
        path(vgrad(Color(0xFFFFF1CF), Color(0xFFE6C690), 4f, 36f)) { moveTo(33f, 36f); quadraticBezierTo(26f, 18f, 31f, 4f); quadraticBezierTo(42f, 16f, 43f, 34f); close() }
        path(vgrad(Color(0xFFFFF1CF), Color(0xFFE6C690), 4f, 36f)) { moveTo(67f, 36f); quadraticBezierTo(74f, 18f, 69f, 4f); quadraticBezierTo(58f, 16f, 57f, 34f); close() }
        human(red, 52f)
        brow(31f, 44f, 45f, 49.5f, 3.6f); brow(69f, 44f, 55f, 49.5f, 3.6f)
        eye(39f, 54f, 4.4f, Color(0xFFD81F26), lid(), 0.2f, white = Color(0xFFFFE066)); eye(61f, 54f, 4.4f, Color(0xFFD81F26), lid(), 0.2f, white = Color(0xFFFFE066))
        circle(47.3f, 62f, 1.1f, Color(0xFF8E1F1A)); circle(52.7f, 62f, 1.1f, Color(0xFF8E1F1A))
        grin(50f, 67f, 28f, 7.5f, teeth = false)
        path(Color.White) { moveTo(36.5f, 68f); lineTo(39f, 60.5f); lineTo(41.5f, 70.5f); close() }; path(Color.White) { moveTo(63.5f, 68f); lineTo(61f, 60.5f); lineTo(58.5f, 70.5f); close() }
    }
    for (i in 0..3) { val p = (u + i * 0.25f) % 1f; circle(18f + i * 22f + sin(p * 6f + i) * 3f, 100f - p * 100f, 1.5f + (i % 2), Color(0xFFFFA23D), (1f - p) * 0.9f) }
}

internal val monster: ArtDraw = {
    val purple = Color(0xFF8B5CF6)
    move(0f, bob) {
        path(Color(0xFFFFF1CF)) { moveTo(33f, 28f); lineTo(29f, 8f); lineTo(43f, 24f); close() }; path(Color(0xFFFFF1CF)) { moveTo(67f, 28f); lineTo(71f, 8f); lineTo(57f, 24f); close() }
        for (i in 0 until 18) { val a = i / 18f * 2f * PI.toFloat(); circle(50f + cos(a) * 29f, 56f + sin(a) * 27f, 8.5f, purple.dark(0.1f)) }
        ball(50f, 56f, 30f, 28f, purple)
        for ((x, y) in listOf(28f to 40f, 74f to 38f, 34f to 74f, 70f to 72f)) circle(x, y, 2.4f, Color.White, 0.2f)
        eye(50f, 47f, 13f, Color(0xFF3DDC97), lid(), sin(ph) * 0.9f, sin(ph * 2) * 0.4f)
        grin(50f, 66f, 26f, 8f)
    }
}

internal val invader: ArtDraw = {
    val a = listOf("..#.....#..", "...#...#...", "..#######..", ".##.###.##.", "###########", "#.#######.#", "#.#.....#.#", "...##.##...")
    val b = listOf("..#.....#..", "#..#...#..#", "#.#######.#", "###.###.###", "###########", ".#########.", "..#.....#..", ".#.......#.")
    val frame = if ((t * 2).toInt() % 2 == 0) a else b
    val cell = 6.4f
    // pixel stars
    for ((i, p) in listOf(12f to 14f, 84f to 18f, 20f to 86f, 80f to 82f, 50f to 8f).withIndex()) rrect(p.first, p.second, 3f, 3f, 0.6f, Color.White, 0.3f + 0.6f * (sin(ph * 2 + i * 1.3f) * 0.5f + 0.5f))
    move(0f, bob) {
        for (glow in 0..1) for ((r, row) in frame.withIndex()) for ((c, ch) in row.withIndex()) if (ch == '#') {
            val x = 14.8f + c * cell; val y = 24f + r * cell
            if (glow == 0) rrect(x - 1.4f, y - 1.4f, cell + 2.8f, cell + 2.8f, 2.4f, Color(0xFF4DFFB0), 0.12f)
            else rrect(x + 0.3f, y + 0.3f, cell - 0.6f, cell - 0.6f, 1.2f, vgrad(Color(0xFF9DFFD4), Color(0xFF2FE08F), 24f, 76f))
        }
    }
}
