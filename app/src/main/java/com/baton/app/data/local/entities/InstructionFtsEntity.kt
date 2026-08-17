package com.baton.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Tier 1.3 (v2.0): FTS4 table for full-text search over
 * instructions. We use a "free" FTS4 entity (no
 * `contentEntity`) — the search rows are written to and
 * read from the FTS table directly by the
 * [com.baton.app.data.local.InstructionFtsDao]. The
 * `InstructionRepository` keeps the FTS table in sync via
 * a `RoomDatabase.runInTransaction { ... }` call after
 * every insert / update.
 *
 * The schema: rowid (auto), title, rawText, personId,
 * capturedAt. The `porter` tokenizer folds
 * "follow"/"followed"/"follows" to the same stem, so
 * common verb / noun forms match a single search token.
 */
@Fts4(tokenizer = "porter")
@Entity(tableName = "instructions_fts")
data class InstructionFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid")
    val rowid: Long,
    val title: String,
    val rawText: String,
    val personId: String?,
    val capturedAt: String,
)
