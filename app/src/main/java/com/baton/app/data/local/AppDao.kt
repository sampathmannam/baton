package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.AppStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * M4-T6: DAO for the local `app_state` mirror. RLS on the server
 * already restricts rows to the calling user; the local mirror is
 * a write-through cache, never a write source for other users'
 * data.
 */
@Dao
interface AppDao {

    @Query("SELECT * FROM app_state WHERE source = :source ORDER BY `key` ASC")
    fun observeBySource(source: String): Flow<List<AppStateEntity>>

    @Query("SELECT * FROM app_state WHERE source = :source AND `key` = :key LIMIT 1")
    suspend fun get(source: String, key: String): AppStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AppStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AppStateEntity>)

    @Query("DELETE FROM app_state WHERE id = :id")
    suspend fun deleteById(id: String)
}
