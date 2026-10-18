package com.lagradost.desktop.tools

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi

/**
 * Dev tool: what composition costs, per composable. The compiler brackets every composable with trace markers; while a tracer is set each pair is
 * timed here (inclusive time, self time without the composables it called, number of runs). /composetrace?start=1 ... something happens ...
 * /composetrace?stop=1&top=40 lists the most expensive by inclusive time and by self time. Only composition is timed (not measure, layout or
 * draw), but a lazy list composes its items inside its measure pass, so those are counted too.
 */
@OptIn(InternalComposeTracingApi::class)
object ComposeTrace : CompositionTracer {
    private class Open(val info: String, val start: Long) { var children = 0L }

    private class Totals { var runs = 0; var inclusive = 0L; var self = 0L; var worst = 0L }

    private val stacks = ThreadLocal.withInitial { ArrayList<Open>() }
    private val totals = HashMap<String, Totals>()
    private val lock = Any()
    @Volatile private var on = false
    private var startedAt = 0L

    fun start() {
        synchronized(lock) { totals.clear() }
        startedAt = System.nanoTime()
        on = true
        Composer.setTracer(this)
    }

    fun stop(top: Int = 40): String {
        on = false
        Composer.setTracer(null)
        val wall = (System.nanoTime() - startedAt) / 1_000_000
        val rows = synchronized(lock) { totals.entries.map { it.key to it.value } }
        fun ms(n: Long) = String.format(java.util.Locale.ROOT, "%.1f", n / 1_000_000.0)
        val sb = StringBuilder("traced for $wall ms, ${rows.size} composables, ${rows.sumOf { it.second.runs }} runs, self total ${ms(rows.sumOf { it.second.self })} ms\n")
        sb.append("By self time (ms self | ms inclusive | runs | worst single run ms):\n")
        rows.sortedByDescending { it.second.self }.take(top).forEach { (k, t) -> sb.append("  ${ms(t.self)} | ${ms(t.inclusive)} | ${t.runs} | ${ms(t.worst)}  $k\n") }
        sb.append("By inclusive time:\n")
        rows.sortedByDescending { it.second.inclusive }.take(top).forEach { (k, t) -> sb.append("  ${ms(t.inclusive)} | self ${ms(t.self)} | ${t.runs} | ${ms(t.worst)}  $k\n") }
        return sb.toString()
    }

    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
        stacks.get().add(Open(info, System.nanoTime()))
    }

    override fun traceEventEnd() {
        val stack = stacks.get()
        if (stack.isEmpty()) return
        val open = stack.removeAt(stack.size - 1)
        val took = System.nanoTime() - open.start
        if (stack.isNotEmpty()) stack[stack.size - 1].children += took
        synchronized(lock) {
            val t = totals.getOrPut(open.info) { Totals() }
            t.runs++
            t.inclusive += took
            t.self += took - open.children
            if (took > t.worst) t.worst = took
        }
    }

    override fun isTraceInProgress(): Boolean = on
}
