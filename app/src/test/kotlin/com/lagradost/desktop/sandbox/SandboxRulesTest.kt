package com.lagradost.desktop.sandbox

import java.awt.AWTPermission
import java.io.FilePermission
import java.net.NetPermission
import java.nio.file.LinkPermission
import java.security.SecurityPermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandboxRulesTest {
    private val data = "c:\\users\\me\\appdata\\roaming\\cloudstream"
    private val roots = SandboxRules.Roots(
        read = listOf(data, "c:\\temp", "c:\\program files\\cloudstream", "c:\\windows"),
        write = listOf(data, "c:\\temp"),
    )

    private fun what(p: java.security.Permission): String? = when (val v = SandboxRules.violation(p, roots)) {
        is SandboxRules.Verdict.Denied -> v.what
        is SandboxRules.Verdict.DeniedIfDirect -> v.what
        else -> null
    }
    private fun fine(p: java.security.Permission) = assertTrue(SandboxRules.violation(p, roots) === SandboxRules.Verdict.Fine, "should be allowed: $p")

    @Test
    fun runningAProgramIsRefusedWhereverItIs() {
        assertTrue(what(FilePermission("c:\\windows\\system32\\cmd.exe", "execute"))!!.startsWith("run the program"))
        assertTrue(what(FilePermission("<<ALL FILES>>", "execute"))!!.startsWith("run the program"))
        assertTrue(what(FilePermission("c:\\users\\me\\appdata\\roaming\\cloudstream\\evil.exe", "execute"))!!.startsWith("run the program"))
    }

    @Test
    fun theDataFolderTheTemporaryFolderAndTheAppCanBeRead() {
        fine(FilePermission("$data\\code_cache\\dex-jvm\\x.jar", "read"))
        fine(FilePermission("c:\\temp\\a.tmp", "read,write"))
        fine(FilePermission("C:\\Program Files\\CloudStream\\app\\app.jar", "read"))
        fine(FilePermission("C:\\Windows\\Fonts\\arial.ttf", "read"))
        // the case of the path does not matter on Windows, and neither do forward slashes or ..
        fine(FilePermission("C:/Users/Me/AppData/Roaming/CloudStream/files/../shared_prefs/a.xml", "read"))
    }

    @Test
    fun theUsersOwnFilesAreNotReadable() {
        assertEquals("read the file c:\\users\\me\\documents\\passwords.txt", what(FilePermission("c:\\users\\me\\documents\\passwords.txt", "read")))
        assertTrue(what(FilePermission("c:\\users\\me\\appdata\\local\\google\\chrome\\user data\\default\\login data", "read"))!!.startsWith("read the file "))
        assertTrue(what(FilePermission("<<ALL FILES>>", "read"))!!.startsWith("read the file "))
    }

    @Test
    fun filesAreChangedOnlyInTheDataFolderAndTheTemporaryFolder() {
        fine(FilePermission("$data\\files\\a.json", "write"))
        fine(FilePermission("$data\\files\\a.json", "delete"))
        assertTrue(what(FilePermission("c:\\users\\me\\appdata\\roaming\\microsoft\\windows\\start menu\\programs\\startup\\run.bat", "write"))!!.startsWith("change the file "))
        assertTrue(what(FilePermission("c:\\users\\me\\documents\\thesis.docx", "delete"))!!.startsWith("delete the file "))
        // a program folder is readable but not writable
        assertTrue(what(FilePermission("c:\\program files\\cloudstream\\app\\app.jar", "write"))!!.startsWith("change the file "))
        // a sibling folder whose name starts like the data folder is not the data folder
        assertTrue(what(FilePermission("$data-evil\\x", "write")) != null)
    }

    @Test
    fun endingTheAppAndTurningTheSandboxOffAreRefused() {
        assertEquals("end the app", what(RuntimePermission("exitVM.0")))
        assertEquals("turn the sandbox off", what(RuntimePermission("setSecurityManager")))
        assertEquals("load a native library", what(RuntimePermission("loadLibrary.evil")))
        assertEquals("control other programs", what(RuntimePermission("manageProcess")))
        assertEquals("use the internals of the system", what(RuntimePermission("accessClassInPackage.sun.misc")))
        assertEquals("use the internals of the system", what(RuntimePermission("accessClassInPackage.com.sun.jna.platform.win32")))
    }

    @Test
    fun whatAnExtensionNeedsIsNotOnTheList() {
        fine(RuntimePermission("getClassLoader"))
        fine(RuntimePermission("setContextClassLoader"))
        fine(RuntimePermission("modifyThread"))
        fine(RuntimePermission("shutdownHooks"))
        fine(RuntimePermission("getenv.PATH"))
        fine(RuntimePermission("accessClassInPackage.org.mozilla.javascript"))
        fine(java.lang.reflect.ReflectPermission("suppressAccessChecks"))
        fine(java.net.SocketPermission("example.com:443", "connect,resolve"))
        fine(java.util.PropertyPermission("user.dir", "read"))
        fine(SecurityPermission("insertProvider.BC"))
        fine(NetPermission("getProxySelector"))
        fine(AWTPermission("showWindowWithoutWarningBanner"))
    }

    @Test
    fun reflectionIntoTheSystemAndLoadingCodeAreRefusedOnlyWhenTheExtensionDoesItItself() {
        val inside = SandboxRules.violation(RuntimePermission("accessDeclaredMembers"), roots)
        assertTrue(inside is SandboxRules.Verdict.DeniedIfDirect)
        assertTrue(SandboxRules.violation(RuntimePermission("createClassLoader"), roots) is SandboxRules.Verdict.DeniedIfDirect)
        assertTrue(SandboxRules.violation(RuntimePermission("loadLibrary.evil"), roots) is SandboxRules.Verdict.DeniedIfDirect)
        assertTrue(SandboxRules.violation(RuntimePermission("accessClassInPackage.sun.misc"), roots) is SandboxRules.Verdict.DeniedIfDirect)
    }

    @Test
    fun spyingOnTheScreenTheKeyboardAndTheClipboardIsRefused() {
        assertEquals("take screenshots", what(AWTPermission("createRobot")))
        assertEquals("take screenshots", what(AWTPermission("readDisplayPixels")))
        assertEquals("watch the keyboard and the mouse", what(AWTPermission("listenToAllAWTEvents")))
        assertEquals("use the clipboard", what(AWTPermission("accessClipboard")))
    }

    @Test
    fun theNetworkSettingsOfTheWholeAppAndTheSecuritySettingsAreRefused() {
        assertEquals("change how the app uses the network", what(NetPermission("setProxySelector")))
        assertEquals("change how the app uses the network", what(NetPermission("setCookieHandler")))
        assertEquals("change the security settings", what(SecurityPermission("setPolicy")))
        assertEquals("change the security settings", what(SecurityPermission("setProperty.package.access")))
        assertEquals("create a link to a file", what(LinkPermission("symbolic")))
    }

    @Test
    fun blockedPackagesIncludeSubpackages() {
        assertTrue(SandboxRules.isBlockedPackage("sun.misc"))
        assertTrue(SandboxRules.isBlockedPackage("com.sun.jna"))
        assertTrue(SandboxRules.isBlockedPackage("com.sun.jna.platform.win32"))
        assertFalse(SandboxRules.isBlockedPackage("com.sun.jnabar"))
        assertFalse(SandboxRules.isBlockedPackage("okhttp3"))
        assertFalse(SandboxRules.isBlockedPackage("java.net"))
        assertFalse(SandboxRules.isBlockedPackage("jdk.internal.misc"))
    }

    @Test
    fun normalizeHandlesWildcardsAndOddInput() {
        assertEquals("c:\\temp", SandboxRules.normalize("C:\\Temp\\-"))
        assertEquals("c:\\temp\\a", SandboxRules.normalize("c:/temp/./b/../a"))
        assertNull(SandboxRules.normalize("<<ALL FILES>>"))
    }

    @Test
    fun onlyPassiveFilesCanBeOpenedForAnExtension() {
        assertTrue(SafeOpen.isPassive(java.io.File("c:\\x\\movie.MKV")))
        assertTrue(SafeOpen.isPassive(java.io.File("sub.srt")))
        assertFalse(SafeOpen.isPassive(java.io.File("setup.exe")))
        assertFalse(SafeOpen.isPassive(java.io.File("run.bat")))
        assertFalse(SafeOpen.isPassive(java.io.File("link.lnk")))
        assertFalse(SafeOpen.isPassive(java.io.File("script.ps1")))
        assertFalse(SafeOpen.isPassive(java.io.File("noextension")))
    }
}
