package com.kaavalan.note.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.8.0 (PROD-READINESS-P2-P1-#4): the offline-
 * queue-cap test. Verifies that
 * [SyncQueueDao.trimToLimit] evicts the oldest
 * rows first (oldest-wins) and returns the correct
 * number of deletions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncQueueTrimTest {

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

    @Test
    fun `trimToLimit is a no-op when the queue is under the cap`() = runTest {
        repeat(5) { i ->
            dao.enqueue(makeRow("p$i"))
        }
        val deleted = dao.trimToLimit(maxSize = 100)
        assertEquals(0, deleted)
        assertEquals(5, dao.snapshot().size)
    }

    @Test
    fun `trimToLimit evicts the oldest rows when over the cap`() = runTest {
        repeat(10) { i ->
            dao.enqueue(makeRow("p$i"))
        }
        // Cap at 3 — 7 rows must be deleted.
        val deleted = dao.trimToLimit(maxSize = 3)
        assertEquals(7, deleted)
        val remaining = dao.snapshot()
        assertEquals(3, remaining.size)
        // The 3 survivors are the newest (p7, p8, p9).
        assertEquals("p7", remaining[0].rowId)
        assertEquals("p8", remaining[1].rowId)
        assertEquals("p9", remaining[2].rowId)
    }

    @Test
    fun `trimToLimit at exact cap is a no-op`() = runTest {
        repeat(100) { i ->
            dao.enqueue(makeRow("p$i"))
        }
        val deleted = dao.trimToLimit(maxSize = 100)
        assertEquals(0, deleted)
        assertEquals(100, dao.snapshot().size)
    }

    @Test
    fun `trimToLimit with cap=0 deletes every row`() = runTest {
        repeat(5) { i ->
            dao.enqueue(makeRow("p$i"))
        }
        val deleted = dao.trimToLimit(maxSize = 0)
        assertEquals(5, deleted)
        assertEquals(0, dao.snapshot().size)
    }

    @Test
    fun `repeated trim calls each delete the excess rows after a fresh insert`() = runTest {
        // v1.8.0 (PROD-READINESS-P2-P1-#4): the
        // enqueueWithCap path trims after every
        // insert. Simulate that here: insert 3
        // rows at the cap of 3, then insert a
        // 4th and trim again. Each insert + trim
        // should leave exactly 3 rows in the
        // queue, the 3 newest.
        val cap = 3
        repeat(5) { i ->
            dao.enqueue(makeRow("p$i"))
            dao.trimToLimit(cap)
        }
        val remaining = dao.snapshot()
        assertEquals(3, remaining.size)
        // p2 was the oldest-survivor after the
        // first trim; p3 replaced p2 after the
        // second insert + trim; ...; p4 is the
        // oldest-survivor after the 5th.
        assertEquals("p2", remaining[0].rowId)
        assertEquals("p3", remaining[1].rowId)
        assertEquals("p4", remaining[2].rowId)
    }

    private fun makeRow(rowId: String) = SyncQueueEntity(
        table = "persons",
        rowId = rowId,
        op = SyncQueueEntity.OP_INSERT,
        payloadJson = "{}",
        createdAt = System.currentTimeMillis(),
    )
}
