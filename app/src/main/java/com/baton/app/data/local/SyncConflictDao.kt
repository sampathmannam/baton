package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conflict: SyncConflictEntity): Long

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC")
    fun observe(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE tableName = :tableName AND rowId = :rowId ORDER BY detectedAt DESC")
    suspend fun forRow(tableName: String, rowId: String): List<SyncConflictEntity>

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    suspend fun count(): Int
}
