import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: keystore.properties (local, git-ignored) or environment
// variables (CI secrets). Without either, release builds stay unsigned.
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key) ?: System.getenv(env)

// Base64 RSA licence key from Play Console (Monetise > Monetisation setup).
// Purchases are verified against it; release builds refuse to build without it.
val playLicenseKey: String =
    (signingProps.getProperty("playLicenseKey") ?: System.getenv("ESCANER_PLAY_LICENSE_KEY") ?: "").trim()

gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { it.project == project && (it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease")) }
    if (buildsRelease && playLicenseKey.isEmpty()) {
        throw GradleException(
            "Falta la clave de licencia de Google Play. Añade playLicenseKey a keystore.properties " +
                "o la variable ESCANER_PLAY_LICENSE_KEY (ver publicar/PUBLICAR.md).",
        )
    }
}

android {
    namespace = "com.cazadoxs.escanerlimpio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cazadoxs.escanerlimpio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenseKey\"")
    }

    signingConfigs {
        val storeFilePath = signingValue("storeFile", "ESCANER_KEYSTORE_PATH")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "ESCANER_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ESCANER_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ESCANER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("MissingTranslation")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.billing)
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
