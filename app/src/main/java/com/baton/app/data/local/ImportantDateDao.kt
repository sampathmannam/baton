package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.ImportantDateEntity
import kotlinx.coroutines.flow.Flow

/**
 * v2.0 Tier 2 (§2.5): DAO for the `important_date` table. One row
 * per person-date pair. `dateEpochDay` is `LocalDate.toEpochDay()`
 * (Long), so the "is today" / "in next 7 days" queries are simple
 * integer comparisons.
 *
 * Cascade delete is set up at the table level: removing a
 * [com.baton.app.data.local.entities.PersonEntity] wipes their
 * dates (foreign_keys = ON is enabled in [DatabaseModule.onOpenPragmaCallback]).
 */
@Dao
interface ImportantDateDao {

    @Query("SELECT * FROM important_date WHERE personId = :personId ORDER BY dateEpochDay ASC")
    fun observeForPerson(personId: String): Flow<List<ImportantDateEntity>>

    @Query("SELECT * FROM important_date WHERE dateEpochDay = :epochDay ORDER BY label")
    fun observeOnDay(epochDay: Long): Flow<List<ImportantDateEntity>>

    @Query("SELECT * FROM important_date WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY dateEpochDay ASC")
    fun observeBetweenDays(fromEpochDay: Long, toEpochDay: Long): Flow<List<ImportantDateEntity>>

    @Query("SELECT * FROM important_date WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ImportantDateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(date: ImportantDateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(dates: List<ImportantDateEntity>)

    @Query("DELETE FROM important_date WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM important_date WHERE personId = :personId")
    suspend fun deleteForPerson(personId: String)
}
