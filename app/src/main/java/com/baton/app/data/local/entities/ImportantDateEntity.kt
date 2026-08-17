package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v2.0 Tier 2 (§2.5): one of N important dates per person. Examples:
 * "Birthday", "First met", "Anniversary" (any custom label is
 * allowed). `dateEpochDay` is `LocalDate.toEpochDay()` so the
 * "is this today" query is a single integer compare.
 *
 * Foreign key on [personId] with `ON DELETE CASCADE` so deleting a
 * person cleans up their dates. `createdAt` and `updatedAt` are
 * ISO-8601 strings (consistent with the rest of the schema).
 */
@Entity(
    tableName = "important_date",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["personId"]),
        Index(value = ["dateEpochDay"]),
    ],
)
data class ImportantDateEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val label: String,
    val dateEpochDay: Long,
    val recurring: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
