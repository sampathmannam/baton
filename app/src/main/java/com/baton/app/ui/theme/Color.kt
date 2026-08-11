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
