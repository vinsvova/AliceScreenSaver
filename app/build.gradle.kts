plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Номер сборки GitHub Actions становится versionCode, чтобы новые APK
// устанавливались поверх старых без удаления.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.vinsvova.alicescreensaver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vinsvova.alicescreensaver"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // Один постоянный ключ для всех сборок: так обновления ставятся поверх.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/app.keystore")
            storePassword = "alicescreensaver"
            keyAlias = "app"
            keyPassword = "alicescreensaver"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
