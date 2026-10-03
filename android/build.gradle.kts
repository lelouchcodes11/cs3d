plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Desktop implementation of the subset of the Android SDK used by CloudStream and its extensions.
// Package names and JVM signatures intentionally match the Android SDK so that extension bytecode
// (converted from dex) links against these classes unchanged. Views are rendered with Compose,
// graphics with Skia and WebViews with JCEF (Chromium).
kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-unchecked"))
}

dependencies {
    api(libs.annotation)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.swing)
    api(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material", module = "material")
    }
    api(libs.compose.runtime)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.ui)
    api(libs.lifecycle.viewmodel)
    api(libs.lifecycle.runtime)
    api(libs.kxml2)
    api(libs.jcefmaven)
    api(libs.json)
    api(libs.jsoup)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    // dex -> jvm bytecode for extensions (dalvik.system class loaders)
    implementation(libs.dex.translator)
    implementation(libs.dex.tools)
    implementation(libs.jmdns)
    testImplementation(libs.kotlin.test)
}

// Prints the resolved runtime classpath (used by tools/ApiChecker)
tasks.register("printRuntimeClasspath") {
    val cp = configurations.named("runtimeClasspath")
    doLast { cp.get().resolve().forEach { println("CP:" + it.absolutePath) } }
}
