package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M4-T6: Room mirror of the `app_state` table (spec §4.8). Holds
 * cross-app state shared with MindAnchor (and any other integrated
 * app). The id is `${source}:${key}` (composite of the unique
 * constraint on the server) so local upserts are idempotent.
 */
@Entity(
    tableName = "app_state",
    indices = [
        Index(value = ["source"]),
    ],
)
data class AppStateEntity(
    @PrimaryKey val id: String,
    val source: String,  // BATON | MINDANCHOR
    val `key`: String,
    val valueJson: String,
    val updatedAt: String,
)
