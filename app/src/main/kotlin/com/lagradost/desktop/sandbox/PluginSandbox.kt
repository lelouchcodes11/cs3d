package com.lagradost.desktop.sandbox

import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.desktop.runtime.AndroidRuntime
import dalvik.system.BaseDexClassLoader
import java.io.File
import java.security.AllPermission
import java.security.Permission
import java.security.PermissionCollection
import java.security.Policy
import java.security.ProtectionDomain
import java.util.concurrent.ConcurrentHashMap

/**
 * The extension sandbox. Extensions are code that anybody can publish and that runs inside the app with the rights of the user. This stops that code
 * (and only that code) from doing the things malware does: running programs, reading the user's files, changing files outside the data folder, ending
 * the app or switching the sandbox off, loading native libraries, using the system's internals (see [SandboxRules] for the whole list).
 *
 * How: a Java SecurityManager that does one thing. A permission check on the deny-list is looked at with the call stack: if code of an extension (a class
 * of a [BaseDexClassLoader]) is on it, the action is refused with a SecurityException that the extension sees (and the user is told); anything else, the
 * app's own work included, passes at once. The app's code that does something on behalf of an extension on purpose (opening a link in the browser, copying
 * text) runs in [SandboxGate.run], which tells the sandbox that the thread is the app's for that moment.
 *
 * What it is not: a barrier against code written to defeat it, and not a virtual machine. It makes the usual attacks fail and shows what an extension
 * tried to do. Settings > General > Security switches it off (the extension then has the rights of the app again).
 *
 * `-Dcloudstream.sandbox=audit` only logs what would be refused (to find out what an extension needs), `=off` does not install it. The JVM has to be
 * started with `-Djava.security.manager=allow` (the launcher and the Gradle tasks do); without it the sandbox is [State.Unavailable].
 */
object PluginSandbox {
    private const val TAG = "PluginSandbox"
    private const val PREF_KEY = "desktop_extension_sandbox"

    enum class State { Off, Active, Audit, Unavailable }

    @Volatile
    var state = State.Off
        private set

    private var decided = false

    /** One thing an extension tried that is on the deny-list */
    class Event(val time: Long, val plugin: String, val what: String, val blocked: Boolean)

    private val recent = ArrayDeque<Event>()
    private val told = ConcurrentHashMap.newKeySet<String>()
    private val blockedCount = java.util.concurrent.atomic.AtomicInteger()

    /** The latest things that were refused (or would have been, in audit mode), newest last */
    fun events(): List<Event> = synchronized(recent) { recent.toList() }

    fun clearEvents() { synchronized(recent) { recent.clear() }; told.clear() }

    // ---- trusted extensions: the sandbox leaves these alone (the user's choice, from the list of what was blocked) --------------------------------------

    private const val TRUST_KEY = "desktop_extension_sandbox_trusted"
    private val trustedNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun loadTrusted() {
        runCatching {
            PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).getString(TRUST_KEY, "")?.split('\n')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.let { trustedNames.addAll(it) }
        }
    }

    /** The extensions the user trusts, by name */
    fun trustedList(): List<String> = trustedNames.sorted()

    fun isTrusted(name: String): Boolean = name.lowercase() in trustedNames

    fun trust(name: String) { trustedNames += name.lowercase(); saveTrusted() }

    fun untrust(name: String) { trustedNames -= name.lowercase(); saveTrusted() }

    private fun saveTrusted() {
        runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).edit().putString(TRUST_KEY, trustedNames.sorted().joinToString("\n")).apply() }
    }

    fun summary(): String = when (state) {
        State.Off -> "off"
        State.Active -> "on, ${blockedCount.get()} actions refused in this run"
        State.Audit -> "audit only, ${blockedCount.get()} actions would have been refused"
        State.Unavailable -> "not available (the JVM was not started with -Djava.security.manager=allow)"
    }

    /** One line for Settings > General > Security (a fixed set of texts, so each can be translated) */
    fun statusLine(): String = when (state) {
        State.Off -> "The sandbox is off."
        State.Active -> "The sandbox is on."
        State.Audit -> "Audit only: nothing is blocked, what would be is listed."
        State.Unavailable -> "Not available: the app was not started with the option the sandbox needs."
    }

    /** The setting (Settings > General > Security); on unless the user turned it off */
    fun enabledSetting(): Boolean = runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).getBoolean(PREF_KEY, true) }.getOrDefault(true)

    /** Turns the sandbox on or off for this run and for the next ones */
    fun setEnabled(on: Boolean) {
        runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).edit().putBoolean(PREF_KEY, on).apply() }
        synchronized(this) {
            if (on) { decided = false; ensureInstalled() }
            else {
                decided = true
                if (installed != null) {
                    runCatching { System.setSecurityManager(null) }.onFailure { Log.w(TAG, "could not switch the sandbox off: ${it.message}") }
                    installed = null
                }
                state = State.Off
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------

    private var installed: Manager? = null

    /** Called before the first extension is loaded (PluginManager); a later call does nothing */
    fun ensureInstalled() {
        if (decided) return
        synchronized(this) {
            if (decided) return
            decided = true
            // the extension sandbox process is itself the sandbox (a Windows AppContainer): no Java security manager in it
            if (System.getProperty("cloudstream.worker") != null) { state = State.Off; return }
            val property = System.getProperty("cloudstream.sandbox")
            if (property == "off" || (property == null && !enabledSetting())) { state = State.Off; return }
            val audit = property == "audit"
            loadTrusted()
            runCatching {
                // a permissive policy: nothing in the JDK that asks the AccessController directly is refused for the app's own code
                Policy.setPolicy(object : Policy() {
                    override fun getPermissions(codesource: java.security.CodeSource?): PermissionCollection = all()
                    override fun getPermissions(domain: ProtectionDomain?): PermissionCollection = all()
                    override fun implies(domain: ProtectionDomain?, permission: Permission?): Boolean = true
                    private fun all() = AllPermission().newPermissionCollection().also { it.add(AllPermission()) }
                })
                val roots = computeRoots()
                val manager = Manager(roots, audit)
                warm(manager, roots)
                System.setSecurityManager(manager)
                installed = manager
                state = if (audit) State.Audit else State.Active
                Log.i(TAG, "the extension sandbox is ${state.name.lowercase()}")
            }.onFailure {
                state = State.Unavailable
                Log.w(TAG, "the extension sandbox could not be installed: $it")
            }
        }
    }

    /**
     * Loads every class the checks use while no check is running. A class that is loaded for the first time inside a check asks the same SecurityManager
     * (checkPackageAccess) and would be refused for an extension on the stack: the classes of this package are on the refused list on purpose.
     */
    private fun warm(manager: Manager, roots: SandboxRules.Roots) {
        // Skia extracts and loads its native library the first time something draws or decodes a picture, which an extension can cause (a poster, a QR code)
        runCatching { org.jetbrains.skia.impl.Library.staticLoad() }
        SandboxGate.isTrusted()
        inspect()
        Event(0L, "", "", false)
        for (p in listOf<Permission>(
            java.io.FilePermission("warm", "read"), java.io.FilePermission("warm", "execute"), RuntimePermission("exitVM.0"), RuntimePermission("accessDeclaredMembers"),
            RuntimePermission("getClassLoader"), java.awt.AWTPermission("createRobot"), java.net.NetPermission("setProxySelector"), java.security.SecurityPermission("setPolicy"),
            java.nio.file.LinkPermission("symbolic"), java.util.PropertyPermission("warm", "read"),
        )) {
            SandboxRules.violation(p, roots)
            manager.checkPermission(p)
        }
        manager.checkPackageAccess("com.sun.jna")
    }

    /** The folders extensions may read and write: the data folder and the temporary folder; read only: the app, the Java runtime, Windows */
    private fun computeRoots(): SandboxRules.Roots {
        fun norm(f: String?) = f?.let { SandboxRules.normalize(it) }
        val data = norm(runCatching { AndroidRuntime.dataDir.absolutePath }.getOrNull())
        val tmp = norm(System.getProperty("java.io.tmpdir"))
        val write = listOfNotNull(data, tmp)
        val read = LinkedHashSet<String>(write)
        listOf(System.getProperty("java.home"), System.getProperty("user.dir"), System.getenv("SystemRoot"), System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("ProgramData"))
            .forEach { norm(it)?.let(read::add) }
        // the app's own folder: where its jars are (the classpath entries' folders; a packaged app has them all in one)
        System.getProperty("java.class.path").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.forEach { entry ->
            norm(File(entry).absoluteFile.parent)?.let(read::add)
        }
        System.getProperty("jpackage.app-path")?.let { norm(File(it).absoluteFile.parent)?.let(read::add) }
        // folders the app itself keeps files in and that code of the app writes while an extension is further down the stack: Skia's extracted library, the safety mirror of the settings
        val shared = listOfNotNull(
            System.getProperty("user.home")?.let { File(it, ".skiko").path },
            runCatching { com.lagradost.desktop.platform.DataKeep.folder?.path }.getOrNull(),
        ).mapNotNull(::norm)
        return SandboxRules.Roots((read + shared).distinct(), (write + shared).distinct())
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------

    /** What the call stack says: the extension on it (null: none) and whether the extension's own code made the call (not a library for it) */
    internal class Inspection(val loader: BaseDexClassLoader?, val direct: Boolean)

    private val walker: StackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

    private fun isInternal(className: String): Boolean =
        className.startsWith("java.") || className.startsWith("jdk.") || className.startsWith("sun.") || className.startsWith("com.lagradost.desktop.sandbox.")

    internal fun inspect(): Inspection = walker.walk { frames ->
        var direct: Boolean? = null
        var found: BaseDexClassLoader? = null
        val it = frames.iterator()
        while (it.hasNext()) {
            val c = it.next().declaringClass
            val loader = c.classLoader
            if (direct == null && !isInternal(c.name)) direct = loader is BaseDexClassLoader
            if (loader is BaseDexClassLoader) {
                found = loader
                if (direct != null) break
            }
        }
        Inspection(found, direct == true)
    }

    /** The name of the extension whose code is running (on this thread's stack), or null: for the app's own gates (a link that an extension opens) */
    fun extensionOnStack(): String? = if (state == State.Off || state == State.Unavailable) null else inspect().loader?.let(::nameOf)?.takeUnless { isTrusted(it) }

    /** "StreamPlay.798583540.cs3" is the extension StreamPlay */
    private fun nameOf(loader: BaseDexClassLoader): String =
        loader.dexPath?.let { File(it.substringBefore(File.pathSeparator)).nameWithoutExtension.replace(Regex("[.]-?[0-9]+$"), "") }?.takeIf { it.isNotBlank() } ?: "an extension"

    /** The first frames of the stack that are not the JDK's or the sandbox's: where an extension was when it was stopped (for the log) */
    private fun whereAmI(): String = runCatching {
        walker.walk { frames -> frames.filter { !isInternal(it.className) }.limit(4).map { it.className.substringAfterLast('.') + "." + it.methodName }.toList() }.joinToString(" < ")
    }.getOrDefault("")

    private fun record(plugin: String, what: String, blocked: Boolean) {
        blockedCount.incrementAndGet()
        val now = System.currentTimeMillis()
        val duplicate = synchronized(recent) {
            val last = recent.lastOrNull { it.plugin == plugin && it.what == what }
            val dup = last != null && now - last.time < 5_000
            if (!dup) { recent.addLast(Event(now, plugin, what, blocked)); while (recent.size > 100) recent.removeFirst() }
            dup
        }
        if (duplicate) return
        Log.w(TAG, "${if (blocked) "refused" else "would refuse"}: $plugin tried to $what  [${whereAmI()}]")
        if (blocked && told.add(plugin)) {
            runCatching { com.lagradost.desktop.ui.Toasts.show("$plugin was stopped from trying to $what. See Settings > Network and security.", true) }
        }
    }

    /** Refuses an action of an extension from outside the permission checks: the app's gates (a link or a file that an extension asked to open) */
    fun refuse(plugin: String, what: String): Nothing {
        record(plugin, what, blocked = state == State.Active)
        throw SecurityException("CloudStream's extension sandbox refused: $plugin tried to $what")
    }

    private class Manager(private val roots: SandboxRules.Roots, private val audit: Boolean) : SecurityManager() {
        private val busy = ThreadLocal.withInitial { BooleanArray(1) }

        override fun checkPermission(perm: Permission) {
            // the check itself reads properties and paths, and those are checked again
            val flag = busy.get()
            if (flag[0]) return
            flag[0] = true
            try {
                val verdict = SandboxRules.violation(perm, roots)
                if (verdict === SandboxRules.Verdict.Fine) return
                if (SandboxGate.isTrusted()) return
                val seen = inspect()
                val loader = seen.loader ?: return
                if (isTrusted(nameOf(loader))) return
                val what = when (verdict) {
                    is SandboxRules.Verdict.Denied -> verdict.what
                    is SandboxRules.Verdict.DeniedIfDirect -> if (seen.direct) verdict.what else return
                    else -> return
                }
                record(nameOf(loader), what, blocked = !audit)
                if (!audit) throw SecurityException("CloudStream's extension sandbox refused: ${nameOf(loader)} tried to $what")
            } finally {
                flag[0] = false
            }
        }

        override fun checkPermission(perm: Permission, context: Any?) = checkPermission(perm)

        /**
         * Loading a class of the system's internals or of JNA (the way to Windows' own functions) from an extension is refused. This runs for every class the app
         * loads, and loading one of OUR classes in here asks the same question again: the flag goes up before anything of ours is touched, and [warm] has
         * loaded the classes this needs before the manager is installed.
         */
        override fun checkPackageAccess(pkg: String) {
            val flag = busy.get()
            if (flag[0]) return
            flag[0] = true
            try {
                if (!SandboxRules.isBlockedPackage(pkg)) return
                if (SandboxGate.isTrusted()) return
                // only the extension's own code: the app's libraries use JNA and Unsafe with an extension further down the stack
                val seen = inspect()
                val loader = seen.loader ?: return
                if (!seen.direct || isTrusted(nameOf(loader))) return
                record(nameOf(loader), "use the internals of the system ($pkg)", blocked = !audit)
                if (!audit) throw SecurityException("CloudStream's extension sandbox refused: ${nameOf(loader)} tried to use $pkg")
            } finally {
                flag[0] = false
            }
        }
    }
}
