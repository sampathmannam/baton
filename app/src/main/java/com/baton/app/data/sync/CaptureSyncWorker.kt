package com.baton.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.SupabaseCaptureRepository
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.SyncQueueDao
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * v1.4.2 (F-09 / F-20) the actual wire-push path for captures.
 *
 * **What this fixes.** The pre-v1.4.2 capture write was
 * straight-to-Supabase via [SupabaseCaptureRepository]. If Supabase
 * was down at save time, the user's note was lost. v1.4.2 made
 * [com.baton.app.data.captures.RoomCaptureRepository] write the
 * capture to Room first (`syncStatus = PENDING_INSERT`), and this
 * worker is the path that pushes the row to Supabase. **If Supabase
 * is down when the user types a note, the note is in Room and the
 * worker will keep retrying until the network is back** — that's
 * the F-09 / F-20 fix.
 *
 * **What it does.** [processDirtyRows] reads every Room capture
 * whose `syncStatus` is not `SYNCED` (i.e. `PENDING_INSERT` or
 * `PENDING_UPDATE`) and calls
 * [SupabaseCaptureRepository.insertCapture] on each. On success the
 * row is flipped to `syncStatus = SYNCED` and the matching
 * `sync_queue` entry is deleted. On failure the row is left dirty
 * for the next pass; the [SyncEngine] exponential backoff applies
 * to the `sync_queue` row (it bumps `attempts` and sets
 * `nextAttemptAt = now + backoff`).
 *
 * **Why a CoroutineWorker rather than a function on SyncEngine.**
 * SyncEngine's [com.baton.app.data.local.SyncEngine.processCaptureEntry]
 * is a stub that just flips `syncStatus` to `SYNCED` without calling
 * Supabase. Modifying SyncEngine is out of scope for this branch
 * (the brief is explicit), so the worker is a separate code path
 * that does the right thing. The worker also deletes the
 * `sync_queue` row on success so SyncEngine's stub never runs
 * against a row the worker already pushed. See
 * [com.baton.app.data.captures.RoomCaptureRepository] for the
 * full race-condition discussion.
 *
 * **WorkManager scheduling.** This is a `CoroutineWorker` with a
 * default constructor; Hilt-injectable via [HiltWorker]. Scheduling
 * is out of scope for this branch (the WorkManager periodic-job
 * wire-up lives in `AppInitializer` / `MainActivity` which are
 * not in the owned-files list). The unit tests exercise
 * [processDirtyRows] directly; the WorkManager `doWork` simply
 * delegates to it.
 */
@HiltWorker
class CaptureSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val captureDao: CaptureDao,
    private val syncQueueDao: SyncQueueDao,
    private val captureRemote: SupabaseCaptureRepository,
) : CoroutineWorker(appContext, params) {

    private val now: () -> Long = { System.currentTimeMillis() }

    override suspend fun doWork(): Result = try {
        processDirtyRows()
        Result.success()
    } catch (e: Exception) {
        // A top-level failure (e.g. database locked) is a transient
        // retryable error. WorkManager's exponential backoff will
        // re-invoke us.
        Result.retry()
    }

    /**
     * Push every dirty capture (syncStatus != SYNCED) to Supabase.
     * Returns the list of (id, success) outcomes so tests can
     * assert per-row state. Production callers ignore the return.
     *
     * **Per-row semantics.**
     *  - On Supabase success: `syncStatus` is set to `SYNCED` and
     *    the matching `sync_queue` row is deleted. The user sees
     *    a synced row; the next pass skips it.
     *  - On Supabase failure: the row is left dirty; the
     *    `sync_queue` row's `attempts` is bumped (via the
     *    [recordDirtyFailure] helper) so the existing
     *    [com.baton.app.data.local.SyncEngine] backoff windows
     *    keep the next drain at bay. We do *not* throw out of
     *    this method on a per-row failure — one bad row shouldn't
     *    block the others. The outcome list lets the caller
     *    surface per-row failure counts if needed.
     */
    suspend fun processDirtyRows(): List<Outcome> {
        val dirty = captureDao.snapshotDirty()
        val outcomes = mutableListOf<Outcome>()
        for (row in dirty) {
            val outcome = try {
                pushOne(row.id, row.mode, row.rawText)
                Outcome(row.id, success = true, error = null)
            } catch (e: Exception) {
                // Mark the sync_queue row's attempt so the engine's
                // backoff applies. We don't throw — the next pass
                // will retry this row.
                recordDirtyFailure(row.id, e.message ?: e.toString())
                Outcome(row.id, success = false, error = e.message ?: e.toString())
            }
            outcomes.add(outcome)
        }
        return outcomes
    }

    private suspend fun pushOne(id: String, modeDbValue: String, rawText: String?) {
        // Use the existing internal `insertCapture` so the
        // BATON-WIRE-006 idempotency key (the client UUID = row's
        // id) is preserved end-to-end. A retry with the same id
        // is a server-side no-op.
        //
        // The wire payload is the captured raw text + the mode +
        // the id — exactly what the existing
        // [SupabaseCaptureRepository.insertCapture] signature
        // expects. The sync_queue's payloadJson is a richer
        // record (it carries the id) but we rebuild it from the
        // local Room row, which is the source of truth.
        captureRemote.insertCapture(
            id = id,
            rawText = rawText.orEmpty(),
            mode = CaptureMode.fromDbValue(modeDbValue),
        )
        // Success: flip the local row to SYNCED and delete the
        // matching sync_queue entry. Deleting the queue row is
        // what keeps SyncEngine's stub from racing the worker —
        // if the engine's drain runs after this, findPending()
        // returns null and the engine skips the row.
        captureDao.setSyncStatus(id, SyncStatus.SYNCED)
        syncQueueDao.findPending("captures", id, inferOpFromStatus(id))?.let {
            syncQueueDao.deleteById(it.id)
        }
    }

    /**
     * Infer the sync_queue op from the row's current syncStatus.
     * `PENDING_UPDATE` came from `markProcessed()` (OP_UPDATE);
     * `PENDING_INSERT` (or any other non-SYNCED value) is the
     * default OP_INSERT.
     */
    private suspend fun inferOpFromStatus(id: String): String {
        val row = captureDao.getById(id) ?: return SyncQueueEntity.OP_INSERT
        return if (row.syncStatus == SyncStatus.PENDING_UPDATE) {
            SyncQueueEntity.OP_UPDATE
        } else {
            SyncQueueEntity.OP_INSERT
        }
    }

    private suspend fun recordDirtyFailure(rowId: String, error: String) {
        // Find the matching sync_queue row (if any) and bump its
        // attempts. This is what the existing SyncEngine does on
        // its own failure path; we mirror it so the outbox UI
        // shows the failure count correctly.
        val entry = syncQueueDao.findPending("captures", rowId, SyncQueueEntity.OP_INSERT)
            ?: syncQueueDao.findPending("captures", rowId, SyncQueueEntity.OP_UPDATE)
            ?: return
        val backoffMs = backoffMillis(entry.attempts + 1)
        syncQueueDao.recordFailureWithBackoff(
            id = entry.id,
            error = error,
            nextAttemptAt = now() + backoffMs,
        )
    }

    private fun backoffMillis(attempts: Int): Long {
        // Mirrors SyncEngine.backoffMillis: 1s * 2^attempts,
        // capped at 5 minutes. See SyncEngine v1.2.2 for the
        // schedule. We don't import SyncEngine here to keep the
        // dependency graph one-way (worker depends on capture
        // infra, not on the engine).
        val baseMs = 1_000L
        val shift = attempts.coerceIn(0, 30)
        val raw = baseMs shl shift
        return raw.coerceAtMost(MAX_BACKOFF_MS)
    }

    /**
     * Snapshot of a single row's outcome. Exposed for tests; the
     * production caller ([doWork]) discards it.
     */
    data class Outcome(
        val id: String,
        val success: Boolean,
        val error: String?,
    )

    companion object {
        private const val MAX_BACKOFF_MS = 5L * 60L * 1000L
    }
}
