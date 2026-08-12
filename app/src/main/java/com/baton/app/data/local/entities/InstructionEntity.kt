package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of the `instructions` table. Mirrors
 * [com.baton.app.data.instructions.Instruction]; the `syncStatus`
 * column follows the same state machine as [PersonEntity].
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
)
