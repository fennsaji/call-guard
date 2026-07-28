import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Read signing credentials from key.properties (local or CI-generated).
// File is gitignored; CI creates it from secrets before invoking Gradle.
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

// Read local.properties for dev secrets (gitignored).
// CI injects the same keys via -P flags or environment, picked up by project.findProperty().
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) load(localPropertiesFile.inputStream())
}
fun localOrProject(key: String, default: String = ""): String =
    localProperties.getProperty(key) ?: project.findProperty(key) as String? ?: default

android {
    namespace = "com.fenn.callshield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fenn.callshield"
        minSdk = 29
        targetSdk = 35
        // versionCode / versionName can be overridden via -P flags in CI
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 2
        versionName = (project.findProperty("versionName") as String?) ?: "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // HMAC static salt — bundled in binary, not a secret
        buildConfigField("String", "HMAC_SALT", "\"${localOrProject("HMAC_SALT", "callshield-v1-salt-2024")}\"")

        // Promo codes — store only SHA-256 hashes; raw codes are never compiled into the APK
        // Generate hash: printf '%s' "YOUR_CODE" | shasum -a 256 | awk '{print $1}'
        // Expiry is epoch-milliseconds (Long); defaults = 2026-09-30 23:59:59 UTC
        buildConfigField("String", "PROMO_CODE_PRO_HASH",   "\"${localOrProject("PROMO_CODE_PRO_HASH")}\"")
        buildConfigField("long",   "PROMO_CODE_PRO_EXPIRY", "${localOrProject("PROMO_CODE_PRO_EXPIRY", "1790812799000")}L")

    }

    signingConfigs {
        create("release") {
            // Values come from key.properties; build proceeds unsigned if file is absent
            keyAlias     = keystoreProperties["keyAlias"]     as? String ?: ""
            keyPassword  = keystoreProperties["keyPassword"]  as? String ?: ""
            storeFile    = (keystoreProperties["storeFile"]   as? String)?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as? String ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Material Components — provides Theme.Material3.DayNight.NoActionBar for window chrome
    implementation(libs.material)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // kotlinx.serialization JSON runtime — used by BackupManager/ScamDigestSeeder
    implementation(libs.ktor.serialization.kotlinx.json)

    // Coroutines
    implementation(libs.coroutines.android)

    // Google Play Billing
    implementation(libs.billing.ktx)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // QR code generation (ZXing core — no camera required)
    implementation(libs.zxing.core)

    // QR scanning — CameraX + ML Kit Barcode
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.junit.android)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
