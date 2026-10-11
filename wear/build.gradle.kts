plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The watch app only rings the wake alarm the phone sends. Wear OS pairs the two by package name and
// signing key, so it has the phone's applicationId and is signed with the same release key.
android {
    namespace = "app.omnitask.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.omnitask"
        // Wear OS 3, the oldest a Galaxy Watch 4 or later runs.
        minSdk = 30
        targetSdk = 34
        val build = (System.getenv("OMNI_BUILD_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "0.2.$build"
    }

    val releaseKeystore = System.getenv("OMNI_KEYSTORE_FILE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("OMNI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OMNI_KEY_ALIAS")
                keyPassword = System.getenv("OMNI_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
