package com.lagradost.desktop.platform

import android.content.Intent
import android.util.Log
import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * "Restart the app" for extensions. On Android they start the launcher activity with
 * `Intent.makeRestartActivityTask(...)` and then kill their own process; Android brings the app back by itself.
 * Here the launcher intent is turned into a relaunch: a shutdown hook starts a new instance of this program
 * (the packaged launcher, or the same java command in development) that waits for this process to be gone
 * before it takes the single-instance lock, the data directory and the dev port.
 */
object AppRestart {
    private const val TAG = "AppRestart"
    private const val WAIT_ENV = "CLOUDSTREAM_RESTART_WAIT_PID"

    @Volatile
    private var requested = false

    /** The launcher activity of the app being started from inside the app: that is a restart */
    fun isRestartIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)

    /**
     * Relaunches the app once the process ends. Extensions kill the process right after starting the launcher
     * intent; when they do not, the app exits by itself after a short moment.
     */
    fun request() {
        if (requested) return
        requested = true
        Log.i(TAG, "restart requested")
        Runtime.getRuntime().addShutdownHook(Thread({ spawn() }, "cloudstream-restart"))
        thread(name = "cloudstream-restart-exit", isDaemon = true) {
            Thread.sleep(1500)
            Log.i(TAG, "extension did not end the process, exiting")
            exitProcess(0)
        }
    }

    /** Called first thing in `main`: a relaunched instance waits until the previous one has fully exited */
    fun awaitPreviousInstance() {
        val pid = System.getenv(WAIT_ENV)?.toLongOrNull() ?: return
        runCatching { ProcessHandle.of(pid).ifPresent { it.onExit().get(20, TimeUnit.SECONDS) } }
    }

    private fun spawn() {
        runCatching {
            val command = launchCommand() ?: return
            val builder = ProcessBuilder(command)
            builder.environment()[WAIT_ENV] = ProcessHandle.current().pid().toString()
            builder.redirectInput(Redirect.from(File("NUL")))
            builder.redirectOutput(Redirect.DISCARD)
            builder.redirectError(Redirect.DISCARD)
            System.getProperty("user.dir")?.let { builder.directory(File(it)) }
            builder.start()
        }
    }

    /** The packaged launcher, or the java command line of this process (development) */
    private fun launchCommand(): List<String>? {
        System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }?.let { return listOf(it) }
        val self = ProcessHandle.current().info().command().orElse(null) ?: return null
        val name = File(self).name.lowercase()
        if (name != "java.exe" && name != "javaw.exe" && name != "java") return listOf(self)
        val main = System.getProperty("sun.java.command")?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return null
        val command = ArrayList<String>()
        command += self
        command += runCatching { java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments }.getOrDefault(emptyList())
        System.getProperty("java.class.path")?.takeIf { it.isNotBlank() }?.let { command += listOf("-cp", it) }
        command += main
        return command
    }
}
