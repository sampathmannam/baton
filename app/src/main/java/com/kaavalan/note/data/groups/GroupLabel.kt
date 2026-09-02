package com.kaavalan.note.data.groups

data class GroupLabel(
    val id: String,
    val name: String,
    val responsiblePersonId: String?,
    val createdAt: String,
    val updatedAt: String,
)
