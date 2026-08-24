package com.kaavalan.note.data.work

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.kaavalan.note.data.export.BackupWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.8.0 (PROD-READINESS-P0-#1): smoke tests for the
 * backup WorkManager enqueue sites. Mirrors the
 * [WorkManagerInitializerCaptureSyncTest] shape so the
 * schedule is asserted on the same framework boundary.
 *
 * What we assert:
 *  1. `enqueueBackupNow(context)` enqueues a unique
 *     one-shot named `baton-backup-now` tagged with
 *     [BackupWorker].
 *  2. `scheduleBackup(context)` enqueues a unique
 *     periodic named `baton-backup-periodic` tagged
 *     with [BackupWorker].
 *  3. The KEEP policies hold for both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkManagerInitializerBackupTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `enqueueBackupNow enqueues unique one-shot work tagged with BackupWorker`() {
        WorkManagerInitializer.enqueueBackupNow(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("baton-backup-now")
            .get()
        assertEquals(1, workInfos.size)
        val info = workInfos[0]
        assertTrue(
            "expected WorkInfo tags to contain BackupWorker FQN, got ${info.tags}",
            info.tags.contains(BackupWorker::class.java.name),
        )
        // v1.8.0: we do NOT assert on info.state because the
        // test WorkManager auto-runs one-shot work on enqueue
        // (the @HiltWorker-annotated BackupWorker cannot be
        // constructed by the default test WorkerFactory, so the
        // work ends in FAILED state). The test is asserting the
        // SCHEDULE — the work is registered, named, and tagged
        // correctly. Real scheduling correctness is verified by
        // the integration test on a real device.
    }

    @Test
    fun `enqueueBackupNow with KEEP policy does not create a second work on a second call`() {
        WorkManagerInitializer.enqueueBackupNow(context)
        WorkManagerInitializer.enqueueBackupNow(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("baton-backup-now")
            .get()
        assertEquals(
            "KEEP policy should keep the existing one work, not enqueue another",
            1, workInfos.size,
        )
    }

    @Test
    fun `scheduleBackup enqueues unique periodic work tagged with BackupWorker`() {
        WorkManagerInitializer.scheduleBackup(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("baton-backup-periodic")
            .get()
        assertEquals(1, workInfos.size)
        val info = workInfos[0]
        assertTrue(
            "expected WorkInfo tags to contain BackupWorker FQN, got ${info.tags}",
            info.tags.contains(BackupWorker::class.java.name),
        )
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }
}
