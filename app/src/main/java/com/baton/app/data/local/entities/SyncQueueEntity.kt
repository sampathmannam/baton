package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox of pending writes. Every create / update / delete that the
 * user makes while offline (or that fails to POST) lands here and
 * is drained by the [com.baton.app.data.local.SyncEngine] when
 * connectivity returns.
 *
 * **Schema:**
 *  - [table]     — `"persons" | "instructions" | "captures"`
 *  - [rowId]     — the local row's `id` (client-generated UUID).
 *  - [op]        — `"INSERT" | "UPDATE" | "DELETE"`
 *  - [payloadJson] — `kotlinx.serialization.Json` of the row as the
 *                    Supabase API expects it. Stored as a string to
 *                    keep the schema flat.
 *  - [attempts]  — number of drain tries. Used to back off or
 *                    surface permanently-failed writes.
 *  - [lastError] — the message from the most recent failed drain;
 *                    `null` if the entry has never been attempted.
 *
 * **FIFO drain order:** entries are ordered by `id ASC` (auto-
 * generated), which is monotonic with `createdAt`. The drain
 * reads in this order, preserving the causal order of writes.
 *
 * **Per-row singleton invariant:** at most one non-deleted entry
 * per `(table, rowId, op)` at a time. The repository checks
 * before enqueueing.
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["table", "rowId", "op"]),
    ],
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val table: String,
    val rowId: String,
    val op: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    companion object {
        const val OP_INSERT = "INSERT"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }
}
