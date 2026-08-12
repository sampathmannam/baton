package com.baton.app.data.work

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.baton.app.BatonApplication

/**
 * M3-T2: WorkManager on-demand initialiser.
 *
 * WorkManager normally boots itself in a ContentProvider declared
 * by the `androidx.work:work-runtime` library. That auto-init has
 * a few costs:
 *
 *  - It runs in every process the app starts, even processes that
 *    never schedule a worker (e.g. a future background tile).
 *  - It can't see Hilt; the worker factory has to be wired via
 *    `androidx.startup` or `Configuration.Provider`.
 *  - It pulls in Room's metadata during init, which slows the
 *    first frame on cold start.
 *
 * M3-T2 disables the auto-init ContentProvider (see the manifest
 * `tools:node="remove"` on the `androidx.work.WorkManagerInitializer`
 * startup entry). WorkManager is now initialised lazily on the
 * first call to [get]. The [Configuration] comes from
 * [BatonApplication.workManagerConfiguration] (Hilt-injected
 * HiltWorkerFactory).
 *
 * The `enqueueSyncDrain()` and similar callers go through this
 * [get] instead of [WorkManager.getInstance] directly so the
 * init order is well-defined.
 */
object WorkManagerInitializer {

    /**
     * Idempotent. Safe to call from any coroutine on any
     * dispatcher; the underlying [WorkManager.getInstance] call
     * is itself idempotent.
     */
    fun get(context: Context): WorkManager =
        WorkManager.getInstance(context.applicationContext)

    /**
     * Convenience: enqueue a one-shot [SyncDrainWorker] right
     * now. The first call triggers [WorkManager.getInstance],
     * which reads [BatonApplication.workManagerConfiguration]
     * (Hilt-injected) and starts the worker factory.
     */
    fun enqueueSyncDrain(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<SyncDrainWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        get(context).enqueueUniqueWork(
            "baton-sync-drain",
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
