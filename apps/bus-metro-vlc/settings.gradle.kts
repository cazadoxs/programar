pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CORE_ONLY") != "1") google()
        mavenCentral()
    }
}

rootProject.name = "BusMetroVLC"
include(":core")
// CORE_ONLY=1 lets the pure-Kotlin core build and test on machines without
// the Android SDK (the app module needs Google's Maven and the SDK).
if (System.getenv("CORE_ONLY") != "1") include(":app")
