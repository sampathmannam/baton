package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY id ASC")
    suspend fun snapshot(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY id ASC")
    fun observe(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE `table` = :table AND rowId = :rowId AND op = :op LIMIT 1")
    suspend fun findPending(table: String, rowId: String, op: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(entry: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String)
}
