package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncStatus
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

/**
 * In-memory Room tests for [PersonDao]. Robolectric gives us a real
 * Android Context; `inMemoryDatabaseBuilder` keeps everything in
 * RAM so the test is fast and isolated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersonDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.personDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun person(
        id: String = "p-${System.nanoTime()}",
        name: String = "Ramu",
        syncStatus: String = SyncStatus.SYNCED,
    ) = PersonEntity(
        id = id,
        name = name,
        designation = "SHO",
        station = "Bandipora",
        phone = null,
        userId = "u-1",
        createdAt = "2026-08-12T00:00:00Z",
        updatedAt = "2026-08-12T00:00:00Z",
        syncStatus = syncStatus,
    )

    @Test
    fun `upsert then observeAll returns the row`() = runTest {
        val p = person(name = "Ramu")
        dao.upsert(p)
        val rows = dao.snapshot()
        assertEquals(1, rows.size)
        assertEquals("Ramu", rows[0].name)
        assertEquals(p.id, rows[0].id)
    }

    @Test
    fun `observeAll emits sorted by name ascending case-insensitive`() = runTest {
        dao.upsert(person(id = "1", name = "Priya"))
        dao.upsert(person(id = "2", name = "ramu"))  // lower-case r
        dao.upsert(person(id = "3", name = "Anand"))

        dao.observeAll().test {
            val rows = awaitItem()
            // COL NOCASE sorts by the lowercase comparison: 'anand' <
            // 'priya' < 'ramu'. The visible-name sort puts Anand
            // first, then Priya, then ramu — case is ignored.
            assertEquals(listOf("3", "1", "2"), rows.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `findByName returns the row case-insensitive`() = runTest {
        dao.upsert(person(id = "1", name = "Ramu"))
        assertNotNull(dao.findByName("ramu"))
        assertNotNull(dao.findByName("RAMU"))
        assertNull(dao.findByName("missing"))
    }

    @Test
    fun `upsertAll replaces all rows on conflict`() = runTest {
        dao.upsert(person(id = "1", name = "Ramu"))
        dao.upsert(person(id = "1", name = "Ramu Updated"))
        assertEquals(1, dao.snapshot().size)
        assertEquals("Ramu Updated", dao.snapshot().first().name)
    }

    @Test
    fun `setSyncStatus flips PENDING to SYNCED`() = runTest {
        val id = "1"
        dao.upsert(person(id = id, name = "Ramu", syncStatus = SyncStatus.PENDING_INSERT))
        dao.setSyncStatus(id, SyncStatus.SYNCED, "2026-08-12T00:01:00Z")
        val row = dao.getById(id)
        assertNotNull(row)
        assertEquals(SyncStatus.SYNCED, row!!.syncStatus)
        assertEquals("2026-08-12T00:01:00Z", row.updatedAt)
    }

    @Test
    fun `deleteById removes the row`() = runTest {
        val id = "1"
        dao.upsert(person(id = id, name = "Ramu"))
        assertNotNull(dao.getById(id))
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }
}
