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
}
