plugins {
    id("com.android.application") // AGP 9: Kotlin support is built in
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mosman.routines"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mosman.routines"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("routines.keystore")
            storePassword = "routines2026"
            keyAlias = "routines"
            keyPassword = "routines2026"
        }
    }

    buildTypes {
        release {
            // material-icons-extended alone is ~20 MB of unused vectors; R8 strips
            // everything we never reference and takes the APK from ~45 MB to ~12 MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    // Real Material Symbols for every trigger/action/routine icon (no emoji).
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // Geofencing + "use my current location" in the place picker.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // In-app map for picking a place — OpenStreetMap, needs no API key.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Shizuku: ADB-level shell for the restricted Wi-Fi/Bluetooth/airplane toggles.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
