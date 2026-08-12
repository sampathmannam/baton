package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.PersonEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `persons` table.
 *
 * **Read path** is Flow-based so the HomeViewModel can collect from
 * Room directly. Room is the single source of truth for the UI.
 *
 * **Write path** is suspend; the repository wraps each call in a
 * coroutine and decides whether to enqueue a sync op afterwards.
 */
@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PersonEntity?

    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE ASC")
    suspend fun snapshot(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(persons: List<PersonEntity>)

    @Query("UPDATE persons SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String, updatedAt: String)

    @Query("UPDATE persons SET updatedAt = :updatedAt, syncStatus = :status, name = :name, designation = :designation, station = :station, phone = :phone WHERE id = :id")
    suspend fun updateLocal(
        id: String,
        name: String,
        designation: String?,
        station: String?,
        phone: String?,
        updatedAt: String,
        status: String,
    )

    /**
     * v1.1: spec §13 — flip the local-only flag. The sync engine
     * filters sensitive rows on the way out, so toggling on for
     * an already-synced row needs a PATCH to the server too
     * (the server should drop the row from its own copy).
     */
    @Query("UPDATE persons SET isSensitive = :sensitive, updatedAt = :updatedAt, syncStatus = :status WHERE id = :id")
    suspend fun setSensitive(id: String, sensitive: Boolean, updatedAt: String, status: String)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: String)
}
