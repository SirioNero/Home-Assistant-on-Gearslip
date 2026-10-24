plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Su GitHub ogni compilazione ha un numero crescente: così ogni APK nuovo si installa sopra il precedente.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// La chiave di firma NON sta nel repository: la compilazione su GitHub la ricrea dal secret PLANCIA_KEYSTORE.
// Senza chiave (per esempio chi contribuisce, o una build sul proprio computer) si usa la chiave di debug di Android.
val planciaKeystore = file("plancia.keystore")
val hasPlanciaKey = planciaKeystore.exists()

android {
    namespace = "it.plancia.ha"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.plancia.ha"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        if (hasPlanciaKey) {
            create("plancia") {
                storeFile = planciaKeystore
                storePassword = System.getenv("PLANCIA_KEYSTORE_PASSWORD") ?: "plancia-android"
                keyAlias = "plancia"
                keyPassword = System.getenv("PLANCIA_KEYSTORE_PASSWORD") ?: "plancia-android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasPlanciaKey) signingConfig = signingConfigs.getByName("plancia")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasPlanciaKey) signingConfig = signingConfigs.getByName("plancia")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
