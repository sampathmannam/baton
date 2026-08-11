package com.baton.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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
