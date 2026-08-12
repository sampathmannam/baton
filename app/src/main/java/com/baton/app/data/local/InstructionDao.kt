package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.InstructionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstructionDao {

    @Query("SELECT * FROM instructions ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<InstructionEntity>>

    @Query("SELECT * FROM instructions WHERE personId = :personId ORDER BY capturedAt DESC")
    fun observeForPerson(personId: String): Flow<List<InstructionEntity>>

    @Query("SELECT * FROM instructions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InstructionEntity?

    @Query("SELECT * FROM instructions ORDER BY capturedAt DESC")
    suspend fun snapshot(): List<InstructionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(instruction: InstructionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(instructions: List<InstructionEntity>)

    @Query("UPDATE instructions SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)

    @Query("DELETE FROM instructions WHERE id = :id")
    suspend fun deleteById(id: String)
}
