import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}
fun releaseConfigValue(propKey: String, envKey: String): String? =
    releaseKeystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStorePath = releaseConfigValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = releaseConfigValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseConfigValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseConfigValue("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = !releaseStorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    file(releaseStorePath).isFile

android {
    namespace = "com.mylittleblover.mkwplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mylittleblover.mkwplayer"
        minSdk = 26
        targetSdk = 35
        // Beta 0.1.6 (new Beta X.Y.Z version format): unified release combining MKW v1.8.0 transitions & 202 connections, 13-playlist + in-game Free Roam engine, pure Free Roam routes, unlock quiz, 7 character theme drawer, track markers, Flow Editor presets & WAV/FLAC export, seek-silence & beta01 5.1 loudness fixes, Koopa B / Cinema / SnowMountain alignment, Rainbow melody switch removal, UI/motion alignment, and Staff Credits dynamic course-themed / split-dual / flowing Rainbow Road progress bar.
        versionCode = 34
        versionName = "Beta 0.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Fixed debug keystore so repeated installs keep one identity.
        // This is a TEST identity only and must never be used for release.
        create("fixedDebug") {
            storeFile = rootProject.file("config/signing/mkwplayer-debug.p12")
            storePassword = "android"
            keyAlias = "mkwplayerdebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "27.0.12077973"

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.google.oboe:oboe:1.10.0")
    // MediaSessionCompat + MediaStyle notifications for background playback.
    implementation("androidx.media:media:1.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    // org.json is part of the Android framework but absent from the JVM test
    // classpath, so this real implementation is needed for unit tests only.
    testImplementation("org.json:json:20250107")

    androidTestImplementation("junit:junit:4.13.2")
    // The Compose BOM is scoped per configuration, so androidTest needs its own.
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
