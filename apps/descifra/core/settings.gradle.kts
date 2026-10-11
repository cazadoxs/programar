// Lets the pure-Kotlin core build and test on its own (`./gradlew -p core test`), without the
// Android SDK. Inside the full build this file is ignored and ../settings.gradle.kts is used.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "2.4.21"
        kotlin("plugin.serialization") version "2.4.21"
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "descifra-core"
