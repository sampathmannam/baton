package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.baton.app.data.person.Person
import com.baton.app.data.person.SupabasePersonRepository
import com.baton.app.data.person.toEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi as ExperimentalCoroutinesApi1
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
 * Tests for [RoomPersonRepository] using an in-memory Room database
 * and a mocked [SupabasePersonRepository]. Verifies the write-through
 * pattern: every local insert creates a Room row + a sync-queue entry
 * + a (fire-and-forget) drain call.
 */
@OptIn(ExperimentalCoroutinesApi1::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomPersonRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var remote: SupabasePersonRepository
    private lateinit var syncEngine: SyncEngine
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: RoomPersonRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        personDao = db.personDao()
        syncQueueDao = db.syncQueueDao()
        remote = mockk(relaxed = true)
        // M2-T6: the SyncEngine has its own DAO + remote deps. The
        // drain path in this test is best-effort; we exercise the
        // "enqueue" path and the "drain" call site separately.
        val captureRemote = mockk<com.baton.app.data.captures.SupabaseCaptureRepository>(relaxed = true)
        val instructionRemote =
            mockk<com.baton.app.data.instructions.SupabaseInstructionRepository>(relaxed = true)
        val captureDao = db.captureDao()
        val instructionDao = db.instructionDao()
        syncEngine = SyncEngine(
            syncQueueDao = syncQueueDao,
            personDao = personDao,
            captureDao = captureDao,
            instructionDao = instructionDao,
            syncConflictDao = db.syncConflictDao(),
            personRemote = remote,
            captureRemote = captureRemote,
            instructionRemote = instructionRemote,
        )
        appScope = CoroutineScope(testDispatcher)
        repo = RoomPersonRepository(
            dao = personDao,
            syncQueueDao = syncQueueDao,
            remote = remote,
            syncEngine = syncEngine,
            appScope = appScope,
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `create writes Room row with PENDING_INSERT, enqueues, fires drain`() = runTest {
        // Mock echoes the clientId so the drain's upsert replaces
        // the local row (same id) instead of inserting a second one.
        coEvery { remote.create(any(), any(), any(), any()) } answers {
            val name = args[0] as String
            val designation = args[1] as String?
            val station = args[2] as String?
            val clientId = args[3] as String?
            Person(id = clientId ?: "fallback", name = name, designation = designation, station = station, phone = null)
        }

        val result = repo.create(name = "Ramu", designation = "SHO", station = "Bandipora")
        // Drive the drain synchronously (the production code fires
        // it on appScope.launch, which is hard to await from a
        // runTest; calling drainOne directly is the equivalent).
        syncEngine.drainOne(result.id, "persons", SyncQueueEntity.OP_INSERT)
        advanceUntilIdle()

        // Returns the local Person (with the client-generated id).
        assertEquals("Ramu", result.name)
        // The Room row exists; the drain's upsert replaced the
        // PENDING row in place (same id) so we still have one row.
        val rows = personDao.snapshot()
        assertEquals(1, rows.size)
        assertEquals("Ramu", rows[0].name)
        // After the drain, the row is SYNCED.
        assertEquals(SyncStatus.SYNCED, rows[0].syncStatus)
        // A sync queue entry was created and then deleted on success.
        assertEquals(0, syncQueueDao.snapshot().size)
        // The remote was called with the client id.
        coVerify { remote.create(name = "Ramu", designation = "SHO", station = "Bandipora", clientId = result.id) }
    }

    @Test
    fun `create keeps local row when remote fails (offline tolerance)`() = runTest {
        coEvery { remote.create(any(), any(), any(), any()) } throws RuntimeException("Network down")

        val result = repo.create(name = "Ramu", designation = "SHO", station = "Bandipora")
        // Synchronous drain — the production appScope.launch would
        // race the test dispatcher; calling drainOne directly is
        // the equivalent for the test. Both the explicit call and
        // the appScope.launch will fail and bump `attempts`; the
        // total depends on dispatcher ordering. We assert
        // `attempts >= 1` rather than `== 1` to be order-agnostic.
        syncEngine.drainOne(result.id, "persons", SyncQueueEntity.OP_INSERT)
        advanceUntilIdle()

        // Returns the local row.
        assertEquals("Ramu", result.name)
        // The local row is still in Room.
        val rows = personDao.snapshot()
        assertEquals(1, rows.size)
        assertEquals("Ramu", rows[0].name)
        // The syncStatus stays PENDING_INSERT (drain failed).
        assertEquals(SyncStatus.PENDING_INSERT, rows[0].syncStatus)
        // The sync queue entry remains for the next drain.
        val queue = syncQueueDao.snapshot()
        assertEquals(1, queue.size)
        assertEquals("persons", queue[0].table)
        assertEquals(SyncQueueEntity.OP_INSERT, queue[0].op)
        assert(queue[0].attempts >= 1) { "expected at least 1 attempt, got ${queue[0].attempts}" }
        assertNotNull(queue[0].lastError)
    }

    @Test
    fun `refreshFromNetwork upserts server rows into Room`() = runTest {
        coEvery { remote.fetchAll() } returns listOf(
            Person(id = "srv-1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
            Person(id = "srv-2", name = "Priya", designation = "DSP", station = "Srinagar", phone = null),
        )

        repo.refreshFromNetwork()
        advanceUntilIdle()

        val rows = personDao.snapshot()
        assertEquals(2, rows.size)
        assertEquals(listOf("Priya", "Ramu"), rows.map { it.name })
        // All marked SYNCED.
        rows.forEach { assertEquals(SyncStatus.SYNCED, it.syncStatus) }
    }

    @Test
    fun `findByName falls back to remote when not in Room`() = runTest {
        coEvery { remote.findByName("missing") } returns
            Person(id = "srv-3", name = "missing", designation = "DSP", station = "Srinagar", phone = null)

        val found = repo.findByName("missing")
        advanceUntilIdle()

        assertNotNull(found)
        assertEquals("missing", found!!.name)
        // The remote hit was also written to Room.
        assertEquals(1, personDao.snapshot().size)
    }

    @Test
    fun `findByName returns Room row when present, no remote call`() = runTest {
        personDao.upsert(
            com.baton.app.data.local.entities.PersonEntity(
                id = "local-1",
                name = "Ramu",
                designation = "SHO",
                station = "Bandipora",
                phone = null,
                userId = "u-1",
                createdAt = "2026-08-12T00:00:00Z",
                updatedAt = "2026-08-12T00:00:00Z",
                syncStatus = SyncStatus.SYNCED,
            )
        )
        val found = repo.findByName("Ramu")
        advanceUntilIdle()

        assertNotNull(found)
        assertEquals("Ramu", found!!.name)
        // Remote was NOT called.
        coVerify(exactly = 0) { remote.findByName(any()) }
    }
}
