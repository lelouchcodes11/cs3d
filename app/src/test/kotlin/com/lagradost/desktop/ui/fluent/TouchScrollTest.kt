package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.gestures.Orientation
import com.lagradost.desktop.platform.isPromotedTouch
import kotlin.test.*

class TouchScrollTest {
    @Test fun detectsWindowsFingerSignatureButNotMouseOrPen() {
        assertTrue(isPromotedTouch(0xFF515780L))
        assertTrue(isPromotedTouch(0xFF5157FFL))
        assertTrue(isPromotedTouch(0xFFFFFFFFFF515780UL.toLong()))
        assertFalse(isPromotedTouch(0))
        assertFalse(isPromotedTouch(0xFF515700L)) // promoted pen
        assertFalse(isPromotedTouch(0xFF51577FL))
        assertFalse(isPromotedTouch(0x12345680L))
    }

    @Test fun autoDoesNotEnableMouseDragScrolling() {
        assertTrue(useMouseSwipe(TouchSwipes.Auto, true))
        assertFalse(useMouseSwipe(TouchSwipes.Auto, false))
        assertTrue(useMouseSwipe(TouchSwipes.On, false))
        assertTrue(useMouseSwipe(TouchSwipes.On, true))
        assertFalse(useMouseSwipe(TouchSwipes.Off, false))
        assertFalse(useMouseSwipe(TouchSwipes.Off, true))
    }

    @Test fun tapsAndSmallJitterDoNotStartScrolling() {
        assertNull(swipeAxis(0f, 0f, 8f))
        assertNull(swipeAxis(7f, -6f, 8f))
        assertNull(swipeAxis(8f, 8f, 8f))
    }

    @Test fun horizontalShelvesAndVerticalPagesChooseDifferentAxes() {
        assertEquals(Orientation.Horizontal, swipeAxis(-30f, 9f, 8f))
        assertEquals(Orientation.Vertical, swipeAxis(9f, -30f, 8f))
        assertEquals(Orientation.Vertical, swipeAxis(20f, 20f, 8f))
    }
}
