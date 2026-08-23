package com.baton.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Tier 1.4 (v2.0): the light scheme was a placeholder
 * (BatonColors.Background) before this commit. The calm
 * palette (the same tokens the dark scheme uses) now backs
 * the light scheme too. No red anywhere.
 */
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

/**
 * Tier 1.4 (v2.0): the theme now accepts an explicit
 * [darkTheme] override. The root composable (MainActivity)
 * computes `useDark = themeViewModel.mode == Dark` (or
 * `themeViewModel.mode == System && isSystemInDarkTheme()`)
 * and passes the result here, so the swap is immediate
 * without an app restart.
 */
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
            // v1.6.7: statusBarColor is deprecated in R+ (and a hard
            // deprecation in Android 15 / API 35). With
            // enableEdgeToEdge() in MainActivity the system bar
            // colour is now driven by the content's surface colour
            // (Material 3's windowInsets handling); the previous
            // explicit setStatusBarColor call is no longer needed
            // and the WindowInsetsController below is the supported
            // way to switch the status-bar icon tint for the
            // current theme.
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
