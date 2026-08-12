package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.captures.SupabaseCaptureRepository
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncConflictEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonInsert
import com.baton.app.data.person.SupabasePersonRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
 * Tests for [SyncEngine] — the outbox drain that pushes local
 * writes to Supabase.
 *
 * M2-T8 added conflict-detection tests: when the server's
 * `updated_at` is newer than the local row, the engine drops the
 * local change, logs to [SyncConflictDao], and mirrors the
 * server's state into Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var syncConflictDao: SyncConflictDao
    private lateinit var personRemote: SupabasePersonRepository
    private lateinit var captureRemote: SupabaseCaptureRepository
    private lateinit var instructionRemote: SupabaseInstructionRepository
    private lateinit var engine: SyncEngine
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        personDao = db.personDao()
        syncQueueDao = db.syncQueueDao()
        syncConflictDao = db.syncConflictDao()
        personRemote = mockk(relaxed = true)
        captureRemote = mockk(relaxed = true)
        instructionRemote = mockk(relaxed = true)
        engine = SyncEngine(
            syncQueueDao = syncQueueDao,
            personDao = personDao,
            captureDao = db.captureDao(),
            instructionDao = db.instructionDao(),
            syncConflictDao = syncConflictDao,
            personRemote = personRemote,
            captureRemote = captureRemote,
            instructionRemote = instructionRemote,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun enqueuePerson(
        id: String,
        name: String,
        op: String = SyncQueueEntity.OP_INSERT,
        localUpdatedAt: String = "2026-08-12T00:00:00Z",
    ) {
        personDao.upsert(
            PersonEntity(
                id = id,
                name = name,
                designation = "SHO",
                station = "Bandipora",
                phone = null,
                userId = "u-1",
                createdAt = localUpdatedAt,
                updatedAt = localUpdatedAt,
                syncStatus = if (op == SyncQueueEntity.OP_INSERT) SyncStatus.PENDING_INSERT
                             else SyncStatus.PENDING_UPDATE,
            )
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "persons",
                rowId = id,
                op = op,
                payloadJson = json.encodeToString(
                    PersonInsert.serializer(),
                    PersonInsert(id = id, name = name, designation = "SHO", station = "Bandipora"),
                ),
                createdAt = 1L,
            )
        )
    }

    /**
     * Helper: every drain test sets up the same mockk answer.
     * The remote.create(...) is the [SupabasePersonRepository.create]
     * suspend function with signature
     * `(name, designation, station, clientId)`. Mockk's `args[n]`
     * is the n-th positional argument; we use the index to read
     * the clientId.
     */
    private fun stubRemoteEcho() {
        coEvery { personRemote.create(any(), any(), any(), any()) } answers {
            // args: [name: String, designation: String?, station: String?, clientId: String?]
            val name = args[0] as String
            val designation = args[1] as String?
            val station = args[2] as String?
            val clientId = args[3] as String?
            Person(
                id = clientId ?: "fallback",
                name = name,
                designation = designation,
                station = station,
                phone = null,
                updatedAt = "2026-08-12T00:00:00Z",
            )
        }
    }

    @Test
    fun `drainAll processes person inserts and marks SYNCED`() = runTest {
        enqueuePerson(id = "client-1", name = "Ramu")
        enqueuePerson(id = "client-2", name = "Priya")
        stubRemoteEcho()

        engine.drainAll()
        advanceUntilIdle()

        // Both rows are SYNCED.
        val rows = personDao.snapshot()
        assertEquals(2, rows.size)
        rows.forEach { assertEquals(SyncStatus.SYNCED, it.syncStatus) }
        // Queue is empty.
        assertEquals(0, syncQueueDao.snapshot().size)
    }

    @Test
    fun `drainAll records failure and keeps the entry on error`() = runTest {
        enqueuePerson(id = "client-1", name = "Ramu")
        coEvery { personRemote.create(any(), any(), any(), any()) } throws RuntimeException("offline")

        engine.drainAll()
        advanceUntilIdle()

        // Row stays PENDING_INSERT (drain failed, no update).
        val row = personDao.getById("client-1")
        assertNotNull(row)
        assertEquals(SyncStatus.PENDING_INSERT, row!!.syncStatus)
        // The queue entry remains with attempts++ and lastError set.
        val queue = syncQueueDao.snapshot()
        assertEquals(1, queue.size)
        assertEquals(1, queue[0].attempts)
        assertEquals("offline", queue[0].lastError)
    }

    @Test
    fun `drainOne processes a single entry`() = runTest {
        enqueuePerson(id = "client-1", name = "Ramu")
        stubRemoteEcho()

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_INSERT)
        advanceUntilIdle()

        val row = personDao.getById("client-1")
        assertNotNull(row)
        assertEquals(SyncStatus.SYNCED, row!!.syncStatus)
        assertEquals(0, syncQueueDao.snapshot().size)
    }

    @Test
    fun `drainOne is a no-op when no entry matches`() = runTest {
        // No entry in the queue.
        engine.drainOne(rowId = "ghost", table = "persons", op = SyncQueueEntity.OP_INSERT)
        advanceUntilIdle()
        // No remote call.
        coVerify(exactly = 0) { personRemote.create(any(), any(), any(), any()) }
    }

    // ----- M2-T8: conflict detection on OP_UPDATE -----

    @Test
    fun `OP_UPDATE drops local write when server is newer and logs conflict`() = runTest {
        // Local row was last touched on Aug 10. Server has a newer
        // version from Aug 12. The local OP_UPDATE must be dropped
        // and a row logged in sync_conflicts.
        val oldLocal = "2026-08-10T00:00:00Z"
        val newerServer = "2026-08-12T00:00:00Z"
        enqueuePerson(
            id = "client-1",
            name = "Ramu (local edit)",
            op = SyncQueueEntity.OP_UPDATE,
            localUpdatedAt = oldLocal,
        )
        coEvery { personRemote.findById("client-1") } returns Person(
            id = "client-1",
            name = "Ramu (server edit)",
            designation = "SHO",
            station = "Bandipora",
            phone = null,
            updatedAt = newerServer,
        )
        // If a conflict is detected we should NOT call create.
        coEvery { personRemote.create(any(), any(), any(), any()) } throws
            AssertionError("create() must not be called when a conflict is detected")

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        // 1. Queue is drained.
        assertEquals(0, syncQueueDao.snapshot().size)
        // 2. Local row is overwritten with the server's newer value.
        val local = personDao.getById("client-1")
        assertNotNull(local)
        assertEquals("Ramu (server edit)", local!!.name)
        assertEquals(SyncStatus.SYNCED, local.syncStatus)
        assertEquals(newerServer, local.updatedAt)
        // 3. Conflict is logged.
        val conflicts = syncConflictDao.forRow("persons", "client-1")
        assertEquals(1, conflicts.size)
        val c: SyncConflictEntity = conflicts[0]
        assertEquals(SyncEngine.REASON_SERVER_NEWER, c.reason)
        assertTrue("local payload should mention local name", c.localPayload.contains("local edit"))
        assertTrue("server payload should mention server name", c.serverPayload.contains("server edit"))
    }

    @Test
    fun `OP_UPDATE proceeds when local is newer than server`() = runTest {
        // Local is Aug 12, server is Aug 10. Local wins; no conflict.
        // v1.1.1: the wire call is now `setSensitive(id, local.isSensitive)`
        // — v1.1's `personRemote.create(...)` for the non-conflict path
        // was a root-cause bug. The default `isSensitive = false` in
        // PersonEntity means the call should be `setSensitive(id, false)`.
        val newerLocal = "2026-08-12T00:00:00Z"
        val olderServer = "2026-08-10T00:00:00Z"
        enqueuePerson(
            id = "client-1",
            name = "Ramu (local edit)",
            op = SyncQueueEntity.OP_UPDATE,
            localUpdatedAt = newerLocal,
        )
        coEvery { personRemote.findById("client-1") } returns Person(
            id = "client-1",
            name = "Ramu (server old)",
            designation = "SHO",
            station = "Bandipora",
            phone = null,
            updatedAt = olderServer,
        )
        // No-op: setSensitive succeeds, no conflict path.
        coEvery { personRemote.setSensitive(any(), any()) } returns Unit

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        // No conflict was logged.
        assertEquals(0, syncConflictDao.count())
        // The PATCH was made (and only the PATCH — never create()).
        coVerify(exactly = 1) { personRemote.setSensitive("client-1", false) }
        coVerify(exactly = 0) { personRemote.create(any(), any(), any(), any()) }
        // The local row is SYNCED.
        val local = personDao.getById("client-1")
        assertEquals(SyncStatus.SYNCED, local!!.syncStatus)
    }

    @Test
    fun `OP_UPDATE proceeds when server has no row yet`() = runTest {
        // Server returns null (e.g. the row was deleted elsewhere).
        // v1.1.1: we still PATCH `is_sensitive` rather than re-INSERTing.
        // The server's RLS / trigger policy decides what to do with the
        // PATCH on a non-existent row (typically a no-op). The local row
        // is the source of truth.
        enqueuePerson(
            id = "client-1",
            name = "Ramu",
            op = SyncQueueEntity.OP_UPDATE,
        )
        coEvery { personRemote.findById("client-1") } returns null
        coEvery { personRemote.setSensitive(any(), any()) } returns Unit

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        // No conflict was logged.
        assertEquals(0, syncConflictDao.count())
        // PATCH was made (and never create()).
        coVerify(exactly = 1) { personRemote.setSensitive("client-1", false) }
        coVerify(exactly = 0) { personRemote.create(any(), any(), any(), any()) }
    }

    @Test
    fun `conflict count increments per conflict`() = runTest {
        val oldLocal = "2026-08-10T00:00:00Z"
        val newerServer = "2026-08-12T00:00:00Z"
        // Two UPDATE entries, both conflict.
        enqueuePerson(id = "client-1", name = "A", op = SyncQueueEntity.OP_UPDATE, localUpdatedAt = oldLocal)
        enqueuePerson(id = "client-2", name = "B", op = SyncQueueEntity.OP_UPDATE, localUpdatedAt = oldLocal)
        coEvery { personRemote.findById("client-1") } returns Person(
            id = "client-1", name = "A-server", designation = null, station = null, phone = null,
            updatedAt = newerServer,
        )
        coEvery { personRemote.findById("client-2") } returns Person(
            id = "client-2", name = "B-server", designation = null, station = null, phone = null,
            updatedAt = newerServer,
        )
        coEvery { personRemote.create(any(), any(), any(), any()) } throws
            AssertionError("create() must not be called when a conflict is detected")

        engine.drainAll()
        advanceUntilIdle()

        assertEquals(2, syncConflictDao.count())
        // Both rows have the server's name.
        assertEquals("A-server", personDao.getById("client-1")!!.name)
        assertEquals("B-server", personDao.getById("client-2")!!.name)
    }

    @Test
    fun `no conflict when updated_at equal`() = runTest {
        // Same timestamp — no LWW loser; we don't log a conflict.
        // v1.1.1: the wire call is `setSensitive`, not `create`.
        val same = "2026-08-12T00:00:00Z"
        enqueuePerson(id = "client-1", name = "Ramu", op = SyncQueueEntity.OP_UPDATE, localUpdatedAt = same)
        coEvery { personRemote.findById("client-1") } returns Person(
            id = "client-1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null,
            updatedAt = same,
        )
        coEvery { personRemote.setSensitive(any(), any()) } returns Unit

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        assertEquals(0, syncConflictDao.count())
        coVerify(exactly = 1) { personRemote.setSensitive("client-1", false) }
        coVerify(exactly = 0) { personRemote.create(any(), any(), any(), any()) }
    }

    // ----- v1.1.1 root-cause: OP_UPDATE always PATCHes is_sensitive -----

    @Test
    fun `OP_UPDATE PATCHes is_sensitive=true when local row is sensitive`() = runTest {
        // v1.1.1 fix: toggling ON must PATCH the server. The previous
        // (correct) branch did this; the test guards against future
        // regressions.
        enqueuePerson(
            id = "client-1",
            name = "Ramu",
            op = SyncQueueEntity.OP_UPDATE,
        )
        // Flip the local row to sensitive.
        personDao.setSensitive(
            id = "client-1",
            sensitive = true,
            updatedAt = "2026-08-12T01:00:00Z",
            status = SyncStatus.PENDING_UPDATE,
        )
        coEvery { personRemote.findById("client-1") } returns null
        coEvery { personRemote.setSensitive(any(), any()) } returns Unit

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        coVerify(exactly = 1) { personRemote.setSensitive("client-1", true) }
        coVerify(exactly = 0) { personRemote.create(any(), any(), any(), any()) }
        assertEquals(SyncStatus.SYNCED, personDao.getById("client-1")!!.syncStatus)
    }

    @Test
    fun `OP_UPDATE PATCHes is_sensitive=false when local row is not sensitive (v1_1_1 regression guard)`() = runTest {
        // v1.1.1 fix: toggling OFF must PATCH the server with
        // `is_sensitive = false`. v1.1's else-branch incorrectly
        // called `personRemote.create(...)` which would re-INSERT
        // the row. This test guards against that regression.
        enqueuePerson(
            id = "client-1",
            name = "Ramu",
            op = SyncQueueEntity.OP_UPDATE,
        )
        // Local row stays `isSensitive = false` (the default).
        coEvery { personRemote.findById("client-1") } returns null
        coEvery { personRemote.setSensitive(any(), any()) } returns Unit
        // If the bug regresses, create() will be called and the
        // AssertionError will fail the test.
        coEvery { personRemote.create(any(), any(), any(), any()) } throws
            AssertionError("v1.1.1 fix: OP_UPDATE for persons must NOT call create()")

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        coVerify(exactly = 1) { personRemote.setSensitive("client-1", false) }
        assertEquals(SyncStatus.SYNCED, personDao.getById("client-1")!!.syncStatus)
    }

    @Test
    fun `OP_UPDATE is a no-op when local row was deleted before drain`() = runTest {
        // v1.1.1 fix: the `localRow == null` early-return guards against
        // a stale queue entry after a local delete. No network call.
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "persons",
                rowId = "ghost",
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = json.encodeToString(
                    PersonInsert.serializer(),
                    PersonInsert(id = "ghost", name = "Ghost", designation = null, station = null),
                ),
                createdAt = 1L,
            )
        )
        coEvery { personRemote.setSensitive(any(), any()) } throws
            AssertionError("must not call setSensitive when the local row is gone")
        coEvery { personRemote.create(any(), any(), any(), any()) } throws
            AssertionError("must not call create when the local row is gone")
        coEvery { personRemote.findById(any()) } throws
            AssertionError("must not call findById when the local row is gone")

        engine.drainOne(rowId = "ghost", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        // Queue entry was deleted (no failure recorded because the
        // early-return is before any network call).
        assertEquals(0, syncQueueDao.snapshot().size)
    }

    @Test
    fun `observe returns the most recent conflict first`() = runTest {
        val oldLocal = "2026-08-10T00:00:00Z"
        val newerServer = "2026-08-12T00:00:00Z"
        enqueuePerson(id = "client-1", name = "Ramu", op = SyncQueueEntity.OP_UPDATE, localUpdatedAt = oldLocal)
        coEvery { personRemote.findById("client-1") } returns Person(
            id = "client-1", name = "server", designation = null, station = null, phone = null,
            updatedAt = newerServer,
        )
        coEvery { personRemote.create(any(), any(), any(), any()) } throws
            AssertionError("create() must not be called when a conflict is detected")

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_UPDATE)
        advanceUntilIdle()

        val firstEmission = syncConflictDao.observe().first()
        assertEquals(1, firstEmission.size)
        assertTrue(firstEmission[0].serverPayload.contains("server"))
    }
}
