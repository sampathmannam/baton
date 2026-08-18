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
     * v2.0 Tier 2: full snapshot of the captures table, used
     * by the meeting-brief view (which lists the 3 most recent
     * photos and 3 most recent notes) and by the Today's win
     * summary. `observeAll()` is a Flow; `snapshot()` is a
     * one-shot read.
     */
    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    suspend fun snapshot(): List<CaptureEntity>

    /**
     * v1.4.2 (F-09 / F-20): the [com.baton.app.data.sync.CaptureSyncWorker]
     * reads every row whose `syncStatus` is not `SYNCED` and pushes
     * it to Supabase. Returns the rows in `id ASC` order so the
     * FIFO drain is stable across passes (id is a client-generated
     * UUID but a stable lexical order is sufficient - we don't
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

    // v2.0 Tier 2 ----

    /**
     * §2.4: store the OCR result for a PHOTO capture. The
     * `TextRecognizer.process()` call returns the recognised
     * text; we persist it on the same row so the detail surface
     * can render it below the thumbnail.
     */
    @Query("UPDATE captures SET ocrText = :ocrText WHERE id = :id")
    suspend fun setOcrText(id: String, ocrText: String?)

    // v1.6.2: bulk delete for the developer fixture loader.
    @Query("DELETE FROM captures")
    suspend fun deleteAll()

    /**
     * §2.9: link a capture to a calendar event. Set when the
     * user accepts the "Attach to your next event" prompt. Null
     * to clear.
     */
    @Query("UPDATE captures SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: String, eventId: String?)

    // v2.0 Tier 2 (§2.11, §2.10) ----

    /**
     * Captures created in the last [sinceMs] epoch-millis, used
     * by the "Today's win" summary (§2.11) and the
     * WorryBox for capture-level worries.
     */
    @Query("SELECT * FROM captures WHERE createdAt >= :sinceMsIso ORDER BY createdAt DESC")
    fun observeSince(sinceMsIso: String): Flow<List<CaptureEntity>>

    /**
     * §2.10: worry capture query. Worry captures appear in the
     * WorryBox section. status NOT IN ('DONE', 'DROPPED') is not
     * a thing on captures (they don't have a status column) so
     * we filter by the worry-bucket pair + not-marked-as-closed
     * (TODO: keep worry → reviewAt cleared, still in worry box).
     */
    @Query(
        """
        SELECT * FROM captures
        WHERE urgency IN ('worry', 'worry_with_date')
        ORDER BY
            CASE WHEN reviewAtEpochDay IS NULL THEN 1 ELSE 0 END,
            reviewAtEpochDay ASC,
            createdAt DESC
        """,
    )
    fun observeWorry(): Flow<List<CaptureEntity>>

    @Query("UPDATE captures SET urgency = 'normal', reviewAtEpochDay = NULL WHERE id = :id")
    suspend fun resolveWorry(id: String)

    @Query("UPDATE captures SET reviewAtEpochDay = NULL WHERE id = :id")
    suspend fun keepWorry(id: String)

    /**
     * §2.4: query for the OCR text of a single capture. Used by
     * PersonDetailScreen to render the OCR'd text below each
     * photo thumbnail.
     */
    @Query("SELECT ocrText FROM captures WHERE id = :id LIMIT 1")
    suspend fun getOcrText(id: String): String?
}
