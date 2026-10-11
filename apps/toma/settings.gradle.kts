pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Toma"

include(":core")
// `TOMA_CORE_ONLY=1 ./gradlew :core:test` runs the pure-Kotlin logic tests on
// machines without access to Google's Maven repository (no Android plugin).
if (System.getenv("TOMA_CORE_ONLY") == null) {
    include(":app")
} else {
    rootProject.buildFileName = "build-core.gradle.kts"
}
