package com.baton.app.data.person

/**
 * Person domain model. The Room row carries more bookkeeping
 * fields ([com.baton.app.data.local.entities.PersonEntity] has
 * [userId], [createdAt], [updatedAt], [syncStatus]); the domain
 * model exposes only what the UI needs plus [updatedAt] (M2-T8)
 * so the sync engine can do last-write-wins on writes.
 *
 * [updatedAt] is the ISO-8601 timestamp the server (or local
 * creator) set on the row. String, not `Instant`, because the
 * value is fed back into SQL queries and the server's `text`
 * column would round-trip; comparing strings is correct because
 * the format is fixed-width and lexicographic = chronological.
 */
data class Person(
    val id: String,
    val name: String,
    val designation: String?,
    val station: String?,
    val phone: String?,
    val updatedAt: String? = null,
    // v1.0: spec §13. When true, this row + its instructions
    // never sync to Supabase.
    val isSensitive: Boolean = false,
)
