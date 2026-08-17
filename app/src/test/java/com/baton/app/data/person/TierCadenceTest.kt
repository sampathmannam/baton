package com.baton.app.data.person

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v2.0 Tier 2 (§2.2): the [TierCadence] constants + lookup
 * helpers. The defaults drive the decay view; the [effectiveDays]
 * helper handles the per-person override.
 */
class TierCadenceTest {

    @Test
    fun `default days match the spec for each tier`() {
        assertEquals(7, TierCadence.defaultDaysFor(TierCadence.TIER_INNER))
        assertEquals(30, TierCadence.defaultDaysFor(TierCadence.TIER_ACTIVE))
        assertEquals(90, TierCadence.defaultDaysFor(TierCadence.TIER_PERIODIC))
        assertEquals(180, TierCadence.defaultDaysFor(TierCadence.TIER_DORMANT))
    }

    @Test
    fun `default days for unknown tier falls back to Active`() {
        assertEquals(30, TierCadence.defaultDaysFor("Unknown"))
        assertEquals(30, TierCadence.defaultDaysFor(""))
    }

    @Test
    fun `effective days uses override when present`() {
        assertEquals(14, TierCadence.effectiveDays(TierCadence.TIER_ACTIVE, 14))
        assertEquals(7, TierCadence.effectiveDays(TierCadence.TIER_INNER, 7))
    }

    @Test
    fun `effective days falls back to tier default when override is null`() {
        assertEquals(30, TierCadence.effectiveDays(TierCadence.TIER_ACTIVE, null))
        assertEquals(7, TierCadence.effectiveDays(TierCadence.TIER_INNER, null))
        assertEquals(180, TierCadence.effectiveDays(TierCadence.TIER_DORMANT, null))
    }

    @Test
    fun `ALL_TIERS contains the four canonical tiers in order`() {
        assertEquals(
            listOf("Inner", "Active", "Periodic", "Dormant"),
            TierCadence.ALL_TIERS,
        )
    }
}
