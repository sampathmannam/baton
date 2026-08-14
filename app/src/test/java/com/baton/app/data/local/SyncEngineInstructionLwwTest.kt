package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.captures.SupabaseCaptureRepository
import com.baton.app.data.instructions.Direction
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.instructions.Status
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncConflictEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.person.SupabasePersonRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
 * v1.4.2 (DATA-FINDING-02): tests for the LWW conflict check in
 * [SyncEngine.processInstructionEntry].
 *
 * **Why this file exists.** The persons sync path has had LWW
 * since M2-T8: the drain reads the server's `updated_at` and
 * drops the local write if the server is newer. The instructions
 * path was missing the same check, which would let a stale local
 * write (e.g. from an offline device) clobber a newer server
 * value (e.g. marked-done from another device) and silently
 * rewrite the authoritative state. DATA-FINDING-02 calls for the
 * same LWW rule on the instructions path.
 *
 * **Three cases, deterministic rule.**
 *  - `server newer` → local PATCH is dropped, conflict logged,
 *    server state mirrored into Room.
 *  - `local newer` → local PATCH goes through, no conflict.
 *  - `equal timestamps` → local PATCH goes through. Equal
 *    timestamps are treated as "server is not newer" because
 *    [SyncEngine.isServerNewer] uses strict `>`; this matches the
 *    `no conflict when updated_at equal` test on the persons path
 *    and keeps the LWW rule deterministic and stable across
 *    devices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineInstructionLwwTest {

    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var instructionDao: InstructionDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var syncConflictDao: SyncConflictDao
    private lateinit var personRemote: SupabasePersonRepository
    private lateinit var captureRemote: SupabaseCaptureRepository
    private lateinit var instructionRemote: SupabaseInstructionRepository
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        personDao = db.personDao()
        instructionDao = db.instructionDao()
        syncQueueDao = db.syncQueueDao()
        syncConflictDao = db.syncConflictDao()
        personRemote = mockk(relaxed = true)
        captureRemote = mockk(relaxed = true)
        instructionRemote = mockk(relaxed = true)
        engine = SyncEngine(
            syncQueueDao = syncQueueDao,
            personDao = personDao,
            captureDao = db.captureDao(),
            instructionDao = instructionDao,
            syncConflictDao = syncConflictDao,
            personRemote = personRemote,
            captureRemote = captureRemote,
            instructionRemote = instructionRemote,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Insert a local instruction row + enqueue the OP_UPDATE that
     * the drain will pick up. The local row's `updatedAt` is the
     * value the LWW check will compare against the server's
     * `updatedAt`. The payload is `"{}"` to match what
     * [com.baton.app.data.instructions.RoomInstructionRepository]
     * enqueues in production (the drain reads the row from Room,
     * not from the queue payload).
     */
    private suspend fun enqueueInstructionUpdate(
        id: String = "inst-1",
        localUpdatedAt: String = "2026-08-10T00:00:00Z",
    ) {
        instructionDao.upsert(
            InstructionEntity(
                id = id,
                personId = "person-1",
                direction = Direction.OUTGOING.name,
                status = Status.OPEN.name,
                source = Source.TEXT.name,
                priority = Priority.NORMAL.name,
                title = "Follow up on FIR",
                rawText = "Please follow up on the Bandipora FIR",
                dueAt = null,
                capturedAt = "2026-08-10T00:00:00Z",
                createdAt = "2026-08-10T00:00:00Z",
                updatedAt = localUpdatedAt,
                isSensitive = false,
                syncStatus = SyncStatus.PENDING_UPDATE,
                completedAt = null,
                droppedReason = null,
            )
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = id,
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = "{}",
                createdAt = 1L,
            )
        )
    }

    /** Build a server-side [Instruction] for the mock `findById` response. */
    private fun stubServerInstruction(
        id: String = "inst-1",
        status: Status = Status.OPEN,
        completedAt: String? = null,
        droppedReason: String? = null,
        isSensitive: Boolean = false,
        updatedAt: String,
    ): Instruction = Instruction(
        id = id,
        personId = "person-1",
        direction = Direction.OUTGOING,
        status = status,
        source = Source.TEXT,
        priority = Priority.NORMAL,
        title = "Server-side title",
        rawText = "Server-side raw text",
        dueAt = null,
        capturedAt = "2026-08-09T00:00:00Z",
        createdAt = "2026-08-09T00:00:00Z",
        updatedAt = updatedAt,
        isSensitive = isSensitive,
        completedAt = completedAt,
        droppedReason = droppedReason,
    )

    @Test
    fun `instruction OP_UPDATE drops local write when server is newer and logs conflict`() = runTest {
        // Local row was last touched on Aug 10. The server has a
        // newer version (DONE) from Aug 12. The local PATCH must be
        // dropped and a row logged in sync_conflicts.
        val oldLocal = "2026-08-10T00:00:00Z"
        val newerServer = "2026-08-12T00:00:00Z"
        enqueueInstructionUpdate(id = "inst-1", localUpdatedAt = oldLocal)
        coEvery { instructionRemote.findById("inst-1") } returns stubServerInstruction(
            status = Status.DONE,
            completedAt = "2026-08-12T00:00:00Z",
            updatedAt = newerServer,
        )
        // If a conflict is detected we should NOT call update().
        coEvery { instructionRemote.update(any(), any(), any(), any(), any()) } throws
            AssertionError("update() must not be called when a conflict is detected")

        engine.drainOne(rowId = "inst-1", table = "instructions", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        // 1. Queue is drained (the conflict path returns from
        //    processInstructionEntry without throwing, so the
        //    outer drain deletes the queue row).
        assertEquals(0, syncQueueDao.snapshot().size)
        // 2. Local row is overwritten with the server's newer value.
        val local = instructionDao.getById("inst-1")
        assertNotNull(local)
        assertEquals(Status.DONE.name, local!!.status)
        assertEquals("2026-08-12T00:00:00Z", local.completedAt)
        assertEquals(newerServer, local.updatedAt)
        assertEquals(SyncStatus.SYNCED, local.syncStatus)
        // 3. Conflict is logged.
        val conflicts = syncConflictDao.forRow("instructions", "inst-1")
        assertEquals(1, conflicts.size)
        val c: SyncConflictEntity = conflicts[0]
        assertEquals(SyncEngine.REASON_SERVER_NEWER, c.reason)
        // The local payload should mention the local status (OPEN);
        // the server payload should mention the server status (DONE).
        assertTrue("local payload should contain OPEN status", c.localPayload.contains("OPEN"))
        assertTrue("server payload should contain DONE status", c.serverPayload.contains("DONE"))
    }

    @Test
    fun `instruction OP_UPDATE proceeds when local is newer than server`() = runTest {
        // Local is Aug 12, server is Aug 10. Local wins; the PATCH
        // is applied and the local row's syncStatus flips to
        // SYNCED. No conflict is logged.
        val newerLocal = "2026-08-12T00:00:00Z"
        val olderServer = "2026-08-10T00:00:00Z"
        enqueueInstructionUpdate(id = "inst-1", localUpdatedAt = newerLocal)
        coEvery { instructionRemote.findById("inst-1") } returns stubServerInstruction(
            updatedAt = olderServer,
        )
        // update() must be called once with the lifecycle fields
        // from the local row.
        coEvery { instructionRemote.update(any(), any(), any(), any(), any()) } returns
            stubServerInstruction(updatedAt = "2026-08-12T01:00:00Z")

        engine.drainOne(rowId = "inst-1", table = "instructions", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        // No conflict was logged.
        assertEquals(0, syncConflictDao.count())
        // The PATCH was made with the local row's lifecycle fields.
        coVerify(exactly = 1) {
            instructionRemote.update(
                id = "inst-1",
                status = Status.OPEN,
                completedAt = null,
                droppedReason = null,
                isSensitive = false,
            )
        }
        // The local row is SYNCED.
        val local = instructionDao.getById("inst-1")
        assertEquals(SyncStatus.SYNCED, local!!.syncStatus)
    }

    @Test
    fun `instruction OP_UPDATE proceeds when updated_at is equal`() = runTest {
        // Equal timestamps = local wins (matches the
        // `isServerNewer` strict-`>` rule used by the persons
        // path; documented in the production code as the
        // deterministic equal-timestamp rule).
        val same = "2026-08-12T00:00:00Z"
        enqueueInstructionUpdate(id = "inst-1", localUpdatedAt = same)
        coEvery { instructionRemote.findById("inst-1") } returns stubServerInstruction(
            updatedAt = same,
        )
        coEvery { instructionRemote.update(any(), any(), any(), any(), any()) } returns
            stubServerInstruction(updatedAt = same)

        engine.drainOne(rowId = "inst-1", table = "instructions", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        // No conflict was logged.
        assertEquals(0, syncConflictDao.count())
        // The PATCH was made.
        coVerify(exactly = 1) {
            instructionRemote.update(
                id = "inst-1",
                status = Status.OPEN,
                completedAt = null,
                droppedReason = null,
                isSensitive = false,
            )
        }
        // The local row is SYNCED.
        val local = instructionDao.getById("inst-1")
        assertEquals(SyncStatus.SYNCED, local!!.syncStatus)
    }
}
