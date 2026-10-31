package com.lagradost.desktop.isolate

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts a program inside a Windows *AppContainer*: the sandbox of the Windows Store apps. The operating system, not this app, decides what the
 * program can reach:
 *  - files: only what is granted to the container ([grant]); the user's documents, browser data, keys and the rest of the disk are closed to it,
 *    reads and writes alike (a restricted token or the low integrity level only stops writes);
 *  - the registry, the clipboard, other windows and the desktop: closed;
 *  - the network: outbound internet only (the capability `internetClient`), no home or company network (no `privateNetworkClientServer`), and no
 *    connection to this PC's loopback addresses (an AppContainer cannot reach 127.0.0.1 unless the PC exempts it);
 *  - a job object: it ends with this app, cannot start another program, and is held to a memory limit.
 * The program talks to the app only through the three pipes created here (standard input, output and error).
 */
object AppContainerLauncher {
    private const val TAG = "AppContainer"

    private interface Kernel : Library {
        fun CreatePipe(read: PointerByReference, write: PointerByReference, attributes: Pointer, size: Int): Boolean
        fun SetHandleInformation(handle: Pointer, mask: Int, flags: Int): Boolean
        fun InitializeProcThreadAttributeList(list: Pointer?, count: Int, flags: Int, size: LongByReference): Boolean
        fun UpdateProcThreadAttribute(list: Pointer, flags: Int, attribute: Long, value: Pointer, size: Long, previous: Pointer?, returned: Pointer?): Boolean
        fun DeleteProcThreadAttributeList(list: Pointer)
        fun CreateProcessW(app: WString?, command: Pointer, processAttributes: Pointer?, threadAttributes: Pointer?, inherit: Boolean, flags: Int, environment: Pointer?, directory: WString?, startup: Pointer, info: Pointer): Boolean
        fun ResumeThread(thread: Pointer): Int
        fun GetExitCodeProcess(process: Pointer, code: IntByReference): Boolean
        fun TerminateProcess(process: Pointer, code: Int): Boolean
        fun WaitForSingleObject(handle: Pointer, millis: Int): Int
        fun CloseHandle(handle: Pointer): Boolean
        fun ReadFile(handle: Pointer, buffer: ByteArray, length: Int, read: IntByReference, overlapped: Pointer?): Boolean
        fun WriteFile(handle: Pointer, buffer: ByteArray, length: Int, written: IntByReference, overlapped: Pointer?): Boolean
        fun CreateJobObjectW(attributes: Pointer?, name: WString?): Pointer?
        fun SetInformationJobObject(job: Pointer, infoClass: Int, info: Pointer, length: Int): Boolean
        fun AssignProcessToJobObject(job: Pointer, process: Pointer): Boolean
        fun LocalFree(memory: Pointer?): Pointer?
    }

    private interface UserEnv : Library {
        fun CreateAppContainerProfile(name: WString, display: WString, description: WString, capabilities: Pointer?, count: Int, sid: PointerByReference): Int
        fun DeriveAppContainerSidFromAppContainerName(name: WString, sid: PointerByReference): Int
        fun DeleteAppContainerProfile(name: WString): Int
    }

    private interface Adv : Library {
        fun ConvertStringSidToSidW(text: WString, sid: PointerByReference): Boolean
        fun ConvertSidToStringSidW(sid: Pointer, text: PointerByReference): Boolean
        fun GetNamedSecurityInfoW(name: WString, type: Int, info: Int, owner: Pointer?, group: Pointer?, dacl: PointerByReference, sacl: Pointer?, descriptor: PointerByReference): Int
        fun SetEntriesInAclW(count: Int, entries: Pointer, old: Pointer?, new: PointerByReference): Int
        fun SetNamedSecurityInfoW(name: WString, type: Int, info: Int, owner: Pointer?, group: Pointer?, dacl: Pointer?, sacl: Pointer?): Int
        fun EqualSid(a: Pointer, b: Pointer): Boolean
    }

    private val kernel: Kernel by lazy { Native.load("kernel32", Kernel::class.java) }
    private val userenv: UserEnv by lazy { Native.load("userenv", UserEnv::class.java) }
    private val adv: Adv by lazy { Native.load("advapi32", Adv::class.java) }

    val available: Boolean by lazy {
        System.getProperty("os.name").orEmpty().lowercase().contains("win") && runCatching { kernel; userenv; adv; true }.getOrDefault(false)
    }

    private const val EXTENDED_STARTUPINFO_PRESENT = 0x00080000
    private const val CREATE_NO_WINDOW = 0x08000000
    private const val CREATE_SUSPENDED = 0x00000004
    private const val CREATE_UNICODE_ENVIRONMENT = 0x00000400
    private const val STARTF_USESTDHANDLES = 0x00000100
    private const val PROC_THREAD_ATTRIBUTE_SECURITY_CAPABILITIES = 0x00020009L
    private const val PROC_THREAD_ATTRIBUTE_HANDLE_LIST = 0x00020002L
    private const val PROC_THREAD_ATTRIBUTE_MITIGATION_POLICY = 0x00020007L

    /**
     * Policies Windows enforces on the program whatever its code does: no extension points (hooks that load a DLL into a program), no DLL from a network share or from a low
     * integrity folder. Strict handle checks are turned OFF on purpose: an AppContainer program has them on, and with them on the Java runtime dies (EXCEPTION_INVALID_HANDLE)
     * when one thread closes a socket another is polling, which OkHttp's connection racing does all the time; the isolation does not depend on them. A start without
     * these policies is made when this Windows does not know one of them.
     */
    private const val MITIGATIONS = (1L shl 32) or (1L shl 52) or (1L shl 56) or (2L shl 24)

    const val CAPABILITY_INTERNET_CLIENT = "S-1-15-3-1"

    /** An AppContainer of this name is created once for the user (a Windows profile, in the registry) and found again by its name */
    class Container(val name: String, val sid: Pointer, val sidText: String)

    private val containers = HashMap<String, Container>()

    @Synchronized
    fun container(name: String, capabilities: List<String> = listOf(CAPABILITY_INTERNET_CLIENT)): Container {
        containers[name]?.let { return it }
        val capSids = capabilities.map { text ->
            val ref = PointerByReference()
            if (!adv.ConvertStringSidToSidW(WString(text), ref)) throw IOException("bad capability $text")
            ref.value
        }
        val array = Memory(16L * capSids.size.coerceAtLeast(1)).also { it.clear() }
        capSids.forEachIndexed { i, sid -> array.setPointer(16L * i, sid); array.setInt(16L * i + 8, 4 /* SE_GROUP_ENABLED */) }
        val sid = PointerByReference()
        var hr = userenv.CreateAppContainerProfile(WString(name), WString("CloudStream extensions"), WString("Runs the extensions in a sandbox"), array, capSids.size, sid)
        if (hr != 0) hr = userenv.DeriveAppContainerSidFromAppContainerName(WString(name), sid)
        if (hr != 0) throw IOException("AppContainer profile failed: 0x" + Integer.toHexString(hr))
        val text = PointerByReference()
        adv.ConvertSidToStringSidW(sid.value, text)
        return Container(name, sid.value, text.value.getWideString(0)).also { containers[name] = it }
    }

    /** Removes the Windows profile of the sandbox (what "Erase all data" is for: nothing of the app is left on the PC); a profile that does not exist is no error */
    @Synchronized
    fun deleteProfile(name: String): Boolean {
        containers.remove(name)
        return runCatching { userenv.DeleteAppContainerProfile(WString(name)) == 0 }.getOrDefault(false)
    }

    private const val SE_FILE_OBJECT = 1
    private const val DACL_SECURITY_INFORMATION = 0x4
    private const val UNPROTECTED_DACL_SECURITY_INFORMATION = 0x20000000
    private const val READ_EXECUTE = 0x1200A9 // FILE_GENERIC_READ | FILE_GENERIC_EXECUTE
    private const val MODIFY = 0x1301BF // read, write, execute, delete

    /**
     * Whether [container] can read ([write] false) or change [path] now: an allow entry for it in the file's access list that holds the right it needs. A folder that was
     * replaced (an update, a new build) has none, however many times it was granted before: what is remembered is not what Windows enforces.
     */
    fun hasAccess(container: Container, path: File, write: Boolean): Boolean {
        if (!path.exists()) return false
        val full = path.absolutePath
        val name = WString(if (full.length > 240 && !full.startsWith("\\\\")) "\\?\\" + full else full)
        val dacl = PointerByReference()
        val descriptor = PointerByReference()
        if (adv.GetNamedSecurityInfoW(name, SE_FILE_OBJECT, DACL_SECURITY_INFORMATION, null, null, dacl, null, descriptor) != 0) return false
        try {
            val acl = dacl.value ?: return true // no access list at all: everyone may
            val needed = if (write) MODIFY else READ_EXECUTE
            var offset = 8L
            val count = acl.getShort(4).toInt() and 0xFFFF
            repeat(count) {
                val type = acl.getByte(offset).toInt()
                val size = acl.getShort(offset + 2).toInt() and 0xFFFF
                if (size < 8) return false
                // ACCESS_ALLOWED_ACE: header (4), the rights (4), the SID
                if (type == 0 && (acl.getInt(offset + 4) and needed) == needed && adv.EqualSid(acl.share(offset + 8), container.sid)) return true
                offset += size
            }
            return false
        } finally {
            kernel.LocalFree(descriptor.value)
        }
    }

    /** Lets the container read ([write] false) or change a file or a folder (and what is in it). The user's own access stays as it is. */
    fun grant(container: Container, path: File, write: Boolean): Boolean {
        if (!path.exists()) return false
        // a path of more than 260 characters needs the long form for the security functions
        val full = path.absolutePath
        val name = WString(if (full.length > 240 && !full.startsWith("\\\\")) "\\\\?\\" + full else full)
        val oldDacl = PointerByReference()
        val descriptor = PointerByReference()
        if (adv.GetNamedSecurityInfoW(name, SE_FILE_OBJECT, DACL_SECURITY_INFORMATION, null, null, oldDacl, null, descriptor) != 0) return false
        try {
            // EXPLICIT_ACCESS_W: permissions, mode (1 = grant), inheritance (3 = files and folders below), then the TRUSTEE (form 0 = a SID)
            val entry = Memory(48).also { it.clear() }
            entry.setInt(0, if (write) MODIFY else READ_EXECUTE)
            entry.setInt(4, 1)
            entry.setInt(8, if (path.isDirectory) 3 else 0)
            entry.setInt(28, 0)
            entry.setInt(32, 0)
            entry.setPointer(40, container.sid)
            val newDacl = PointerByReference()
            if (adv.SetEntriesInAclW(1, entry, oldDacl.value, newDacl) != 0) return false
            try {
                return adv.SetNamedSecurityInfoW(name, SE_FILE_OBJECT, DACL_SECURITY_INFORMATION or UNPROTECTED_DACL_SECURITY_INFORMATION, null, null, newDacl.value, null) == 0
            } finally {
                kernel.LocalFree(newDacl.value)
            }
        } finally {
            kernel.LocalFree(descriptor.value)
        }
    }

    /** A program of the container: its pipes, the job it is held in, and its end */
    class Isolated(private val process: Pointer, private val job: Pointer?, val pid: Int, val stdin: OutputStream, val stdout: InputStream, val stderr: InputStream) {
        private val closed = AtomicBoolean()

        fun isAlive(): Boolean {
            val code = IntByReference()
            return kernel.GetExitCodeProcess(process, code) && code.value == 259
        }

        fun exitCode(): Int? = IntByReference().let { c -> if (kernel.GetExitCodeProcess(process, c) && c.value != 259) c.value else null }

        fun waitFor(millis: Long): Boolean = kernel.WaitForSingleObject(process, millis.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) == 0

        /** Ends the program (and closes the pipes); the job's handle goes with it */
        fun destroy() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { kernel.TerminateProcess(process, 1) }
            runCatching { stdin.close() }
            runCatching { stdout.close() }
            runCatching { stderr.close() }
            runCatching { kernel.CloseHandle(process) }
            job?.let { runCatching { kernel.CloseHandle(it) } }
        }
    }

    private class PipeIn(private val handle: Pointer) : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val buffer = if (off == 0) b else ByteArray(len)
            val n = IntByReference()
            if (!kernel.ReadFile(handle, buffer, minOf(len, buffer.size), n, null) || n.value == 0) return -1
            if (buffer !== b) System.arraycopy(buffer, 0, b, off, n.value)
            return n.value
        }

        override fun close() { kernel.CloseHandle(handle) }
    }

    private class PipeOut(private val handle: Pointer) : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var done = 0
            while (done < len) {
                val chunk = b.copyOfRange(off + done, off + len)
                val n = IntByReference()
                if (!kernel.WriteFile(handle, chunk, chunk.size, n, null) || n.value <= 0) throw IOException("the pipe to the sandbox is closed")
                done += n.value
            }
        }

        override fun close() { kernel.CloseHandle(handle) }
    }

    /**
     * Starts [commandLine] in [container]. [environment] is the whole environment of the program (the app's own is not passed on: it may hold
     * keys and tokens). [memoryLimitBytes] is the job's limit for the program.
     */
    fun launch(container: Container, commandLine: String, workingDir: File, environment: Map<String, String>, memoryLimitBytes: Long, policies: Boolean = true): Isolated {
        // the pipes: the program's ends are inheritable, ours are not
        val sa = Memory(24).also { it.clear(); it.setInt(0, 24); it.setInt(16, 1) }
        val inRead = PointerByReference(); val inWrite = PointerByReference()
        val outRead = PointerByReference(); val outWrite = PointerByReference()
        val errRead = PointerByReference(); val errWrite = PointerByReference()
        check(kernel.CreatePipe(inRead, inWrite, sa, 0) && kernel.CreatePipe(outRead, outWrite, sa, 0) && kernel.CreatePipe(errRead, errWrite, sa, 0)) { "pipes: error ${Native.getLastError()}" }
        for (mine in listOf(inWrite.value, outRead.value, errRead.value)) kernel.SetHandleInformation(mine, 1, 0)

        // attributes: the container (with its capabilities) and the only handles the program inherits
        val capability = PointerByReference().also { adv.ConvertStringSidToSidW(WString(CAPABILITY_INTERNET_CLIENT), it) }
        val capabilities = Memory(16).also { it.clear(); it.setPointer(0, capability.value); it.setInt(8, 4) }
        val security = Memory(24).also { it.clear(); it.setPointer(0, container.sid); it.setPointer(8, capabilities); it.setInt(16, 1) }
        val handles = Memory(24).also { it.setPointer(0, inRead.value); it.setPointer(8, outWrite.value); it.setPointer(16, errWrite.value) }
        val command = Memory(((commandLine.length + 1) * 2).toLong()).also { it.setWideString(0, commandLine) }
        // Windows wants the block sorted by name, ignoring case
        val env = environment.toSortedMap(String.CASE_INSENSITIVE_ORDER).entries.joinToString("") { "${it.key}=${it.value}\u0000" } + "\u0000"
        val envMemory = Memory(((env.length + 1) * 2).toLong()).also { m -> m.clear(); env.forEachIndexed { i, ch -> m.setChar(i * 2L, ch) } }
        val info = Memory(24).also { it.clear() }
        val mitigations = Memory(8).also { it.setLong(0, MITIGATIONS) }

        // with the extra policies first; a Windows that does not know one of them refuses the start, then it is made without
        var created = false
        var error = 0
        for (withPolicies in if (!policies || System.getProperty("cloudstream.processpolicies") == "off") listOf(false) else listOf(true, false)) {
            val count = if (withPolicies) 3 else 2
            val size = LongByReference()
            kernel.InitializeProcThreadAttributeList(null, count, 0, size)
            val list = Memory(size.value)
            check(kernel.InitializeProcThreadAttributeList(list, count, 0, size)) { "attribute list: error ${Native.getLastError()}" }
            check(kernel.UpdateProcThreadAttribute(list, 0, PROC_THREAD_ATTRIBUTE_SECURITY_CAPABILITIES, security, 24, null, null)) { "container attribute: error ${Native.getLastError()}" }
            check(kernel.UpdateProcThreadAttribute(list, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST, handles, 24, null, null)) { "handle list: error ${Native.getLastError()}" }
            if (withPolicies) {
                check(kernel.UpdateProcThreadAttribute(list, 0, PROC_THREAD_ATTRIBUTE_MITIGATION_POLICY, mitigations, 8, null, null)) { "mitigation policy: error ${Native.getLastError()}" }
            }
            val startup = Memory(112).also { it.clear(); it.setInt(0, 112); it.setInt(60, STARTF_USESTDHANDLES) }
            startup.setPointer(80, inRead.value); startup.setPointer(88, outWrite.value); startup.setPointer(96, errWrite.value); startup.setPointer(104, list)
            created = kernel.CreateProcessW(
                null, command, null, null, true,
                EXTENDED_STARTUPINFO_PRESENT or CREATE_NO_WINDOW or CREATE_SUSPENDED or CREATE_UNICODE_ENVIRONMENT,
                envMemory, WString(workingDir.absolutePath), startup, info,
            )
            error = if (created) 0 else Native.getLastError()
            kernel.DeleteProcThreadAttributeList(list)
            if (created) { if (!withPolicies) Log.w(TAG, "started without the process policies (this Windows refused them)"); break }
            if (withPolicies) Log.w(TAG, "the start with process policies failed (Windows error $error): trying without")
        }
        for (theirs in listOf(inRead.value, outWrite.value, errWrite.value)) kernel.CloseHandle(theirs)
        if (!created) {
            for (mine in listOf(inWrite.value, outRead.value, errRead.value)) kernel.CloseHandle(mine)
            throw IOException("the sandbox program did not start (Windows error $error)")
        }
        val process = info.getPointer(0)
        val thread = info.getPointer(8)
        val pid = info.getInt(16)

        val job = jobFor(memoryLimitBytes)
        if (job != null && !kernel.AssignProcessToJobObject(job, process)) Log.w(TAG, "the job did not take the program (error ${Native.getLastError()})")
        kernel.ResumeThread(thread)
        kernel.CloseHandle(thread)
        Log.i(TAG, "started process $pid in container ${container.name}")
        return Isolated(process, job, pid, PipeOut(inWrite.value), PipeIn(outRead.value), PipeIn(errRead.value))
    }

    /** A job that ends its program with this app, lets it start no other program, holds it to a memory limit and closes the UI (clipboard, desktop, ...) */
    private fun jobFor(memoryLimitBytes: Long): Pointer? {
        val job = kernel.CreateJobObjectW(null, null) ?: return null
        val limits = Memory(144).also { it.clear() }
        // KILL_ON_JOB_CLOSE (0x2000) | ACTIVE_PROCESS (0x8) | PROCESS_MEMORY (0x100)
        limits.setInt(16, 0x2000 or 0x8 or 0x100)
        limits.setInt(40, 1)
        limits.setLong(112, memoryLimitBytes)
        val ui = Memory(4).also { it.setInt(0, 0xFF) }
        val ok = kernel.SetInformationJobObject(job, 9, limits, 144) && kernel.SetInformationJobObject(job, 4, ui, 4)
        if (!ok) Log.w(TAG, "job limits not applied (error ${Native.getLastError()})")
        return job
    }
}
