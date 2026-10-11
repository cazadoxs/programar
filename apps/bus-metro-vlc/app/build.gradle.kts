import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Secrets and ids never live in the repo: they come from keystore.properties
// (local, git-ignored) or environment variables (CI secrets).
val secrets = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String, default: String = ""): String =
    secrets.getProperty(name) ?: System.getenv(name) ?: providers.gradleProperty(name).orNull ?: default

android {
    namespace = "vlc.busmetro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cazadoxs.busmetrovlc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // Google's public test ids; real ones are injected at release time.
        manifestPlaceholders["admobAppId"] = secret("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
        buildConfigField("String", "BANNER_UNIT_ID", "\"${secret("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/9214589741")}\"")
        buildConfigField("String", "JCDECAUX_KEY", "\"${secret("JCDECAUX_KEY")}\"")
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    signingConfigs {
        create("release") {
            val store = secret("RELEASE_STORE_FILE")
            if (store.isNotEmpty()) {
                storeFile = rootProject.file(store)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("RELEASE_STORE_FILE").isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("MissingTranslation", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.play.billing)
    implementation(libs.play.ads)
    implementation(libs.play.ump)
    implementation(libs.play.location)
    implementation(libs.play.review)
    implementation(libs.maplibre)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
