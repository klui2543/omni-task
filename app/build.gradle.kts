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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // The tests in sharedTest run twice in CI: on the JVM (Robolectric for the screens) and on an Android
    // emulator, which catches what only Android's runtime trips on. Tests that read the sources stay JVM only.
    // The shared module's tests run on the emulator too, since Android reads regexes its own way (ICU).
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("../shared/src/commonTest/kotlin")
    }

    // A crash prints in full in the build log.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStackTraces = true
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // Google sign-in for Drive, and HTTP for Drive's API (the Omni folder, see DriveLink).
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
