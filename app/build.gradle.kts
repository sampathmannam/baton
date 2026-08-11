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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.baton.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // M1: on-device LLM (llama.cpp JNI). arm64-v8a only per the
        // global constraint. The CMake build is configured below
        // (see externalNativeBuild + the vendorLlamaCpp task).
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The auto-injected WorkManagerInitializer ContentProvider is
            // expected when on-demand init is wired in M3. Disable the
            // lint check so the M1 release APK can build without the
            // M3 plumbing.
            lint {
                disable += "RemoveWorkManagerInitializer"
            }
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

// M1-T3: vendor the pinned llama.cpp release into app/src/main/cpp/llama-cpp/
// at configuration time. CMake's add_subdirectory() then compiles it as
// part of the externalNativeBuild above. This is a no-op if the directory
// already exists, so a developer can delete it to force a re-vendor.
//
// The fallback (if CMake+NDK doesn't build on a particular host) is to
// download the prebuilt libllama.so for android-arm64 from the
// ggerganov/llama.cpp releases page and skip this task — see
// docs/superpowers/plans/2026-08-11-baton-m1-capture.md Task 3 risks.
val vendorLlamaCpp = tasks.register("vendorLlamaCpp") {
    val tag = "b4600"
    val url = "https://github.com/ggerganov/llama.cpp/archive/refs/tags/$tag.tar.gz"
    val cppDir = layout.projectDirectory.dir("src/main/cpp/llama-cpp")
    val marker = layout.buildDirectory.file("llama-cpp/$tag.vendored")
    outputs.file(marker)
    doLast {
        if (cppDir.asFile.exists() && cppDir.asFile.listFiles()?.isNotEmpty() == true) {
            logger.lifecycle("llama-cpp already present, skipping vendor")
            marker.get().asFile.parentFile.mkdirs()
            marker.get().asFile.writeText("present\n")
            return@doLast
        }
        val tarball = layout.buildDirectory.file("llama-cpp/$tag.tar.gz").get().asFile
        tarball.parentFile.mkdirs()
        logger.lifecycle("Downloading $url -> ${tarball.absolutePath}")
        ant.invokeMethod("get", mapOf("src" to url, "dest" to tarball.absolutePath, "verbose" to true))
        logger.lifecycle("Extracting ${tarball.absolutePath}")
        ant.invokeMethod("untar", mapOf("src" to tarball.absolutePath, "dest" to cppDir.asFile.parentFile.absolutePath, "compression" to "gzip"))
        // The tarball extracts into llama.cpp-<tag>/; rename to llama-cpp/.
        val extracted = cppDir.asFile.parentFile.resolve("llama.cpp-$tag")
        if (extracted.exists()) {
            extracted.renameTo(cppDir.asFile)
        }
        marker.get().asFile.parentFile.mkdirs()
        marker.get().asFile.writeText("present\n")
    }
}
tasks.matching { it.name.startsWith("externalNativeBuild") || it.name.startsWith("configureCMake") }
    .configureEach { dependsOn(vendorLlamaCpp) }

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

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.okhttp)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.mockk)

    debugImplementation(libs.compose.ui.tooling)
}
