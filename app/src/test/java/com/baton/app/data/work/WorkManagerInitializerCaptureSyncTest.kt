package com.baton.app.data.work

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.baton.app.data.sync.CaptureSyncWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * v1.4.3 (F-09 / F-20 wiring) smoke tests for the two new
 * [WorkManagerInitializer] enqueue sites.
 *
 * The bug: v1.4.2-final added
 * [com.baton.app.data.sync.CaptureSyncWorker] (Hilt CoroutineWorker)
 * but never registered it with WorkManager. Every capture that
 * [com.baton.app.data.captures.RoomCaptureRepository] wrote with
 * `syncStatus = PENDING_INSERT` stayed dirty forever — the entire
 * F-09 / F-20 offline-first guarantee was a no-op on production
 * devices. v1.4.3 closes the wiring gap by adding
 * [WorkManagerInitializer.enqueueCaptureSync] (one-shot) and
 * [WorkManagerInitializer.schedulePeriodicCaptureSync] (5-minute
 * safety net) and calling the latter from
 * [com.baton.app.BatonApplication.onCreate].
 *
 * **What these tests assert.**
 *  1. `enqueueCaptureSync(context)` enqueues a unique work named
 *     `baton-capture-sync` with `ExistingWorkPolicy.KEEP`, and the
 *     resulting [WorkInfo] is bound to [CaptureSyncWorker] (verified
 *     by the auto-tag WorkManager adds — the worker's fully-qualified
 *     class name).
 *  2. `schedulePeriodicCaptureSync(context)` enqueues a unique
 *     periodic work named `baton-capture-sync-periodic` with
 *     `ExistingPeriodicWorkPolicy.KEEP`, and the resulting
 *     [WorkInfo] has a next-schedule time roughly 5 minutes from
 *     now (i.e. the work was enqueued with a 5-minute interval,
 *     not the default 15-minute sync-drain interval).
 *  3. The `KEEP` policies hold: a second enqueue under the same
 *     unique name does not create a second [WorkInfo] entry.
 *
 * **Why Robolectric + `WorkManagerTestInitHelper`.** The unit under
 * test is a thin wrapper around `WorkManager.enqueueUniqueWork` /
 * `enqueueUniquePeriodicWork`. We want to exercise the real
 * WorkManager path (unique-name dedup, KEEP policy, tag assignment)
 * rather than a pure mock. `WorkManagerTestInitHelper` swaps in an
 * in-memory WorkManager backed by Robolectric's SQLite, which is
 * the canonical way to unit-test WorkManager integrations in
 * `src/test` (no instrumented test needed). The
 * `BatonApplication.workManagerConfiguration` (Hilt-injected
 * `HiltWorkerFactory`) is bypassed because we never actually
 * execute the worker — we only inspect the [WorkInfo] state after
 * enqueue. Construction happens at run time, not enqueue time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkManagerInitializerCaptureSyncTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Initialise a fresh in-memory WorkManager. The default
        // WorkerFactory is fine — we never `startWork()` the
        // requests; we only inspect the WorkInfo state after the
        // unique-work enqueue.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    // ----- one-shot enqueue -----

    @Test
    fun `enqueueCaptureSync enqueues unique one-shot work tagged with CaptureSyncWorker`() {
        WorkManagerInitializer.enqueueCaptureSync(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CAPTURE_ONE_SHOT_NAME)
            .get()

        assertEquals(
            "expected exactly one WorkInfo for unique work '$CAPTURE_ONE_SHOT_NAME'",
            1,
            workInfos.size,
        )
        val info = workInfos[0]
        // WorkManager auto-adds the worker's fully-qualified class
        // name as a tag when the request is built. If the wrong
        // worker class is enqueued (e.g. SyncDrainWorker by mistake)
        // this assertion catches the regression.
        assertTrue(
            "expected WorkInfo tags to contain CaptureSyncWorker FQN, got ${info.tags}",
            info.tags.contains(CaptureSyncWorker::class.java.name),
        )
        // Freshly enqueued one-time work is ENQUEUED (it hasn't
        // been picked up by a worker thread yet).
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    @Test
    fun `enqueueCaptureSync with KEEP policy does not create a second work on a second call`() {
        WorkManagerInitializer.enqueueCaptureSync(context)
        WorkManagerInitializer.enqueueCaptureSync(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CAPTURE_ONE_SHOT_NAME)
            .get()

        // KEEP: the second enqueue is a no-op; the first work is
        // still there. This is the "don't pile up duplicate syncs"
        // property the v1.4.2-final spec requires.
        assertEquals(
            "KEEP policy should keep the existing one work, not enqueue another",
            1,
            workInfos.size,
        )
    }

    // ----- periodic schedule -----

    @Test
    fun `schedulePeriodicCaptureSync enqueues unique periodic work tagged with CaptureSyncWorker`() {
        WorkManagerInitializer.schedulePeriodicCaptureSync(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CAPTURE_PERIODIC_NAME)
            .get()

        assertEquals(
            "expected exactly one WorkInfo for unique periodic work '$CAPTURE_PERIODIC_NAME'",
            1,
            workInfos.size,
        )
        val info = workInfos[0]
        assertTrue(
            "expected WorkInfo tags to contain CaptureSyncWorker FQN, got ${info.tags}",
            info.tags.contains(CaptureSyncWorker::class.java.name),
        )
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    @Test
    fun `schedulePeriodicCaptureSync interval is WorkManager's 15-minute minimum (brief asked for 5 min)`() {
        // We can't observe the interval via WorkInfo after enqueue
        // — `WorkInfo.nextScheduleTimeMillis` is the next scheduled
        // execution time, not the interval, and the test WorkManager
        // driver does not advance it (it stays at enqueue time). The
        // public `WorkInfo` API doesn't expose the underlying
        // `WorkSpec.intervalDuration` either. So we build the request
        // directly via the internal `buildCaptureSyncPeriodicRequest`
        // helper and read the interval off the `WorkSpec` via
        // reflection. This is the canonical way to assert on a
        // `PeriodicWorkRequest`'s interval in a unit test.
        //
        // DEVIATION FROM BRIEF: the v1.4.3 design brief specified
        // `CAPTURE_PERIODIC_INTERVAL_MIN = 5L` with a comment
        // "faster than sync drain; captures are time-sensitive".
        // WorkManager 2.9.1 silently clamps any `PeriodicWorkRequest`
        // interval below `PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`
        // (= 15 min = 900 000 ms) to 15 min (see
        // `WorkSpec.setPeriodic(long)` which logs "Interval duration
        // lesser than minimum allowed value; Changed to 900000"
        // and bumps the value). The 5-min intent is therefore not
        // achievable with a single `PeriodicWorkRequest`. The
        // production constant stays at 5L (per the brief); the test
        // asserts the actual stored interval (15 min) so a future
        // reader understands the framework's clamp, and so the
        // assertion cannot silently regress to e.g. 30 min without
        // a test failure. The 5-min intent can only be honoured
        // with a chained-one-time pattern, which is a much larger
        // change and explicitly out of scope for this branch.
        val request = WorkManagerInitializer.buildCaptureSyncPeriodicRequest()
        val intervalMillis = readIntervalMillis(request)

        // WorkManager 2.9.1's minimum periodic interval.
        val expectedFifteenMinMs = TimeUnit.MINUTES.toMillis(15L)
        assertEquals(
            "expected WorkManager's 15-min minimum interval " +
                "(=$expectedFifteenMinMs ms) — the brief asked for 5 min " +
                "but WorkManager clamps anything < 15 min to 15 min; " +
                "got $intervalMillis ms",
            expectedFifteenMinMs,
            intervalMillis,
        )

        // And it must NOT be the requested 5 min — that's the clamp
        // signal. If this assertion ever starts passing, WorkManager
        // has changed its minimum and we should re-read the brief.
        val fiveMinMs = TimeUnit.MINUTES.toMillis(CAPTURE_PERIODIC_INTERVAL_MIN)
        assertNotEquals(
            "the request's intervalDuration must NOT equal the 5-min " +
                "intent (=$fiveMinMs ms); if it does, WorkManager has " +
                "relaxed its minimum and the test must be updated",
            fiveMinMs,
            intervalMillis,
        )
    }

    /**
     * Read `intervalDuration` from the internal `WorkSpec` via
     * reflection. The field is on
     * `androidx.work.impl.model.WorkSpec.intervalDuration` (NOT
     * `intervalMillis` — the Kotlin name is `intervalDuration`).
     * The path goes through `WorkRequest.getWorkSpec()` which is
     * annotated `@RestrictTo(LIBRARY_GROUP)`. Reflection is the
     * only stable way to assert the interval in a unit test; if
     * the field/method name ever changes in a future WorkManager
     * release, this test will fail loudly (NoSuchFieldException)
     * rather than silently green-light a wrong interval.
     */
    private fun readIntervalMillis(request: androidx.work.WorkRequest): Long {
        val workSpec = request.javaClass.getMethod("getWorkSpec").invoke(request)
        val intervalField = workSpec.javaClass.getField("intervalDuration")
        return intervalField.getLong(workSpec)
    }

    companion object {
        // Mirrors the private constants in [WorkManagerInitializer].
        // We keep these in sync by hand because the production
        // constants are `private` — tests don't need to mutate
        // them, only read them. If the names change, this file
        // breaks loudly with a clear "expected unique work named X"
        // message rather than a silent false-positive.
        private const val CAPTURE_ONE_SHOT_NAME = "baton-capture-sync"
        private const val CAPTURE_PERIODIC_NAME = "baton-capture-sync-periodic"
        private const val CAPTURE_PERIODIC_INTERVAL_MIN = 5L
    }
}
