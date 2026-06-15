plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.boardapp.annotate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.boardapp.annotate"
        // graphics-core low-latency front-buffer path needs Android 10 (API 29)+.
        // Most classroom interactive panels run Android 11–13, so 29 is a safe floor.
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-spike"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // ---- The latency-critical pieces ----
    // Front-buffered (wet ink) low-latency rendering.
    implementation("androidx.graphics:graphics-core:1.0.1")
    // Motion prediction (draws ink ~1 frame ahead of the finger/stylus).
    implementation("androidx.input:input-motionprediction:1.0.0-beta05")
}
