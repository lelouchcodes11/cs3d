package com.lagradost.desktop.platform

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Windows limits for a program that this app starts and that talks to the internet (the torrent engine), applied by the operating system and not by
 * the app: whatever the program does or is made to do, Windows enforces them.
 *
 *  - a job object: the program ends when this app ends, can start no other program, and cannot reach the clipboard, other windows, the desktop,
 *    the display settings or global atoms;
 *  - the low integrity level: it can read the user's files but cannot change anything that is not marked low (the registry's Run keys, the startup
 *    folder, documents, this app's own files). The folders it works in are marked low first ([prepareFolder]); the program itself is not.
 *
 * Applied to the process right after it started (the process is ours, same user). When a step fails the program keeps running with the steps that
 * worked, and the log says which.
 */
object ProcessConfine {
    private const val TAG = "ProcessConfine"

    private interface Kernel : Library {
        fun CreateJobObjectW(attributes: Pointer?, name: WString?): Pointer?
        fun SetInformationJobObject(job: Pointer, infoClass: Int, info: Pointer, length: Int): Boolean
        fun AssignProcessToJobObject(job: Pointer, process: Pointer): Boolean
        fun OpenProcess(access: Int, inherit: Boolean, pid: Int): Pointer?
        fun CloseHandle(handle: Pointer): Boolean
    }

    private interface Advapi : Library {
        fun OpenProcessToken(process: Pointer, access: Int, token: PointerByReference): Boolean
        fun ConvertStringSidToSidW(sid: WString, out: PointerByReference): Boolean
        fun SetTokenInformation(token: Pointer, infoClass: Int, info: Pointer, length: Int): Boolean
        fun GetLengthSid(sid: Pointer): Int
    }

    private val kernel: Kernel? by lazy { runCatching { Native.load("kernel32", Kernel::class.java) }.getOrNull() }
    private val advapi: Advapi? by lazy { runCatching { Native.load("advapi32", Advapi::class.java) }.getOrNull() }

    /** The jobs stay open as long as this app runs: closing the last handle of a job ends the programs in it */
    private val jobs = ArrayList<Pointer>()

    private const val PROCESS_TERMINATE = 0x0001
    private const val PROCESS_SET_QUOTA = 0x0100
    private const val PROCESS_QUERY_INFORMATION = 0x0400
    private const val TOKEN_QUERY = 0x0008
    private const val TOKEN_ADJUST_DEFAULT = 0x0080
    private const val TOKEN_INTEGRITY_LEVEL = 25
    private const val SE_GROUP_INTEGRITY = 0x20
    private const val LOW_INTEGRITY_SID = "S-1-16-4096"

    private const val JOB_EXTENDED_LIMITS = 9
    private const val JOB_UI_RESTRICTIONS = 4
    private const val LIMIT_ACTIVE_PROCESS = 0x8
    private const val LIMIT_KILL_ON_JOB_CLOSE = 0x2000
    private const val UI_ALL = 0xFF

    private val windows: Boolean get() = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /** The low mark of a folder (and what is created in it later), kept in a file so the work is done once */
    private const val LOW_MARK = ".marked-low"

    /**
     * Marks [folder] as low integrity, so that the confined program can write in it. Returns false when it could not be done (the program should then not
     * be lowered, it could not write its files).
     */
    fun prepareFolder(folder: File): Boolean {
        if (!windows) return false
        folder.mkdirs()
        val mark = File(folder, LOW_MARK)
        if (mark.isFile) return true
        return runCatching {
            val p = ProcessBuilder("icacls", folder.absolutePath, "/setintegritylevel", "(OI)(CI)L", "/T", "/C", "/Q").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            val done = p.waitFor(60, TimeUnit.SECONDS)
            if (!done) p.destroyForcibly()
            val ok = done && p.exitValue() == 0
            if (ok) mark.writeText("low integrity level set by CloudStream so that the torrent engine can write here")
            ok
        }.onFailure { Log.w(TAG, "could not mark ${folder.path} low: $it") }.getOrDefault(false)
    }

    /**
     * Puts the running [process] into a job and, when [lowIntegrity], lowers its integrity level. Returns what was applied ("job", "job+low", "none").
     */
    fun confine(process: Process, lowIntegrity: Boolean): String {
        val k = kernel ?: return "none"
        val a = advapi ?: return "none"
        if (!windows) return "none"
        val applied = ArrayList<String>()
        var handle: Pointer? = null
        try {
            handle = k.OpenProcess(PROCESS_TERMINATE or PROCESS_SET_QUOTA or PROCESS_QUERY_INFORMATION, false, process.pid().toInt())
            if (handle == null) { Log.w(TAG, "cannot open process ${process.pid()}"); return "none" }
            val job = k.CreateJobObjectW(null, null)
            if (job != null) {
                val limits = Memory(144).also { it.clear() }
                limits.setInt(16, LIMIT_KILL_ON_JOB_CLOSE or LIMIT_ACTIVE_PROCESS) // BasicLimitInformation.LimitFlags
                limits.setInt(40, 1) // .ActiveProcessLimit: the program itself, nothing it starts
                val ui = Memory(4).also { it.setInt(0, UI_ALL) }
                val ok = k.SetInformationJobObject(job, JOB_EXTENDED_LIMITS, limits, 144) &&
                    k.SetInformationJobObject(job, JOB_UI_RESTRICTIONS, ui, 4) &&
                    k.AssignProcessToJobObject(job, handle)
                if (ok) { synchronized(jobs) { jobs += job }; applied += "job" } else { Log.w(TAG, "job limits not applied (error ${Native.getLastError()})"); k.CloseHandle(job) }
            }
            if (lowIntegrity) {
                val token = PointerByReference()
                if (a.OpenProcessToken(handle, TOKEN_QUERY or TOKEN_ADJUST_DEFAULT, token)) {
                    val sid = PointerByReference()
                    if (a.ConvertStringSidToSidW(WString(LOW_INTEGRITY_SID), sid)) {
                        val label = Memory(32).also { it.clear(); it.setPointer(0, sid.value); it.setInt(8, SE_GROUP_INTEGRITY) }
                        if (a.SetTokenInformation(token.value, TOKEN_INTEGRITY_LEVEL, label, 16 + a.GetLengthSid(sid.value))) applied += "low"
                        else Log.w(TAG, "integrity level not lowered (error ${Native.getLastError()})")
                    }
                    k.CloseHandle(token.value)
                } else Log.w(TAG, "cannot open the token of process ${process.pid()} (error ${Native.getLastError()})")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "could not confine process ${process.pid()}: $t")
        } finally {
            handle?.let { k.CloseHandle(it) }
        }
        val result = if (applied.isEmpty()) "none" else applied.joinToString("+")
        Log.i(TAG, "process ${process.pid()}: $result")
        return result
    }

    /** SHA-256 of a file, lower case hex */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
