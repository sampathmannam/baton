package com.kaavalan.note.data.person

/**
 * v2.0 Tier 2 (§2.2): the closed set of relationship tiers and
 * their default cadences (in days).
 *
 *  - Inner    : 7  days (close family, daily-ish contact)
 *  - Active   : 30 days (working colleagues, monthly check-in)
 *  - Periodic : 90 days (occasional contacts, quarterly)
 *  - Dormant  : 180 days (archived / past relationships)
 *
 * `defaultDaysFor(tier)` is the canonical mapping; callers that
 * want a per-person override use
 * `Person.cadenceOverrideDays ?: defaultDaysFor(tier)`.
 *
 * The `tier` value is a `String` at the storage layer (forward
 * compatibility for user-defined tiers in a future release), but
 * the canonical four are kept here.
 */
object TierCadence {
    const val TIER_INNER = "Inner"
    const val TIER_ACTIVE = "Active"
    const val TIER_PERIODIC = "Periodic"
    const val TIER_DORMANT = "Dormant"

    const val DEFAULT_INNER_DAYS = 7
    const val DEFAULT_ACTIVE_DAYS = 30
    const val DEFAULT_PERIODIC_DAYS = 90
    const val DEFAULT_DORMANT_DAYS = 180

    val ALL_TIERS: List<String> = listOf(
        TIER_INNER, TIER_ACTIVE, TIER_PERIODIC, TIER_DORMANT,
    )

    fun defaultDaysFor(tier: String): Int = when (tier) {
        TIER_INNER -> DEFAULT_INNER_DAYS
        TIER_ACTIVE -> DEFAULT_ACTIVE_DAYS
        TIER_PERIODIC -> DEFAULT_PERIODIC_DAYS
        TIER_DORMANT -> DEFAULT_DORMANT_DAYS
        else -> DEFAULT_ACTIVE_DAYS
    }

    /**
     * Effective cadence = override if present, else tier default.
     * Used by the decay view (§2.1) and the redistribution
     * logic (§2.14).
     */
    fun effectiveDays(tier: String, overrideDays: Int?): Int =
        overrideDays ?: defaultDaysFor(tier)
}
