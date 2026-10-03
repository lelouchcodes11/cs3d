pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

rootProject.name = "CloudStreamDesktop"

// library  : official CloudStream plugin API + extractors (upstream sources, JVM build)
// shared   : official CloudStream compose components/theme (upstream sources, JVM build)
// android  : desktop implementation of the Android APIs used by CloudStream and its extensions
// app      : the CloudStream desktop application
include(":android", ":library", ":shared", ":app")
