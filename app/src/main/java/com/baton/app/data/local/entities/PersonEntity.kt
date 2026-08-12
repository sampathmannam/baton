package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of the `persons` table. One row per person the user
 * has saved. The Room copy is the source of truth for the UI; the
 * Supabase mirror is reconciled by [com.baton.app.data.local.RoomPersonRepository]
 * on every read (cold start) and on every Realtime event.
 *
 * **Sync state machine** ([syncStatus]):
 *  - `SYNCED`        — Room and Supabase are in agreement. Default after
 *                      an initial pull, or after a write completes.
 *  - `PENDING_INSERT` — created locally, not yet sent to Supabase. The
 *                      row carries a client-side UUID ([id]); on
 *                      successful drain the id stays (PostgREST accepts
 *                      the client id on insert).
 *  - `PENDING_UPDATE` — updated locally, the change has not been
 *                      POSTed to Supabase. The local row carries the
 *                      intended new values; drain sends a PATCH.
 *  - `PENDING_DELETE` — marked for deletion. The row stays in Room
 *                      until the drain succeeds, so the user sees the
 *                      deletion immediately; on success the row is
 *                      removed from Room.
 *
 * **Conflict resolution** (M2-T8): if the server's `updated_at` is
 * newer than the local one when the sync queue drains, the local
 * change is dropped and a row is inserted into `sync_conflicts` for
 * the audit trail. For M2-T6 the implementation is last-write-wins
 * by [updatedAt] (client-side wins on local writes, server-side wins
 * on remote Realtime events).
 */
@Entity(
    tableName = "persons",
    indices = [
        Index(value = ["name"]),
        Index(value = ["syncStatus"]),
    ],
)
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val designation: String?,
    val station: String?,
    val phone: String?,
    val userId: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = SyncStatus.SYNCED,
)
