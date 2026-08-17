package com.baton.app.data.dates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.ImportantDateDao
import com.baton.app.data.local.entities.ImportantDateEntity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * v2.0 Tier 2 (§2.5): the [ImportantDateRepository] is a thin
 * wrapper over [ImportantDateDao]. The tests exercise the
 * observe / add / delete / todayEpochDay paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportantDateRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var dao: ImportantDateDao
    private lateinit var repo: ImportantDateRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.importantDateDao()
        repo = ImportantDateRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `add then observeForPerson returns the new row`() = runTest {
        val personId = seedPerson("Kavitha")
        repo.add(personId, "Birthday", 19500L, recurring = true)
        val dates = repo.observeForPerson(personId).first()
        assertEquals(1, dates.size)
        assertEquals("Birthday", dates[0].label)
        assertEquals(19500L, dates[0].dateEpochDay)
        assertEquals(true, dates[0].recurring)
    }

    @Test
    fun `observeOnDay filters by epoch day`() = runTest {
        val personId = seedPerson("Kavitha")
        repo.add(personId, "Birthday", 19500L, recurring = false)
        repo.add(personId, "First met", 19600L, recurring = false)
        val onDay = repo.observeOnDay(19500L).first()
        assertEquals(1, onDay.size)
        assertEquals("Birthday", onDay[0].label)
    }

    @Test
    fun `observeBetweenDays spans a range`() = runTest {
        val personId = seedPerson("Kavitha")
        repo.add(personId, "A", 19500L, recurring = false)
        repo.add(personId, "B", 19600L, recurring = false)
        repo.add(personId, "C", 19700L, recurring = false)
        val between = repo.observeBetweenDays(19500L, 19600L).first()
        assertEquals(2, between.size)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val personId = seedPerson("Kavitha")
        val entity = repo.add(personId, "Birthday", 19500L, recurring = true)
        repo.delete(entity.id)
        val dates = repo.observeForPerson(personId).first()
        assertEquals(0, dates.size)
    }

    @Test
    fun `todayEpochDay returns a non-zero Long`() {
        val day = repo.todayEpochDay()
        assertNotNull(day)
        // A reasonable lower bound: 19000 (1972-01-01).
        assert(day > 19000L) { "todayEpochDay must be > 19000, was $day" }
    }

    private fun seedPerson(name: String): String {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        kotlinx.coroutines.runBlocking {
            db.personDao().upsert(PersonEntity(
                id = id,
                name = name,
                designation = null,
                station = null,
                phone = null,
                userId = "u1",
                createdAt = now,
                updatedAt = now,
                isSensitive = false,
                syncStatus = SyncStatus.SYNCED,
            ))
        }
        return id
    }
}
