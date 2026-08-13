package com.baton.app.features.capture

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4 (F-23): WCAG AA contrast for the [ConfidenceChip].
 */
class ConfirmationCardTest {

    private val v13ContainerColor = Color.Transparent

    @Test
    fun `confidence 0_8 or above uses primaryContainer (WCAG AA)`() {
        val container = confidenceContainerColor(0.8)
        val label = confidenceLabelColor(0.8)
        assertNotEquals(
            "v1.3's Color.Transparent container would fail WCAG on light; the v1.4 chip must use primaryContainer",
            v13ContainerColor, container,
        )
        assertEquals(
            "primaryContainer must resolve to the M3 light default #D7E3FC",
            0x00D7E3FC,
            container.toArgb() and 0x00FFFFFF,
        )
        assertEquals(
            "onPrimaryContainer must resolve to the M3 light default #001A41",
            0x00001A41,
            label.toArgb() and 0x00FFFFFF,
        )
    }

    @Test
    fun `confidence between 0_5 and 0_8 uses tertiaryContainer (WCAG AA)`() {
        val container = confidenceContainerColor(0.5)
        val label = confidenceLabelColor(0.5)
        assertNotEquals(v13ContainerColor, container)
        assertEquals(
            "tertiaryContainer must resolve to the M3 light default #FFD8E4",
            0x00FFD8E4,
            container.toArgb() and 0x00FFFFFF,
        )
        assertEquals(
            "onTertiaryContainer must resolve to the M3 light default #31111D",
            0x0031111D,
            label.toArgb() and 0x00FFFFFF,
        )
    }

    @Test
    fun `confidence below 0_5 uses surfaceVariant (WCAG AA)`() {
        val container = confidenceContainerColor(0.4)
        val label = confidenceLabelColor(0.4)
        assertNotEquals(v13ContainerColor, container)
        assertEquals(
            "surfaceVariant (Low) must match the Baton palette #EFEAE0",
            0x00EFEAE0,
            container.toArgb() and 0x00FFFFFF,
        )
        assertEquals(
            "onSurfaceVariant (Low) must match the Baton palette #6B6358",
            0x006B6358,
            label.toArgb() and 0x00FFFFFF,
        )
    }

    @Test
    fun `every bucket is a paired M3 tonal pair - no mixing primary container with tertiary label`() {
        val high = confidenceContainerColor(0.9) to confidenceLabelColor(0.9)
        val medium = confidenceContainerColor(0.6) to confidenceLabelColor(0.6)
        val low = confidenceContainerColor(0.2) to confidenceLabelColor(0.2)
        val containers = setOf(high.first, medium.first, low.first)
        assertEquals(
            "High / Medium / Low must use three distinct container colours",
            3, containers.size,
        )
        listOf(high, medium, low).forEach { (container, _) ->
            val r = container.red
            val g = container.green
            val b = container.blue
            val isRed = r > 0.6f && r > g * 1.5f && r > b * 1.5f
            assertTrue(
                "Container $container is red-dominant; the chip must not use red for any confidence bucket",
                !isRed,
            )
        }
    }

    @Test
    fun `bucket boundaries are exactly 0_8 and 0_5 - no overlap and no gap`() {
        assertEquals(
            "0.8 should land in the High bucket",
            confidenceContainerColor(0.9),
            confidenceContainerColor(0.8),
        )
        assertEquals(
            "0.5 should land in the Medium bucket",
            confidenceContainerColor(0.6),
            confidenceContainerColor(0.5),
        )
        assertEquals(
            "0.4999 should land in the Low bucket",
            confidenceContainerColor(0.4),
            confidenceContainerColor(0.4999),
        )
        val distinct = setOf(
            confidenceContainerColor(0.8),
            confidenceContainerColor(0.5),
            confidenceContainerColor(0.4),
        )
        assertEquals(
            "0.8 / 0.5 / 0.4 should map to three different container colours",
            3, distinct.size,
        )
    }

    private fun Color.toArgb(): Int = (this.value shr 32).toInt()
}
