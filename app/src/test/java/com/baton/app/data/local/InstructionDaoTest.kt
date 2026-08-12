package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncStatus
import kotlinx.coroutines.flow.first
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
 * M3-T5 tests for [InstructionDao.observeOpenCountByPerson]. The
 * query groups instructions by `personId` and counts those that
 * are not in a terminal status (DONE / CARRIED_OVER / DROPPED).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstructionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: InstructionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.instructionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ins(
        id: String,
        personId: String?,
        status: String,
        capturedAt: String = "2026-08-12T00:00:00Z",
    ) = InstructionEntity(
        id = id,
        personId = personId,
        direction = "OUTGOING",
        status = status,
        source = "TEXT",
        priority = "NORMAL",
        title = "t-$id",
        rawText = "r-$id",
        dueAt = null,
        capturedAt = capturedAt,
        createdAt = capturedAt,
        updatedAt = capturedAt,
        syncStatus = SyncStatus.SYNCED,
    )

    @Test
    fun `observeOpenCountByPerson returns 0 rows for an empty table`() = runTest {
        val rows = dao.observeOpenCountByPerson().first()
        assertEquals(0, rows.size)
    }

    @Test
    fun `counts open instructions per person, ignoring closed ones`() = runTest {
        dao.upsert(ins("i1", "p1", "OPEN"))
        dao.upsert(ins("i2", "p1", "OPEN"))
        dao.upsert(ins("i3", "p1", "DONE"))           // closed — ignored
        dao.upsert(ins("i4", "p1", "CARRIED_OVER"))   // closed — ignored
        dao.upsert(ins("i5", "p2", "OPEN"))
        dao.upsert(ins("i6", "p2", "DROPPED"))        // closed — ignored
        dao.upsert(ins("i7", null, "OPEN"))           // no person — ignored

        val rows = dao.observeOpenCountByPerson().first()
        val byId = rows.associateBy { it.personId }
        assertEquals(2, byId["p1"]?.cnt)
        assertEquals(1, byId["p2"]?.cnt)
        assertTrue("null personId must not appear", byId.keys.none { it.isNullOrEmpty() })
    }

    @Test
    fun `instructions without a personId are excluded from the count`() = runTest {
        dao.upsert(ins("i1", null, "OPEN"))
        dao.upsert(ins("i2", null, "IN_PROGRESS"))

        val rows = dao.observeOpenCountByPerson().first()
        assertEquals("rows without a personId must be filtered out", 0, rows.size)
    }
}
