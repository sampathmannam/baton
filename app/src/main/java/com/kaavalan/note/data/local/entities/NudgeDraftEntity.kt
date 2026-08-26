package com.kaavalan.note.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M4-T4: Room mirror of the `nudge_drafts` table (spec §4.6).
 */
@Entity(
    tableName = "nudge_drafts",
    indices = [
        Index(value = ["instructionId"]),
        Index(value = ["status"]),
    ],
)
data class NudgeDraftEntity(
    @PrimaryKey val id: String,
    val instructionId: String,
    val draftText: String,
    val status: String,
    val sentVia: String?,
    val sentAt: String?,
    val createdAt: String,
    val syncStatus: String = SyncStatus.SYNCED,
)
