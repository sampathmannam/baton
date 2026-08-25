package com.kaavalan.note.data.links

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.PersonLinkDao
import com.kaavalan.note.data.local.entities.PersonEntity
import com.kaavalan.note.data.local.entities.SyncStatus
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * v2.0 Tier 2 (§2.12): the [PersonLinkRepository] round-trips
 * directed edges between two people. The tests assert the
 * add / observe / delete contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersonLinkRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var linkDao: PersonLinkDao
    private lateinit var personDao: PersonDao
    private lateinit var repo: PersonLinkRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        linkDao = db.personLinkDao()
        personDao = db.personDao()
        repo = PersonLinkRepository(linkDao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `add and observe a link`() = runTest {
        val (a, b) = seedTwoPeople()
        repo.add(a, b, "Reports to")
        val links = repo.observeForPerson(a).first()
        assertEquals(1, links.size)
        assertEquals("Reports to", links[0].relation)
        assertEquals(b, links[0].toId)
        assertEquals(a, links[0].fromId)
    }

    @Test
    fun `observeForPerson matches both directions`() = runTest {
        val (a, b) = seedTwoPeople()
        repo.add(a, b, "Reports to")
        // Observe from the "to" side: should also see the edge.
        val linksFromB = repo.observeForPerson(b).first()
        assertEquals(1, linksFromB.size)
    }

    @Test
    fun `delete removes the edge`() = runTest {
        val (a, b) = seedTwoPeople()
        repo.add(a, b, "Knows")
        repo.delete(a, b, "Knows")
        val links = repo.observeForPerson(a).first()
        assertEquals(0, links.size)
    }

    @Test
    fun `same pair can have multiple distinct relations`() = runTest {
        val (a, b) = seedTwoPeople()
        repo.add(a, b, "Reports to")
        repo.add(a, b, "Knows")
        val links = repo.observeForPerson(a).first()
        assertEquals(2, links.size)
        assertEquals(setOf("Reports to", "Knows"), links.map { it.relation }.toSet())
    }

    private fun seedTwoPeople(): Pair<String, String> {
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        kotlinx.coroutines.runBlocking {
            personDao.upsert(PersonEntity(
                id = a, name = "A", designation = null, station = null,
                phone = null, userId = "u1", createdAt = now, updatedAt = now,
                isSensitive = false, syncStatus = SyncStatus.SYNCED,
            ))
            personDao.upsert(PersonEntity(
                id = b, name = "B", designation = null, station = null,
                phone = null, userId = "u1", createdAt = now, updatedAt = now,
                isSensitive = false, syncStatus = SyncStatus.SYNCED,
            ))
        }
        return a to b
    }
}
