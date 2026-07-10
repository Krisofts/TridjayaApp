plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

/**
 * Baseline Profile *producer* module. It drives the real app through its startup path on a device
 * and records which classes/methods to AOT-compile ahead of time, then feeds the result back to
 * `:app` (via the `baselineProfile(project(":baselineprofile"))` dependency). The profile is bundled
 * into the release APK and installed on first run by `androidx.profileinstaller`, removing the
 * cold-start / first-scroll JIT jank.
 *
 * Regenerate after meaningful UI/startup changes:
 *   gradlew :app:generateReleaseBaselineProfile
 */
android {
    namespace = "com.krisoft.tridjayaelektronik.baselineprofile"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        // Baseline profile generation needs API 28+; the physical test device is API 33.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app under test.
    targetProjectPath = ":app"
}

// Generate against the physical connected device (no Gradle Managed Device is configured in this
// CLI-only environment).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}
