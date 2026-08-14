package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.CaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CaptureEntity?

    @Query("SELECT * FROM captures WHERE processed = 0 ORDER BY createdAt DESC")
    suspend fun unprocessed(): List<CaptureEntity>

    /**
     * v1.4.2 (F-09 / F-20): the [com.baton.app.data.sync.CaptureSyncWorker]
     * reads every row whose `syncStatus` is not `SYNCED` and pushes
     * it to Supabase. Returns the rows in `id ASC` order so the
     * FIFO drain is stable across passes (id is a client-generated
     * UUID but a stable lexical order is sufficient — we don't
     * promise a particular sync order to the user, just eventual
     * convergence).
     */
    @Query(
        "SELECT * FROM captures " +
            "WHERE syncStatus != :synced " +
            "ORDER BY id ASC",
    )
    suspend fun snapshotDirty(synced: String = "SYNCED"): List<CaptureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(capture: CaptureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(captures: List<CaptureEntity>)

    @Query("UPDATE captures SET processed = :processed, syncStatus = :status WHERE id = :id")
    suspend fun setProcessed(id: String, processed: Boolean, status: String)

    @Query("UPDATE captures SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}
