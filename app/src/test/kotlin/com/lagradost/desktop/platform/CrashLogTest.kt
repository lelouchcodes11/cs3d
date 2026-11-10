package com.lagradost.desktop.platform

import com.lagradost.desktop.CrashLog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** crash.log keeps every report whole when several threads crash at the same moment, and stays small */
class CrashLogTest {
    @Test
    fun reportsFromManyThreadsAtOnceStayWhole() {
        val file = File.createTempFile("crash", ".log").also { it.delete(); it.deleteOnExit() }
        val report = { n: Int -> "CloudStream Desktop Crash Log\nThread: t$n\n" + "x".repeat(4000) + "\nend $n" }
        val start = java.util.concurrent.CountDownLatch(1)
        val threads = (1..8).map { n -> Thread { start.await(); CrashLog.append(file, report(n)) }.apply { start() } }
        start.countDown()
        threads.forEach { it.join() }
        val text = file.readText()
        for (n in 1..8) assertTrue(text.contains(report(n)), "report $n is whole")
        assertEquals(8, Regex("CloudStream Desktop Crash Log").findAll(text).count())
    }

    @Test
    fun aLongLogKeepsItsNewestReports() {
        val file = File.createTempFile("crash", ".log").also { it.delete(); it.deleteOnExit() }
        for (n in 1..200) CrashLog.append(file, "CloudStream Desktop Crash Log\nThread: t$n\n" + "y".repeat(5000) + "\nend $n")
        assertTrue(file.length() <= CrashLog.MAX_BYTES + 6000, "size ${file.length()}")
        val text = file.readText()
        assertTrue(text.startsWith("CloudStream Desktop Crash Log"), "starts with a whole report")
        assertTrue(text.endsWith("end 200"))
        assertTrue(!text.contains("\nend 1\n"))
    }
}
