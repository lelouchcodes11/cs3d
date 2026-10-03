import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=com.lagradost.cloudstream3.InternalAPI",
            "-opt-in=com.lagradost.cloudstream3.Prerelease",
            "-Xcontext-parameters",
            // lambdas and SAM conversions as plain classes (they come from the class-data-sharing archive) instead of invokedynamic, which links at run time: a cold start spent seconds on it
            "-Xlambdas=class",
            "-Xsam-conversions=class",
            "-Xstring-concat=inline",
        )
    }
}

/** The CloudStream engine this app is built on: extensions look at it (BuildConfig.VERSION_NAME), so it follows upstream */
val appVersion = "4.8.0"

/** This desktop app's own release version: the installer, the About page and the update checker (a GitHub release tag `v<this>`) */
val desktopVersion = "0.1.3"

/**
 * Generates com.lagradost.cloudstream3.R and packages the resources: the Android libraries' res/
 * (Material, AppCompat, ... from their AARs, like the Android build merges them) under the upstream
 * app's res/ folder, which wins. Values are resolved at runtime from the packaged XML files (every
 * qualifier folder) through an index written next to them.
 */
abstract class GenerateAndroidR : DefaultTask() {
    @get:InputDirectory
    abstract val resDir: DirectoryProperty

    /** Library AARs, highest priority first */
    @get:InputFiles
    abstract val libraries: ConfigurableFileCollection

    /** Libraries used as code (their classes run on the compat views): R classes from their R.txt */
    @get:InputFiles
    abstract val codeLibraries: ConfigurableFileCollection

    /** R classes only (no bytecode) for library packages upstream code refers to */
    @get:InputFiles
    abstract val rLibraries: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val sourceOut: DirectoryProperty

    @get:OutputDirectory
    abstract val resourceOut: DirectoryProperty

    private val typeIds = linkedMapOf(
        "anim" to 0x01, "array" to 0x02, "attr" to 0x03, "bool" to 0x04, "color" to 0x05, "dimen" to 0x06,
        "drawable" to 0x07, "font" to 0x08, "id" to 0x09, "integer" to 0x0a, "layout" to 0x0b, "menu" to 0x0c,
        "mipmap" to 0x0d, "navigation" to 0x0e, "plurals" to 0x0f, "raw" to 0x10, "string" to 0x11,
        "style" to 0x12, "xml" to 0x13, "animator" to 0x14, "interpolator" to 0x15, "transition" to 0x16, "fraction" to 0x17,
    )

    private val keywords = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
        "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
        "val", "var", "when", "while"
    )

    /** One res/ tree: entries relative to res/ ("drawable-v21/x.xml") with their bytes */
    private class Source(val tag: String, val entries: List<Pair<String, () -> ByteArray>>)

    @TaskAction
    fun generate() {
        val sources = ArrayList<Source>()
        // lowest priority first: the last library, ..., the first library, then the app
        for (aar in libraries.files.toList().reversed()) {
            val zip = ZipFile(aar)
            val tag = "lib-" + aar.name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9]+"), "-")
            val entries = zip.entries().toList().filter { !it.isDirectory && it.name.startsWith("res/") }
                .map { e -> e.name.removePrefix("res/") to { zip.getInputStream(e).use { it.readBytes() } } }
            sources.add(Source(tag, entries))
        }
        val res = resDir.get().asFile
        sources.add(Source("app", res.walkTopDown().filter { it.isFile && !it.name.startsWith(".") && it.parentFile.parentFile == res }
            .map { f -> "${f.parentFile.name}/${f.name}" to { f.readBytes() } }.toList()))

        val out = File(resourceOut.get().asFile, "android-res")
        out.deleteRecursively()
        out.mkdirs()
        val names = typeIds.keys.associateWith { sortedSetOf<String>() }.toMutableMap()
        val styleables = LinkedHashMap<String, MutableList<String>>() // name -> attr refs ("android:x" or "x")
        val files = HashMap<String, MutableList<String>>() // "type/name" -> packaged paths
        val valueFiles = sortedMapOf<String, MutableList<String>>() // values dir -> files
        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        val idRef = Regex("@\\+id/([A-Za-z0-9_.]+)")

        for (src in sources) {
            for ((rel, bytes) in src.entries) {
                val dir = rel.substringBefore('/')
                val file = rel.substringAfter('/')
                val type = dir.substringBefore('-')
                if (type == "values") {
                    val target = if (src.tag == "app") file else "${src.tag}.xml"
                    val data = bytes()
                    File(out, "$dir/$target").apply { parentFile.mkdirs() }.writeBytes(data)
                    valueFiles.getOrPut(dir) { ArrayList() }.let { if (target !in it) it.add(target) }
                    val doc = try {
                        dbf.newDocumentBuilder().parse(ByteArrayInputStream(data))
                    } catch (e: Exception) {
                        continue
                    }
                    val nodes = doc.documentElement.childNodes
                    for (i in 0 until nodes.length) {
                        val e = nodes.item(i) as? org.w3c.dom.Element ?: continue
                        val n = e.getAttribute("name").replace('.', '_')
                        if (n.isEmpty()) continue
                        when (e.tagName) {
                            "string" -> names["string"]!!.add(n)
                            "plurals" -> names["plurals"]!!.add(n)
                            "string-array", "integer-array", "array" -> names["array"]!!.add(n)
                            "color" -> names["color"]!!.add(n)
                            "dimen" -> names["dimen"]!!.add(n)
                            "integer" -> names["integer"]!!.add(n)
                            "bool" -> names["bool"]!!.add(n)
                            "fraction" -> names["fraction"]!!.add(n)
                            "style" -> names["style"]!!.add(n)
                            "attr" -> {
                                if (!e.getAttribute("name").contains(':')) names["attr"]!!.add(n)
                                val children = e.childNodes
                                for (j in 0 until children.length) {
                                    val c = children.item(j) as? org.w3c.dom.Element ?: continue
                                    if (c.tagName == "enum" || c.tagName == "flag") {
                                        val cn = c.getAttribute("name").replace('.', '_')
                                        if (cn.isNotEmpty()) names["id"]!!.add(cn)
                                    }
                                }
                            }
                            "declare-styleable" -> {
                                val list = styleables.getOrPut(n) { ArrayList() }
                                val attrs = e.getElementsByTagName("attr")
                                for (j in 0 until attrs.length) {
                                    val ae = attrs.item(j) as org.w3c.dom.Element
                                    val an = ae.getAttribute("name")
                                    if (!an.contains(':')) names["attr"]!!.add(an)
                                    if (an !in list) list.add(an)
                                    val children = ae.childNodes
                                    for (k in 0 until children.length) {
                                        val c = children.item(k) as? org.w3c.dom.Element ?: continue
                                        if (c.tagName == "enum" || c.tagName == "flag") {
                                            val cn = c.getAttribute("name").replace('.', '_')
                                            if (cn.isNotEmpty()) names["id"]!!.add(cn)
                                        }
                                    }
                                }
                            }
                            "item" -> {
                                val t = e.getAttribute("type")
                                if (t in typeIds) names[t]!!.add(n)
                            }
                            "drawable" -> names["drawable"]!!.add(n)
                        }
                    }
                } else if (typeIds.containsKey(type)) {
                    val data = bytes()
                    File(out, "$dir/$file").apply { parentFile.mkdirs() }.writeBytes(data)
                    val n = file.substringBefore('.')
                    names[type]!!.add(n)
                    files.getOrPut("$type/$n") { ArrayList() }.let { if ("$dir/$file" !in it) it.add("$dir/$file") }
                    if (file.endsWith(".xml") && (type == "layout" || type == "menu" || type == "navigation")) {
                        idRef.findAll(String(data)).forEach { names["id"]!!.add(it.groupValues[1].replace('.', '_')) }
                    }
                }
            }
        }

        // The file a (type, name) resolves to: unqualified or API qualified folders (highest API),
        // bitmaps from the densest folder up to xxhdpi; other qualifiers only when nothing else exists
        fun score(path: String): Int {
            val qualifiers = path.substringBefore('/').split('-').drop(1)
            var s = 0
            for (q in qualifiers) {
                s += when {
                    q.matches(Regex("v\\d+")) -> q.drop(1).toInt().coerceAtMost(34)
                    q == "anydpi" -> 950
                    q == "nodpi" -> 900
                    q == "xxhdpi" -> 800
                    q == "xhdpi" -> 700
                    q == "xxxhdpi" -> 650
                    q == "hdpi" -> 600
                    q == "mdpi" -> 500
                    q == "ldpi" -> 400
                    else -> -100_000
                }
            }
            return s
        }

        // Collect all resource names from library R.txt files so every declared field gets a valid non-zero id
        for (aar in (libraries.files + codeLibraries.files + rLibraries.files).distinct()) {
            ZipFile(aar).use { zip ->
                val rtxtEntry = zip.getEntry("R.txt") ?: return@use
                val text = String(zip.getInputStream(rtxtEntry).readBytes())
                for (line in text.lines()) {
                    val p = line.split(' ')
                    if (p.size >= 3 && p[0] == "int") {
                        val t = p[1]
                        val n = p[2].replace('.', '_')
                        if (t in typeIds && !n.contains(':')) {
                            names[t]?.add(n)
                        }
                    }
                }
            }
        }

        // Framework attributes of styleables get ids of android.R.attr (or stable made up ones)
        val frameworkAttrs = sortedSetOf<String>()
        styleables.values.forEach { l -> l.filter { it.startsWith("android:") }.forEach { frameworkAttrs.add(it.removePrefix("android:")) } }

        val src = StringBuilder()
        src.append("// Generated from app/src/main/android-res and the Android libraries' resources, do not edit\n")
        src.append("@file:Suppress(\"ClassName\", \"unused\", \"ObjectPropertyName\")\n\n")
        src.append("package com.lagradost.cloudstream3\n\n")
        src.append("object R {\n")
        val index = StringBuilder()
        val attrIds = HashMap<String, Int>()
        for ((type, typeId) in typeIds) {
            val entries = names[type]!!
            if (entries.isEmpty()) continue
            src.append("    object $type {\n")
            entries.forEachIndexed { i, n ->
                val id = (0x7f shl 24) or (typeId shl 16) or i
                if (type == "attr") attrIds[n] = id
                val kn = if (n in keywords) "`$n`" else n
                src.append("        const val $kn: Int = 0x${Integer.toHexString(id)}\n")
                index.append(Integer.toHexString(id)).append(' ').append(type).append(' ').append(n)
                files["$type/$n"]?.maxByOrNull { score(it) }?.let { index.append(' ').append(it) }
                index.append('\n')
            }
            src.append("    }\n")
        }
        frameworkAttrs.forEachIndexed { i, n -> index.append("#fwattr ").append(n).append('\n') }
        src.append("    object styleable {\n")
        for ((name, attrs) in styleables) {
            val ids = attrs.map { a ->
                if (a.startsWith("android:")) "com.lagradost.desktop.runtime.res.FrameworkResources.attrId(\"${a.removePrefix("android:")}\")"
                else "0x" + Integer.toHexString(attrIds[a] ?: 0)
            }
            src.append("        @JvmField val $name: IntArray = intArrayOf(${ids.joinToString(", ")})\n")
            attrs.forEachIndexed { i, a -> src.append("        const val ${name}_${a.replace(':', '_')}: Int = $i\n") }
        }
        src.append("    }\n")
        src.append("}\n")
        for ((dir, list) in valueFiles) index.append("#values ").append(dir).append(' ').append(list.joinToString(" ")).append('\n')

        val outDir = File(sourceOut.get().asFile, "com/lagradost/cloudstream3")
        outDir.mkdirs()
        File(outDir, "R.kt").writeText(src.toString())
        File(out, "index.txt").writeText(index.toString())

        // R classes of code libraries, with the ids of the merged resources (fields are read at runtime)
        val allIds = HashMap<String, Int>()
        for ((type, typeId) in typeIds) names[type]!!.forEachIndexed { i, n -> allIds["$type/$n"] = (0x7f shl 24) or (typeId shl 16) or i }
        fun libraryR(aar: File, zip: ZipFile) {
            run {
                val manifest = zip.getEntry("AndroidManifest.xml")?.let { e -> String(zip.getInputStream(e).readBytes()) } ?: return
                val pkg = Regex("package=\"([^\"]+)\"").find(manifest)?.groupValues?.get(1) ?: return
                val rtxt = zip.getEntry("R.txt")?.let { e -> String(zip.getInputStream(e).readBytes()) } ?: return
                val byType = LinkedHashMap<String, MutableList<String>>()
                val arrays = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
                val arrayNames = rtxt.lines().filter { it.startsWith("int[] styleable ") }.map { it.split(' ')[2] }.sortedByDescending { it.length }
                for (line in rtxt.lines()) {
                    val p = line.split(' ')
                    if (p.size < 4) continue
                    if (p[0] == "int" && p[1] == "styleable") {
                        val owner = arrayNames.firstOrNull { p[2].startsWith(it + "_") } ?: continue
                        val attr = p[2].removePrefix(owner + "_")
                        arrays.getOrPut(owner) { ArrayList() }.add(p[3].toInt() to attr)
                    } else if (p[0] == "int") byType.getOrPut(p[1]) { ArrayList() }.add(p[2])
                }
                val nl = "\n"
                val sb = StringBuilder("// Generated from R.txt of ").append(aar.name).append(", do not edit").append(nl)
                sb.append("@file:Suppress(\"ClassName\", \"unused\", \"ObjectPropertyName\")").append(nl).append(nl)
                sb.append("pack" + "age ").append(pkg).append(nl).append(nl).append("object R {").append(nl)
                for ((type, list) in byType) {
                    sb.append("    object ").append(type).append(" {").append(nl)
                    for (n in list) sb.append("        @JvmField var ").append(n).append(": Int = 0x").append(Integer.toHexString(allIds["$type/$n"] ?: 0)).append(nl)
                    sb.append("    }").append(nl)
                }
                sb.append("    object styleable {").append(nl)
                for ((name, attrs) in arrays) {
                    val sorted = attrs.sortedBy { it.first }
                    val ids = sorted.map { (_, a) ->
                        if (a.startsWith("android_")) "com.lagradost.desktop.runtime.res.FrameworkResources.attrId(\"" + a.removePrefix("android_") + "\")"
                        else "0x" + Integer.toHexString(allIds["attr/$a"] ?: 0)
                    }
                    sb.append("        @JvmField var ").append(name).append(": IntArray = intArrayOf(").append(ids.joinToString(", ")).append(")").append(nl)
                    for ((i, a) in sorted) sb.append("        @JvmField var ").append(name).append("_").append(a).append(": Int = ").append(i).append(nl)
                }
                sb.append("    }").append(nl).append("}").append(nl)
                val dir = File(sourceOut.get().asFile, pkg.replace('.', '/'))
                dir.mkdirs()
                File(dir, "R.kt").writeText(sb.toString())
            }
        }
        for (aar in codeLibraries.files) ZipFile(aar).let { z -> try { libraryR(aar, z) } finally { z.close() } }
        for (aar in rLibraries.files) ZipFile(aar).let { z -> try { libraryR(aar, z) } finally { z.close() } }
    }
}

/** Android libraries whose resources the app uses (the upstream versions), as AARs */
val androidLibraryRes: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}

dependencies {
    androidLibraryRes("com.google.android.material:material:1.14.0@aar")
    androidLibraryRes("androidx.appcompat:appcompat:1.7.1@aar")
    androidLibraryRes("androidx.appcompat:appcompat-resources:1.7.1@aar")
    androidLibraryRes("androidx.preference:preference:1.2.1@aar")
    androidLibraryRes("androidx.media3:media3-ui:1.9.3@aar")
    androidLibraryRes("androidx.constraintlayout:constraintlayout:2.2.1@aar")
    androidLibraryRes("androidx.coordinatorlayout:coordinatorlayout:1.2.0@aar")
    androidLibraryRes("androidx.cardview:cardview:1.0.0@aar")
    androidLibraryRes("androidx.recyclerview:recyclerview:1.4.0@aar")
    androidLibraryRes("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0@aar")
    androidLibraryRes("androidx.viewpager2:viewpager2:1.1.0@aar")
    androidLibraryRes("androidx.core:core:1.18.0@aar")
    androidLibraryRes("com.facebook.shimmer:shimmer:0.5.0@aar")
}

/** Android libraries used as code on the compat views (layout only libraries) */
val androidCodeAars: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}

dependencies {
    androidCodeAars("androidx.constraintlayout:constraintlayout:2.2.1@aar")
}

/** classes.jar of the code libraries */
val extractCodeAars = tasks.register<Copy>("extractCodeAars") {
    from(androidCodeAars.elements.map { files -> files.map { f -> zipTree(f.asFile).matching { include("classes.jar") } } })
    into(layout.buildDirectory.dir("codeLibs"))
    eachFile { name = "constraintlayout.jar" }
}

dependencies {
    implementation(files(extractCodeAars.map { layout.buildDirectory.file("codeLibs/constraintlayout.jar") }).builtBy(extractCodeAars))
    implementation("androidx.constraintlayout:constraintlayout-core:1.1.1")
}

/** Library AARs whose R class is generated, without putting their bytecode on the classpath */
val androidRLibraries: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}

dependencies {
    androidRLibraries("com.google.android.material:material:1.14.0@aar")
    androidRLibraries("androidx.appcompat:appcompat:1.7.1@aar")
    androidRLibraries("androidx.preference:preference:1.2.1@aar")
    androidRLibraries("androidx.media3:media3-ui:1.9.3@aar")
    androidRLibraries("androidx.core:core:1.18.0@aar")
    androidRLibraries("androidx.recyclerview:recyclerview:1.4.0@aar")
    androidRLibraries("androidx.viewpager2:viewpager2:1.1.0@aar")
    androidRLibraries("androidx.coordinatorlayout:coordinatorlayout:1.2.0@aar")
    androidRLibraries("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0@aar")
    androidRLibraries("com.facebook.shimmer:shimmer:0.5.0@aar")
}

val generateAndroidR = tasks.register<GenerateAndroidR>("generateAndroidR") {
    resDir.set(layout.projectDirectory.dir("src/main/android-res"))
    libraries.from(androidLibraryRes)
    codeLibraries.from(androidCodeAars)
    rLibraries.from(androidRLibraries)
    sourceOut.set(layout.buildDirectory.dir("generated/androidR/kotlin"))
    resourceOut.set(layout.buildDirectory.dir("generated/androidR/resources"))
}

/** com.lagradost.cloudstream3.BuildConfig with the same fields as the Android build */
abstract class GenerateBuildConfig : DefaultTask() {
    @get:Input
    abstract val fields: MapProperty<String, String>

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = File(out.get().asFile, "com/lagradost/cloudstream3")
        dir.mkdirs()
        val body = fields.get().entries.joinToString("\n") { (k, v) -> "  public static final $v;".replace("\$NAME", k) }
        File(dir, "BuildConfig.java").writeText(
            "// Generated, do not edit\npackage com.lagradost.cloudstream3;\n\npublic final class BuildConfig {\n$body\n  private BuildConfig() {}\n}\n"
        )
    }
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(env: String, prop: String): String =
    (System.getenv(env) ?: localProps.getProperty(prop) ?: "null").replace("\\", "\\\\").replace("\"", "\\\"")

/**
 * Copies [app/src/shims/AndroidPropertyShims.kt.tmpl] into every package under
 * com.lagradost.cloudstream3 so upstream property syntax resolves without imports.
 */
abstract class GeneratePropertyShims : DefaultTask() {
    @get:InputFile
    abstract val template: RegularFileProperty

    @get:InputDirectory
    abstract val sourceRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = sourceRoot.get().asFile
        val templateText = template.get().asFile.readText()
        if (!templateText.startsWith("package __PKG__")) {
            throw GradleException("AndroidPropertyShims.kt.tmpl must start with \"package __PKG__\"")
        }
        val ident = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val outRoot = outputDir.get().asFile
        outRoot.deleteRecursively()
        for (dir in root.walkTopDown().filter { it.isDirectory }) {
            val rel = dir.relativeTo(root).invariantSeparatorsPath
            val suffix = if (rel.isEmpty() || rel == ".") emptyList() else rel.split('/')
            if (suffix.any { !ident.matches(it) }) {
                throw GradleException("Not a Kotlin package directory: ${dir.path}")
            }
            val pkg = if (suffix.isEmpty()) "com.lagradost.cloudstream3" else "com.lagradost.cloudstream3." + suffix.joinToString(".")
            val dest = if (suffix.isEmpty()) File(outRoot, "com/lagradost/cloudstream3") else File(outRoot, "com/lagradost/cloudstream3/$rel")
            dest.mkdirs()
            File(dest, "AndroidPropertyShims.kt").writeText(
                templateText.replaceFirst("package __PKG__", "package $pkg"),
            )
        }
    }
}

val generatePropertyShims = tasks.register<GeneratePropertyShims>("generatePropertyShims") {
    template.set(layout.projectDirectory.file("src/shims/AndroidPropertyShims.kt.tmpl"))
    sourceRoot.set(layout.projectDirectory.dir("src/main/kotlin/com/lagradost/cloudstream3"))
    outputDir.set(layout.buildDirectory.dir("generated/propertyShims"))
}

val generateBuildConfig = tasks.register<GenerateBuildConfig>("generateBuildConfig") {
    fields.set(
        linkedMapOf(
            "DEBUG" to "boolean \$NAME = false",
            "APPLICATION_ID" to "String \$NAME = \"com.lagradost.cloudstream3\"",
            "BUILD_TYPE" to "String \$NAME = \"release\"",
            "FLAVOR" to "String \$NAME = \"stable\"",
            "VERSION_CODE" to "int \$NAME = 68",
            "VERSION_NAME" to "String \$NAME = \"$appVersion\"",
            "DESKTOP_VERSION" to "String \$NAME = \"$desktopVersion\"",
            "BUILD_DATE" to "long \$NAME = ${System.currentTimeMillis() / 86_400_000L * 86_400_000L}L",
            "SIMKL_CLIENT_ID" to "String \$NAME = \"${secret("SIMKL_CLIENT_ID", "simkl.id")}\"",
            "SIMKL_CLIENT_SECRET" to "String \$NAME = \"${secret("SIMKL_CLIENT_SECRET", "simkl.secret")}\"",
            "MAL_KEY" to "String \$NAME = \"${secret("MAL_KEY", "mal.key")}\"",
            "ANILIST_KEY" to "String \$NAME = \"${secret("ANILIST_KEY", "anilist.key")}\"",
        )
    )
    out.set(layout.buildDirectory.dir("generated/buildConfig/java"))
}

/**
 * Java view bindings for app/src/main/android-res/layout*. One class per layout name.
 * Configurations of the same layout merge; an id missing from some of them is left nullable
 * by omitting the null check (no nullability annotations, matching upstream call sites).
 */
abstract class GenerateViewBindings : DefaultTask() {
    @get:InputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val toolsNs = "http://schemas.android.com/tools"
    private val viewTags = setOf("View", "ViewStub", "SurfaceView", "TextureView")
    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null",
    )

    private class IdRef(val name: String, val android: Boolean) {
        val key: String = (if (android) "a:" else "p:") + name
        fun r(): String = if (android) "android.R.id.$name" else "com.lagradost.cloudstream3.R.id.${name.replace('.', '_')}"
    }

    // Root ids are bound from rootView like AGP, since an <include android:id> replaces the root's id
    private val rootIds = HashMap<String, String>()

    private class Seen(val id: IdRef, val type: String, val includeLayout: String?)
    private class Field(val id: IdRef, val type: String, val includeLayout: String?, var hits: Int, val field: String)

    @TaskAction
    fun generate() {
        val res = resDir.get().asFile
        val byName = sortedMapOf<String, MutableList<File>>()
        res.listFiles()?.filter { it.isDirectory && it.name.startsWith("layout") }?.forEach { dir ->
            dir.listFiles()?.filter { it.isFile && it.extension.equals("xml", true) }?.forEach { file ->
                byName.getOrPut(file.nameWithoutExtension) { ArrayList() }.add(file)
            }
        }
        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        val outRoot = outputDir.get().asFile
        outRoot.deleteRecursively()
        val outDir = File(outRoot, "com/lagradost/cloudstream3/databinding")
        outDir.mkdirs()
        val layouts = LinkedHashMap<String, List<Field>>()
        val roots = HashMap<String, String>()
        rootIds.clear()
        for ((name, files0) in byName) {
            val files = files0.sortedWith(compareBy({ it.parentFile.name.length }, { it.parentFile.name }))
            val docs = files.map { file ->
                val doc = try {
                    dbf.newDocumentBuilder().parse(file).documentElement
                } catch (e: Exception) {
                    throw GradleException("Cannot parse ${file.path}: ${e.message}")
                }
                logicalRoot(doc) to file
            }
            if (docs.any { ignored(it.first) }) continue
            val rootKinds = docs.map { rootKind(it.first) }
            if (rootKinds.toSet().size != 1) {
                throw GradleException("Layout $name has different roots: " + docs.zip(rootKinds).joinToString { "${it.first.second.parentFile.name}=${it.second}" })
            }
            roots[name] = rootKinds.first()
            val rootIdKeys = docs.map { if (it.first.localName == "merge") null else idOf(it.first)?.key }.toSet()
            if (rootIdKeys.size == 1 && rootIdKeys.first() != null) rootIds[name] = rootIdKeys.first()!!
            val merged = LinkedHashMap<String, Field>()
            for ((root, _) in docs) {
                val seen = ArrayList<Seen>()
                collect(root, seen)
                val unique = LinkedHashMap<String, Seen>()
                for (s in seen) {
                    val prev = unique[s.id.key]
                    if (prev == null) unique[s.id.key] = s
                    else if (prev.type != s.type) throw GradleException("Layout $name id ${s.id.name} has types ${prev.type} and ${s.type}")
                }
                for (s in unique.values) {
                    val field = fieldName(s.id.name)
                    val existing = merged[s.id.key]
                    if (existing == null) merged[s.id.key] = Field(s.id, s.type, s.includeLayout, 1, field)
                    else {
                        if (existing.type != s.type) throw GradleException("Layout $name id ${s.id.name} has types ${existing.type} and ${s.type}")
                        existing.hits++
                    }
                }
            }
            val fields = merged.values.toList()
            val dup = fields.groupBy { it.field }.filter { it.value.size > 1 }
            if (dup.isNotEmpty()) throw GradleException("Layout $name field names collide: ${dup.keys}")
            layouts[name] = fields
        }
        val home = layouts["fragment_home"] ?: throw GradleException("fragment_home binding was not generated")
        if (home.none { it.id.name == "home_root" }) throw GradleException("fragment_home is missing home_root")
        for ((name, fields) in layouts) {
            val root = roots[name] ?: throw GradleException("Missing root for $name")
            for (f in fields) {
                if (f.includeLayout != null && rootKindOfIncluded(f.includeLayout, roots) == null) {
                    throw GradleException("Layout $name includes missing layout ${f.includeLayout}")
                }
            }
            File(outDir, bindingName(name) + ".java").writeText(render(name, root, fields, filesOf(name, byName)))
        }
    }

    private fun filesOf(name: String, byName: Map<String, MutableList<File>>): Int = byName[name]?.size ?: 0

    private fun rootKindOfIncluded(layout: String, roots: Map<String, String>): String? = roots[layout]

    private fun logicalRoot(el: Element): Element {
        if (el.localName != "layout") return el
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i)
            if (child is Element && child.localName != "data") return child
        }
        throw GradleException("layout wrapper has no view")
    }

    private fun ignored(el: Element): Boolean = xmlAttr(el, toolsNs, "viewBindingIgnore") == "true"

    private fun rootKind(el: Element): String = if (el.localName == "merge") "merge" else viewType(el)

    private fun collect(el: Element, out: MutableList<Seen>) {
        val tag = el.localName ?: return
        if (tag == "requestFocus" || tag == "tag" || tag == "eat-comment" || tag == "fragment") return
        if (tag == "include") {
            val id = idOf(el)
            val layout = el.getAttribute("layout").removePrefix("@layout/").substringBefore('.')
            if (id != null && layout.isNotEmpty()) out.add(Seen(id, bindingName(layout), layout))
            return
        }
        if (tag != "merge") {
            val id = idOf(el)
            if (id != null) out.add(Seen(id, viewType(el), null))
        }
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i)
            if (child is Element) collect(child, out)
        }
    }

    private fun idOf(el: Element): IdRef? {
        val raw = xmlAttr(el, androidNs, "id")
        return when {
            raw.startsWith("@android:id/") -> IdRef(raw.removePrefix("@android:id/"), true)
            raw.startsWith("@+id/") || raw.startsWith("@id/") -> IdRef(raw.substringAfterLast('/'), false)
            else -> null
        }
    }

    private fun viewType(el: Element): String {
        val override = xmlAttr(el, toolsNs, "viewBindingType")
        if (override.isNotEmpty()) return override
        val tag = el.localName
        if (tag == "view") {
            val cls = el.getAttribute("class")
            return if (cls.isNotEmpty()) cls else "android.view.View"
        }
        if (tag.contains('.')) return tag
        if (tag == "WebView") return "android.webkit.WebView"
        if (tag in viewTags) return "android.view.$tag"
        return "android.widget.$tag"
    }

    private fun xmlAttr(el: Element, ns: String, local: String): String {
        val named = el.getAttributeNS(ns, local)
        if (!named.isNullOrEmpty()) return named
        val prefix = if (ns == androidNs) "android" else "tools"
        return el.getAttribute("$prefix:$local")
    }

    private fun bindingName(layout: String): String {
        val parts = layout.split('_').filter { it.isNotEmpty() }
        return parts.joinToString("") { it.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() } } + "Binding"
    }

    private fun fieldName(id: String): String {
        val parts = id.split('_').filter { it.isNotEmpty() }
        val raw = if (parts.isEmpty()) "view" else parts.mapIndexed { i, part ->
            if (i == 0) part else part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }.joinToString("")
        val safe = if (raw[0].isDigit()) "_$raw" else raw
        return if (safe in javaKeywords) safe + "_" else safe
    }

    private fun render(name: String, rootKind: String, fields: List<Field>, fileCount: Int): String {
        val nl = "\n"
        val className = bindingName(name)
        val merge = rootKind == "merge"
        val rootType = if (merge) "android.view.ViewGroup" else rootKind
        val rootField = if (fields.any { it.field == "rootView" }) "bindingRoot" else "rootView"
        val sb = StringBuilder()
        sb.append("// Generated from layout resources by generateViewBindings. Do not edit.").append(nl)
        sb.append("pack").append("age com.lagradost.cloudstream3.databinding;").append(nl).append(nl)
        sb.append("import androidx.viewbinding.ViewBinding;").append(nl).append(nl)
        sb.append("public final class ").append(className).append(" implements ViewBinding {").append(nl)
        for (f in fields) {
            sb.append("    public final ").append(f.type).append(" ").append(f.field).append(";").append(nl)
        }
        sb.append("    private final ").append(rootType).append(" ").append(rootField).append(";").append(nl).append(nl)
        sb.append("    private ").append(className).append("(").append(rootType).append(" ").append(rootField)
        for (f in fields) sb.append(", ").append(f.type).append(" ").append(f.field)
        sb.append(") {").append(nl)
        sb.append("        this.").append(rootField).append(" = ").append(rootField).append(";").append(nl)
        for (f in fields) sb.append("        this.").append(f.field).append(" = ").append(f.field).append(";").append(nl)
        sb.append("    }").append(nl).append(nl)
        sb.append("    @Override").append(nl)
        sb.append("    public ").append(rootType).append(" getRoot() { return ").append(rootField).append("; }").append(nl).append(nl)
        if (!merge) {
            sb.append("    public static ").append(className).append(" inflate(android.view.LayoutInflater inflater) {").append(nl)
            sb.append("        return inflate(inflater, null, false);").append(nl)
            sb.append("    }").append(nl).append(nl)
            sb.append("    public static ").append(className).append(" inflate(android.view.LayoutInflater inflater, android.view.ViewGroup parent, boolean attachToParent) {").append(nl)
            sb.append("        android.view.View root = inflater.inflate(com.lagradost.cloudstream3.R.layout.").append(name).append(", parent, false);").append(nl)
            sb.append("        if (attachToParent) {").append(nl)
            sb.append("            if (parent == null) throw new NullPointerException(\"parent\");").append(nl)
            sb.append("            parent.addView(root);").append(nl)
            sb.append("        }").append(nl)
            sb.append("        return bind(root);").append(nl)
            sb.append("    }").append(nl).append(nl)
        } else {
            sb.append("    public static ").append(className).append(" inflate(android.view.LayoutInflater inflater, android.view.ViewGroup parent) {").append(nl)
            sb.append("        if (parent == null) throw new NullPointerException(\"parent\");").append(nl)
            sb.append("        inflater.inflate(com.lagradost.cloudstream3.R.layout.").append(name).append(", parent, true);").append(nl)
            sb.append("        return bind(parent);").append(nl)
            sb.append("    }").append(nl).append(nl)
        }
        sb.append("    public static ").append(className).append(" bind(android.view.View rootView) {").append(nl)
        sb.append("        ").append(rootType).append(" root = (").append(rootType).append(") rootView;").append(nl)
        fields.forEachIndexed { i, f ->
            val required = f.hits == fileCount
            val expr = f.id.r()
            if (f.includeLayout != null) {
                sb.append("        android.view.View w").append(i).append(" = root.findViewById(").append(expr).append(");").append(nl)
                if (required) {
                    sb.append("        if (w").append(i).append(" == null) throw new NullPointerException(\"Missing required view with ID: \" + root.getResources().getResourceName(").append(expr).append("));").append(nl)
                    sb.append("        ").append(f.type).append(" f").append(i).append(" = ").append(f.type).append(".bind(w").append(i).append(");").append(nl)
                } else {
                    sb.append("        ").append(f.type).append(" f").append(i).append(" = w").append(i).append(" == null ? null : ").append(f.type).append(".bind(w").append(i).append(");").append(nl)
                }
            } else if (!merge && rootIds[name] == f.id.key) {
                sb.append("        ").append(f.type).append(" f").append(i).append(" = (").append(f.type).append(") rootView;").append(nl)
            } else if (required) {
                sb.append("        ").append(f.type).append(" f").append(i).append(" = (").append(f.type).append(") root.findViewById(").append(expr).append(");").append(nl)
                sb.append("        if (f").append(i).append(" == null) throw new NullPointerException(\"Missing required view with ID: \" + root.getResources().getResourceName(").append(expr).append("));").append(nl)
            } else {
                sb.append("        ").append(f.type).append(" f").append(i).append(" = (").append(f.type).append(") root.findViewById(").append(expr).append(");").append(nl)
            }
        }
        sb.append("        return new ").append(className).append("(root")
        for (i in fields.indices) sb.append(", f").append(i)
        sb.append(");").append(nl)
        sb.append("    }").append(nl)
        sb.append("}").append(nl)
        return sb.toString()
    }
}

val generateViewBindings = tasks.register<GenerateViewBindings>("generateViewBindings") {
    resDir.set(layout.projectDirectory.dir("src/main/android-res"))
    outputDir.set(layout.buildDirectory.dir("generated/viewBinding"))
}

sourceSets {
    main {
        kotlin.srcDir(generateAndroidR.map { it.sourceOut })
        kotlin.srcDir(generatePropertyShims.map { it.outputDir })
        java.srcDir(generateBuildConfig.map { it.out })
        java.srcDir(generateViewBindings.map { it.outputDir })
        resources.srcDir(generateAndroidR.map { it.resourceOut })
    }
}

tasks.named("compileKotlin") { dependsOn(generatePropertyShims, generateViewBindings) }
tasks.named("compileJava") { dependsOn(generateViewBindings) }

tasks.named("processResources") { dependsOn(generateAndroidR) }

dependencies {
    implementation(project(":android"))
    implementation(project(":library"))
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material", module = "material")
    }
    implementation(libs.bundles.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.network.okhttp)
    implementation(libs.dex.translator)
    implementation(libs.dex.tools)
    implementation(libs.bouncycastle)
    implementation(libs.gson)
    implementation(libs.fuzzywuzzy)
    implementation(libs.juniversalchardet)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.qrcode.kotlin)
    implementation(libs.anime.db)
    implementation(libs.conscrypt)

    testImplementation(libs.kotlin.test)
}

compose.desktop {
    application {
        mainClass = "com.lagradost.desktop.MainKt"
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
            "-Xss4m",
            // start-up: the app's classes (about 12k) are loaded from a class-data-sharing archive that portableDist creates
            // (and that the JVM re-creates by itself at exit when the jars changed); the native splash shows at once
            "-XX:+AutoCreateSharedArchive",
            // forward slashes: jpackage drops backslashes from java options
            "-XX:SharedArchiveFile=\$APPDIR/cloudstream.jsa",
            "-splash:\$APPDIR/splash.png",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "CloudStream"
            packageVersion = desktopVersion
            description = "CloudStream for desktop"
            vendor = "CloudStream Desktop"
            modules("java.sql", "java.naming", "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.mscapi", "java.management", "jdk.management", "java.net.http", "jdk.accessibility", "jdk.httpserver", "jdk.localedata")
            windows {
                iconFile.set(project.file("src/main/icons/icon.ico"))
                menuGroup = "CloudStream"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8f7a1c2e-8d5a-4b1e-9f0e-6f1c9d3a2b7c"
            }
            linux {
                iconFile.set(project.file("src/main/icons/icon.png"))
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

/**
 * Start-up speed of a copy of the app image in [root]: a class-data-sharing archive of the runtime and of the app (see the notes in the body).
 * Failures only cost speed.
 */
fun trainStartupArchive(root: File) {
    // Start-up speed. jpackage's runtime has no class-data-sharing archive (JDK classes then load about twice as slowly),
    // so one is made with the JDK the runtime was linked from (its java.exe is only borrowed for this and removed again).
    // Then the app is started once on a throw-away data folder, shown every screen, and quit: the JVM writes
    // app/cloudstream.jsa at exit, which the launcher uses from then on (see jvmArgs above). Failures only cost speed.
    runCatching {
        val runtimeBin = File(root, "runtime/bin")
        val borrowed = File(runtimeBin, "java.exe")
        File(System.getProperty("java.home"), "bin/java.exe").copyTo(borrowed, overwrite = true)
        try {
            ProcessBuilder(borrowed.absolutePath, "-Xshare:dump").redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor()
        } finally {
            borrowed.delete()
        }
        File(root, "app/cloudstream.jsa").delete()
        val scratch = File(System.getProperty("java.io.tmpdir"), "cloudstream-cds-training").also { it.deleteRecursively(); it.mkdirs() }
        val port = 8793
        val pb = ProcessBuilder(File(root, "CloudStream.exe").absolutePath).directory(root).redirectErrorStream(true)
        pb.environment()["JAVA_TOOL_OPTIONS"] = "-Dcloudstream.data=${scratch.absolutePath} -Dcloudstream.devport=$port -Dcloudstream.ipcport=52998"
        val proc = pb.start()
        Thread { runCatching { proc.inputStream.readBytes() } }.apply { isDaemon = true }.start()
        fun call(path: String): Boolean = runCatching {
            (URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection).run {
                connectTimeout = 1000; readTimeout = 20000; responseCode in 200..299
            }
        }.getOrDefault(false)
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline && !call("/state")) Thread.sleep(500)
        for (route in listOf("home", "search&q=test", "library", "downloads", "extensions", "settings", "home")) {
            call("/nav?route=$route"); Thread.sleep(1200)
        }
        // every settings page (the preference screens, the subtitle style editor, the account list): their classes and lambdas go into the archive too
        for (page in listOf("general", "player", "subtitles", "ui", "updates", "account", "about")) {
            call("/nav?route=settings&q=$page"); Thread.sleep(1500)
        }
        call("/nav?route=home"); Thread.sleep(800)
        call("/quit")
        proc.waitFor(60, TimeUnit.SECONDS)
        scratch.deleteRecursively()
        println("start-up archive: class-data-sharing archive " + (if (File(root, "app/cloudstream.jsa").exists()) "created" else "NOT created"))
    }.onFailure { println("start-up archive: start-up archive skipped: $it") }
}

// Portable build: ./gradlew :app:portableDist -> dist/CloudStream-Portable/CloudStream.exe
// Nothing to install; because of portable.txt everything the app stores goes to the data folder next to the exe.
tasks.register<Copy>("portableDist") {
    group = "distribution"
    description = "Portable app folder (no installer) in dist/CloudStream-Portable"
    dependsOn("createDistributable")
    val target = rootProject.layout.projectDirectory.dir("dist/CloudStream-Portable")
    from(layout.buildDirectory.dir("compose/binaries/main/app/CloudStream"))
    from("src/main/splash") { into("app") }
    into(target)
    doFirst {
        // an older copy must not leave stale jars behind; the data folder stays
        delete(target.dir("app"), target.dir("runtime"))
    }
    doLast {
        target.file("portable.txt").asFile.writeText(
            "CloudStream portable mode.\r\n" +
                "While this file (or a data folder) is next to CloudStream.exe, settings, extensions and caches are kept in the data folder here.\r\n" +
                "Move or copy this whole folder to take everything with you; delete it to remove everything.\r\n",
        )
        trainStartupArchive(target.asFile)
    }
}

// Installer: ./gradlew :app:installerMsi -> dist/release/CloudStream-<version>-windows-x64.msi
// One MSI for the current user (no administrator rights; start menu entry, desktop shortcut, folder of choice, default %LOCALAPPDATA%\CloudStream).
// A newer MSI of the same product (same upgrade UUID) replaces the installed version, which is how the app's update checker installs a release.
// The data of an installed copy lives in %APPDATA%\CloudStream (a portable copy keeps it next to the exe). jpackage needs the WiX Toolset 3.x
// (candle.exe, light.exe) on PATH; the windows runners of GitHub Actions have it.
tasks.register("installerMsi") {
    group = "distribution"
    description = "Windows installer (MSI) in dist/release"
    dependsOn("createDistributable")
    val image = layout.buildDirectory.dir("compose/binaries/main/app/CloudStream")
    val staging = layout.buildDirectory.dir("installer")
    val splash = layout.projectDirectory.dir("src/main/splash")
    val icon = layout.projectDirectory.file("src/main/icons/icon.ico")
    val dest = rootProject.layout.projectDirectory.dir("dist/release")
    val version = desktopVersion
    doLast {
        val stage = staging.get().asFile.also { it.deleteRecursively(); it.mkdirs() }
        val root = File(stage, "CloudStream")
        image.get().asFile.copyRecursively(root, overwrite = true)
        splash.asFile.copyRecursively(File(root, "app"), overwrite = true)
        trainStartupArchive(root)
        val jpackage = File(System.getProperty("java.home"), "bin/jpackage.exe")
        val command = listOf(
            jpackage.absolutePath, "--type", "msi", "--app-image", root.absolutePath, "--dest", stage.absolutePath,
            "--name", "CloudStream", "--app-version", version, "--vendor", "CloudStream Desktop", "--description", "CloudStream for desktop",
            "--icon", icon.asFile.absolutePath, "--win-per-user-install", "--win-menu", "--win-menu-group", "CloudStream",
            "--win-shortcut", "--win-dir-chooser", "--win-upgrade-uuid", "8f7a1c2e-8d5a-4b1e-9f0e-6f1c9d3a2b7c",
        )
        val proc = ProcessBuilder(command).directory(stage).redirectErrorStream(true).start()
        val log = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0) throw GradleException("jpackage failed (is the WiX Toolset 3.x on PATH?):\n$log")
        val out = dest.asFile.also { it.mkdirs() }
        val msi = File(out, "CloudStream-$version-windows-x64.msi")
        File(stage, "CloudStream-$version.msi").copyTo(msi, overwrite = true)
        println("installerMsi: ${msi.absolutePath} (${msi.length() / 1_048_576} MB)")
    }
}

tasks.register("printRuntimeClasspath") {
    val cp = configurations.named("runtimeClasspath")
    doLast { cp.get().resolve().forEach { println("CP:" + it.absolutePath) } }
}

// Headless extension test: ./gradlew :app:runHarness -Pargs="<dataDir> <repos> <load|full> [filters]"
tasks.register<JavaExec>("runHarness") {
    group = "verification"
    mainClass.set("com.lagradost.desktop.tools.ExtensionHarness")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs("-Xmx3g", "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
    args = (project.findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

// Chromium for the WebView implementation, bundled so it is not downloaded on first use
val jcefNativesPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val cpu = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "amd64"
    when {
        os.contains("win") -> "windows-$cpu"
        os.contains("mac") -> "macosx-$cpu"
        else -> "linux-$cpu"
    }
}
dependencies {
    runtimeOnly("me.friwi:jcef-natives-$jcefNativesPlatform:${libs.versions.jcefNatives.get()}")
}

// Development run with a separate data folder and the local dev control server:
// ./gradlew :app:runDev -PdataDir=<dir> [-PdevPort=8765]
tasks.register<JavaExec>("runDev") {
    group = "application"
    mainClass.set("com.lagradost.desktop.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs(
        "-Xmx3g",
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
        "-Xss4m",
        "-Dfile.encoding=UTF-8",
    )
    (project.findProperty("dataDir") as String?)?.let { jvmArgs("-Dcloudstream.data=$it") }
    (project.findProperty("devPort") as String?)?.let { jvmArgs("-Dcloudstream.devport=$it") }
}

// Link lab: real extension links through the Exo style fetch and the desktop player.
// ./gradlew :app:runLinkLab -Pargs="<dataDir> phisher,megix streamplay,cinestream 60 Inception|Breaking Bad"
tasks.register<JavaExec>("runLinkLab") {
    group = "verification"
    mainClass.set("com.lagradost.desktop.tools.LinkLab")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs("-Xmx3g", "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
    jvmArgs((project.findProperty("jvm") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList<String>())
    args = (project.findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
