package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of the `captures` table. Mirrors
 * [com.baton.app.data.captures.Capture]. `mode` is stored as the
 * string form ("TEXT" | "VOICE" | "PHOTO") for direct read parity
 * with Supabase; the in-memory conversion happens at the
 * repository boundary.
 */
@Entity(
    tableName = "captures",
    indices = [
        Index(value = ["mode"]),
        Index(value = ["processed"]),
        Index(value = ["syncStatus"]),
    ],
)
data class CaptureEntity(
    @PrimaryKey val id: String,
    val mode: String,
    val rawText: String?,
    val audioUri: String?,
    val imageUri: String?,
    val processed: Boolean,
    val createdAt: String,
    val syncStatus: String = SyncStatus.SYNCED,
)
