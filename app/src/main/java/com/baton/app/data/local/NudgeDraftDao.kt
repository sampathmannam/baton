package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.NudgeDraftEntity
import kotlinx.coroutines.flow.Flow

/**
 * M4-T4: DAO for the local `nudge_drafts` mirror.
 */
@Dao
interface NudgeDraftDao {

    @Query("SELECT * FROM nudge_drafts WHERE instructionId = :instructionId ORDER BY createdAt DESC")
    fun observeForInstruction(instructionId: String): Flow<List<NudgeDraftEntity>>

    @Query("SELECT * FROM nudge_drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NudgeDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: NudgeDraftEntity)

    @Query("UPDATE nudge_drafts SET draftText = :text WHERE id = :id")
    suspend fun updateText(id: String, text: String)

    @Query("UPDATE nudge_drafts SET status = 'SENT', sentVia = :sentVia, sentAt = :sentAt WHERE id = :id")
    suspend fun markSent(id: String, sentVia: String, sentAt: String)

    @Query("UPDATE nudge_drafts SET status = 'CANCELLED' WHERE id = :id")
    suspend fun cancel(id: String)
}
