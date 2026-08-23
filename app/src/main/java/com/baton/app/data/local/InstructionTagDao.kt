package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.InstructionTagCrossRef
import com.baton.app.data.local.entities.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * M3-T7: DAO for the `instruction_tags` join table. The composite
 * primary key (instructionId, tagId) means [attach] is a no-op when
 * the pair already exists — exactly what the LLM extraction wants
 * when it produces a duplicate `#tag` for an instruction.
 */
@Dao
interface InstructionTagDao {

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN instruction_tags it ON it.tagId = t.id
        WHERE it.instructionId = :instructionId
        ORDER BY t.kind, t.name
        """,
    )
    fun observeForInstruction(instructionId: String): Flow<List<TagEntity>>

    @Query(
        """
        SELECT it.instructionId FROM instruction_tags it
        WHERE it.tagId = :tagId
        """,
    )
    fun observeInstructionsForTag(tagId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(ref: InstructionTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachAll(refs: List<InstructionTagCrossRef>)

    @Query("DELETE FROM instruction_tags WHERE instructionId = :instructionId AND tagId = :tagId")
    suspend fun detach(instructionId: String, tagId: String)

    @Query("DELETE FROM instruction_tags WHERE instructionId = :instructionId")
    suspend fun detachAllForInstruction(instructionId: String)

    // v1.6.2: bulk delete + bulk insert for the developer fixture
    // loader. The attachAll above uses IGNORE conflict; we need
    // REPLACE for the loader so re-running with a fresh fixture
    // overwrites cleanly. (The clear step before insert handles
    // that, but REPLACE makes the loader safe to call against a
    // non-empty table too.)
    @Query("DELETE FROM instruction_tags")
    suspend fun deleteAll()

    // v1.8.0 (PROD-READINESS-P0-#1): full snapshot for the
    // BackupManager. The existing per-instruction and per-tag
    // observers are Flow-based and not usable from a one-shot
    // backup snapshot. The composite primary key is the natural
    // order so two snapshots are stable.
    @Query("SELECT * FROM instruction_tags ORDER BY instructionId ASC, tagId ASC")
    suspend fun snapshotAll(): List<InstructionTagCrossRef>
}
