package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.4.2 (DATA-FINDING-04): the sync_queue outbox must enforce
 * the per-row singleton invariant — at most one entry per
 * `(op, table, rowId)`. Before this fix, a user who tapped
 * `markDone` twice in quick succession enqueued two UPDATE rows
 * for the same instruction, and the drain would re-PATCH
 * Supabase twice. The invariant is now enforced at the DB layer
 * via a UNIQUE INDEX on `(op, table, rowId)` declared on
 * [SyncQueueEntity], and [SyncQueueDao.enqueue] is configured
 * with `OnConflictStrategy.REPLACE` so the latest payload wins.
 *
 * These tests use the in-memory `AppDatabase` (Robolectric) so
 * they exercise the real Room-generated DDL with the unique
 * index in place — they do NOT use raw SQLite, so any
 * regression on the @Entity declaration or the DAO conflict
 * strategy is caught.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncQueueDaoDedupTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SyncQueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncQueueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * DATA-FINDING-04 scenario: `markDone` (or any other
     * action) enqueues the same `(op, table, rowId)` twice in
     * quick succession. The second enqueue must collapse to a
     * single row, and because the DAO uses REPLACE the surviving
     * row must carry the latest payload and a fresh auto-id.
     */
    @Test
    fun `enqueueing the same UPDATE twice keeps only the latest row`() = runTest {
        val firstId = dao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = "row-1",
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = "{\"status\":\"DONE\",\"stamp\":1}",
                createdAt = 1L,
            )
        )
        val secondId = dao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = "row-1",
                op = SyncQueueEntity.OP_UPDATE,
                // Different payload — the second tap is a fresh
                // write, so its payload must win.
                payloadJson = "{\"status\":\"DONE\",\"stamp\":2}",
                createdAt = 2L,
            )
        )

        val rows = dao.snapshot()
        assertEquals(
            "double enqueue of the same (op, table, rowId) must dedup to one row",
            1,
            rows.size,
        )
        val survivor = rows.single()
        // REPLACE means the old id is gone; the new row has a
        // fresh auto-id. We don't assert specific id values —
        // just that the surviving id is the second enqueue's id.
        assertEquals(secondId, survivor.id)
        assertNotEquals(
            "REPLACE should not preserve the first enqueue's id",
            firstId,
            survivor.id,
        )
        // Latest payload must win.
        assertEquals(
            "{\"status\":\"DONE\",\"stamp\":2}",
            survivor.payloadJson,
        )
        // Fresh attempt semantics: failure / backoff state
        // resets on REPLACE so a duplicate enqueue doesn't
        // inherit a stale `attempts` / `lastError`.
        assertEquals(0, survivor.attempts)
        assertEquals(null, survivor.lastError)
        assertEquals(0L, survivor.nextAttemptAt)
    }

    /**
     * A pending UPDATE and a pending DELETE for the same row
     * are different ops — they must coexist. (In practice the
     * delete is enqueued only after the user explicitly deletes
     * the row, but the DB invariant doesn't know that and
     * shouldn't have to.)
     */
    @Test
    fun `UPDATE and DELETE for the same rowId coexist`() = runTest {
        dao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = "row-1",
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = "{\"status\":\"DONE\"}",
                createdAt = 1L,
            )
        )
        dao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = "row-1",
                op = SyncQueueEntity.OP_DELETE,
                payloadJson = "{}",
                createdAt = 2L,
            )
        )

        val rows = dao.snapshot()
        assertEquals(2, rows.size)
        val ops = rows.map { it.op }.toSet()
        assertTrue(
            "both ops must be present, got $ops",
            ops == setOf(SyncQueueEntity.OP_UPDATE, SyncQueueEntity.OP_DELETE),
        )
    }

    /**
     * Two UPDATEs for different rowIds are independent writes —
     * the unique index is on the triple, not on rowId alone.
     */
    @Test
    fun `UPDATEs for different rowIds coexist`() = runTest {
        dao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = "row-1",
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = "{\"status\":\"DONE\"}",
                createdAt = 1L,
            )
        )
        dao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = "row-2",
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = "{\"status\":\"DROPPED\"}",
                createdAt = 2L,
            )
        )

        val rows = dao.snapshot()
        assertEquals(2, rows.size)
        val byRowId = rows.associateBy { it.rowId }
        assertNotNull(byRowId["row-1"])
        assertNotNull(byRowId["row-2"])
        assertEquals(
            "{\"status\":\"DONE\"}",
            byRowId.getValue("row-1").payloadJson,
        )
        assertEquals(
            "{\"status\":\"DROPPED\"}",
            byRowId.getValue("row-2").payloadJson,
        )
    }
}
