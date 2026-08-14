package com.baton.app.data.captures

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.SyncQueueDao
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.4.2 (F-09 / F-20) tests for [RoomCaptureRepository].
 *
 * The brief: when the user creates a capture, the note must be in
 * local Room *before* any network call so a Supabase outage can't
 * cost data. This test class exercises that offline-first
 * guarantee using an in-memory Room database (no real Supabase, no
 * real network).
 *
 * **What we assert.**
 *  1. `create()` writes a Room row with `syncStatus = PENDING_INSERT`
 *     and `processed = false`.
 *  2. `create()` enqueues a `sync_queue` row (`table = "captures"`,
 *     op = `INSERT`) so the existing [com.baton.app.data.local.SyncEngine]
 *     and the new [com.baton.app.data.sync.CaptureSyncWorker] can
 *     drain it.
 *  3. `create()` returns a [Capture] to the caller synchronously,
 *     with the client-generated UUID — the caller's hot path
 *     never sees a network error.
 *  4. `markProcessed()` updates the Room row to
 *     `processed = true, syncStatus = PENDING_UPDATE` and enqueues
 *     a `sync_queue` UPDATE row.
 *  5. The local row is durable: a `create()` followed by reading
 *     the row back returns the same id, even though we never
 *     touched the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomCaptureRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var captureDao: CaptureDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var repo: RoomCaptureRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        captureDao = db.captureDao()
        syncQueueDao = db.syncQueueDao()
        repo = RoomCaptureRepository(
            dao = captureDao,
            syncQueueDao = syncQueueDao,
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // ----- F-09 / F-20: offline-first create() -----

    @Test
    fun `create writes Room row with PENDING_INSERT and enqueues sync queue row`() = runTest {
        val capture = repo.create(rawText = "First note of the day", mode = CaptureMode.TEXT)

        // 1. The returned Capture has the right shape.
        assertEquals("First note of the day", capture.rawText)
        assertEquals(CaptureMode.TEXT, capture.mode)
        assertEquals(false, capture.processed)
        // 2. The Room row exists.
        val row = captureDao.getById(capture.id)
        assertNotNull("Room row must exist immediately after create()", row)
        assertEquals("First note of the day", row!!.rawText)
        // 3. F-09: the row is PENDING_INSERT (dirty / needs sync).
        // This is the wire the rest of the system uses to know the
        // row hasn't been pushed to Supabase yet.
        assertEquals(SyncStatus.PENDING_INSERT, row.syncStatus)
        // 4. The sync_queue row is enqueued so the worker / engine
        // can drain it.
        val queue = syncQueueDao.snapshot()
        assertEquals(1, queue.size)
        val entry = queue[0]
        assertEquals("captures", entry.table)
        assertEquals(capture.id, entry.rowId)
        assertEquals(SyncQueueEntity.OP_INSERT, entry.op)
        // 5. The payload is a JSON object carrying the
        // (id, rawText, mode) triple that the worker needs.
        assertTrue(
            "payload must contain the row id, was: ${entry.payloadJson}",
            entry.payloadJson.contains("\"id\":\"${capture.id}\""),
        )
        assertTrue(
            "payload must contain the raw text, was: ${entry.payloadJson}",
            entry.payloadJson.contains("\"rawText\":\"First note of the day\""),
        )
    }

    @Test
    fun `create returns synchronously without touching the network`() = runTest {
        // F-09 / F-20: a Supabase outage must not lose the user's
        // note. We don't pass a remote repository to the Room repo
        // (it doesn't take one); the test only asserts that the
        // local row exists after the call returns. A real Supabase
        // outage would manifest as a throw from the worker's
        // `insertCapture`; the user's note is already safe in
        // Room at that point.
        val capture = repo.create(rawText = "Offline note", mode = CaptureMode.TEXT)
        // Caller sees the local row immediately.
        assertEquals("Offline note", capture.rawText)
        // The Room row is durable across the call boundary.
        val row = captureDao.getById(capture.id)
        assertNotNull(row)
        assertEquals(SyncStatus.PENDING_INSERT, row!!.syncStatus)
    }

    @Test
    fun `create with PHOTO mode preserves mode through to Room row`() = runTest {
        val capture = repo.create(rawText = "OCR'd text from a photo", mode = CaptureMode.PHOTO)
        val row = captureDao.getById(capture.id)
        assertNotNull(row)
        assertEquals("PHOTO", row!!.mode)
        // sync_queue row carries the mode too.
        val entry = syncQueueDao.snapshot().first()
        assertTrue(
            "payload must carry PHOTO mode, was: ${entry.payloadJson}",
            entry.payloadJson.contains("\"mode\":\"PHOTO\""),
        )
    }

    // ----- F-09 / F-20: markProcessed() -----

    @Test
    fun `markProcessed updates Room row and enqueues UPDATE sync queue row`() = runTest {
        val capture = repo.create(rawText = "Some note", mode = CaptureMode.TEXT)
        // Drain the INSERT row from the queue so we can isolate the
        // UPDATE row that markProcessed enqueues.
        syncQueueDao.snapshot().forEach { syncQueueDao.deleteById(it.id) }

        repo.markProcessed(capture.id)

        // 1. Local row is processed = true.
        val row = captureDao.getById(capture.id)
        assertNotNull(row)
        assertEquals(true, row!!.processed)
        // 2. syncStatus is PENDING_UPDATE (dirty, awaiting wire PATCH).
        assertEquals(SyncStatus.PENDING_UPDATE, row.syncStatus)
        // 3. A UPDATE sync_queue row is enqueued.
        val queue = syncQueueDao.snapshot()
        assertEquals(1, queue.size)
        assertEquals(SyncQueueEntity.OP_UPDATE, queue[0].op)
        assertEquals(capture.id, queue[0].rowId)
    }

    @Test
    fun `markProcessed on a non-existent id is a no-op (no crash, no queue row)`() = runTest {
        repo.markProcessed("does-not-exist")
        val queue = syncQueueDao.snapshot()
        // No row in Room to read back, so no UPDATE enqueued.
        assertEquals(0, queue.size)
    }
}
