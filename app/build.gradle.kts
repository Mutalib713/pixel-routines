plugins {
    id("com.android.application") // AGP 9: Kotlin support is built in
    id("org.jetbrains.kotlin.plugin.compose")
    // Reads app/google-services.json (git-ignored) so Firebase knows which project to talk to.
    id("com.google.gms.google-services")
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

    val keystoreFile = rootProject.file("routines.keystore")
    val hasKeystore = keystoreFile.exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = "routines2026"
                keyAlias = "routines"
                keyPassword = "routines2026"
            }
        }
    }

    buildTypes {
        release {
            // material-icons-extended alone is ~20 MB of unused vectors; R8 strips
            // everything we never reference and takes the APK from ~45 MB to ~12 MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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

    // On-device ML Kit Text Recognition for Routine Lens. The BUNDLED model (inside the APK),
    // not the Play-services one: that downloads its model on first use, so the first scan
    // could fail offline. Same com.google.mlkit.vision.text API.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Routine AI online: Gemini through Firebase AI Logic, guarded by App Check.
    // The BoM keeps all Firebase library versions in step.
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-ai")
    // App Check: debug provider in debug builds only, Play Integrity in release builds.
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
}
