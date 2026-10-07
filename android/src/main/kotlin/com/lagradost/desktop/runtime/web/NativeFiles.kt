package com.lagradost.desktop.runtime.web

import java.io.File

/**
 * Native files shipped next to the app (not inside a jar, so nothing is unpacked at run time): the installer and the portable folder have
 * them in `app/resources` (Compose's appResourcesRootDir, `compose.application.resources.dir`); a development run finds them in
 * `app/native/windows-x64` of the source tree.
 */
object NativeFiles {
    private val dirs: List<File> by lazy {
        listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let(::File),
            File("native/windows-x64"),
            File("app/native/windows-x64"),
            File("../app/native/windows-x64"),
        ).map { it.absoluteFile }
    }

    /** The folder that has [name], or null */
    fun dirOf(name: String): File? = dirs.firstOrNull { File(it, name).isFile }

    fun find(name: String): File? = dirOf(name)?.let { File(it, name) }
}
