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

    /**
     * v2.0 T3-1 (deniable vault): the HomeViewModel observes
     * `persons WHERE vaultMode = :mode` so the list shows only
     * the rows the user has access to in the current mode. The
     * `vaultMode` column is indexed (see PersonEntity @Index).
     */
    @Query("SELECT * FROM persons WHERE vaultMode = :mode ORDER BY name COLLATE NOCASE ASC")
    fun observeAllInMode(mode: String): Flow<List<PersonEntity>>

    /**
     * v2.0 T3-1: count of persons in the OTHER mode (so the
     * HomeScreen can render an "X items in vault" affordance
     * when the user is in visible mode and there are hidden
     * rows; in hidden mode the same affordance is suppressed).
     */
    @Query("SELECT COUNT(*) FROM persons WHERE vaultMode = :mode")
    fun observeCountInMode(mode: String): Flow<Int>

    /**
     * v2.0 T3-1: flip a single person's vault mode. The repository
     * also updates the person's instructions (and propagates the
     * sync outbox) so the rest of the UI doesn't see orphan rows.
     */
    @Query("UPDATE persons SET vaultMode = :mode, updatedAt = :updatedAt, syncStatus = :status WHERE id = :id")
    suspend fun setVaultMode(id: String, mode: String, updatedAt: String, status: String)

    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PersonEntity?

    /**
     * v1.1.1: reactive read for the PersonDetailViewModel. Emits
     * every time the row changes (e.g. when [setSensitive]
     * toggles the local flag). The previous one-shot
     * [getById] would not re-emit on a local update, so the
     * detail screen's "Mark as sensitive" button stayed in its
     * old state after a tap even though the local Room row
     * flipped correctly.
     */
    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PersonEntity?>

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
