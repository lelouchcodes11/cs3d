package com.lagradost.desktop.ui.fluent

import com.lagradost.desktop.platform.WindowsTouch
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.Frame
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TouchScrollNativeTest {
    @Test fun nativeObserverSurvivesCanvasRecreationAndWindowDisposal() {
        assumeTrue(System.getProperty("os.name", "").startsWith("Windows"))
        EventQueue.invokeAndWait {
            val window = Frame()
            try {
                window.add(Canvas())
                window.setSize(300, 300)
                window.addNotify()
                assertTrue(WindowsTouch.install(window))
                assertTrue(WindowsTouch.install(window)) // no duplicate native subclass
                window.removeAll()
                window.add(Canvas())
                window.validate()
                assertTrue(WindowsTouch.install(window))
            } finally {
                window.dispose()
            }
        }
    }
}
