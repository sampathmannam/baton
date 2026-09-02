package com.kaavalan.note.data.groups

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupLabelDao {
    @Query("SELECT * FROM group_labels ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GroupLabelEntity>>

    @Query("SELECT * FROM group_labels WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): GroupLabelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: GroupLabelEntity): Long

    @Query("DELETE FROM group_labels WHERE id = :id")
    suspend fun deleteById(id: String)
}
