package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of the `instructions` table. Mirrors
 * [com.baton.app.data.instructions.Instruction]; the `syncStatus`
 * column follows the same state machine as [PersonEntity].
 *
 * v1.1: added `completedAt` and `droppedReason` columns for the
 * mark-done / mark-drop flow (Room v8). The Postgres schema already
 * had these columns from M0; the Room mirror only just learned to
 * read/write them. The repository sets `completedAt = now()` on
 * markDone and `droppedReason` on markDropped.
 */
@Entity(
    tableName = "instructions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["personId"]),
        Index(value = ["dueAt"]),
        Index(value = ["syncStatus"]),
    ],
)
data class InstructionEntity(
    @PrimaryKey val id: String,
    val personId: String?,
    val direction: String,
    val status: String,
    val source: String,
    val priority: String,
    val title: String,
    val rawText: String,
    val dueAt: String?,
    val capturedAt: String,
    val createdAt: String,
    val updatedAt: String,
    // v1.0: is_sensitive flag (spec §13). When true, the row
    // never syncs to Supabase; it lives in the local SQLCipher
    // mirror only. The sync engine filters these out before any
    // network read/write.
    val isSensitive: Boolean = false,
    val syncStatus: String = SyncStatus.SYNCED,
    // v1.1: lifecycle fields. Set by mark-done / mark-drop.
    val completedAt: String? = null,
    val droppedReason: String? = null,
)
