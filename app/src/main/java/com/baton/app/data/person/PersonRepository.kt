package com.baton.app.data.person

import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    /**
     * M2-T6: the read path is a Flow from Room. The UI collects
     * this; Room emits a new list on every local write. The
     * M2-T7 Realtime subscription calls [com.baton.app.data.local.RoomPersonRepository.refreshFromNetwork]
     * on every server change, which feeds Room, which re-emits.
     */
    fun observeAll(): Flow<List<Person>>

    suspend fun create(
        name: String,
        designation: String?,
        station: String?,
        clientId: String? = null,
    ): Person

    /**
     * M1-T5: find a person by name. Returns the first row with the
     * given [name] for the calling user, ignoring designation / station.
     * Returns `null` if no match. The unique constraint on
     * `(user_id, name, designation, station)` is permissive — a user
     * could in theory have two "Ramu"s with different stations; we pick
     * the first row and let the user disambiguate via the UI later
     * (M3's person picker will use this).
     */
    suspend fun findByName(name: String): Person?

    /**
     * M1-T5: find an existing person by name, or create one. Used by
     * the M1-T5 save flow to auto-create the person named in the LLM
     * proposal. The unique constraint
     * `(user_id, name, designation, station)` prevents duplicates
     * when a race creates the same person twice; the loser of the
     * race will get a 409 and the impl retries the lookup.
     */
    suspend fun findOrCreate(name: String, designation: String? = null, station: String? = null): Person

    /**
     * v1.1: spec §13 — flip the `is_sensitive` flag. The row stays
     * in Room (the user is still tracking the person) but the sync
     * engine stops pushing it to the server on the next change.
     * Toggling on for an already-synced row also PATCHes the
     * server so the server copy is removed.
     */
    suspend fun setSensitive(id: String, sensitive: Boolean)
}


