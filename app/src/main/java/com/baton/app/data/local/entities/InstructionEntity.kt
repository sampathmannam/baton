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
        // v2.0 T3-1: the deniable-vault filter. An instruction
        // inherits the vault mode of its owning person; the
        // (personId, vaultMode) index is the natural composite
        // the list filter relies on.
        Index(value = ["vaultMode"]),
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
    // v2.0 T3-1: deniable vault. See [PersonEntity.vaultMode]
    // for the threat-model note. An instruction's vaultMode is
    // mirrored from its person at create time; a person-level
    // flip propagates to the instructions via the
    // RoomInstructionRepository.
    val vaultMode: String = "visible",
)
