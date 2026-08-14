package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    /**
     * v1.2.2 (F-HIGH-07): drain order is FIFO by id, with
     * backoff. Entries whose `nextAttemptAt` is in the future
     * (i.e. the previous attempt failed and the backoff window
     * hasn't expired) are skipped. Permanently-failed entries
     * (where `lastError` starts with `PERMANENT_FAILURE:`) are
     * also skipped — they only re-appear after
     * [resetPermanentlyFailed] is called.
     */
    @Query(
        "SELECT * FROM sync_queue " +
            "WHERE nextAttemptAt <= :now " +
            "AND (lastError IS NULL OR lastError NOT LIKE 'PERMANENT_FAILURE:%') " +
            "ORDER BY id ASC",
    )
    suspend fun snapshotReady(now: Long): List<SyncQueueEntity>

    /**
     * Pre-v1.2.2 callers (e.g. instrumentation tests that don't
     * care about backoff) can still read the full queue. Production
     * code uses [snapshotReady].
     */
    @Query("SELECT * FROM sync_queue ORDER BY id ASC")
    suspend fun snapshot(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY id ASC")
    fun observe(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE `table` = :table AND rowId = :rowId AND op = :op LIMIT 1")
    suspend fun findPending(table: String, rowId: String, op: String): SyncQueueEntity?

    /**
     * v1.4.2 (DATA-FINDING-04): on conflict REPLACE so a
     * double-tap of the same UI action (e.g. `markDone`) that
     * enqueues two `UPDATE` rows for the same `(table, rowId)`
     * collapses to a single row holding the latest payload.
     * The unique index on `(op, table, rowId)` (declared on
     * [SyncQueueEntity]) is what raises the conflict. The new
     * row is assigned a fresh auto-generated `id`, so any
     * external references to the old id (e.g. an in-flight
     * `recordFailureWithBackoff` from the drain) become stale —
     * the new row's failure / backoff counters start at zero,
     * which is the intended "fresh attempt" semantics.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * v1.2.2 (F-HIGH-07): backoff-aware failure record. The
     * `nextAttemptAt` is set to `now + backoffMs` so the drain
     * skips the entry on the next pass. The exponential backoff
     * is `1s * 2^attempts` capped at 5 minutes — a transient
     * 30s outage won't trigger a tight retry loop, but a
     * 5-minute cap means the user isn't waiting forever.
     */
    @Query(
        "UPDATE sync_queue SET attempts = attempts + 1, lastError = :error, " +
            "nextAttemptAt = :nextAttemptAt WHERE id = :id",
    )
    suspend fun recordFailureWithBackoff(id: Long, error: String, nextAttemptAt: Long)

    /**
     * Pre-v1.2.2 callers: legacy `recordFailure` (no backoff).
     * Kept for test backwards compat; production should use
     * [recordFailureWithBackoff] via [SyncEngine].
     */
    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String)

    /**
     * v1.2.2 (F-HIGH-07): mark a row as permanently failed. The
     * drain skips it on every future pass (the `lastError` LIKE
     * `PERMANENT_FAILURE:%` check in [snapshotReady]). The user
     * can call [resetPermanentlyFailed] from a "Retry stuck
     * entries" action in Settings to put them back in rotation.
     *
     * Also bumps `attempts` to [attempts] (the final count from
     * the engine) so the UI / debug surfaces can read the actual
     * failure count without joining against [snapshot].
     */
    @Query(
        "UPDATE sync_queue SET attempts = :attempts, lastError = :error, " +
            "nextAttemptAt = 0 WHERE id = :id",
    )
    suspend fun markPermanentlyFailed(id: Long, attempts: Int, error: String)

    /**
     * v1.2.2 (F-HIGH-07): reset all `PERMANENT_FAILURE:*` rows
     * so they can be tried again. Called from Settings →
     * "Retry stuck outbox entries". The next drain re-processes
     * them with `attempts = 0` so the backoff clock starts over.
     */
    @Query(
        "UPDATE sync_queue SET attempts = 0, lastError = NULL, nextAttemptAt = 0 " +
            "WHERE lastError LIKE 'PERMANENT_FAILURE:%'",
    )
    suspend fun resetPermanentlyFailed(): Int

    /**
     * v1.2.4 (F-HIGH-08): count of `PERMANENT_FAILURE:*` rows.
     * Used by the SettingsViewModel to surface "N stuck outbox
     * rows" in the UI. The count is also the input to the
     * "Retry stuck entries" action.
     */
    @Query(
        "SELECT COUNT(*) FROM sync_queue WHERE lastError LIKE 'PERMANENT_FAILURE:%'",
    )
    fun observeStuckCount(): Flow<Int>

    /**
     * v1.2.4: synchronous count, for the SettingsViewModel's
     * `retryStuck()` action — it shows the count on the
     * confirmation snackbar ("Reset 3 stuck entries").
     */
    @Query(
        "SELECT COUNT(*) FROM sync_queue WHERE lastError LIKE 'PERMANENT_FAILURE:%'",
    )
    suspend fun stuckCount(): Int
}
