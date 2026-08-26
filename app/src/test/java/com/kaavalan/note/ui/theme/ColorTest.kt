package com.kaavalan.note.ui.theme

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
            KaavalanColors.Quiet,
            KaavalanColors.Primary,
            KaavalanColors.Surface,
            KaavalanColors.OnSurface,
            KaavalanColors.OnSurfaceMuted,
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
        val quiet = KaavalanColors.Quiet
        val r = quiet.red
        val g = quiet.green
        assertTrue("Amber needs significant green", g > 0.4f)
        assertTrue("Amber has red component", r > 0.6f)
    }
}
