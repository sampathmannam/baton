package com.kaavalan.note.data.groups

import kotlinx.coroutines.flow.Flow

interface GroupLabelRepository {
    fun observeAll(): Flow<List<GroupLabel>>
    suspend fun create(name: String, responsiblePersonId: String?): GroupLabel
    suspend fun delete(id: String)
}
