package com.baton.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baton.app.data.local.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * M3-T2: a one-shot worker that drains the Room sync outbox to
 * Supabase. Hilt-injected; runs whenever WorkManager fires it
 * (e.g. on connectivity change, or as a periodic worker — TBD in
 * M3.5).
 *
 * **v1.2 root-cause fix (F-CRIT-07 / BUG-DATA-005):** v1.1 had the
 * worker but never scheduled it. The per-write drainOne covered
 * only the foreground path. A user who made 3 writes while
 * backgrounded, was offline, and reopened the app 4 hours later
 * had those 3 PENDING rows in `sync_queue` until the next
 * foreground write — potentially forever. v1.2:
 *  - calls drainAll (was already correct)
 *  - is now scheduled periodically in [WorkManagerInitializer.schedule]
 *  - the per-write drain in RoomPersonRepository.create /
 *    RoomInstructionRepository.enqueueUpdate is unchanged — it
 *    covers the foreground case
 */
@HiltWorker
class SyncDrainWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            syncEngine.drainAll()
            Result.success()
        } catch (e: CancellationException) {
            // Cooperative cancel — rethrow and do NOT retry. The
            // work is canceled; the next periodic run will pick up
            // whatever was left in the outbox.
            throw e
        } catch (e: Exception) {
            // Transient failure (network, 5xx). Retry with
            // WorkManager's exponential backoff. The outbox stays
            // on disk and the next worker run (or the per-write
            // drain in the VM) re-tries.
            Result.retry()
        }
    }
}
