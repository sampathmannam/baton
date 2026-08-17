package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * v2.0 Tier 2 (§2.12): a directed edge between two people with a
 * free-form relation label. Examples: "Reports to", "Knows",
 * "Family of", "Classmate of", and any custom string the user
 * types.
 *
 * Self-loops (from == to) are accepted at the DB layer; the UI
 * prevents them. Composite primary key on `(fromId, toId, relation)`
 * lets the same pair of people have multiple distinct relations
 * ("mentor of" + "former colleague of" on the same pair).
 *
 * Foreign keys on both columns with `ON DELETE CASCADE` so deleting
 * a person removes their edges.
 */
@Entity(
    tableName = "person_link",
    primaryKeys = ["fromId", "toId", "relation"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["toId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["toId"]),
    ],
)
data class PersonLinkEntity(
    val fromId: String,
    val toId: String,
    val relation: String,
    val createdAt: String,
)
