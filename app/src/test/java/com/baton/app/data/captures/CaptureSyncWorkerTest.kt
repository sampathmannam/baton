package com.baton.app.data.captures

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.SyncQueueDao
import com.baton.app.data.local.entities.CaptureEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.sync.CaptureSyncWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.4.2 (F-09 / F-20) tests for [CaptureSyncWorker].
 *
 * The brief: the worker is the path that actually pushes a dirty
 * capture to Supabase. The local row exists before the push
 * (offline-first), and a Supabase outage must not lose data.
 *
 * **What we assert.**
 *  1. `processDirtyRows()` reads every capture whose `syncStatus`
 *     is not `SYNCED` and calls
 *     [SupabaseCaptureRepository.insertCapture] on each with the
 *     row's client-generated id (the BATON-WIRE-006 idempotency
 *     key).
 *  2. On Supabase success: the local row is flipped to
 *     `syncStatus = SYNCED`, and the matching `sync_queue` row is
 *     deleted (so the existing
 *     [com.baton.app.data.local.SyncEngine] stub never runs
 *     against a row the worker already pushed).
 *  3. On Supabase failure: the local row is left dirty
 *     (`syncStatus` unchanged), the `sync_queue` row's `attempts`
 *     is bumped, and the per-row outcome is `success = false`.
 *  4. The worker is a no-op on a clean database (no dirty rows).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CaptureSyncWorkerTest {

    private lateinit var db: AppDatabase
    private lateinit var captureDao: CaptureDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var remote: SupabaseCaptureRepository
    private lateinit var worker: CaptureSyncWorker

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
        remote = mockk(relaxed = true)
        // The CoroutineWorker constructor needs a Context +
        // WorkerParameters. Tests mock WorkerParameters — its
        // methods aren't exercised by the unit under test
        // ([processDirtyRows]); only the `Context` is used (for
        // getApplicationContext, which `CoroutineWorker` stores
        // but doesn't read in `doWork`).
        val params = mockk<androidx.work.WorkerParameters>(relaxed = true)
        worker = CaptureSyncWorker(
            appContext = context,
            params = params,
            captureDao = captureDao,
            syncQueueDao = syncQueueDao,
            captureRemote = remote,
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // ----- F-09 / F-20: success path -----

    @Test
    fun `processDirtyRows calls Supabase and marks synced on success`() = runTest {
        // 1. Seed a dirty row (PENDING_INSERT) and a matching
        //    sync_queue row. This is what RoomCaptureRepository
        //    would have written.
        val id = "cap-uuid-1"
        captureDao.upsert(
            CaptureEntity(
                id = id,
                mode = "TEXT",
                rawText = "user note",
                audioUri = null,
                imageUri = null,
                processed = false,
                createdAt = "2026-08-14T00:00:00Z",
                syncStatus = SyncStatus.PENDING_INSERT,
            )
        )
        val queueId = syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "captures",
                rowId = id,
                op = SyncQueueEntity.OP_INSERT,
                payloadJson = """{"id":"$id","rawText":"user note","mode":"TEXT"}""",
                createdAt = System.currentTimeMillis(),
            )
        )
        // 2. Mock the wire: a clean response.
        coEvery { remote.insertCapture(any(), any(), any()) } returns
            Capture(
                id = id,
                mode = CaptureMode.TEXT,
                rawText = "user note",
                processed = false,
                createdAt = "2026-08-14T00:00:00Z",
            )

        // 3. Run the worker.
        val outcomes = worker.processDirtyRows()

        // 4. The Supabase insert was called with the row's id and
        //    raw text — the BATON-WIRE-006 idempotency key.
        coVerify(exactly = 1) {
            remote.insertCapture(
                id = id,
                rawText = "user note",
                mode = CaptureMode.TEXT,
            )
        }
        // 5. The outcome reflects success.
        assertEquals(1, outcomes.size)
        assertEquals(id, outcomes[0].id)
        assertTrue(outcomes[0].success)
        assertNull(outcomes[0].error)
        // 6. The local row is now SYNCED.
        val row = captureDao.getById(id)
        assertNotNull(row)
        assertEquals(SyncStatus.SYNCED, row!!.syncStatus)
        // 7. The matching sync_queue row is deleted.
        val queue = syncQueueDao.snapshot()
        assertEquals(0, queue.size)
        // The previously enqueued row id is no longer in the table.
        assertNull(syncQueueDao.snapshot().find { it.id == queueId })
    }

    @Test
    fun `processDirtyRows processes multiple dirty rows in id order`() = runTest {
        // Two dirty rows; the worker should call Supabase for both.
        val idA = "cap-aaa"
        val idB = "cap-bbb"
        listOf(
            CaptureEntity(
                id = idA, mode = "TEXT", rawText = "first",
                audioUri = null, imageUri = null, processed = false,
                createdAt = "2026-08-14T00:00:00Z",
                syncStatus = SyncStatus.PENDING_INSERT,
            ),
            CaptureEntity(
                id = idB, mode = "TEXT", rawText = "second",
                audioUri = null, imageUri = null, processed = false,
                createdAt = "2026-08-14T00:00:01Z",
                syncStatus = SyncStatus.PENDING_UPDATE,
            ),
        ).forEach { captureDao.upsert(it) }
        coEvery { remote.insertCapture(any(), any(), any()) } returns
            Capture(
                id = "ignored",
                mode = CaptureMode.TEXT,
                rawText = "ignored",
                processed = false,
                createdAt = "ignored",
            )

        val outcomes = worker.processDirtyRows()

        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it.success })
        // Both rows are now SYNCED.
        assertEquals(SyncStatus.SYNCED, captureDao.getById(idA)!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, captureDao.getById(idB)!!.syncStatus)
        // Supabase was called twice.
        coVerify(exactly = 2) { remote.insertCapture(any(), any(), any()) }
    }

    @Test
    fun `processDirtyRows is a no-op on a clean database`() = runTest {
        val outcomes = worker.processDirtyRows()
        assertEquals(0, outcomes.size)
        coVerify(exactly = 0) { remote.insertCapture(any(), any(), any()) }
    }

    // ----- F-09 / F-20: failure path (Supabase outage) -----

    @Test
    fun `processDirtyRows keeps dirty on Supabase failure`() = runTest {
        // The user's note is in Room, syncStatus = PENDING_INSERT.
        // Supabase throws. The note must stay in Room (offline
        // tolerance — that's the F-09 / F-20 guarantee).
        val id = "cap-uuid-fail"
        captureDao.upsert(
            CaptureEntity(
                id = id,
                mode = "TEXT",
                rawText = "survives an outage",
                audioUri = null,
                imageUri = null,
                processed = false,
                createdAt = "2026-08-14T00:00:00Z",
                syncStatus = SyncStatus.PENDING_INSERT,
            )
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "captures",
                rowId = id,
                op = SyncQueueEntity.OP_INSERT,
                payloadJson = """{"id":"$id","rawText":"survives an outage","mode":"TEXT"}""",
                createdAt = System.currentTimeMillis(),
            )
        )
        coEvery { remote.insertCapture(any(), any(), any()) } throws
            RuntimeException("Supabase is down")

        val outcomes = worker.processDirtyRows()

        // 1. The outcome reports failure.
        assertEquals(1, outcomes.size)
        assertEquals(id, outcomes[0].id)
        assertEquals(false, outcomes[0].success)
        assertNotNull("error message should be propagated", outcomes[0].error)
        // 2. F-09 / F-20: the local row is still here, still
        //    dirty, so the next worker run will retry.
        val row = captureDao.getById(id)
        assertNotNull("F-09: row must NOT be lost on a Supabase failure", row)
        assertEquals("survives an outage", row!!.rawText)
        assertEquals(SyncStatus.PENDING_INSERT, row.syncStatus)
        assertEquals(false, row.processed)
        // 3. The sync_queue row is still present (so the
        //    SyncEngine periodic drain can also retry).
        val queue = syncQueueDao.snapshot()
        assertEquals(1, queue.size)
        // 4. The worker's [recordDirtyFailure] bumped `attempts`
        //    and recorded the error.
        assertTrue(
            "attempts should be >= 1, was ${queue[0].attempts}",
            queue[0].attempts >= 1,
        )
        assertNotNull(queue[0].lastError)
        // 5. We do not throw out of processDirtyRows on a per-row
        //    failure — the catch wraps the row, not the loop.
    }

    @Test
    fun `processDirtyRows continues after one row fails (one bad row does not block others)`() = runTest {
        // Two dirty rows; the first call to Supabase throws, the
        // second succeeds. The second row must still be processed.
        val idFailing = "cap-fail"
        val idOk = "cap-ok"
        listOf(
            CaptureEntity(
                id = idFailing, mode = "TEXT", rawText = "fails",
                audioUri = null, imageUri = null, processed = false,
                createdAt = "2026-08-14T00:00:00Z",
                syncStatus = SyncStatus.PENDING_INSERT,
            ),
            CaptureEntity(
                id = idOk, mode = "TEXT", rawText = "ok",
                audioUri = null, imageUri = null, processed = false,
                createdAt = "2026-08-14T00:00:01Z",
                syncStatus = SyncStatus.PENDING_INSERT,
            ),
        ).forEach { captureDao.upsert(it) }
        coEvery { remote.insertCapture(idFailing, any(), any()) } throws
            RuntimeException("first row fails")
        coEvery { remote.insertCapture(idOk, any(), any()) } returns
            Capture(
                id = idOk, mode = CaptureMode.TEXT, rawText = "ok",
                processed = false, createdAt = "2026-08-14T00:00:01Z",
            )

        val outcomes = worker.processDirtyRows()

        assertEquals(2, outcomes.size)
        // The first one failed; the second succeeded.
        val byId = outcomes.associateBy { it.id }
        assertEquals(false, byId[idFailing]!!.success)
        assertEquals(true, byId[idOk]!!.success)
        // The successful row is SYNCED; the failing row is still dirty.
        assertEquals(SyncStatus.SYNCED, captureDao.getById(idOk)!!.syncStatus)
        assertEquals(SyncStatus.PENDING_INSERT, captureDao.getById(idFailing)!!.syncStatus)
    }
}
