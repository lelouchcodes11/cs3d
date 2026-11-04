package com.lagradost.desktop.platform

import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File
import kotlin.concurrent.thread

/** Which of Skia's renderers draws the window (Settings > General > Performance > Graphics renderer) */
enum class RenderChoice(val label: String, val detail: String, val api: String?) {
    Auto("Automatic", "Direct3D. If the app did not start properly twice in a row, a more compatible renderer is used by itself.", null),
    Direct3D("Direct3D", "The fastest on most PCs.", "DIRECT3D"),
    OpenGL("OpenGL", "For older graphics cards and drivers. Try it if the window flickers or shows black or white patches.", "OPENGL"),
    Software("Software", "Drawn by the processor: the most compatible and the slowest. For a PC with no usable graphics driver.", "SOFTWARE_FAST"),
}

/** How much the look costs: Auto looks at this PC, Light always takes load off, Full never does */
enum class PerformanceMode(val label: String, val detail: String) {
    Auto("Automatic", "Light on a PC with little memory or few processor cores, the full look on any other."),
    Light("Light", "Fewer and shorter animations, no moving profile pictures or hover zoom, no seek preview, and a video that stops blending frames. Smaller picture and texture caches."),
    Full("Full", "Everything on, whatever the PC."),
}

/** Is this PC a weak one? Memory and processor cores are what decides whether the full look runs smoothly */
object LowEnd {
    /** below this much memory (decimal GB as Windows counts it: a 4 GB PC reports about 3.9) the light look is used in Automatic mode */
    const val MEMORY_GB = 6.0
    const val CORES = 2

    class Facts(val memoryGb: Double, val cores: Int)

    fun facts(): Facts {
        val memory = runCatching { (java.lang.management.ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / 1e9 }.getOrDefault(16.0)
        return Facts(memory, Runtime.getRuntime().availableProcessors())
    }

    fun isLowEnd(f: Facts): Boolean = f.memoryGb < MEMORY_GB || f.cores <= CORES

    private val measured: Facts by lazy { facts() }

    /** This PC is weak (Automatic mode switches to the light look); `-Dcloudstream.lowend=true|false` decides it for a test */
    val detected: Boolean by lazy { System.getProperty("cloudstream.lowend")?.toBooleanStrictOrNull() ?: isLowEnd(measured) }

    fun describe(): String = "This PC: %.1f GB memory, %d processor threads.".format(measured.memoryGb, measured.cores)

    /** The light look is in effect (read by the animations and the video path through Appearance.light) */
    fun active(mode: PerformanceMode): Boolean = when (mode) {
        PerformanceMode.Auto -> detected
        PerformanceMode.Light -> true
        PerformanceMode.Full -> false
    }
}

/**
 * The renderer of the window, chosen before the first window exists. Skia draws with Direct3D by default; on some PCs (old drivers, hybrid graphics,
 * virtual machines) that flickers, shows patches or leaves the window blank. Two ways out:
 *
 *  - the user picks OpenGL or Software (Settings > General > Performance), and the choice is used from the next start;
 *  - Automatic notices a start that did not become healthy (the app was gone or killed within 20 s of the window opening, twice in a row) and moves one
 *    step down the list Direct3D, OpenGL, Software for good, until the user picks Automatic again.
 *
 * The state is `files/graphics.txt` (`level=` the renderer reached, `pending=` starts since the last healthy one). `-Dskiko.renderApi=...` on the command line wins over all of it.
 */
object Graphics {
    private const val TAG = "Graphics"
    private const val PREF_KEY = "desktop_gfx_renderer"
    private const val HEALTHY_AFTER_MS = 20_000L
    private val chain = listOf("DIRECT3D", "OPENGL", "SOFTWARE_FAST")

    /** What the renderer is in this run, and why (for the diagnostics) */
    @Volatile
    var note = "not chosen yet"
        private set

    @Volatile
    var api: String = "DIRECT3D"
        private set

    /** The automatic choice stepped down from Direct3D (the app did not start properly with the faster renderers) */
    @Volatile
    var fellBack = false
        private set

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    fun choice(): RenderChoice = runCatching { RenderChoice.valueOf(prefs().getString(PREF_KEY, null) ?: "Auto") }.getOrDefault(RenderChoice.Auto)

    fun setChoice(c: RenderChoice) {
        runCatching { prefs().edit().putString(PREF_KEY, c.name).apply() }
        // picking Automatic again is the way back to Direct3D after an automatic fall back
        if (c == RenderChoice.Auto) writeState(State(0, 0))
    }

    class State(val level: Int, val pending: Int)

    internal fun parseState(text: String?): State {
        fun value(key: String) = text?.lineSequence()?.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim()?.toIntOrNull()
        return State((value("level") ?: 0).coerceIn(0, chain.size - 1), (value("pending") ?: 0).coerceAtLeast(0))
    }

    private fun stateFile() = File(File(AndroidRuntime.dataDir, "files").also { it.mkdirs() }, "graphics.txt")

    private fun readState(): State = parseState(runCatching { stateFile().takeIf { it.isFile }?.readText() }.getOrNull())

    private fun writeState(s: State) {
        runCatching { stateFile().writeText("level=${s.level}\npending=${s.pending}\n") }
    }

    /**
     * What an automatic start does with the state it finds: [State.pending] starts did not become healthy; the third start in a row steps down one renderer
     * (a start counts as unhealthy until [markHealthy]), and the count starts again at the new level.
     */
    internal fun next(prev: State): State {
        val stepDown = prev.pending >= 2 && prev.level < chain.size - 1
        return if (stepDown) State(prev.level + 1, 1) else State(prev.level, prev.pending + 1)
    }

    /**
     * `--safe-graphics` (or `--software`), `--opengl` and `--direct3d` on the command line: a renderer choice that is saved, for a window that flickers or
     * is blank so badly that Settings can not be reached (the "CloudStream (safe graphics)" shortcut of the installer and the portable folder use it).
     */
    fun choiceFromArgs(args: Array<String>): RenderChoice? = args.firstNotNullOfOrNull {
        when (it.lowercase()) {
            "--safe-graphics", "--software" -> RenderChoice.Software
            "--opengl" -> RenderChoice.OpenGL
            "--direct3d" -> RenderChoice.Direct3D
            "--auto-graphics" -> RenderChoice.Auto
            else -> null
        }
    }

    /** Called once at the start, after the preferences exist and before the first window: sets `skiko.renderApi` and starts the health timer */
    fun applyAtStart(explicitProperty: Boolean, fromArgs: RenderChoice? = null) {
        runCatching {
            if (fromArgs != null) {
                setChoice(fromArgs)
                thread(name = "graphics-notice", isDaemon = true) {
                    Thread.sleep(6_000)
                    com.lagradost.desktop.ui.Toasts.show("Drawing with ${fromArgs.label} from now on. Change it in Settings > General > Performance.", true)
                }
            }
            if (explicitProperty) { api = System.getProperty("skiko.renderApi"); note = "$api (set on the command line)"; return }
            val choice = choice()
            if (choice != RenderChoice.Auto) {
                api = choice.api!!
                System.setProperty("skiko.renderApi", api)
                note = "$api (your choice)"
                return
            }
            val before = readState()
            // another window of a running app: it uses the level reached and leaves the count of the first window alone
            if (MultiInstance.number > 1) {
                api = chain[before.level]
                System.setProperty("skiko.renderApi", api)
                fellBack = before.level > 0
                note = "$api (automatic)"
                return
            }
            val now = next(before)
            writeState(now)
            api = chain[now.level]
            System.setProperty("skiko.renderApi", api)
            fellBack = now.level > 0
            note = if (now.level == 0) "$api (automatic)" else "$api (automatic: the app did not start properly with the faster renderers)"
            Log.i(TAG, "renderer $api (level ${now.level}, start ${now.pending} since the last healthy one)")
            if (now.level > before.level) {
                thread(name = "graphics-notice", isDaemon = true) {
                    Thread.sleep(6_000)
                    com.lagradost.desktop.ui.Toasts.show("The app did not start properly last time, so it now draws with $api. Change it in Settings > General > Performance.", true)
                }
            }
            autoLevel = now.level
            thread(name = "graphics-healthy", isDaemon = true) {
                Thread.sleep(HEALTHY_AFTER_MS)
                markHealthy()
            }
        }.onFailure { Log.w(TAG, "could not choose the renderer: ${it.message}") }
    }

    /** The level this automatic start chose (-1: not an automatic start, or this start already counted as healthy) */
    @Volatile
    private var autoLevel = -1

    /** The level stays, only the count of unhealthy starts goes back to zero */
    private fun markHealthy() {
        val level = autoLevel
        if (level < 0) return
        autoLevel = -1
        writeState(State(level, 0))
    }

    /**
     * The user pressed or scrolled inside the app's own drawing (even the window's buttons are drawn by the app), so the window shows what it should and this
     * start is a good one, also when it is closed within 20 s. Before, opening and closing the app quickly three times in a row (or the app restarting itself
     * after a restore) moved it to a slower renderer for good, and the app stayed laggy. A blank window has nothing to press, so that case still steps down.
     */
    fun markUsed() {
        val level = autoLevel
        if (level < 0) return
        autoLevel = -1
        thread(name = "graphics-healthy", isDaemon = true) { writeState(State(level, 0)) }
    }
}
