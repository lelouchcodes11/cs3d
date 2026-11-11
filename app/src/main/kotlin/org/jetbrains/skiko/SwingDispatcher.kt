@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@file:Suppress("DEPRECATION", "removal")

package org.jetbrains.skiko

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import java.awt.EventQueue
import java.awt.event.ActionListener
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.concurrent.TimeUnit
import javax.swing.Timer
import kotlin.coroutines.CoroutineContext

/**
 * Replaces skiko's own `SwingDispatcher` (a private object behind `MainUIDispatcher`, the dispatcher of every coroutine of the Compose UI): the
 * app's classes come first on the class path, so the JVM loads this one. It does the same thing with one difference: the event is posted inside
 * `doPrivileged`.
 *
 * Why: every `AWTEvent` records the security context of the code that creates it (`AccessController.getContext()` in its constructor, done by
 * Java 21 whether a security manager is installed or not), which walks the whole call stack. Compose resumes its animations, effects and frame
 * waiters through this dispatcher from deep inside the frame (100+ frames down), every frame, one event each. Profiled while scrolling: that walk
 * was 19 % of the UI thread's busy time (1210 of 1508 samples of `getStackAccessControlContext` came from here). Inside `doPrivileged` the walk
 * stops at it: a handful of frames.
 *
 * Safe: nothing of the caller runs inside the privileged block (only the posting), and the app's extension sandbox (PluginSandbox) decides from
 * the class loaders on the stack when a check is made, not from contexts recorded in events.
 */
internal object SwingDispatcher : CoroutineDispatcher(), Delay {
    private val post = { block: Runnable -> PrivilegedAction<Unit> { EventQueue.invokeLater(block) } }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        AccessController.doPrivileged(post(block))
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val timer = schedule(timeMillis, TimeUnit.MILLISECONDS) { with(continuation) { resumeUndispatched(Unit) } }
        continuation.invokeOnCancellation { timer.stop() }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val timer = schedule(timeMillis, TimeUnit.MILLISECONDS) { block.run() }
        return DisposableHandle { timer.stop() }
    }

    /** A one-shot Swing timer (its events are made on the timer thread, where the stack is short anyway) */
    private fun schedule(time: Long, unit: TimeUnit, action: ActionListener): Timer =
        AccessController.doPrivileged(PrivilegedAction {
            Timer(unit.toMillis(time).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), action).apply {
                isRepeats = false
                start()
            }
        })
}
