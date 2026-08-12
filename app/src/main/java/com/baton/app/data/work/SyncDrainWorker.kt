package com.baton.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baton.app.data.local.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * M3-T2: a one-shot worker that drains the Room sync outbox to
 * Supabase. Hilt-injected; runs whenever WorkManager fires it
 * (e.g. on connectivity change, or as a periodic worker — TBD in
 * M3.5).
 *
 * **Why a worker, not just a coroutine launch in the VM.** The
 * `HomeViewModel`'s `refreshFromNetwork` and the
 * `RoomPersonRepository.create` path both fire their own drains
 * — those cover the foreground use case. The worker covers
 * writes that the user made while the app was backgrounded or
 * killed (the outbox grows on disk until something flushes it).
 * For M2 the worker is registered but not scheduled; the
 * per-write drain is enough for the alpha. M3 wires it up to
 * WorkManager on connectivity change.
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
        } catch (e: Exception) {
            // Retry up to the WorkManager default. The outbox stays
            // on disk and the next worker run (or the per-write
            // drain in the VM) re-tries.
            Result.retry()
        }
    }
}
