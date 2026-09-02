package com.kaavalan.note.data.groups

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_labels",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["responsiblePersonId"]),
    ],
)
data class GroupLabelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val responsiblePersonId: String?,
    val createdAt: String,
    val updatedAt: String,
)

internal fun GroupLabelEntity.toDomain(): GroupLabel = GroupLabel(
    id = id,
    name = name,
    responsiblePersonId = responsiblePersonId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
