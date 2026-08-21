package com.baton.app

import android.content.Context

/**
 * v1.8.0 (PROD-READINESS-P2-#6): the branding config.
 *
 * The pre-v1.8.0 code path hardcoded the app name and icon
 * (`R.string.app_name = "Kaavalan note"`, `@mipmap/ic_launcher`)
 * for every build flavour. The pilot deployment needs per-
 * department branding: a TNeGA / CCPS build displays the
 * TNeGA badge + name, a hypothetical state-IT build displays
 * that org's name, and the default R&D build stays "Kaavalan
 * note" so the v1.8.0 default is unchanged for non-pilot
 * users.
 *
 * **The build-time path.** [BuildConfig.BRAND_NAME],
 * [BuildConfig.BRAND_DEPARTMENT], and [BuildConfig.BRAND_ICON]
 * are populated from Gradle properties at the time the APK
 * is built. The default APK (no `-Pbrand.*` flags) is the
 * R&D "Kaavalan note" build. A pilot build is
 * ```
 * ./gradlew assembleRelease \
 *   -Pbrand.name="TNeGA CCPS" \
 *   -Pbrand.dept="Tamil Nadu e-Governance Agency" \
 *   -Pbrand.icon="ic_launcher_tnega"
 * ```
 *
 * **The runtime path.** [BrandingConfig.fromBuildConfig] is
 * the single read site. The Settings "About" section,
 * the splash-screen text, and the app name in the launcher
 * all read from this object.
 *
 * **No persistence.** Branding is a build-time concern, not
 * a user preference. Switching brands means rebuilding the
 * APK, not flipping a Settings toggle. (A future build-flavor
 * "Demo" can set `brand.name="Baton — Demo"` to make
 * screenshots / QA demo builds self-identify.)
 */
data class BrandingConfig(
    val appName: String,
    val department: String,
    val iconName: String,
) {
    val hasDepartment: Boolean get() = department.isNotBlank()

    companion object {
        /**
         * The single read site. Reads from [BuildConfig] so
         * the value is baked at compile time. The `default`
         * factory takes a [Context] for completeness but
         * does not need it; the icon is a string resource
         * name, not a Drawable.
         */
        fun fromBuildConfig(): BrandingConfig = BrandingConfig(
            appName = BuildConfig.BRAND_NAME,
            department = BuildConfig.BRAND_DEPARTMENT,
            iconName = BuildConfig.BRAND_ICON,
        )

        // Keep the static for callers that want to avoid
        // allocating a fresh object per access.
        @Volatile private var cached: BrandingConfig? = null

        fun get(): BrandingConfig =
            cached ?: synchronized(this) {
                cached ?: fromBuildConfig().also { cached = it }
            }
    }
}
