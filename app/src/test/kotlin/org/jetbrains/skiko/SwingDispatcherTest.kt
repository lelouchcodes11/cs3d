package org.jetbrains.skiko

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SwingDispatcherTest {
    /** The app's copy (posting inside doPrivileged) is the one the UI uses; skiko's own would come back if the class path order changed */
    @Test
    fun composeUsesTheAppsDispatcher() {
        assertSame(SwingDispatcher, MainUIDispatcher)
    }

    @Test
    fun itRunsOnTheUiThreadAndKeepsTime() = runBlocking {
        withTimeout(10_000) {
            val onEdt = withContext(MainUIDispatcher) { SwingUtilities.isEventDispatchThread() }
            assertTrue(onEdt)
            val start = System.nanoTime()
            withContext(MainUIDispatcher) { delay(120) }
            assertTrue((System.nanoTime() - start) / 1_000_000 >= 110, "delay ran short")
        }
    }
}
