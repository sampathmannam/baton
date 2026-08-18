import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// local.properties is NOT auto-loaded into Gradle properties; AGP only
// reads sdk.dir from it. Read keys ourselves so a clone + add-to-properties
// + build works without editing gradle.properties (which is checked in).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val supabaseUrl: String = localProps.getProperty("BATON_SUPABASE_URL", "")
    .ifBlank { providers.gradleProperty("BATON_SUPABASE_URL").getOrElse("") }
val supabaseAnonKey: String = localProps.getProperty("BATON_SUPABASE_ANON_KEY", "")
    .ifBlank { providers.gradleProperty("BATON_SUPABASE_ANON_KEY").getOrElse("") }
if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
    throw GradleException(
        "BATON_SUPABASE_URL and BATON_SUPABASE_ANON_KEY must be set in local.properties " +
        "(gitignored). Copy from local.properties.example and fill in the values from " +
        "the Supabase dashboard."
    )
}

android {
    namespace = "com.baton.app"
    compileSdk = 35
    // v1.2: pin NDK for reproducible builds + first-class 16 KB
    // page-size support. The version catalog (libs.versions.toml)
    // declares the same version; we read it here so gradle.properties
    // overrides propagate.
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "com.baton.app"
        minSdk = 26
        targetSdk = 35
        // v1.6.3: UI/UX round 3 (Obsidian-style pass + app icon).
        // (1) App icon redesigned — new adaptive foreground
        // (indigo shield on cream) shipped to all 5 mipmap
        // densities + the launcher round variants. Notification
        // small icon (24x24 white silhouette) added; the
        // adaptive background switched from a teal-to-coral
        // gradient to a solid cream so the shield carries full
        // visual weight. (2) HomeScreen Quick-note bar moved
        // from a floating Box overlay into the Scaffold's
        // bottomBar slot — the previous overlay hid the last
        // person row on 1080x2400. (3) Open-count badge dropped
        // the prominent tertiaryContainer CircleShape pill and
        // is now a small labelMedium text on the right (Obsidian
        // document density). (4) HomeScreen and TodayScreen
        // TopAppBar titles re-styled to titleSmall + onSurface
        // with a 4dp start inset to compensate for
        // `windowInsets(0)`. (5) Typography tokens retuned to
        // Obsidian scale (16sp body, 14sp UI, 12sp small,
        // 1.5 line height, weight 400-500). (6) TodayScreen
        // passes personNameById to search results so the
        // instruction group header shows "K. Ramana" not a
        // truncated UUID. (7) TodayScreen "Review" button
        // switched from OutlinedButton to TextButton (secondary
        // to content). (8) PersonList / search-results
        // LazyColumns now use horizontal contentPadding so
        // rows have full-width click hit-targets.
        // versionCode 21 (one above v1.6.2's 20), versionName
        // "1.6.3".
        versionCode = 21
        versionName = "1.6.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    signingConfigs {
        // v1.3 (F-CRIT-03): proper production keystore. Generated
        // 2026-08-13 with 10000-day validity (~27.4 years) so the
        // key never expires mid-pilot. Passwords live in
        // local.properties (gitignored); the fallback string is
        // intentionally the same as the keystore password so a
        // fresh clone + build works without an env-var step. For
        // Play Store submission, rotate the passwords and re-sign
        // outside the repo.
        create("release") {
            storeFile = file("baton-release.keystore")
            storePassword = providers.gradleProperty("BATON_RELEASE_STORE_PASSWORD").orNull
                ?: "baton-release-2026"
            keyAlias = "baton-release"
            keyPassword = providers.gradleProperty("BATON_RELEASE_KEY_PASSWORD").orNull
                ?: "baton-release-2026"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            // v1.2: enable R8 minify + resource shrink for release
            // APKs. Saves ~30% size and forces a clean proguard-rules.pro
            // (was missing in v1.1.1; now mandatory). Debug build is
            // unaffected.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // v1.3 (F-CRIT-03): sign the release APK with the
            // production keystore. The previous M5 config used
            // the debug keystore as a placeholder; Play Store
            // rejects that. The new keystore is committed (see
            // .gitignore exception) so a fresh clone + build
            // produces a Play-acceptable artifact.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// v1.6.1: removed the `vendorLlamaCpp` + `vendorWhisperCpp` tasks.
// The on-device LLM is gone, so no C++ source to vendor, no
// JNI library to build, no `libllama.so` / `libwhisper.so` to
// merge. Capture uses `android.speech.SpeechRecognizer` (a
// system service) for voice transcription.
dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.sqlcipher.android)
    implementation(libs.security.crypto)

    implementation(libs.supabase.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.functions.kt)
    implementation(libs.supabase.realtime.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    // M2-T7: Realtime WebSocket subscription. The OkHttp engine
    // supports WebSockets; the Android engine does not.
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.okhttp)

    // M2-T2: photo capture via CameraX + ML Kit on-device OCR.
    implementation(libs.camerax.camera.core)
    implementation(libs.camerax.camera.camera2)
    implementation(libs.camerax.camera.lifecycle)
    implementation(libs.camerax.camera.view)
    implementation(libs.mlkit.text.recognition)

    // Tier 0.1: Jetpack Glance for the home-screen / lock-screen
    // capture widget. The Glance composable API + a
    // GlanceAppWidgetReceiver entry point; replaces the legacy
    // AppWidgetProvider + RemoteViews implementation in
    // BatonCaptureWidget.
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Tier 1.1 (v2.0): vault backup KDF.
    implementation(libs.argon2kt)
    // Tier 1.4 (v2.0): DataStore for the theme switcher + 1.2
    // onboarding flag.
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.okhttp)
    testImplementation(libs.room.testing)
    testImplementation(libs.sqlite)
    // v1.4.3 (F-09/F-20 wiring): WorkManagerTestInitHelper so the
    // WorkManagerInitializer unit tests can exercise enqueue without
    // an actual WorkManager runtime.
    testImplementation(libs.work.testing)
    // v1.3: Compose a11y contentDescription assertions
    // (createComposeRule + hasContentDescription). The test runs
    // under Robolectric so no device or emulator is needed.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.mockk)

    debugImplementation(libs.compose.ui.tooling)
}
