package com.baton.app.data.work

import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import com.baton.app.BatonApplication
import java.util.concurrent.TimeUnit

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

    private const val ONE_SHOT_NAME = "baton-sync-drain"
    private const val PERIODIC_NAME = "baton-sync-periodic"
    private const val BRIEF_NAME = "baton-daily-brief"
    private const val PERIODIC_INTERVAL_MIN = 15L

    /**
     * Idempotent. Safe to call from any coroutine on any
     * dispatcher; the underlying [WorkManager.getInstance] call
     * is itself idempotent.
     */
    fun get(context: Context): WorkManager =
        WorkManager.getInstance(context.applicationContext)

    /**
     * v1.2 root-cause fix (F-CRIT-07): v1.1 declared this method
     * but never called it. A user who made writes while the app
     * was backgrounded and never made another write would have
     * their outbox rows stay PENDING forever. We now call this
     * from [BatonApplication.onCreate].
     */
    fun enqueueSyncDrain(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncDrainWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        get(context).enqueueUniqueWork(
            ONE_SHOT_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * v1.2 root-cause fix (F-CRIT-07): periodic background drain
     * so the outbox self-heals after process kill + reopen. Runs
     * every 15 minutes when the device is online. The per-write
     * drain in the VMs remains the foreground path; this is the
     * "I was offline and now I'm not" safety net.
     */
    fun schedulePeriodicDrain(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncDrainWorker>(
            PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        get(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
