package com.kaavalan.note.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Audit row for a write that lost to the server. The local copy is
 * preserved here so the user can review what they tried to change
 * vs. what the server already had.
 *
 * **Schema:**
 *  - [tableName]   — `"persons" | "instructions" | "captures"`.
 *  - [rowId]      — the local row's id (matches the conflicted row).
 *  - [localPayload] — the local update that was dropped, in
 *                     `kotlinx.serialization.Json` form.
 *  - [serverPayload] — what the server had at the time of conflict.
 *  - [reason]     — `"server_newer"` (LWW) | `"version_mismatch"` |
 *                    future values.
 *  - [detectedAt] — epoch millis when the conflict was logged.
 *
 * **Read path** is M3 (the people list shows "Last write lost
 * to server, see history" with a link to the conflict row). For
 * M2 the table is write-only; rows accumulate in the local DB
 * and the user can inspect via a debug screen later.
 */
@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["tableName", "rowId"]),
        Index(value = ["detectedAt"]),
    ],
)
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val rowId: String,
    val localPayload: String,
    val serverPayload: String,
    val reason: String,
    val detectedAt: Long,
)
