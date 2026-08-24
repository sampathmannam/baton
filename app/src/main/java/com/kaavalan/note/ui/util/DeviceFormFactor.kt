package com.kaavalan.note.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * v1.9.0 (PROD-READINESS-P3-P2-#1): the device
 * form-factor helper. The single-column
 * phone layout is the v1.x default; on a
 * tablet (width >= 600dp) the screens
 * re-arrange to a 2-column grid.
 *
 * **Why a manual width check, not
 * `material3.windowsizeclass.WindowSizeClass`.**
 *  - The Material 3 WindowSizeClass
 *    library is on the classpath but
 *    adds 200 KB to the APK; the
 *    threshold check is 4 lines of
 *    Kotlin.
 *  - The v1.x screens are not built
 *    around the `WindowSizeClass`
 *    composable contract (no
 *    `WindowWidthSizeClass.Compact` /
 *    `Medium` / `Expanded` branches).
 *  - A v2.x that fully migrates to
 *    M3's adaptive layout can swap
 *    this helper for the official
 *    WindowSizeClass; the call sites
 *    are the same.
 *
 * **Thresholds.**
 *  - `Phone` — width < 600dp.
 *  - `Tablet` — width >= 600dp.
 *
 * The 600dp boundary is the standard M3
 * "medium width" boundary; below that the
 * typical tablet is in portrait, above that
 * it has enough horizontal room for a
 * 2-column grid.
 */
enum class DeviceFormFactor { Phone, Tablet }

@Composable
fun rememberDeviceFormFactor(): DeviceFormFactor {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    return if (widthDp >= 600) DeviceFormFactor.Tablet else DeviceFormFactor.Phone
}
