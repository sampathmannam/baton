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
 *
 * v2.0 (Tier 1.5): added `nextActionAt: Long?` for the date
 * picker on each instruction. Optional; the user can leave it
 * blank. `null` = "no scheduled next action".
 * v2.0 Tier 2 fields (migration v10 -> v11):
 *  - [caseType]: optional typed-block marker for §2.8 ("Case" | "Witness"
 *    | "FIR" | "Other" | null). Null = freeform instruction.
 *  - [urgency]: §2.10 worry-box marker. "normal" | "worry" | "worry_with_date".
 *  - [reviewAtEpochDay]: §2.10 "worry with date" — the day the user
 *    wants to revisit the worry capture. `LocalDate.toEpochDay()`.
 */
@Entity(
    tableName = "instructions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["personId"]),
        Index(value = ["dueAt"]),
        Index(value = ["syncStatus"]),
        Index(value = ["urgency"]),
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
    // v2.0 (Tier 1.5): optional scheduled next action (epoch millis).
    val nextActionAt: Long? = null,
    // v2.0 Tier 2 (§2.8): typed-block marker. Null = freeform
    // instruction. Valid values: "Case" | "Witness" | "FIR" | "Other".
    val caseType: String? = null,
    // v2.0 Tier 2 (§2.10): worry-box marker. "normal" (default) |
    // "worry" | "worry_with_date". Worry rows render in the
    // WorryBox section on Today.
    val urgency: String = "normal",
    // v2.0 Tier 2 (§2.10): if urgency == "worry_with_date", the
    // epoch-day on which the user wants to revisit the worry.
    val reviewAtEpochDay: Long? = null,
)
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
