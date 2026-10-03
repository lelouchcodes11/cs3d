plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

/*
 * The official CloudStream "shared" module (compose theme, components and settings widgets).
 * src/commonMain is the upstream source. src/jvmMain holds the desktop actuals: settings are stored
 * in the same SharedPreferences file and keys as the Android app (see AndroidPreferenceStore).
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
            implementation(libs.coil.network.ktor3)
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.collections.immutable)
            implementation(project(":library"))
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
            implementation(project(":android"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.lagradost.cloudstream4.generated.resources"
    generateResClass = auto
}
