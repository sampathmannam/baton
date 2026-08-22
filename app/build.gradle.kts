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
        // v1.7.2: versionCode 28, versionName "1.7.2".
        // v1.7.1 fresh-eyes critique came back at 6.5/10.
        // v1.7.2 is a single-ship monolith that closes the new
        // P0 + P1 items and the two carry-over debts from the
        // v1.7.0 critique:
        //   P0-A (worry box "Review in 719163 days"): the
        //     synthetic fixture's reviewAtEpochDay values mapped
        //     to year 3995, so the Worry box rendered "Review
        //     in 719163 days (3995-08-21)" on the default Today
        //     view. v1.7.2 caps the days display to year-only
        //     when >365 days out, and tightens the fixture so
        //     the dated worries use days 3..21 from today.
        //   P1-B (Home last row clipped): when a person row
        //     has no designation + station, the inner Column
        //     collapses to just the name TextView. On a 7-row
        //     initial viewport the 7th row's name was clipped
        //     to h=14. v1.7.2 adds a minHeight=88.dp on the
        //     PersonRow so empty-subtitle rows are still
        //     readable.
        //   P1-C (PersonDetail header duplicated): the
        //     TopAppBar title showed the person's name AND the
        //     body's PersonHeader showed the name again. The
        //     TopAppBar is now just a chevron back icon; the
        //     body's PersonHeader is the single source of
        //     truth.
        //   P1-D (Today search person = no-op): tapping a
        //     person result on Today did nothing (comment said
        //     "search is read-only on Today"). Now routes to
        //     onOpenPerson, matching the Home behaviour.
        //   P1-F (Settings "Free" badge): the TagKind.FREE
        //     section header rendered as the bare word "Free"
        //     — a developer term that leaked into the user-
        //     facing UI. Now renders as "Your tags" for the
        //     FREE kind.
        //   Debt-1: TagPicker.kt's Color(0xFF6F6F6F) literal
        //     replaced with BatonColors.KindNeutralLight.
        //   Debt-2: Erase all data confirmation dialog now
        //     requires the user to type "ERASE" before the
        //     confirm button enables (was: single-tap
        //     irreversible action).
        // Bugfix release; no public-surface changes (version
        // bump because the v1.7.1 → v1.7.2 changes are
        // user-visible: worry box dates, home row height,
        // person-detail header, today search, settings tag
        // section label, and erase confirmation flow).
        // v1.7.1: versionCode 27, versionName "1.7.1".
        // v1.7.0 fresh-eyes critique came back at 4.5/10
        // (down from v1.6.8's 6.5 because the v1.6.8 nav
        // fix was incomplete). v1.7.1 is a single-ship
        // monolith that closes every P0 and P1 from that
        // critique:
        //   P0 nav (H1+H2, H3, H4): the system 3-button
        //     gesture-nav hit area overlaps the bottom
        //     NavigationBar — Today/Settings taps on the
        //     Home screen were captured by the system
        //     recents button and the user was dropped into
        //     a background app (BSA for Dummies on
        //     ZD2232FCR5). v1.6.4's 48dp extra bottom
        //     padding was JUST barely enough; the
        //     extended touch area reaches another 30-50dp
        //     above the visible buttons. Bumped to 96dp.
        //   P1 data (T1, T4, T5, P1): synthetic data
        //     duplicates ("B. Srinivas" x2, "Whitespace
        //     Edge" x2) and placeholder strings
        //     (AAAA..., XXX..., "Station-with-a-very-
        //     long-name-...") dominated the top of the
        //     People list. Renamed the duplicates to be
        //     unique and demoted the placeholders so the
        //     top of the list shows realistic names.
        //   P1 UI (T3, Q1, St1): the count badge in
        //     PersonRow now shows " 3 open" (visible
        //     label, not just a digit). The NoteBar
        //     capture buttons (Photo, Voice) now have
        //     visible "Photo" / "Voice" labels under
        //     the icons. The Settings sheet now has a
        //     visible Close X button in the top-right
        //     (was: scrim-tap / swipe-down only).
        // Bugfix release; no public-surface changes.
        // v1.7.0: versionCode 26, versionName "1.7.0". Closes
        // the three gaps flagged by the v1.6.8 fresh-eyes
        // critique (6.5/10):
        //   A. Real-user test: 1-page structured handoff in
        //      docs/v1.7.0_user_test.md (the test itself is a
        //      30-minute observation, not code).
        //   B. Backup story: encrypted local export/import
        //      was already built (VaultExporter/Importer with
        //      Argon2id KDF + AES-256-GCM). v1.7.0 is the first
        //      release to actually exercise it on a real
        //      device round trip (export → wipe → import →
        //      verify counts match). Previously: code path
        //      existed since v1.5.0 but no APK on a phone had
        //      used it.
        //   C. Search: bar + debounce + FTS4 + person filter
        //      were already wired into Home + Today. v1.7.0
        //      closes the remaining gap — tapping an instruction
        //      in search results now opens the InstructionDetailSheet
        //      directly (used to navigate to the person). Lifts
        //      InstructionDetailSheet into ui/components/ so
        //      Home + Today share one implementation.
        // No new public surface; ship as v1.7.0 (not v1.6.9)
        // because C changes the search-result tap behaviour
        // — it's a user-visible contract change worth its own
        // minor bump.
        // v1.7.4: versionCode 30, versionName "1.7.4". Closes
        // the UI critique of v1.7.3's People search result:
        // P1-A unbounded subtitle Text that broke hyphenated
        // words mid-character ("mobi|le-screens") and
        // truncated without ellipsis ("...Waran"); P1-C
        // last row clipped behind the Quick note bar.
        // v1.7.3: versionCode 29, versionName "1.7.3". Closes
        // the v1.7.3 fresh-eyes critique: P0-A stale seeded
        // data on upgrade, P1-B test-data placeholders, P1-C
        // Export CSV/JSON radio, P1-D M3 NavigationBarItem
        // clickable=false on active tab, P2-A quiet-contacts row
        // clip, P2-C (unknown person) header clip.
        // v1.8.0: versionCode 31, versionName "1.8.0". Closes
        // the production-readiness Phase 1 P0s + P1s +
        // Phase 2 P0 #1..#7: backup+restore, dedup,
        // retention, audit chain, branding, eFIR bridge,
        // role model. Phase 1 audit found 3 P0 + 3 P1 were
        // pre-existing closures; only 5 P0 + 3 P1 needed
        // fresh work.
        // v1.9.4: versionCode 36, versionName "1.9.4".
        // "Compact cards" release. The v1.9.3 fix
        // removed the visible gap below the bottom nav,
        // but the DecayRow card layout was still
        // ~292px tall because the v1.9.2 fix stacked
        // the "Mark recent" button above the
        // "Quiet a while" pill in a right-aligned
        // Column. On a 1264x2780 device, only 3 full
        // DecayRow cards + a partial 4th fit on the
        // visible Today screen, with a partial card
        // clipping at the bottom. The user reported
        // "UI should use the screen properly". v1.9.4
        // flips the right-side controls back to
        // horizontal siblings (the v1.8.0 layout)
        // while keeping the v1.9.2 `maxLines` caps on
        // the name (2 lines) + designation (1 line),
        // both ellipsized. The name + designation
        // + days-quiet column is bounded to ~3 lines,
        // so a ~420dp right column leaves ~800dp for
        // the name and designation — enough for any
        // realistic Indian-police name. Card height
        // drops from ~292px back to ~210px, the
        // screen shows 4-5 full DecayRow cards
        // instead of 3, and the bottom of the scroll
        // is no longer a partial card. Bugfix release;
        // no public-API or schema changes. versionCode
        // 35 -> 36. The third drive-verify polish
        // in this cycle (v1.9.2: stack controls;
        // v1.9.3: remove 192dp gap; v1.9.4: keep
        // stack fix, drop the visual gap; flip back
        // to horizontal controls because the stack
        // wasted too much vertical space).
        versionCode = 36
        versionName = "1.9.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        // v1.8.0 (PROD-READINESS-P2-#6): the brand-name and
        // brand-department build-config fields. A pilot
        // build (e.g. TNeGA / CCPS) overrides these via
        // `gradle -Pbrand.name="TNeGA CCPS" -Pbrand.dept="..."`;
        // the defaults are the R&D "Kaavalan note" brand
        // so a plain `./gradlew assembleDebug` produces
        // the v1.8.0 default. The Settings "About" row
        // reads BRAND_NAME so the user-visible label
        // tracks the build that produced the APK.
        buildConfigField(
            "String",
            "BRAND_NAME",
            "\"${project.findProperty("brand.name") ?: "Kaavalan note"}\"",
        )
        buildConfigField(
            "String",
            "BRAND_DEPARTMENT",
            "\"${project.findProperty("brand.dept") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "BRAND_ICON",
            "\"${project.findProperty("brand.icon") ?: "ic_launcher"}\"",
        )
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

    // v1.9.1 (PROD-READINESS-P3-P1-#5 + honest
    // deployability): per-ABI splits for release builds.
    // The v1.9.0 universal release APK was 71.3 MB
    // (R8 + shrinkResources enabled, but all four ABIs
    // arm64-v8a / armeabi-v7a / x86 / x86_64 bundled).
    // Most of the size is the per-ABI lib/ subtree
    // (SQLCipher, OkHttp, ktor, supabase-kt native
    // shims, CameraX, ML Kit). Splitting per ABI drops
    // each per-architecture APK to ~35-40 MB on disk;
    // the App Bundle (.aab) lets Google Play deliver
    // the right ABI per device, so the user-installed
    // size is the per-ABI number.
    //
    // v1.9.1 trade-off: the splits config is added
    // here so `./gradlew :app:assembleRelease` emits
    // 4 APKs (one per ABI). The full App Bundle
    // (`./gradlew :app:bundleRelease` -> .aab) is
    // documented in `docs/play-store-listing.md` as
    // the Play-Store submission artifact; the splits
    // here are also useful for sideloading a single
    // per-ABI APK to a tester. v1.9.1 does NOT change
    // the debug build (still universal; debug never
    // ships to users).
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
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
    // v1.8.0 (PROD-READINESS-P0-#7): the androidTest source
    // set needs the Compose UI test rule + the Hilt testing
    // annotation. The previous build was missing these — the
    // existing M0AcceptanceTest.kt and VaultEndToEndTest.kt
    // imported them but did not compile. Adding the deps here
    // unblocks both the existing tests and the new
    // CaptureHappyPathTest.
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
}
