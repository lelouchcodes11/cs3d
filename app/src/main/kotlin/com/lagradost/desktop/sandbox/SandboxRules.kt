package com.lagradost.desktop.sandbox

import java.awt.AWTPermission
import java.io.FilePermission
import java.net.NetPermission
import java.nio.file.LinkPermission
import java.nio.file.Paths
import java.security.Permission
import java.security.SecurityPermission

/**
 * What the extension sandbox stops an extension from doing. Pure rules, no state: [violation] says in words what a permission check is, or null when
 * it is fine for an extension (reading its own files, the network, reflection on its own classes, threads ... everything not listed here).
 *
 * The rules are a deny-list on purpose. Extensions are Android code and use the whole JDK, a long list of permissions that would have to be granted
 * one by one breaks them; what is listed is what an extension has no business doing and what malware does:
 *
 *  - run a program (also through the shell's "open" of a file),
 *  - read files outside the app's own folders (the profile: documents, browser data, keys) and change or delete files outside the data folder and
 *    the temporary folder (a startup entry, a document),
 *  - end the app or turn the sandbox off, load native libraries, create a class loader of its own, use the JDK's and JNA's internals (system calls),
 *  - take screenshots, watch the keyboard or read the clipboard, change the proxy, cookie or certificate handling of the whole app, make links.
 */
internal object SandboxRules {
    /** Folders as [normalize] writes them: lower case, backslashes, no trailing separator */
    class Roots(val read: List<String>, val write: List<String>)

    /** Packages an extension may not load a class from (its own code is loaded by its own loader; these are the JDK's and the app's way to the system) */
    val blockedPackages = listOf("sun.misc", "com.sun.jna", "com.lagradost.desktop.sandbox")

    fun isBlockedPackage(pkg: String): Boolean = blockedPackages.any { pkg == it || pkg.startsWith("$it.") }

    /** The words for a permission an extension may not have, or null; [directCaller] tells (lazily) whether the extension's own code made the call */
    sealed interface Verdict {
        object Fine : Verdict
        class Denied(val what: String) : Verdict

        /** Denied when the extension's own code made the call (reflection on the JDK's classes), fine when a library did it for the extension's data */
        class DeniedIfDirect(val what: String) : Verdict
    }

    fun violation(p: Permission, paths: Roots): Verdict {
        return when (p) {
            is FilePermission -> file(p, paths)?.let { Verdict.Denied(it) } ?: Verdict.Fine
            is RuntimePermission -> runtime(p)
            is AWTPermission -> when (p.name) {
                "createRobot", "readDisplayPixels" -> Verdict.Denied("take screenshots")
                "watchMousePointer", "listenToAllAWTEvents", "accessEventQueue" -> Verdict.Denied("watch the keyboard and the mouse")
                "accessClipboard" -> Verdict.Denied("use the clipboard")
                else -> Verdict.Fine
            }
            is NetPermission -> when (p.name) {
                "setDefaultAuthenticator", "setProxySelector", "setCookieHandler", "setResponseCache", "specifyStreamHandler" -> Verdict.Denied("change how the app uses the network")
                else -> Verdict.Fine
            }
            is SecurityPermission -> {
                val n = p.name
                if (n.startsWith("setPolicy") || n.startsWith("createPolicy") || n.startsWith("getPolicy") || n.startsWith("setProperty") ||
                    n == "createAccessControlContext" || n == "getDomainCombiner" || n.startsWith("removeProvider") || n.startsWith("clearProviderProperties")
                ) Verdict.Denied("change the security settings") else Verdict.Fine
            }
            is LinkPermission -> Verdict.Denied("create a link to a file")
            else -> Verdict.Fine
        }
    }

    private fun runtime(p: RuntimePermission): Verdict {
        val n = p.name
        return when {
            n.startsWith("exitVM") -> Verdict.Denied("end the app")
            n == "setSecurityManager" || n == "createSecurityManager" -> Verdict.Denied("turn the sandbox off")
            // the next five are refused when the extension's own code asks: the app's libraries do these things (Skia loads its library the first time it draws,
            // Rhino makes a class loader for the script it compiles, Jackson looks inside the extension's classes) with an extension further down the stack
            n.startsWith("loadLibrary") -> Verdict.DeniedIfDirect("load a native library")
            n == "manageProcess" -> Verdict.Denied("control other programs")
            n == "createClassLoader" -> Verdict.DeniedIfDirect("load code of its own")
            n == "accessDeclaredMembers" -> Verdict.DeniedIfDirect("look inside the classes of the system")
            n.startsWith("accessClassInPackage.") && isBlockedPackage(n.removePrefix("accessClassInPackage.")) -> Verdict.DeniedIfDirect("use the internals of the system")
            else -> Verdict.Fine
        }
    }

    private fun file(p: FilePermission, paths: Roots): String? {
        val actions = p.actions
        val name = p.name
        if ("execute" in actions) return "run the program ${short(name)}"
        val write = "write" in actions || "delete" in actions
        val read = "read" in actions
        if (!write && !read) return null
        val path = normalize(name)
        if (write) {
            if (path == null || !within(path, paths.write)) return (if ("write" !in actions) "delete the file " else "change the file ") + short(name)
        } else if (path == null || !within(path, paths.read)) return "read the file ${short(name)}"
        return null
    }

    /** A path as the rules compare it: absolute, lower case, backslashes, no wildcard tail; null for "all files" and for a path that is not one */
    fun normalize(name: String): String? {
        if (name == "<<ALL FILES>>") return null
        var n = name
        if (n.endsWith("\\-") || n.endsWith("/-") || n.endsWith("\\*") || n.endsWith("/*")) n = n.dropLast(2)
        return runCatching { Paths.get(n).toAbsolutePath().normalize().toString() }.getOrNull()?.lowercase()?.replace('/', '\\')?.trimEnd('\\')?.takeIf { it.isNotEmpty() }
    }

    fun within(path: String, roots: List<String>): Boolean = roots.any { path == it || path.startsWith("$it\\") }

    private fun short(name: String): String = if (name.length <= 70) name else "…" + name.takeLast(68)
}
