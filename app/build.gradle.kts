plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.omnitask"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.omnitask"
        minSdk = 26
        targetSdk = 35
        // CI passes the run number, so every release installs over the last one.
        val build = (System.getenv("OMNI_BUILD_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "0.2.$build"
    }

    // The release key never lives in the repo: CI writes it from GitHub secrets.
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.glance:glance-appwidget:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
