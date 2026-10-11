// Root build used only with TOMA_CORE_ONLY=1 (see settings.gradle.kts): no Android plugin.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
