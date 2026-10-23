package com.lagradost.desktop.ui.shell

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedAvatarsTest {

    /** The picture [id] at time [t], 96 x 96 px, as bytes */
    private fun render(id: String, t: Float): IntArray {
        val bmp = ImageBitmap(96, 96)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(96f, 96f)) { drawAvatarArt(avatarArt.getValue(id), t) }
        return bmp.toPixelMap().buffer
    }

    @Test
    fun everyAvatarHasItsPicture() {
        for (avatar in animatedAvatars) {
            if (avatarArt.containsKey(avatar.id)) continue
            val stream = AnimatedAvatarsTest::class.java.classLoader.getResourceAsStream("avatars/${avatar.id}.webp")
            assertNotNull("Resource avatars/${avatar.id}.webp should exist", stream)
            val codec = org.jetbrains.skia.Codec.makeFromData(org.jetbrains.skia.Data.makeFromBytes(stream.use { it.readBytes() }))
            assertTrue("Avatar ${avatar.id} frameCount should be >= 1", codec.frameCount >= 1)
        }
        for (id in avatarArt.keys) assertNotNull("Drawn avatar $id should be in the picker", animatedAvatar(id))
    }

    @Test
    fun drawnPicturesRenderAtEveryTimeAndLoopWithoutASeam() {
        for (id in avatarArt.keys) {
            val first = render(id, 0f)
            assertTrue("$id should paint something", first.any { it != 0 })
            for (t in listOf(0.7f, 1.1f, 2.5f, 3.9f)) render(id, t)
            val last = render(id, ART_LOOP)
            var off = 0
            for (i in first.indices) for (sh in intArrayOf(0, 8, 16)) if (kotlin.math.abs(((first[i] shr sh) and 0xFF) - ((last[i] shr sh) and 0xFF)) > 10) { off++; break }
            assertTrue("$id: the loop must end where it starts ($off of ${first.size} pixels differ)", off < first.size / 100)
        }
    }

    @Test
    fun defaultAvatarIsDrawn() {
        for (i in 0..7) assertTrue(avatarArt.containsKey(defaultAnimatedAvatar(i).id))
        assertEquals("ninja", defaultAnimatedAvatar(0).id)
    }

    @Test
    fun allGroupsRepresented() {
        val groups = animatedAvatars.map { it.group }.distinct()
        assertEquals(animatedAvatarGroups.sorted(), groups.sorted())
        for (g in animatedAvatarGroups) assertTrue("Group $g should have avatars", animatedAvatars.count { it.group == g } >= 5)
    }
}
