package com.lagradost.desktop.tools

import java.awt.EventQueue

/**
 * Dev tool: samples what the UI thread is doing every few milliseconds, to find what a long frame spends its time on (the start-up profile looks
 * every 50 ms for a fixed time, too coarse for a hitch of 100 ms). /edtprofile?start=1 begins, /edtprofile?stop=1&top=40 answers with the code
 * the busy samples were in: the app's own frames, and the leaf frames, most frequent first. Idle samples (waiting for an event) are left out.
 */
object EdtSampler {
    @Volatile private var thread: Thread? = null
    private val inclusive = HashMap<String, Int>()
    private val leaves = HashMap<String, Int>()
    private val callers = HashMap<String, Int>()
    private val lock = Any()
    private var busy = 0
    private var total = 0
    private class Sample(val at: Long, val frames: List<String>, val leaf: String)
    private val timed = ArrayList<Sample>()
    private val idleAt = ArrayList<Long>()
    private var cpuAtStart = HashMap<Long, Long>()

    private fun edt(): Thread? {
        var found: Thread? = null
        EventQueue.invokeAndWait { found = Thread.currentThread() }
        return found
    }

    fun start(everyMs: Long = 4, threadName: String? = null) {
        stop()
        synchronized(lock) { inclusive.clear(); leaves.clear(); callers.clear(); timed.clear(); idleAt.clear(); busy = 0; total = 0 }
        val bean = java.lang.management.ManagementFactory.getThreadMXBean()
        cpuAtStart = HashMap<Long, Long>().also { m -> bean.allThreadIds.forEach { id -> m[id] = bean.getThreadCpuTime(id) } }
        val target = (if (threadName == null) edt() else Thread.getAllStackTraces().keys.firstOrNull { it.name == threadName }) ?: return
        val sampler = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val trace = target.stackTrace
                synchronized(lock) {
                    total++
                    // idle = waiting for the next event; a park or wait inside an event that is being handled is the thread being held up by something
                    val idle = if (threadName != null) trace.isEmpty() || trace[0].methodName == "park" || trace[0].methodName == "wait0" else trace.isEmpty() || trace.any { it.className == "java.awt.EventQueue" && it.methodName == "getNextEvent" } || trace[0].className.contains("WToolkit") || trace[0].methodName == "eventLoop"
                    if (idle) idleAt.add(System.nanoTime())
                    if (!idle) {
                        busy++
                        timed.add(Sample(System.nanoTime(), trace.filter { !it.className.startsWith("java.") && !it.className.startsWith("jdk.") && !it.className.startsWith("sun.") && !it.className.startsWith("kotlin.") }.map { it.className.substringAfterLast('.') + "." + it.methodName }.distinct(), if (trace[0].methodName == "park" || trace[0].methodName == "wait0" || trace[0].methodName == "sleep0") "HELD UP: " + trace.take(7).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName } else trace[0].className.substringAfterLast('.') + "." + trace[0].methodName))
                        val seen = HashSet<String>()
                        for (f in trace) {
                            val name = f.className.substringAfterLast('.') + "." + f.methodName
                            val own = f.className.startsWith("com.lagradost") || f.className.startsWith("coil3") || f.className.startsWith("org.jetbrains.skia") || f.className.startsWith("androidx.compose.ui.text") || f.className.startsWith("androidx.compose.foundation.lazy")
                            if (own && seen.add(name)) inclusive[name] = (inclusive[name] ?: 0) + 1
                        }
                        // who asks for the leaf: the first six frames above it that are not the JDK's own plumbing
                        if (trace.size > 1 && trace[0].methodName == "getStackAccessControlContext") {
                            val chain = trace.drop(1).filter { !it.className.startsWith("java.security") }.take(7).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName }
                            callers[chain] = (callers[chain] ?: 0) + 1
                        }
                        trace.firstOrNull()?.let { leaves[it.className.substringAfterLast('.') + "." + it.methodName] = (leaves[it.className.substringAfterLast('.') + "." + it.methodName] ?: 0) + 1 }
                    }
                }
                try { Thread.sleep(everyMs) } catch (_: InterruptedException) { return@Thread }
            }
        }
        sampler.isDaemon = true
        sampler.name = "edt-sampler"
        thread = sampler
        sampler.start()
    }

    /** What the busy samples inside each frame longer than [overMs] (the last [seconds] seconds of FrameStats) were in: the [top] most frequent frames of each */
    fun jank(seconds: Int, overMs: Int, top: Int = 14): String {
        val sb = StringBuilder()
        val now = System.nanoTime(); val wall = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS")
        synchronized(lock) {
            for ((end, len) in FrameStats.frames(seconds).filter { it.second > overMs * 1_000_000L }) {
                val inside = timed.filter { it.at in (end - len)..end }
                sb.append("frame ending ").append(fmt.format(java.util.Date(wall - (now - end) / 1_000_000))).append(" ").append(len / 1_000_000).append(" ms, ").append(inside.size).append(" busy, ").append(idleAt.count { it in (end - len)..end }).append(" idle samples\n")
                val count = HashMap<String, Int>()
                for (smp in inside) for (n in smp.frames) count[n] = (count[n] ?: 0) + 1
                count.entries.filter { it.value < inside.size }.sortedByDescending { it.value }.take(top).forEach { sb.append("    ").append(it.value).append("  ").append(it.key).append('\n') }
                val leafCount = HashMap<String, Int>()
                for (smp in inside) leafCount[smp.leaf] = (leafCount[smp.leaf] ?: 0) + 1
                sb.append("    leaves: ").append(leafCount.entries.sortedByDescending { it.value }.take(6).joinToString(", ") { it.value.toString() + " " + it.key }).append('\n')
            }
        }
        return sb.toString()
    }

    /** CPU time each thread used since [start], busiest first (is the UI thread working, or the render thread, the image decoders, the JIT?) */
    fun threads(top: Int = 14): String {
        val bean = java.lang.management.ManagementFactory.getThreadMXBean()
        val rows = bean.allThreadIds.toList().mapNotNull { id: Long ->
            val info = bean.getThreadInfo(id, 0) ?: return@mapNotNull null
            val used = (bean.getThreadCpuTime(id) - (cpuAtStart[id] ?: 0L)) / 1_000_000
            if (used > 0) used to info.threadName else null
        }.sortedByDescending { it.first }.take(top)
        return "thread CPU since start (ms):\n" + rows.joinToString("\n") { "  " + it.first + "  " + it.second }
    }

    fun stop(top: Int = 40): String {
        thread?.interrupt()
        thread = null
        synchronized(lock) {
            val sb = StringBuilder("samples=$total busy=$busy\nApp and drawing frames (samples they were in, most first):\n")
            inclusive.entries.sortedByDescending { it.value }.take(top).forEach { sb.append("  ").append(it.value).append("  ").append(it.key).append('\n') }
            sb.append("Leaf frames:\n")
            leaves.entries.sortedByDescending { it.value }.take(15).forEach { sb.append("  ").append(it.value).append("  ").append(it.key).append('\n') }
            if (callers.isNotEmpty()) {
                sb.append("Who asks for getStackAccessControlContext:\n")
                callers.entries.sortedByDescending { it.value }.take(6).forEach { sb.append("  ").append(it.value).append("  ").append(it.key).append('\n') }
            }
            return sb.toString()
        }
    }
}
