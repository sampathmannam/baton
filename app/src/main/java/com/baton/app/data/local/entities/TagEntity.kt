package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M3-T7: Room mirror of the `tags` table (spec §4.3). One row per
 * tag the user has ever used; auto-created on first sighting of a
 * person name, designation, station, FIR number, or free-form
 * `#tag` from the LLM extraction.
 *
 * **Kind drives UI color** — PERSON/DESIGNATION/STATION/CASE/FIR/PRIORITY
 * get a colored chip; FREE tags render plain. The colour is the
 * server-side `color` column (hex string) the user can override on
 * the tag management screen.
 *
 * **Sync state machine** mirrors PersonEntity (SYNCED / PENDING_INSERT
 * / PENDING_UPDATE). The `usage_count` is recomputed by the server on
 * every instruction_tags insert/delete; the client mirrors it.
 */
@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["syncStatus"]),
    ],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,  // PERSON | DESIGNATION | STATION | CASE | FIR | PRIORITY | FREE
    val color: String?,
    val usageCount: Int = 0,
    val lastUsedAt: String?,
    val userId: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = SyncStatus.SYNCED,
)
