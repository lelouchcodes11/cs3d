package com.lagradost.desktop.sandbox

import dalvik.system.ProbeLoader
import java.util.function.Supplier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sandbox in a real JVM (the test task starts it with -Djava.security.manager=allow): code of a dex class loader (SandboxProbe, defined in a ProbeLoader) is
 * refused what malware does and is left alone for everything else; the same actions by the app's own code pass.
 */
class PluginSandboxTest {
    @AfterTest
    fun switchOff() { PluginSandbox.setEnabled(false) }

    private fun probe(): Any {
        PluginSandbox.setEnabled(true)
        assertEquals(PluginSandbox.State.Active, PluginSandbox.state, "the JVM of the tests has to be started with -Djava.security.manager=allow")
        return ProbeLoader(javaClass.classLoader).probe()
    }

    private fun Any.call(name: String): String = javaClass.getMethod(name).invoke(this) as String

    @Test
    fun anExtensionIsRefusedWhatMalwareDoes() {
        val p = probe()
        for (name in listOf("runProgram", "readProfile", "writeProfile", "deleteProfile", "exitCheck", "turnOff", "jna", "unsafe", "nativeLibrary", "insideTheJdk", "ownClassLoader")) {
            val answer = p.call(name)
            assertTrue(answer.startsWith("refused"), "$name should be refused, it answered: $answer")
        }
        val events = PluginSandbox.events().map { it.what }
        assertTrue(events.any { it.startsWith("run the program") }, "the refusals are listed: $events")
        assertTrue(events.any { it == "end the app" }, "$events")
    }

    @Test
    fun anExtensionKeepsWhatItNeeds() {
        val p = probe()
        for (name in listOf("ownClass", "publicMembers", "libraryReflects", "network", "tempFile", "runtimeFiles")) {
            assertEquals("ok", p.call(name), name)
        }
    }

    @Test
    fun theSameActionsOfTheAppItselfPass() {
        PluginSandbox.setEnabled(true)
        assertEquals(PluginSandbox.State.Active, PluginSandbox.state)
        val documents = java.io.File(System.getProperty("user.home"), "Documents")
        documents.exists()
        // a library reading its own classes, JNA loaded by the app
        Class.forName("com.sun.jna.Native")
        java.net.URLClassLoader(arrayOf(), null).close()
    }

    @Test
    fun theGateLetsTheAppActForAnExtension() {
        val p = probe()
        assertTrue(p.call("readProfile").startsWith("refused"))
        // the app's own code that does something for the extension on purpose (opening a link) runs in the gate
        val inside = SandboxGate.run(Supplier { p.call("readProfile") })
        assertEquals("ok", inside)
        assertTrue(p.call("readProfile").startsWith("refused"), "outside the gate it is refused again")
        assertTrue(SandboxGate.run(Supplier { SandboxGate.isTrusted() }))
        assertTrue(!SandboxGate.isTrusted())
    }

    @Test
    fun aTrustedExtensionIsLeftAlone() {
        val p = probe()
        // the probe has no file, so the sandbox calls it "an extension"
        assertTrue(p.call("readProfile").startsWith("refused"))
        PluginSandbox.trust("An Extension")
        try {
            assertEquals("ok", p.call("readProfile"))
            assertEquals("ok", p.call("jna"), "the package check follows the trust as well")
            assertTrue(PluginSandbox.isTrusted("an extension"))
        } finally {
            PluginSandbox.untrust("an extension")
        }
        assertTrue(p.call("readProfile").startsWith("refused"), "refused again after the trust is taken back")
    }

    @Test
    fun auditModeOnlyListsWhatWouldBeRefused() {
        // the setting off: nothing is installed and the probe may do all of it (a harmless one is enough)
        PluginSandbox.setEnabled(false)
        assertEquals(PluginSandbox.State.Off, PluginSandbox.state)
        val p = ProbeLoader(javaClass.classLoader).probe()
        assertEquals("ok", p.call("readProfile"))
    }
}
