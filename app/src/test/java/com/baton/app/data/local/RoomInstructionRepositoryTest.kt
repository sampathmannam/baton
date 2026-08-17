package com.baton.app.data.local

import com.baton.app.data.instructions.Direction
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.instructions.Status
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0: tests for the RoomInstructionRepository's is_sensitive
 * filtering. The sync engine must drop any is_sensitive rows the
 * server returned (defensive — by spec these are local-only, but
 * if a future migration drops the row-level gate we still don't
 * want sensitive rows in the local mirror from the server).
 */
class RoomInstructionRepositoryTest {

    @Test
    fun `refreshFromNetwork filters out is_sensitive rows before upserting`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        val remote = mockk<SupabaseInstructionRepository>()
        val sensitiveId = "sensitive-1"
        val normalId = "normal-1"
        coEvery { remote.fetchAll() } returns listOf(
            instruction(id = sensitiveId, isSensitive = true),
            instruction(id = normalId, isSensitive = false),
        )

        val repo = com.baton.app.data.instructions.RoomInstructionRepository(
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = mockk(relaxed = true),
            syncEngine = mockk(relaxed = true),
            touchOnActivity = mockk(relaxed = true),
            appScope = kotlinx.coroutines.GlobalScope,
        )
        repo.refreshFromNetwork(remote)

        val captured = mutableListOf<List<InstructionEntity>>()
        coVerify { dao.upsertAll(capture(captured)) }
        assertEquals(1, captured.size)
        val upserted = captured.first()
        assertEquals(1, upserted.size)
        assertEquals(normalId, upserted.first().id)
        assertTrue(
            "is_sensitive row must be filtered out of the local mirror",
            upserted.none { it.id == sensitiveId },
        )
    }

    @Test
    fun `refreshFromNetwork handles all-sensitive response (upserts empty list)`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        val remote = mockk<SupabaseInstructionRepository>()
        coEvery { remote.fetchAll() } returns listOf(
            instruction(id = "a", isSensitive = true),
            instruction(id = "b", isSensitive = true),
        )

        val repo = com.baton.app.data.instructions.RoomInstructionRepository(
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = mockk(relaxed = true),
            syncEngine = mockk(relaxed = true),
            touchOnActivity = mockk(relaxed = true),
            appScope = kotlinx.coroutines.GlobalScope,
        )
        repo.refreshFromNetwork(remote)

        val captured = mutableListOf<List<InstructionEntity>>()
        coVerify { dao.upsertAll(capture(captured)) }
        assertEquals(0, captured.first().size)
    }

    /**
     * v1.1: markDone sets status=DONE, completedAt=now, refreshes
     * updatedAt (so the brief's 7-day window resets), and writes
     * PENDING_UPDATE so the sync engine will PATCH the server.
     */
    @Test
    fun `markDone transitions to DONE with completedAt and queues PENDING_UPDATE`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        val syncQueueDao = mockk<com.baton.app.data.local.SyncQueueDao>(relaxed = true)
        val syncEngine = mockk<com.baton.app.data.local.SyncEngine>(relaxed = true)
        val repo = com.baton.app.data.instructions.RoomInstructionRepository(
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = syncQueueDao,
            syncEngine = syncEngine,
            touchOnActivity = mockk(relaxed = true),
            appScope = kotlinx.coroutines.GlobalScope,
        )

        repo.markDone("ins-1")

        coVerify {
            dao.updateStatus(
                id = "ins-1",
                status = Status.DONE.name,
                updatedAt = any(),
                completedAt = any(),
                droppedReason = null,
                syncStatus = SyncStatus.PENDING_UPDATE,
            )
        }
        // The sync queue gets an UPDATE entry for the same row.
        coVerify {
            syncQueueDao.enqueue(match { it.table == "instructions" && it.rowId == "ins-1" && it.op == "UPDATE" })
        }
    }

    /**
     * v1.1: markDropped sets status=DROPPED, droppedReason, and
     * refreshes updatedAt. The row stays in Room (the spec's
     * silent drop is the carriedOver > 30 days rule).
     */
    @Test
    fun `markDropped transitions to DROPPED with reason`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        val syncQueueDao = mockk<com.baton.app.data.local.SyncQueueDao>(relaxed = true)
        val syncEngine = mockk<com.baton.app.data.local.SyncEngine>(relaxed = true)
        val repo = com.baton.app.data.instructions.RoomInstructionRepository(
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = syncQueueDao,
            syncEngine = syncEngine,
            touchOnActivity = mockk(relaxed = true),
            appScope = kotlinx.coroutines.GlobalScope,
        )

        repo.markDropped("ins-2", "Already handled offline")

        coVerify {
            dao.updateStatus(
                id = "ins-2",
                status = Status.DROPPED.name,
                updatedAt = any(),
                completedAt = null,
                droppedReason = "Already handled offline",
                syncStatus = SyncStatus.PENDING_UPDATE,
            )
        }
    }

    /**
     * v1.1: re-open clears completedAt/droppedReason and resets
     * status to OPEN. The 7-day brief window restarts.
     */
    @Test
    fun `reopen clears lifecycle fields and resets status to OPEN`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        val syncQueueDao = mockk<com.baton.app.data.local.SyncQueueDao>(relaxed = true)
        val syncEngine = mockk<com.baton.app.data.local.SyncEngine>(relaxed = true)
        val repo = com.baton.app.data.instructions.RoomInstructionRepository(
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = syncQueueDao,
            syncEngine = syncEngine,
            touchOnActivity = mockk(relaxed = true),
            appScope = kotlinx.coroutines.GlobalScope,
        )

        repo.reopen("ins-3")

        coVerify {
            dao.updateStatus(
                id = "ins-3",
                status = Status.OPEN.name,
                updatedAt = any(),
                completedAt = null,
                droppedReason = null,
                syncStatus = SyncStatus.PENDING_UPDATE,
            )
        }
    }

    private fun instruction(id: String, isSensitive: Boolean): Instruction = Instruction(
        id = id,
        personId = null,
        direction = Direction.INCOMING,
        status = Status.OPEN,
        source = Source.TEXT,
        priority = Priority.NORMAL,
        title = "t",
        rawText = "r",
        dueAt = null,
        capturedAt = "2026-08-12T00:00:00+05:30",
        createdAt = "2026-08-12T00:00:00+05:30",
        updatedAt = "2026-08-12T00:00:00+05:30",
        isSensitive = isSensitive,
    )
}
