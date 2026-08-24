package com.kaavalan.note.data.backup

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.kaavalan.note.data.auth.SecurePreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v2.1.1 (PM rating): the [DriveBackupWorker] tests.
 *
 * The v2.1.0 worker returned [ListenableWorker.Result.retry]
 * for [DriveBackupManager.DriveBackupException.NotSignedIn],
 * which meant the worker re-fired on every cold start of a
 * device that had never signed in (the periodic schedule
 * was registered unconditionally in
 * [com.kaavalan.note.BatonApplication.onCreate]). Each fire
 * wrote a `failure` to the WorkManager log.
 *
 * The v2.1.1 fix: return [ListenableWorker.Result.failure]
 * on `NotSignedIn` so the worker stops polluting the log
 * after the first miss. The user re-enables the schedule
 * on the next manual sign-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DriveBackupWorkerTest {

    private lateinit var context: Context
    private lateinit var driveBackupManager: DriveBackupManager
    private lateinit var securePreferences: SecurePreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        driveBackupManager = mockk()
        securePreferences = mockk(relaxed = true)
    }

    @Test
    fun `doWork returns failure when passphrase is not set`() = runTest {
        every { securePreferences.getBackupEncryptionKeyHash() } returns null

        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(testWorkerFactory())
            .build()
        val result = worker.doWork()

        // v2.1.0 + v2.1.1: no passphrase → failure.
        // The user must set a passphrase in Settings
        // before the daily worker can back anything up.
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns failure (not retry) when not signed in`() = runTest {
        // v2.1.1: the critical change. v2.1.0 returned
        // Result.retry which caused the worker to fire
        // on every cold start of a device that had never
        // signed in. v2.1.1 returns Result.failure.
        every { securePreferences.getBackupEncryptionKeyHash() } returns "hash"
        coEvery { driveBackupManager.backUpNow(any()) } throws
            DriveBackupManager.DriveBackupException.NotSignedIn()

        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(testWorkerFactory())
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns success when backup completes and file is non-empty`() = runTest {
        every { securePreferences.getBackupEncryptionKeyHash() } returns "hash"
        val file = mockk<DriveRestApi.DriveFile>()
        every { file.sizeBytes } returns 1024L
        coEvery { driveBackupManager.backUpNow(any()) } returns file

        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(testWorkerFactory())
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns retry when backup file is empty`() = runTest {
        every { securePreferences.getBackupEncryptionKeyHash() } returns "hash"
        val file = mockk<DriveRestApi.DriveFile>()
        every { file.sizeBytes } returns 0L
        coEvery { driveBackupManager.backUpNow(any()) } returns file

        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(testWorkerFactory())
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private fun testWorkerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = DriveBackupWorker(
            appContext = appContext,
            params = workerParameters,
            driveBackupManager = driveBackupManager,
            securePreferences = securePreferences,
        )
    }
}
