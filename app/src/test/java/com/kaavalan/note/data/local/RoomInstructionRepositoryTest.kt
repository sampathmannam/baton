package com.kaavalan.note.data.local

import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.local.entities.SyncStatus
import com.kaavalan.note.data.local.entities.SyncQueueEntity
import com.kaavalan.note.data.local.entities.InstructionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * v2.0.0 (drop Supabase): the RoomInstructionRepository is
 * local-only. The v1.x `refreshFromNetwork(remote: SupabaseInstructionRepository)`
 * path is gone (no remote to refresh from). The lifecycle
 * mutations ([markDone], [markDropped], [reopen]) are unchanged
 * except for the no-op `syncEngine` field (kept for forward-compat
 * with the `sync_queue` table; no rows are drained in v2.0.0).
 */
class RoomInstructionRepositoryTest {

    /**
     * v1.1: markDone sets status=DONE, completedAt=now, refreshes
     * updatedAt (so the brief's 7-day window resets), and writes
     * PENDING_UPDATE so a future v2.x cloud-sync drain can PATCH
     * the server.
     */
    @Test
    fun `markDone transitions to DONE with completedAt and queues PENDING_UPDATE`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        val syncQueueDao = mockk<com.kaavalan.note.data.local.SyncQueueDao>(relaxed = true)
        val repo = com.kaavalan.note.data.instructions.RoomInstructionRepository(
            db = mockk(relaxed = true),
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = syncQueueDao,
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
        // The sync queue gets an UPDATE entry for the same row
        // (forward-compat with a future v2.x cloud drain — no
        // rows are drained in v2.0.0 itself).
        coVerify {
            syncQueueDao.enqueue(match {
                it.table == "instructions" && it.rowId == "ins-1" && it.op == SyncQueueEntity.OP_UPDATE
            })
        }
    }

    /**
     * v1.1: markDropped sets status=DROPPED, droppedReason, and
     * refreshes updatedAt. The row stays in Room (the spec's
     * silent drop is the carriedOver > 30 days rule).
     */
    @Test
    fun `markDropped archives in place and preserves status`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        coEvery { dao.getById("ins-2") } returns instruction("ins-2")
        val syncQueueDao = mockk<com.kaavalan.note.data.local.SyncQueueDao>(relaxed = true)
        val repo = com.kaavalan.note.data.instructions.RoomInstructionRepository(
            db = mockk(relaxed = true),
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = syncQueueDao,
            touchOnActivity = mockk(relaxed = true),
            appScope = kotlinx.coroutines.GlobalScope,
        )

        repo.markDropped("ins-2", "Already handled offline")

        coVerify {
            dao.updateExisting(match {
                it.id == "ins-2" &&
                    it.status == Status.WAITING.name &&
                    it.archivedAtEpochMs != null &&
                    it.droppedReason == "Already handled offline" &&
                    it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
    }

    /**
     * v1.1: re-open clears completedAt/droppedReason and resets
     * status to OPEN. The 7-day brief window restarts.
     */
    @Test
    fun `reopen clears archive fields and preserves status`() = runTest {
        val dao = mockk<InstructionDao>(relaxed = true)
        coEvery { dao.getById("ins-3") } returns instruction("ins-3").copy(
            archivedAtEpochMs = 1L,
            droppedReason = "legacy reason",
        )
        val syncQueueDao = mockk<com.kaavalan.note.data.local.SyncQueueDao>(relaxed = true)
        val repo = com.kaavalan.note.data.instructions.RoomInstructionRepository(
            db = mockk(relaxed = true),
            dao = dao,
            ftsDao = mockk(relaxed = true),
            syncQueueDao = syncQueueDao,
            touchOnActivity = mockk(relaxed = true),
            appScope = kotlinx.coroutines.GlobalScope,
        )

        repo.reopen("ins-3")

        coVerify {
            dao.updateExisting(match {
                it.id == "ins-3" &&
                    it.status == Status.WAITING.name &&
                    it.archivedAtEpochMs == null &&
                    it.droppedReason == null &&
                    it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
    }

    private fun instruction(id: String) = InstructionEntity(
        id = id,
        personId = null,
        direction = "OUTGOING",
        status = Status.WAITING.name,
        source = "TEXT",
        priority = "NORMAL",
        title = "title",
        rawText = "raw",
        dueAt = null,
        capturedAt = "2026-08-31T00:00:00Z",
        createdAt = "2026-08-31T00:00:00Z",
        updatedAt = "2026-08-31T00:00:00Z",
    )
}
