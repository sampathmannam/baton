package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncStatus
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

    // v1.6.2: bulk delete for the developer fixture loader.
    @Query("DELETE FROM instructions")
    suspend fun deleteAll()

    // v1.1: state transitions. The repository sets `completedAt = now()`
    // when status = DONE, and `droppedReason` when status = DROPPED.
    // All transitions touch `updatedAt` so the brief's 7-day window
    // starts fresh (a user can't "carry over" a row by never touching
    // it; reopening/marking-done both reset the clock).
    @Query(
        """
        UPDATE instructions
        SET status = :status,
            updatedAt = :updatedAt,
            completedAt = :completedAt,
            droppedReason = :droppedReason,
            syncStatus = :syncStatus
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: String,
        completedAt: String?,
        droppedReason: String?,
        syncStatus: String = SyncStatus.PENDING_UPDATE,
    )

    // M3-T5: open-instruction count per person. Used to show the
    // badge on the People list. "Open" = status NOT IN (DONE,
    // CARRIED_OVER, DROPPED). Indexed on (status, personId) via
    // the existing indices on the columns; the planner uses the
    // (status) index to filter, then aggregates in memory.
    @Query("SELECT personId, COUNT(*) as cnt FROM instructions WHERE status NOT IN ('DONE', 'CARRIED_OVER', 'DROPPED') AND personId IS NOT NULL GROUP BY personId")
    fun observeOpenCountByPerson(): Flow<List<PersonOpenCount>>

    /**
     * M4-T3: stale-outgoing per person. "Stale" = the user has an
     * OUTGOING instruction (sent to a subordinate) that hasn't
     * been moved to a closed state in 3+ days (spec §8.2). Used to
     * render the soft amber dot on the People-list row.
     *
     * **Fix (15-day audit):** use `MAX(daysQuiet)`, not `MIN`. With
     * MIN, a single fresh OUTGOING (0d) for a person who also has a
     * 5d OUTGOING would yield 0 and the HAVING `>= 3` filter would
     * drop the row. MAX correctly returns the age of the oldest
     * open OUTGOING, so the dot fires the moment ANY OUTGOING goes
     * 3+ days without an update.
     */
    @Query(
        """
        SELECT personId, MAX(julianday('now') - julianday(updatedAt)) AS daysQuiet
        FROM instructions
        WHERE direction = 'OUTGOING'
          AND status NOT IN ('DONE', 'CARRIED_OVER', 'DROPPED')
          AND personId IS NOT NULL
        GROUP BY personId
        HAVING daysQuiet >= 3
        """,
    )
    fun observeStaleByPerson(): Flow<List<PersonStaleAge>>

    // v2.0 Tier 2 (§2.10): worry-box query. Worry rows (urgency
    // IN ('worry', 'worry_with_date')) that are not in a closed
    // state. Sort: reviewAtEpochDay ASC (the soonest first), then
    // updatedAt DESC for tie-breaks.
    @Query(
        """
        SELECT * FROM instructions
        WHERE urgency IN ('worry', 'worry_with_date')
          AND status NOT IN ('DONE', 'DROPPED')
        ORDER BY
            CASE WHEN reviewAtEpochDay IS NULL THEN 1 ELSE 0 END,
            reviewAtEpochDay ASC,
            updatedAt DESC
        """,
    )
    fun observeWorry(): Flow<List<InstructionEntity>>

    /**
     * v2.0 Tier 2 (§2.10): close the worry loop. Sets the
     * instruction back to NORMAL urgency and DONE status in a
     * single statement. Repository still enqueues a sync_queue
     * UPDATE row.
     */
    @Query(
        """
        UPDATE instructions
        SET urgency = 'normal',
            status = 'DONE',
            completedAt = :now,
            updatedAt = :now,
            syncStatus = :syncStatus
        WHERE id = :id
        """,
    )
    suspend fun resolveWorry(id: String, now: String, syncStatus: String)

    /**
     * v2.0 Tier 2 (§2.10): keep the worry (mark as
     * "still relevant"). Clears reviewAtEpochDay but keeps the
     * urgency so it stays in the worry box.
     */
    @Query(
        """
        UPDATE instructions
        SET reviewAtEpochDay = NULL,
            updatedAt = :now,
            syncStatus = :syncStatus
        WHERE id = :id
        """,
    )
    suspend fun keepWorry(id: String, now: String, syncStatus: String)
}

/**
 * M4-T3: one (personId -> daysQuiet) row per stale person.
 */
data class PersonStaleAge(
    val personId: String,
    val daysQuiet: Double,
)

/**
 * M3-T5: a single (personId -> open count) row for the badge on
 * the People list. The HomeScreen maps this onto the
 * `Person` rows in the `PersonList` composable.
 */
data class PersonOpenCount(
    val personId: String,
    val cnt: Int,
)
