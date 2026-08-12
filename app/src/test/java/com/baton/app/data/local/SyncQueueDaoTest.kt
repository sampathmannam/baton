package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncQueueDaoTest {

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
    fun `enqueue then snapshot returns the row`() = runTest {
        dao.enqueue(
            SyncQueueEntity(
                table = "persons",
                rowId = "p1",
                op = SyncQueueEntity.OP_INSERT,
                payloadJson = "{}",
                createdAt = 1L,
            )
        )
        val rows = dao.snapshot()
        assertEquals(1, rows.size)
        assertEquals("persons", rows[0].table)
        assertEquals("p1", rows[0].rowId)
        assertEquals(SyncQueueEntity.OP_INSERT, rows[0].op)
    }

    @Test
    fun `findPending returns the entry by (table, rowId, op)`() = runTest {
        dao.enqueue(
            SyncQueueEntity(
                table = "persons", rowId = "p1", op = SyncQueueEntity.OP_INSERT,
                payloadJson = "{}", createdAt = 1L,
            )
        )
        val found = dao.findPending("persons", "p1", SyncQueueEntity.OP_INSERT)
        assertNotNull(found)
        val missing = dao.findPending("persons", "p2", SyncQueueEntity.OP_INSERT)
        assertNull(missing)
        val wrongOp = dao.findPending("persons", "p1", SyncQueueEntity.OP_UPDATE)
        assertNull(wrongOp)
    }

    @Test
    fun `deleteById removes the entry`() = runTest {
        val id = dao.enqueue(
            SyncQueueEntity(
                table = "persons", rowId = "p1", op = SyncQueueEntity.OP_INSERT,
                payloadJson = "{}", createdAt = 1L,
            )
        )
        assertEquals(1, dao.snapshot().size)
        dao.deleteById(id)
        assertEquals(0, dao.snapshot().size)
    }

    @Test
    fun `recordFailure increments attempts and records lastError`() = runTest {
        val id = dao.enqueue(
            SyncQueueEntity(
                table = "persons", rowId = "p1", op = SyncQueueEntity.OP_INSERT,
                payloadJson = "{}", createdAt = 1L,
            )
        )
        dao.recordFailure(id, "Network error")
        val entry = dao.snapshot().first()
        assertEquals(1, entry.attempts)
        assertEquals("Network error", entry.lastError)
    }
}
