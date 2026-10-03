import com.codingfeline.buildkonfig.compiler.FieldSpec
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
}

/*
 * The official CloudStream library (plugin API + extractors), compiled for the desktop JVM.
 *
 * src/commonMain, src/jvmCommonMain and src/androidMain are the upstream sources (see
 * tools/sync-upstream). The Android platform implementations are compiled against the desktop
 * Android runtime (:android) so extensions observe the same behaviour as on the phone app.
 * src/desktopMain only replaces WebViewResolver with a native Chromium implementation.
 */
kotlin {
    jvm()
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
            }
        }

        commonMain.dependencies {
            api(libs.annotation)
            api(libs.jackson.module.kotlin)
            api(libs.jsoup)
            api(libs.kotlinx.atomicfu)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.io.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ksoup)
            api(libs.ktor.http)
            api(libs.nicehttp)
            api(libs.rhino)
            api(libs.bundles.cryptography)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                api(libs.kotlin.reflect)
                api(libs.newpipeextractor)
            }
        }

        jvmMain {
            dependsOn(jvmCommonMain)
            kotlin.setSrcDirs(listOf("src/androidMain/kotlin", "src/desktopMain/kotlin"))
            kotlin.exclude("**/network/WebViewResolver.android.kt")
            dependencies {
                api(project(":android"))
            }
        }
    }
}

buildkonfig {
    packageName = "com.lagradost.api"
    exposeObjectWithName = "BuildConfig"

    defaultConfigs {
        val localProperties = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField(FieldSpec.Type.STRING, "MDL_API_KEY", (System.getenv("MDL_API_KEY") ?: localProperties["mdl.key"]).toString())
        buildConfigField(FieldSpec.Type.STRING, "TRAKT_CLIENT_ID", (System.getenv("TRAKT_CLIENT_ID") ?: localProperties["trakt.id"]).toString())
    }
}
