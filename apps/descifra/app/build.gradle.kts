import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing and the Play licence key come from apps/descifra/keystore.properties or
// environment variables, never from the repository.
val secrets = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String? = secrets.getProperty(name) ?: System.getenv(name)

android {
    namespace = "com.cazadoxs.descifra"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cazadoxs.descifra"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SUPPORT_EMAIL", "\"${secret("DESCIFRA_SUPPORT_EMAIL") ?: ""}\"")
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"${secret("DESCIFRA_PLAY_LICENSE_KEY") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val path = secret("DESCIFRA_KEYSTORE")
            if (path != null) {
                storeFile = file(path)
                storePassword = secret("DESCIFRA_KEYSTORE_PASSWORD")
                keyAlias = secret("DESCIFRA_KEY_ALIAS")
                keyPassword = secret("DESCIFRA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("DESCIFRA_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
        }
    }

    lint {
        // Strings are inline Spanish on purpose: the app targets Spanish letters only.
        disable += setOf("HardcodedText", "MissingTranslation")
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.billing.ktx)
}
