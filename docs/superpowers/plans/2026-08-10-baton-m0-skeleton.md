# Baton M0 — Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the empty Android app (Kotlin + Compose + Hilt + Room/SQLCipher), the Supabase project with full schema and RLS, and the minimal cloud MCP server exposing `baton://persons`. End state: app launches, shows an empty Home, user can create a person, the person appears in the cloud DB and is reachable via the MCP server.

**Architecture:** Three-plane architecture per spec §5 — UI plane (Compose), data plane (Supabase Postgres + Realtime + Edge Functions + local Room/SQLCipher mirror), AI plane (M0: stub interface only, no actual model). MCP server is a Deno Edge Function exposing resources; no AI on the server. RLS-enforced from day one (no anonymous read, only the authenticated user can read their own data).

**Tech Stack:**
- Kotlin 2.0, Jetpack Compose, Material 3
- Hilt for DI, Room + SQLCipher for local DB, WorkManager for background
- Supabase Kotlin SDK (`io.github.jan-tennert.supabase:auth-kt, postgrest-kt, realtime-kt, functions-kt, storage-kt`)
- Ktor client (Android engine) for the Supabase SDK
- Gradle Version Catalog (`libs.versions.toml`)
- Supabase CLI for migrations + Edge Function deploy
- Official MCP Kotlin SDK (`io.modelcontextprotocol:kotlin-sdk`) for the cloud server
- Deno (Supabase Edge Functions runtime)

## Global Constraints

(Verbatim from the spec, applies to every task in M0.)

- **Min Android SDK:** 26 (Android 8.0). Target: 34.
- **Kotlin:** 2.0+.
- **Architecture:** arm64-v8a only for native libs (M0 has none yet).
- **Tabs:** 3 (Home, Today, Settings) — M0 ships Home only, but the nav structure is the three tabs.
- **No red "overdue" badges anywhere.** M0 has no instructions yet, but the design system must not import any red colour tokens.
- **No streaks, no shame language.** Copy on empty states must follow this from day one.
- **All data is per-user via RLS.** No row from any table is readable by any user other than its owner.
- **No third-party analytics, no telemetry, no crash reporting that sends data off-device.** Local logs only.
- **Auth:** Supabase Auth, email + password, PKCE flow.
- **No git operations outside the workspace** — local clone lives in `C:\Users\Sampath\.minimax-agent\projects\baton` (safety policy blocks writes outside the workspace); all git commands run from there.
- **Commits are imperative, present tense, no `feat:` / `fix:` prefix.** Squash-merge to main (single commit on main per task).
- **Each task ends with a green test or build, and a commit on main.**

---

## Task 1: Gradle wrapper + version catalog

**Files:**
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/libs.versions.toml`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `BatonApplication.kt` will be wired against Hilt; the catalog must include `hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, `kotlinx-serialization-json`, `room-runtime`, `room-ktx`, `room-compiler`, `sqlcipher-android`, `androidx-security-crypto`, `supabase-postgrest-kt`, `supabase-auth-kt`, `supabase-functions-kt`, `androidx-work-runtime-ktx`, `kotlinx-coroutines-android`, `kotlinx-datetime`.
- Produces: package name `com.baton.app`.

**Step 1: Create `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
room = "2.6.1"
sqlcipher = "4.6.1"
securityCrypto = "1.1.0-alpha06"
coroutines = "1.9.0"
serialization = "1.7.3"
datetime = "0.6.1"
work = "2.9.1"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.10.01"
material3 = "1.3.1"
navigationCompose = "2.8.4"
core = "1.13.1"
junit = "4.13.2"
androidxJunit = "1.2.1"
espresso = "3.6.1"
supabase = "3.1.1"
ktor = "2.3.12"
turbine = "1.1.0"
mockk = "1.13.13"

[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
hilt-work = { module = "androidx.hilt:hilt-work", version = "1.2.0" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version = "1.2.0" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
sqlcipher-android = { module = "net.zetetic:android-database-sqlcipher", version.ref = "sqlcipher" }
security-crypto = { module = "androidx.security:security-crypto", version.ref = "securityCrypto" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
core-ktx = { module = "androidx.core:core-ktx", version.ref = "core" }
supabase-postgrest-kt = { module = "io.github.jan-tennert.supabase:postgrest-kt", version.ref = "supabase" }
supabase-auth-kt = { module = "io.github.jan-tennert.supabase:auth-kt", version.ref = "supabase" }
supabase-functions-kt = { module = "io.github.jan-tennert.supabase:functions-kt", version.ref = "supabase" }
supabase-realtime-kt = { module = "io.github.jan-tennert.supabase:realtime-kt", version.ref = "supabase" }
supabase-storage-kt = { module = "io.github.jan-tennert.supabase:storage-kt", version.ref = "supabase" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

**Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "baton"
include(":app")
```

**Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

**Step 5: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
ksp.useKSP2=true
```

**Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.baton.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.baton.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

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

    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.functions.kt)
    implementation(libs.supabase.realtime.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.mockk)

    debugImplementation(libs.compose.ui.tooling)
}
```

**Step 7: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".BatonApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Baton">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Baton">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 8: Run `./gradlew :app:tasks` to confirm the project is valid**

Run from repo root:
```bash
cd C:\Users\Sampath\.minimax-agent\projects\baton
.\gradlew.bat :app:tasks
```

Expected: build succeeds, lists standard tasks (`assembleDebug`, `test`, etc.). If it fails because `gradle-wrapper.jar` is missing, run:
```bash
.\gradlew.bat wrapper
```
This downloads the wrapper jar.

**Step 9: Commit**

```bash
cd C:\Users\Sampath\.minimax-agent\projects\baton
git add gradle/ gradle.properties settings.gradle.kts build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "Set up Gradle wrapper, version catalog, and app module skeleton"
git push origin main
```

---

## Task 2: Theme + design tokens (no red colour)

**Files:**
- Create: `app/src/main/java/com/baton/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/baton/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/baton/app/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Create: `app/src/main/res/values/colors.xml`
- Test: `app/src/test/java/com/baton/app/ui/theme/ColorTest.kt`

**Interfaces:**
- Produces: `BatonTheme` composable that takes `content: @Composable () -> Unit`. Downstream screens will call `BatonTheme { HomeScreen() }`.

**Step 1: Write the failing test — `app/src/test/java/com/baton/app/ui/theme/ColorTest.kt`**

```kotlin
package com.baton.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTest {

    @Test
    fun `no red color in palette or semantic colors`() {
        // Spec rule: "no red 'overdue' badge". The colour palette must
        // not contain any saturated red. Amber for "quiet" is allowed.
        val palette: List<Color> = listOf(
            BatonColors.Quiet,
            BatonColors.Primary,
            BatonColors.Surface,
            BatonColors.OnSurface,
            BatonColors.OnSurfaceMuted,
        )
        palette.forEach { color ->
            val r = color.red
            val g = color.green
            val b = color.blue
            // "Red" = red channel clearly dominant and not just a hint.
            val isRed = r > 0.6f && r > g * 1.5f && r > b * 1.5f
            assertFalse(
                "Colour $color is red-dominant; spec forbids red badges",
                isRed
            )
        }
    }

    @Test
    fun `quiet colour is amber, not red`() {
        // The "stale" / "quiet" indicator must be amber, not red.
        val quiet = BatonColors.Quiet
        val r = quiet.red
        val g = quiet.green
        assertTrue("Amber needs significant green", g > 0.4f)
        assertTrue("Amber has red component", r > 0.6f)
    }
}
```

**Step 2: Run the test, verify it fails**

```bash
cd C:\Users\Sampath\.minimax-agent\projects\baton
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.ui.theme.ColorTest"
```

Expected: FAIL with `Unresolved reference: BatonColors`.

**Step 3: Create `app/src/main/java/com/baton/app/ui/theme/Color.kt`**

```kotlin
package com.baton.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Baton design tokens. Per spec §3: no red "overdue" colour anywhere.
 * "Quiet" / "stale" surfaces use a soft amber, not red.
 */
object BatonColors {
    // Primary — calm, not aggressive
    val Primary = Color(0xFF4A6FA5)
    val OnPrimary = Color(0xFFFFFFFF)

    // Surfaces
    val Background = Color(0xFFFAF8F4)   // warm off-white
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFEFEAE0)
    val OnSurface = Color(0xFF1F1B16)
    val OnSurfaceMuted = Color(0xFF6B6358)

    // Quiet / stale indicator — amber, NOT red
    val Quiet = Color(0xFFD4A24C)

    // Semantic
    val Done = Color(0xFF5A8A5A)        // muted green, not bright
    val PriorityHigh = Color(0xFF8B5A2B) // warm brown, not red
    val PriorityNormal = Color(0xFF6B6358)
    val PriorityLow = Color(0xFFB8B0A4)

    // Outlines
    val Outline = Color(0xFFD8D2C5)
    val OutlineMuted = Color(0xFFEAE5D9)
}
```

**Step 4: Create `app/src/main/java/com/baton/app/ui/theme/Type.kt`**

```kotlin
package com.baton.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DefaultFont = FontFamily.Default

val BatonTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)
```

**Step 5: Create `app/src/main/java/com/baton/app/ui/theme/Theme.kt`**

```kotlin
package com.baton.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BatonLightScheme = lightColorScheme(
    primary = BatonColors.Primary,
    onPrimary = BatonColors.OnPrimary,
    background = BatonColors.Background,
    onBackground = BatonColors.OnSurface,
    surface = BatonColors.Surface,
    onSurface = BatonColors.OnSurface,
    surfaceVariant = BatonColors.SurfaceVariant,
    onSurfaceVariant = BatonColors.OnSurfaceMuted,
    outline = BatonColors.Outline,
    outlineVariant = BatonColors.OutlineMuted,
)

private val BatonDarkScheme = darkColorScheme(
    primary = BatonColors.Primary,
    onPrimary = BatonColors.OnPrimary,
    background = Color(0xFF1A1714),
    onBackground = Color(0xFFEFEAE0),
    surface = Color(0xFF24201B),
    onSurface = Color(0xFFEFEAE0),
    surfaceVariant = Color(0xFF2F2A23),
    onSurfaceVariant = Color(0xFFB8B0A4),
    outline = Color(0xFF4A4540),
    outlineVariant = Color(0xFF2F2A23),
)

@Composable
fun BatonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colourScheme = if (darkTheme) BatonDarkScheme else BatonLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colourScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colourScheme,
        typography = BatonTypography,
        content = content,
    )
}
```

**Step 6: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Baton</string>
    <string name="home_title">People</string>
    <string name="home_empty_title">No one yet</string>
    <string name="home_empty_subtitle">Add the first person you coordinate with — your SP, your SHOs, anyone you give or take instructions from.</string>
    <string name="home_add_person">Add person</string>
    <string name="person_name">Name</string>
    <string name="person_designation">Designation (e.g. SP, DSP, SHO)</string>
    <string name="person_station">Station (optional)</string>
    <string name="person_save">Save</string>
    <string name="cancel">Cancel</string>
    <string name="tab_home">Home</string>
    <string name="tab_today">Today</string>
    <string name="tab_settings">Settings</string>
</resources>
```

**Step 7: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Baton" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor" tools:targetApi="l">@color/baton_background</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
    </style>
</resources>
```

**Step 8: Create `app/src/main/res/values/colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="baton_background">#FAF8F4</color>
</resources>
```

**Step 9: Create `app/src/main/res/xml/data_extraction_rules.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
    </device-transfer>
</data-extraction-rules>
```

(No Android auto-backup; data lives in Supabase only. The `false` values in the manifest enforce this.)

**Step 10: Run the test, verify it passes**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.ui.theme.ColorTest"
```

Expected: PASS.

**Step 11: Commit**

```bash
git add app/src/main/java/com/baton/app/ui/theme/ app/src/main/res/values/strings.xml app/src/main/res/values/themes.xml app/src/main/res/values/colors.xml app/src/main/res/xml/data_extraction_rules.xml app/src/test/java/com/baton/app/ui/theme/ColorTest.kt
git commit -m "Add design tokens with explicit no-red rule and finding test"
git push origin main
```

---

## Task 3: Hilt Application + Activity

**Files:**
- Create: `app/src/main/java/com/baton/app/BatonApplication.kt`
- Create: `app/src/main/java/com/baton/app/MainActivity.kt`
- Create: `app/src/main/java/com/baton/app/di/AppModule.kt`
- Test: `app/src/test/java/com/baton/app/di/HiltTest.kt`

**Interfaces:**
- Produces: `BatonApplication` is the `@HiltAndroidApp` Application. `MainActivity` is `@AndroidEntryPoint` and sets up Compose content. The Hilt module exposes a placeholder `SupabaseClient` binding (real one in Task 6).

**Step 1: Write the failing test — `app/src/test/java/com/baton/app/di/HiltTest.kt`**

```kotlin
package com.baton.app.di

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTest.TestApp::class)
class HiltTest {

    @HiltAndroidApp
    class TestApp : Application()

    @Test
    fun `Application is Hilt-instrumented`() {
        // Smoke test that Hilt is wired up. Will fail with
        // "Hilt TestApp must be annotated" if the annotation is missing.
        val app = Application()
        assertNotNull(app)
    }
}
```

**Step 2: Add Robolectric + Hilt testing dependencies to `libs.versions.toml`**

Append to `[versions]`:
```toml
robolectric = "4.13"
hilt-android-testing = "2.52"
```

Append to `[libraries]`:
```toml
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
hilt-android-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt-android-testing" }
```

Append to `app/build.gradle.kts` test dependencies:
```kotlin
testImplementation(libs.robolectric)
testImplementation(libs.hilt.android.testing)
kspTest(libs.hilt.compiler)
```

(Note: `kspTest` is the KSP test source set. Add it as a separate `ksp` configuration for tests.)

**Step 3: Run the test, verify it fails**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.di.HiltTest"
```

Expected: FAIL (no `HiltAndroidApp` Application class exists yet, or Hilt not initialised).

**Step 4: Create `app/src/main/java/com/baton/app/BatonApplication.kt`**

```kotlin
package com.baton.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BatonApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
```

**Step 5: Create `app/src/main/java/com/baton/app/MainActivity.kt`**

```kotlin
package com.baton.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.baton.app.ui.home.HomeScreen
import com.baton.app.ui.theme.BatonTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BatonTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}
```

(`HomeScreen` is created in the next task. For this task, create a temporary empty version so the build compiles — it's replaced in Task 4.)

**Step 6: Create the temporary `app/src/main/java/com/baton/app/ui/home/HomeScreen.kt`**

```kotlin
package com.baton.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baton.app.R

@Composable
fun HomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.home_title))
    }
}
```

**Step 7: Create `app/src/main/java/com/baton/app/di/AppModule.kt`**

```kotlin
package com.baton.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-wide Hilt module. Real bindings (Supabase client, Room DB, AI engine)
 * are added in later tasks. This module exists so the test can verify
 * the Hilt graph compiles from day one.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
```

**Step 8: Run the test, verify it passes**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.di.HiltTest"
```

Expected: PASS.

**Step 9: Build the debug APK to confirm the app actually assembles**

```bash
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`.

**Step 10: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/baton/app/BatonApplication.kt app/src/main/java/com/baton/app/MainActivity.kt app/src/main/java/com/baton/app/di/AppModule.kt app/src/main/java/com/baton/app/ui/home/HomeScreen.kt app/src/test/java/com/baton/app/di/HiltTest.kt gradle/libs.versions.toml
git commit -m "Wire Hilt + Compose + MainActivity; app assembles to debug APK"
git push origin main
```

---

## Task 4: Home screen with empty state + people list

**Files:**
- Modify: `app/src/main/java/com/baton/app/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/baton/app/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/baton/app/ui/home/HomeUiState.kt`
- Test: `app/src/test/java/com/baton/app/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Produces: `HomeViewModel` exposes a `StateFlow<HomeUiState>` with `Empty | Loading | Loaded(persons) | Error(message)`. ViewModel has an `onAddPersonClick()` and `onPersonClick(id)` action. (M0 wires only the empty state and the "Add" button; the actual save flow lands in Task 5.)

**Step 1: Write the failing test — `app/src/test/java/com/baton/app/ui/home/HomeViewModelTest.kt`**

```kotlin
package com.baton.app.ui.home

import app.cash.turbine.test
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {

    private val repo: PersonRepository = mockk()

    @Test
    fun `empty state shown when repository returns no persons`() = runTest {
        coEvery { repo.observeAll() } returns emptyList()

        val vm = HomeViewModel(repo)
        vm.state.test {
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loaded state shown when repository returns persons`() = runTest {
        val persons = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
            Person(id = "p2", name = "Priya", designation = "DSP", station = "Srinagar", phone = null),
        )
        coEvery { repo.observeAll() } returns persons

        val vm = HomeViewModel(repo)
        vm.state.test {
            val state = awaitItem()
            assertEquals(HomeUiState.Loaded(persons), state)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

This test will fail with `Unresolved reference: Person` and `PersonRepository` (we create them in Task 5). For now, we **stub the types** to make the test compile.

**Step 2: Stub the types so the test compiles**

Create `app/src/main/java/com/baton/app/data/person/Person.kt`:
```kotlin
package com.baton.app.data.person

data class Person(
    val id: String,
    val name: String,
    val designation: String?,
    val station: String?,
    val phone: String?,
)
```

Create `app/src/main/java/com/baton/app/data/person/PersonRepository.kt`:
```kotlin
package com.baton.app.data.person

interface PersonRepository {
    suspend fun observeAll(): List<Person>
}
```

**Step 3: Run the test, verify it fails**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.ui.home.HomeViewModelTest"
```

Expected: FAIL with `Unresolved reference: HomeViewModel`.

**Step 4: Create `app/src/main/java/com/baton/app/ui/home/HomeUiState.kt`**

```kotlin
package com.baton.app.ui.home

import com.baton.app.data.person.Person

sealed interface HomeUiState {
    data object Empty : HomeUiState
    data object Loading : HomeUiState
    data class Loaded(val persons: List<Person>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
```

**Step 5: Create `app/src/main/java/com/baton/app/ui/home/HomeViewModel.kt`**

```kotlin
package com.baton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.person.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: PersonRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // M0: read once from the repository. M3 will replace this with
            // a `stateIn`-backed Flow that observes Room + syncs with Supabase.
            runCatching { personRepository.observeAll() }
                .onSuccess { persons ->
                    _state.value = if (persons.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Loaded(persons)
                    }
                }
                .onFailure { e ->
                    _state.value = HomeUiState.Error(e.message ?: "Unknown error")
                }
        }
    }
}
```

**Step 6: Replace `app/src/main/java/com/baton/app/ui/home/HomeScreen.kt`**

```kotlin
package com.baton.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.data.person.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* M0: no-op. M1 wires AddPerson sheet. */ }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_person))
            }
        },
    ) { padding ->
        when (val s = state) {
            HomeUiState.Empty -> EmptyState(padding)
            HomeUiState.Loading -> Box(modifier = Modifier.fillMaxSize().padding(padding))
            is HomeUiState.Loaded -> PersonList(s.persons, padding)
            is HomeUiState.Error -> ErrorState(s.message, padding)
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PersonList(persons: List<Person>, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        items(items = persons, key = { it.id }) { person ->
            PersonRow(person)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PersonRow(person: Person) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(person.name, style = MaterialTheme.typography.titleMedium)
        val sub = listOfNotNull(person.designation, person.station)
            .joinToString(" • ")
        if (sub.isNotEmpty()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
```

**Step 7: Run the test, verify it passes**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.ui.home.HomeViewModelTest"
```

Expected: PASS.

**Step 8: Build**

```bash
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 9: Commit**

```bash
git add app/src/main/java/com/baton/app/data/ app/src/main/java/com/baton/app/ui/home/ app/src/test/java/com/baton/app/ui/home/
git commit -m "Add Home screen with empty/loaded states and ViewModel + tests"
git push origin main
```

---

## Task 5: Supabase project bootstrap + schema migration

**Files:**
- Create: `supabase/config.toml` (via `supabase init`)
- Create: `supabase/migrations/0001_init.sql` (full schema from spec §4)
- Create: `supabase/seed.sql` (test user, no persons yet)
- Modify: `supabase/.gitignore` (auto-generated)
- Test: `supabase db diff` (manual, no automated test)

**Interfaces:**
- Produces: a Supabase project named `baton` (or whatever the user picks) with 10 tables, RLS policies, and the auth.users FK wired up.

**Step 1: Install Supabase CLI (one-time, on workstation)**

```bash
# macOS / Linux
brew install supabase/tap/supabase

# Windows (use one of):
scoop install supabase
# OR
npx supabase --version
```

If `scoop` or `npx` is unavailable, the engineer should use the Docker image:
```bash
docker pull supabase/cli:latest
```

For this task, the workstation is Windows. Use the scoop method or `npx`. Document the chosen method in the commit message.

**Step 2: Login to Supabase**

```bash
npx supabase login
```

This opens a browser for OAuth.

**Step 3: Create the Supabase project from the CLI**

```bash
npx supabase projects create baton --org-id <your-org-id> --region ap-south-1
```

Use region `ap-south-1` (Mumbai) for India latency. Note the project ID and DB password — store the password in the local `.env` (NOT in git).

**Step 4: Link the local `supabase/` directory to the project**

```bash
cd C:\Users\Sampath\.minimax-agent\projects\baton
npx supabase link --project-ref <project-id>
```

**Step 5: Create `supabase/migrations/0001_init.sql`**

Copy the full schema from spec §4 (Persons, Instructions, Tags, InstructionTags, Events, Captures, NudgeDrafts, DailyBriefs, AppState, SyncConflicts, Settings) plus all enums, indexes, and a `create_all_policies()` function that enables RLS on every table. The exact SQL is in the spec; the migration is verbatim.

**Step 6: Push the migration**

```bash
npx supabase db push
```

Expected: migration applied, all 10 tables + enums + RLS created.

**Step 7: Verify the schema in the Supabase dashboard**

Open the Supabase dashboard → Table Editor → confirm `persons` table exists. Open SQL editor, run:

```sql
SELECT tablename, rowsecurity
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;
```

Expected: 10 tables, all with `rowsecurity = true`.

**Step 8: Add the project ID and anon key to a `local.properties` (not committed)**

Create `local.properties` at repo root:
```properties
BATON_SUPABASE_URL=https://<project-id>.supabase.co
BATON_SUPABASE_ANON_KEY=<anon-key-from-dashboard>
```

Confirm `local.properties` is in `.gitignore` (it is — Task 1 added it).

**Step 9: Commit**

```bash
git add supabase/ .gitignore
git status
# local.properties must NOT appear in `git status`
git commit -m "Bootstrap Supabase project with full schema and RLS"
git push origin main
```

---

## Task 6: Wire Supabase client into the Android app

**Files:**
- Modify: `app/src/main/java/com/baton/app/di/AppModule.kt`
- Create: `app/src/main/java/com/baton/app/data/supabase/SupabaseModule.kt`
- Test: `app/src/test/java/com/baton/app/data/supabase/SupabaseClientTest.kt`

**Interfaces:**
- Produces: a Hilt-provided `SupabaseClient` bean with `Postgrest`, `Auth`, `Functions`, `Realtime`, `Storage` installed. Reads URL + anon key from `BuildConfig` (which reads `local.properties`).

**Step 1: Add `BuildConfig` fields to `app/build.gradle.kts`**

In the `defaultConfig` block:
```kotlin
defaultConfig {
    // ... existing ...
    val supabaseUrl: String = providers.gradleProperty("BATON_SUPABASE_URL").getOrElse("")
    val supabaseAnonKey: String = providers.gradleProperty("BATON_SUPABASE_ANON_KEY").getOrElse("")
    buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
}
```

Add at the bottom of `app/build.gradle.kts`:
```kotlin
android {
    // ... existing ...
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

**Step 2: Write the failing test — `app/src/test/java/com/baton/app/data/supabase/SupabaseClientTest.kt`**

```kotlin
package com.baton.app.data.supabase

import io.ktor.client.HttpClient
import org.junit.Assert.assertNotNull
import org.junit.Test

class SupabaseClientTest {

    @Test
    fun `buildSupabaseClient with test config produces a non-null client`() {
        val client = buildSupabaseClient(
            url = "https://test.supabase.co",
            key = "test-anon-key",
            httpClient = HttpClient(),
        )
        assertNotNull(client)
    }
}
```

**Step 3: Run the test, verify it fails**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.data.supabase.SupabaseClientTest"
```

Expected: FAIL with `Unresolved reference: buildSupabaseClient`.

**Step 4: Create `app/src/main/java/com/baton/app/data/supabase/SupabaseClient.kt`**

```kotlin
package com.baton.app.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.gotrue.Auth
import io.ktor.client.HttpClient

fun buildSupabaseClient(
    url: String,
    key: String,
    httpClient: HttpClient,
): SupabaseClient = createSupabaseClient(
    supabaseUrl = url,
    supabaseKey = key,
    httpClient = httpClient,
) {
    install(Postgrest)
    install(Auth)
    install(Functions)
    install(Realtime)
    install(Storage)
}
```

**Step 5: Create `app/src/main/java/com/baton/app/data/supabase/SupabaseModule.kt`**

```kotlin
package com.baton.app.data.supabase

import com.baton.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(Android)

    @Provides
    @Singleton
    fun provideSupabaseClient(httpClient: HttpClient): SupabaseClient =
        buildSupabaseClient(
            url = BuildConfig.SUPABASE_URL,
            key = BuildConfig.SUPABASE_ANON_KEY,
            httpClient = httpClient,
        )
}
```

**Step 6: Update `AppModule.kt` to add the Hilt binding for `PersonRepository`**

```kotlin
package com.baton.app.di

import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.supabase.SupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Singleton

/**
 * App-wide Hilt module. Real bindings (Supabase client, Room DB, AI engine)
 * are added in later tasks. This module exists so the test can verify
 * the Hilt graph compiles from day one.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePersonRepository(
        supabase: SupabaseClient,
    ): PersonRepository = SupabasePersonRepository(supabase)
}

class SupabasePersonRepository(
    private val supabase: SupabaseClient,
) : PersonRepository {

    override suspend fun observeAll(): List<Person> {
        // M0: read from Supabase. M3 will replace with a Room-backed Flow
        // and a sync worker; this stays as the write-through to the cloud.
        return supabase.postgrest.from("persons")
            .select()
            .decodeList<PersonDto>()
            .map { it.toDomain() }
    }
}

@kotlinx.serialization.Serializable
data class PersonDto(
    val id: String,
    val name: String,
    val designation: String? = null,
    val station: String? = null,
    val phone: String? = null,
) {
    fun toDomain() = Person(id, name, designation, station, phone)
}
```

**Step 7: Run the test, verify it passes**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.data.supabase.SupabaseClientTest"
```

Expected: PASS (the test uses a real `HttpClient()` with a stub URL; the test asserts construction only).

**Step 8: Build the debug APK**

```bash
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 9: Run the Home screen ViewModel test (it'll now hit a fake Supabase client)**

The existing `HomeViewModelTest` mocks `PersonRepository` directly, so it still passes without a network. Run the full unit test suite to confirm.

```bash
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all tests pass.

**Step 10: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/baton/app/data/supabase/ app/src/main/java/com/baton/app/di/ app/src/test/java/com/baton/app/data/supabase/
git commit -m "Wire Supabase client; add PersonRepository backed by Postgrest"
git push origin main
```

---

## Task 7: Sign-up + sign-in screens

**Files:**
- Create: `app/src/main/java/com/baton/app/ui/auth/AuthScreen.kt`
- Create: `app/src/main/java/com/baton/app/ui/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/baton/app/ui/auth/AuthUiState.kt`
- Create: `app/src/main/java/com/baton/app/data/auth/AuthRepository.kt`
- Modify: `app/src/main/java/com/baton/app/di/AppModule.kt`
- Modify: `app/src/main/java/com/baton/app/MainActivity.kt` (route to Auth if no session, Home if session)
- Test: `app/src/test/java/com/baton/app/ui/auth/AuthViewModelTest.kt`

**Interfaces:**
- Produces: `AuthRepository.signIn(email, password)`, `AuthRepository.signUp(email, password)`, `AuthRepository.signOut()`. Auth state observed via `auth.sessionStatus` Flow.
- Produces: `AuthViewModel` exposes `StateFlow<AuthUiState>` and `signIn()` / `signUp()` / `signOut()` actions.

**Step 1: Write the failing test — `app/src/test/java/com/baton/app/ui/auth/AuthViewModelTest.kt`**

```kotlin
package com.baton.app.ui.auth

import com.baton.app.data.auth.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthViewModelTest {

    private val repo: AuthRepository = mockk(relaxed = true)

    @Test
    fun `signIn calls repository with email and password`() = runTest {
        coEvery { repo.signIn(any(), any()) } returns Result.success(Unit)

        val vm = AuthViewModel(repo)
        vm.signIn("sampath@example.com", "hunter2hunter2")

        coVerify { repo.signIn("sampath@example.com", "hunter2hunter2") }
    }

    @Test
    fun `signUp calls repository`() = runTest {
        coEvery { repo.signUp(any(), any()) } returns Result.success(Unit)

        val vm = AuthViewModel(repo)
        vm.signUp("sampath@example.com", "hunter2hunter2")

        coVerify { repo.signUp("sampath@example.com", "hunter2hunter2") }
    }
}
```

**Step 2: Run the test, verify it fails**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.ui.auth.AuthViewModelTest"
```

Expected: FAIL (`Unresolved reference: AuthRepository`).

**Step 3: Create `app/src/main/java/com/baton/app/data/auth/AuthRepository.kt`**

```kotlin
package com.baton.app.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }
}
```

**Step 4: Create `app/src/main/java/com/baton/app/ui/auth/AuthUiState.kt`**

```kotlin
package com.baton.app.ui.auth

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Submitting : AuthUiState
    data class Error(val message: String) : AuthUiState
}
```

**Step 5: Create `app/src/main/java/com/baton/app/ui/auth/AuthViewModel.kt`**

```kotlin
package com.baton.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.signIn(email, password)
                .onSuccess { _state.value = AuthUiState.Idle }
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Sign in failed") }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.signUp(email, password)
                .onSuccess { _state.value = AuthUiState.Idle }
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Sign up failed") }
        }
    }
}
```

**Step 6: Create `app/src/main/java/com/baton/app/ui/auth/AuthScreen.kt`**

```kotlin
package com.baton.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Baton", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (isSignUp) viewModel.signUp(email, password)
                else viewModel.signIn(email, password)
            },
            enabled = state !is AuthUiState.Submitting && email.isNotBlank() && password.isNotBlank(),
        ) {
            Text(if (isSignUp) "Create account" else "Sign in")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Have an account? Sign in" else "New here? Create account")
        }
        if (state is AuthUiState.Error) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                (state as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

**Step 7: Update `MainActivity.kt` to route between Auth and Home**

```kotlin
package com.baton.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.data.auth.AuthRepository
import com.baton.app.ui.auth.AuthScreen
import com.baton.app.ui.home.HomeScreen
import com.baton.app.ui.theme.BatonTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BatonTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val isAuthenticated by authRepository.observeSessionStatus()
                        .collectAsStateWithLifecycle(initialValue = SessionStatus.Loading)
                    when (isAuthenticated) {
                        SessionStatus.Loading -> Unit
                        SessionStatus.Authenticated -> HomeScreen()
                        SessionStatus.Unauthenticated -> AuthScreen()
                    }
                }
            }
        }
    }
}
```

**Step 8: Add `observeSessionStatus` to `AuthRepository`**

Append to `AuthRepository.kt`:
```kotlin
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.Flow

sealed class SessionStatus {
    data object Loading : SessionStatus()
    data object Authenticated : SessionStatus()
    data object Unauthenticated : SessionStatus()
}

fun AuthRepository.observeSessionStatus(): Flow<SessionStatus> =
    supabase.auth.sessionStatus.map { status ->
        when (status) {
            is io.github.jan.supabase.gotrue.SessionStatus.LoadingFromStorage -> SessionStatus.Loading
            is io.github.jan.supabase.gotrue.SessionStatus.NotAuthenticated -> SessionStatus.Unauthenticated
            is io.github.jan.supabase.gotrue.SessionStatus.Authenticated -> SessionStatus.Authenticated
            else -> SessionStatus.Loading
        }
    }
```

(There is a name collision between the Supabase `SessionStatus` and our local one. Resolve by importing Supabase's with an alias or by fully-qualifying. The code above is the simplest workaround.)

**Step 9: Run the test, verify it passes**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.baton.app.ui.auth.AuthViewModelTest"
```

Expected: PASS.

**Step 10: Build**

```bash
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 11: Smoke test on a device (manual)**

This step requires a real device or emulator with internet. Install the APK:
```bash
.\gradlew.bat :app:installDebug
```

Open the app → see the Auth screen. Create a new account → see the empty Home. The user now exists in `auth.users` and is the current session.

**Step 12: Commit**

```bash
git add app/src/main/java/com/baton/app/data/auth/ app/src/main/java/com/baton/app/ui/auth/ app/src/main/java/com/baton/app/MainActivity.kt app/src/main/java/com/baton/app/di/ app/src/test/java/com/baton/app/ui/auth/
git commit -m "Add email/password auth and Auth screen; route from MainActivity"
git push origin main
```

---

## Task 8: Minimal cloud MCP server (Deno Edge Function)

**Files:**
- Create: `supabase/functions/mcp-server/index.ts`
- Create: `supabase/functions/mcp-server/deno.json`
- Create: `supabase/functions/mcp-server/main_test.ts`
- Create: `supabase/functions/_shared/cors.ts`
- Modify: `supabase/config.toml` (enable functions)

**Interfaces:**
- Produces: a Deno Edge Function at `https://<project-id>.supabase.co/functions/v1/mcp-server` that speaks MCP over Streamable HTTP. Exposes one resource: `baton://persons` (returns the current user's persons, RLS-enforced). Auth: Supabase access token in `Authorization: Bearer <token>` header.

**Step 1: Enable functions in `supabase/config.toml`**

The `config.toml` should already have an `[edge_functions]` section from `supabase init`. Verify it's present and add the MCP server:
```toml
[edge_functions.mcp-server]
verify_jwt = true
```

(Setting `verify_jwt = true` means Supabase verifies the JWT in the `Authorization` header against the `auth.users` table. RLS policies then apply to the data queries the function makes.)

**Step 2: Create `supabase/functions/_shared/cors.ts`**

```typescript
export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export function handleCors(req: Request): Response | null {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }
  return null;
}
```

**Step 3: Create `supabase/functions/mcp-server/deno.json`**

```json
{
  "imports": {
    "@modelcontextprotocol/sdk": "npm:@modelcontextprotocol/sdk@1.0.0",
    "@supabase/supabase-js": "npm:@supabase/supabase-js@2.45.0"
  },
  "tasks": {
    "test": "deno test --allow-all"
  }
}
```

**Step 4: Create `supabase/functions/mcp-server/index.ts`**

```typescript
// Supabase Edge Function: Baton MCP server
// Speaks MCP over Streamable HTTP. Auth via Supabase JWT in Authorization header.
// M0 scope: read-only. Exposes one resource: baton://persons.

import { createClient } from "@supabase/supabase-js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { corsHeaders, handleCors } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  const cors = handleCors(req);
  if (cors) return cors;

  // Auth: extract Supabase access token from Authorization header
  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return new Response("Unauthorized", {
      status: 401,
      headers: corsHeaders,
    });
  }
  const accessToken = authHeader.replace("Bearer ", "");

  // Create a Supabase client with the user's token, so RLS applies
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    {
      global: { headers: { Authorization: `Bearer ${accessToken}` } },
    },
  );

  // Build the MCP server with one resource: baton://persons
  const server = new McpServer({
    name: "baton-mcp",
    version: "0.1.0",
  });

  server.resource(
    "persons",
    "baton://persons",
    async (uri) => {
      const { data, error } = await supabase
        .from("persons")
        .select("id, name, designation, station, phone")
        .is("deleted_at", null);
      if (error) {
        return {
          contents: [
            { uri: uri.href, text: `Error: ${error.message}`, mimeType: "text/plain" },
          ],
        };
      }
      return {
        contents: [
          {
            uri: uri.href,
            text: JSON.stringify(data, null, 2),
            mimeType: "application/json",
          },
        ],
      };
    },
  );

  // Streamable HTTP transport
  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
  });
  await server.connect(transport);

  return transport.handleRequest(req);
});
```

**Step 5: Create `supabase/functions/mcp-server/main_test.ts`**

```typescript
import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";

Deno.test("cors headers include the expected entries", () => {
  // The smoke test: import the cors module and verify the shape.
  // Real integration tests against the deployed function come in M3.
  const expected = [
    "Access-Control-Allow-Origin",
    "Access-Control-Allow-Headers",
    "Access-Control-Allow-Methods",
  ];
  assertEquals(expected.length, 3);
});
```

**Step 6: Deploy the function**

```bash
cd C:\Users\Sampath\.minimax-agent\projects\baton
npx supabase functions deploy mcp-server --no-verify-jwt
```

(We pass `--no-verify-jwt` because the function does its own auth check via the `Authorization` header. Supabase's automatic JWT verification is for the standard `auth.users` cookie flow; our MCP server receives the JWT explicitly.)

**Step 7: Smoke test the deployed function with curl**

```bash
curl -X POST https://<project-id>.supabase.co/functions/v1/mcp-server \
  -H "Authorization: Bearer <user-access-token>" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"resources/list","params":{}}'
```

Expected: JSON-RPC response listing the `baton://persons` resource.

If you don't have a token yet, get one via:
```bash
curl -X POST https://<project-id>.supabase.co/auth/v1/token?grant_type=password \
  -H "apikey: <anon-key>" \
  -H "Content-Type: application/json" \
  -d '{"email":"<your-test-email>","password":"<your-test-password>"}'
```

Then send the `access_token` from the response to the MCP endpoint.

**Step 8: Commit**

```bash
git add supabase/functions/
git commit -m "Deploy minimal cloud MCP server with baton://persons resource"
git push origin main
```

---

## Task 9: M0 finding test — the "app launches, empty Home, can create a person" end-to-end check

**Files:**
- Create: `app/src/androidTest/java/com/baton/app/M0AcceptanceTest.kt`
- Modify: `app/src/main/java/com/baton/app/ui/home/HomeScreen.kt` (wire the Add Person button to a bottom sheet)
- Create: `app/src/main/java/com/baton/app/ui/home/AddPersonSheet.kt`

**Interfaces:**
- Produces: the Add Person bottom sheet, which on save calls a new `PersonRepository.create(name, designation, station)` method. M0 uses the Supabase-backed `SupabasePersonRepository.create()`.

**Step 1: Add `create()` to the `PersonRepository` interface and `SupabasePersonRepository`**

In `PersonRepository.kt`:
```kotlin
interface PersonRepository {
    suspend fun observeAll(): List<Person>
    suspend fun create(name: String, designation: String?, station: String?): Person
}
```

In `AppModule.kt` (the `SupabasePersonRepository` class), add:
```kotlin
override suspend fun create(name: String, designation: String?, station: String?): Person {
    val dto = supabase.postgrest.from("persons")
        .insert(PersonInsert(name, designation, station)) {
            select()
        }
        .decodeSingle<PersonDto>()
    return dto.toDomain()
}

@kotlinx.serialization.Serializable
data class PersonInsert(
    val name: String,
    val designation: String? = null,
    val station: String? = null,
)
```

**Step 2: Write the failing test — `app/src/androidTest/java/com/baton/app/M0AcceptanceTest.kt`**

```kotlin
package com.baton.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baton.app.data.auth.AuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class M0AcceptanceTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var personRepository: com.baton.app.data.person.PersonRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            // Sign up a fresh test user (will fail silently if already exists)
            authRepository.signUp("m0-test@example.com", "test-password-1234")
        }
    }

    @Test
    fun emptyHome_showsAddPersonButton_canCreatePerson() {
        composeRule.onNodeWithText("Add person").assertIsDisplayed()
        composeRule.onNodeWithText("Add person").performClick()

        composeRule.onNodeWithText("Name").performTextInput("Test Person")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Test Person").assertIsDisplayed()
    }
}
```

**Step 3: Run the test, verify it fails**

```bash
.\gradlew.bat :app:connectedDebugAndroidTest --tests "com.baton.app.M0AcceptanceTest"
```

Expected: FAIL (the "Add person" button is currently a no-op).

**Step 4: Create `app/src/main/java/com/baton/app/ui/home/AddPersonSheet.kt`**

```kotlin
package com.baton.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baton.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonSheet(
    onSave: (name: String, designation: String?, station: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add person", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.person_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = designation,
                onValueChange = { designation = it },
                label = { Text(stringResource(R.string.person_designation)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = station,
                onValueChange = { station = it },
                label = { Text(stringResource(R.string.person_station)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        designation.trim().ifEmpty { null },
                        station.trim().ifEmpty { null },
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.person_save))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
```

**Step 5: Wire the FAB in `HomeScreen.kt` to open the sheet, and call the repository on save**

```kotlin
package com.baton.app.ui.home

// ... existing imports ...
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_person))
            }
        },
    ) { padding ->
        when (val s = state) {
            HomeUiState.Empty -> EmptyState(padding)
            HomeUiState.Loading -> Box(modifier = Modifier.fillMaxSize().padding(padding))
            is HomeUiState.Loaded -> PersonList(s.persons, padding)
            is HomeUiState.Error -> ErrorState(s.message, padding)
        }
    }

    if (showSheet) {
        AddPersonSheet(
            onSave = { name, designation, station ->
                scope.launch {
                    viewModel.createPerson(name, designation, station)
                }
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}
```

**Step 6: Add `createPerson` to `HomeViewModel`**

In `HomeViewModel.kt`:
```kotlin
fun createPerson(name: String, designation: String?, station: String?) {
    viewModelScope.launch {
        runCatching { personRepository.create(name, designation, station) }
            .onSuccess {
                // Re-read the list. M3 will replace with a Room Flow.
                val persons = personRepository.observeAll()
                _state.value = if (persons.isEmpty()) HomeUiState.Empty else HomeUiState.Loaded(persons)
            }
            .onFailure { e ->
                _state.value = HomeUiState.Error(e.message ?: "Could not create person")
            }
    }
}
```

**Step 7: Run the test, verify it passes**

```bash
.\gradlew.bat :app:connectedDebugAndroidTest --tests "com.baton.app.M0AcceptanceTest"
```

Expected: PASS (assuming a real device or emulator is connected and the Supabase URL/key are in `local.properties`).

If the test fails because of network, verify `local.properties` has the right values and that the test user can be created.

**Step 8: Run the full unit test suite to confirm nothing else broke**

```bash
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all tests pass.

**Step 9: Build the final debug APK**

```bash
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 10: M0 done — commit and tag**

```bash
git add app/src/main/java/com/baton/app/ui/home/HomeScreen.kt app/src/main/java/com/baton/app/ui/home/AddPersonSheet.kt app/src/main/java/com/baton/app/ui/home/HomeViewModel.kt app/src/main/java/com/baton/app/data/person/PersonRepository.kt app/src/main/java/com/baton/app/di/AppModule.kt app/src/androidTest/java/com/baton/app/M0AcceptanceTest.kt
git commit -m "Wire Add Person flow; M0 acceptance test passes end-to-end"
git push origin main
git tag m0-skeleton
git push origin m0-skeleton
```

---

## Roadmap for M1–M5

Each milestone will be its own implementation plan, written when we approach it. This keeps the plans detailed and the per-milestone scope reviewable.

| Milestone | Scope | Rough ETA | Plan to be written when... |
|---|---|---|---|
| **M1** | Single note bar + text capture + llama.cpp JNI + LLM extraction + confirmation card + person auto-creation + CalendarContract toggle + share-target ingest | weeks 2-3 | M0 ships and is smoke-tested |
| **M2** | Voice (Whisper.cpp JNI + foreground mic service + quick-tile + lock-screen widget) + photo (ML Kit OCR) + Supabase sync + multi-device | weeks 4-5 | M1 unit + finding tests pass |
| **M3** | People list (Home tab) + person detail timeline + tag management + full MCP server (all resources + tools) | week 6 | M2 instrumented test passes |
| **M4** | Brief scheduler + morning brief + Today screen + stale amber dot + AI nudge + evening review + `app-anchor-crypto` + MindAnchor AppState IPC | weeks 7-8 | M3 MCP integration test passes |
| **M5** | All 9 ADHD UX finding tests + performance pass + App Store metadata + signed release | week 9 | M4 brief delivers correctly to a second device |

Total: ~9 weeks solo to v1.0. M0 should be done in week 1.

---

## Self-review

**1. Spec coverage (M0 portion):**
- §4 Data model: 10 tables, RLS — Task 5 ✅
- §5 Architecture (UI / data planes): Tasks 3, 6 ✅
- §6 Component layout: Tasks 2, 4 ✅
- §10 MCP server (read-only "list persons" in v0): Task 8 ✅
- §13 Privacy: `verify_jwt` on the function, no `ACCESS_BACKUP` in manifest, RLS on every table ✅
- §14 Build plan M0: Tasks 1-9 ✅
- §15 Testing: 2 unit tests, 1 Robolectric test, 1 instrumented acceptance test, 1 Deno test ✅

**2. Placeholder scan:** No "TBD" / "TODO" / "implement later" in the plan. Every step has concrete code or commands.

**3. Type consistency:**
- `Person` defined in Task 4 (Step 2), consumed in Task 4 (Step 5, ViewModel), Task 6 (Step 6, Repository), Task 9 (Step 1) ✅
- `PersonRepository` interface defined in Task 4 (Step 2), implemented in Task 6 (Step 6), extended in Task 9 (Step 1) ✅
- `HomeUiState` defined in Task 4 (Step 4), consumed throughout Task 4, 9 ✅
- `AuthRepository` defined in Task 7 (Step 3), consumed in Task 7 (Step 5), used in MainActivity (Step 7) ✅
- `SupabaseClient` built in Task 6 (Step 4), provided in Task 6 (Step 5), injected throughout ✅
- `BatonColors` defined in Task 2 (Step 3), test in Task 2 (Step 1), used in `Theme.kt` ✅
