package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * M3-T7: composite-key join table from spec §4.3. Each row links one
 * instruction to one tag. The cloud table is `instruction_tags`; the
 * RLS policies there require the calling user to own the parent
 * instruction (the migration in `0001_init.sql` defines the policy).
 *
 * **No syncStatus column.** Inserts and deletes are atomic SQL
 * operations; if the device loses connectivity mid-write, the next
 * [com.baton.app.data.local.SyncEngine] drain of the parent
 * `instructions` row will trigger a `refreshFromNetwork` and the
 * server-truthful instruction_tags set will be mirrored back. We
 * deliberately keep this table simpler than the person/instruction
 * mirrors — there's no edit-in-place conflict possible.
 */
@Entity(
    tableName = "instruction_tags",
    primaryKeys = ["instructionId", "tagId"],
    indices = [
        Index(value = ["tagId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = InstructionEntity::class,
            parentColumns = ["id"],
            childColumns = ["instructionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class InstructionTagCrossRef(
    val instructionId: String,
    val tagId: String,
)
