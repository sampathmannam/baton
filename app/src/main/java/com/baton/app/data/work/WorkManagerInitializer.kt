package com.baton.app.data.work

import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import com.baton.app.BatonApplication
import com.baton.app.data.brief.MorningBriefWorker
import com.baton.app.data.export.BackupWorker
import com.baton.app.data.retention.RetentionWorker
// v2.0.0: CaptureSyncWorker removed (no cloud sync).
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

    // v2.0.0 (drop Supabase): the capture-sync work names from
    // v1.4.3 (F-09 / F-20) are no longer used. Captures are
    // local-only; no `CaptureSyncWorker` exists. Kept as a
    // doc-comment only so the audit trail is intact.

    // v1.8.0 (PROD-READINESS-P0-#1): names for the backup work
    // that snapshots the Room DB to the app's private filesDir.
    // The one-shot is the "Back up now" button in the Settings
    // sheet; the periodic is the daily safety-net that runs even
    // if the user never opens Settings.
    private const val BACKUP_ONE_SHOT_NAME = "baton-backup-now"
    private const val BACKUP_PERIODIC_NAME = "baton-backup-periodic"
    private const val BACKUP_PERIODIC_INTERVAL_HOURS = 24L

    // v1.8.0 (PROD-READINESS-P2-#5): names for the
    // retention sweep. The one-shot is fired after
    // a settings change (future); the periodic is
    // the daily safety-net.
    private const val RETENTION_ONE_SHOT_NAME = "baton-retention-now"
    private const val RETENTION_PERIODIC_NAME = "baton-retention-periodic"
    private const val RETENTION_PERIODIC_INTERVAL_HOURS = 24L

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
    // v2.0.0: SyncDrainWorker removed (no cloud sync). The
    // outbox rows that this would have drained still accumulate
    // in sync_queue. A future v2.x pass that adds optional
    // cloud sync should re-introduce the worker.

    fun enqueueSyncDrain(context: Context) {
        // No-op in v2.0.0.
    }

    fun schedulePeriodicDrain(context: Context) {
        // No-op in v2.0.0.
    }

    /**
     * v1.4.3 (F-09 / F-20 wiring): one-shot capture-sync enqueue.
     * The brief was that v1.4.2-final
     * [com.baton.app.data.captures.RoomCaptureRepository] wrote
     * every capture to Room with `syncStatus = PENDING_INSERT` and
     * a separate worker pushed the row to Supabase.
     *
     * **v2.0.0 (drop Supabase):** [com.baton.app.data.sync.CaptureSyncWorker]
     * is gone. Captures are local-only; the
     * `syncStatus` column on the `captures` row is now always
     * `SYNCED` at write time (see
     * [com.baton.app.data.captures.RoomCaptureRepository.create]).
     * The function is kept (no-op) so the call site in
     * [com.baton.app.data.captures.RoomCaptureRepository] still
     * compiles against the v2.0.0 API. A future optional
     * cloud-sync v2.x release would re-introduce a real
     * implementation.
     */
    @Suppress("UNUSED_PARAMETER")
    fun enqueueCaptureSync(context: Context) {
        // No-op in v2.0.0.
    }

    /**
     * v1.4.3 (F-09 / F-20 wiring): periodic capture-sync
     * safety-net.
     *
     * **v2.0.0 (drop Supabase):** no-op (see [enqueueCaptureSync]).
     */
    @Suppress("UNUSED_PARAMETER")
    fun schedulePeriodicCaptureSync(context: Context) {
        // No-op in v2.0.0.
    }

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

    /**
     * v1.8.0 (PROD-READINESS-P0-#1): the "Back up now"
     * one-shot. Called by the Settings sheet's "Back up
     * now" button. `ExistingWorkPolicy.KEEP` means a
     * second tap while the first job is running is a
     * no-op (the first job will complete the backup).
     */
    fun enqueueBackupNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .build()
        get(context).enqueueUniqueWork(
            BACKUP_ONE_SHOT_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * v1.8.0 (PROD-READINESS-P0-#1): the daily periodic
     * backup. Runs once every 24 h; `ExistingPeriodicWorkPolicy.KEEP`
     * so re-enqueueing on every app start is a no-op when
     * the schedule already exists.
     *
     * The first run lands `BACKUP_PERIODIC_INTERVAL_HOURS`
     * from now. This is the safety-net for a user who
     * never opens Settings — at least one backup per day
     * is guaranteed (modulo the WorkManager deferral rules:
     * battery, doze, app-standby).
     */
    fun scheduleBackup(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            BACKUP_PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    // v1.8.0: backup is local (writes to
                    // filesDir), no network. But we still
                    // require the device to be on battery
                    // so a 200-row backup doesn't fire
                    // while the user is trying to send an
                    // emergency email.
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        get(context).enqueueUniquePeriodicWork(
            BACKUP_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * v1.8.0 (PROD-READINESS-P2-#5): enqueue the
     * one-shot retention sweep. Called by a future
     * Settings → Compliance → "Run retention now"
     * button. `ExistingWorkPolicy.KEEP` means a
     * second tap while the first job is running is
     * a no-op.
     */
    fun enqueueRetentionNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RetentionWorker>().build()
        get(context).enqueueUniqueWork(
            RETENTION_ONE_SHOT_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * v1.8.0 (PROD-READINESS-P2-#5): the daily
     * retention sweep. Runs once every 24 h;
     * `ExistingPeriodicWorkPolicy.KEEP` so
     * re-enqueueing on every app start is a no-op
     * after the first.
     */
    fun scheduleRetention(context: Context) {
        val request = PeriodicWorkRequestBuilder<RetentionWorker>(
            RETENTION_PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    // Local DB write; battery-friendly.
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        get(context).enqueueUniquePeriodicWork(
            RETENTION_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
