package dalvik.system

import java.io.File

/**
 * Stands in for an extension in the sandbox test: its bytes are defined a second time in a [ProbeLoader] (a dex class loader), so the code that runs is
 * code of a dex class loader, which is what the sandbox looks for on the call stack. Every function returns "ok" or what stopped it.
 */
class SandboxProbe {
    private inline fun attempt(block: () -> Unit): String = try { block(); "ok" } catch (e: SecurityException) { "refused: ${e.message}" } catch (e: Throwable) { "failed: $e" }

    fun runProgram() = attempt { ProcessBuilder("cmd.exe", "/c", "exit").start().waitFor() }
    fun readProfile() = attempt { File(System.getProperty("user.home"), "Documents").exists() }
    fun writeProfile() = attempt {
        val f = File(System.getProperty("user.home"), "cloudstream-sandbox-probe.tmp")
        f.createNewFile()
        f.delete()
    }
    fun deleteProfile() = attempt { File(System.getProperty("user.home"), "cloudstream-sandbox-probe-missing.tmp").delete() }
    fun exitCheck() = attempt { System.getSecurityManager()?.checkExit(7) }
    fun turnOff() = attempt { System.setSecurityManager(null) }
    fun jna() = attempt { Class.forName("com.sun.jna.Native") }
    fun unsafe() = attempt { Class.forName("sun.misc.Unsafe") }
    fun nativeLibrary() = attempt { System.loadLibrary("sandboxprobe") }
    fun insideTheJdk() = attempt { Class.forName("java.lang.ProcessImpl").declaredMethods }
    fun ownClass() = attempt { SandboxProbe::class.java.declaredMethods }
    fun publicMembers() = attempt { Class.forName("java.lang.String").getMethod("length") }
    fun ownClassLoader() = attempt { java.net.URLClassLoader(arrayOf(), null).close() }
    fun libraryReflects() = attempt { com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"a\":1}", Map::class.java) }
    fun network() = attempt { java.net.InetAddress.getByName("localhost") }
    fun tempFile() = attempt {
        val f = File.createTempFile("probe", ".tmp")
        f.writeText("x")
        f.readText()
        f.delete()
    }
    fun runtimeFiles() = attempt { File(System.getProperty("java.home"), "release").exists() }
}

/** A dex class loader that defines [SandboxProbe] itself instead of asking its parent */
class ProbeLoader(parent: ClassLoader) : BaseDexClassLoader(arrayOf<java.net.URL>(), parent) {
    fun probe(): Any {
        val bytes = SandboxProbe::class.java.getResourceAsStream("SandboxProbe.class")!!.use { it.readBytes() }
        val c = defineClass(SandboxProbe::class.java.name, bytes, 0, bytes.size)
        check(c.classLoader === this && c !== SandboxProbe::class.java)
        return c.getDeclaredConstructor().newInstance()
    }
}
