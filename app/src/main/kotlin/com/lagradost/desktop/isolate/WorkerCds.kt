package com.lagradost.desktop.isolate

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * A start-up archive (class data sharing) for the extension sandbox program: the classes it loads at every start, already parsed and checked. Measured with the
 * packaged app: the program was ready in ~2.7 s without it and ~1.95 s with it, so the extensions load that much sooner after every start.
 *
 * The archive is made by the APP, not by the sandbox: a program of the sandbox could otherwise leave crafted classes behind for its next start. The app runs the
 * program's own start once, outside the sandbox, with a throw-away data folder and no extension (nothing but the app's code runs: it is closed as soon as it says
 * it is ready), and the JVM writes the archive when it ends. The file lies in a folder the sandbox may only read. One archive per version of the app and its
 * runtime (the classes must be the same); an older one is deleted. Only for the packaged app (an archive holds classes from jar files, not from folders).
 */
internal object WorkerCds {
    private const val TAG = "WorkerCds"

    /** Options that keep the JVM's own messages off standard output, which is the sandbox's channel to the app */
    val quietLog = listOf("-Xlog:disable", "-Xlog:all=warning:stderr")

    @Volatile private var training = false

    private fun key(javaHome: File, classpath: List<String>): String {
        val text = javaHome.absolutePath + "|" + System.getProperty("java.version") + "|" + classpath.joinToString(File.pathSeparator)
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }

    fun usable(classpath: List<String>): Boolean = classpath.isNotEmpty() && classpath.all { it.endsWith(".jar", true) } && System.getProperty("cloudstream.workercds") != "off"

    /** The archive for this app and runtime when it has been made, else null */
    fun archive(root: File, javaHome: File, classpath: List<String>): File? {
        if (!usable(classpath)) return null
        return File(File(root, "cds"), "worker-${key(javaHome, classpath)}.jsa").takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * Makes the archive a little after the start when there is none (once per version). [args] are the program's options up to its main class, with [trainData] as its
     * data folder; the program is [javaExe] with the small environment [env].
     */
    fun trainLater(root: File, javaHome: File, javaExe: File, classpath: List<String>, env: Map<String, String>, args: (data: File, tmp: File) -> List<String>) {
        if (!usable(classpath) || training || archive(root, javaHome, classpath) != null) return
        training = true
        Thread({
            try {
                // well after the start, when the extensions are loaded and the first pages are drawn
                Thread.sleep(90_000)
                train(root, javaHome, javaExe, classpath, env, args)
            } catch (t: Throwable) {
                Log.w(TAG, "the start-up archive of the sandbox was not made: $t")
            } finally {
                training = false
            }
        }, "sandbox-cds").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    private fun train(root: File, javaHome: File, javaExe: File, classpath: List<String>, env: Map<String, String>, args: (data: File, tmp: File) -> List<String>) {
        val dir = File(root, "cds").also { it.mkdirs() }
        val target = File(dir, "worker-${key(javaHome, classpath)}.jsa")
        val partial = File(dir, target.name + ".part").also { it.delete() }
        val trainData = File(root, "cds-train").also { it.deleteRecursively(); it.mkdirs() }
        val trainTmp = File(trainData, "tmp").also { it.mkdirs() }
        val started = System.currentTimeMillis()
        val command = listOf(javaExe.absolutePath, "-XX:ArchiveClassesAtExit=" + partial.absolutePath) + quietLog + args(trainData, trainTmp)
        val pb = ProcessBuilder(command).directory(trainData).redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.environment().clear()
        pb.environment().putAll(env + mapOf("TEMP" to trainTmp.absolutePath, "TMP" to trainTmp.absolutePath, "LOCALAPPDATA" to trainData.absolutePath))
        val p = pb.start()
        // low priority: it is work for later starts, the app in use comes first
        runCatching {
            val k = com.sun.jna.platform.win32.Kernel32.INSTANCE
            val h = k.OpenProcess(0x0200 or 0x0400, false, p.pid().toInt()) // PROCESS_SET_INFORMATION | PROCESS_QUERY_INFORMATION
            if (h != null) { k.SetPriorityClass(h, com.sun.jna.platform.win32.WinDef.DWORD(0x00004000)); k.CloseHandle(h) }
        }
        var ready = false
        p.errorStream.bufferedReader().use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (line.contains("worker: ready")) { ready = true; break }
            }
            // ready: its end (standard input closed) makes it write the archive and exit
            runCatching { p.outputStream.close() }
            r.readText()
        }
        val ended = p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
        if (!ended) p.destroyForcibly()
        trainData.deleteRecursively()
        if (!ready || !ended || p.exitValue() != 0 || !partial.isFile || partial.length() == 0L) {
            partial.delete()
            Log.w(TAG, "the start-up archive of the sandbox was not made (ready=$ready, ended=$ended)")
            return
        }
        dir.listFiles()?.filter { it.name.startsWith("worker-") && it.name.endsWith(".jsa") && it != target }?.forEach { it.delete() }
        if (!partial.renameTo(target)) { partial.delete(); return }
        Log.i(TAG, "start-up archive of the sandbox made in ${System.currentTimeMillis() - started} ms (${target.length() / (1 shl 20)} MB): the next start of the sandbox is quicker")
    }
}
