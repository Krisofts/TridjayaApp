import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.baselineprofile")
}

// Release signing credentials live outside version control in keystore.properties
// (see .gitignore) — never hardcode a keystore password in this build file.
val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.krisoft.tridjayaelektronik"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.krisoft.tridjayaelektronik"
        minSdk = 24
        targetSdk = 35
        versionCode = 82
        versionName = "2.71"

        // Gateway Rust tridjaya, deployed at tridjaya.com (HTTPS, no emulator/LAN
        // workaround needed since it's a public domain). Migrated 2026-07-13 from
        // tridjayaelektronik.tech, which now only serves an HTML redirect page.
        buildConfigField("String", "API_BASE_URL", "\"https://tridjaya.com/\"")

        // Penanda "ini bukan build produksi", dibaca UI untuk menampilkan badge BETA
        // di header. DUA jalur menyalakannya, karena "versi uji coba" di repo ini
        // ada dua bentuk yang berbeda:
        //
        //   1. build DEBUG  — otomatis (blok `debug` di bawah). Ini yang dipasang
        //      lewat kabel saat mengembangkan.
        //   2. `-Pbeta`     — RELEASE yang sengaja ditandai uji coba. Perlu karena
        //      APK yang dibagikan ke karyawan lewat APK_UPLOAD_DIR adalah build
        //      release bertanda tangan asli (debug tak bisa dipakai: kuncinya beda,
        //      Android menolak memasangnya di atas app terpasang) — jadi tanpa flag
        //      ini rilis percobaan tak punya cara membedakan diri dari rilis biasa.
        //
        // Default `false`: rilis normal tak boleh diam-diam mengaku beta.
        buildConfigField("boolean", "IS_BETA", if (project.hasProperty("beta")) "true" else "false")
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Build debug SELALU beta — ia tak pernah sampai ke tangan karyawan
            // sebagai app resmi, jadi tak ada kasus di mana penandanya salah.
            buildConfigField("boolean", "IS_BETA", "true")

            // Debug memakai API_BASE_URL default (https://tridjaya.com/) sama seperti
            // release. Untuk uji ke gateway lokal:
            //
            //   ./scripts/adb-reverse-watch.sh      (biarkan jalan di terminal lain)
            //   ./gradlew installDebug -PlocalApi
            //
            // Pakai skrip penjaga itu, JANGAN `adb reverse` sekali jalan: tunnelnya
            // tidak bertahan melewati kabel tersenggol, USB di-suspend saat HP sleep,
            // atau daemon adb restart — dan hilangnya cuma terlihat sebagai "app tidak
            // bisa terhubung", tanpa petunjuk bahwa penyebabnya ada di laptop.
            //
            // localhost sudah diizinkan cleartext di network_security_config.xml.
            //
            // Flag opt-in, BUKAN edit tangan di defaultConfig: cara lama menyuruh
            // mengubah baris yang dipakai release lalu mengandalkan orang untuk
            // ingat mengembalikannya sebelum commit. Rilis yang menembak
            // localhost tidak menimbulkan error apa pun saat di-build — cuma app
            // di lapangan yang tak bisa memuat apa-apa.
            //
            // `applicationIdSuffix` ikut DI DALAM flag supaya varian uji terpasang
            // BERDAMPINGAN dengan app produksi di HP yang sama. Tanpa itu, debug
            // (keystore SDK bawaan) bentrok tanda tangan dengan rilis terpasang →
            // INSTALL_FAILED_UPDATE_INCOMPATIBLE, dan satu-satunya jalan keluar
            // adalah uninstall app produksi: sesi login karyawan ikut hilang.
            // Debug build biasa (tanpa flag) TIDAK berubah sama sekali.
            if (project.hasProperty("localApi")) {
                // -PapiBaseUrl override: HP nyata di luar jangkauan USB/adb reverse
                // (mis. lewat tunnel Cloudflare) butuh URL publik, bukan localhost.
                val url = project.findProperty("apiBaseUrl")?.toString() ?: "http://localhost:4100/"
                buildConfigField("String", "API_BASE_URL", "\"$url\"")
                applicationIdSuffix = ".localapi"
                versionNameSuffix = "-localapi"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("androidx.core:core-ktx:1.15.0")
    // EXIF orientation saat kompres foto bukti indent (IndentCreateViewModel)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Secure token storage: encrypted DataStore (Android Keystore AES-GCM). security-crypto is
    // kept only for the one-time migration reading the legacy EncryptedSharedPreferences store.
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Dependency injection
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Local cache
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Paging
    implementation("androidx.paging:paging-runtime:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")

    // Penjadwal pengingat prospek mandek (push/ProspekReminder.kt) — periodik harian, selamat
    // dari reboot & force-stop tanpa receiver BOOT_COMPLETED atau izin exact-alarm Android 12+
    // yang dituntut AlarmManager. SENGAJA tanpa androidx.hilt:hilt-work: worker ini cuma butuh
    // dua singleton, jadi EntryPointAccessors sudah cukup dan tak menambah KSP processor
    // maupun memaksa TridjayaApplication mengimplementasi Configuration.Provider.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // XLSX export (Inventory "Export ke Excel") — lightweight pure-Java writer, no POI/reflection
    // baggage, small enough for Android; supports styled cells + embedded row images.
    implementation("org.dhatim:fastexcel:0.20.2")

    // Product photo thumbnails (Inventory list + detail flyer) — Coil 2.x (not 3.x: avoids the
    // multi-artifact network-engine split for a project this size). Disk+memory caching built in,
    // so scrolling the Inventory list doesn't refetch images on every recomposition.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Firebase Cloud Messaging (push approval izin/absen). Active only when a real
    // google-services.json is present (the plugin below is applied conditionally); otherwise the
    // dependency is inert (no default FirebaseApp). Remote Config dropped — update-check /
    // force-update is now driven by our own backend (`UpdateManager` → `/api/users/app-apk/meta`),
    // not Firebase.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Scan barcode serial number (PDI + Input SPK) — Google code scanner:
    // TANPA izin kamera (UI scanner disediakan Play Services, model di-download
    // on-demand), jauh lebih ringan daripada bundling CameraX+ML Kit.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Installs the bundled baseline profile on first run (removes cold-start/first-scroll JIT jank).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Consumes the profile produced by the :baselineprofile module and bundles it into the APK.
    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Apply the Google Services plugin only when a real Firebase config file has been added (mirrors how
// release signing is gated on keystore.properties) — the app still builds & runs without it, and the
// update system simply stays inert until Firebase is configured.
// `-PlocalApi` mengubah applicationId jadi ...localapi, dan google-services.json
// cuma memuat klien untuk package aslinya — plugin ini MENGGAGALKAN build kalau
// tak menemukan klien yang cocok ("No matching client found for package name").
// Dilewati saat flag aktif; FCM lalu inert (DeviceRepository.fetchFcmToken sudah
// menangani Firebase tak ter-inisialisasi), yang memang benar untuk varian uji:
// ia tak boleh ikut menerima push yang ditujukan ke app produksi.
if (rootProject.file("app/google-services.json").exists() && !project.hasProperty("localApi")) {
    apply(plugin = "com.google.gms.google-services")
}
