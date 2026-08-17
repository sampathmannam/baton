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
import com.baton.app.data.brief.MorningBriefWorker
import com.baton.app.data.sync.CaptureSyncWorker
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
    private const val MORNING_BRIEF_NAME = "baton-morning-brief"
    private const val PERIODIC_INTERVAL_MIN = 15L

    // v1.4.3 (F-09 / F-20 wiring): names for the capture-sync work
    // that pushes dirty capture rows to Supabase. Lives in this
    // file (not CaptureSyncWorker.kt) so the schedule is co-located
    // with the other WorkManager enqueue sites. The 5-minute
    // interval is intentionally faster than the 15-minute sync
    // drain: captures are time-sensitive (the user is waiting on
    // the "synced" badge) and a dirty row only blocks a small
    // portion of the outbox. See `BatonApplication.onCreate` for
    // the safety-net rationale.
    private const val CAPTURE_ONE_SHOT_NAME = "baton-capture-sync"
    private const val CAPTURE_PERIODIC_NAME = "baton-capture-sync-periodic"
    private const val CAPTURE_PERIODIC_INTERVAL_MIN = 5L

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
     *
     * v1.2.1 (F-HIGH-13): add `setRequiresBatteryNotLow(true)` so
     * the drain doesn't fire while the device is in low-power mode
     * (where the user is trying to save battery for an emergency
     * call). WorkManager will defer to the next battery-OK window.
     */
    fun enqueueSyncDrain(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncDrainWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
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
     *
     * v1.2.1 (F-HIGH-13): BATTERY_NOT_LOW constraint.
     */
    fun schedulePeriodicDrain(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncDrainWorker>(
            PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        get(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * v1.4.3 (F-09 / F-20 wiring): one-shot capture-sync enqueue.
     * The brief is that the v1.4.2-final
     * [com.baton.app.data.captures.RoomCaptureRepository] now writes
     * every capture to Room with `syncStatus = PENDING_INSERT` first,
     * and the [CaptureSyncWorker] is the path that pushes the row to
     * Supabase. But the worker only runs if WorkManager schedules it.
     * Without this enqueue, the dirty rows stay dirty forever and the
     * F-09 / F-20 offline-first guarantee is a no-op on production
     * devices.
     *
     * **KEEP policy** — if a sync is already in-flight or pending,
     * do not enqueue a duplicate. WorkManager will not pile up
     * overlapping runs of the same unique work.
     *
     * **Network + battery constraints** — same as the sync drain
     * (CONNECTED + BATTERY_NOT_LOW). The worker is best-effort
     * retry; a missed window just means the next pass catches it.
     */
    fun enqueueCaptureSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<CaptureSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        get(context).enqueueUniqueWork(
            CAPTURE_ONE_SHOT_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * v1.4.3 (F-09 / F-20 wiring): periodic capture-sync
     * safety-net, called once from
     * [BatonApplication.onCreate]. Runs every 5 minutes when the
     * device is online, which is faster than the 15-minute sync
     * drain because captures are user-visible (the "synced" badge
     * on each row) and we want the user to see their note go green
     * promptly even if the foreground per-write enqueue was missed
     * (e.g. process killed mid-write).
     *
     * The per-write enqueue in `RoomCaptureRepository` (and the
     * [enqueueCaptureSync] one-shot above, where it's wired in
     * a follow-up) remains the foreground path; this is the
     * "I was offline and now I'm not" safety net, mirroring
     * the [schedulePeriodicDrain] rationale for the regular
     * outbox.
     */
    fun schedulePeriodicCaptureSync(context: Context) {
        val request = buildCaptureSyncPeriodicRequest()
        get(context).enqueueUniquePeriodicWork(
            CAPTURE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * v1.4.3 (F-09 / F-20 wiring): build the
     * [androidx.work.PeriodicWorkRequest] the
     * [schedulePeriodicCaptureSync] enqueues. Exposed as
     * [internal] so the unit test
     * [com.baton.app.data.work.WorkManagerInitializerCaptureSyncTest]
     * can assert the 5-minute interval directly. The interval is
     * not exposed on the public [androidx.work.WorkInfo] API
     * (it lives on the internal [androidx.work.WorkSpec]), so the
     * test cannot read it back after the enqueue — the only
     * observable signal is `nextScheduleTimeMillis`, which the
     * `WorkManagerTestInitHelper` test driver does not advance
     * (it stays at "enqueue time + 0"). Calling the builder
     * directly is the only stable way to assert the interval.
     */
    internal fun buildCaptureSyncPeriodicRequest(): androidx.work.PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<CaptureSyncWorker>(
            CAPTURE_PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

    /**
     * v2.0 Tier 2 (§2.6): enqueue the [MorningBriefWorker] as a
     * one-shot work request with a 2-second initial delay. Used
     * by the dev / QA "test in < 1 hour" path; the periodic
     * variant below is the production path.
     */
    fun enqueueMorningBriefOneShot(context: Context, delaySec: Long = 2L) {
        val request = OneTimeWorkRequestBuilder<MorningBriefWorker>()
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .build()
        get(context).enqueueUniqueWork(
            MORNING_BRIEF_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * v2.0 Tier 2 (§2.6): the periodic 24-h morning brief.
     * The initial delay is computed from the current wall-clock
     * so the first run lands at [hourOfDay]:[minute] local.
     */
    fun scheduleMorningBrief(context: Context, hourOfDay: Int = 9, minute: Int = 0) {
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        val target = java.time.LocalDate.now(zone)
            .atTime(hourOfDay, minute)
            .atZone(zone)
            .toInstant()
        val first = if (target.isAfter(now)) target else target.plus(java.time.Duration.ofDays(1))
        val initialDelay = java.time.Duration.between(now, first)
        val request = PeriodicWorkRequestBuilder<MorningBriefWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        get(context).enqueueUniquePeriodicWork(
            MORNING_BRIEF_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
